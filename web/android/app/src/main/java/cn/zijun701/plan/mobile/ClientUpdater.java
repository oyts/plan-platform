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

/** Update traffic never uses business-server cookies or tokens. Source is pinned at build time. */
final class ClientUpdater {
    private final Activity activity;
    private JSONObject pending;
    private File ready;
    ClientUpdater(Activity activity) { this.activity = activity; }
    boolean enabled() { return !BuildConfig.PLAN_UPDATE_URL.isEmpty(); }
    JSObject check() throws Exception {
        if (!enabled()) throw new IllegalStateException("当前测试包未绑定可信更新源。");
        HttpURLConnection connection = open(UpdateRules.url(BuildConfig.PLAN_UPDATE_URL, BuildConfig.DEBUG));
        JSONObject manifest;
        try {
            int status = connection.getResponseCode();
            if (status == 204) { pending = null; ready = null; return null; }
            if (status != 200) throw new IllegalStateException("更新清单暂不可用，请稍后重试。");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[4096]; int read;
                while ((read = input.read(chunk)) != -1) { if (output.size() + read > 65536) throw new IllegalStateException("更新清单过大。"); output.write(chunk, 0, read); }
                manifest = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            }
        } finally { connection.disconnect(); }
        long latest = manifest.getLong("versionCode"), minimum = manifest.has("minimumSupportedVersionCode") ? manifest.getLong("minimumSupportedVersionCode") : 1;
        boolean required = UpdateRules.mandatory(BuildConfig.VERSION_CODE, latest, minimum);
        if (!manifest.getString("version").matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")) throw new IllegalArgumentException("发布版本必须使用 x.y.z 格式。");
        UpdateRules.url(manifest.getString("url"), BuildConfig.DEBUG);
        UpdateRules.artifact(manifest.getString("sha256"), manifest.getLong("size"));
        if (latest <= BuildConfig.VERSION_CODE) { pending = null; ready = null; return null; }
        pending = manifest; ready = null;
        return new JSObject().put("version", manifest.getString("version")).put("versionCode", latest).put("notes", manifest.optString("notes", "")).put("mandatory", required);
    }
    void download() throws Exception {
        if (pending == null) throw new IllegalStateException("请先检查新版本。");
        File directory = new File(activity.getCacheDir(), "updates");
        if (!directory.exists() && !directory.mkdirs()) throw new IllegalStateException("更新目录创建失败。");
        File part = new File(directory, "plan-update.part");
        File destination = new File(directory, "plan-update.apk");
        ready = null;
        HttpURLConnection connection = open(UpdateRules.url(pending.getString("url"), BuildConfig.DEBUG));
        try {
            if (connection.getResponseCode() != 200) throw new IllegalStateException("更新包下载失败，请稍后重试。");
            long expected = pending.getLong("size"), total = 0;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(part)) {
                byte[] chunk = new byte[32768]; int read;
                while ((read = input.read(chunk)) != -1) {
                    total += read;
                    if (total > expected || total > UpdateRules.MAX_APK_BYTES) throw new IllegalStateException("更新包大小不符合发布清单。");
                    digest.update(chunk, 0, read); output.write(chunk, 0, read);
                }
            }
            if (total != expected || !hex(digest.digest()).equalsIgnoreCase(pending.getString("sha256"))) throw new IllegalStateException("更新包完整性校验失败，已拒绝安装。");
            verifyArchive(part);
            if (destination.exists() && !destination.delete()) throw new IllegalStateException("旧更新缓存无法替换。");
            if (!part.renameTo(destination)) throw new IllegalStateException("更新包保存失败。");
            ready = destination;
        } finally { connection.disconnect(); if (part.exists()) part.delete(); }
    }
    void install() throws Exception {
        if (ready == null || !ready.isFile() || pending == null) throw new IllegalStateException("请先下载并校验更新包。");
        // Revalidate the exact bytes immediately before handing them to Android.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new java.io.FileInputStream(ready)) { byte[] chunk = new byte[32768]; int read; while ((read = input.read(chunk)) != -1) digest.update(chunk, 0, read); }
        if (ready.length() != pending.getLong("size") || !hex(digest.digest()).equalsIgnoreCase(pending.getString("sha256"))) throw new IllegalStateException("更新缓存校验失败，请重新下载。");
        verifyArchive(ready);
        if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) throw new IllegalStateException("请先点击“允许安装更新”，在系统设置中授权，再返回点击安装。");
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", ready);
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
    private void verifyArchive(File file) throws Exception {
        PackageManager manager = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags), installed = manager.getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || !activity.getPackageName().equals(archive.packageName) || !pending.getString("version").equals(archive.versionName) || code(archive) != pending.getLong("versionCode") || code(archive) <= code(installed) || !signers(archive).equals(signers(installed))) {
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
