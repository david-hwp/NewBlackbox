# Phase 20: 新包名无感迁移发布 - Context

**Gathered:** 2026-06-22
**Status:** Ready for planning
**Source:** User directive plus Xiaomi MIX 2S migration spike

<domain>
## Phase Boundary

This phase ships a new package-name release APK and gives existing users a rootless migration path from the old engine package to the new engine package. The migration starts only after the user logs in to the new main package because a fresh package install has no local token or user identity. The migration must use the old engine's own exported Activity to read old private engine data, then import the generated artifact into the new engine and map only the logged-in user's shops/clones.

The phase includes Android main app orchestration, new engine import capability, backend one-shot migration state, release APK packaging, release announcement, and Xiaomi real-device verification. It does not attempt to recover the lost old keystore and does not require root/Magisk for production users.
</domain>

<decisions>
## Implementation Decisions

### D-01 Rootless production constraint
- The production migration flow must not require root, Magisk, adb shell private-directory access, or manual file copy. Root observed on the Xiaomi test device is allowed only for forensic cleanup and cannot be part of the product path.

### D-02 New main cannot bind old engine service
- The new main package must not depend on binding `com.zhirang.zhanghaoguanjia.engine` AIDL services because old engine service permissions are signature-protected and the old signing key is unavailable.

### D-03 Old engine self-export is the source extraction mechanism
- For old engines that contain `top.niunaijun.blackbox.engine.EngineCloneDataExportActivity`, the new main package must trigger that Activity with `includeAll=true` so the old engine reads its own private `blackbox` data and writes a zip under its own external `clone-export` directory.

### D-04 Export capability version gate
- The new main package must feature-detect the old export Activity via `PackageManager.resolveActivity` before migration. It must support old engines from `1.2.14-beta` onward where the Activity exists. If the Activity does not exist, the app must skip this legacy-engine migration path and use a clear fallback state, not attempt inaccessible private data reads.

### D-05 Do not rely on old export manifest
- The migration orchestrator must not require the `.json` manifest from `EngineCloneDataExportActivity`. Xiaomi validation showed full zip generation succeeds but the Activity can report `Missing clone mapping for package=, userId=0` while building the result JSON. Completion must be detected from final `.zip` existence, `.tmp` disappearance, stable file size, and zip signatures.

### D-06 Zip64 import required
- The new engine importer must support Zip64. Xiaomi validation produced a `4,563,468,021` byte export with Zip64 EOCD records; Android's shell `unzip` reported it as invalid, so correctness cannot depend on shell unzip behavior.

### D-07 Login-gated precise migration
- The new package has no user info immediately after install. Migration must start after login succeeds, token/user are stored, and current-user shop data has been loaded. Import must map only the logged-in server user's shops and cloneInstanceIds into the new engine.

### D-08 One-shot local and server state
- The migration must be a one-time action per server user. The main app must record per-user migration state locally, and the backend `users` table must add a hidden field such as `legacy_engine_migrated`/`legacyEngineMigrated`. Subsequent logins for the same user must not re-run old-engine export/import.

### D-09 Backend field is not admin-facing
- The server migration-complete field must be returned to the Android app via login and `/auth/me`, and updated by an authenticated endpoint after successful migration. It must not be added to admin user list/detail/edit UI.

### D-10 Preserve current user data and avoid account bleed
- Migration must never blindly import another user's old engine data into the logged-in user's new engine state. The importer must use server shop records and `cloneInstanceId`/`packageName`/`serverUserId` to select or rewrite only matching old clone data. It must preserve Phase 20's prerequisite bugfix that exact clone mapping prevents account/shop bleed.

### D-11 Release and verification target
- The release APK must use the new package names `com.zhirang.zhanghaoguanjia.new` and `com.zhirang.zhanghaoguanjia.new.engine`, connect to the internal API environment requested by the user, and be installed on Xiaomi real device `3ca26684` for acceptance.

### D-12 User-visible migration UX
- After first successful login, the app must show a blocking progress dialog such as "正在迁移数据" while export/import runs. The user should not need to pick files, grant root, or run adb. The dialog should end in success or a recoverable failure state.

### the agent's Discretion
- Exact endpoint names, local SharedPreferences keys, and import backup naming can follow existing codebase conventions.
- The importer can choose a safe implementation strategy between full-root replacement with filtering or targeted clone extraction, as long as it enforces logged-in-user scope and backs up existing new-engine data before destructive writes.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Android engine migration
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineCloneDataExportActivity.kt` — Existing old-engine export Activity contract, extras, zip layout, and known manifest failure mode.
- `Bcore/src/main/AndroidManifest.xml` — Engine export Activity declaration and new importer Activity/service declarations.
- `Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java` — Engine private/external roots and scoped clone data paths.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt` — Clone mapping storage and scoped migration helpers.
- `engine-aidl/src/main/aidl/top/niunaijun/blackbox/core/system/IBlackBoxEngine.aidl` — Existing engine IPC surface and whether import should be Activity-based or AIDL-based.

### Android main app orchestration
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/login/LoginViewModel.kt` — Login success stores token/user; migration must occur after this point.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/login/LoginActivity.kt` — Login navigation flow into home.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt` — Existing migration dialog/progress patterns and clone data scoped migration hook.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/TokenManager.kt` — Local user/token storage and place to persist migration-related user state if appropriate.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/UserRepository.kt` — Login and `/auth/me` access layer.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt` — API declarations for migration state update.
- `app/build.gradle` — New package-name release build properties and internal API base URL.

### Backend user state
- `admin/backend/src/main/java/com/duodian/admin/entity/User.java` — Add hidden user migration field.
- `admin/backend/src/main/java/com/duodian/admin/controller/AuthController.java` — Login and `/auth/me` response payloads.
- `admin/backend/src/main/java/com/duodian/admin/controller/UserController.java` — Ensure admin-facing user endpoints do not expose or edit the hidden field unless reused for app auth.
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java` — Startup schema migration pattern for adding new columns.
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java` and auth/user tests — Existing backend test style.

### Prior decisions and related phases
- `.planning/phases/13-meituan-login-state-sync/13-STANDARD.md` — Login-state export/import identity constraints and profile lessons.
- `.planning/phases/16-app/16-CONTEXT.md` — Main app as login-state data center and server restore policy.
- `.planning/ROADMAP.md` — Phase 20 scope and acceptance criteria.
</canonical_refs>

<specifics>
## Specific Ideas

- Xiaomi MIX 2S device ID used for validation: `3ca26684`.
- Verified old engine `1.2.18-beta / 50028` contains `EngineCloneDataExportActivity`.
- Verified trigger command:
  `am start -n com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineCloneDataExportActivity --ez includeAll true`.
- Verified old export path:
  `/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/clone-export/clone_data_all_u0_20260622_020036.zip`.
- Verified final zip size:
  `4,563,468,021` bytes.
- Verified new main package UID on Android 10 can read the old engine external clone-export zip after export.
- Completion should use stable `.zip` detection rather than `.json` manifest.
- The new release should continue to point at internal API environment, not production, per the user's earlier instruction.
</specifics>

<deferred>
## Deferred Ideas

- Recovering or reverse-engineering the old signing keystore is out of scope.
- Supporting devices whose old engine predates `1.2.14-beta` and lacks `EngineCloneDataExportActivity` is a fallback/notification path, not a full rootless private-data migration guarantee.
- Making this migration visible/editable in admin UI is explicitly out of scope.
</deferred>

<scope_fence>
## Scope Fence

Do not implement a root-only migration, Magisk helper, adb-only manual procedure, or new-main-to-old-engine binder dependency. Do not start migration before login. Do not re-run migration for a server user once either local or server migration-complete state says it has completed.
</scope_fence>

---

*Phase: 20-new-package-migration-release*
*Context gathered: 2026-06-22 via user directive and Xiaomi validation*
