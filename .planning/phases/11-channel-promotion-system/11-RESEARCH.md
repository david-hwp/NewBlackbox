---
phase: 11-channel-promotion-system
status: complete
created_at: "2026-06-09T15:35:00+08:00"
---

# Phase 11: 渠道推广完整体系 - Research

## Existing Codebase Findings

### Channel Is Present But Not Authoritative

The existing `apkChannel` is only a registration attribute:

- Android: `BuildConfig.APK_CHANNEL` is generated from Gradle property `DUODIAN_APK_CHANNEL`, default `main`.
- Android: `UserRepository.register()` sends `apkChannel` in `RegisterRequest`.
- Backend: `RegisterRequest.apkChannel` is saved to `User.apkChannel`.
- Backend/Admin: user list displays `apkChannel`.

This is insufficient for channel operations because there is no channel table, no channel admin, no channel balance, no channel scope on announcements or versions, and no access control based on channel.

### Role Model Is Too Coarse

The backend treats role as a string:

- `ADMIN` means unrestricted admin in current controllers.
- `USER` is normal APP user.
- `AuthContext` stores only `userId`.
- Controllers repeatedly call `getCurrentUser()` and `isAdmin()` where needed, but many management controllers do not enforce admin at all.

Phase 11 needs a centralized `CurrentUser`/`PermissionService` layer so every management controller can apply `SUPER_ADMIN` vs `CHANNEL_ADMIN` vs `USER`.

### Admin API Has Broad Surface Area

The following controllers need channel scope:

- `UserController`
- `ShopController`
- `TransactionLogController`
- `FeedbackController`
- `AnnouncementController`
- `AppVersionController`
- `PlatformController` for read visibility if platform availability becomes channel-specific later

The following should remain super-admin only:

- `EngineVersionController`
- global file upload for app/engine packages
- channel create/update/delete
- channel APK publishing/uploading
- platform package metadata unless explicitly delegated later

### Billing Needs A Channel Ledger

Current balances:

- User balance: `users.compute_balance`.
- Non-transferable user balance: `users.non_transferable_compute_balance`.
- Deduction idempotency: `compute_deductions`.
- User-visible logs: `transaction_logs`.

Needed additions:

- Channel pool: `channels.compute_balance`.
- Channel immutable ledger: `channel_compute_logs` or a generalized ledger with `owner_type`.
- Channel admin allocation to users must be transactional:
  - lock channel row;
  - ensure channel balance >= amount;
  - decrement channel pool;
  - lock target user in same channel;
  - increment user balance;
  - write channel ledger and user transaction log.

User clone creation/renewal should continue deducting from the user balance, not channel pool. Channel pool is only upstream inventory for allocation.

### Version And Announcement Filtering

Current APP calls:

- `GET /announcements?published=true&type=...`
- `GET /app-versions?published=true`
- `POST /app-versions/verify`

Those endpoints currently return global rows. In Phase 11:

- APP should send channel explicitly or backend should infer from token.
- Startup/version checks require login according to the previous requirement, so token-based inference is acceptable for logged-in flows.
- Registration and login happen before token exists, so they must send `apkChannel`.
- To avoid token/channel mismatch after an app is rebranded or data is restored, APP should include `X-Apk-Channel` header or query parameter in channel-sensitive public/pre-login requests, and backend should validate token user channel matches request channel for logged-in requests.

### APK Branding And Branch Constraint

Android launcher label and icon are packaged resources. The backend cannot change an installed APK's icon/name dynamically. Practical design:

- Store channel brand config in `channels`: `apk_display_name`, `apk_icon_url`, `brand_color`, etc.
- Android build accepts Gradle properties:
  - `DUODIAN_APK_CHANNEL`
  - `DUODIAN_APP_NAME`
  - optional icon resource replacement inputs
- Each channel APK is managed on a dedicated release branch, for example `release/channel/{channelCode}`.
- Super admin initiates a release in the admin UI; a controlled release worker merges the main release branch into the channel release branch, validates channel resources, builds the APK, uploads it through the file service, and calls a backend callback to mark release complete.

This matches the user's requirement that APK publishing is still super-admin-managed.

### Release Automation Security

Backend-triggered builds are effectively remote code execution if implemented carelessly. The safe model is:

- Do not execute arbitrary commands from request payloads.
- Use a fixed script path, fixed workspace root, fixed branch naming rules, and strictly validated parameters.
- Run the release worker as a restricted OS user with a clean git workspace.
- Require a one-time release job token or HMAC callback token so only the worker can mark a task complete.
- Store job logs and final git commit/tag/branch names for audit.
- Fail closed on dirty working tree, merge conflict, resource mismatch, build failure, upload failure, or checksum mismatch.

## Proposed Domain Model

### channels

Fields:

- `id`
- `code` unique, immutable once used
- `name`
- `apk_display_name`
- `apk_icon_url`
- `brand_color`
- `compute_balance`
- `register_bonus_compute`
- `register_bonus_policy`: `GRANT_IF_AVAILABLE` default, future `REJECT_IF_INSUFFICIENT`
- `status`: `ACTIVE/DISABLED`
- `remark`
- `deleted`
- timestamps

### users additions

- `channel_id` nullable during migration, then not null.
- `apk_channel` remains denormalized for compatibility and display.
- unique index changes from `phone` to `(channel_id, phone, deleted)` or equivalent active uniqueness strategy.
- roles:
  - `SUPER_ADMIN`
  - `CHANNEL_ADMIN`
  - `USER`

### app_versions additions

- `channel_id` not null after migration.
- optional `channel_code` denormalized for display.
- unique `(channel_id, version_code, deleted)` or active-only equivalent.
- `published` applies within channel.

### channel_app_releases

Release automation should use a separate job table instead of overloading `app_versions`:

- `id`
- `channel_id`
- `main_release_branch`
- `channel_release_branch`
- `version_code`
- `version_name`
- `announcement_content`
- `status`: `PENDING/MERGING/VALIDATING/BUILDING/UPLOADING/COMPLETED/FAILED`
- `app_version_id`
- `apk_url`
- `checksum`
- `file_size`
- `log_url` or `log_text`
- `requested_by`
- `started_at`
- `completed_at`
- `error_message`
- `callback_token_hash`
- `deleted`
- timestamps

`app_versions` is created or marked published only after the release job reaches `COMPLETED`.

### announcements additions

- `scope`: `GLOBAL/CHANNEL`
- `channel_id` nullable for `GLOBAL`, required for `CHANNEL`
- APP list returns global published announcements plus current channel published announcements.
- `APP_RELEASE` announcements are channel-aware; title remains fixed `新版本发布`.

### shops / transaction_logs / feedbacks / compute_deductions additions

- add `channel_id`.
- Existing rows are backfilled from owning user where possible, fallback `main`.
- New writes set `channel_id` at creation time.
- Admin searches filter by channel according to current user role.

### channel_compute_logs

Immutable ledger for channel pool operations:

- `id`
- `channel_id`
- `type`: `SUPER_ADMIN_GRANT`, `ADMIN_ALLOCATE_TO_USER`, `REGISTER_BONUS`, `ADJUSTMENT`, future `REVERSAL`
- `amount`
- `balance_before`
- `balance_after`
- `target_user_id`
- `operator_user_id`
- `remark`
- `deleted`
- `created_at`

## Access Control Matrix

| Capability | SUPER_ADMIN | CHANNEL_ADMIN | USER |
|---|---:|---:|---:|
| Manage channels | Yes | No | No |
| Grant channel pool compute | Yes | No | No |
| Create channel admins | Yes | No | No |
| Upload/publish channel APK | Yes | Read own channel only | No |
| Manage engine versions | Yes | No | No |
| Manage platform config | Yes | Read only if needed | No |
| List users | All channels | Own channel | Self via APP only |
| Adjust user compute | Any channel | Own channel, from channel pool | No |
| List shops/logs/feedback | All channels | Own channel | Own data via APP endpoints |
| Publish announcements | Global or any channel | Own channel only | No |

## API Design

### Auth

- `POST /auth/login`
  - request adds `apkChannel`.
  - APP login requires `apkChannel`; admin login may omit only for `SUPER_ADMIN` legacy account during migration, but preferred admin login should also resolve channel admins by phone plus channel context or use unique admin phone.
- `POST /auth/register`
  - must resolve active channel by `apkChannel`.
  - creates user with `channel_id` and `apk_channel`.
  - grants registration bonus from channel pool according to channel policy.
- JWT claims:
  - `role`
  - `channelId`
  - `apkChannel`

### Channels

- `GET /channels`
- `POST /channels`
- `PUT /channels/{id}`
- `DELETE /channels/{id}` soft delete / disable safe checks
- `POST /channels/{id}/compute/grant` super admin increases/decreases channel pool with ledger
- `GET /channels/{id}/compute-logs`
- `POST /channels/{id}/admins`

### Channel APK Release Jobs

- `POST /channel-app-releases`
  - super admin only.
  - request: `channelId`, `mainReleaseBranch`, `versionCode`, `versionName`, `announcementContent`.
  - creates a `PENDING` job and returns job id.
- `POST /channel-app-releases/{id}/start`
  - super admin only or internal scheduler.
  - invokes the fixed release worker/script asynchronously.
- `POST /channel-app-releases/{id}/callback`
  - worker only, authenticated with callback token/HMAC.
  - updates status, apk URL, checksum, file size, log URL/text, and creates/updates `app_versions` plus release announcement on success.
- `GET /channel-app-releases`
  - super admin sees all; channel admin can read own channel jobs.

Worker script phases:

1. Verify clean workspace and fetch latest branches.
2. Checkout `release/channel/{channelCode}`.
3. Merge `mainReleaseBranch` into channel branch.
4. Validate:
   - Gradle channel config equals `channels.code`.
   - app name equals `channels.apk_display_name`.
   - launcher icon exists and matches configured checksum or asset id.
   - engine display/notification name matches channel config.
5. Build release APK.
6. Compute MD5/SHA-256 and file size.
7. Upload through existing file service as `app-packages`.
8. Callback backend with artifact metadata and final status.
9. Push updated channel release branch and optional channel tag if configured.

### User Compute Allocation

- `POST /users/{id}/compute/allocate`
  - channel admin only for own channel.
  - super admin can optionally allocate directly or should prefer channel grant first.
  - writes user `transaction_logs` and `channel_compute_logs`.

### Channel-Sensitive Reads

- Existing list endpoints add optional `channelId/channelCode` filters for super admin.
- For channel admin, backend ignores arbitrary channel filters and forces own channel.
- For APP endpoints, backend uses authenticated user channel.

### Versions

- `GET /app-versions?published=true`
  - for APP: returns current channel only. Super admin list can filter by channel.
- `POST /app-versions/verify`
  - request adds `channelCode` or uses token channel.
  - verifies against a published version in the same channel.
- Admin create/update requires channel for app versions.

### Announcements

- `GET /announcements?published=true&type=...`
  - APP sees `GLOBAL` plus own channel.
  - Admin sees role-scoped data.
- Admin create/update requires:
  - super admin can choose `GLOBAL` or any channel;
  - channel admin can only create `CHANNEL` announcement for own channel.

## Migration Strategy

1. Create `channels` and insert `main`.
2. Add nullable `channel_id` to all channel-scoped tables.
3. Backfill:
   - users: `channel_id = channels.id where channels.code = users.apk_channel`, unknown codes create disabled channels or map to `main` after audit. Recommendation: create active/inactive channel rows for distinct existing `apk_channel` values to preserve attribution.
   - shops/logs/feedbacks/compute_deductions: from owning user channel.
   - announcements/app_versions: `main`, unless manually reclassified.
4. Convert old role `ADMIN` to `SUPER_ADMIN`.
5. Add unique constraints/indexes after data cleanup.
6. Deploy backend with backward-compatible request handling.
7. Deploy admin frontend role-scoped UI.
8. Release APP update that sends channel on login and uses channel-scoped version/announcement behavior.
9. Roll out release worker on the build host with SSH/Git credentials and file-service credentials scoped to app-package upload only.

## Verification Strategy

- Unit tests for `ChannelScopeService` and `PermissionService`.
- Integration tests:
  - same phone can register in two channels;
  - channel A admin cannot see channel B users/logs/shops;
  - channel admin allocation decrements channel pool and increments user balance atomically;
  - cross-channel gift is rejected;
  - APP channel A sees only A/global announcements;
  - APP channel A update check sees only A versions;
  - package verify fails when version exists in another channel only.
- Integration tests for channel APK release job status transitions and callback authentication.
- Script dry run against a test channel release branch:
  - merge success path;
  - resource mismatch fail path;
  - upload/callback success path.
- Migration test using existing `main` seed data.
- Admin UI smoke test for super admin and channel admin accounts.
- Android smoke test by building two debug channel APKs with different `DUODIAN_APK_CHANNEL` values and confirming registration/update/announcement isolation.
