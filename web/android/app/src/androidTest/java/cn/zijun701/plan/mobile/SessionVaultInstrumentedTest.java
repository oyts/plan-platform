package cn.zijun701.plan.mobile;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.UUID;
import static org.junit.Assert.*;

/** Run on a real device or emulator; this test never accesses a real account. */
@RunWith(AndroidJUnit4.class)
public class SessionVaultInstrumentedTest {
    @Test public void encryptedSessionSurvivesRecreationAndIsIsolated() throws Exception {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        var vault = new SessionVault(context);
        String first = "https://" + UUID.randomUUID() + ".example.invalid";
        String second = "https://" + UUID.randomUUID() + ".example.invalid";
        try {
            vault.write(first, "test-only-refresh-value");
            assertEquals("test-only-refresh-value", new SessionVault(context).read(first));
            assertNull(vault.read(second));
            var entries = context.getSharedPreferences("plan-native-sessions", 0).getAll();
            assertFalse(entries.containsValue("test-only-refresh-value"));
            vault.write(first, null);
            assertNull(new SessionVault(context).read(first));
        } finally { vault.write(first, null); vault.write(second, null); }
    }
}
