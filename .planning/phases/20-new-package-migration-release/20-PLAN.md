---
phase: 20
plan: 20-PLAN
type: execute
wave: 1
depends_on: []
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/entity/User.java
  - admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java
  - admin/backend/src/main/java/com/duodian/admin/controller/AuthController.java
  - admin/backend/src/main/java/com/duodian/admin/service/UserService.java
  - admin/backend/src/test/java/com/duodian/admin/controller/AuthControllerTest.java
  - Bcore/src/main/AndroidManifest.xml
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineCloneDataImportActivity.kt
  - Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/bean/dto/UserDto.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/TokenManager.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/migration/LegacyEngineMigrationCoordinator.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
autonomous: false
requirements:
  - PH20-D01
  - PH20-D02
  - PH20-D03
  - PH20-D04
  - PH20-D05
  - PH20-D06
  - PH20-D07
  - PH20-D08
  - PH20-D09
---

<objective>
Ship a new package-name release APK that migrates old-engine clone data after the first successful login, without root/Magisk, without requiring user file picking, and without repeating migration for the same server user.
</objective>

<must_haves>
- PH20-D01: The production migration path uses old engine self-export and new engine self-import only; no root, Magisk, adb private-dir reads, or direct old-engine AIDL binding.
- PH20-D02: Migration is triggered only after login success and current-user shop data is available.
- PH20-D03: Migration is one-shot per server user, guarded by both local state and a hidden backend `users` migration field.
- PH20-D04: Import filters by current server user and server shop/cloneInstance mapping to avoid account/shop bleed.
- PH20-D05: Old export `.json` manifest is optional; stable final `.zip` detection is authoritative.
- PH20-D06: New engine importer supports Zip64 and rejects Zip Slip paths.
- PH20-D07: Admin UI does not display or edit the hidden migration field.
- PH20-D08: Release APK uses `com.zhirang.zhanghaoguanjia.new` and `com.zhirang.zhanghaoguanjia.new.engine`, connected to the internal API environment.
- PH20-D09: Xiaomi MIX 2S real-device verification proves migration, shop opening, and no repeat migration.
</must_haves>

<threat_model>
## Threat Model

| Threat | Severity | Mitigation |
|--------|----------|------------|
| Importing another user's old clone data into the logged-in user's new engine | High | Require current server user ID and server shop/cloneInstance set; ignore unmatched mappings; verify exact `serverUserId`, `packageName`, and `cloneInstanceId`. |
| Zip Slip path traversal during multi-GB import | High | Canonicalize every zip entry target and reject entries escaping the intended private/external root. |
| Re-running old migration overwrites newer new-engine data | High | Check backend marker and local per-user success marker before export; import only runs when both say pending. |
| Old engine export Activity absent or blocked | Medium | Runtime feature detection and graceful skip/fallback; do not attempt private-dir reads. |
| Multi-GB export exhausts storage | Medium | Check free space before export/import; show recoverable failure and do not mark server migrated until import succeeds. |
| Admin accidentally exposes hidden migration field | Low | Keep field out of admin DTO/table/edit forms and add regression tests. |
</threat_model>

<tasks>

<task id="20-01" type="execute">
<title>Backend hidden one-shot migration state</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/entity/User.java`
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/AuthController.java`
- `admin/backend/src/main/java/com/duodian/admin/service/UserService.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/UserController.java`
- Existing backend auth/user tests under `admin/backend/src/test/java/com/duodian/admin/`
</read_first>
<action>
Add hidden user migration state to the backend:
- Add boolean `legacyEngineMigrated` and timestamp `legacyEngineMigratedAt` to `User`.
- Extend startup schema migration to add `legacy_engine_migrated` default false and nullable `legacy_engine_migrated_at`.
- Include `legacyEngineMigrated` in app login response and `/auth/me` response.
- Add an authenticated app endpoint, for example `POST /api/auth/legacy-engine-migration/complete`, that sets the field true for the current principal and records the timestamp.
- Do not add the field to admin user table columns, admin edit forms, or admin-facing DTO fields unless those endpoints already return the raw entity and need explicit JSON exclusion.
</action>
<acceptance_criteria>
- `User.java` contains `legacyEngineMigrated` and `legacyEngineMigratedAt`.
- `SoftDeleteSchemaInitializer.java` creates `legacy_engine_migrated` and `legacy_engine_migrated_at` if missing.
- `AuthController.java` login and `/auth/me` payloads include `legacyEngineMigrated`.
- A successful authenticated complete-migration request sets `legacyEngineMigrated=true`.
- Admin user list/detail frontend source does not contain `legacyEngineMigrated`.
- Backend tests cover login field, completion endpoint, and admin non-exposure.
</acceptance_criteria>
<verify>
- `cd admin/backend && mvn -Dtest=AuthControllerTest,UserControllerTest test`
</verify>
</task>

<task id="20-02" type="execute">
<title>New engine Zip64 import endpoint</title>
<read_first>
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineCloneDataExportActivity.kt`
- `Bcore/src/main/AndroidManifest.xml`
- `Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java`
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt`
- `engine-aidl/src/main/aidl/top/niunaijun/blackbox/core/system/IBlackBoxEngine.aidl`
</read_first>
<action>
Add a new engine-side importer that the new main package can invoke:
- Create `EngineCloneDataImportActivity` or an equivalent same-signature protected component in Bcore.
- Accept a zip file path or content URI plus current `serverUserId` and a JSON array of allowed shops containing `shopId`, `cloneInstanceId`, `packageName`, and target/new `localVirtualUserId` if available.
- Stream the export zip and support Zip64.
- Recognize `virtual-root/` and `external-virtual-root/` entries.
- Backup current new-engine private/external blackbox roots before destructive writes.
- Import only entries and mappings that match the current `serverUserId` and allowed cloneInstance/package set.
- Rebuild or preserve `system/clone-instances.json`, `system/clone-auth/**`, and package data so `BEnvironment.getDataDir(packageName, userId)` resolves in the new engine.
- Reject path traversal by canonical target-root checks.
- Return structured JSON result with imported count, skipped count, backup paths, and errors.
</action>
<acceptance_criteria>
- Bcore manifest declares the import component and protects it so arbitrary third-party apps cannot import into the new engine.
- Importer source contains canonical path validation before writing any zip entry.
- Importer handles zip entries with prefixes `virtual-root/` and `external-virtual-root/`.
- Importer accepts current server user/shop mapping input and skips unmatched mappings.
- Importer result JSON includes `ok`, `imported`, `skipped`, and `errors`.
- Unit or instrumentation tests cover Zip Slip rejection and mapping filtering, or the code includes a deterministic test helper covered by JVM tests.
</acceptance_criteria>
<verify>
- `./gradlew :Bcore:assembleRelease :Bcore:testDebugUnitTest --no-daemon`
</verify>
</task>

<task id="20-03" type="execute">
<title>Main app login-gated migration coordinator</title>
<read_first>
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/login/LoginViewModel.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/login/LoginActivity.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/TokenManager.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineInstaller.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineIdentity.kt`
</read_first>
<action>
Implement `LegacyEngineMigrationCoordinator` in the main app:
- Trigger only after login success and after current-user shops are loaded in Home.
- Check `UserDto.legacyEngineMigrated` and local per-server-user migration state before starting.
- Resolve old export Activity `com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineCloneDataExportActivity`.
- If old export Activity is missing, mark local unsupported/skipped without marking server migrated.
- Start old export Activity with `includeAll=true`.
- Poll `/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/clone-export` for a new `clone_data_all_u0_*.zip`; ignore `.tmp`; require stable size over repeated intervals.
- Validate `PK 03 04` header and accept Zip64 tail.
- Launch new engine importer with current user ID and current server shop/cloneInstance mappings.
- On import success, call backend complete-migration endpoint and persist local success.
- On failure, persist retryable failure with error text; do not call backend complete endpoint.
</action>
<acceptance_criteria>
- Migration code has an explicit guard that returns before login/user/shop data exist.
- Migration state key includes server user ID.
- Code checks both server `legacyEngineMigrated` and local success before export.
- Code resolves old export Activity before launching it.
- Code detects final zip without requiring `.json`.
- Code passes current user's server shop/cloneInstance mapping to new engine importer.
- Code calls backend complete endpoint only after importer returns `ok=true`.
- User can continue normal app flow after success or graceful unsupported skip.
</acceptance_criteria>
<verify>
- `./gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon`
</verify>
</task>

<task id="20-04" type="execute">
<title>Migration progress UX and recovery</title>
<read_first>
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`
- `app/src/main/res/layout/`
- Existing migration dialog code around `clone_data_migration`
- Existing Material dialog usage in app source
</read_first>
<action>
Add a blocking, user-friendly migration progress UI:
- Show "正在迁移数据" after login when migration starts.
- Show progress stages: checking old data, exporting old engine data, importing new engine data, finishing migration.
- Prevent opening shop cards while migration is in progress.
- On success, dismiss dialog and refresh shop list/new engine state.
- On unsupported old engine, dismiss with a non-blocking fallback state.
- On retryable failure, show a concise error and retry option; do not leave a permanent blocking dialog.
</action>
<acceptance_criteria>
- HomeActivity or coordinator shows a blocking progress UI before old export starts.
- Shop open actions are disabled or guarded while migration is in-flight.
- Success path dismisses the dialog and refreshes data.
- Failure path does not set local/server success markers.
- The UI text does not mention root, adb, Zip64, or implementation details.
</acceptance_criteria>
<verify>
- Manual UI test on Xiaomi real device after installing new release package.
</verify>
</task>

<task id="20-05" type="execute">
<title>New package-name release build and publication</title>
<read_first>
- `app/build.gradle`
- `Bcore/build.gradle`
- `admin/scripts/release-channel-apk.sh`
- Existing release/upload scripts under `scripts/` and `admin/scripts/`
- Current app version publishing backend endpoints/entities under `admin/backend`
</read_first>
<action>
Build a release APK for:
- Main package: `com.zhirang.zhanghaoguanjia.new`
- Engine package: `com.zhirang.zhanghaoguanjia.new.engine`
- API base URL: internal environment, currently `http://172.20.0.13:8006/api/` unless config has changed.

Publish the APK through the existing app-version mechanism and create a release announcement that explains the new package-name upgrade and one-time data migration without exposing technical internals.
</action>
<acceptance_criteria>
- Release APK package name is `com.zhirang.zhanghaoguanjia.new`.
- Embedded engine APK package name is `com.zhirang.zhanghaoguanjia.new.engine`.
- `BuildConfig.API_BASE_URL` in the APK resolves to the internal API environment.
- APK installs alongside old `com.zhirang.zhanghaoguanjia` without uninstalling it.
- App-version record and release announcement exist for the new package release.
</acceptance_criteria>
<verify>
- `./gradlew :app:assembleRelease -PDUODIAN_APP_APPLICATION_ID=com.zhirang.zhanghaoguanjia.new -PDUODIAN_ENGINE_APPLICATION_ID=com.zhirang.zhanghaoguanjia.new.engine --no-daemon`
- `adb -s 3ca26684 install -r app/build/outputs/apk/release/*arm64-v8a-release.apk`
- `adb -s 3ca26684 shell dumpsys package com.zhirang.zhanghaoguanjia.new | grep -E 'versionCode|versionName|pkgFlags'`
</verify>
</task>

<task id="20-06" type="execute">
<title>Xiaomi real-device acceptance</title>
<read_first>
- `.planning/phases/20-new-package-migration-release/20-CONTEXT.md`
- `.planning/phases/20-new-package-migration-release/20-RESEARCH.md`
- `scripts/` test helpers if present
</read_first>
<action>
Run acceptance on Xiaomi MIX 2S `3ca26684`:
- Keep old main and old engine installed.
- Install new package-name release APK.
- Login to the new app with the target test account.
- Confirm migration starts only after login and shop list availability.
- Confirm old export Activity starts and old export zip appears under the old engine clone-export directory.
- Confirm new engine import succeeds.
- Open migrated shops in the new engine and verify they correspond to the logged-in user's shops, not another user's shops.
- Logout/login the same user again and verify migration does not start again.
- Confirm backend user marker is true and admin UI does not show the hidden field.
</action>
<acceptance_criteria>
- Product flow uses no `su`, Magisk, or root-only command.
- Xiaomi screen shows migration progress after login.
- Old export zip final file is created and stable.
- New engine data contains current user's imported clone mappings.
- Opening a migrated shop does not reproduce the original account/shop bleed.
- Second login for same server user does not start old export Activity.
- Backend `legacyEngineMigrated` is true for the user after success.
- Admin UI remains unchanged with respect to the hidden field.
</acceptance_criteria>
<verify>
- `adb -s 3ca26684 shell cmd package resolve-activity --brief -n com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineCloneDataExportActivity`
- `adb -s 3ca26684 shell ls -lh /sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/clone-export`
- `adb -s 3ca26684 logcat -d | grep -E 'LegacyEngineMigration|EngineCloneImport|EngineCloneExport'`
</verify>
</task>

</tasks>

<verification>
## Overall Verification

1. Backend test suite for auth/user migration state passes.
2. Android app and Bcore release builds pass with new package names.
3. Xiaomi real-device acceptance proves first-login migration and no-repeat semantics.
4. The final git commit includes planning, backend, Android app, engine importer, release metadata, and verification notes.
</verification>

<success_criteria>
- New package-name release APK can be installed beside the old package.
- Existing old-engine data can be exported by the old engine without root.
- New engine can import the logged-in user's matching clone data.
- Migration starts only after login and only once per server user.
- Server stores hidden migration state and admin UI does not expose it.
- Xiaomi real-device verification passes.
</success_criteria>

## Artifacts This Phase Produces

- `User.legacyEngineMigrated`
- `User.legacyEngineMigratedAt`
- Auth complete-migration endpoint, e.g. `POST /api/auth/legacy-engine-migration/complete`
- `UserDto.legacyEngineMigrated`
- `LegacyEngineMigrationCoordinator`
- `LegacyEngineMigrationState`
- `EngineCloneDataImportActivity`
- Engine import result JSON contract
- Release APK for `com.zhirang.zhanghaoguanjia.new`
- Embedded engine APK for `com.zhirang.zhanghaoguanjia.new.engine`
