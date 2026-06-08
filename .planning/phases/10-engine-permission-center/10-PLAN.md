---
phase: 10-engine-permission-center
plan: 10
type: execute
status: completed
created_at: "2026-06-08T04:20:00+08:00"
completed_at: "2026-06-08T23:25:00+08:00"
branch: feature-phase10
wave: 1
depends_on:
  - 09-clone-auth-billing
files_modified:
  - app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EnginePermissionCenter.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/main/ShortcutActivity.kt
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/EnginePermissionActivity.kt
  - Bcore/src/main/AndroidManifest.xml
  - .planning/ROADMAP.md
  - .planning/STATE.md
autonomous: true
requirements:
  - ENGINE-PERMISSION-CENTER-01
  - DOUYIN-LAIKE-CAMERA-01
---

<objective>
Promote the existing Douyin Laike camera/record-audio compatibility patch into a reusable engine permission center.

The main APK decides which host permissions the engine needs, and the engine APK executes a one-time baseline permission flow plus per-platform missing-permission fallback. Phase 10 must preserve Phase 9 clone authorization and billing behavior.
</objective>

<context>
@.planning/phases/10-engine-permission-center/10-CONTEXT.md
@.planning/phases/10-engine-permission-center/10-RESEARCH.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Build main APK permission center</name>
  <files>
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EnginePermissionCenter.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
  </files>
  <description>
    1. Add an `EnginePermissionCenter` object in the main APK.
    2. Generate baseline dangerous permissions by SDK version.
    3. Parse target platform Manifest requested permissions when available.
    4. Add platform fallback for Douyin Laike: `CAMERA` and `RECORD_AUDIO`.
    5. Check engine package runtime permission and AppOps status.
    6. Build explicit Intent to engine permission Activity with one group of permissions.
    7. Record baseline prompt by installed engine version so homepage does not repeatedly prompt.
  </description>
  <verify>
    - Main APK compiles.
    - Missing permission detection returns camera/record-audio for Douyin Laike when engine lacks them.
    - Baseline prompt is skipped after it has already been shown for the same engine version.
  </verify>
</task>

<task type="auto">
  <name>Task 2: Generalize engine permission Activity</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/engine/EnginePermissionActivity.kt
    Bcore/src/main/AndroidManifest.xml
  </files>
  <description>
    1. Replace fixed media-permission logic with a generic permission list from Intent extras.
    2. Filter unsupported SDK permissions and permissions not declared by the engine APK.
    3. Request the full missing runtime permission list in one `requestPermissions` call.
    4. Re-check runtime permissions and AppOps after grant.
    5. Keep MIUI/settings fallback for denied permissions.
    6. Ensure manifest declares baseline permissions used by the engine.
  </description>
  <verify>
    - Engine APK compiles.
    - Permission Activity can request camera and recording permissions.
    - Activity exits successfully only when required permissions pass runtime/AppOps checks.
  </verify>
</task>

<task type="auto">
  <name>Task 3: Wire homepage and shop opening flows</name>
  <files>
    app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
  </files>
  <description>
    1. Initialize a single ActivityResult launcher for engine permission requests.
    2. On HomeActivity after login/home creation, request baseline engine permissions only if missing and not previously prompted for the installed engine version.
    3. Before opening a shop card, compute platform-required permissions and request only missing ones.
    4. After successful platform permission grant, continue the existing shop opening flow.
    5. If the user denies a required platform permission, show a concise failure message and do not launch the clone.
  </description>
  <verify>
    - Existing non-Douyin platform open path still calls the original launch logic when no required permission is missing.
    - Douyin Laike opens permission center first when engine lacks camera/record-audio.
    - Orientation changes do not replay the baseline prompt.
  </verify>
</task>

<task type="auto">
  <name>Task 4: Verify build and device behavior</name>
  <files>
    app/build/outputs/apk/debug/*.apk
    Bcore/build/outputs/apk/debug/*.apk
  </files>
  <description>
    1. Run `git diff --check`.
    2. Build `:app:assembleDebug` and `:Bcore:assembleDebug`.
    3. Install debug APKs to a test device if connected.
    4. Verify baseline prompt, Douyin Laike permission flow, and a non-Douyin shop smoke path.
  </description>
  <verify>
    - `git diff --check` passes.
    - Gradle build passes.
    - Device smoke test logs show no new engine permission crash.
  </verify>
</task>

</tasks>

<acceptance_criteria>

- Phase 10 planning files exist and reference the active feature branch.
- The Douyin Laike patch is no longer a one-off media-permission path in `HomeActivity`; it is routed through the permission center.
- Engine permission Activity accepts a list of permissions and requests the missing set.
- Baseline permissions are prompted once per installed engine version.
- Shop-card launch and shortcut launch both route through the same permission center before starting a virtual app.
- Main APK and engine APK compile successfully.

</acceptance_criteria>

<verification>

- `git diff --check` passed.
- `JAVA_HOME=/Users/heweiping/Library/Java/JavaVirtualMachines/azul-21.0.10/Contents/Home ./gradlew :app:assembleDebug :Bcore:assembleDebug` passed.
- Installed debug main APK and engine APK on Xiaomi MIX 2S (`3ca26684`) with `adb install -r -d`.
- Launched the main APK and confirmed it opened `top.niunaijun.blackbox.engine.EnginePermissionActivity` through the real main-APK caller path.
- Granted baseline permissions on MIUI; `dumpsys package com.zhirang.zhanghaoguanjia.engine` showed `CAMERA`, `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `READ_EXTERNAL_STORAGE`, and `WRITE_EXTERNAL_STORAGE` granted.
- Force-stopped and relaunched the main APK; current foreground Activity stayed on `HomeActivity`, confirming the baseline permission center did not repeat.
- Added shortcut launch preflight so direct clone shortcuts cannot bypass the platform permission center.

</verification>
