---
phase: 11-channel-promotion-system
status: complete
created_at: "2026-06-09T15:35:00+08:00"
updated_at: "2026-06-10T00:00:00+08:00"
---

# Phase 11: 渠道推广完整体系 - Research

## Existing Codebase Findings

### Channel Is Present But Not Authoritative

The existing `apkChannel` is only a registration attribute:

- Android: `BuildConfig.APK_CHANNEL` is generated from Gradle property `DUODIAN_APK_CHANNEL`, default `main`.
- Android: `UserRepository.register()` sends `apkChannel` in `RegisterRequest`.
- Backend: `RegisterRequest.apkChannel` is saved to `User.apkChannel`.
- Backend/Admin: user list displays `apkChannel`.

This is insufficient because there is no `channels` table, no channel administrator binding, no channel scope on announcements/versions, and no access control based on channel.

### Role Model Is Too Coarse

The backend treats role as a string:

- `ADMIN` means unrestricted admin in current controllers.
- `USER` is normal APP user.
- `AuthContext` stores only `userId`.
- Controllers repeatedly call `getCurrentUser()` and `isAdmin()` where needed, but many management controllers do not enforce admin at all.

Phase 11 needs a centralized `CurrentUser`/`PermissionService` layer so every management controller can apply `SUPER_ADMIN` vs `CHANNEL` vs `USER`.

### Admin API Has Broad Surface Area

The following controllers need channel scope:

- `UserController`
- `ShopController`
- `TransactionLogController`
- `FeedbackController`
- `AnnouncementController`
- `AppVersionController`
- `EngineVersionController`
- `FileController` for app/engine package uploads
- `PlatformController` for read visibility if platform availability becomes channel-specific later

The following should remain super-admin only:

- channel create/update/delete
- channel administrator binding
- unified release job creation/start/retry
- platform package metadata unless explicitly delegated later

### Billing Uses User Balances

Current balances:

- User balance: `users.compute_balance`.
- Non-transferable user balance: `users.non_transferable_compute_balance`.
- Deduction idempotency: `compute_deductions`.
- User-visible logs: `transaction_logs`.

Phase 11 business decision:

- A channel administrator is a registered user with role `CHANNEL`; that user's `compute_balance` is the channel's distributable balance.
- Super admin grants compute by adjusting the selected `CHANNEL` user's balance through the existing user compute adjustment flow.
- Channel admin allocates compute to same-channel users by deducting from the channel admin user's balance and increasing the target user's balance in one transaction.
- Both sides should be represented in existing `transaction_logs`.
- Registration bonus compute is a promotion/experience grant and is not deducted from the channel admin balance.
- Clone creation/renewal continues deducting from the user's own balance, not from channel admin balance.

### Version And Announcement Filtering

Current APP calls:

- `GET /announcements?published=true&type=...`
- `GET /app-versions?published=true`
- `GET /engine-versions?available=true`
- `POST /app-versions/verify`
- `POST /engine-versions/verify`

Phase 11 behavior:

- New APK sends `X-Apk-Channel: BuildConfig.APK_CHANNEL` on every API request through the unified OkHttp interceptor.
- Login/register may also retain body `apkChannel` temporarily for compatibility, but the header is the unified channel signal.
- Old APKs without the header fall back to `main`.
- Announcements, app versions, engine versions, and package verification are all channel-scoped.
- No APP-visible global announcement scope remains; old APK sees `main`.
- Do not add special token/header mismatch rejection in Phase 11.

### APK Branding, Package Identity, And Data Directories

Android launcher label, icon, `applicationId`, provider authorities, custom permissions, and service binding targets are packaged resources or manifest values. The backend cannot change them after build.

Practical design:

- Store channel build config in `channels`:
  - APK channel code
  - main APK display name
  - main APK `applicationId`
  - main APK launcher icon asset/checksum
  - engine APK display name
  - engine APK `applicationId`
  - engine notification title/text
  - engine icon asset/checksum
- Android build accepts Gradle properties:
  - `DUODIAN_APK_CHANNEL`
  - `DUODIAN_APP_NAME`
  - `DUODIAN_APP_APPLICATION_ID`
  - `DUODIAN_ENGINE_NAME`
  - `DUODIAN_ENGINE_APPLICATION_ID`
  - optional icon resource replacement inputs
- Main APK should expose `BuildConfig.ENGINE_PACKAGE` and manifest placeholders for engine bind permission.
- Engine APK manifest custom permission/provider authorities should derive from `${applicationId}`.
- Different channels with different main APK and engine APK `applicationId` get separate Android app data directories automatically.
- Wave 2 must verify Bcore data roots are based on current engine context/package name rather than hardcoded old package or public fixed directories.

### Unified Release Automation Security

Backend-triggered builds are effectively remote code execution if implemented carelessly. The safe model is:

- Do not execute arbitrary commands from request payloads.
- Use one fixed release script path with strictly validated parameters.
- Every run creates a temporary clean working directory and deletes it after completion/failure.
- Retry is initiated by a management-backend button and starts a fresh run.
- Backend supplies a short-lived release token tied to the current operator; the token must not be printed, persisted, or included in logs.
- The script must send `X-Apk-Channel` when uploading app/engine packages and when calling release callbacks.
- Store job logs and final git commit/tag/branch names for audit, with secrets redacted.
- Fail closed on dirty temp workspace, merge conflict, resource mismatch, build failure, upload failure, checksum mismatch, or callback failure.

## Proposed Domain Model

### channels

Fields:

- `id`
- `code` unique, immutable once used
- `name`
- `admin_user_id` references `users.id` where role is `CHANNEL`
- `app_display_name`
- `app_application_id`
- `app_icon_url`
- `app_icon_checksum`
- `engine_display_name`
- `engine_application_id`
- `engine_icon_url`
- `engine_icon_checksum`
- `engine_notification_title`
- `engine_notification_text`
- `brand_color`
- `register_bonus_compute`
- `status`: `ACTIVE/DISABLED`
- `remark`
- `deleted`
- timestamps

No `compute_balance` field in Phase 11.

### users additions

- `channel_id` nullable during migration, then not null for normal APP users and channel admins.
- `apk_channel` remains denormalized for compatibility and display.
- roles:
  - `SUPER_ADMIN`
  - `CHANNEL`
  - `USER`
- Active normal user uniqueness changes from global `phone` to active uniqueness on `(channel_id, phone)`.
- Admin/channel login requires phone uniqueness among `SUPER_ADMIN` and `CHANNEL` users. Enforce in service layer, with an optional generated-column/index strategy if practical.

### app_versions / engine_versions additions

- `channel_id` not null after migration.
- optional `channel_code` denormalized for display.
- `application_id` stores the expected package name for the artifact.
- unique `(channel_id, version_code, deleted)` or active-only equivalent per version table.
- `published` applies within channel.
- package verification uses `(channel_id, version_code, checksum, application_id/package_name)`.

### release_jobs

Release automation should use a job table instead of overloading `app_versions`:

- `id`
- `channel_id`
- `source_release_ref`
- `output_branch`
- `version_code`
- `version_name`
- `announcement_content`
- `status`: `PENDING/MERGING/VALIDATING/BUILDING/UPLOADING/COMPLETED/FAILED`
- `app_version_id`
- `engine_version_id`
- `app_apk_url`
- `app_checksum`
- `app_file_size`
- `engine_apk_url`
- `engine_checksum`
- `engine_file_size`
- `log_url` or `log_text`
- `requested_by`
- `started_at`
- `completed_at`
- `error_message`
- `callback_token_hash`
- `deleted`
- timestamps

`app_versions`, `engine_versions`, and the channel `APP_RELEASE` announcement are created or marked published only after the release job reaches `COMPLETED`.

### announcements additions

- `channel_id` required.
- `type`: existing `NORMAL` / `APP_RELEASE` etc.
- `APP_RELEASE` announcements are channel-aware; title remains fixed `新版本发布`.
- APP list returns only published announcements for the current channel.

### shops / transaction_logs / feedbacks / compute_deductions additions

- add `channel_id`.
- Existing rows are backfilled to `main`.
- New writes set `channel_id` at creation time.
- Admin searches filter by channel according to current user role.

## Access Control Matrix

| Capability | SUPER_ADMIN | CHANNEL | USER |
|---|---:|---:|---:|
| Manage channels | Yes | No | No |
| Bind channel administrator | Yes | No | No |
| Increase CHANNEL user compute | Yes | No | No |
| Allocate compute to channel users | Any channel if needed | Own channel only, from own balance | No |
| Create/retry release jobs | Yes | No | No |
| Read release jobs | All channels | Own channel only | No |
| Manage platform config | Yes | Read only if needed | No |
| List users | All channels | Own channel | Self via APP only |
| List shops/logs/feedback | All channels | Own channel | Own data via APP endpoints |
| Publish announcements | Any channel | Own channel only if channel active | No |
| Publish app/engine versions | Via release jobs | No | No |

When a channel is disabled:

- APP/API requests for that channel return `该产品暂不可用，请联系：xxx（渠道管理员的手机号）`.
- The channel administrator may still log in to the management backend.
- The channel administrator receives read-only access and cannot allocate compute, publish announcements, or publish versions.

## API Design

### Auth And Channel Resolution

- APP networking adds `X-Apk-Channel` on all API requests.
- `POST /auth/login`
  - APP login resolves by `X-Apk-Channel + phone`, falling back to body `apkChannel`, then `main`.
  - Admin login resolves globally for `SUPER_ADMIN` and `CHANNEL` roles without requiring channel selection.
- `POST /auth/register`
  - resolves active channel by `X-Apk-Channel`, falling back to body `apkChannel`, then `main`.
  - creates user with `channel_id` and `apk_channel`.
  - grants registration bonus according to channel config, without deducting channel admin balance.
- JWT claims should include:
  - `role`
  - `channelId`
  - `apkChannel`
- Existing tokens without these claims remain compatible by loading role/channel from DB.

### Channels

- `GET /channels`
- `POST /channels`
- `PUT /channels/{id}`
- `DELETE /channels/{id}` soft delete / disable safe checks
- `GET /channels/admin-candidates?keyword=...`
  - returns existing registered users eligible for role `CHANNEL`, showing username and phone.
- `POST /channels/{id}/admin`
  - binds an existing user as channel administrator.

### User Compute Allocation

- Super admin uses the existing user compute adjustment endpoint/UI on users filtered to role `CHANNEL`.
- `POST /users/{id}/compute/allocate`
  - channel admin only for own channel and active channel.
  - deducts from current `CHANNEL` admin user's `compute_balance`.
  - increments target same-channel user's `compute_balance`.
  - writes existing `transaction_logs` for audit.
- User gift compute remains same-channel only.

### Unified Release Jobs

- `POST /release-jobs`
  - super admin only.
  - request: `channelId`, `sourceReleaseRef`, `versionCode`, `versionName`, `announcementContent`.
  - creates a `PENDING` job and returns job id.
- `POST /release-jobs/{id}/start`
  - super admin only or internal scheduler.
  - invokes the fixed release worker/script asynchronously.
- `POST /release-jobs/{id}/retry`
  - super admin only.
  - creates or restarts a fresh temp-dir run from a failed job.
- `POST /release-jobs/{id}/callback`
  - worker only, authenticated with release token/HMAC.
  - updates status, app/engine URLs, checksums, file sizes, logs, and creates/updates `app_versions`, `engine_versions`, and release announcement on success.
- `GET /release-jobs`
  - super admin sees all; channel admin can read own channel jobs.

Worker script phases:

1. Create a temporary working directory.
2. Fetch the configured repository/source ref.
3. For `main`, build from `release/{versionName}` or selected `sourceReleaseRef`.
4. For non-main channels, checkout/create `release/channel/{channelCode}/{versionName}` and merge the selected source ref.
5. Validate channel config:
   - `BuildConfig.APK_CHANNEL == channels.code`
   - main APK `applicationId == channels.app_application_id`
   - engine APK `applicationId == channels.engine_application_id`
   - app name equals `channels.app_display_name`
   - launcher icon exists and matches configured checksum or asset id
   - engine display/notification name matches channel config
6. Build release main APK.
7. Build release engine APK.
8. Compute MD5/SHA-256 and file sizes for both APKs.
9. Upload both artifacts through existing file service with `X-Apk-Channel`.
10. Callback backend with artifact metadata and final status.
11. Push output branch and optional channel tag if configured.
12. Delete the temporary directory.

### Channel-Sensitive Reads

- Existing list endpoints add optional `channelId/channelCode` filters for super admin.
- For channel admin, backend ignores arbitrary channel filters and forces own channel.
- For APP endpoints, backend uses authenticated user/channel resolution and `X-Apk-Channel` fallback rules.

### Versions

- `GET /app-versions?published=true`
  - APP: returns current channel only.
  - Admin: super admin can filter by channel; channel admin sees own channel read-only.
- `GET /engine-versions?available=true`
  - same channel behavior as app versions.
- `POST /app-versions/verify`
  - verifies against a published version in the same channel.
- `POST /engine-versions/verify`
  - verifies against a published engine version in the same channel.

### Announcements

- `GET /announcements?published=true&type=...`
  - APP sees only current channel announcements.
  - Admin sees role-scoped data.
- Admin create/update requires channel.
- Super admin can choose any channel.
- Channel admin can only create own-channel announcements while channel is active.

## Migration Strategy

1. Confirm latest database backup exists and copy it off-host before migration.
2. Create `channels` and insert `main`.
3. Add nullable `channel_id` to all channel-scoped tables.
4. Backfill:
   - users: all existing rows to `main`.
   - shops/logs/feedbacks/compute_deductions: from owning user when possible, otherwise `main`.
   - announcements/app_versions/engine_versions: `main`.
5. Convert old role `ADMIN` to `SUPER_ADMIN`.
6. Add role `CHANNEL`; no existing users become `CHANNEL` automatically unless explicitly configured.
7. Run duplicate/preflight checks.
8. Replace old global phone unique behavior with active `(channel_id, phone)` uniqueness for normal users plus service-level global uniqueness for admin/channel login accounts.
9. Make channel columns non-null where safe.
10. Deploy backend with backward-compatible request handling.
11. Deploy admin frontend role-scoped UI.
12. Release APP update that sends `X-Apk-Channel` and handles unified server errors.
13. Roll out release worker on the build host with restricted credentials.
14. Enable unified release jobs for `main` first, then non-main channels.

## Verification Strategy

- Unit tests for `ChannelScopeService` and `PermissionService`.
- Integration tests:
  - old APK without `X-Apk-Channel` sees `main`;
  - same phone can register in two channels;
  - channel admin phone is globally unique for admin login;
  - channel A admin cannot see channel B users/logs/shops;
  - channel admin allocation deducts own balance and increments target user balance atomically;
  - registration bonus does not reduce channel admin balance;
  - cross-channel gift is rejected;
  - disabled channel APP request returns the required user-facing message;
  - APP channel A sees only A announcements;
  - APP channel A update check sees only A app/engine versions;
  - package verify fails when version exists in another channel only.
- Integration tests for release job status transitions and callback authentication.
- Script dry run for `main` and one non-main test channel:
  - merge/build success path;
  - resource mismatch fail path;
  - upload/callback success path;
  - retry path creates a fresh temp directory.
- Migration test using existing `main` seed data.
- Admin UI smoke test for super admin and channel admin accounts.
- Android smoke test by building two debug channel APKs with different `DUODIAN_APK_CHANNEL`, main APK `applicationId`, and engine APK `applicationId`.
- Wave 2 data isolation verification:
  - both channels can be installed on one device;
  - each channel binds to its own engine package;
  - each channel has separate Android app data directories;
  - Bcore data roots do not use hardcoded old package names or shared public fixed directories.
