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
    public static void artifact(String sha256, long size) {
        if (sha256 == null || !sha256.matches("[a-fA-F0-9]{64}") || size < 1 || size > MAX_APK_BYTES) throw new IllegalArgumentException("安装包校验值或大小无效。");
    }
}
