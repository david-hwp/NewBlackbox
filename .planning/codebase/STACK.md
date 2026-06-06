# Technology Stack

**Analysis Date:** 2026-05-30

## Languages

**Primary:**
- Java 21 — Core virtualization engine (`Bcore` module), annotation processor (`compiler`), reflection utilities (`black-reflection`)
- Kotlin 1.9.23 — UI layer (`app` module): Activities, Fragments, ViewModels, Adapters
- C/C++ (C++17) — Native hooking layer: Dobby hooks, xDL symbol resolution, ART method hooking, Binder IPC interception
- AIDL — Android IPC interface definitions for system service proxies (`Bcore/src/main/aidl/`)

**Secondary:**
- Shell (Bash) — E2E test automation (`e2e/run.sh`, `e2e/lib/utils.sh`, `e2e/tests/*.sh`)
- XML — Android resources, manifests, layouts
- Gradle DSL (Groovy/Kotlin) — Build scripts

## Runtime

**Environment:**
- Android Runtime (ART) — Target Android 5.0–15+ (API 21–35)
- JVM (for build) — JDK 21 (Temurin distribution recommended)
- NDK — 29.0.13846066 (`r29b`) with `ndkBuild` via `Android.mk`

**Package Manager:**
- Gradle 8.13 (via wrapper)
- Lockfile: `gradle/libs.versions.toml` (version catalog)
- Repositories: Aliyun Maven mirrors (primary), Google Maven, Maven Central, JitPack

## Frameworks

**Core:**
- Android Gradle Plugin (AGP) 8.13.2 — Build system
- Android SDK 35 (compileSdk), targetSdk 28, minSdk 21
- AndroidX — AppCompat, Core KTX, Lifecycle, RecyclerView, Preference, WorkManager, ConstraintLayout
- Material Design Components 1.12.0

**Testing:**
- JUnit 4.13.2 — Unit tests
- AndroidX Test Ext JUnit 1.2.1 — Instrumentation tests
- Espresso 3.6.1 — UI instrumentation tests
- Shell-based E2E (`e2e/`) — ADB-driven smoke and feature tests

**Build/Dev:**
- Gradle 8.13 with Kotlin DSL (partial)
- `ndkBuild` via `Android.mk` / `Application.mk` — Native compilation
- ProGuard/R8 — Code obfuscation for release builds (`minifyEnabled true`)
- Annotation Processing — `compiler` module generates `BR*` reflection helpers at compile time

## Key Dependencies

**Critical:**
- `com.github.tiann:FreeReflection:3.2.2` — Bypasses Android hidden API restrictions on Android 9+ (essential for system service hooking)
- `com.moandjiezana.toml:toml4j:0.7.2` — TOML parsing in Bcore
- Dobby (prebuilt static libraries `libdobby.a`) — Native function hooking framework (arm64-v8a, armeabi-v7a)
- xDL (embedded C source) — Dynamic symbol resolution for ELF libraries

**Infrastructure:**
- `com.google.auto.service:auto-service:1.1.1` — Annotation processor service registration (`compiler`)
- `com.squareup:javapoet:1.13.0` — Java source code generation (`compiler`)
- `org.osmdroid:osmdroid-android:6.1.11` — OpenStreetMap for fake location UI
- `com.gitee.cbfg5210:RVAdapter:0.3.7` — RecyclerView adapter helper
- `com.afollestad.material-dialogs:core:3.3.0` / `input:3.3.0` — Material dialogs
- `androidx.work:work-runtime:2.7.1` — Background work scheduling

**UI Libraries:**
- `com.github.Othershe:CornerLabelView:1.0.0` — Corner badges
- `com.github.nukc.stateview:kotlin:2.2.0` — Empty/loading/error state views
- `com.github.Ferfalk:SimpleSearchView:0.2.0` — Search UI component
- `com.tbuonomo:dotsindicator:4.2` — ViewPager dot indicator
- `catloading-release.aar` / `floatingview-release.aar` — Bundled AARs (loading animation, floating view)

## Configuration

**Environment:**
- `local.properties` — `sdk.dir`, `ndk.dir` (machine-specific, not committed)
- `gradle.properties` — AndroidX, Jetifier, annotation processor settings, JVM args (`-Xmx2048m`)
- No `.env` files detected

**Build:**
- Root `build.gradle` — Shared ext vars: `compileSdk=35`, `targetSdk=28`, `minSdk=21`, `versionCode=400`, `versionName="4.0.0"`
- `gradle/libs.versions.toml` — Centralized dependency versions
- `settings.gradle` — Repository configuration with Aliyun mirrors
- `Bcore/build.gradle` — `aidl true`, `prefab true`, `externalNativeBuild { ndkBuild }`, lint non-blocking
- `app/build.gradle` — `viewBinding true`, ABI splits (armeabi-v7a, arm64-v8a, universal APK)

## Platform Requirements

**Development:**
- JDK 21 (required by Gradle 8.13 + AGP 8.13.2)
- Android SDK 35 with command-line tools
- NDK 29.0.13846066
- macOS, Linux, or Windows with compatible shell for E2E scripts

**Production:**
- Android devices running API 21+ (Android 5.0+)
- Target ABI: `armeabi-v7a`, `arm64-v8a`
- No x86/x86_64 support
- Requires `QUERY_ALL_PACKAGES` permission

## Notable Technology Choices

- **Dual language split**: Java for low-level engine (performance, reflection, JNI), Kotlin for UI (conciseness, coroutines, ViewBinding)
- **Custom annotation processor**: `compiler` + `black-reflection` modules generate compile-time reflection accessors to hidden Android APIs, avoiding runtime reflection penalties
- **Native hook stack**: Dobby (hooking) + xDL (symbol resolution) + custom ART method hooking (`JniHook/`) for intercepting system calls at the native layer
- **AIDL-heavy**: 20+ AIDL files in `Bcore/src/main/aidl/` for proxying system Binder interfaces
- **Non-standard NDK integration**: Uses legacy `ndkBuild` with `Android.mk` instead of CMake; `prefab` enabled for potential native dependency packaging
- **Aliyun Maven mirrors**: Primary repository for China-based builds; JitPack for GitHub-hosted libraries
- **Lint suppression**: Extensive lint disabling (`abortOnError false`, many checks disabled) due to heavy use of hidden/internal APIs

---

*Stack analysis: 2026-05-30*
