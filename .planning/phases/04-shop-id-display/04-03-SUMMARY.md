---
phase: 04-shop-id-display
plan: 03
status: complete
started: "2026-06-03T16:15:00+08:00"
completed: "2026-06-03T16:45:00+08:00"
commits:
  - "058d572e feat(04-03): add getShopInfo AIDL and BPackageManager proxy"
  - "59e4b393 feat(04-03): populate shop info in AppsRepository.getVmInstallList"
  - "7679ffda feat(04-03): add shopId TextView to item_app.xml layout"
  - "f0368840 feat(04-03): display shop ID in AppsAdapter with conditional formatting"
  - "7e00280c feat(04-03): trigger shop ID extraction in AppsFragment.onResume"
---

# Plan 04-03 Summary: UI Display Layer

## Objective
Update the UI layer to display shop IDs in the app grid, support manual refresh, and populate shop data from the persistence layer.

## Tasks Completed

### Task 1: Update AppsRepository to populate shop info
- Modified `AppsRepository.getVmInstallList()` to call `blackBoxCore.getBPackageManager().getShopInfo()` for each installed app
- AppInfo construction now passes `shopInfo?.shopId`, `shopInfo?.shopName`, `shopInfo?.platform`
- Added AIDL interface `getShopInfo(String packageName, int userId)` to `IBPackageManagerService.aidl`
- Added `ShopInfo.aidl` for Parcelable transport across IPC boundary
- Updated `BPackageManager` proxy to expose `getShopInfo()` method

### Task 2: Update item_app.xml layout
- Added `shopId` TextView below the `name` TextView in the grid item layout
- TextView configured with `textSize="10sp"`, secondary text color, initially `gone`
- Keeps layout compatible with existing 4-column GridLayoutManager

### Task 3: Update AppsAdapter to display shop ID
- Modified `AppsVH.setContent()` to conditionally format display name as `"{name}-{shopId}"` when shopId is present
- Falls back to original app name when shopId is null or blank
- `FallbackAppsVH` applies the same formatting logic
- `shopId` TextView visibility toggled based on presence of shopId data

### Task 4: Add onResume re-extraction trigger in AppsFragment
- Added `onResume()` override to trigger `ShopIdManager.get().triggerExtract()` for apps without shopId
- ShopIdManager's 30-second throttle prevents excessive extraction attempts
- Existing `onStart()` already calls `getInstalledAppsWithRetry()`, which picks up newly extracted shop IDs
- No layout changes required — leverages existing fragment lifecycle

## Deviation from Plan
- **AIDL addition**: To make `getShopInfo()` accessible from the app module, AIDL interfaces were added for `getShopInfo()` and `ShopInfo` parcelable. This was necessary because `BPackageManager` is a proxy that communicates with `BPackageManagerService` via AIDL/Binder IPC. The original plan did not anticipate this cross-module IPC requirement.

## Verification
- `./gradlew :Bcore:compileDebugJavaWithJavac` passed
- `./gradlew :app:compileDebugKotlin` passed with warnings (pre-existing)

## Issues Encountered
- Agent encountered a verification loop when trying to confirm `getShopInfo` symbol resolution across the Bcore/app module boundary. The AIDL approach resolved the IPC gap. Manual intervention was required to break the loop and write this summary.

## Key Files Created/Modified
| File | Change |
|------|--------|
| `app/src/main/java/top/niunaijun/blackboxa/data/AppsRepository.kt` | Populate shop info in getVmInstallList |
| `app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsAdapter.kt` | Conditional `{name}-{shopId}` display |
| `app/src/main/java/top/niunaijun/blackboxa/view/apps/AppsFragment.kt` | onResume extraction trigger |
| `app/src/main/res/layout/item_app.xml` | Added shopId TextView |
| `Bcore/src/main/aidl/top/niunaijun/blackbox/core/system/pm/IBPackageManagerService.aidl` | Added getShopInfo AIDL method |
| `Bcore/src/main/aidl/top/niunaijun/blackbox/entity/pm/ShopInfo.aidl` | New ShopInfo parcelable AIDL |
| `Bcore/src/main/java/top/niunaijun/blackbox/fake/frameworks/BPackageManager.java` | Added getShopInfo proxy method |
| `Bcore/src/main/java/top/niunaijun/blackbox/entity/pm/ShopInfo.java` | Made Parcelable for AIDL transport |

## Self-Check
- [x] All tasks executed
- [x] Each task committed individually
- [x] SUMMARY.md created and committed
