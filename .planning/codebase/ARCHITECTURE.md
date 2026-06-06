<!-- refreshed: 2026-05-30 -->
# Architecture

**Analysis Date:** 2026-05-30

## System Overview

NewBlackbox is an Android virtual engine that clones and runs apps in isolated sandboxed environments. It works on Android 5.0-15+ without root by hooking system services at both the Java and native layers.

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                         UI Layer (app module)                                │
│  MainActivity / AppsFragment / Settings / FakeLocation / GmsManager         │
│  `app/src/main/java/top/niunaijun/blackboxa/view/`                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                    BlackBoxCore API (ClientConfiguration)                    │
│  `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`             │
├──────────────────┬──────────────────────────────┬───────────────────────────┤
│   Fake Framework │   Virtual System Services    │   Process / App Thread    │
│   BPackageManager│   BActivityManagerService    │   BActivityThread         │
│   BActivityManager│  BPackageManagerService     │   AppServiceDispatcher    │
│   BUserManager   │   BProcessManagerService     │   AppJobServiceDispatcher │
│   `fake/frameworks/` │ `core/system/`           │   `app/`                  │
├──────────────────┴──────────────────────────────┴───────────────────────────┤
│                      Service Hook Layer (Binder IPC)                         │
│  IPackageManagerProxy / IActivityManagerProxy / IWindowManagerProxy ...     │
│  BinderInvocationStub / ClassInvocationStub / MethodHook                    │
│  `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/`                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                    black-reflection + compiler (APT)                         │
│  Generated BR* classes for hidden Android API access                        │
│  `Bcore/src/main/java/black/android/...`                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                         Native Layer (C++/NDK)                               │
│  BoxCore.cpp / BinderHook / FileSystemHook / DexFileHook / RuntimeHook      │
│  Dobby (hooking) + xDL (symbol resolution)                                  │
│  `Bcore/src/main/cpp/`                                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

| Component | Responsibility | File |
|-----------|----------------|------|
| BlackBoxCore | Singleton entry point, process type detection, service initialization, lifecycle callbacks | `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java` |
| BActivityThread | Binds virtual app components to host process, application creation, provider installation | `Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java` |
| HookManager | Registers and injects all system service hooks, critical hook recovery | `Bcore/src/main/java/top/niunaijun/blackbox/fake/hook/HookManager.java` |
| BPackageManagerService | Virtual APK metadata, install/uninstall, component resolution | `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java` |
| BActivityManagerService | Activity lifecycle, service management, broadcast dispatch | `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/BActivityManagerService.java` |
| BProcessManagerService | Spawns isolated processes, process records, death monitoring | `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BProcessManagerService.java` |
| IOCore | File system path redirection for sandboxed storage | `Bcore/src/main/java/top/niunaijun/blackbox/core/IOCore.java` |
| NativeCore | JNI bridge to native hooks, UID resolution, DEX loading | `Bcore/src/main/java/top/niunaijun/blackbox/core/NativeCore.java` |
| BlackBoxSystem | Initializes all system services on daemon startup | `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BlackBoxSystem.java` |

## Pattern Overview

**Overall:** Virtual Machine / Container Pattern with Proxy/Stub Interception

**Key Characteristics:**
- **Dual-process architecture**: Main process (UI) + Server process (daemon with system services) + BAppClient processes (sandboxed apps)
- **Binder IPC interception**: Every system service call is intercepted via dynamic proxy and redirected to virtual implementations
- **Reflection-heavy**: Uses annotation-processed reflection wrappers (`black.*` package) to access hidden Android APIs across API levels
- **Native hooking**: C++ layer hooks ART methods, file system calls, and Binder transactions
- **Proxy component pattern**: 50 pre-declared proxy Activity/Service/Provider subclasses handle component delegation

## Layers

**UI Layer (app module):**
- Purpose: User-facing interface for installing, launching, and managing virtual apps
- Location: `app/src/main/java/top/niunaijun/blackboxa/`
- Contains: Activities, Fragments, ViewModels, Adapters (Kotlin)
- Depends on: Bcore module
- Used by: End user

**Core API Layer:**
- Purpose: Public API facade exposed to the UI layer
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`
- Contains: Install/launch/uninstall APIs, process type detection, lifecycle callbacks
- Depends on: Fake frameworks, system services
- Used by: UI layer, virtual apps

**Fake Framework Layer:**
- Purpose: Client-side stubs that communicate with virtual system services via Binder
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/fake/frameworks/`
- Contains: BPackageManager, BActivityManager, BUserManager, BStorageManager, etc.
- Depends on: ServiceManager, AIDL interfaces
- Used by: BlackBoxCore, proxy classes, virtual apps

**Virtual System Services Layer:**
- Purpose: Server-side implementations of Android system services (runs in daemon process)
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/`
- Contains: BPackageManagerService, BActivityManagerService, BProcessManagerService, BUserManagerService, etc.
- Depends on: BEnvironment, entity classes, AIDL interfaces
- Used by: Fake frameworks via Binder IPC

**Service Hook Layer:**
- Purpose: Intercepts real Android system service calls and redirects them
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/`
- Contains: 70+ proxy classes (IPackageManagerProxy, IActivityManagerProxy, etc.)
- Depends on: black-reflection generated classes, HookManager
- Used by: Virtual apps running in BAppClient processes

**Reflection Abstraction Layer:**
- Purpose: Type-safe access to hidden Android APIs across all API levels
- Location: `Bcore/src/main/java/black/android/...`
- Contains: 220+ generated BR* classes
- Depends on: compiler annotation processor
- Used by: All Bcore components

**Native Layer:**
- Purpose: Low-level hooking of ART, file system, Binder, and runtime
- Location: `Bcore/src/main/cpp/`
- Contains: BoxCore.cpp, Hook modules, JniHook, Utils
- Depends on: Dobby, xDL
- Used by: NativeCore Java bridge

## Data Flow

### Primary Request Path (App Launch)

1. **UI triggers launch** (`app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt:launchApk()`)
2. **BlackBoxCore.launchApk()** checks single-instance mode, resolves intent (`Bcore/.../BlackBoxCore.java:1099`)
3. **BActivityManagerService.startActivity()** finds or creates process, delegates to ActivityStack (`Bcore/.../am/BActivityManagerService.java:336`)
4. **ActivityStack.startActivityLocked()** resolves target, creates ProxyActivityRecord, starts proxy Activity (`Bcore/.../am/ActivityStack.java:97`)
5. **ProxyActivity (host manifest)** receives intent, extracts real target, re-starts with real Activity info (`Bcore/.../proxy/ProxyActivity.java`)
6. **HCallbackProxy.handleMessage()** intercepts LAUNCH_ACTIVITY/EXECUTE_TRANSACTION, swaps intent back to real target (`Bcore/.../fake/service/HCallbackProxy.java:71`)
7. **BActivityThread.bindApplication()** creates Application, installs providers, calls onCreate (`Bcore/.../app/BActivityThread.java:316`)

### Service Hook Interception Path

1. Virtual app calls `getPackageManager()`
2. **IPackageManagerProxy** (dynamic proxy) intercepts the Binder call (`Bcore/.../fake/service/IPackageManagerProxy.java`)
3. Proxy checks `MethodHook` registry for the method name
4. Hook implementation queries `BPackageManagerService` via Binder IPC
5. Result is transformed (package names, UIDs redirected) and returned to caller

### Process Spawn Path

1. **BProcessManagerService.startProcessLocked()** allocates bpid, creates ProcessRecord (`Bcore/.../BProcessManagerService.java:49`)
2. **initAppProcessL()** sends init config via ContentProvider call to proxy authority (`Bcore/.../BProcessManagerService.java:149`)
3. **SystemCallProvider** in target process receives call, returns IBActivityThread binder (`Bcore/.../system/SystemCallProvider.java`)
4. **ProcessRecord.attachClientL()** links death recipient, stores app thread reference
5. Target process runs as `:pN` sub-process with virtual app bound

## Key Abstractions

**BinderInvocationStub:**
- Purpose: Base class for all Binder-based service proxies
- Examples: `IPackageManagerProxy.java`, `IActivityManagerProxy.java`
- Pattern: Extends `BinderInvocationStub`, implements `IBinder`, replaces system service cache entry

**ClassInvocationStub:**
- Purpose: Base class for non-Binder class proxies using Java dynamic proxy
- Examples: `IActivityManagerProxy.java` (for ActivityManager), `AppInstrumentation.java`
- Pattern: Uses `Proxy.newProxyInstance()` to intercept interface methods

**MethodHook + @ProxyMethod:**
- Purpose: Declarative method interception using inner classes
- Examples: Inside `IPackageManagerProxy.java` - inner classes annotated with `@ProxyMethod("getPackageInfo")`
- Pattern: Inner class extends `MethodHook`, annotated with target method name, auto-registered by `ScanClass`

**BlackManager&lt;Service&gt;:**
- Purpose: Generic base for fake framework clients to acquire remote Binder services
- Examples: `BPackageManager extends BlackManager<IBPackageManagerService>`
- Pattern: Caches service reference, handles death/recovery, rate-limits creation

**ProcessRecord:**
- Purpose: Represents a running virtual app process
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/ProcessRecord.java`
- Pattern: Holds ApplicationInfo, bpid/buid, IBActivityThread binder, death monitoring

**UserSpace:**
- Purpose: Per-user isolation container for activity and service state
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/UserSpace.java`
- Pattern: Contains ActivityStack, ActiveServices, PendingIntentRecord map

## Entry Points

**Application Entry (Main Process):**
- Location: `app/src/main/java/top/niunaijun/blackboxa/app/App.kt`
- Triggers: Android Application attachBaseContext
- Responsibilities: Initializes BlackBoxCore, attaches base context, registers lifecycle callbacks

**BlackBoxCore Initialization:**
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`
- Triggers: `doAttachBaseContext()` from App.kt
- Responsibilities: Detects process type, initializes hooks, starts DaemonService, installs crash fixes

**Daemon Service Entry (Server Process):**
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/DaemonService.java`
- Triggers: startService / startForegroundService
- Responsibilities: Keeps server process alive, initializes BlackBoxSystem via SystemCallProvider

**System Service Initialization:**
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BlackBoxSystem.java`
- Triggers: SystemCallProvider.onCreate -> BlackBoxSystem.startup()
- Responsibilities: Initializes all virtual system services (PMS, AMS, UMS, etc.)

**Virtual App Process Entry:**
- Location: `Bcore/src/main/java/top/niunaijun/blackbox/proxy/ProxyActivity.java`
- Triggers: ActivityManager starts proxy Activity in `:pN` process
- Responsibilities: Delegates to real virtual app Activity via HCallbackProxy intent swap

## Architectural Constraints

- **Threading:** Single-threaded UI with Handler-based dispatch. BProcessManagerService uses synchronized blocks on `mProcessLock`. ActivityStack uses synchronized on `mTasks`.
- **Global state:** `BlackBoxCore` singleton (`sBlackBoxCore`), `HookManager` singleton (`sHookManager`), all system services are singletons (`sService`).
- **Process model:** Three process types detected by name: Main (host package), Server (`:black_box_service_name`), BAppClient (`:pN` where N is 0-49).
- **Proxy count limit:** `ProxyManifest.FREE_COUNT = 50` maximum concurrent virtual app processes.
- **Circular imports:** Not explicitly detected, but `BlackBoxCore` coordinates all layers and must be initialized before any service access.
- **API level branching:** Extensive `BuildCompat.isX()` checks throughout hook registration and reflection usage.

## Anti-Patterns

### God Object

**What happens:** `BlackBoxCore.java` is 2277 lines and contains process management, service initialization, logging, crash fixes, VPN setup, device info logging, and log sending.
**Why it's wrong:** Violates single responsibility; changes to unrelated features (e.g., log sending) require modifying the core engine file.
**Do this instead:** Extract `LogSender`, `DeviceInfoLogger`, `VpnInitializer`, `CrashFixInstaller` into separate classes in a `core/init/` package.

### Deep Nesting in Fallback Logic

**What happens:** `BlackBoxCore.getServiceInternal()` and `areServicesAvailable()` contain deeply nested try-catch blocks with multiple fallback paths and retry loops.
**Why it's wrong:** Makes service initialization logic hard to reason about and test; fallback creation currently returns null (stubbed out).
**Do this instead:** Extract a `ServiceResolver` or `ServiceInitializer` class with clear state machine (uninitialized -> starting -> fallback -> ready).

### Static Singletons Everywhere

**What happens:** Nearly every service class uses static singleton pattern (`sService = new XxxService()`), making unit testing and process isolation difficult.
**Why it's wrong:** Tight coupling, impossible to mock for testing, risk of initialization order bugs.
**Do this instead:** Use dependency injection or at least a `ServiceLocator` pattern with lazy initialization guards.

## Error Handling

**Strategy:** Defensive programming with extensive try-catch blocks and Slog logging. Most methods catch `Exception` and log rather than propagate.

**Patterns:**
- Service creation failures are tracked with `AtomicBoolean mServiceCreationFailed` and retry timeouts in `BlackManager`
- Hook injection failures are logged and optionally recovered in `HookManager.handleHookError()`
- Process death is monitored via `IBinder.linkToDeath()` in `BProcessManagerService.attachClientL()`
- Application creation has multiple fallback attempts in `BActivityThread.handleBindApplication()`

## Cross-Cutting Concerns

**Logging:** `Slog` utility wraps Android Log with tag formatting. Extensive debug logging throughout all layers.
**Validation:** Input validation scattered; package name blacklist check in `BlackBoxCore.installPackageAsUser()` prevents self-cloning.
**Authentication:** No explicit auth layer; relies on Android UID isolation. `NativeCore.getCallingUid()` resolves UIDs for sandboxed apps.
**Crash Prevention:** Multiple static crash fix classes installed at class load time (`SimpleCrashFix`, `StackTraceFilter`, `SocialMediaAppCrashPrevention`, `DexCrashPrevention`, `NativeCrashPrevention`, `CrashMonitor`).

---

*Architecture analysis: 2026-05-30*
