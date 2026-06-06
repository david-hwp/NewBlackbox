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
        return createProcessRecord(packageName, processName, buid, 0);
    }

    private ProcessRecord createProcessRecord(String packageName, String processName, int buid, int userId) {
        ApplicationInfo info = new ApplicationInfo();
        info.packageName = packageName;
        ProcessRecord record = new ProcessRecord(info, processName);
        record.buid = buid;
        record.userId = userId;
        record.pid = 0; // keep kill() a no-op in JVM unit tests
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

    @Test
    public void testKillAllOtherProcessesGlobalKeepsOnlyTargetPackageAndUser() throws Exception {
        ProcessRecord appA_user0 = createProcessRecord("com.app.a", "com.app.a", 10001, 0);
        ProcessRecord appA_user1 = createProcessRecord("com.app.a", "com.app.a", 110001, 1);
        ProcessRecord appB_user0 = createProcessRecord("com.app.b", "com.app.b", 10002, 0);

        mPidsSelfLocked.add(appA_user0);
        mPidsSelfLocked.add(appA_user1);
        mPidsSelfLocked.add(appB_user0);

        Map<String, ProcessRecord> appAUser0Processes = new HashMap<>();
        appAUser0Processes.put("com.app.a", appA_user0);
        mProcessMap.put(10001, appAUser0Processes);

        Map<String, ProcessRecord> appAUser1Processes = new HashMap<>();
        appAUser1Processes.put("com.app.a", appA_user1);
        mProcessMap.put(110001, appAUser1Processes);

        Map<String, ProcessRecord> appBProcesses = new HashMap<>();
        appBProcesses.put("com.app.b", appB_user0);
        mProcessMap.put(10002, appBProcesses);

        List<ProcessRecord> killed = service.performKillAllOtherProcessesGlobalLocked("com.app.a", 0);

        assertEquals("Should report 2 killed", 2, killed.size());
        assertEquals("Should keep only target user process", 1, mPidsSelfLocked.size());
        assertEquals("Remaining should be appA user0", 0, mPidsSelfLocked.get(0).userId);
        assertEquals("com.app.a", mPidsSelfLocked.get(0).getPackageName());
        assertNotNull("Target app/user should remain", mProcessMap.get(10001));
        assertNull("Same package in another user should be removed", mProcessMap.get(110001));
        assertNull("Other package should be removed", mProcessMap.get(10002));
    }
}
