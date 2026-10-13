package cn.zijun701.plan.mobile;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;

/** Pinned source. Private updates use only same-origin short access tokens, never refresh cookies. */
final class ClientUpdater {
    private final Activity activity;
    private volatile JSONObject pending;
    private volatile File ready;
    private volatile String pendingOwner;
    private final Object stateLock = new Object();
    private long revision;
    ClientUpdater(Activity activity) { this.activity = activity; }
    boolean enabled() { return !BuildConfig.PLAN_UPDATE_URL.isEmpty(); }
    boolean requiresLogin() { return UpdateRules.authenticated(BuildConfig.PLAN_UPDATE_URL); }
    void invalidate() { synchronized (stateLock) { revision++; pending = null; ready = null; pendingOwner = null; } }
    private long snapshotRevision() { synchronized (stateLock) { return revision; } }
    private void ensureRevision(long expected) { if (revision != expected) throw new IllegalStateException("账号或服务器已变化，请重新检查更新。"); }
    private void authorize(String server, JSObject credentials, boolean requirePending) {
        if (!requiresLogin()) return;
        UpdateRules.credentials(UpdateRules.url(BuildConfig.PLAN_UPDATE_URL, BuildConfig.DEBUG), server,
            credentials.getString("serverUrl"), credentials.getString("ownerId"), credentials.getString("accessToken"), BuildConfig.DEBUG);
        if (requirePending && !credentials.getString("ownerId").equals(pendingOwner))
            throw new IllegalStateException("更新属于之前的账号，请重新检查更新。");
    }
    private HttpURLConnection connection(URI uri, JSObject credentials) throws Exception {
        HttpURLConnection connection = open(uri);
        if (requiresLogin()) connection.setRequestProperty("Authorization", "Bearer " + credentials.getString("accessToken"));
        return connection;
    }
    private static void requireResponse(int status, boolean manifest) {
        if (status == 401) throw new IllegalStateException("登录状态已失效，请重新登录后检查更新。");
        if (status == 403) throw new IllegalStateException("当前账号没有受邀更新权限，请联系管理员。");
        if (status == 404 || status == 503) throw new IllegalStateException("受邀更新源尚未就绪，请联系管理员获取新版安装包。");
        if (status != 200) throw new IllegalStateException(manifest ? "更新清单暂不可用，请稍后重试。" : "更新包下载失败，请稍后重试。");
    }
    JSObject check(String server, JSObject credentials) throws Exception {
        if (!enabled()) throw new IllegalStateException("当前测试包未绑定可信更新源。");
        authorize(server, credentials, false);
        long started = snapshotRevision();
        HttpURLConnection connection = connection(UpdateRules.url(BuildConfig.PLAN_UPDATE_URL, BuildConfig.DEBUG), credentials);
        JSONObject manifest;
        try {
            int status = connection.getResponseCode();
            if (status == 204) { synchronized (stateLock) { ensureRevision(started); pending = null; ready = null; pendingOwner = null; } return null; }
            requireResponse(status, true);
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096]; int read;
                while ((read = input.read(chunk)) != -1) { if (output.size() + read > 65536) throw new IllegalStateException("更新清单过大。"); output.write(chunk, 0, read); }
                manifest = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
        long latest = manifest.getLong("versionCode"), minimum = manifest.has("minimumSupportedVersionCode") ? manifest.getLong("minimumSupportedVersionCode") : 1;
        boolean required = UpdateRules.mandatory(BuildConfig.VERSION_CODE, latest, minimum);
        if (!manifest.getString("version").matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) throw new IllegalArgumentException("发布版本必须使用 x.y.z 格式。");
        if (requiresLogin()) {
            UpdateRules.authenticatedArtifact(UpdateRules.url(BuildConfig.PLAN_UPDATE_URL, BuildConfig.DEBUG), manifest.getString("url"), latest, BuildConfig.DEBUG);
            if (!credentials.getString("ownerId").equals(manifest.getString("accountId")) || !activity.getPackageName().equals(manifest.getString("packageName")))
                throw new IllegalStateException("更新清单与当前账号或应用不匹配，已拒绝下载。");
        } else UpdateRules.url(manifest.getString("url"), BuildConfig.DEBUG);
        UpdateRules.artifact(manifest.getString("sha256"), manifest.getLong("size"));
        synchronized (stateLock) {
            ensureRevision(started);
            if (latest <= BuildConfig.VERSION_CODE) { pending = null; ready = null; pendingOwner = null; return null; }
            pending = manifest; ready = null; pendingOwner = requiresLogin() ? credentials.getString("ownerId") : null;
        }
        JSObject result = new JSObject().put("version", manifest.getString("version")).put("versionCode", latest).put("notes", manifest.optString("notes", "")).put("mandatory", required);
        if (requiresLogin()) result.put("accountId", pendingOwner);
        return result;
    }
    void download(String server, JSObject credentials) throws Exception {
        long started; JSONObject manifest;
        synchronized (stateLock) {
            authorize(server, credentials, true);
            if (pending == null) throw new IllegalStateException("请先检查新版本。");
            started = revision; manifest = pending; ready = null;
        }
        File directory = new File(activity.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("更新目录创建失败。");
        File part = new File(directory, "plan-update.part");
        File destination = new File(directory, "plan-update.apk");
        URI target = requiresLogin() ? UpdateRules.authenticatedArtifact(UpdateRules.url(BuildConfig.PLAN_UPDATE_URL, BuildConfig.DEBUG), manifest.getString("url"), manifest.getLong("versionCode"), BuildConfig.DEBUG) : UpdateRules.url(manifest.getString("url"), BuildConfig.DEBUG);
        HttpURLConnection connection = connection(target, credentials);
        try {
            requireResponse(connection.getResponseCode(), false);
            long expected = manifest.getLong("size"), total = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(part)) {
                byte[] chunk = new byte[32768]; int read;
                while ((read = input.read(chunk)) != -1) {
                    total += read;
                    if (total > expected || total > UpdateRules.MAX_APK_BYTES) throw new IllegalStateException("更新包大小不符合发布清单。");
                    digest.update(chunk, 0, read); output.write(chunk, 0, read);
                }
            }
            if (total != expected || !hex(digest.digest()).equalsIgnoreCase(manifest.getString("sha256"))) throw new IllegalStateException("更新包完整性校验失败，已拒绝安装。");
            verifyArchive(part, manifest);
            synchronized (stateLock) {
                ensureRevision(started);
                if (destination.exists() && !destination.delete()) throw new IllegalStateException("旧更新缓存无法替换。");
                if (!part.renameTo(destination)) throw new IllegalStateException("更新包保存失败。");
                ready = destination;
            }
        } finally { connection.disconnect(); if (part.exists()) part.delete(); }
    }
    void install(String server, JSObject credentials) throws Exception {
        long started; File file; JSONObject manifest;
        synchronized (stateLock) {
            authorize(server, credentials, true);
            if (ready == null || !ready.isFile() || pending == null) throw new IllegalStateException("请先下载并校验更新包。");
            started = revision; file = ready; manifest = pending;
        }
        // Revalidate the exact bytes immediately before handing them to Android.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(file)) { byte[] chunk = new byte[32768]; int read; while ((read = input.read(chunk)) != -1) digest.update(chunk, 0, read); }
        if (file.length() != manifest.getLong("size") || !hex(digest.digest()).equalsIgnoreCase(manifest.getString("sha256"))) throw new IllegalStateException("更新缓存校验失败，请重新下载。");
        verifyArchive(file, manifest);
        synchronized (stateLock) { ensureRevision(started); }
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) throw new IllegalStateException("请先点击“允许安装更新”，在系统设置中授权，再返回点击安装。");
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(activity.getContentResolver(), "计划舱更新", uri));
        startSystemActivity(intent);
    }
    void allowInstall() throws Exception {
        if (Build.VERSION.SDK_INT >= 26) startSystemActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName())));
    }
    private void startSystemActivity(Intent intent) throws Exception {
        java.util.concurrent.FutureTask<Void> task = new java.util.concurrent.FutureTask<>(() -> {
            try { activity.startActivity(intent); }
            catch (android.content.ActivityNotFoundException error) { throw new IllegalStateException("当前设备无法打开系统安装界面，请检查系统设置。", error); }
            return null;
        });
        activity.runOnUiThread(task);
        try { task.get(10, java.util.concurrent.TimeUnit.SECONDS); }
        catch (java.util.concurrent.ExecutionException error) { throw new IllegalStateException(error.getCause().getMessage(), error.getCause()); }
    }
    private void verifyArchive(File file, JSONObject manifest) throws Exception {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags), installed = manager.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || !activity.getPackageName().equals(archive.packageName) || !manifest.getString("version").equals(archive.versionName) || code(archive) != manifest.getLong("versionCode") || code(archive) <= code(installed) || !signers(archive).equals(signers(installed))) {
            throw new IllegalStateException("安装包名称、版本或签名与当前应用不匹配，已拒绝安装。");
        }
    }
    private static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private static Set<String> signers(PackageInfo info) throws Exception {
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : info.signatures;
        if (signatures == null || signatures.length == 0) throw new IllegalStateException("无法验证应用签名。");
        Set<String> result = new HashSet<>(); for (Signature signature : signatures) result.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()))); return result;
    }
    private static String hex(byte[] bytes) { StringBuilder result = new StringBuilder(); for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 255)); return result.toString(); }
    private static HttpURLConnection open(URI uri) throws Exception {
        if (!BuildConfig.PLAN_TEST_UPDATE_CERT.isEmpty()) UpdateRules.labUrl(uri);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        if (!BuildConfig.PLAN_TEST_UPDATE_CERT.isEmpty()) {
            if (!BuildConfig.DEBUG) throw new IllegalStateException("正式包禁止使用本地测试证书。");
            // Private updater-only trust store. Keep Android's hostname verification enabled.
            java.security.cert.Certificate cert = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(BuildConfig.PLAN_TEST_UPDATE_CERT.getBytes(StandardCharsets.US_ASCII)));
            java.security.KeyStore store = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            store.load(null); store.setCertificateEntry("update-lab", cert);
            javax.net.ssl.TrustManagerFactory factory = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            javax.net.ssl.SSLContext context = javax.net.ssl.SSLContext.getInstance("TLS");
            context.init(null, factory.getTrustManagers(), null);
            ((javax.net.ssl.HttpsURLConnection) connection).setSSLSocketFactory(context.getSocketFactory());
        }
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(30000); connection.setRequestProperty("Accept-Encoding", "identity"); return connection;
    }
}
