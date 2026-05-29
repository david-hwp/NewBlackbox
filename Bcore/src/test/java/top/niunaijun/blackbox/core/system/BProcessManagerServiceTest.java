package top.niunaijun.blackbox.core.system;

import android.content.pm.ApplicationInfo;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for BProcessManagerService core kill logic.
 *
 * Uses new BProcessManagerService() + reflection to avoid triggering
 * BActivityManagerService / BNotificationManagerService singleton init
 * (which depends on BlackBoxCore static block and fails in JVM unit tests).
 */
public class BProcessManagerServiceTest {

    private BProcessManagerService service;
    private List<ProcessRecord> mPidsSelfLocked;
    private Map<Integer, Map<String, ProcessRecord>> mProcessMap;

    @Before
    public void setUp() throws Exception {
        // Create a fresh instance directly — avoids the global singleton
        service = new BProcessManagerService();

        // Access private fields via reflection
        Field pidsField = BProcessManagerService.class.getDeclaredField("mPidsSelfLocked");
        pidsField.setAccessible(true);
        mPidsSelfLocked = (List<ProcessRecord>) pidsField.get(service);
        mPidsSelfLocked.clear();

        Field mapField = BProcessManagerService.class.getDeclaredField("mProcessMap");
        mapField.setAccessible(true);
        mProcessMap = (Map<Integer, Map<String, ProcessRecord>>) mapField.get(service);
        mProcessMap.clear();
    }

    private ProcessRecord createProcessRecord(String packageName, String processName, int buid) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        ProcessRecord record = new ProcessRecord(info, processName);
        record.buid = buid;
        record.userId = 0;
        record.pid = 12345; // dummy pid
        return record;
    }

    @Test
    public void testKillAllOtherProcesses_killsNonTargetProcesses() throws Exception {
        // Setup: 3 processes from 2 different packages
        ProcessRecord appA_main = createProcessRecord("com.app.a", "com.app.a", 10001);
        ProcessRecord appA_service = createProcessRecord("com.app.a", "com.app.a:service", 10001);
        ProcessRecord appB_main = createProcessRecord("com.app.b", "com.app.b", 10002);

        mPidsSelfLocked.add(appA_main);
        mPidsSelfLocked.add(appA_service);
        mPidsSelfLocked.add(appB_main);

        Map<String, ProcessRecord> appAProcesses = new HashMap<>();
        appAProcesses.put("com.app.a", appA_main);
        appAProcesses.put("com.app.a:service", appA_service);
        mProcessMap.put(10001, appAProcesses);

        Map<String, ProcessRecord> appBProcesses = new HashMap<>();
        appBProcesses.put("com.app.b", appB_main);
        mProcessMap.put(10002, appBProcesses);

        // Act: kill all except com.app.b
        List<ProcessRecord> killed = service.performKillAllOtherProcessesLocked("com.app.b");

        // Assert: returned correct killed list
        assertEquals("Should report 2 killed processes", 2, killed.size());

        // Assert: only appB remains in mPidsSelfLocked
        assertEquals("Should have 1 process left", 1, mPidsSelfLocked.size());
        assertEquals("Remaining should be appB", "com.app.b", mPidsSelfLocked.get(0).getPackageName());

        // Assert: appA removed from mProcessMap, appB remains
        assertNull("AppA should be removed from mProcessMap", mProcessMap.get(10001));
        assertNotNull("AppB should remain in mProcessMap", mProcessMap.get(10002));
    }

    @Test
    public void testKillAllOtherProcesses_keepsAllWhenAllAreTarget() throws Exception {
        ProcessRecord appA_main = createProcessRecord("com.app.a", "com.app.a", 10001);
        ProcessRecord appA_service = createProcessRecord("com.app.a", "com.app.a:service", 10001);

        mPidsSelfLocked.add(appA_main);
        mPidsSelfLocked.add(appA_service);

        Map<String, ProcessRecord> appAProcesses = new HashMap<>();
        appAProcesses.put("com.app.a", appA_main);
        appAProcesses.put("com.app.a:service", appA_service);
        mProcessMap.put(10001, appAProcesses);

        List<ProcessRecord> killed = service.performKillAllOtherProcessesLocked("com.app.a");

        assertEquals("Should report 0 killed", 0, killed.size());
        assertEquals("Should keep all target processes", 2, mPidsSelfLocked.size());
        assertNotNull("AppA should remain in mProcessMap", mProcessMap.get(10001));
    }

    @Test
    public void testKillAllOtherProcesses_handlesEmptyList() throws Exception {
        List<ProcessRecord> killed = service.performKillAllOtherProcessesLocked("com.app.a");
        assertEquals("Should report 0 killed", 0, killed.size());
        assertTrue("Should remain empty", mPidsSelfLocked.isEmpty());
        assertTrue("Map should remain empty", mProcessMap.isEmpty());
    }

    @Test
    public void testKillAllOtherProcesses_keepsAllProcessesForSamePackage() throws Exception {
        ProcessRecord appA_main = createProcessRecord("com.app.a", "com.app.a", 10001);
        ProcessRecord appA_service = createProcessRecord("com.app.a", "com.app.a:service", 10001);
        ProcessRecord appB_main = createProcessRecord("com.app.b", "com.app.b", 10002);

        mPidsSelfLocked.add(appA_main);
        mPidsSelfLocked.add(appA_service);
        mPidsSelfLocked.add(appB_main);

        Map<String, ProcessRecord> appAProcesses = new HashMap<>();
        appAProcesses.put("com.app.a", appA_main);
        appAProcesses.put("com.app.a:service", appA_service);
        mProcessMap.put(10001, appAProcesses);

        Map<String, ProcessRecord> appBProcesses = new HashMap<>();
        appBProcesses.put("com.app.b", appB_main);
        mProcessMap.put(10002, appBProcesses);

        List<ProcessRecord> killed = service.performKillAllOtherProcessesLocked("com.app.a");

        assertEquals("Should report 1 killed", 1, killed.size());
        assertEquals("Should have 2 processes left", 2, mPidsSelfLocked.size());
        assertNotNull("AppA should remain in mProcessMap", mProcessMap.get(10001));
        assertNull("AppB should be removed from mProcessMap", mProcessMap.get(10002));
    }
}
