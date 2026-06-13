# Phase 16: 主 App 登录态数据中心重构 - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning
**Source:** User direction after Phase 13-15 validation

<domain>

## Phase Boundary

This phase refactors ownership of shop login-state backup and synchronization.

The current Phase 13 implementation proves that cross-device login can work, but the decision state is split across:

- server shop fields such as `hasLoginState`
- main App shop card state
- engine clone directories and engine-managed export/restore calls

The target architecture is:

- main App owns login-state backup files and metadata
- engine exposes AIDL capabilities only
- server stores latest uploaded login-state by system shop ID
- shop information collection is the only trigger that can lead to login-state export
- login-state restore happens only during repair, not during normal open or pull-to-refresh

</domain>

<decisions>

## Locked Decisions

### Data Ownership

- Main App is the login-state data center for business decisions.
- Main App may stage login-state artifact bytes only until upload/recovery completes, and stores long-term metadata in its own app data directory.
- Engine keeps only virtualization infrastructure data needed to run clones.
- Engine must not decide whether to upload, download, overwrite, or restore server login-state.
- Server shop info and login-state are backup records during normal use. The server accepts reports from devices and keeps the latest trusted backup, but it is not the runtime authority for an already usable local clone.

### Data Cleanup

- Engine-collected shop info and exported login-state artifacts are transient AIDL results. Engine must not persist extra copies outside the live clone runtime data required by virtualization.
- Main App must delete raw collected blobs/zips immediately after successful upload/report, keeping only minimal metadata such as system shop ID, packageName, profileId, sha256, size, createdAt, uploadedAt, and report status.
- If upload fails, Main App may keep the staged artifact only as a retry queue item with bounded retention and must delete it after success, expiry, or user logout/data cleanup.
- Repair/new-device restore may temporarily download a server artifact into Main App staging; after engine restore succeeds or fails terminally, the staged raw artifact must be deleted.

### Trusted Identity

- System shop ID is the only trusted business key.
- Platform `shopId` is recorded only as extracted platform identity, never as a business lookup or upload key.
- Login-state upload/download APIs must use system shop ID.

### Collection Order

- Main App first invokes engine shop-info collection.
- If shop info collection succeeds, Main App invokes engine login-state export.
- If shop info collection fails, Main App treats the clone as not confirmed logged in and must not export/upload login-state.

### Restore Timing

- Pull-to-refresh updates shop basic information only.
- Normal shop open must not automatically restore server login-state over an existing local clone.
- Repair shop is the only user-facing path that releases login-state into the clone directory.
- Server login-state may be released into the local clone only in two cases: the phone has no usable local clone login-state, such as new-device login/restore, or the user explicitly triggers repair shop.
- If the phone already has a usable local clone login-state, the app must keep using it. Server login-state must not overwrite the local clone during normal open, refresh, background sync, shop-info collection, or login-state upload.
- Server shop info follows the same backup-first model: normal device reports may update the server record, but the server record should only be used to recover local display/clone state when the local device has no usable data or during explicit repair.

### Engine Scope

- Engine may keep:
  - virtual user records
  - package install records
  - cloneInstanceId to virtualUserId mapping
  - launch authorization token
  - scoped clone runtime directories
- Engine must expose atomic AIDL methods and return results to Main App.
- Engine must avoid owning long-term login-state backup truth beyond the live clone runtime files.

</decisions>

<canonical_refs>

## Canonical References

Downstream implementation must read these before coding:

- `.planning/phases/13-meituan-login-state-sync/13-PLAN.md` — existing login-state sync implementation scope and validation history.
- `.planning/phases/13-meituan-login-state-sync/13-STANDARD.md` — platform-specific login-state artifact profiles and restore path constraints.
- `.planning/phases/14-subscription-billing/14-PLAN.md` — shop entry flow and system shop ID constraints from billing.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt` — current shop open, repair, shop-info sync, login-state export/restore call sites.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt` — current shop list refresh and report/update behavior.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/ShopRepository.kt` — current shop API wrapper.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt` — current upload/download login-state endpoints.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineProxy.kt` — current Main App to engine AIDL wrapper.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/LoginStateSyncManager.kt` — current engine export/restore profile implementation.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt` — clone mapping and authorization data that must remain engine-owned.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/ScopedCloneStorage.kt` — scoped clone runtime directory model.
- `Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java` — path resolution between legacy and scoped clone directories.
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java` — current login-state upload/download API behavior.
- `admin/backend/src/main/java/com/duodian/admin/service/ShopService.java` — current server login-state persistence.

</canonical_refs>

<specifics>

## Specific Requirements

- Main App local artifact storage should be file-backed, not stored as giant SharedPreferences values.
- Metadata should be queryable by system shop ID.
- Staged artifact file names should not include platform shopId.
- Staged artifact replacement must compare freshness and checksum so older files do not replace newer known-good files while the item is waiting for upload/recovery.
- Server upload should include artifact creation/export time or manifest time. Server should only persist if the incoming artifact is newer than the stored one.
- Repair should choose the freshest available source from the current phone live clone, any bounded Main App retry staging item, and server metadata/download.
- The server artifact participates in restore selection only when the local clone has no usable login-state or after an explicit repair action. Outside those two cases, server login-state is cloud backup metadata only and must not be released into the clone directory.
- Server shop information and login-state should be treated as backup payloads produced by device reports. They are read for recovery only in the same two cases: local data is absent or the user explicitly repairs the shop.
- The implementation must preserve already verified profiles:
  - Meituan CIPS profile
  - JD `jd-jingming-prefs-d`
  - Ele.me `ele-napos-prefs-e-min`
- Verification must include Xiaomi real-device regression for at least one already logged-in shop and one repair flow.

</specifics>

<deferred>

## Deferred Ideas

- Moving live clone runtime files into the Main App data directory is out of scope; Android package sandboxing and virtualized runtime path expectations make that unsafe for this phase.
- Full encryption-at-rest for login-state artifact blobs can be a follow-up hardening phase if not already covered by platform storage controls.
- Background periodic login-state sync is out of scope. This phase intentionally uses explicit Main App triggers.

</deferred>

---

*Phase: 16-app*
*Context gathered: 2026-06-13*
