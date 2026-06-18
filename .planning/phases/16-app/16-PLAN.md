# Phase 16: 主 App 登录态数据中心重构 - Plan

**Status:** Completed
**Created:** 2026-06-13
**Completed:** 2026-06-13

## Goal

Make the Main App the business owner of shop login-state metadata, upload orchestration, and short-lived artifact staging. The engine should remain responsible for virtualized clone runtime and atomic AIDL capabilities, but it should not own server sync decisions or long-term login-state backup truth.

## Non-Negotiable Constraints

- System shop ID is the only trusted business key for login-state upload/download.
- Platform shopId is display/diagnostic data only and must not participate in business lookup.
- Shop-info collection must happen before login-state export.
- If shop-info collection fails, do not export or upload login-state.
- Pull-to-refresh updates shop basic information only.
- Normal shop open must not restore server login-state.
- Login-state release into the clone directory happens only when local clone login-state is absent or during repair.
- Unless the user actively taps repair shop, the app always uses the phone's current local clone login-state.
- Server login-state can overwrite local clone data only in two cases: local clone login-state is absent, such as new-device login/restore, or the user actively taps repair shop.
- Server login-state is cloud backup only outside those two cases and must not overwrite local clone data during normal open, refresh, sync, collection, or upload.
- Server shop information follows the same backup role: it normally accepts device reports and stores backup display/identity data; it is used to recover local device state only when local data is absent or during explicit repair.
- Engine-collected data must not be persisted by the engine outside the live clone runtime.
- Main App must delete raw collected login-state blobs/zips immediately after successful upload/report, keeping only metadata and audit/debug status.
- Java builds use JDK 21.

## Wave 1 - Main App Login-State Metadata and Staging Repository

1. Add a Main App login-state repository under app data files, for example `files/login-state/shops/<systemShopId>/`.
2. Store artifact bytes only as bounded staging files while upload/recovery is in progress, not as long-lived SharedPreferences or permanent blob records.
3. Store metadata in a queryable local index or atomic JSON file:
   - system shop ID
   - packageName
   - cloneInstanceId
   - virtualUserId
   - profileId
   - platform
   - extracted platform shopId and shopName
   - artifact path, size, sha256
   - artifact created/exported time
   - last shop-info collection time
   - upload/server sync state
   - raw artifact retention state
4. Add cleanup helpers:
   - delete staging after successful upload/report
   - delete restore download staging after restore success or terminal failure
   - expire failed upload staging after bounded retry policy
5. Add freshness comparison helpers: newer artifact wins while staged; same timestamp requires sha256 equality; older artifact cannot replace newer staged or server-known metadata.
6. Add focused unit tests for metadata read/write, atomic replace, checksum, freshness comparison, and cleanup.

**Acceptance:**
- Main App can persist and reload login-state metadata by system shop ID.
- A stale artifact cannot overwrite newer known metadata or a newer staged artifact.
- Successfully uploaded raw artifacts are deleted immediately.
- No artifact path or metadata key depends on platform shopId.

## Wave 2 - AIDL Boundary Cleanup

1. Audit `EngineProxy` and AIDL methods involved in shop open, shop-info extraction, export, restore, clear, mapping, and authorization.
2. Keep engine APIs as atomic capabilities only.
3. Ensure no engine-side code initiates server sync or business upload/download decisions.
4. Ensure engine-side shop-info and login-state export results are returned as transient AIDL payloads and not written as extra engine-owned report/cache files.
5. Keep engine-owned infrastructure data:
   - clone user mapping
   - virtual package/user state
   - scoped runtime directories
   - authorization token files
6. Add explicit result objects or error codes where Main App needs to distinguish "not logged in", "profile missing", "export empty", and "engine unavailable".

**Acceptance:**
- Main App owns orchestration decisions.
- Engine does not decide whether to upload, download, or restore server login-state.
- Engine does not persist extra copies of collected shop info or exported login-state artifacts.
- Existing clone launch still works through the upgraded engine.

## Wave 3 - Unified Shop-Info Then Login-State Collection

1. Create one Main App entry point for "collect current shop state".
2. This entry point must:
   - resolve system shop ID and clone mapping
   - call engine `triggerShopIdExtract`
   - update Main App/backend shop basic info if extraction succeeds
   - call engine `defaultLoginStateProfile`
   - call engine `exportLoginState`
   - stage artifact and metadata into Main App local repository
   - mark artifact dirty for upload if it is new or changed
   - upload/report to server when network is available
   - delete raw staged artifact after successful upload/report
3. Remove or disable login-state export paths that are not preceded by successful shop-info extraction.
4. Ensure shop card down-refresh only invokes shop basic info refresh and does not export login-state.

**Acceptance:**
- Successful shop-info extraction can produce a staged login-state artifact and then delete the raw artifact after successful upload.
- Failed extraction produces no artifact and no upload.
- Pull-to-refresh does not export or restore login-state.

## Wave 4 - Server Sync Rewire

1. Change upload logic to read from Main App local repository and upload by system shop ID.
2. Include artifact metadata:
   - profile
   - sha256
   - size
   - artifact created/exported time
   - manifest
3. Update backend to persist an artifact creation/export timestamp.
4. Backend only replaces stored login-state when incoming artifact is newer than stored metadata.
5. Keep response DTO lightweight: metadata only, never echo blob content.
6. Confirm every shop-related backend path that affects login-state uses system shop ID and not platform shopId.
7. Treat server shop information as a backup target during normal use:
   - accept device reports for shop name, platform shopId, logo, identity status, and login-state metadata
   - do not let normal list/refresh responses force local clone login-state replacement
   - expose recovery data only when Main App calls the new-device/local-absent restore path or explicit repair path
8. Make API naming or repository methods reflect backup semantics where practical, so future code does not confuse server records with runtime authority.

**Acceptance:**
- Upload by system shop ID works.
- Older upload is ignored or rejected without replacing newer server blob.
- Backend tests cover newer, equal, and older artifact uploads.
- Server shop info and login-state are consumed for recovery only in local-absent and explicit-repair flows.

## Wave 5 - Repair-Only Restore

1. Remove login-state restore from normal shop-open preparation.
2. Implement the local-absent restore flow for new-device login:
   - detect that the current phone has no usable local clone login-state for the system shop ID
   - fetch/download server login-state by system shop ID
   - stage it in the Main App local repository
   - call engine `restoreLoginState`
   - delete the staged download after restore success or terminal failure
   - never run this path if local shop-info collection proves the clone is already logged in
3. Implement repair flow:
   - clear target clone data only after user confirms repair
   - fetch server metadata/download only during repair when needed
   - compare server artifact with Main App local artifact
   - choose freshest artifact
   - call engine `restoreLoginState`
   - delete any downloaded/staged raw artifact after restore success or terminal failure
   - launch or prompt user after restore result
4. If local clone already appears logged in through successful shop-info collection, repair should not overwrite it silently.
5. Add clear user-facing error handling for no local/server login-state artifact.
6. Block all callers from invoking `restoreLoginState` unless the path is local-absent restore or explicit repair, even if the server reports `hasLoginState=true`.

**Acceptance:**
- Normal open never overwrites clone data with server login-state.
- Local-absent new-device restore and explicit repair are the only paths that release server login-state into engine clone directories.
- Repair uses system shop ID for download.
- Server login-state is never released automatically when the phone already has usable local clone login-state.
- Restore download staging is cleaned after restore completion.

## Wave 6 - Migration and Backfill

1. On upgrade, for each shop card with a valid clone mapping but no Main App local login-state metadata, schedule a backfill attempt.
2. Backfill must use the normal "shop-info then export/upload/delete staging" entry point.
3. Do not clear or overwrite clone data during backfill.
4. Preserve Phase 13 scoped directory migration logic in the engine.
5. Add logs that distinguish:
   - migrated from existing local clone
   - restored from server
   - no confirmed login
   - engine unavailable

**Acceptance:**
- Existing logged-in shops can populate Main App metadata and server backup without repair.
- Existing not-logged-in shops do not create fake login-state artifacts.
- Backfill does not leave raw login-state zips/blobs behind after successful upload.

## Wave 7 - Verification

1. Android unit tests for Main App repository and freshness comparison.
2. Backend tests for login-state timestamp replacement rules and system-ID-only upload/download.
3. Android compile with JDK 21:

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
ANDROID_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}" \
ANDROID_NDK_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/ndk/29.0.13846066" \
./gradlew :app:compileDebugKotlin
```

4. Backend tests:

```bash
cd admin/backend
JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Dtest=ShopControllerTest,ShopServiceTest test
```

5. Xiaomi real-device regression:
   - normal open existing logged-in shop
   - pull-to-refresh
   - collect shop info then export local artifact
   - repair using local/server artifact
   - verify Meituan/JD/Ele.me profile compatibility where devices have logged-in shops

## Done Criteria

- Main App owns login-state artifact backup and metadata.
- Engine remains a capability layer and live clone runtime owner.
- Server sync uses system shop ID only.
- Normal open and refresh do not restore login-state.
- Repair is the only restore/release path.
- Phase 13 verified platform profiles still work on real device.

## Completion Verification

- Android compile passed with JDK 21:
  `JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}" ANDROID_NDK_HOME="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}/ndk/29.0.13846066" ./gradlew :app:compileDebugKotlin`
- Backend focused tests passed with JDK 21:
  `cd admin/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Dtest=ShopControllerTest,ShopServiceTest test`
- Built and installed debug APK `1.2.14-beta` to Xiaomi MIX 2S `3ca26684`; MIUI installer updated engine from `1.2.13-phase13` to `1.2.14-beta` at `2026-06-13 12:03:23`.
- Local Docker backend was redeployed from current code; `shops.login_state_artifact_created_at` was added and backend started on Java 21.
- Xiaomi real-device regression used user `15200837196` and system shop ID `53`:
  - Normal open prepared `shop=53 package=com.sankuai.meituan.meituanwaimaibusiness user=3` and did not log any `restoreLoginState`.
  - Before repair, delayed shop-info extraction returned `found=false`, and no login-state staging file remained under `files/login-state`.
  - Explicit repair confirmed by user action logged `restoreLoginState allowed shop=53 package=com.sankuai.meituan.meituanwaimaibusiness user=3 reason=repair hasServer=true profile=meituan-waimai-cips-f`.
  - Repair downloaded `462736` bytes, restored successfully, and restore staging was deleted.
  - After repair, normal open again did not trigger server restore, entered the Meituan Waimai merchant order page, and delayed shop-info extraction returned `found=true`.
  - Successful collection uploaded a fresh backup by system shop ID; server row `shops.id=53` updated `login_state_size` to `463616`, `login_state_updated_at` to `2026-06-13 12:18:34.274094`, and `login_state_artifact_created_at` to `2026-06-13 04:18:33.231000`.
  - Main App private `files/login-state` had no raw zip/blob residuals after restore/upload completion.
