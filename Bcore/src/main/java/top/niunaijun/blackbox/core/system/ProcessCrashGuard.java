package top.niunaijun.blackbox.core.system;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import top.niunaijun.blackbox.utils.ByteDanceProcessCompat;
import top.niunaijun.blackbox.utils.Slog;

public final class ProcessCrashGuard {
    private static final String TAG = "ProcessCrashGuard";
    private static final long WINDOW_MS = 30_000L;
    private static final long BLOCK_MS = 60_000L;
    private static final int MAX_DEATHS_IN_WINDOW = 2;
    private static final Map<String, CrashState> sStates = new HashMap<>();

    private ProcessCrashGuard() {
    }

    public static boolean canStart(String packageName, String processName, int userId) {
        if (!ByteDanceProcessCompat.isSupportedPackage(packageName)) {
            return true;
        }
        String key = key(packageName, processName, userId);
        long now = System.currentTimeMillis();
        synchronized (sStates) {
            pruneLocked(now);
            CrashState state = sStates.get(key);
            if (state != null && state.blockUntilMs > now) {
                Slog.w(TAG, "Blocked crash-loop process start: " + key);
                return false;
            }
            return true;
        }
    }

    public static void recordStartSuccess(String packageName, String processName, int userId) {
        // Do not clear recent deaths immediately on process start. A process that
        // starts successfully and then crashes during Activity launch is still a
        // crash loop and must be counted across retries.
    }

    public static void recordProcessDeath(ProcessRecord record) {
        if (record == null) {
            return;
        }
        if (!ByteDanceProcessCompat.isSupportedPackage(record.getPackageName())) {
            return;
        }
        String key = key(record.getPackageName(), record.processName, record.userId);
        long now = System.currentTimeMillis();
        synchronized (sStates) {
            CrashState state = sStates.get(key);
            if (state == null || now - state.firstDeathMs > WINDOW_MS) {
                state = new CrashState();
                state.firstDeathMs = now;
                state.deaths = 1;
                sStates.put(key, state);
                return;
            }
            state.deaths++;
            if (state.deaths >= MAX_DEATHS_IN_WINDOW) {
                state.blockUntilMs = now + BLOCK_MS;
                Slog.w(TAG, "Process crash-loop blocked for " + BLOCK_MS + "ms: " + key);
            }
        }
    }

    private static String key(String packageName, String processName, int userId) {
        return userId + ":" + packageName + ":" + processName;
    }

    private static void pruneLocked(long now) {
        Iterator<Map.Entry<String, CrashState>> iterator = sStates.entrySet().iterator();
        while (iterator.hasNext()) {
            CrashState state = iterator.next().getValue();
            boolean oldWindow = now - state.firstDeathMs > WINDOW_MS;
            boolean unblocked = state.blockUntilMs == 0L || state.blockUntilMs < now;
            if (oldWindow && unblocked) {
                iterator.remove();
            }
        }
    }

    private static final class CrashState {
        long firstDeathMs;
        long blockUntilMs;
        int deaths;
    }
}
