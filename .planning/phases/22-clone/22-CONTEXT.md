# Phase 22: Clone 身份唯一性收口与跨账号防串号 - Context

**Gathered:** 2026-06-26
**Status:** Ready for execution
**Source:** User request in `$gsd-plan-phase 22` plus OPPO/Xiaomi real-device investigation

<domain>

## Phase Boundary

Phase 22 fixes the shop identity boundary shared by APP, engine, and backend. The canonical rule is:

- Backend identity: current token `AuthContext.userId + cloneInstanceId`.
- Local runtime identity: `cloneInstanceId` resolves the current device's engine directory/user mapping.
- `localVirtualUserId` is a local engine directory number only. It can differ across devices, engine package names, migrations, and account history, so it cannot be used as a server-side identity guard.

The phase does not redesign clone billing, remote authorization, order ingestion, or profile migration. It only changes the minimal identity contract needed to stop shop info/login-state串号 while keeping current authorized shops openable.

</domain>

<decisions>

## Implementation Decisions

### D-22-01 Minimal Identity

The server must locate a reported shop by `AuthContext.userId + cloneInstanceId` whenever the request carries `cloneInstanceId`. With `cloneInstanceId` present, `/shops/report` must not fall back to "any pending shop for this user/package", because that fallback can attach a device runtime to the wrong card after account switching.

### D-22-02 Local Directory Number

`localVirtualUserId` remains accepted and recorded for diagnostics/current-device display, but it is not a cross-device consistency check. Existing data proves the same `cloneInstanceId` can be user 9 on one device and user 2 on another, so comparing to `shops.local_virtual_user_id` causes false "虚拟用户目录号校验失败" errors.

### D-22-03 Binding Guard

A clone already bound to a real platform shop ID cannot be silently overwritten by a different real platform shop ID. That condition means either the platform login state is for the wrong shop, or a card/runtime was mismatched. The backend must reject it with a clear error and must not update shop fields or login-state backup.

### D-22-04 Login-State Sequencing

APP shop-info reporting and login-state uploading must be a single ordered chain: extract verified shop identity, call `/shops/report`, and upload login-state only after the report succeeds. This prevents a failed shop identity report from still overwriting the server backup through `/shops/{id}/login-state`.

### D-22-05 Manifest Check

Login-state upload manifest must carry `systemShopId`, `packageName`, `profileId`, and `cloneInstanceId`. The backend validates the manifest against the target shop before accepting the blob. This guards the backup path even if APP-side sequencing regresses later.

### D-22-06 Compatibility

No destructive data migration is allowed. Existing `shops.local_virtual_user_id`, clone auth token, clone validation code/hash, and scoped engine directories remain compatible. Historical local user IDs may be overwritten by the most recent device report for display, but they are not authoritative.

</decisions>

<canonical_refs>

## Canonical References

Downstream implementation must read these before changing code.

### Planning

- `.planning/REQUIREMENTS.md` - Phase 22 requirement IDs `PH22-D01` through `PH22-D09`.
- `.planning/ROADMAP.md` - Phase 22 roadmap entry and environment boundary.
- `.planning/phases/13-meituan-login-state-sync/13-PLAN.md` - login-state profile and upload/restore constraints.
- `.planning/phases/16-app/16-PLAN.md` - APP-side login-state data center and "phone latest login-state wins" rule.
- `.planning/phases/20-new-package-migration-release/20-PLAN.md` - scoped engine storage and migration compatibility.

### Backend

- `admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java` - `/shops/report` identity matching and localVirtualUserId validation.
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java` - login-state upload and manifest validation.
- `admin/backend/src/main/java/com/duodian/admin/service/ShopService.java` - existing `findByUserIdAndCloneInstanceId`.
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopReportControllerTest.java`
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java`

### APP / Engine

- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt` - shop-info sync, report sequencing, and login-state export/upload.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt` - `/shops/report` callback surface.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/LocalShopIdentityStore.kt` - local verified-state keying.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt` - clone mapping key and scoped auth.
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/ScopedCloneStorage.kt` - physical scoped directory layout.

</canonical_refs>

<real_device_facts>

## Real-Device Findings

- OPPO `com.zhirang.zhanghaoguanjia` and `.engine` are both `1.2.19-release` versionCode `50034`.
- OPPO engine stores account-scoped cards under `blackbox/accounts/<serverUserId>/cards/<cloneInstanceId>`. The newly tested account's data is under `accounts/4`; the earlier account's Ele data is under `accounts/10`.
- OPPO symlinks for server user 4 point to `blackbox/accounts/4/cards/<cloneInstanceId>/user/<localVirtualUserId>/<packageName>`, with local virtual users in the 22..36 range.
- Xiaomi has both old package (`1.2.18-beta`) and new package (`1.2.19-release`) installed. Old and new engines can map the same clone to different local user IDs.
- The same clone ID `CLN1-15200837196-me.ele.napos-N1-U2-R068066dd` was observed with different local users across Xiaomi old/new engine state. This proves `localVirtualUserId` is device/engine-local.
- A malformed legacy/noise mapping was found in Xiaomi old engine state with `serverUserId=0` and a non-package string in `packageName`, proving broad global scans are unsafe as an identity source.
- Previous logcat investigation showed the "虚拟用户目录号校验失败" error came from `/shops/report` before login-state upload, not from the upload endpoint itself.

</real_device_facts>

<deferred>

## Deferred Ideas

- Full engine API refactor to remove every packageName+userId global lookup path.
- Database schema rename of `local_virtual_user_id` to a diagnostic field.
- Automatic remediation UI for a clone that is logged into a different platform shop than the card binding.
- Production rollout; production `zhirang-dev` must not be upgraded without explicit user approval.

</deferred>

---

*Phase: 22-clone*
*Context gathered: 2026-06-26 from real-device data and code inspection*
