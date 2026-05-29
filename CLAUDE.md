# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NewBlackbox is an Android virtual engine that clones and runs apps in isolated sandboxed environments. It works on Android 5.0–15+ without root by hooking system services at both the Java and native layers.

## Module Structure

| Module | Type | Purpose |
|--------|------|---------|
| `app` | Android Application | UI layer (Kotlin). Activities, fragments, view models, and user-facing settings. Package: `top.niunaijun.blackboxa` |
| `Bcore` | Android Library | Core virtualization engine (Java + C++). Hooks system services, manages virtual packages/processes, and handles IPC. Package: `top.niunaijun.blackbox` |
| `black-reflection` | Java Library | Reflection utilities for accessing hidden Android APIs |
| `compiler` | Annotation Processor | Generates reflection helpers at compile time |

## Build Requirements

- **JDK**: 21 (Gradle 8.x + AGP 8.5.2 require it; black-reflection module specifically targets Java 21)
- **Android SDK**: 35 (compileSdk)
- **NDK**: 29.0.13846066 (set via `ndk.dir` in `local.properties`)
- **minSdk**: 21, **targetSdk**: 28

### Common Build Commands

```bash
# Use Java 21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Build debug APK (outputs universal + per-ABI APKs)
./gradlew :app:assembleDebug --no-daemon

# Build release APK
./gradlew :app:assembleRelease --no-daemon

# Clean
./gradlew clean --no-daemon
```

APKs are written to `app/build/outputs/apk/debug/` with naming pattern `BlackBox_${versionName}_${abi}-debug.apk`.

### Installing to Device

```bash
# Find adb (homebrew: android-commandlinetools)
ADB="/opt/homebrew/share/android-commandlinetools/platform-tools/adb"

# Check connected devices
$ADB devices

# Install (allow downgrade + replace existing)
$ADB install -r -d app/build/outputs/apk/debug/BlackBox_4.0.0_universal-debug.apk

# If install fails with VERSION_DOWNGRADE, -d flag handles it
# If install fails with conflicting signatures, uninstall first:
# $ADB uninstall top.niunaijun.blackbox
```

### One-Shot Build + Install

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
ADB="/Applications/wechatwebdevtools.app/Contents/Resources/bin/adb-macos/adb"
./gradlew :app:assembleDebug --no-daemon && $ADB install -r -d app/build/outputs/apk/debug/BlackBox_4.0.0_universal-debug.apk
```

## Architecture

### High-Level Flow

1. **App (`app` module)** presents the UI for installing/launching virtual apps.
2. **`BlackBoxCore`** (`Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`) is the main entry point for the engine. It initializes the hook system, sets up virtual environment paths, and exposes APIs for install/launch.
3. **`BActivityThread`** (`Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java`) binds virtual app components (activities, services, receivers) to the host process, replacing references to real package names with virtual ones.
4. **`FakeCore`** + **`HookManager`** initialize the service hooking layer. `FakeCore.init()` uses JNI (`ReflectCore`) to hook into `ActivityThread`.
5. **Native layer** (`Bcore/src/main/cpp/`) uses **Dobby** for function hooking and **xDL** for dynamic symbol resolution to intercept Binder calls, file system access, and runtime behavior.

### Hook System (Bcore)

The engine hooks Android system services by intercepting Binder IPC:

- **Proxy classes** live in `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/`. Each file (e.g., `IPackageManagerProxy.java`) extends `BinderInvocationStub` and defines `@ProxyMethod`-annotated inner classes that override system service methods.
- **Stub classes** in `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/context/providers/` handle ContentProvider interception.
- **`MethodHook`** is the base class for all method interception logic. Override `hook()` to replace behavior, `beforeHook()`/`afterHook()` for side effects.
- The `black/` package tree (`Bcore/src/main/java/black/...`) contains reflection-generated accessors for Android internal classes, produced by the `compiler` annotation processor.

### Native Layer (C++)

- **`BoxCore.cpp`** — Main JNI bridge; initializes all native hooks.
- **`Hook/`** — Individual hook modules: `BinderHook`, `FileSystemHook`, `DexFileHook`, `RuntimeHook`, `VMClassLoaderHook`.
- **`JniHook/`** — ART method hooking infrastructure.
- **`Utils/`** — Anti-detection helpers, virtual spoofing, ELF utilities.

Native hooks are built via `ndkBuild` using `Bcore/src/main/cpp/Android.mk`.

### Virtual Environment

- **Package management**: `BPackageManagerService` maintains virtual APK metadata in an internal database.
- **Process management**: `BProcessManagerService` spawns isolated processes for virtual apps.
- **User space**: Virtual users are managed by `BUserManagerService`; apps are installed per-user.
- **Storage**: Virtual apps get redirected data directories under the host app's private storage.

## Key Files for Common Tasks

| Task | Files |
|------|-------|
| Add system package whitelist | `Bcore/src/main/java/top/niunaijun/blackbox/core/env/AppSystemEnv.java` |
| Hook a new system service | Create class in `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/`, extend `BinderInvocationStub`, register in `HookManager` |
| Fix WebView/provider compatibility | `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IWebViewUpdateServiceProxy.java`, `WebViewFactoryProxy.java`, `NativeCore.java` |
| Modify app list/install logic | `app/src/main/java/top/niunaijun/blackboxa/data/AppsRepository.kt` |
| Change version | `build.gradle` (`versionCode`, `versionName`) |
| Native hook changes | `Bcore/src/main/cpp/Hook/*.cpp`, rebuild triggers NDK build automatically |

## Branch Strategy

- **`dev`** — Primary development branch. Contains latest features (VPN mode, log sender, Android 10 black-screen fix, removed Xposed). **Use this as the base for new feature branches.**
- **`main`** — Stable branch with select PR merges. Lacks `dev` features but has a few unique patches (e.g., `getExternalObbDir`).
- **`feature-hwp`** — Example feature branch: `dev` base + main patches + Honor/Huawei compatibility fixes.

When syncing patches across branches, cherry-pick in this order to minimize conflicts:
1. Main branch functional patches first (`d9af6d0`, `9c74477`)
2. Device-specific fixes on top

## Testing

There is minimal automated test coverage. The project is validated through:
- Manual APK installation and smoke testing on target devices
- `JarManagerTest.java` (basic jar loading verification)

No unit test runner commands are configured beyond standard Android instrumentation.

## Reference Documentation

- **`Docs.md`** — Contains detailed API documentation and usage examples for the BlackBoxCore API (installing packages, launching apps, GPS spoofing, GMS integration, etc.). Refer to this when adding new features that use the core engine APIs.

## Notes

- The project uses **Aliyun Maven mirrors** in `settings.gradle` for faster dependency resolution in China.
- `Bcore/build.gradle` enables `aidl` and `prefab` build features; AIDL interfaces are in `Bcore/src/main/aidl/`.
- Lint is configured to be non-blocking (`abortOnError false`) with many checks disabled.
- The `black-reflection` module's annotation processor (`compiler`) generates `BR*` reflection classes at compile time. If reflection helpers are missing, rebuild after adding new `@BClass` annotations.
