# Codebase Structure

**Analysis Date:** 2026-05-30

## Directory Layout

```
[project-root]/
├── app/                        # Android Application module (UI layer, Kotlin)
│   ├── src/main/java/...       # Activities, Fragments, ViewModels, Repositories
│   ├── src/main/res/           # Layouts, drawables, values
│   ├── src/test/java/          # Unit tests (minimal)
│   └── build.gradle            # App module build config
├── Bcore/                      # Core virtualization engine (Java + C++)
│   ├── src/main/java/          # Java source
│   │   ├── top/niunaijun/blackbox/           # Core API and entry points
│   │   ├── top/niunaijun/blackbox/app/       # App thread binding
│   │   ├── top/niunaijun/blackbox/core/      # System services and env
│   │   ├── top/niunaijun/blackbox/entity/    # Parcelable data classes
│   │   ├── top/niunaijun/blackbox/fake/      # Hook system and proxies
│   │   ├── top/niunaijun/blackbox/proxy/     # Manifest proxy components
│   │   ├── top/niunaijun/blackbox/utils/     # Utilities and compat
│   │   └── black/android/...   # Generated reflection wrappers (220+ files)
│   ├── src/main/aidl/          # AIDL interfaces for IPC
│   ├── src/main/cpp/           # Native C++ hooks (Dobby, xDL)
│   ├── src/main/res/           # Bcore resources (layouts, drawables)
│   └── build.gradle            # Bcore library build config
├── black-reflection/           # Reflection annotation library
│   └── src/main/java/...       # @BClass, @BMethod, @BField annotations
├── compiler/                   # Annotation processor for black-reflection
│   └── src/main/java/...       # Generates BR* classes at compile time
├── e2e/                        # End-to-end shell test suite
│   ├── run.sh                  # Main test runner
│   ├── lib/utils.sh            # ADB helper functions
│   └── tests/                  # smoke.sh, phase1_single_instance.sh
├── docs/                       # Documentation
├── assets/                     # Static assets
├── build.gradle                # Root build script
├── settings.gradle             # Project settings + Aliyun Maven mirrors
├── gradle.properties           # Gradle properties
└── local.properties            # SDK/NDK paths (not committed)
```

## Directory Purposes

**`app/`:**
- Purpose: User-facing Android application
- Contains: Kotlin Activities, Fragments, ViewModels, Adapters, Repositories
- Key files: `App.kt`, `MainActivity.kt`, `AppsRepository.kt`, `BlackBoxLoader.kt`
- Package: `top.niunaijun.blackboxa`

**`Bcore/src/main/java/top/niunaijun/blackbox/`:**
- Purpose: Main API entry point and core engine
- Contains: `BlackBoxCore.java` (2277 lines), process detection, service access

**`Bcore/src/main/java/top/niunaijun/blackbox/app/`:**
- Purpose: Virtual app component binding
- Contains: `BActivityThread.java`, `LauncherActivity.java`, dispatchers
- Key files: `BActivityThread.java` (1314 lines), `AppServiceDispatcher.java`, `AppJobServiceDispatcher.java`

**`Bcore/src/main/java/top/niunaijun/blackbox/core/`:**
- Purpose: System services and environment setup
- Contains: `BlackBoxSystem.java`, `ServiceManager.java`, `DaemonService.java`, `SystemCallProvider.java`
- Subdirs: `system/am/` (activity management), `system/pm/` (package management), `system/user/` (user management), `system/accounts/`, `system/location/`, `system/notification/`, `system/os/`

**`Bcore/src/main/java/top/niunaijun/blackbox/entity/`:**
- Purpose: Data transfer objects and Parcelables
- Contains: `AppConfig.java`, `ServiceRecord.java`, `JobRecord.java`, `pm/InstallResult.java`, `am/RunningAppProcessInfo.java`, `location/BLocation.java`

**`Bcore/src/main/java/top/niunaijun/blackbox/fake/`:**
- Purpose: Hook system for intercepting Android system services
- Contains:
  - `hook/` - `HookManager.java`, `BinderInvocationStub.java`, `ClassInvocationStub.java`, `MethodHook.java`, `IInjectHook.java`
  - `service/` - 70+ proxy classes (IPackageManagerProxy, IActivityManagerProxy, etc.)
  - `service/base/` - `PkgMethodProxy.java`, `UidMethodProxy.java`, `ValueMethodProxy.java`
  - `service/context/` - ContentProvider stubs
  - `frameworks/` - Client-side service accessors (BPackageManager, BActivityManager, etc.)
  - `delegate/` - `AppInstrumentation.java`, `ContentProviderDelegate.java`, `ServiceConnectionDelegate.java`
  - `provider/` - `FileProvider.java`, `FileProviderHandler.java`

**`Bcore/src/main/java/top/niunaijun/blackbox/proxy/`:**
- Purpose: Pre-declared AndroidManifest proxy components
- Contains: `ProxyActivity.java` (P0-P49), `ProxyService.java`, `ProxyContentProvider.java`, `ProxyBroadcastReceiver.java`, `ProxyJobService.java`, `ProxyVpnService.java`, `ProxyManifest.java`
- Subdirs: `record/` - `ProxyActivityRecord.java`, `ProxyServiceRecord.java`, `ProxyBroadcastRecord.java`, `ProxyPendingRecord.java`

**`Bcore/src/main/java/top/niunaijun/blackbox/utils/`:**
- Purpose: Utilities, compatibility shims, crash prevention
- Contains: `Reflector.java`, `FileUtils.java`, `Slog.java`, `ComponentUtils.java`, `compat/` package with API-level shims

**`Bcore/src/main/java/black/android/`:**
- Purpose: Generated reflection wrappers for hidden Android APIs
- Contains: 220+ `BR*` classes generated by compiler APT
- Pattern: `BRActivityThread.java`, `BRPackageManager.java`, etc.
- Generated: Yes (by compiler module)
- Committed: Yes

**`Bcore/src/main/aidl/`:**
- Purpose: AIDL interfaces for cross-process communication
- Contains: 40+ AIDL files for system services and Android framework interfaces
- Key files: `IBActivityThread.aidl`, `IBPackageManagerService.aidl`, `IBActivityManagerService.aidl`

**`Bcore/src/main/cpp/`:**
- Purpose: Native hooking infrastructure
- Contains:
  - `BoxCore.cpp` / `BoxCore.h` - Main JNI bridge
  - `Hook/` - `BinderHook.cpp`, `FileSystemHook.cpp`, `DexFileHook.cpp`, `RuntimeHook.cpp`, `VMClassLoaderHook.cpp`, `UnixFileSystemHook.cpp`
  - `JniHook/` - ART method hooking infrastructure
  - `Utils/` - `AntiDetection.cpp`, `VirtualSpoof.cpp`, `elf_util.cpp`, `HexDump.cpp`
  - `Dobby/` - Dobby hooking library headers
  - `xdl/` - xDL dynamic symbol resolution headers
  - `Android.mk` / `Application.mk` - NDK build scripts

**`black-reflection/src/main/java/`:**
- Purpose: Annotation definitions for compile-time reflection generation
- Contains: `BlackReflection.java`, `annotation/BClass.java`, `annotation/BMethod.java`, `annotation/BField.java`, etc.

**`compiler/src/main/java/`:**
- Purpose: Annotation processor that generates `BR*` reflection classes
- Contains: `BlackReflectionProcessor.java`, `BlackReflectionInfo.java`, proxy generators
- Depends on: `black-reflection`, `javapoet`, `auto-service`

**`e2e/`:**
- Purpose: Shell-based end-to-end testing via ADB
- Contains: `run.sh`, `lib/utils.sh`, `tests/smoke.sh`, `tests/phase1_single_instance.sh`
- Generated dirs: `screenshots/`, `logs/` (not committed)

## Key File Locations

**Entry Points:**
- `app/src/main/java/top/niunaijun/blackboxa/app/App.kt` - Application attachBaseContext entry
- `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java` - Core engine entry point
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/DaemonService.java` - Daemon process entry
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/SystemCallProvider.java` - ContentProvider bridge for service access

**Configuration:**
- `build.gradle` - Root build script (compileSdk 35, targetSdk 28, minSdk 21, Java 21)
- `settings.gradle` - Module list + Aliyun Maven mirrors
- `app/build.gradle` - App module (viewBinding, Kotlin 21, ABI splits)
- `Bcore/build.gradle` - Library module (aidl, prefab, NDK build, lint disabled)
- `black-reflection/build.gradle.kts` - Java library (sourceCompatibility 17)
- `compiler/build.gradle.kts` - Annotation processor (sourceCompatibility 17)
- `Bcore/src/main/cpp/Android.mk` - Native build configuration
- `Bcore/src/main/cpp/Application.mk` - NDK application config

**Core Logic:**
- `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java` - Main API facade
- `Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java` - Virtual app thread binding
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/hook/HookManager.java` - Hook registration and injection
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java` - Virtual package management
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/BActivityManagerService.java` - Virtual activity management
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BProcessManagerService.java` - Process lifecycle

**Testing:**
- `Bcore/src/test/java/` - Minimal unit tests (JarManagerTest)
- `e2e/run.sh` - E2E test runner
- `e2e/tests/smoke.sh` - Smoke test (build, install, launch)
- `e2e/tests/phase1_single_instance.sh` - Single-instance mode test

## Naming Conventions

**Files:**
- Java: PascalCase (e.g., `BlackBoxCore.java`, `BActivityThread.java`)
- Kotlin: PascalCase for classes (e.g., `MainActivity.kt`, `AppsRepository.kt`)
- Native: PascalCase for headers (e.g., `BoxCore.h`), camelCase for implementation (e.g., `BoxCore.cpp`)

**Directories:**
- Lowercase with hyphens for module names: `black-reflection/`
- Lowercase for packages: `top/niunaijun/blackbox/`

**Generated Classes:**
- `BR` prefix for black-reflection generated classes: `BRActivityThread`, `BRPackageManager`
- `B` prefix for BlackBox virtual services: `BPackageManagerService`, `BActivityManagerService`
- `IB` prefix for AIDL interfaces: `IBPackageManagerService`, `IBActivityManagerService`

## Where to Add New Code

**New System Service Hook:**
- Implementation: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/MyServiceProxy.java`
- Register in: `Bcore/src/main/java/top/niunaijun/blackbox/fake/hook/HookManager.java` (`addInjector()`)
- Extend: `BinderInvocationStub` for Binder services, `ClassInvocationStub` for class-based

**New Virtual System Service:**
- Implementation: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/myfeature/BMyService.java`
- AIDL interface: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/myfeature/IBMyService.aidl`
- Register in: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/ServiceManager.java`
- Client accessor: `Bcore/src/main/java/top/niunaijun/blackbox/fake/frameworks/BMyManager.java`

**New Native Hook:**
- Implementation: `Bcore/src/main/cpp/Hook/MyHook.cpp` + `MyHook.h`
- Register in: `Bcore/src/main/cpp/BoxCore.cpp` (`nativeHook()` function)
- Follow pattern of existing hooks: `BaseHook::init(env)` in constructor

**New UI Feature:**
- Activity/Fragment: `app/src/main/java/top/niunaijun/blackboxa/view/myfeature/`
- ViewModel: `app/src/main/java/top/niunaijun/blackboxa/view/myfeature/MyViewModel.kt`
- Layout: `app/src/main/res/layout/activity_myfeature.xml`
- Repository (if needed): `app/src/main/java/top/niunaijun/blackboxa/data/MyRepository.kt`

**New Reflection Wrapper:**
- Add `@BClass` annotation to target class in Bcore source
- Rebuild to trigger compiler APT generation
- Generated file appears in: `Bcore/src/main/java/black/android/...`

**New E2E Test:**
- Create: `e2e/tests/my_feature.sh`
- Define `run_test()` function using helpers from `e2e/lib/utils.sh`
- Run: `./e2e/run.sh my_feature`

## Special Directories

**`Bcore/src/main/java/black/android/`:**
- Purpose: Contains generated reflection wrapper classes (BR*)
- Generated: Yes (by compiler APT at build time)
- Committed: Yes (checked into repo)
- Note: If reflection helpers are missing after adding new `@BClass` annotations, rebuild the project

**`Bcore/src/main/cpp/Dobby/`:**
- Purpose: Dobby hooking library headers
- Generated: No (vendored)
- Committed: Yes

**`Bcore/src/main/cpp/xdl/`:**
- Purpose: xDL dynamic symbol resolution headers
- Generated: No (vendored)
- Committed: Yes

**`app/build/outputs/apk/`:**
- Purpose: Build output directory for APKs
- Generated: Yes
- Committed: No (in .gitignore)
- Naming: `BlackBox_${versionName}_${abi}-debug.apk`

**`e2e/screenshots/` and `e2e/logs/`:**
- Purpose: E2E test artifacts
- Generated: Yes (by e2e/run.sh)
- Committed: No

---

*Structure analysis: 2026-05-30*
