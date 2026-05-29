# External Integrations

**Analysis Date:** 2026-05-30

## APIs & External Services

**Mapping / Location:**
- OpenStreetMap (via `org.osmdroid:osmdroid-android:6.1.11`) — Fake location picker UI in `app/src/main/java/top/niunaijun/blackboxa/view/fake/`

**Distribution / Notifications:**
- Telegram Bot API — CI/CD artifact distribution
  - Used in: `.github/workflows/build_and_telegram.yml`
  - Auth: `secrets.TELEGRAM_BOT_TOKEN`, `secrets.TELEGRAM_CHAT_ID`
  - Sends debug/release APKs and Bcore AARs with commit captions and chat pinning

**No cloud backends, analytics, or crash reporting services detected.**

## Data Storage

**Databases:**
- SQLite (via Android framework) — `BPackageManagerService` maintains virtual APK metadata in an internal database
  - Location: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/`
  - No ORM; raw SQLite usage through Android APIs

**File Storage:**
- Local filesystem only — Virtual apps get redirected data directories under the host app's private storage (`/data/data/<host>/...`)
- No cloud file storage integration

**Caching:**
- Android `SharedPreferences` — App-level settings and user preferences (`AppSharedPreferenceDelegate.kt`)
- In-memory caches in repositories (`AppsRepository.kt`, `GmsRepository.kt`)

## Authentication & Identity

**Auth Provider:**
- None / Custom — No OAuth, Firebase Auth, or third-party identity providers
- Virtual app identity spoofing is handled internally by the engine (`VirtualSpoof.cpp`, `AntiDetection.cpp`)

## Monitoring & Observability

**Error Tracking:**
- None — No Sentry, Crashlytics, Bugsnag, or similar service integrated

**Logs:**
- Android `Logcat` — Native layer uses custom `Log.h` macros
- File-based log dumping in E2E tests (`e2e/logs/`)

## CI/CD & Deployment

**Hosting:**
- GitHub Actions (`.github/workflows/build_and_telegram.yml`)
  - Trigger: push to `main` branch or `workflow_dispatch`
  - Runner: `ubuntu-latest`
  - JDK: Temurin 21
  - Android SDK: `android-actions/setup-android@v3`

**CI Pipeline Steps:**
1. Checkout code
2. Set up JDK 21 + Android SDK + accept licenses
3. Build debug APK (`./gradlew assembleDebug`)
4. Build release APK (`./gradlew assembleRelease`)
5. Build Bcore debug AAR (`./gradlew :Bcore:assembleDebug`)
6. Build Bcore release AAR (`./gradlew :Bcore:assembleRelease`)
7. Send all artifacts to Telegram with pinning

**No Play Store, F-Droid, or other app store integration detected.**

## Build Pipeline Components

**Native Build:**
- `ndkBuild` via `Android.mk` + `Application.mk`
- Prebuilt static library: Dobby (`Bcore/src/main/cpp/Dobby/<abi>/libdobby.a`)
- Embedded C library: xDL (source in `Bcore/src/main/cpp/xdl/`)

**Annotation Processing:**
- `compiler` module runs at compile time to generate `BR*` classes
- Uses `com.google.auto.service:auto-service:1.1.1` for processor registration
- Uses `com.squareup:javapoet:1.13.0` for source generation
- Output: `black/` package tree in `Bcore/src/main/java/black/...`

**Code Shrinking:**
- ProGuard/R8 enabled for release builds (`minifyEnabled true`)
- Config files: `app/proguard-rules.pro`, `Bcore/proguard-rules.pro`, `Bcore/consumer-rules.pro`

## Platform-Specific Integrations

**Android System Services (Hooked):**
The engine integrates with Android at the system service level by proxying these Binder interfaces:
- `IPackageManager` — App installation/query spoofing
- `IActivityManager` — Activity/Service lifecycle interception
- `IWebViewUpdateService` / WebView factory — WebView compatibility
- ContentProviders — URI redirection
- File system — Path redirection (`FileSystemHook`, `UnixFileSystemHook`)
- Binder IPC — Interception of all cross-process calls (`BinderHook`)

**GMS (Google Mobile Services):**
- GMS management UI (`GmsManagerActivity`, `GmsRepository`) for enabling/disabling Google services in virtual apps
- Not a third-party SDK integration; manages GMS APK presence within the sandbox

**Honor/Huawei Compatibility:**
- Device-specific patches in `feature-hwp` branch for Honor/Huawei devices
- See `Bcore/src/main/java/top/niunaijun/blackbox/core/env/AppSystemEnv.java` for system package whitelists

## Environment Configuration

**Required env vars (CI only):**
- `TELEGRAM_BOT_TOKEN` — Telegram bot authentication
- `TELEGRAM_CHAT_ID` — Target chat for artifact distribution
- `ANDROID_HOME` — Android SDK path (set up by `setup-android` action)
- `JAVA_HOME` — JDK 21 path

**Secrets location:**
- GitHub Actions secrets (repository-level)
- No local secret files committed

**Local build requirements:**
- `local.properties` with `sdk.dir` and `ndk.dir` (gitignored, must be created locally)

## Webhooks & Callbacks

**Incoming:**
- None — No webhook endpoints exposed

**Outgoing:**
- Telegram Bot API (`sendDocument`, `pinChatMessage`) — CI artifact distribution only

---

*Integration audit: 2026-05-30*
