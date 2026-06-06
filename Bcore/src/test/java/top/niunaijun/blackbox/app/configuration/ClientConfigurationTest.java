package top.niunaijun.blackbox.app.configuration;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for single instance mode configuration interface.
 */
public class ClientConfigurationTest {

    @Test
    public void testDefaultSingleInstanceModeReturnsTrue() {
        ClientConfiguration config = new ClientConfiguration() {
            @Override
            public String getHostPackageName() {
                return "test.package";
            }
        };
        assertTrue("Default single instance mode should be true", config.isSingleInstanceMode());
    }

    @Test
    public void testCustomSingleInstanceModeCanReturnTrue() {
        ClientConfiguration config = new ClientConfiguration() {
            @Override
            public String getHostPackageName() {
                return "test.package";
            }

            @Override
            public boolean isSingleInstanceMode() {
                return true;
            }
        };
        assertTrue("Custom single instance mode should be true", config.isSingleInstanceMode());
    }

    @Test
    public void testOtherDefaultsUnchanged() {
        ClientConfiguration config = new ClientConfiguration() {
            @Override
            public String getHostPackageName() {
                return "test.package";
            }
        };
        assertFalse("hideRoot default should be false", config.isHideRoot());
        assertTrue("daemon default should be true", config.isEnableDaemonService());
        assertTrue("launcher default should be true", config.isEnableLauncherActivity());
        assertFalse("vpn default should be false", config.isUseVpnNetwork());
        assertFalse("flagSecure default should be false", config.isDisableFlagSecure());
    }
}
