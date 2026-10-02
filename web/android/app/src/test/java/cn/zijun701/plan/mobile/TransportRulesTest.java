package cn.zijun701.plan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransportRulesTest {
    @Test public void releaseRequiresHttpsRoot() {
        assertEquals("https://plan.example.cn", TransportRules.server("https://plan.example.cn/", false));
        for (String value : new String[]{"http://plan.example.cn", "http://127.0.0.1:43129", "https://user:pw@plan.example.cn", "https://plan.example.cn/api", "https://plan.example.cn?a=1", "https://plan.example.cn/#/login", "file:///etc/passwd"})
            assertThrows(IllegalArgumentException.class, () -> TransportRules.server(value, false));
    }
    @Test public void debugOnlyAllowsLocalHttp() {
        assertEquals("http://127.0.0.1:43129", TransportRules.server("http://127.0.0.1:43129", true));
        assertEquals("http://10.0.2.2:43129", TransportRules.server("http://10.0.2.2:43129", true));
        assertThrows(IllegalArgumentException.class, () -> TransportRules.server("http://192.168.1.2", true));
    }
    @Test public void apiCannotEscapePinnedOrigin() {
        assertEquals("https://plan.example.cn/api/plans?page=1", TransportRules.target("https://plan.example.cn", "/api/plans", "page=1").toString());
        for (String path : new String[]{"//evil.example/api", "/actuator/health", "/api/../admin", "/api/%2e%2e/admin", "/api/plans?x=1", "/api/\\evil", "/api/test\n"})
            assertThrows(IllegalArgumentException.class, () -> TransportRules.target("https://plan.example.cn", path, null));
    }
    @Test public void pageCannotInjectCookiesOrMethods() {
        assertFalse(TransportRules.header("Cookie")); assertFalse(TransportRules.header("Host")); assertTrue(TransportRules.header("X-Idempotency-Key"));
        assertEquals("PATCH", TransportRules.method("PATCH")); assertThrows(IllegalArgumentException.class, () -> TransportRules.method("TRACE"));
    }
    @Test public void exportIsRestrictedToTextFiles() {
        assertEquals("plan-2026-10-02.csv", TransportRules.exportName("plan-2026-10-02.csv"));
        for (String name : new String[]{"../session.csv", "secret.apk", "/tmp/test.md"}) assertThrows(IllegalArgumentException.class, () -> TransportRules.exportName(name));
    }
    @Test public void cookieExpiryIsAbsoluteAndCapped() {
        long now = 100000;
        assertEquals(now, TransportRules.cookieExpiry(now, 0));
        assertEquals(now + 60_000, TransportRules.cookieExpiry(now, 60));
        assertEquals(now + 30L * 24 * 3600 * 1000, TransportRules.cookieExpiry(now, Long.MAX_VALUE));
    }
}
