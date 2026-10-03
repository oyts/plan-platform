package cn.zijun701.plan.mobile;
import org.junit.Test;
import static org.junit.Assert.*;
public class UpdateRulesTest {
    @Test public void releaseRequiresHttpsAndNoCredentials() {
        assertEquals("updates.example.cn", UpdateRules.url("https://updates.example.cn/latest.json", false).getHost());
        for (String value : new String[]{"http://updates.example.cn/latest.json", "http://127.0.0.1:43133/latest.json", "https://a:b@updates.example.cn/latest.json", "file:///tmp/a.apk", "https://updates.example.cn/a#fragment"}) assertThrows(IllegalArgumentException.class, () -> UpdateRules.url(value, false));
    }
    @Test public void debugHttpIsLoopbackOnly() {
        assertEquals("http", UpdateRules.url("http://127.0.0.1:43133/latest.json", true).getScheme());
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.url("http://192.168.1.10/a.apk", true));
    }
    @Test public void requiredIsOnlyBelowMinimum() {
        assertFalse(UpdateRules.mandatory(5, 6, 5)); assertTrue(UpdateRules.mandatory(5, 6, 6));
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.mandatory(5, 6, 7));
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.mandatory(5, 6, 0));
    }
    @Test public void malformedOrOversizedArtifactRejected() {
        UpdateRules.artifact("a".repeat(64), 1024);
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.artifact("bad", 1024));
        assertThrows(IllegalArgumentException.class, () -> UpdateRules.artifact("a".repeat(64), UpdateRules.MAX_APK_BYTES + 1));
    }
    @Test public void testCertificateIsRestrictedToOneLoopbackOrigin() {
        UpdateRules.labUrl(java.net.URI.create("https://127.0.0.1:43133/android/latest.json"));
        for (String value : new String[]{"http://127.0.0.1:43133/a", "https://localhost:43133/a", "https://127.0.0.1:443/a", "https://example.com/a", "https://user@127.0.0.1:43133/a", "https://127.0.0.1:43133/a#b"}) assertThrows(IllegalArgumentException.class, () -> UpdateRules.labUrl(java.net.URI.create(value)));
    }
}
