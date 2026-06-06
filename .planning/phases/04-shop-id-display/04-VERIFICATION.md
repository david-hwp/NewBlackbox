---
phase: 04-shop-id-display
verified: 2026-06-03T17:30:00Z
status: passed
score: 14/14 must-haves verified
overrides_applied: 0
overrides: []
gaps: []
human_verification:
  - test: "Install JD Jingming (com.jd.mrd.jingming) as virtual app, log in, and verify app list shows '京东秒送商家-{shopId}'"
    expected: "App grid displays concatenated name with shopId suffix after login"
    why_human: "Requires actual JD app login flow and SharedPreferences data generation; cannot be verified statically"
  - test: "Verify shop ID persists across BlackBox restarts"
    expected: "After killing and reopening BlackBox, previously extracted shop IDs still display"
    why_human: "Requires runtime behavior observation; static analysis confirms persistence path but not actual disk survival"
  - test: "Verify onResume re-extraction trigger works for apps without shopId"
    expected: "Navigating away and returning to AppsFragment triggers background extraction for apps missing shopId"
    why_human: "Lifecycle timing and async behavior require runtime observation"
---

# Phase 04: 店铺ID自动获取与展示 Verification Report

**Phase Goal:** BlackBox 自动获取分身应用登录后的店铺Id，获取成功后展示在分身名称后面，格式如：`京东秒送商家-16364870`。设计需支持多平台扩展（京东、淘宝闪购、美团外卖等）。

**Verified:** 2026-06-03T17:30:00Z

**Status:** passed

**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| #   | Truth                                                                 | Status     | Evidence |
| --- | --------------------------------------------------------------------- | ---------- | -------- |
| 1   | ShopIdExtractor interface exists with extract(context, userId) -> ShopInfo contract | VERIFIED   | `Bcore/.../pm/ShopIdExtractor.java` has both methods; compiles |
| 2   | ShopIdExtractorRegistry routes com.jd.mrd.jingming to JDShopIdExtractor | VERIFIED   | Registry static block registers JD extractor; `getExtractor()` returns non-null for JD package |
| 3   | JDShopIdExtractor reads SharedPreferences ge_tui_push_alias_bind_flag as primary strategy | VERIFIED   | `extractFromSharedPreferences()` parses XML via `BEnvironment.getXSharedPreferences()`; regex matches key; splits comma format |
| 4   | JDShopIdExtractor falls back to WebView DevTools URL parsing when SharedPrefs fails | VERIFIED   | `extractFromWebViewDevTools()` scans `/proc/net/unix`, connects LocalSocket, queries `/json/list`, parses `storeId` param |
| 5   | ShopInfo carries shopId, shopName, platform, extractedAt fields       | VERIFIED   | All four fields present; Parcelable implementation for AIDL; constructor auto-sets `extractedAt` |
| 6   | BPackageUserState stores shopId, shopName, platform per user per package | VERIFIED   | Three fields added; `writeToParcel`/`CREATOR` updated; copy constructor updated; backward-compatible append |
| 7   | BPackageManagerService exposes getShopInfo and updateShopInfo APIs    | VERIFIED   | Three public methods: `getShopInfo()`, `updateShopInfo()`, `clearShopInfo()`; all synchronize on `mPackages` |
| 8   | BActivityThread triggers async shop ID extraction after application bind completes | VERIFIED   | `triggerExtract()` called after `onAfterApplicationOnCreate()` in `handleBindApplication()`; wrapped in try-catch |
| 9   | AppInfo (app module) carries shopId, shopName, platform fields from Bcore | VERIFIED   | Data class has three fields with `= null` defaults; all existing call sites compile unchanged |
| 10  | InstalledAppBean carries shopId, shopName, platform fields for UI consumption | VERIFIED   | Data class has three fields with `= null` defaults |
| 11  | AppsRepository populates AppInfo.shopId from BPackageManagerService.getShopInfo() | VERIFIED   | `getVmInstallList()` calls `getShopInfo()` per app; passes shop fields to AppInfo constructor |
| 12  | AppsAdapter displays '{appName}-{shopId}' when shopId is present, original name when absent | VERIFIED   | `AppsVH.setContent()` and `FallbackAppsVH.setContent()` both use conditional `${name}-${shopId}` formatting |
| 13  | AppsFragment triggers re-extraction in onResume (throttled by ShopIdManager) | VERIFIED   | `onResume()` iterates apps without shopId, calls `ShopIdManager.get().triggerExtract()`; 3s delayed refresh follows |
| 14  | item_app.xml has shopId TextView                                      | VERIFIED   | Second TextView with id `@+id/shopId`, textSize 10sp, secondary_text color, initially `gone`; data binding generates `shopId` field |

**Score:** 14/14 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `Bcore/.../entity/pm/ShopInfo.java` | Data class with shopId, shopName, platform, extractedAt | VERIFIED | Parcelable for AIDL IPC; 4 fields; 2 constructors |
| `Bcore/.../pm/ShopIdExtractor.java` | Generic extraction interface | VERIFIED | 2 methods: `extract(Context,int)` and `getTargetPackage()` |
| `Bcore/.../pm/ShopIdExtractorRegistry.java` | Package-name to extractor routing | VERIFIED | Static map with JD registration; thread-safe `register()` |
| `Bcore/.../pm/JDShopIdExtractor.java` | JD-specific extraction implementation | VERIFIED | Dual strategy (SharedPrefs + WebView DevTools); 5s timeout; numeric validation |
| `Bcore/.../pm/BPackageUserState.java` | Per-user per-package state with shop fields | VERIFIED | 3 new fields; Parcel serialization updated; backward compatible |
| `Bcore/.../pm/ShopIdManager.java` | Async extraction orchestrator with throttling | VERIFIED | Singleton; background HandlerThread; 30s throttle; persists via BPackageManagerService |
| `Bcore/.../pm/BPackageManagerService.java` | Shop info CRUD APIs | VERIFIED | `getShopInfo()`, `updateShopInfo()`, `clearShopInfo()`; synchronized |
| `Bcore/.../pm/BPackageSettings.java` | Package settings with shop setters | VERIFIED | `setShopInfo()` and `clearShopInfo()` public setters following existing pattern |
| `Bcore/.../app/BActivityThread.java` | App lifecycle trigger | VERIFIED | `triggerExtract()` called after `onAfterApplicationOnCreate()` |
| `Bcore/.../entity/pm/InstalledPackage.java` | Convenience getShopInfo() method | VERIFIED | Delegates to `BPackageManagerService.get().getShopInfo()` |
| `app/.../bean/AppInfo.kt` | UI data model with shop fields | VERIFIED | 3 nullable fields with default `= null`; backward compatible |
| `app/.../bean/InstalledAppBean.kt` | UI data model with shop fields | VERIFIED | 3 nullable fields with default `= null`; backward compatible |
| `app/.../data/AppsRepository.kt` | Shop info population in getVmInstallList | VERIFIED | Calls `getShopInfo()` per app; constructs AppInfo with shop fields |
| `app/.../view/apps/AppsAdapter.kt` | UI display with shop ID suffix | VERIFIED | Conditional `{name}-{shopId}` display; both VH classes updated |
| `app/.../view/apps/AppsFragment.kt` | onResume extraction trigger | VERIFIED | Triggers extraction for apps without shopId; 3s delayed list refresh |
| `app/.../res/layout/item_app.xml` | Grid item layout with shopId TextView | VERIFIED | shopId TextView below name; 10sp; secondary_text; gone by default |
| `Bcore/.../aidl/.../IBPackageManagerService.aidl` | AIDL interface with getShopInfo | VERIFIED | Method added; ShopInfo imported as parcelable |
| `Bcore/.../aidl/.../entity/pm/ShopInfo.aidl` | ShopInfo parcelable AIDL | VERIFIED | `parcelable ShopInfo;` declaration |
| `Bcore/.../fake/frameworks/BPackageManager.java` | Proxy getShopInfo method | VERIFIED | Delegates to AIDL service with null-safety and exception handling |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| ShopIdExtractorRegistry | JDShopIdExtractor | static registration in EXTRACTORS map | WIRED | `EXTRACTORS.put("com.jd.mrd.jingming", new JDShopIdExtractor())` in static block |
| JDShopIdExtractor | BEnvironment.getXSharedPreferences | File-based XML read | WIRED | `BEnvironment.getXSharedPreferences(TARGET_PACKAGE, PREFS_FILE)` returns File; parsed with regex |
| JDShopIdExtractor | ShopInfo | extract() return value | WIRED | Returns `new ShopInfo(shopId, null, "jd")` from both strategies |
| BActivityThread.handleBindApplication | ShopIdManager.triggerExtract | post to background Handler after onAfterApplicationOnCreate | WIRED | Called at line 484 after `onAfterApplicationOnCreate()`; wrapped in try-catch |
| ShopIdManager | JDShopIdExtractor | ShopIdExtractorRegistry.getExtractor(packageName) | WIRED | Line 72: `ShopIdExtractor extractor = ShopIdExtractorRegistry.getExtractor(packageName)` |
| ShopIdManager | BPackageManagerService.updateShopInfo | callback after async extraction completes | WIRED | Line 78: `BPackageManagerService.get().updateShopInfo(packageName, userId, result)` |
| AppsRepository.getVmInstallList | BPackageManagerService.getShopInfo | BlackBoxCore.getBPackageManager().getShopInfo() | WIRED | Line 339: calls `getShopInfo(packageName, userId)` for each app |
| AppsAdapter.AppsVH.setContent | AppInfo.shopId | conditional name formatting | WIRED | Lines 71-76: `if (!item.shopId.isNullOrBlank()) "${item.name}-${item.shopId}"` |
| AppsFragment | ShopIdManager.triggerExtract | onResume lifecycle | WIRED | Line 190: `ShopIdManager.get().triggerExtract(app.packageName, userID, requireContext())` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| JDShopIdExtractor | shopId | SharedPreferences XML file or WebView DevTools socket | Yes - reads actual app data | FLOWING |
| ShopIdManager | result | JDShopIdExtractor.extract() | Yes - extractor returns real ShopInfo | FLOWING |
| BPackageManagerService | state.shopId | BPackageSettings.modifyUserState() | Yes - persisted to Parcel file via ps.save() | FLOWING |
| AppsRepository | shopInfo | BlackBoxCore.getBPackageManager().getShopInfo() | Yes - reads from persisted BPackageSettings | FLOWING |
| AppsAdapter | displayName | AppInfo.shopId (from repository) | Yes - rendered conditionally in setContent() | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Bcore compiles | `./gradlew :Bcore:compileDebugJavaWithJavac` | BUILD SUCCESSFUL | PASS |
| App Kotlin compiles | `./gradlew :app:compileDebugKotlin` | BUILD SUCCESSFUL | PASS |
| Full APK builds | `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL (70 tasks) | PASS |
| Data binding generates shopId field | `find app/build -name "ItemAppBinding*"` | Generated Java binding with `TextView shopId` field | PASS |

### Probe Execution

No probes defined for this phase. Spot-checks above serve as runnable verification.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ----------- | ----------- | ------ | -------- |
| SHOP-01 | 04-01 | ShopIdExtractor interface with extract/getTargetPackage | SATISFIED | `ShopIdExtractor.java` exists with correct contract |
| SHOP-02 | 04-01 | ShopIdExtractorRegistry routes by package name | SATISFIED | Registry has static map with JD registration; `getExtractor()`/`hasExtractor()` methods |
| SHOP-03 | 04-01 | JDShopIdExtractor with SharedPrefs + WebView fallback | SATISFIED | Two strategies implemented; 5s timeout; numeric validation; read-only |
| SHOP-04 | 04-02 | BPackageUserState stores shop fields with Parcel serialization | SATISFIED | Three fields added; writeToParcel/CREATOR/copy constructor all updated |
| SHOP-05 | 04-02 | BPackageManagerService exposes getShopInfo/updateShopInfo/clearShopInfo | SATISFIED | All three methods present; synchronized on mPackages; delegates to BPackageSettings |
| SHOP-06 | 04-02 | BActivityThread triggers extraction after app bind; ShopIdManager async with 30s throttle | SATISFIED | Trigger at line 484 after onAfterApplicationOnCreate; ShopIdManager has HandlerThread + EXTRACT_THROTTLE_MS |
| SHOP-07 | 04-03 | AppsRepository populates shop info from BPackageManagerService | SATISFIED | `getVmInstallList()` calls `getShopInfo()` per app; constructs AppInfo with shop fields |
| SHOP-08 | 04-03 | AppsAdapter displays {name}-{shopId} format | SATISFIED | Both AppsVH and FallbackAppsVH use conditional formatting; shopId TextView toggles visibility |
| SHOP-09 | 04-03 | AppsFragment triggers re-extraction in onResume | SATISFIED | `onResume()` iterates apps, calls `triggerExtract()` for those without shopId; 3s delayed refresh |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| None | — | — | — | No debt markers, stubs, or anti-patterns found in phase-modified files |

**Notes:**
- `ShopIdExtractorRegistry.java` line 21 has commented placeholder lines for Taobao/Meituan extractors. This is intentional extension documentation, not a stub.
- `BActivityThread.java` lines 511, 520 contain "not available" in log messages unrelated to this phase (pre-existing JAR loading code).

### Human Verification Required

1. **End-to-end JD login flow**
   - **Test:** Install JD Jingming (`com.jd.mrd.jingming`) as virtual app, complete login, return to BlackBox app list
   - **Expected:** App grid shows `京东秒送商家-16364870` (or actual shopId)
   - **Why human:** Requires actual JD app login flow and SharedPreferences data generation

2. **Persistence across restarts**
   - **Test:** Kill BlackBox completely, reopen, navigate to app list
   - **Expected:** Previously extracted shop IDs still display without re-login
   - **Why human:** Requires runtime behavior observation; static analysis confirms persistence path but not actual disk survival

3. **onResume re-extraction behavior**
   - **Test:** Install JD app but do NOT log in; observe app list; navigate away and return
   - **Expected:** App shows original name (no suffix); background extraction attempted (throttled); no UI crash
   - **Why human:** Lifecycle timing and async background behavior require runtime observation

### Gaps Summary

No gaps found. All 14 observable truths verified. All 19 artifacts exist, are substantive, and are wired. Full compilation passes. All 9 requirement IDs (SHOP-01 through SHOP-09) are satisfied.

---

_Verified: 2026-06-03T17:30:00Z_
_Verifier: Claude (gsd-verifier)_
