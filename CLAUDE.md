# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

NewBlackbox is an Android virtual engine that clones and runs apps in isolated sandboxed environments. It works on Android 5.0–15+ without root by hooking system services at both the Java and native layers.

## Module Structure

| Module | Type | Purpose |
|--------|------|---------|
| `app` | Android Application | UI layer (Kotlin). Activities, fragments, view models, and user-facing settings. Package: `com.zhirang.zhanghaoguanjia` |
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

App APKs are written to `app/build/outputs/apk/<buildType>/` with naming pattern `zhanghaoguanjia_${versionName}_${abi}-${buildType}.apk`.
Engine APKs are written to `Bcore/build/outputs/apk/<buildType>/` with naming pattern `FxEngine_${versionName}_${buildType}.apk`.

### Release Checklist

Use this checklist for every public release.

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# 1. Pick a monotonically increasing versionCode in root build.gradle.
# Check the server first so app/engine versionCode is higher than active engine_versions.

# 2. Build the signed release APKs.
./gradlew :app:assembleRelease --no-daemon

# 3. Verify app and engine package metadata.
AAPT=/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/aapt
$AAPT dump badging app/build/outputs/apk/release/zhanghaoguanjia_${VERSION_NAME}_universal-release.apk | sed -n '1,4p'
$AAPT dump badging Bcore/build/outputs/apk/release/FxEngine_${VERSION_NAME}_release.apk | sed -n '1,4p'

# 4. Verify both APK signatures. Engine must verify before upload.
APKSIGNER=/opt/homebrew/share/android-commandlinetools/build-tools/35.0.0/apksigner
$APKSIGNER verify --verbose --print-certs app/build/outputs/apk/release/zhanghaoguanjia_${VERSION_NAME}_universal-release.apk
$APKSIGNER verify --verbose --print-certs Bcore/build/outputs/apk/release/FxEngine_${VERSION_NAME}_release.apk

# 5. Verify the app-embedded engine is exactly the engine release APK.
shasum -a 256 \
  Bcore/build/outputs/apk/release/FxEngine_${VERSION_NAME}_release.apk \
  app/src/main/assets/engine/engine-base.apk
```

Release verification must include:
- Install the universal release APK on an emulator/device with `adb install -r -d`.
- Launch with `adb shell monkey -p com.zhirang.zhanghaoguanjia -c android.intent.category.LAUNCHER 1`.
- Scan logcat for `FATAL EXCEPTION`, `AndroidRuntime`, `ClassCastException`, and `Missing type parameter`.
- If the installed engine is older, confirm the app creates a `PackageInstaller` session, opens the system engine update dialog, and the engine package upgrades to the new `versionCode` after confirmation.

When uploading/registering an engine release:
- Upload the engine APK through the admin `/api/files/engine-packages` file service so local/OBS storage behavior stays unified.
- Store a checksum matching the uploaded APK. The app supports MD5 (32 hex chars) and SHA-256 (64 hex chars).
- Verify the public download URL returns the same checksum and `apksigner verify` passes on the downloaded APK.
- Verify `/api/engine-versions?available=true` returns the new version first.

For any manual MySQL writes that include text, always force UTF-8 on the client/session:

```bash
docker exec -i admin-duodian-mysql-1 sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" duodian_admin'
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Do not use a plain `mysql` session for Chinese text. The server and tables are `utf8mb4`, but the client/session charset can still corrupt manually inserted strings.

### Admin Deployment

- Start or redeploy the management backend/frontend only through `admin/deploy.sh`.
- Do not manually run root-level compose commands for the management backend.
- The admin stack exposes a single frontend/nginx entrypoint, defaulting to port `8006`; backend APIs continue to be reached through the frontend nginx `/api` proxy.

```bash
./admin/deploy.sh
```

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
| Modify app list/install logic | `app/src/main/java/com/zhirang/zhanghaoguanjia/data/AppsRepository.kt` |
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

### E2E Automated Testing (via ADB)

The project includes a shell-based E2E test suite in the [`e2e/`](e2e/) directory that automates build, install, launch, and UI validation via `adb`.

#### Quick Start

```bash
# Run all tests (uses existing APK if available)
./e2e/run.sh

# Build + reinstall + run all tests
./e2e/run.sh --build --reinstall

# Run only smoke test
./e2e/run.sh smoke

# Run Phase 1 single-instance test with clean data
./e2e/run.sh --clean phase1
```

#### E2E Test Scripts

| Script | Command | Coverage |
|--------|---------|----------|
| Smoke test | `./e2e/run.sh smoke` | Build, install, launch, no crashes, screenshots of main/settings tabs |
| Phase 1 single-instance | `./e2e/run.sh phase1` | Toggle ON/OFF, setting persistence, process behavior, ActivityStack cleanup |

#### E2E Test Structure

```
e2e/
├── run.sh              # Main runner (argument parsing, report generation)
├── lib/utils.sh        # Shared ADB helpers: install, screenshot, tap, assert_log_contains
├── tests/
│   ├── smoke.sh
│   └── phase1_single_instance.sh
├── screenshots/        # Auto-captured PNGs per test step
├── logs/               # Logcat dumps per test run
└── README.md           # Full guide for adding new tests
```

#### E2E Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ADB` | Auto-detected | Path to `adb` executable |
| `JAVA_HOME` | Auto-detected | JDK 21 for Gradle build |

#### Adding a New E2E Test

1. Create `e2e/tests/my_feature.sh`
2. Define `run_test()` — use helpers from `lib/utils.sh`
3. Run: `./e2e/run.sh my_feature`

See [`e2e/README.md`](e2e/README.md) for the full template and coordinate reference.

### Manual Testing

The project is also validated through:
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
