---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: milestone
status: Phase 19 Wave 3 completed; development environment migrated to double
last_updated: "2026-06-21T18:49:14.077Z"
progress:
  total_phases: 11
  completed_phases: 10
  total_plans: 11
  completed_plans: 11
  percent: 100
---

## Recent Changes

### 2026-06-22: Phase 20 new package migration release added

- Added Phase 20 for publishing the new package-name release APK and migrating existing old-engine clone data after the user logs in to the new package.
- Locked the product constraint that the migration must be rootless and ordinary-user invisible: the old engine must export its own private data through `EngineCloneDataExportActivity`, and the new engine must import it into its own data root.
- Locked the timing constraint that the new package has no local user data on first install, so migration starts only after successful login and current-user shop data is available.
- Locked one-shot semantics: both local main-app state and a hidden server-side `users` field must record whether that server user has already migrated old-engine data so later logins do not re-run the old-engine migration.

### Roadmap Evolution

- Phase 20 added: 新包名无感迁移发布

### 2026-06-18: Development environment migrated to double

- Migrated the active development machine to `hwp@double` at `/Users/hwp/Projects/personal/zhirang-zhanghaoguanjia` on branch `dev`; future project work should continue there.
- Configured Thunderbolt Bridge transfer path with local `10.10.10.1` and double `10.10.10.2`; SSH target is `ssh -o HostKeyAlias=double hwp@10.10.10.2`.
- Installed Android Studio `2025.3` at `/Applications/Android Studio.app`; Android SDK is fixed to Android Studio's default user SDK path `/Users/hwp/Library/Android/sdk` with `ANDROID_HOME` and `ANDROID_SDK_ROOT` pointing there.
- Important path rule: do not use the old Homebrew-managed SDK location for SDK work on double; future SDK installs/updates must target `/Users/hwp/Library/Android/sdk`.
- Added a minimal `/Users/hwp/.zshenv` so direct non-interactive SSH commands also resolve Android SDK tools, Android Studio JBR 21, VS Code CLI, and scrcpy without sourcing shell profiles manually.
- Migrated Android SDK (`36G`) and AVD data (`43G`) to `/Users/hwp/.android/avd`; rewrote AVD and Android Studio SDK path references away from `/Users/heweiping` and Homebrew SDK paths.
- Verified `adb`, `sdkmanager`, `emulator`, `avdmanager`, Gradle wrapper, Android Studio JBR 21, and `Pixel_8` emulator launch; emulator log confirmed system image resolution under `/Users/hwp/Library/Android/sdk` and `adb devices` saw `emulator-5554`.
- Synced VS Code extensions including `karthikaradhya.vscode-scrcpy@1.1.1`; installed `scrcpy 4.0` at `/Users/hwp/.local/bin/scrcpy`.

### 2026-06-15: Phase 19 Wave 3 completed

- Completed the Xpra authorization experience and state-closure wave.
- Added high-resolution remote rendering, local loading before stream display, shop-card authorization status, backend/admin authorization fields, shop-level super-admin authorization URL, and profile-based authorization probing with redacted cookie/storage/page signals.
- Verified on Xiaomi real device `3ca26684`: `1.2.18-beta` installed, authorization entry placement correct, loading state correct, remote Meituan login page displayed clearly, keyboard only appeared after tapping the remote input field, and closing the authorization window triggered backend probe/writeback.
- Verified the authorized-state UX by temporarily marking shop `92` as `AUTHORIZED`: APP displayed grey “已授权” and tapping did not open the authorization dialog. The shop was restored via `/probe` to `UNKNOWN` with redacted signals after the test.
- Important finding: current server-side Luo Jia Chou Dou Fu related profiles visible under `~/data/profiles/15200837196/` are not actually authorized; JD/Taobao remain on login forms and Meituan is at slider verification, so the probe correctly returns `UNKNOWN` instead of misclassifying them as `AUTHORIZED`.
- Canonical completion record: `.planning/phases/19-xpra-shop-authorization/19-PLAN.md`.

### 2026-06-15: Phase 19 Wave 3 planned

- Planned Wave 3 for Xpra authorization experience and state closure.
- Scope covers sharper WebView/Xpra rendering, moving the APP authorization entry below shop remarks, local loading before remote stream display, authorization-success probing based on browser profile evidence, backend/admin/APP authorization status display, and a shop-level authorization URL for super-admin direct access to that shop's Xpra profile.
- Locked the authorization-success rule away from URL-only checks: first implementation must validate the authorized Luo Jia Chou Dou Fu profile with multiple independent signals such as page state, cookie/storage evidence, and management-console accessibility.
- Canonical plan document: `.planning/phases/19-xpra-shop-authorization/19-PLAN.md`.

### 2026-06-15: Phase 19 Wave 2 completed

- Completed the Xpra remote login form auto-alignment wave.
- Server-side Chromium now detects the visible login form region for JD, Ele.me, and Meituan authorization pages, keeps the platform authorization URL hidden from the APP, and returns `alignment` diagnostics through `/open`.
- Fixed the JD false-positive alignment case where only the top logo was visible: the aligner now preserves document width, recomputes selected controls after wrapper layout, and requires account/password/login controls to be inside the final `visibleBox`.
- Verified `/open` for JD, Ele.me, and Meituan against `100.99.88.6:14501`; all returned `alignment.ok=true` and `selectedControlsVisible=true`.
- Canonical completion record: `.planning/phases/19-xpra-shop-authorization/19-PLAN.md`.

### 2026-06-15: Phase 19 Xpra shop authorization MVP started

- Created a dedicated Phase 19 worktree and branch `phase19-xpra-stream`.
- Added the APP-side “授权登录该店铺” entry under shop ID, leaving the existing “私域吸粉” advanced-feature switch unchanged.
- Added the “店铺授权” dialog that loads the Xpra HTML5 stream URL from build config; the dialog is centered, about two thirds of the screen height, and can move with the soft keyboard.
- Brought up the Aliyun Ubuntu virtual desktop stack on Tailscale IP `100.99.88.6`: Xvfb/fluxbox/Chromium/Xpra HTML5/VNC.
- Merged latest `origin/dev`, rebuilt `1.2.18-beta`, and installed it to Xiaomi real device `3ca26684`.
- Updated APK defaults to use backend API `http://100.99.88.2:8006/api/`, Xpra stream `http://100.99.88.6:14500/`, and ZR control endpoint `http://100.99.88.6:14501/`.
- Moved the remote startup scripts into repository source under `admin/scripts/browser/`; server-side runtime now lives under `~/data`.
- Stabilized the remote desktop to a single `360x520` Chromium kiosk window; browser profiles are isolated under `~/data/profiles/<phone>/<shopId>/chrome` and browser tracking goes to `~/data/logs/browser-trace.jsonl`.
- Updated ZR control so the APP sends the authorization WebView logical `width/height`, browser `scale`, and platform-specific authorization `url`; server display stack and Chromium now resize to that viewport before streaming back through Xpra.
- Added “授权地址” to backend platform configuration; APP does not display it and only passes it to `/open`.
- Gated Xpra keyboard capture so the soft keyboard is only focused for the JD login input regions, not for every tap inside the stream.
- Confirmed Ubuntu apt and Docker apt sources are on Alibaba mirrors; remaining slow paths are third-party HTTPS repositories or Playwright browser downloads.
- Canonical plan document: `.planning/phases/19-xpra-shop-authorization/19-PLAN.md`.

### 2026-06-14: Phase 11 checked branch completed

- Closed the `phase11-checked` validation branch after minimizing the Android 15 WebView compatibility patch to four engine-side source files.
- Build verification passed with `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :Bcore:assembleDebug --no-daemon`.
- Pixel 9 / Android 15 (`emulator-5554`, SDK 35) installed `FxEngine_1.2.12-release_debug.apk` successfully and launched the JD clone through the debug authorized launch path.
- JD login WebView reached `LoginFusionActivity`; DongCore WebView logs showed successful network probe and the system WebView sandbox process started.
- SMS-code trigger was revalidated with the phone number entered into the correct `jd_phone_et` field. Within the 5-second success window, the real sliding captcha did not appear; the button entered resend countdown instead.
- Completion evidence is recorded in `.planning/phases/11-channel-promotion-system/11-PLAN.md`.

### 2026-06-13: Phase 14 subscription billing completed

- Marked Phase 14 complete after Xiaomi MIX 2S regression against the migrated local server `172.20.0.13:8006`.
- Verified normal user expired-shop flow: confirmation prompt, balance deduction, 30-day renewal, and `RENEW` logs.
- Verified active subscriber flow: subscription expiry displayed, shop remaining-days hidden, expired shop opened without compute deduction.
- Verified expired subscriber fallback: exact expiry prompt, confirmation deducted 1 compute, and renewed the shop.
- Canonical completion document: `.planning/phases/14-subscription-billing/14-PLAN.md`.

### 2026-06-13: Phase 16 Main App login-state data center completed

- Completed Phase 16 implementation and Xiaomi MIX 2S real-device verification.
- Verified normal shop open keeps the current phone's local clone login-state and does not restore server backup.
- Verified explicit repair is the release path: shop `53` 罗家臭豆腐 restored `meituan-waimai-cips-f` backup into Meituan Waimai user3, then opened the merchant order page.
- Verified shop-info extraction gates login-state upload: pre-repair extraction failed without upload; post-repair extraction succeeded and uploaded a fresh backup by system shop ID.
- Verified raw login-state staging files are deleted after restore/upload completion.
- Canonical completion document: `.planning/phases/16-app/16-PLAN.md`.

### 2026-06-13: Phase 16 Main App login-state data center planned

- Added Phase 16 to move login-state backup artifacts, metadata, and sync decisions into the Main App data directory.
- Locked the boundary that engine remains responsible for clone runtime infrastructure and AIDL capabilities only.
- Required shop-info collection before login-state export, and repair-only restore/release into clone directories.
- Canonical plan document: `.planning/phases/16-app/16-PLAN.md`.

### 2026-06-09: Phase 11 channel promotion system planned

- Planned a complete channel promotion system based on the existing `apkChannel` registration marker.
- Locked the design around a first-class `channels` table, `users.channel_id`, `SUPER_ADMIN / CHANNEL_ADMIN / USER` roles, and a channel compute pool with immutable ledger.
- Defined channel isolation for users, shops, transaction logs, feedbacks, announcements, app versions, package verification, and Android login/register/update flows.
- Clarified Android branding constraints: channel icon/name are build-time APK resources, while the backend stores channel brand config and super admins upload built channel APKs.
- Added channel APK release automation design: per-channel release branches, fixed release worker script, brand/resource validation, file-service upload, and authenticated backend callback to complete app version and announcement publication.
- Canonical plan document: `.planning/phases/11-channel-promotion-system/11-PLAN.md`.

### 2026-06-08: Phase 10 engine permission center started

- Created Phase 10 from latest `dev` on `feature-phase10`.
- Migrated the existing Douyin Laike compatibility patch into the Phase 10 branch.
- Scope: promote the Douyin camera/record-audio host-permission fix into a reusable engine permission center, with a one-time baseline permission request and per-platform missing-permission fallback.
- Implemented `EnginePermissionCenter` in the main APK and generalized `EnginePermissionActivity` in the engine APK.
- Verification: `git diff --check` passed; `JAVA_HOME=/Users/heweiping/Library/Java/JavaVirtualMachines/azul-21.0.10/Contents/Home ./gradlew :app:assembleDebug :Bcore:assembleDebug` passed; Xiaomi MIX 2S smoke test opened the engine permission center from the main APK, granted baseline permissions, and confirmed restart did not reopen the permission center.
- Canonical plan document: `.planning/phases/10-engine-permission-center/10-PLAN.md`.

### 2026-06-08: Phase 09 clone auth billing completed

- Marked Phase 09 complete after the `1.2.3-release` closeout.
- Final release includes cloneInstanceId-based create/renew billing, server-signed authorization tokens, engine system-dir clone-auth storage, platform-scoped restore preparation, local-only repair, and no shopName/shopId-based billing.
- Verification evidence from the release closeout: `./gradlew :app:assembleRelease --no-daemon` passed for the release APK, server app-version and release announcement records were published, and Xiaomi real-device smoke testing verified the repair swipe dialog and no engine ANR after the 1.2.3 fix.
- Canonical completion document: `.planning/phases/09-clone-auth-billing/09-PLAN.md`.

### 2026-06-02: Fix Pixel E2E test script

- Fixed `e2e_pixel/tests/jd_captcha.sh` to properly detect and click UI elements
- Added dynamic UI element detection via uiautomator as primary strategy
- Added screenshot comparison to verify clicks actually change the UI
- Added fallback coordinates when dynamic detection fails
- Added detailed per-step status reporting
- Created `e2e_pixel/run.sh` test runner with build/reinstall options
