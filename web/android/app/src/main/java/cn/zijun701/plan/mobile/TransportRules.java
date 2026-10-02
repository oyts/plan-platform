package cn.zijun701.plan.mobile;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

/** Pure Java rules: independently tested without an Android emulator. */
final class TransportRules {
    private static final Set<String> METHODS = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE"));
    private static final Set<String> HEADERS = new HashSet<>(Arrays.asList("authorization", "content-type", "accept", "x-idempotency-key", "if-match"));
    static String server(String input, boolean debug) {
        try {
            URI uri = new URI(input.trim());
            String host = uri.getHost(); String path = uri.getPath();
            boolean local = Arrays.asList("localhost", "127.0.0.1", "10.0.2.2", "[::1]", "::1").contains(host == null ? "" : host.toLowerCase(Locale.ROOT));
            if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (path != null && !path.isEmpty() && !path.equals("/")) || uri.getPort() > 65535
                    || !("https".equals(uri.getScheme()) || (debug && local && "http".equals(uri.getScheme()))))
                throw new IllegalArgumentException();
            return new URI(uri.getScheme(), null, host.toLowerCase(Locale.ROOT), uri.getPort(), null, null, null).toString();
        } catch (Exception error) { throw new IllegalArgumentException("请填写 HTTPS 服务器根地址；测试包仅允许本机或模拟器的 HTTP 地址。"); }
    }
    static URI target(String origin, String path, String query) {
        if (path == null || !path.startsWith("/api/") || path.contains("..") || path.contains("%") || path.contains("\\")
                || path.contains("?") || path.contains("#") || path.contains("\r") || path.contains("\n"))
            throw new IllegalArgumentException("仅允许固定服务器 /api/ 下的请求。");
        if (query != null && (query.contains("#") || query.contains("\r") || query.contains("\n"))) throw new IllegalArgumentException("查询参数无效。");
        URI uri = URI.create(origin + path + (query == null || query.isEmpty() ? "" : "?" + query));
        if (!URI.create(origin).getAuthority().equals(uri.getAuthority())) throw new IllegalArgumentException("请求来源无效。");
        return uri;
    }
    static String method(String method) {
        if (method == null || !METHODS.contains(method)) throw new IllegalArgumentException("请求方法不支持。");
        return method;
    }
    static boolean header(String name) { return HEADERS.contains(name.toLowerCase(Locale.ROOT)); }
    static String exportName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_-]{1,100}\\.(csv|md)")) throw new IllegalArgumentException("导出文件名无效。");
        return name;
    }
    static long cookieExpiry(long now, long maxAge) {
        return now + Math.min(Math.max(maxAge, 0), 30L * 24 * 3600) * 1000;
    }
}
