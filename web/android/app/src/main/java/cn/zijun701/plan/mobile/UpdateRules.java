package cn.zijun701.plan.mobile;

import java.net.URI;

/** Pure validation shared by manifest checks and download checks. */
public final class UpdateRules {
    public static final long MAX_APK_BYTES = 256L * 1024 * 1024;
    private UpdateRules() {}
    public static URI url(String value, boolean debug) {
        URI uri;
        try { uri = URI.create(value); } catch (Exception e) { throw new IllegalArgumentException("更新地址无效。"); }
        boolean loopback = "127.0.0.1".equals(uri.getHost()) || "10.0.2.2".equals(uri.getHost());
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || (!"https".equals(uri.getScheme()) && !(debug && loopback && "http".equals(uri.getScheme())))) {
            throw new IllegalArgumentException("更新源和安装包必须使用 HTTPS；测试包仅额外允许本机 HTTP。");
        }
        return uri;
    }
    public static boolean mandatory(long current, long latest, long minimum) {
        if (current < 1 || latest < 1 || latest > Integer.MAX_VALUE || minimum < 1 || minimum > latest) throw new IllegalArgumentException("发布版本策略无效。");
        return current < minimum;
    }
    public static void labUrl(URI uri) {
        if (!"https".equals(uri.getScheme()) || !"127.0.0.1".equals(uri.getHost()) || uri.getPort() != 43133 || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("本地验收更新仅允许 https://127.0.0.1:43133。");
    }
    public static void artifact(String sha256, long size) {
        if (sha256 == null || !sha256.matches("[a-fA-F0-9]{64}") || size < 1 || size > MAX_APK_BYTES) throw new IllegalArgumentException("安装包校验值或大小无效。");
    }
    public static boolean authenticated(String manifestUrl) {
        return manifestUrl != null && !manifestUrl.isEmpty()
            && "/api/client-updates/android/latest".equals(URI.create(manifestUrl).getPath());
    }
    private static int port(URI uri) { return uri.getPort() == -1 ? ("https".equals(uri.getScheme()) ? 443 : 80) : uri.getPort(); }
    public static boolean sameOrigin(URI first, URI second) {
        return first.getScheme().equals(second.getScheme()) && first.getHost().equalsIgnoreCase(second.getHost()) && port(first) == port(second);
    }
    public static void credentials(URI manifest, String server, String reportedServer, String owner, String token, boolean debug) {
        URI business = url(server, debug), reported = url(reportedServer, debug);
        if (!sameOrigin(manifest, business) || !sameOrigin(business, reported) || !server.equals(reportedServer)
            || !(business.getPath().isEmpty() || business.getPath().equals("/")) || business.getQuery() != null
            || manifest.getQuery() != null || owner == null || !owner.matches("[A-Za-z0-9_-]{1,100}")
            || token == null || token.length() > 8192 || !token.matches("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")) {
            throw new IllegalStateException("请使用更新源对应的云端账号登录，再重新检查更新。");
        }
    }
    public static URI authenticatedArtifact(URI manifest, String value, long versionCode, boolean debug) {
        URI target = url(value, debug);
        if (!sameOrigin(manifest, target) || target.getQuery() != null
            || !target.getPath().equals("/api/client-updates/android/releases/" + versionCode + "/apk"))
            throw new IllegalArgumentException("受邀更新包必须来自同域受保护接口，已拒绝下载。");
        return target;
    }
}
