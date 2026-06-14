---
phase: 11-channel-promotion-system
plan: 11
type: plan
status: completed
created_at: "2026-06-09T15:45:00+08:00"
updated_at: "2026-06-10T00:00:00+08:00"
completed_at: "2026-06-10T10:15:00+08:00"
branch: dev
waves: 3
depends_on:
  - 10-engine-permission-center
files_expected:
  - admin/backend/src/main/java/com/duodian/admin/entity/Channel.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ReleaseJob.java
  - admin/backend/src/main/java/com/duodian/admin/service/ChannelService.java
  - admin/backend/src/main/java/com/duodian/admin/service/ReleaseJobService.java
  - admin/backend/src/main/java/com/duodian/admin/service/PermissionService.java
  - admin/backend/src/main/java/com/duodian/admin/service/ChannelScopeService.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ChannelController.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ReleaseJobController.java
  - admin/backend/src/main/resources/schema.sql
  - admin/frontend/src/views/Channels.vue
  - admin/frontend/src/views/ReleaseJobs.vue
  - admin/scripts/release-channel-apk.sh
  - app/build.gradle
  - Bcore/build.gradle
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/zhirang/zhanghaoguanjia/network/AuthInterceptor.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt
autonomous: false
requirements:
  - CHANNEL-ENTITY-01
  - CHANNEL-RBAC-01
  - CHANNEL-COMPUTE-01
  - CHANNEL-APP-VERSION-01
  - CHANNEL-ENGINE-VERSION-01
  - CHANNEL-ANNOUNCEMENT-01
  - CHANNEL-APP-CLIENT-01
  - CHANNEL-PACKAGE-ID-01
  - UNIFIED-RELEASE-01
---

<objective>
Implement a complete channel promotion system where every channel, including `main`, has isolated users, announcements, app versions, engine versions, APK package identity, and release automation, while preserving existing `main` users and release compatibility.
</objective>

<context>
@.planning/phases/11-channel-promotion-system/11-CONTEXT.md
@.planning/phases/11-channel-promotion-system/11-RESEARCH.md
@.planning/phases/11-channel-promotion-system/11-DISCUSSION-NOTES.md
@.planning/phases/11-channel-promotion-system/11-01-PLAN.md
@.planning/phases/11-channel-promotion-system/11-02-PLAN.md
@.planning/phases/11-channel-promotion-system/11-03-PLAN.md
</context>

<architecture>

## Target Model

Introduce `channels` as the authoritative tenant boundary:

- `channels.code` is the immutable external channel identifier that corresponds to Android `BuildConfig.APK_CHANNEL`.
- `users.channel_id` is the trusted relational boundary for APP users and channel admins.
- `users.apk_channel` remains for compatibility and display.
- `SUPER_ADMIN` can cross channel boundaries.
- `CHANNEL` is a registered user bound to exactly one channel and acts as that channel's administrator.
- A `CHANNEL` user's `compute_balance` is the channel's distributable compute pool.
- `USER` can only use APP endpoints for their own channel-scoped data.
- Announcements, app versions, engine versions, package verification, shops, transaction logs, feedbacks, and compute deductions all carry `channel_id`.
- Unified release jobs support both `main` and non-main channels; each job builds and publishes both the main APK and engine APK for the selected channel.
- Channel package identity is parameterized: main APK `applicationId`, engine APK `applicationId`, display names, icons, and engine notification text come from channel config.

</architecture>

<waves>

## Wave 1: Backend channel model, RBAC, data migration, and APP channel headers

<task type="design">
  <name>Task 1: Add channel schema and safe migration</name>
  <files>
    admin/backend/src/main/resources/schema.sql
    admin/backend/src/main/resources/data.sql
    admin/backend/src/main/java/com/duodian/admin/entity/Channel.java
    admin/backend/src/main/java/com/duodian/admin/repository/ChannelRepository.java
    admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java
  </files>
  <description>
    1. Create `channels` with code/name/status/admin_user_id/app display fields/app application id/engine display fields/engine application id/icon metadata/register bonus/deleted/timestamps.
    2. Seed `main` with current package names:
       - `app_application_id = com.zhirang.zhanghaoguanjia`
       - `engine_application_id = com.zhirang.zhanghaoguanjia.engine`
    3. Add nullable `channel_id` to `users`, `shops`, `transaction_logs`, `compute_deductions`, `feedbacks`, `announcements`, `app_versions`, and `engine_versions`.
    4. Backfill all existing rows to `main`; dependent rows may use owning user when available but must fall back to `main`.
    5. Migrate legacy `ADMIN` users to `SUPER_ADMIN`.
    6. Add `CHANNEL` role support without automatically converting existing users.
    7. Replace global normal-user phone uniqueness with active uniqueness on `(channel_id, phone)` while preserving soft-delete semantics.
    8. Keep admin/channel-login phone uniqueness global in service validation.
    9. Make channel fields non-null after backfill where safe.
  </description>
  <verify>
    - Fresh schema creates successfully with `main` channel and super admin.
    - Existing database upgrades with all current rows visible under `main`.
    - Same phone can exist in two channels as normal users, but not twice in one active channel.
    - `SUPER_ADMIN` and `CHANNEL` login phones are globally unique.
  </verify>
</task>

<task type="design">
  <name>Task 2: Centralize auth context, role checks, and channel scope</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/config/AuthContext.java
    admin/backend/src/main/java/com/duodian/admin/config/AuthInterceptor.java
    admin/backend/src/main/java/com/duodian/admin/config/JwtUtil.java
    admin/backend/src/main/java/com/duodian/admin/service/PermissionService.java
    admin/backend/src/main/java/com/duodian/admin/service/ChannelScopeService.java
  </files>
  <description>
    1. Extend JWT claims with role, channelId, and apkChannel.
    2. Replace `AuthContext` userId-only state with a current principal object.
    3. Add helpers: `requireSuperAdmin`, `requireAdminRole`, `requireChannelAccess(channelId)`, `requireActiveChannelForMutation`.
    4. Keep backwards compatibility for existing tokens by loading role/channel from DB when claims are missing.
    5. Resolve channel for APP requests by `X-Apk-Channel`, then compatibility body field where applicable, then `main`.
    6. Do not add special Phase11 rejection for token channel mismatch with `X-Apk-Channel`.
    7. Add central disabled-channel handling for APK requests: return `该产品暂不可用，请联系：xxx（渠道管理员的手机号）`.
  </description>
  <verify>
    - USER cannot call management endpoints.
    - CHANNEL receives 403 for another channel's user, shop, log, announcement, app version, or engine version.
    - Disabled-channel APP request returns the required message.
    - Old-token requests still work after DB lookup.
  </verify>
</task>

<task type="design">
  <name>Task 3: Implement channel management and compute allocation using CHANNEL user balance</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/controller/ChannelController.java
    admin/backend/src/main/java/com/duodian/admin/service/ChannelService.java
    admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java
    admin/backend/src/main/java/com/duodian/admin/service/UserService.java
    admin/backend/src/main/java/com/duodian/admin/controller/UserController.java
  </files>
  <description>
    1. Add super-admin channel CRUD with soft delete/disable safeguards.
    2. Add channel admin candidate endpoint that searches existing users by username or phone.
    3. Bind a selected existing user as role `CHANNEL` and set `channels.admin_user_id`.
    4. Add user-list role/type filter so super admin can filter `CHANNEL` users.
    5. Reuse existing user compute adjustment flow to increase/decrease CHANNEL user balance and write transaction logs.
    6. Add channel-admin allocation endpoint: deduct from current CHANNEL user's balance, increment target same-channel USER balance, write transaction logs.
    7. Enforce user gift compute only within the same channel.
    8. Keep registration bonus independent of channel admin balance.
  </description>
  <verify>
    - Super admin binds an existing user as CHANNEL admin through searchable selector.
    - Super admin grants 100 compute to CHANNEL user using existing adjustment path and transaction log is created.
    - CHANNEL admin allocates 10 to same-channel user; admin balance decreases, user balance increases, logs exist.
    - CHANNEL admin cannot allocate to another channel.
    - Registration bonus does not reduce CHANNEL admin balance.
    - Disabled CHANNEL admin has read-only backend access.
  </verify>
</task>

<task type="design">
  <name>Task 4: Channel-scope operational data APIs</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/controller/UserController.java
    admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java
    admin/backend/src/main/java/com/duodian/admin/controller/TransactionLogController.java
    admin/backend/src/main/java/com/duodian/admin/controller/FeedbackController.java
    admin/backend/src/main/java/com/duodian/admin/repository/UserRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/ShopRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/TransactionLogRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/FeedbackRepository.java
  </files>
  <description>
    1. Add `channelId/channelCode` filters for super admin search pages.
    2. Force CHANNEL admin queries to own channel even when request params specify another channel.
    3. Stamp `channel_id` on all new shops, transaction logs, feedbacks, and compute deductions.
    4. Ensure APP `/shops/my`, `/logs/my`, and `/feedbacks` remain self-scoped and cannot cross channel.
    5. Update DTOs to include channel code/name where useful for admin pages.
  </description>
  <verify>
    - CHANNEL list pages only return own channel rows.
    - Super admin can filter users/shops/logs/feedbacks by channel.
    - New clone create, renew, feedback submit, and logs all persist correct `channel_id`.
  </verify>
</task>

<task type="design">
  <name>Task 5: Android request channel header and unified error display</name>
  <files>
    app/src/main/java/com/zhirang/zhanghaoguanjia/network/AuthInterceptor.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/BaseRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
  </files>
  <description>
    1. Add `X-Apk-Channel: BuildConfig.APK_CHANNEL` to all Retrofit requests in the OkHttp interceptor regardless of login state.
    2. Keep registration body `apkChannel` temporarily for compatibility.
    3. Add channel to login request if needed, but treat header as authoritative for new APKs.
    4. Add unified server error handling so backend messages such as disabled-channel errors are displayed to the user.
  </description>
  <verify>
    - Login/register/announcements/versions/shops/logs requests include `X-Apk-Channel`.
    - Old body `apkChannel` still compiles and works.
    - Disabled-channel backend response shows the exact server message.
  </verify>
</task>

## Wave 2: Package identity, engine binding, and channel data directory isolation

<task type="design">
  <name>Task 6: Parameterize main APK and engine APK package identity</name>
  <files>
    app/build.gradle
    Bcore/build.gradle
    app/src/main/AndroidManifest.xml
    Bcore/src/main/AndroidManifest.xml
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineInstaller.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineConnection.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineLoader.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EnginePermissionCenter.kt
  </files>
  <description>
    1. Add Gradle parameters:
       - `DUODIAN_APP_APPLICATION_ID`
       - `DUODIAN_ENGINE_APPLICATION_ID`
       - `DUODIAN_APP_NAME`
       - `DUODIAN_ENGINE_NAME`
    2. Default `DUODIAN_APP_APPLICATION_ID` to `com.zhirang.zhanghaoguanjia`.
    3. Default `DUODIAN_ENGINE_APPLICATION_ID` to `com.zhirang.zhanghaoguanjia.engine`.
    4. Set app module `applicationId` from `DUODIAN_APP_APPLICATION_ID`.
    5. Set Bcore module `applicationId` from `DUODIAN_ENGINE_APPLICATION_ID`.
    6. Generate `BuildConfig.ENGINE_PACKAGE` in main APK.
    7. Replace hardcoded engine package references in main APK with `BuildConfig.ENGINE_PACKAGE`.
    8. Parameterize manifest engine bind permission and FileProvider authorities where package-name dependent.
    9. Ensure engine manifest permission/provider authorities derive from `${applicationId}`.
  </description>
  <verify>
    - `./gradlew :app:assembleDebug -PDUODIAN_APK_CHANNEL=main` preserves current package names.
    - A test channel build produces different main APK and engine APK package names.
    - Main APK binds only to its matching channel engine package.
  </verify>
</task>

<task type="design">
  <name>Task 7: Verify and fix Bcore data root isolation</name>
  <files>
    Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java
    Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java
    Bcore/src/main/java/top/niunaijun/blackbox/engine/*
    Bcore/src/main/cpp/*
  </files>
  <description>
    1. Audit Bcore data directory construction for hardcoded old package names or public fixed directories.
    2. Ensure engine persistent state, clone mappings, package install roots, token metadata, and virtual user directories derive from current engine `context.getPackageName()` or app private directories.
    3. Keep Phase9 `cloneInstanceId` unchanged.
    4. Add a small diagnostic log or test helper for the resolved engine data root during debug builds.
  </description>
  <verify>
    - Two channels installed on the same device resolve different engine private data directories.
    - Creating a shop in channel A does not create or modify channel B engine mapping files.
    - No code path writes channel clone state to a shared public fixed root.
  </verify>
</task>

## Wave 3: Channel-scoped announcements, versions, unified release jobs, admin UI, and verification

<task type="design">
  <name>Task 8: Channel-aware announcements, app versions, and engine versions</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/entity/Announcement.java
    admin/backend/src/main/java/com/duodian/admin/entity/AppVersion.java
    admin/backend/src/main/java/com/duodian/admin/entity/EngineVersion.java
    admin/backend/src/main/java/com/duodian/admin/controller/AnnouncementController.java
    admin/backend/src/main/java/com/duodian/admin/controller/AppVersionController.java
    admin/backend/src/main/java/com/duodian/admin/controller/EngineVersionController.java
    admin/backend/src/main/java/com/duodian/admin/service/PackageIntegrityService.java
    admin/backend/src/main/java/com/duodian/admin/repository/AnnouncementRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/AppVersionRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/EngineVersionRepository.java
  </files>
  <description>
    1. Add required `channel_id` to announcements; remove APP-visible global scope.
    2. Keep `APP_RELEASE` title fixed as `新版本发布`.
    3. APP announcement list returns only published announcements for current channel.
    4. CHANNEL admin can create/update/delete only own-channel announcements while channel is active.
    5. Add `channel_id` and `application_id` to app versions and engine versions.
    6. APP update checks return only current channel releases.
    7. Package verify validates version code, application id/package name, and checksum against the same channel release.
  </description>
  <verify>
    - Channel A APP sees A release announcement but not B release announcement.
    - Channel A APP update check sees A app and engine URLs.
    - Channel A package verify fails for a package published only under channel B.
    - Old APK without header sees only `main`.
  </verify>
</task>

<task type="design">
  <name>Task 9: Unified release job backend and worker script</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/entity/ReleaseJob.java
    admin/backend/src/main/java/com/duodian/admin/controller/ReleaseJobController.java
    admin/backend/src/main/java/com/duodian/admin/service/ReleaseJobService.java
    admin/backend/src/main/java/com/duodian/admin/repository/ReleaseJobRepository.java
    admin/scripts/release-channel-apk.sh
  </files>
  <description>
    1. Add `release_jobs` table with channel, source ref, version, app artifact, engine artifact, status, logs, requested_by, callback token hash, and timestamps.
    2. Add create/start/retry/list/callback APIs.
    3. Use one flow for `main` and non-main channels.
    4. Backend signs a short-lived release token for the current operator and passes it to the script without logging it.
    5. Script creates a temporary clean directory per run and deletes it after completion/failure.
    6. Script builds both main APK and engine APK with channel parameters.
    7. Script uploads both packages through file service with `X-Apk-Channel`.
    8. Script callbacks with app and engine URLs/checksums/file sizes/log metadata/final status.
    9. Successful callback creates/updates channel `app_versions`, `engine_versions`, and channel `APP_RELEASE` announcement.
    10. Failure never publishes versions or announcements.
  </description>
  <verify>
    - Super admin can create `main` release job and observe `COMPLETED`.
    - Super admin can create non-main channel release job and observe `COMPLETED`.
    - A branding/package mismatch causes `FAILED`.
    - Retry creates a new clean temp-dir run.
    - Worker logs do not contain release token.
  </verify>
</task>

<task type="design">
  <name>Task 10: Admin frontend role-scoped channel UX</name>
  <files>
    admin/frontend/src/router/index.js
    admin/frontend/src/views/Layout.vue
    admin/frontend/src/views/Login.vue
    admin/frontend/src/views/Channels.vue
    admin/frontend/src/views/Users.vue
    admin/frontend/src/views/Shops.vue
    admin/frontend/src/views/Logs.vue
    admin/frontend/src/views/Feedbacks.vue
    admin/frontend/src/views/Announcements.vue
    admin/frontend/src/views/AppVersions.vue
    admin/frontend/src/views/EngineVersions.vue
    admin/frontend/src/views/ReleaseJobs.vue
  </files>
  <description>
    1. Add a super-admin-only "渠道管理" page for channel profile, package ids, brand settings, status, and channel admin binding.
    2. Channel admin selector uses existing users, shows username/phone, and supports fuzzy search.
    3. Add role/type filter to user list.
    4. Add channel filters to user/shop/log/feedback/announcement/app-version/engine-version pages for super admin.
    5. Hide or disable super-admin-only menus for CHANNEL users.
    6. Disabled CHANNEL users can log in but see read-only channel data.
    7. Replace channel pool UI with CHANNEL user balance display and allocation dialog.
    8. Add release job page: select channel including `main`, source ref/version, announcement content, submit job, retry failed job, watch logs/status.
    9. Keep default pagination at 10 rows.
  </description>
  <verify>
    - Super admin sees all menus and channel filters.
    - CHANNEL sees only own channel data and cannot navigate to hidden routes manually.
    - Disabled CHANNEL cannot mutate data.
    - Super admin can create and retry release jobs from the UI.
  </verify>
</task>

<task type="design">
  <name>Task 11: Tests, migration rehearsal, and release checklist</name>
  <files>
    admin/backend/src/test/java/com/duodian/admin/service/*
    admin/backend/src/test/java/com/duodian/admin/controller/*
    CLAUDE.md
  </files>
  <description>
    1. Add backend unit/integration tests for channel registration, login, RBAC, allocation, gift rejection, disabled-channel handling, announcements, app versions, engine versions, and package verify.
    2. Add migration rehearsal notes and SQL checks for existing production data.
    3. Add release worker dry-run tests for main and non-main channels: merge success, branding mismatch, build failure, upload failure, callback success, retry success.
    4. Add admin frontend smoke checklist for super admin, active CHANNEL, and disabled CHANNEL.
    5. Add Android smoke checklist for two channel APKs with different app and engine package names.
    6. Export a current production data snapshot, restore it into local Docker MySQL, and run final acceptance against that restored local copy to mirror the online environment without writing to production.
    7. Build the APK for Xiaomi real-device validation with a temporary API base URL pointing to the local machine's Tailscale IP and local Docker nginx `/api/` proxy, for example `-PDUODIAN_API_BASE_URL=http://<本机Tailscale-IP>:8006/api/`.
    8. Before commit, restore the APK API base URL to the formal default `http://dpgj.zrnh.cn/api/` and run a repository search to prove no local Tailscale IP, temporary port, or temporary base URL remains.
    9. Update release/test process docs after implementation to make unified release job the normal release path.
  </description>
  <verify>
    - `mvn test` passes for admin backend.
    - Admin frontend builds successfully.
    - Android debug builds can be produced for `main` and one test channel.
    - Production data snapshot is restored into local Docker MySQL for final acceptance.
    - APK installed on Xiaomi real device points to local Docker through temporary Tailscale IP and validates channel/package-data isolation.
    - Pre-commit search confirms temporary Tailscale API base URL has been reverted.
  </verify>
</task>

</waves>

<completion_verification>

Completed and verified locally only, per Phase 11 constraint.

- Local admin stack: `ENV_FILE=.env.phase11-local ./admin/deploy.sh`, Docker project `phase11-local`, frontend at `http://localhost:8011`.
- Backend tests: `mvn test` passed, 55 tests, 0 failures.
- Admin frontend build: `npm run build` passed.
- Release script syntax: `bash -n admin/scripts/release-channel-apk.sh` passed.
- Channel release job #11 succeeded for channel `phase11qa062913`, app/engine version `phase11-local-final-verify`, versionCode `61012`.
- Channel release branch `release/channel/phase11qa062913-local` was auto-created/updated and points at `feature-phase11-integration` commit `15ed224`.
- Local release artifacts uploaded through the unified file service:
  - APP SHA-256 `1e12e1419dfff334e956c67dfdc0214cce31b461b50f190627331fdb3382272a`.
  - Engine SHA-256 `4966bf81f0f3b0e68af1449687d5d4f8eff19059dba817bf91c34eba08b0b944`.
- Package verification APIs returned `valid=true` for both channel APP and engine 61012 artifacts, and rejected wrong channel/package/checksum cases.
- DevTools local admin page verified:
  - channel management page shows `Phase11测试渠道` and UI-created `Phase11页面新增验证`.
  - release job page shows job #11, status success, APP/engine `61012`.
- Xiaomi real device `MIX_2S` installed channel APP and engine package:
  - `com.zhirang.channel.phase11qa062913`, versionCode `61012`, dataDir `/data/user/0/com.zhirang.channel.phase11qa062913`.
  - `com.zhirang.channel.phase11qa062913.engine`, versionCode `61012`, dataDir `/data/user/0/com.zhirang.channel.phase11qa062913.engine`.
  - Main packages remain installed separately under `com.zhirang.zhanghaoguanjia*`.
- Local database evidence shows user, compute, transaction log, announcement, app version, and engine version data isolated by `channel_id`.
- No production deployment or server mutation was performed for Phase 11.

</completion_verification>

<success_criteria>

- `channels` exists with `main` seeded and historical data backfilled.
- Role access control is centralized and enforced on every management endpoint.
- Super admin can manage channel brand, package ids, status, channel admins, and release jobs.
- Super admin grants channel capacity by adjusting `CHANNEL` user compute using existing transaction logs.
- CHANNEL admin can only manage own channel users, compute allocation, shops, logs, feedback, and announcements.
- Unified release jobs build and publish both main APK and engine APK for `main` and non-main channels.
- APP registration/login/update/announcement/package verification behavior is channel-aware.
- Cross-channel user compute transfer and data visibility are blocked.
- Main APK and engine APK package verification are channel-aware and checksum validation remains mandatory.
- Channel APKs with different `applicationId` values install side by side and use separate Android data directories.
- Final acceptance runs against local Docker restored from a current production data snapshot, not an empty database and not direct production writes.
- Xiaomi real-device APK validation uses a temporary local Tailscale API base URL that must be reverted before commit.

</success_criteria>

<verification>

Run these after implementation:

1. `cd admin/backend && mvn test`
2. `cd admin/frontend && npm run build`
3. `./gradlew :app:assembleDebug -PDUODIAN_APK_CHANNEL=main`
4. `./gradlew :app:assembleDebug -PDUODIAN_APK_CHANNEL=test-channel -PDUODIAN_APP_APPLICATION_ID=com.zhirang.testchannel -PDUODIAN_ENGINE_APPLICATION_ID=com.zhirang.testchannel.engine -PDUODIAN_APP_NAME=测试渠道 -PDUODIAN_ENGINE_NAME=测试渠道引擎`
5. Local Docker production-snapshot restore:
   - export a current production database snapshot;
   - restore the snapshot into local Docker MySQL;
   - run Phase 11 migration against the local restored copy;
   - verify row counts and `main` channel backfill before mutating test data;
   - ensure all acceptance writes happen only against the local restored copy.
6. Local Docker service smoke:
   - run the admin stack through `admin/deploy.sh` or Docker compose;
   - verify the frontend/nginx endpoint is reachable on the configured local port, default `8006`;
   - verify `/api/` is proxied to the backend through local Docker.
7. Xiaomi real-device APK smoke:
   - get the local machine Tailscale IP with `tailscale ip -4`;
   - build the APK temporarily with `-PDUODIAN_API_BASE_URL=http://<本机Tailscale-IP>:8006/api/`;
   - install on Xiaomi real device and validate login/register/channel headers/announcements/version checks/engine checks/shops/logs against local Docker;
   - before commit, rebuild or restore config with the default `http://dpgj.zrnh.cn/api/`;
   - run `rg "<本机Tailscale-IP>|DUODIAN_API_BASE_URL=http://|localhost:8006|127.0.0.1:8006" app build.gradle gradle.properties .planning` and confirm only documented examples remain.
8. Backend API smoke:
   - old request without `X-Apk-Channel` resolves to `main`;
   - register same phone in two channels;
   - login both;
   - create users/shops/logs under both;
   - assert CHANNEL admin isolation;
   - assert disabled-channel APP error;
   - assert registration bonus does not affect CHANNEL admin balance;
   - assert channel-specific announcements, app versions, and engine versions.
9. Release worker smoke:
   - create `main` release job;
   - create test channel release job;
   - validate package id and branding checks;
   - verify uploaded artifact checksums and completed callback;
   - verify failed branding check does not publish;
   - verify retry uses a fresh temp directory.
10. Device smoke:
   - install channel A APK and matching engine, verify only A announcements/update;
   - install channel B APK and matching engine, verify only B announcements/update;
   - confirm channel A and B app/engine package data directories differ;
   - create a shop in channel A and verify channel B engine mappings are unaffected.

</verification>
