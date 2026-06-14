---
gsd_state_version: 1.0
milestone: v1.3
milestone_name: phase11-channel-promotion-system
status: Phase 16 completed
last_updated: "2026-06-14T22:33:46+08:00"
progress:
  total_phases: 11
  completed_phases: 10
  total_plans: 11
  completed_plans: 11
  percent: 100
---

## Recent Changes

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
