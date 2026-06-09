---
phase: 11-channel-promotion-system
plan: 11
type: plan
status: planned
created_at: "2026-06-09T15:45:00+08:00"
branch: dev
wave: 1
depends_on:
  - 10-engine-permission-center
files_expected:
  - admin/backend/src/main/java/com/duodian/admin/entity/Channel.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ChannelComputeLog.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ChannelAppRelease.java
  - admin/backend/src/main/java/com/duodian/admin/service/ChannelService.java
  - admin/backend/src/main/java/com/duodian/admin/service/ChannelAppReleaseService.java
  - admin/backend/src/main/java/com/duodian/admin/service/PermissionService.java
  - admin/backend/src/main/java/com/duodian/admin/service/ChannelScopeService.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ChannelController.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ChannelAppReleaseController.java
  - admin/backend/src/main/resources/schema.sql
  - admin/frontend/src/views/Channels.vue
  - admin/frontend/src/views/ChannelAppReleases.vue
  - admin/scripts/release-channel-apk.sh
  - app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt
autonomous: false
requirements:
  - CHANNEL-ENTITY-01
  - CHANNEL-RBAC-01
  - CHANNEL-COMPUTE-01
  - CHANNEL-APP-VERSION-01
  - CHANNEL-ANNOUNCEMENT-01
  - CHANNEL-APP-CLIENT-01
---

<objective>
Design and implement a complete channel promotion system so each APK channel has isolated users, announcements, main APK releases, and user compute balances, while super administrators retain global control over channel compute pools and APK publishing.
</objective>

<context>
@.planning/phases/11-channel-promotion-system/11-CONTEXT.md
@.planning/phases/11-channel-promotion-system/11-RESEARCH.md
</context>

<architecture>

## Target Model

Introduce `channels` as the authoritative tenant boundary:

- `channels.code` is the immutable external channel identifier that corresponds to Android `BuildConfig.APK_CHANNEL`.
- `users.channel_id` is the trusted relational boundary.
- `users.apk_channel` remains for compatibility and display.
- `SUPER_ADMIN` can cross channel boundaries.
- `CHANNEL_ADMIN` is bound to exactly one `channel_id`.
- `USER` can only use APP endpoints for their own channel-scoped data.
- Channel APK releases are managed by dedicated channel release branches. Super admin starts a release job; a fixed release script merges the chosen main release branch into the channel release branch, validates channel branding resources, builds, uploads, and calls back to the backend.

Channel compute is an upstream pool. User clone create/renew continues deducting from user balance. Super admin grants compute to channel pools; channel admins allocate from channel pools to users.

</architecture>

<tasks>

<task type="design">
  <name>Task 1: Add channel schema and migration</name>
  <files>
    admin/backend/src/main/resources/schema.sql
    admin/backend/src/main/resources/data.sql
    admin/backend/src/main/java/com/duodian/admin/entity/Channel.java
    admin/backend/src/main/java/com/duodian/admin/entity/ChannelComputeLog.java
    admin/backend/src/main/java/com/duodian/admin/repository/ChannelRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/ChannelComputeLogRepository.java
    admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java
  </files>
  <description>
    1. Create `channels` with code/name/status/APK display fields/register bonus policy/compute balance/deleted/timestamps.
    2. Create `channel_compute_logs` as an immutable audit ledger.
    3. Create `channel_app_releases` for automated APK release jobs and status tracking.
    4. Add `channel_id` to `users`, `shops`, `transaction_logs`, `compute_deductions`, `feedbacks`, `announcements`, and `app_versions`.
    5. Insert or migrate a default `main` channel.
    6. Backfill historical users by `apk_channel`; create channel rows for unknown existing channel codes instead of losing attribution.
    7. Backfill dependent rows from owning user, falling back to `main` only when no owner exists.
    8. Migrate legacy `ADMIN` users to `SUPER_ADMIN`.
    9. Replace global unique phone with active uniqueness on `(channel_id, phone)` while preserving deleted semantics.
  </description>
  <verify>
    - Fresh schema creates successfully with `main` channel and super admin.
    - Existing database can be upgraded without losing users, shops, logs, announcements, or app versions.
    - A same phone can exist under two different channels, but not twice in one active channel.
  </verify>
</task>

<task type="design">
  <name>Task 2: Centralize auth context, roles, and channel scope</name>
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
    3. Add role helpers: `requireSuperAdmin`, `requireAdmin`, `requireChannelAccess(channelId)`.
    4. Enforce that `CHANNEL_ADMIN` can never query or mutate another channel by passing arbitrary query params.
    5. Keep backwards compatibility for existing tokens during rollout by loading role/channel from DB when claims are missing.
  </description>
  <verify>
    - USER cannot call admin list/create/update/delete endpoints.
    - CHANNEL_ADMIN receives 403 for another channel's user, shop, log, announcement, or app version.
    - SUPER_ADMIN can filter by any channel.
  </verify>
</task>

<task type="design">
  <name>Task 3: Implement channel management and compute pool allocation</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/controller/ChannelController.java
    admin/backend/src/main/java/com/duodian/admin/service/ChannelService.java
    admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java
    admin/backend/src/main/java/com/duodian/admin/service/UserService.java
    admin/backend/src/main/java/com/duodian/admin/controller/UserController.java
  </files>
  <description>
    1. Add super-admin channel CRUD with soft delete/disable safeguards.
    2. Add super-admin grant/deduct operation for channel compute pool with `channel_compute_logs`.
    3. Add channel-admin allocation endpoint to give compute to users in the same channel.
    4. Write both user `transaction_logs` and channel ledger entries for allocations.
    5. Change admin user edit flow so direct `computeBalance` mutation routes through a service that records who changed what and respects channel pool rules.
    6. Enforce user gift compute only within the same channel.
    7. Change registration bonus to deduct from channel pool according to channel policy.
  </description>
  <verify>
    - Super admin grants 100 compute to channel A; channel A pool increases and ledger records before/after balance.
    - Channel A admin allocates 10 to user A; channel pool decreases, user balance increases, both logs exist.
    - Channel A admin cannot allocate to channel B user.
    - User A cannot gift compute to channel B user.
    - Registration under a channel with insufficient pool follows configured policy and logs the result.
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
    2. Force channel admin queries to their own channel even when request params specify another channel.
    3. Stamp `channel_id` on all new shops, transaction logs, feedbacks, and compute deductions.
    4. Ensure APP `/shops/my`, `/logs/my`, and `/feedbacks` remain self-scoped and cannot cross channel.
    5. Update DTOs to include channel code/name where useful for admin pages.
  </description>
  <verify>
    - Channel admin list pages only return own channel rows.
    - Super admin can filter users/shops/logs/feedbacks by channel.
    - New clone create, renew, feedback submit, and logs all persist correct `channel_id`.
  </verify>
</task>

<task type="design">
  <name>Task 5: Channel-aware announcements, app versions, and release jobs</name>
  <files>
    admin/backend/src/main/java/com/duodian/admin/entity/Announcement.java
    admin/backend/src/main/java/com/duodian/admin/entity/AppVersion.java
    admin/backend/src/main/java/com/duodian/admin/entity/ChannelAppRelease.java
    admin/backend/src/main/java/com/duodian/admin/controller/AnnouncementController.java
    admin/backend/src/main/java/com/duodian/admin/controller/AppVersionController.java
    admin/backend/src/main/java/com/duodian/admin/controller/ChannelAppReleaseController.java
    admin/backend/src/main/java/com/duodian/admin/service/ChannelAppReleaseService.java
    admin/backend/src/main/java/com/duodian/admin/service/PackageIntegrityService.java
    admin/backend/src/main/java/com/duodian/admin/repository/AnnouncementRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/AppVersionRepository.java
    admin/backend/src/main/java/com/duodian/admin/repository/ChannelAppReleaseRepository.java
    admin/scripts/release-channel-apk.sh
  </files>
  <description>
    1. Add `scope` and `channel_id` to announcements.
    2. Keep `APP_RELEASE` title fixed as `新版本发布`.
    3. APP announcement list returns published global announcements plus published announcements for the current channel.
    4. Channel admin can create/update/delete only own channel announcements; cannot create global announcements.
    5. Add `channel_id` to app versions and require super admin to bind each main APK release to a channel.
    6. APP update check returns only current channel releases.
    7. Package verify validates version code and checksum against the same channel release; another channel's same version code must not pass.
    8. Add channel APK release job APIs: create job, start job, list jobs, authenticated worker callback.
    9. Implement a fixed release script that:
       - checks out `release/channel/{channelCode}`;
       - merges the selected main release branch;
       - validates APK channel identifier, app name, launcher icon, and engine display/notification name against channel config;
       - builds the channel APK;
       - uploads through the file service;
       - calls backend callback with URL, checksum, file size, logs, and final status.
    10. On successful callback, create/update the channel `app_versions` row and create the channel `APP_RELEASE` announcement using the super-admin-provided announcement content.
  </description>
  <verify>
    - Channel A APP sees A release announcement but not B release announcement.
    - Channel A APP update check sees A APK URL.
    - Channel A package verify fails for a package published only under channel B.
    - Channel admin can publish normal channel announcement, but not global announcement or app version.
    - Super admin can start a release job and observe status transitions through `PENDING/MERGING/VALIDATING/BUILDING/UPLOADING/COMPLETED`.
    - A branding mismatch causes `FAILED` with a readable error and does not publish an app version or announcement.
    - Worker callback without valid token/HMAC is rejected.
  </verify>
</task>

<task type="design">
  <name>Task 6: Admin frontend role-scoped channel UX</name>
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
    admin/frontend/src/views/ChannelAppReleases.vue
    admin/frontend/src/views/EngineVersions.vue
  </files>
  <description>
    1. Add a super-admin-only "渠道管理" page for channel profile, brand settings, channel pool balance, and channel admins.
    2. Add channel filters to user/shop/log/feedback/announcement/app-version pages for super admin.
    3. Hide or disable super-admin-only menus for channel admins: channels, platform writes, engine versions, app version upload.
    4. Show channel admin's channel name and remaining channel pool in layout/header.
    5. Replace direct user compute editing with an allocation dialog that calls channel-aware API.
    6. Add channel field to announcement and app version tables.
    7. Add a super-admin release workflow: select channel, select/enter main release branch or version, fill release announcement, submit job, watch release status/logs.
    8. Keep default pagination at 10 rows.
  </description>
  <verify>
    - Super admin sees all menus and channel filters.
    - Channel admin sees only allowed menus and cannot navigate to hidden routes manually.
    - Channel admin allocation dialog prevents cross-channel user selection.
    - Super admin can create a channel APK release job from the UI and see completion/failure details.
  </verify>
</task>

<task type="design">
  <name>Task 7: Android channel-aware client behavior</name>
  <files>
    app/build.gradle
    app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/network/AuthInterceptor.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/AnnouncementRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/data/AppVersionRepository.kt
    app/src/main/java/com/zhirang/zhanghaoguanjia/update/AppUpdateManager.kt
  </files>
  <description>
    1. Continue using `BuildConfig.APK_CHANNEL` as the packaged immutable channel identifier.
    2. Add `apkChannel` to login request and an `X-Apk-Channel` header for channel-sensitive requests.
    3. On login response, verify returned user channel matches `BuildConfig.APK_CHANNEL`; otherwise clear token and show a channel mismatch error.
    4. Ensure registration, announcements, app versions, and package verify all include current channel context.
    5. Keep main APK version update flow login-gated.
    6. Document build parameters and branch resource expectations for channel APKs: `DUODIAN_APK_CHANNEL`, `DUODIAN_APP_NAME`, launcher icon replacement, engine display/notification name, channel release branch, and upload/callback flow.
  </description>
  <verify>
    - Two debug APKs built with different `DUODIAN_APK_CHANNEL` values register into different backend channels.
    - Channel A APK does not show channel B announcement or update.
    - Login to an account from a different channel is rejected or resolves to that channel's separate account according to backend behavior.
  </verify>
</task>

<task type="design">
  <name>Task 8: Tests, migration rehearsal, and release checklist</name>
  <files>
    admin/backend/src/test/java/com/duodian/admin/service/*
    admin/backend/src/test/java/com/duodian/admin/controller/*
    CLAUDE.md
  </files>
  <description>
    1. Add backend unit/integration tests for channel registration, login, RBAC, allocation, gift rejection, announcements, app versions, and package verify.
    2. Add migration rehearsal notes and SQL checks for existing production data.
    3. Add channel release worker dry-run tests for merge success, branding mismatch, build failure, upload failure, and callback success.
    4. Add admin frontend smoke checklist for super admin and channel admin.
    5. Add Android smoke checklist for two channel APKs.
    6. Update release/test process docs after implementation.
  </description>
  <verify>
    - `mvn test` passes for admin backend.
    - Admin frontend builds successfully.
    - Android debug build can be produced with at least two channel values.
    - Manual smoke validates isolation.
  </verify>
</task>

</tasks>

<success_criteria>

- `channels` and `channel_compute_logs` exist, with `main` seeded and historical data backfilled.
- Role access control is centralized and enforced on every management endpoint.
- Super admin can manage channel brand, channel compute pool, channel admins, and channel APK releases.
- Super admin channel APK release jobs automatically merge main release into channel release, validate channel resources, build, upload, callback, and publish channel app version/announcement.
- Channel admin can only manage own channel users, compute allocation, shops, logs, feedback, and announcements.
- APP registration/login/update/announcement behavior is channel-aware.
- Cross-channel user compute transfer and data visibility are blocked.
- Main APK package verification is channel-aware and checksum validation remains mandatory.

</success_criteria>

<verification>

Run these after implementation:

1. `cd admin/backend && mvn test`
2. `cd admin/frontend && npm run build`
3. `./gradlew :app:assembleDebug -PDUODIAN_APK_CHANNEL=main`
4. `./gradlew :app:assembleDebug -PDUODIAN_APK_CHANNEL=test-channel -PDUODIAN_APP_NAME=测试渠道`
5. Backend API smoke:
   - register same phone in two channels;
   - login both;
   - create users/shops/logs under both;
   - assert channel admin isolation;
   - assert super admin visibility;
   - assert channel-specific announcements and app versions.
6. Channel release worker smoke:
   - create test channel release job;
   - validate branch merge and branding checks;
   - verify uploaded artifact checksum and completed callback;
   - verify failed branding check does not publish.
7. Device smoke:
   - install channel A APK and verify only A announcements/update;
   - install channel B APK and verify only B announcements/update.

</verification>
