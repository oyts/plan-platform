package cn.zijun701.plan.mobile;

import android.content.Intent;
import android.content.ClipData;
import android.net.Uri;
import androidx.core.content.FileProvider;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

@CapacitorPlugin(name = "PlanNative")
public class PlanNativePlugin extends Plugin {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    // Large update downloads must not block login, API requests or offline replay.
    private final ExecutorService updateWorker = Executors.newSingleThreadExecutor();
    private String server;
    private SessionVault vault;
    private ClientUpdater updater;
    @Override public void load() {
        vault = new SessionVault(getContext());
        updater = new ClientUpdater(getActivity());
        server = getContext().getSharedPreferences("plan-native-settings", 0).getString("server", "https://plan.zijun701.cn");
        try { if (!server.isEmpty()) server = TransportRules.server(server, BuildConfig.DEBUG); }
        catch (IllegalArgumentException invalid) { server = ""; }
    }
    @PluginMethod public void execute(PluginCall call) {
        String command = call.getString("command", "");
        ExecutorService executor = Arrays.asList("desktop_check_update", "desktop_download_update", "desktop_install_update", "desktop_allow_update_install").contains(command) ? updateWorker : worker;
        executor.execute(() -> {
            try {
                JSObject args = call.getObject("args", new JSObject());
                Object result;
                switch (command) {
                    case "desktop_info":
                        result = new JSObject().put("version", BuildConfig.VERSION_NAME).put("versionCode", BuildConfig.VERSION_CODE).put("serverUrl", server).put("updatesEnabled", updater.enabled()).put("updateChannel", BuildConfig.PLAN_UPDATE_URL).put("debug", BuildConfig.DEBUG); break;
                    case "desktop_check_update": result = updater.check(); break;
                    case "desktop_download_update": updater.download(); result = true; break;
                    case "desktop_install_update": updater.install(); result = true; break;
                    case "desktop_allow_update_install": updater.allowInstall(); result = true; break;
                    case "desktop_probe": result = probe(args.getString("serverUrl")); break;
                    case "desktop_save_server":
                        String next = TransportRules.server(args.getString("serverUrl"), BuildConfig.DEBUG);
                        if (!getContext().getSharedPreferences("plan-native-settings", 0).edit().putString("server", next).commit()) throw new IllegalStateException("配置保存失败。");
                        server = next; result = true; break;
                    case "desktop_request":
                        if (server.isEmpty()) throw new IllegalStateException("请先进入客户端设置，配置新版 Spring Boot 服务器地址。");
                        result = request(server, args.getJSObject("request"), true); break;
                    case "clear_native_session": if (!server.isEmpty()) vault.write(server, null); result = true; break;
                    case "desktop_open_url": openUrl(args.getString("url")); result = true; break;
                    case "share_file": shareFile(args); result = true; break;
                    default: throw new IllegalArgumentException("客户端操作不支持。");
                }
                call.resolve(new JSObject().put("value", result));
            } catch (Exception error) {
                // No tokens, passwords or server responses in logs / errors.
                String message = error instanceof IllegalArgumentException || error instanceof IllegalStateException ? error.getMessage() : "连接失败，请检查网络、HTTPS 证书和服务器设置。";
                call.reject(message);
            }
        });
    }
    private String probe(String input) throws Exception {
        String origin = TransportRules.server(input, BuildConfig.DEBUG);
        JSObject response = request(origin, new JSObject().put("method", "GET").put("path", "/api/auth/config").put("headers", new JSObject()), false);
        if (response.getInteger("status", 0) != 200) throw new IllegalStateException("账号接口未就绪，请使用新版 Spring Boot 后端。");
        JSONObject body = new JSONObject(response.getString("body"));
        if (!body.has("registrationEnabled")) throw new IllegalStateException("服务器未提供新版账号接口。");
        return "连接成功，可以保存并登录。";
    }
    private JSObject request(String origin, JSObject args, boolean session) throws Exception {
        URI target = TransportRules.target(origin, args.getString("path"), args.getString("query"));
        String method = TransportRules.method(args.getString("method"));
        // HttpURLConnection does not support PATCH on all Android releases; use the platform implementation.
        HttpURLConnection connection = (HttpURLConnection) target.toURL().openConnection();
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestMethod(method);
        JSObject headers = args.getJSObject("headers", new JSObject());
        Iterator<String> names = headers.keys();
        while (names.hasNext()) {
            String name = names.next(); String value = headers.getString(name);
            if (TransportRules.header(name) && value != null && !value.contains("\r") && !value.contains("\n")) connection.setRequestProperty(name, value);
        }
        if (session && target.getPath().startsWith("/api/auth/")) {
            String saved = vault.read(origin);
            if (saved != null) {
                JSONObject data = new JSONObject(saved);
                if (data.getLong("expiresAt") > System.currentTimeMillis()) connection.setRequestProperty("Cookie", "plan_refresh=" + data.getString("value"));
                else vault.write(origin, null);
            }
        }
        String body = args.getString("body");
        try {
            if (body != null && !method.equals("GET")) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("提交内容过大。");
                connection.setDoOutput(true); connection.setFixedLengthStreamingMode(bytes.length);
                try (var output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (session) {
                for (Map.Entry<String, List<String>> entry : connection.getHeaderFields().entrySet()) {
                    if (!"set-cookie".equalsIgnoreCase(entry.getKey())) continue;
                    for (String raw : entry.getValue()) for (HttpCookie cookie : HttpCookie.parse(raw)) {
                        if (!cookie.getName().equals("plan_refresh")) continue;
                        if (cookie.getValue().isEmpty() || cookie.getMaxAge() == 0) vault.write(origin, null);
                        else if (cookie.getValue().matches("[A-Za-z0-9._~-]+") && cookie.getMaxAge() > 0) {
                            vault.write(origin, new JSONObject().put("value", cookie.getValue()).put("expiresAt", TransportRules.cookieExpiry(System.currentTimeMillis(), cookie.getMaxAge())).toString());
                        }
                    }
                }
            }
            JSObject responseHeaders = new JSObject();
            for (String name : Arrays.asList("content-type", "content-disposition", "etag", "retry-after")) {
                String value = connection.getHeaderField(name); if (value != null) responseHeaders.put(name, value);
            }
            InputStream input = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String text = "";
            if (input != null) try (input; ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] chunk = new byte[8192]; int read;
                while ((read = input.read(chunk)) != -1) {
                    if (buffer.size() + read > 8 * 1024 * 1024) throw new IllegalStateException("响应过大，请缩小导出日期范围。");
                    buffer.write(chunk, 0, read);
                }
                text = buffer.toString(StandardCharsets.UTF_8.name());
            }
            return new JSObject().put("status", status).put("headers", responseHeaders).put("body", text);
        } finally { connection.disconnect(); }
    }
    private void openUrl(String url) {
        URI uri = URI.create(url);
        if (!Arrays.asList("https", "http").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException("仅支持打开网页链接。");
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        if (intent.resolveActivity(getContext().getPackageManager()) == null) throw new IllegalStateException("未找到可以打开链接的浏览器。");
        getActivity().runOnUiThread(() -> getActivity().startActivity(intent));
    }
    private void shareFile(JSObject args) throws Exception {
        String name = TransportRules.exportName(args.getString("fileName"));
        String body = args.getString("body", "");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 8 * 1024 * 1024) throw new IllegalArgumentException("导出文件过大。");
        File dir = new File(getContext().getCacheDir(), "exports");
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("导出目录创建失败。");
        File[] old = dir.listFiles(); if (old != null) for (File file : old) if (System.currentTimeMillis() - file.lastModified() > 24 * 3600 * 1000L) file.delete();
        File file = new File(dir, name); try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", file);
        Intent intent = new Intent(Intent.ACTION_SEND).setType(name.endsWith(".csv") ? "text/csv" : "text/markdown")
                .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setClipData(ClipData.newUri(getContext().getContentResolver(), name, uri));
        getActivity().runOnUiThread(() -> getActivity().startActivity(Intent.createChooser(intent, "保存或分享导出文件")));
    }
    @Override protected void handleOnDestroy() { worker.shutdown(); updateWorker.shutdown(); }
}
