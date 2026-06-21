# Phase 20: 新包名无感迁移发布 - Research

**Date:** 2026-06-22
**Status:** Complete

## Research Question

What do we need to know to plan a rootless, user-invisible migration from the old package-name engine to the new package-name engine after the user logs in to the new main APK?

## Key Findings

### 1. New main cannot directly connect to old engine

The current main app resolves its engine package from `BuildConfig.ENGINE_PACKAGE`, and engine service binding is protected by the engine package signature permission. Existing production/old engine packages were signed by the lost old key. A new package-name main APK signed with the current key cannot bind the old engine's AIDL service in a production-safe way.

Planning implication: do not design the migration around old-engine AIDL. Use an exported old-engine component that runs inside old engine and can read old engine private data.

### 2. Old engine self-export is available from 1.2.14-beta onward

`EngineCloneDataExportActivity` was introduced by commit `342d743` on 2026-06-12. It exists in `1.2.14-beta`, `v1.2.16-release`, `v1.2.17-release`, and Xiaomi's installed old engine `1.2.18-beta / 50028`. It does not exist in `v1.2.12-release`.

From `1.2.14-beta` to current HEAD:

- Activity class name did not change.
- Manifest declaration did not change.
- `exported=true` did not change.
- No permission was added.
- Intent extras did not change: `packageName`, `userId`, `includeAll`.
- Output directory did not change: `getExternalFilesDir("clone-export")`.
- Zip prefixes did not change: `virtual-root/`, `external-virtual-root/`.

Planning implication: feature-detect the Activity at runtime. For versions that have it, one call contract works across those versions. For older versions, no full rootless private-data extraction is available.

### 3. Xiaomi real-device export worked without root

On Xiaomi MIX 2S device `3ca26684`, Android 10/API 29, ordinary adb shell triggered:

```bash
am start -n com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineCloneDataExportActivity --ez includeAll true
```

The old engine wrote:

```text
/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/clone-export/clone_data_all_u0_20260622_020036.zip
```

Final size was `4,563,468,021` bytes. The file head was `PK 03 04`, and the tail contained EOCD plus Zip64 EOCD/locator records. This proves the old engine can self-export private engine data into an external artifact without root.

Planning implication: the user-visible migration should trigger old self-export and poll for stable zip output. It must budget for multi-GB temporary storage and several minutes of work on real devices.

### 4. The old manifest is unreliable

The same Xiaomi export produced a complete final zip but no `.json` manifest. UI/logcat showed:

```text
ok=false
error=Missing clone mapping for package=, userId=0
```

This is caused by the Activity constructing package-scoped `target` metadata after an `includeAll=true` export with an empty package name. The zip had already been written and renamed from `.tmp` to `.zip`.

Planning implication: use `.zip` stability and zip signatures as the completion signal. Do not require the `.json` manifest.

### 5. Zip64 support is required

Android shell `unzip -l` reported the 4.2GB zip as invalid, but tail inspection showed valid Zip64 structures. Import code must not depend on shell unzip or APIs that fail on Zip64 central directory records.

Planning implication: use Java/Kotlin zip APIs that support Zip64 for streaming extraction, or a tested library already available in the build. The importer should stream entries and guard against Zip Slip.

### 6. New main UID can read the old export on the target Xiaomi device

After installing a temporary debuggable `.new` main package on the Xiaomi device, `run-as com.zhirang.zhanghaoguanjia.new` could list the old engine `clone-export` directory and read the zip file header. The test package was then restored to release.

Planning implication: the Android 10 Xiaomi acceptance path can have the new main orchestrate old export and pass the resulting file path/URI to the new engine importer. The implementation should still fail gracefully if a future Android storage policy blocks cross-app `Android/data` reads.

### 7. Migration must be login-gated

A fresh install under the new package name has no existing token, no local user ID, and no previous main-app migration state. The user explicitly requires precise migration by current user. Therefore the earliest valid trigger is after:

1. Login succeeds.
2. Token and `UserDto` are saved.
3. Current-user shops/clones are loaded from the server.

Planning implication: the orchestration belongs after login/home data load, not in splash or engine install alone.

### 8. One-shot state must be local and server-side

The user explicitly requires that the same server user does not re-run old engine migration on later logins. A local-only marker is insufficient because app data can be cleared or the user may reinstall. A server-only marker is insufficient because an already-started local import may need crash recovery details.

Planning implication:

- Local state tracks per-server-user migration lifecycle: not started, exporting, importing, succeeded, failed/retryable, skipped.
- Server `users` gets a hidden boolean/timestamp such as `legacy_engine_migrated` and `legacy_engine_migrated_at`.
- Android app updates the server marker only after import success.
- Login and `/auth/me` return the marker so the app can suppress future migrations.

## Proposed Architecture

1. `LegacyEngineMigrationCoordinator` in the main app runs after login and shop list load.
2. Coordinator checks local per-user state and server `legacyEngineMigrated`.
3. If both indicate migration is pending, show a blocking progress dialog.
4. Resolve old export Activity. If absent, mark local skipped/unsupported and do not block normal app use.
5. Start old export Activity with `includeAll=true`.
6. Poll old engine `clone-export` for a new zip whose `.tmp` no longer exists and size is stable across multiple intervals.
7. Validate file signatures: local header `PK 03 04`; Zip64 EOCD accepted.
8. Launch new engine importer with current server user ID and server shop/clone mapping payload.
9. New engine importer streams the zip, backs up current new roots, extracts/imports matching virtual-root/external-virtual-root content for the current user only, and rebuilds clone mappings/auth state in new root.
10. Main app reconnects/restarts new engine, verifies migrated clone mappings for current user's shops, then calls backend to mark migration complete.
11. Local state records success and suppresses future runs.

## Risks And Mitigations

| Risk | Mitigation |
|------|------------|
| Old engine lacks export Activity | Runtime feature detection; skip legacy migration and use server/login-state fallback. |
| Export zip is huge | Show progress, require free-space check before starting, stream import without copying the zip. |
| Old manifest missing | Ignore manifest; use final zip and stable size detection. |
| Android storage blocks new main reading old export | Prefer passing URI/file path after app-readable validation; fail gracefully with retry/help state. |
| Import accidentally brings another user's clone | Filter by current server user/shop cloneInstanceIds; do not wholesale trust every old mapping. |
| Crash mid-import | Backup new engine roots; record local migration lifecycle; retry or restore safely. |
| Same user logs in again | Check local and server one-shot markers before export. |
| Admin accidentally exposes hidden field | Backend DTO/API tests should assert admin user list/detail payload does not include the migration field. |

## Validation Architecture

### Static/source validation

- `EngineCloneDataImportActivity` or equivalent importer is declared in new engine manifest and protected so only the new main can invoke it.
- Importer code rejects Zip Slip paths and supports entries under `virtual-root/` and `external-virtual-root/`.
- Coordinator checks login/user/shop state before migration and never runs before `TokenManager.saveUser`.
- Local state key is scoped by server user ID, not by phone text alone.
- Backend `User` has hidden migration field and authenticated app endpoint to mark it.
- Admin user DTO/list code does not expose or edit migration field.

### Unit/integration validation

- JVM/unit tests for zip path normalization, zip completion detection, and migration state transitions.
- Backend tests for login response including migration field, mark-migrated endpoint, and admin response excluding the field.
- Android build verifies both app and Bcore release variants.

### Xiaomi real-device validation

1. Install old package/engine with existing clone data and new package release APK.
2. Confirm no root commands are used for the product flow.
3. Login to `.new` package as the intended user.
4. Confirm blocking "正在迁移数据" dialog appears after login.
5. Confirm old `EngineCloneDataExportActivity` is started and final zip is generated.
6. Confirm new engine imports the relevant current-user shops.
7. Confirm opening migrated shops uses new engine and does not show another user's account/shop.
8. Logout/login same user again and confirm migration does not re-run.
9. Confirm backend user marker is set while admin UI remains unchanged.

## Research Complete

The phase is plannable with a four-wave implementation: backend state, new engine importer, Android orchestration/UX, release and Xiaomi acceptance.
