# Phase 20 Summary

Date: 2026-06-22
Status: Debug verified; release packaging intentionally deferred for fast validation.

## Implemented

- Added first-login legacy engine migration for the new main package.
- Export is triggered from the installed legacy engine package after login, scoped by the logged-in server user shops.
- Import is handled by the new engine package and binds migrated clone data into the same server user directory.
- Added local per-user migration state so the same user does not re-run migration after success.
- Added backend `legacyEngineMigrated` / `legacyEngineMigratedAt` user fields and `POST /auth/legacy-engine-migration/complete`.
- The server marker is returned in login/user responses but no admin UI column was added.
- Deployed the backend endpoint to the intranet backend used by the debug APK.

## Verification

- Android debug build passed:
  `./gradlew :app:compileDebugKotlin :app:assembleDebug -PDUODIAN_APP_APPLICATION_ID=com.zhirang.zhanghaoguanjia.new -PDUODIAN_ENGINE_APPLICATION_ID=com.zhirang.zhanghaoguanjia.new.engine -PDUODIAN_API_BASE_URL=http://172.20.0.13:8006/api/`
- Backend controller test passed on the intranet build machine:
  `mvn test -Dtest=AuthControllerTest`
- Xiaomi device debug test passed with account `15200837196` / user `10`:
  - First login armed migration and exported/imported legacy engine clone data.
  - Import result: `requested=41`, `matched=16`, `copiedFiles=13880`, `copiedBytes=2158239901`, `ok=true`.
  - Server marker became `legacyEngineMigrated=true`.
  - New engine data directory for user `10` contains migrated clone data, about `2.1G`.
  - After clearing only the new main app data and logging in again, migration was not armed and no export/import activity started.

## Notes

- Release APK build and announcement are deferred by the latest validation request.
- Generated `black-reflection/bin/` and `compiler/bin/` class outputs are intentionally not committed.
