# Phase 03: JD 验证码虚拟环境检测规避 - Pattern Map

**Mapped:** 2026-05-31
**Files analyzed:** 9
**Analogs found:** 9 / 9

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `Bcore/src/main/java/.../fake/service/ProcessBuilderProxy.java` (new) | proxy | request-response | `FileSystemProxy.java` | role-match |
| `Bcore/src/main/java/.../fake/service/IPackageManagerProxy.java` (modify) | proxy | request-response | `IPackageManagerProxy.java` (self) | exact |
| `Bcore/src/main/java/.../fake/service/IActivityManagerProxy.java` (modify) | proxy | request-response | `IActivityManagerProxy.java` (self) | exact |
| `Bcore/src/main/cpp/Hook/ProcessHook.cpp` (new) | native hook | file-I/O | `FileSystemHook.cpp` | role-match |
| `Bcore/src/main/cpp/Hook/ProcessHook.h` (new) | native hook header | file-I/O | `FileSystemHook.h` | role-match |
| `Bcore/src/main/java/.../fake/service/libcore/OsStub.java` (modify) | proxy | request-response | `OsStub.java` (self) | exact |
| `Bcore/src/main/java/.../core/IOCore.java` (modify) | utility | file-I/O | `IOCore.java` (self) | exact |
| `e2e_honor/tests/jd_captcha.sh` (modify) | test | request-response | `e2e_honor/tests/jd_captcha.sh` (self) | exact |
| `e2e_honor/lib/honor_test_utils.sh` (modify) | test utility | request-response | `e2e_honor/lib/honor_test_utils.sh` (self) | exact |

## Pattern Assignments

### `ProcessBuilderProxy.java` (proxy, request-response) — NEW FILE

**Analog:** `FileSystemProxy.java`

**Imports pattern** (lines 1-10):
```java
package top.niunaijun.blackbox.fake.service;

import java.io.File;
import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
```

**Core proxy pattern** (from `FileSystemProxy.java` lines 12-55):
```java
public class FileSystemProxy extends ClassInvocationStub {
    public static final String TAG = "FileSystemProxy";

    public FileSystemProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("mkdirs")
    public static class Mkdirs extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                File file = (File) who;
                String path = file.getAbsolutePath();
                if (path.contains("Helium Crashpad") || path.contains("HeliumCrashReporter")) {
                    Slog.d(TAG, "FileSystem: mkdirs called for Helium crash path: " + path + ", returning true");
                    return true;
                }
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "FileSystem: mkdirs failed, returning true", e);
                return true;
            }
        }
    }
}
```

**Registration pattern** (from `HookManager.java` lines 141-142):
```java
addInjector(new FileSystemProxy());
```

---

### `IPackageManagerProxy.java` (proxy, request-response) — MODIFY EXISTING

**Analog:** `IPackageManagerProxy.java` (self, existing code)

**Intent query filtering pattern** (from existing `QueryBroadcastReceivers`, lines 413-431):
```java
@ProxyMethod("queryIntentReceivers")
public static class QueryBroadcastReceivers extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        Intent intent = MethodParameterUtils.getFirstParam(args, Intent.class);
        String type = MethodParameterUtils.getFirstParam(args, String.class);
        Integer flags = MethodParameterUtils.getFirstParam(args, Integer.class);
        List<ResolveInfo> resolves = BlackBoxCore.getBPackageManager().queryBroadcastReceivers(intent, flags, type, BActivityThread.getUserId());
        Slog.d(TAG, "queryIntentReceivers: " + resolves);
        if (BuildCompat.isN()) {
            return ParceledListSliceCompat.create(resolves);
        }
        return resolves;
    }
}
```

**Host package filtering pattern** (from existing `GetInstalledPackages`, lines 327-371):
```java
@ProxyMethod("getInstalledPackages")
public static class GetInstalledPackages extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        int flags = MethodParameterUtils.toInt(args[0]);
        List<PackageInfo> installedPackages = BlackBoxCore.getBPackageManager().getInstalledPackages(flags, BlackBoxCore.getUserId());

        // Filter out BlackBox to prevent virtual environment detection
        String hostPkg = BlackBoxCore.getHostPkg();
        Iterator<PackageInfo> iterator = installedPackages.iterator();
        while (iterator.hasNext()) {
            PackageInfo pi = iterator.next();
            if (pi.packageName != null && pi.packageName.equals(hostPkg)) {
                iterator.remove();
                Slog.d(TAG, "GetInstalledPackages: Filtered out host package " + hostPkg);
            }
        }
        return ParceledListSliceCompat.create(installedPackages);
    }
}
```

**ParceledListSlice return pattern** (from `ParceledListSliceCompat.java`, lines 18-30):
```java
public static Object create(List<?> list) {
    Object slice = BRParceledListSlice.get()._new(list);
    if (slice != null) {
        return slice;
    } else {
        slice = BRParceledListSlice.get()._new();
    }
    for (Object item : list) {
        BRParceledListSlice.get(slice).append(item);
    }
    BRParceledListSlice.get(slice).setLastSlice(true);
    return slice;
}
```

---

### `IActivityManagerProxy.java` (proxy, request-response) — MODIFY EXISTING

**Analog:** `IActivityManagerProxy.java` (self, existing code)

**Process list filtering pattern** (from existing `GetRunningAppProcesses`, lines 482-493):
```java
@ProxyMethod("getRunningAppProcesses")
public static class GetRunningAppProcesses extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        RunningAppProcessInfo runningAppProcesses = BActivityManager.get().getRunningAppProcesses(BActivityThread.getAppPackageName(), BActivityThread.getUserId());
        if (runningAppProcesses == null) {
            return new ArrayList<>();
        }
        return runningAppProcesses.mAppProcessInfoList;
    }
}
```

**Security exception handling pattern** (from existing `invoke()`, lines 105-129):
```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
        return super.invoke(proxy, method, args);
    } catch (SecurityException e) {
        String methodName = method.getName();
        Slog.w(TAG, "ActivityManager invoke: SecurityException in " + methodName + ", returning safe default", e);
        if (methodName.startsWith("set") || methodName.startsWith("update")) {
            return null;
        } else if (methodName.startsWith("get") || methodName.startsWith("query")) {
            return null;
        } else if (methodName.startsWith("start") || methodName.startsWith("bind")) {
            return false;
        } else if (methodName.startsWith("stop") || methodName.startsWith("unbind")) {
            return true;
        } else {
            return null;
        }
    } catch (Exception e) {
        Slog.e(TAG, "ActivityManager invoke: Unexpected error in " + method.getName(), e);
        return super.invoke(proxy, method, args);
    }
}
```

---

### `ProcessHook.cpp` / `ProcessHook.h` (native hook, file-I/O) — NEW FILES

**Analog:** `FileSystemHook.cpp` / `FileSystemHook.h`

**Header pattern** (from `FileSystemHook.h`, lines 1-13):
```cpp
#ifndef VIRTUALM_FILESYSTEMHOOK_H
#define VIRTUALM_FILESYSTEMHOOK_H

class FileSystemHook {
public:
    static void init();
};

#endif
```

**xDL hook initialization pattern** (from `FileSystemHook.cpp`, lines 66-95):
```cpp
#include "FileSystemHook.h"
#include "Log.h"
#include "xdl.h"
#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <cstring>
#include <errno.h>

static int (*orig_open)(const char *pathname, int flags, ...) = nullptr;

int new_open(const char *pathname, int flags, ...) {
    if (pathname != nullptr) {
        if (strstr(pathname, "resource-cache") ||
            strstr(pathname, "@idmap")) {
            ALOGD("FileSystemHook: Blocking problematic file access: %s", pathname);
            errno = ENOENT;
            return -1;
        }
    }
    va_list args;
    va_start(args, flags);
    mode_t mode = va_arg(args, mode_t);
    va_end(args);
    return orig_open(pathname, flags, mode);
}

void FileSystemHook::init() {
    ALOGD("FileSystemHook: Initializing file system hooks");
    void* handle = xdl_open("libc.so", XDL_DEFAULT);
    if (!handle) {
        ALOGE("FileSystemHook: Failed to open libc.so");
        return;
    }
    orig_open = (int (*)(const char*, int, ...))xdl_sym(handle, "open", nullptr);
    if (orig_open) {
        ALOGD("FileSystemHook: Found open function at %p", orig_open);
    } else {
        ALOGE("FileSystemHook: Failed to find open function");
    }
    xdl_close(handle);
}
```

**JNI hook pattern** (from `RuntimeHook.cpp`, lines 9-38):
```cpp
#include "RuntimeHook.h"
#import "JniHook/JniHook.h"
#include "BoxCore.h"

HOOK_JNI(jstring, nativeLoad, JNIEnv *env, jobject obj, jstring name, jobject class_loader) {
    const char *nameC = env->GetStringUTFChars(name, JNI_FALSE);
    ALOGD("nativeLoad: %s", nameC);
    jstring result = orig_nativeLoad(env, obj, name, class_loader);
    env->ReleaseStringUTFChars(name, nameC);
    return result;
}

void RuntimeHook::init(JNIEnv *env) {
    const char *className = "java/lang/Runtime";
    if (BoxCore::getApiLevel() >= __ANDROID_API_Q__) {
        JniHook::HookJniFun(env, className, "nativeLoad",
                            "(Ljava/lang/String;Ljava/lang/ClassLoader;Ljava/lang/Class;)Ljava/lang/String;",
                            (void *) new_nativeLoad2,
                            (void **) (&orig_nativeLoad2), true);
    }
}
```

---

### `OsStub.java` (proxy, request-response) — MODIFY EXISTING

**Analog:** `OsStub.java` (self, existing code)

**Path redirection pattern** (from existing `invoke()`, lines 45-59):
```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    if (args != null) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] == null)
                continue;
            if (args[i] instanceof String && ((String) args[i]).startsWith("/")) {
                String orig = (String) args[i];
                args[i] = IOCore.get().redirectPath(orig);
            }
        }
    }
    return super.invoke(proxy, method, args);
}
```

**UID spoofing pattern** (from existing `getFakeUid()`, lines 88-97):
```java
private static int getFakeUid(int callUid) {
    if (callUid > 0 && callUid <= Process.FIRST_APPLICATION_UID)
        return callUid;
    if (BActivityThread.isThreadInit() && BActivityThread.currentActivityThread().isInit()) {
        return BActivityThread.getBAppId();
    } else {
        return BlackBoxCore.getHostUid();
    }
}
```

---

### `IOCore.java` (utility, file-I/O) — MODIFY EXISTING

**Analog:** `IOCore.java` (self, existing code)

**Proc/cmdline redirection pattern** (from existing `proc()`, lines 173-182):
```java
private void proc(Map<String, String> rule) {
    int appPid = BlackBoxCore.getAppPid();
    int pid = Process.myPid();
    String selfProc = "/proc/self/";
    String proc = "/proc/" + pid + "/";

    String cmdline = new File(BEnvironment.getProcDir(appPid), "cmdline").getAbsolutePath();
    rule.put(proc + "cmdline", cmdline);
    rule.put(selfProc + "cmdline", cmdline);
}
```

**Redirect registration pattern** (from existing `addRedirect()`, lines 42-53):
```java
public void addRedirect(String origPath, String redirectPath) {
    if (TextUtils.isEmpty(origPath) || TextUtils.isEmpty(redirectPath) || mRedirectMap.get(origPath) != null)
        return;
    mTrieTree.add(origPath);
    mRedirectMap.put(origPath, redirectPath);
    File redirectFile = new File(redirectPath);
    if (!redirectFile.exists()) {
        FileUtils.mkdirs(redirectPath);
    }
    NativeCore.addIORule(origPath, redirectPath);
}
```

---

### `jd_captcha.sh` (test, request-response) — MODIFY EXISTING

**Analog:** `e2e_honor/tests/jd_captcha.sh` (self)

**Test structure pattern** (lines 1-30):
```bash
#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${E2E_HONOR_DIR:-}" ]]; then
    __TEST_DIR="$E2E_HONOR_DIR/tests"
else
    __TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
fi
source "$__TEST_DIR/../lib/utils.sh"
source "$__TEST_DIR/../lib/honor_test_utils.sh"
SCRIPT_DIR="$__TEST_DIR"
unset __TEST_DIR

TEST_ITERATION=${TEST_ITERATION:-1}

run_test() {
    log_info "Honor 京东验证码测试 — 迭代 #$TEST_ITERATION"
    # ... test steps
}

run_test
```

**UI detection pattern** (lines 119-138):
```bash
ui_content=$($ADB shell cat "$ui_dump" 2>/dev/null | tr -d '\r' || true)

if echo "$ui_content" | grep -qi "安全验证"; then
    ui_result="SUCCESS"
elif echo "$ui_content" | grep -qi "拖动箭头填充拼图"; then
    ui_result="SUCCESS"
elif echo "$ui_content" | grep -qiE "(LoadFail|加载失败|loadfail)"; then
    ui_result="LOADFAIL"
elif echo "$ui_content" | grep -qi "验证失败.*请重试"; then
    ui_result="VERIFY_FAIL"
fi
```

---

### `honor_test_utils.sh` (test utility, request-response) — MODIFY EXISTING

**Analog:** `e2e_honor/lib/honor_test_utils.sh` (self)

**UI Automator coordinate extraction pattern** (lines 76-111):
```bash
launch_jd_app() {
    local ui_dump="/sdcard/window_dump.xml"
    $ADB shell uiautomator dump "$ui_dump" 2>/dev/null || true
    sleep 0.5

    local jd_found=false
    local jd_x=""
    local jd_y=""

    for keyword in "京东秒送" "京东" "秒送" "jingdong" "JD" "jd"; do
        local bounds
        bounds=$($ADB shell cat "$ui_dump" 2>/dev/null | grep -oiE "text=\"[^\"]*$keyword[^\"]*\"[^>]*bounds=\"\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]\"" | head -1)
        if [[ -n "$bounds" ]]; then
            local coords
            coords=$(echo "$bounds" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
            if [[ -n "$coords" ]]; then
                local x1 y1 x2 y2
                x1=$(echo "$coords" | grep -oE '\[[0-9]+' | head -1 | tr -d '[')
                y1=$(echo "$coords" | grep -oE ',[0-9]+\]' | head -1 | tr -d ',]')
                x2=$(echo "$coords" | grep -oE '\[[0-9]+' | tail -1 | tr -d '[')
                y2=$(echo "$coords" | grep -oE ',[0-9]+\]' | tail -1 | tr -d ',]')
                if [[ -n "$x1" && -n "$y1" && -n "$x2" && -n "$y2" ]]; then
                    jd_x=$(( (x1 + x2) / 2 ))
                    jd_y=$(( (y1 + y2) / 2 ))
                    jd_found=true
                    break
                fi
            fi
        fi
    done

    if [[ "$jd_found" == "true" ]]; then
        $ADB shell input tap $jd_x $jd_y
    fi
}
```

**Resource-id based element location pattern** (lines 163-180):
```bash
bounds=$($ADB shell cat "$ui_dump" 2>/dev/null | grep -oE 'resource-id="com\.jd\.mrd\.jingming:id/jd_phone_et"[^>]*bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' | head -1)
if [[ -n "$bounds" ]]; then
    local coords=$(echo "$bounds" | grep -oE '\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]')
    local x1=$(echo "$coords" | grep -oE '\[[0-9]+' | head -1 | tr -d '[')
    local y1=$(echo "$coords" | grep -oE ',[0-9]+\]' | head -1 | tr -d ',]')
    local x2=$(echo "$coords" | grep -oE '\[[0-9]+' | tail -1 | tr -d '[')
    local y2=$(echo "$coords" | grep -oE ',[0-9]+\]' | tail -1 | tr -d ',]')
    if [[ -n "$x1" && -n "$y1" && -n "$x2" && -n "$y2" ]]; then
        input_x=$(( (x1 + x2) / 2 ))
        input_y=$(( (y1 + y2) / 2 ))
        input_found=true
    fi
fi
```

---

## Shared Patterns

### Proxy Class Structure
**Source:** `ClassInvocationStub.java` (lines 15-68)
**Apply to:** All new proxy classes (`ProcessBuilderProxy.java`)
```java
public abstract class ClassInvocationStub implements InvocationHandler, IInjectHook {
    private final Map<String, MethodHook> mMethodHookMap = new HashMap<>();
    private Object mBase;
    private Object mProxyInvocation;

    protected abstract Object getWho();
    protected abstract void inject(Object baseInvocation, Object proxyInvocation);
    protected void onBindMethod() { }

    @Override
    public void injectHook() {
        mBase = getWho();
        if (mBase == null) { return; }
        mProxyInvocation = Proxy.newProxyInstance(mBase.getClass().getClassLoader(),
            MethodParameterUtils.getAllInterface(mBase.getClass()), this);
        inject(mBase, mProxyInvocation);
        onBindMethod();
        // Annotation scanning...
    }
}
```

### MethodHook Pattern
**Source:** `MethodHook.java` (lines 8-26)
**Apply to:** All proxy method implementations
```java
public abstract class MethodHook {
    protected String getMethodName() { return null; }
    protected Object afterHook(Object result) throws Throwable { return result; }
    protected Object beforeHook(Object who, Method method, Object[] args) throws Throwable { return null; }
    protected abstract Object hook(Object who, Method method, Object[] args) throws Throwable;
    protected boolean isEnable() { return BlackBoxCore.get().isBlackProcess(); }
}
```

### @ProxyMethod Annotation
**Source:** `IPackageManagerProxy.java` (lines 91-104)
**Apply to:** All proxy method inner classes
```java
@ProxyMethod("resolveIntent")
public static class ResolveIntent extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        Intent intent = (Intent) args[0];
        String resolvedType = (String) args[1];
        int flags = MethodParameterUtils.toInt(args[2]);
        ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveIntent(...);
        if (resolveInfo != null) { return resolveInfo; }
        return method.invoke(who, args);
    }
}
```

### Slog Logging
**Source:** `Slog.java` (convention across all proxy files)
**Apply to:** All Java proxy and service files
```java
import top.niunaijun.blackbox.utils.Slog;
// Usage:
Slog.d(TAG, "message");
Slog.w(TAG, "message", exception);
Slog.e(TAG, "message", exception);
```

### Native Hook Registration
**Source:** `BoxCore.cpp` (inferred from HookManager pattern)
**Apply to:** `ProcessHook.cpp`
New native hooks must be initialized from the JNI bridge. Follow the pattern in `FileSystemHook::init()` which is called from the main native initialization path.

### E2E Test Source Pattern
**Source:** `e2e_honor/tests/jd_captcha.sh` (lines 17-29)
**Apply to:** All e2e test scripts
```bash
if [[ -n "${E2E_HONOR_DIR:-}" ]]; then
    __TEST_DIR="$E2E_HONOR_DIR/tests"
else
    __TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" 2>/dev/null && pwd)"
fi
source "$__TEST_DIR/../lib/utils.sh"
source "$__TEST_DIR/../lib/honor_test_utils.sh"
SCRIPT_DIR="$__TEST_DIR"
unset __TEST_DIR
```

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| None | — | — | All files have direct analogs in the codebase |

## Metadata

**Analog search scope:**
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/` (85 Java files)
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/` (5 Java files)
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/libcore/` (1 Java file)
- `Bcore/src/main/cpp/Hook/` (10 C++ files)
- `e2e_honor/` (5 shell scripts)

**Files scanned:** 15 (key analogs read in detail)
**Pattern extraction date:** 2026-05-31

### Key Patterns Summary for Planner

1. **All Java proxies extend `ClassInvocationStub` or `BinderInvocationStub`**, use `@ProxyMethod` annotation on inner `MethodHook` subclasses, and are registered in `HookManager.addInjector()`.

2. **Intent query methods** (`queryIntentActivities`, `queryIntentServices`) return `ParceledListSliceCompat.create(list)` on Android N+ and raw `List` on older versions. Follow the exact pattern from `QueryBroadcastReceivers`.

3. **Host package filtering** uses `BlackBoxCore.getHostPkg()` and `Iterator.remove()` pattern already established in `GetInstalledPackages` and `GetInstalledApplications`.

4. **Native file system hooks** use `xdl_open("libc.so")` + `xdl_sym()` to find original functions, then intercept with custom implementations that filter by `strstr(pathname, "pattern")`.

5. **E2E tests** source `utils.sh` and `honor_test_utils.sh` with the `E2E_HONOR_DIR` environment variable pattern, use `uiautomator dump` for dynamic element location, and extract coordinates via grep on bounds attributes.

6. **IO redirection** for `/proc/self/cmdline` is already handled in `IOCore.proc()` — the planner should extend this pattern to cover additional proc files if needed.
