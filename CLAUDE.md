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

Phase 9 clone-auth release verification must also include:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Backend clone billing, token signing, soft-delete schema, and admin shop paging tests.
cd admin/backend && mvn test

# Engine local token verification tests.
cd ../.. && ./gradlew :Bcore:testReleaseUnitTest --no-daemon

# Release build with embedded engine.
./gradlew :app:assembleRelease --no-daemon
```

Manual Pixel 8 Phase 9 smoke test:
- Confirm the target device with `adb -s emulator-5554 emu avd name`; do not use Pixel 9 for this test.
- Log in first. Phase 9 server operations must not run before a logged-in session exists.
- Add a shop and confirm the UI asks for one compute point before creating the clone.
- Confirm the backend creates `cloneInstanceId`, deducts exactly one point, and the new shop uses `新增店铺-[编号]` plus a `NEW-*` shop ID until store info is reported or manually edited.
- Open the clone with a valid token, then corrupt or remove `{BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/auth.token` and confirm launch is blocked.
- Renew the shop and confirm the server returns a new authorization token, the engine replaces only `auth.token`, and the clone opens again.
- Manually edit shop name/shopId and confirm the compute balance does not change.
- Inspect the cloned app visible data directory and confirm it contains no `.clone_meta.json`, `.clone_auth.token`, `meta.json`, `auth.token`, or other clone/token semantic files.
- Delete the shop and confirm the virtual user/data directory and `clone-auth/{cloneInstanceId}` authorization directory are removed.

Phase 9 server deployment prerequisites:
- `APP_CLONE_AUTH_PRIVATE_KEY` must be set in `admin/.env.product`; it is a Base64-encoded PKCS#8 RSA private key whose public key matches the engine verifier public key.
- `APP_CLONE_AUTH_PUBLIC_KEY_ID` defaults to `rsa_2026_01`.
- `shops.clone_instance_id` must be a normal non-unique index (`idx_clone_instance_id`), not `uk_clone_instance_id`, so soft-deleted historical clone IDs do not block re-creation.
- `compute_deductions` must contain the idempotency unique keys for create and renew operations.

When uploading/registering an engine release:
- Upload the engine APK through the admin `/api/files/engine-packages` file service so local/OBS storage behavior stays unified.
- Store a checksum matching the uploaded APK. The app supports MD5 (32 hex chars) and SHA-256 (64 hex chars), and checksum mismatches must block installation.
- Verify the public download URL returns the same checksum and `apksigner verify` passes on the downloaded APK.
- Verify `/api/engine-versions?available=true` returns the new version first.
- Engine upgrade eligibility is based on the engine APK's real `versionCode`: the candidate engine `versionCode` must be greater than the installed engine `versionCode`. Do not compare engine versions against the main APK version; a main APK can install a newer engine release.
- Engine upgrades require a logged-in user token. Before starting the Android install flow, the app must send the candidate package `versionCode`, MD5, and SHA-256 to `/api/engine-versions/verify`; only `valid=true` may proceed. `valid=false` must show `您使用的安装包未通过检验，不可升级`.

When uploading/registering a main APK release:
- Upload the main APK through the admin `/api/files/app-packages` file service. Do not copy APKs directly into local or OBS storage; the file service owns the local/OBS mapping.
- Add or update the matching row in `/api/app-versions` with `versionCode`, `versionName`, `apkUrl`, SHA-256 `checksum`, `fileSize`, and `published=true`.
- Add a system announcement with `type=APP_RELEASE` for the same release. Ordinary announcements must use `type=NORMAL`.
- Version release announcements are independent from ordinary announcements: app startup first checks `/api/app-versions?published=true`; it fetches `APP_RELEASE` announcements only when the latest published `versionCode` is greater than the installed app `versionCode`.
- If the installed app version equals the latest published version, `APP_RELEASE` must not be shown; normal announcements still follow the existing `NORMAL` announcement flow.
- The release announcement content should include the public download URL, and the app also exposes the one-click upgrade action from the release announcement and `我的 -> 关于 -> 检查更新`.
- Main APK upgrades also require a logged-in user token. Before starting the Android install flow, the app must send the candidate package `versionCode`, MD5, and SHA-256 to `/api/app-versions/verify`; only `valid=true` may proceed. `valid=false` must show `您使用的安装包未通过检验，不可升级`.

### Phase 11 Channel Release Flow

Phase 11 channel work must be verified locally unless the operator explicitly approves production deployment. Do not run `admin/deploy.sh` on the production server for Phase 11 without that approval. The only production access allowed during Phase 11 local acceptance is a one-time database dump used to restore a local Docker MySQL copy.

Normal public releases for `main` and non-main channels use the unified release job:

1. Create or update the channel in the admin UI with channel code, app package name, engine package name, app name, engine name, icons, status, and channel admin.
2. Create a release job from the admin UI. The selected channel can be `main` or a non-main channel.
3. The backend starts `admin/scripts/release-channel-apk.sh` with a job token, current admin token, `X-Apk-Channel`, package IDs, app/engine names, version names/codes, source release branch, and channel release branch.
4. The script creates a temporary checkout, merges the source release branch into the channel release branch, builds both app and engine APKs, uploads both through the file service, then calls the release-job callback.
5. Successful callback publishes channel-scoped `app_versions`, `engine_versions`, and one `APP_RELEASE` announcement titled `新版本发布`. Failed jobs must not publish versions or announcements.
6. Retry a failed release job from the admin UI; retry must create a fresh temporary checkout.

Channel release invariants:
- `main` keeps package names `com.zhirang.zhanghaoguanjia` and `com.zhirang.zhanghaoguanjia.engine`, and engine data root `blackbox`.
- Non-main channels use their configured app and engine package names. The release script passes `DUODIAN_ENGINE_DATA_ROOT_NAME=blackbox-<channelCode>` unless explicitly overridden, so virtual engine data roots are separated by channel.
- APK requests send `X-Apk-Channel: BuildConfig.APK_CHANNEL`; old APKs without the header fall back to `main`.
- Version and announcement queries are channel-scoped. Package verification also checks channel, versionCode, checksum, and package name.
- Release worker logs must not contain callback tokens or admin bearer tokens.

Phase 11 local acceptance checklist:

```bash
# Backend tests.
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -f admin/backend/pom.xml

# Frontend build.
cd admin/frontend && npm run build

# Script syntax.
bash -n admin/scripts/release-channel-apk.sh
python3 -m py_compile admin/scripts/release-worker-http.py

# Optional local-only real release worker. Start this on the macOS host before
# using APP_RELEASE_RUNNER=http in admin/.env.phase11-local. It binds to
# 127.0.0.1 only; Docker reaches it through host.docker.internal.
RELEASE_WORKER_TOKEN=phase11-local-worker \
  admin/scripts/release-worker-http.py \
  --host 127.0.0.1 \
  --port 19091 \
  --path /release

# Debug channel build against local Docker API for Xiaomi real-device testing.
./gradlew :app:assembleDebug :Bcore:assembleDebug \
  -PDUODIAN_APK_CHANNEL=testa \
  -PDUODIAN_APP_APPLICATION_ID=com.zhirang.channel.testa \
  -PDUODIAN_ENGINE_APPLICATION_ID=com.zhirang.channel.testa.engine \
  -PDUODIAN_APP_NAME=测试账号管家A \
  -PDUODIAN_ENGINE_NAME=测试引擎A \
  -PDUODIAN_ENGINE_DATA_ROOT_NAME=blackbox-testa \
  -PDUODIAN_API_BASE_URL=http://<本机Tailscale-IP>:<本地后台端口>/api/
```

Before committing Phase 11 work, confirm no temporary local API address remains in source:

```bash
rg "DUODIAN_API_BASE_URL=http://|127\\.0\\.0\\.1|localhost|<本机Tailscale-IP>|100\\." \
  app Bcore admin .planning CLAUDE.md
```

Documented examples may remain in `CLAUDE.md` and `.planning`; production source defaults must remain `http://dpgj.zrnh.cn/api/`.

Example `1.1.0-release` server verification:

```bash
# Public APK download must not require auth.
curl -I http://dpgj.zrnh.cn/api/files/app-packages/<apk-file>.apk

# Authenticated checks should show exactly one active published app version and release announcement.
curl -H "Authorization: Bearer $TOKEN" \
  "http://dpgj.zrnh.cn/api/app-versions?published=true"
curl -H "Authorization: Bearer $TOKEN" \
  "http://dpgj.zrnh.cn/api/announcements?published=true&type=APP_RELEASE"
```

Pixel 8 release smoke test:

```bash
ADB=/opt/homebrew/share/android-commandlinetools/platform-tools/adb

# Confirm the emulator. Do not use Pixel 9 for release smoke tests.
$ADB -s emulator-5554 emu avd name

$ADB -s emulator-5554 install -r -d app/build/outputs/apk/release/zhanghaoguanjia_${VERSION_NAME}_universal-release.apk
$ADB -s emulator-5554 shell dumpsys package com.zhirang.zhanghaoguanjia | rg "versionCode|versionName"

$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell monkey -p com.zhirang.zhanghaoguanjia -c android.intent.category.LAUNCHER 1
$ADB -s emulator-5554 logcat -d -t 1200 | rg "GET http://dpgj.zrnh.cn/api/(announcements|app-versions)|FATAL EXCEPTION|AndroidRuntime"
```

For a same-version smoke test, logs should show `type=NORMAL` announcements and `app-versions`, but no `type=APP_RELEASE` request and no update dialog text such as `发现新版本` or `立即升级`.

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
