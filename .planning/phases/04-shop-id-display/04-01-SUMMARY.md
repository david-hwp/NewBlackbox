---
phase: 04-shop-id-display
plan: 01
subsystem: Bcore / Package Management
completed: 2026-06-03
tags: [shop-id, extraction, jd, registry, infrastructure]
dependency_graph:
  requires: []
  provides: [04-02, 04-03]
  affects: [BPackageManagerService]
tech_stack:
  added: []
  patterns: [Registry pattern, Strategy pattern, Defensive parsing]
key_files:
  created:
    - Bcore/src/main/java/top/niunaijun/blackbox/entity/pm/ShopInfo.java
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/ShopIdExtractor.java
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/ShopIdExtractorRegistry.java
    - Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/JDShopIdExtractor.java
  modified: []
decisions:
  - "ShopInfo is a plain data class (no Parcelable) because it is consumed within the host process only"
  - "JDShopIdExtractor uses direct XML file read instead of Android SharedPreferences API to avoid cross-process issues with virtual app data"
  - "WebView DevTools fallback uses LocalSocket abstract namespace connection rather than adb forward for in-process execution"
  - "Numeric validation (isValidShopId) added per T-04-03 to mitigate tampered SharedPreferences XML"
metrics:
  duration: "~10 minutes"
  tasks_completed: 3
  files_created: 4
  compile_errors: 0
---

# Phase 04 Plan 01: Shop ID Extraction Infrastructure Summary

One-liner: Core extraction framework with generic ShopIdExtractor interface, ShopInfo data model, registry-based routing, and JD-specific dual-strategy extractor (SharedPreferences primary + WebView DevTools fallback).

## What Was Built

### 1. ShopInfo Data Class (`entity/pm/ShopInfo.java`)
- Four public fields: `shopId`, `shopName`, `platform`, `extractedAt`
- Convenience constructor `(shopId, shopName, platform)` auto-sets `extractedAt = System.currentTimeMillis()`
- No-arg constructor for framework use

### 2. ShopIdExtractor Interface (`core/system/pm/ShopIdExtractor.java`)
- `ShopInfo extract(Context context, int userId)` — attempts extraction, returns null on failure
- `String getTargetPackage()` — returns handled package name
- Documented as read-only, background-safe, and exception-resilient

### 3. ShopIdExtractorRegistry (`core/system/pm/ShopIdExtractorRegistry.java`)
- Singleton with `static final Map<String, ShopIdExtractor> EXTRACTORS`
- JD extractor pre-registered for `com.jd.mrd.jingming`
- Thread-safe `register()` with `synchronized (EXTRACTORS)`
- Placeholder comments for Taobao and Meituan extensions

### 4. JDShopIdExtractor (`core/system/pm/JDShopIdExtractor.java`)
- **Primary strategy**: Reads `BEnvironment.getXSharedPreferences("com.jd.mrd.jingming", "JingmingAndroidClient")` XML, parses `ge_tui_push_alias_bind_flag` value (format: `shopId,true`), validates numeric shopId
- **Fallback strategy**: Finds JD process PID via ActivityManager, scans `/proc/net/unix` for `webview_devtools_remote_{pid}`, connects via `LocalSocket` (abstract namespace), sends HTTP GET `/json/list`, parses JSON for `storeId` query parameter in URLs
- **5-second timeout**: `deadline` checked between strategies and during socket I/O
- **Defensive validation**: `isValidShopId()` rejects null, empty, and non-numeric strings (T-04-03 mitigation)
- **Logging**: Slog.d/Slog.w/Slog.e with tag "JDShopIdExtractor"

## Deviations from Plan

None — plan executed exactly as written.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: tampering-mitigation | JDShopIdExtractor.java | `isValidShopId()` validates numeric format before returning parsed shopId (T-04-03) |
| threat_flag: dos-mitigation | JDShopIdExtractor.java | 5s deadline + try-catch on all I/O paths (T-04-02) |

## Known Stubs

None. All four files are fully implemented with functional extraction logic.

## Self-Check: PASSED

- [x] `ShopInfo.java` exists and compiles
- [x] `ShopIdExtractor.java` exists and compiles
- [x] `ShopIdExtractorRegistry.java` exists and compiles
- [x] `JDShopIdExtractor.java` exists and compiles
- [x] `./gradlew :Bcore:compileDebugJavaWithJavac` passes with 0 errors
- [x] All three task commits present in git log
