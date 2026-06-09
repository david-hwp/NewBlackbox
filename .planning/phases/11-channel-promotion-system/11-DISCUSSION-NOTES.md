---
phase: 11-channel-promotion-system
type: discussion-notes
status: folded
created_at: "2026-06-09T23:05:30+08:00"
folded_into:
  - 11-CONTEXT.md
  - 11-RESEARCH.md
  - 11-PLAN.md
---

# Phase 11 Discussion Notes

These notes capture agreed discussion outcomes that should be folded into the formal Phase 11 plan after open questions are resolved.

## 2026-06-09 - APK channel propagation

- `X-Apk-Channel` should be treated as a standard HTTP request header.
- APP networking should add `X-Apk-Channel: BuildConfig.APK_CHANNEL` in the unified OkHttp interceptor for all Retrofit API requests, regardless of login state.
- Registration currently sends `apkChannel` in the JSON body. Keep it temporarily for backward compatibility, but prefer the request header as the unified channel source going forward.
- Backend channel resolution priority should be:
  1. For authenticated requests, trust the token/user database channel.
  2. For login/register and other unauthenticated channel-sensitive requests, read `X-Apk-Channel`.
  3. If the header is absent for old clients, fall back to `main`.
  4. During rollout, tolerate body `apkChannel` as a compatibility fallback.
- This compatibility path is required so existing online users and older APK builds are not locked out during Phase 11 rollout.

## 2026-06-09 - Review resolution decisions

- Historical production data currently has no real channel split, so Phase 11 migration should assign all existing rows to the default `main` channel. This supersedes the earlier concern about preserving existing non-main channel attribution.
- Adopt the safer phone uniqueness migration sequence:
  1. Add nullable channel columns and default `main` channel data.
  2. Backfill users and dependent rows.
  3. Run duplicate/preflight checks.
  4. Create the new active uniqueness rule for `(channel_id, phone)`.
  5. Drop or replace the legacy global phone unique rule only after preflight passes.
- APP requests in new builds will always include `X-Apk-Channel`; backend must remain compatible with old APK requests without this header by falling back to `main`.
- Announcements and app release announcements are both channel-scoped. Publishing and querying must require a channel. Old APKs without a channel header see only `main` channel announcements/releases.
- Channel APK release worker isolation:
  - The release script creates a temporary clean working directory for each run.
  - The temporary directory is deleted after the release attempt finishes.
  - Retry is initiated from a management-backend button and reruns the same clean workflow.
  - The script receives the current logged-in user's token from the backend.
  - The script uses that token plus `X-Apk-Channel` when uploading the generated APK and creating the channel release announcement.
  - The script and logs must not print, persist, or expose the token.
- Do not add channel information to Phase9 `cloneInstanceId` in Phase 11. Different channel APKs still share the same local clone data area today, so channel isolation for local clone directories is a broader follow-up problem. Record this as a future phase topic rather than solving it inside Phase 11.

## 2026-06-09 - Remaining business decisions

- Registration bonus compute is a promotion/experience grant and must not be deducted from the channel compute pool. Channel pool compute is only for channel admins to transfer/allocate to users after buying compute from the platform.
- When a channel is disabled, backend should handle this centrally for APK requests carrying `X-Apk-Channel` and return a user-facing error: `该产品暂不可用，请联系：xxx（渠道管理员的手机号）`.
- APP should have a unified server error handling path so central backend errors such as disabled-channel responses are displayed consistently to users.
- Management backend channel-admin phone numbers are globally unique. A channel admin account is created by the super admin, bound to one channel, and logs in without selecting a channel.
- Channel releases include both main APK and engine APK customization/building. The management backend and release scripts should use the same `X-Apk-Channel` channel context for channel-specific main APK and engine APK artifacts, without adding a separate shared-engine special case.

## 2026-06-09 - Final channel behavior decisions

- Do not add special handling for authenticated token channel mismatch with `X-Apk-Channel` in Phase 11. Cross-channel local data/package behavior will be addressed later when different channels get isolated clone directories.
- When creating a channel, the channel administrator must be selected from existing registered users, not manually created by typing a phone number.
- The channel administrator selector should show username and phone number and support fuzzy search by username or phone.
- User list pages should support filtering by user type/role.
- Channel compute should be granted by filtering to `CHANNEL` type users and increasing the selected channel administrator user's compute balance. This should reuse the existing user compute adjustment flow so the operation naturally writes to the existing transaction log table.
- Disabled channel administrators may still log in to the management backend, but should only have read-only access to channel status, users, balances, and transaction records. Disabled channels cannot allocate compute, publish announcements, or publish versions.

## 2026-06-10 - Unified release automation

- The new channel release automation should also support the `main` channel. Main releases and non-main channel releases should use one parameterized release job flow instead of keeping main as a separate manual-only process.
- `main` channel remains the compatibility default for existing online users and old APKs, but it is still represented as an ordinary channel in release automation.
- The release job parameters should include channel code, main release branch or source ref, version name, version code, announcement content, main APK application id, engine APK application id, app display name, engine display/notification name, icon assets, and upload/callback target.
- For `main`, default parameters must preserve current package names:
  - main APK: `com.zhirang.zhanghaoguanjia`
  - engine APK: `com.zhirang.zhanghaoguanjia.engine`
- Existing manual publish pages may remain as emergency/admin maintenance tools, but the normal public release path should be the unified release job.
- The release job should build and publish both main APK and engine APK records for the selected channel, then create the channel-scoped app release announcement after artifact upload and checksum verification succeed.

## 2026-06-10 - Phase 11 local acceptance environment

- Phase 11 acceptance should not be run on an empty database. Export a current production data snapshot first, restore it into the local Docker MySQL service, and use that local restored database to validate migration, channel backfill, RBAC, announcements, versions, shops, transaction logs, and feedback.
- Local acceptance must not write to the production database. Any destructive or mutating checks run only against the restored local Docker copy.
- APK acceptance runs on the Xiaomi real device. The test APK temporarily points to the local machine's Tailscale IP and local Docker nginx `/api/` proxy, for example `-PDUODIAN_API_BASE_URL=http://<本机Tailscale-IP>:8006/api/`.
- Before commit/push, restore the APK API base URL to `http://dpgj.zrnh.cn/api/` and search the repository for the local Tailscale IP or temporary base URL to ensure it was not committed.
