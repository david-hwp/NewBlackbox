---
phase: 04-shop-id-display
plan: 02
type: execute
subsystem: Bcore / app
wave: 2
depends_on:
  - 04-01
tags:
  - shop-id
  - data-model
  - persistence
  - async-extraction
  - lifecycle-trigger
tech-stack:
  added: []
  patterns:
    - Parcel serialization with backward-compatible field append
    - Singleton background HandlerThread for async work
    - Package-private setters delegating to private modifyUserState
key-files:
  created:
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/ShopIdManager.java
  modified:
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageUserState.java
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageSettings.java
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java
    - Bcore/src/main/java/top/niunaijun/blackbox/app/BActivityThread.java
    - Bcore/src/main/java/top/niunaijun/blackbox/entity/pm/InstalledPackage.java
    - app/src/main/java/top/niunaijun/blackboxa/bean/AppInfo.kt
    - app/src/main/java/top/niunaijun/blackboxa/bean/InstalledAppBean.kt
decisions:
  - "BPackageSettings.modifyUserState() is private; added public setShopInfo()/clearShopInfo() setters following existing pattern (setInstalled, setStopped, setHidden)"
  - "Parcel backward compatibility: new shop fields appended after existing booleans; old files read gracefully (readString returns null)"
  - "30-second extraction throttle per package+user to prevent DoS on app startup"
  - "BActivityThread trigger placed after onAfterApplicationOnCreate so app is fully initialized before extraction begins"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-06-03"
---

# Phase 04 Plan 02: Data Model, Persistence, and Async Trigger Summary

Extend the data model and persistence layer to support shop ID storage, and wire the async extraction trigger into the app lifecycle.

## What Was Built

1. **BPackageUserState** — Extended with `shopId`, `shopName`, `platform` fields. Parcel serialization updated with backward-compatible append (old files read as null). Copy constructor and no-arg constructor updated.

2. **ShopIdManager** — New singleton orchestrator running on a background `HandlerThread`. Provides `triggerExtract()` which checks the extractor registry, enforces a 30-second throttle per package+user, runs extraction asynchronously, and persists results via `BPackageManagerService.updateShopInfo()`.

3. **BPackageManagerService** — Added `getShopInfo()`, `updateShopInfo()`, `clearShopInfo()` APIs. All methods synchronize on `mPackages`. Uses new public setters on `BPackageSettings`.

4. **BActivityThread** — Wires `ShopIdManager.get().triggerExtract()` after `onAfterApplicationOnCreate()` in `handleBindApplication()`, wrapped in try-catch to never crash app startup.

5. **InstalledPackage** — Added `getShopInfo()` convenience method delegating to `BPackageManagerService`.

6. **AppInfo & InstalledAppBean** — Extended with `shopId`, `shopName`, `platform` fields using Kotlin default arguments (`= null`) for full backward compatibility at all call sites.

## Commits

| Commit | Message | Files |
|--------|---------|-------|
| 73e852e8 | feat(04-02): extend BPackageUserState with shop fields and serialization | BPackageUserState.java |
| 73cb81df | feat(04-02): add ShopIdManager for async extraction orchestration | ShopIdManager.java |
| 2c3af0ad | feat(04-02): add shop info CRUD APIs and wire BActivityThread trigger | BPackageManagerService.java, BActivityThread.java, InstalledPackage.java |
| b4174609 | feat(04-02): update AppInfo and InstalledAppBean with shop fields | AppInfo.kt, InstalledAppBean.kt, BPackageSettings.java, BPackageManagerService.java |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] BPackageSettings.modifyUserState() is private**
- **Found during:** Task 3 compile verification
- **Issue:** `BPackageManagerService.updateShopInfo()` and `clearShopInfo()` called `ps.modifyUserState(userId)` which has `private` visibility in `BPackageSettings`
- **Fix:** Added public `setShopInfo(ShopInfo, int)` and `clearShopInfo(int)` methods to `BPackageSettings` following the existing pattern (`setInstalled`, `setStopped`, `setHidden`). Updated `BPackageManagerService` to use these public setters.
- **Files modified:** `BPackageSettings.java`, `BPackageManagerService.java`
- **Commit:** folded into b4174609

## Threat Flags

None — all security-relevant surface was already documented in the plan's threat model. No new network endpoints, auth paths, or trust boundaries introduced.

## Known Stubs

None. All data fields are wired to real persistence and extraction paths.

## Self-Check: PASSED

- [x] BPackageUserState.java exists with shop fields
- [x] ShopIdManager.java created
- [x] BPackageManagerService.java has getShopInfo/updateShopInfo/clearShopInfo
- [x] BActivityThread.java calls ShopIdManager.triggerExtract
- [x] InstalledPackage.java has getShopInfo()
- [x] AppInfo.kt has shop fields
- [x] InstalledAppBean.kt has shop fields
- [x] `./gradlew :Bcore:compileDebugJavaWithJavac :app:compileDebugKotlin` passes
- [x] All 4 commits recorded
