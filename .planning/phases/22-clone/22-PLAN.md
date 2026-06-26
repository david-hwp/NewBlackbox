---
phase: 22
plan: 22-PLAN
type: execute
wave: 1
depends_on:
  - .planning/phases/13-meituan-login-state-sync/13-PLAN.md
  - .planning/phases/16-app/16-PLAN.md
  - .planning/phases/20-new-package-migration-release/20-PLAN.md
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java
  - admin/backend/src/test/java/com/duodian/admin/controller/ShopReportControllerTest.java
  - admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/ShopListAdapter.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/ShopOrderScope.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt
  - app/src/main/java/com/zhirang/zhanghaoguanjia/data/LocalShopIdentityStore.kt
  - app/src/test/java/com/zhirang/zhanghaoguanjia/view/home/ShopOrderScopeTest.kt
autonomous: false
requirements:
  - PH22-D01
  - PH22-D02
  - PH22-D03
  - PH22-D04
  - PH22-D05
  - PH22-D06
  - PH22-D07
  - PH22-D08
  - PH22-D09
  - PH22-D10
---

<objective>
Unify shop identity around `AuthContext.userId + cloneInstanceId` on the server and `cloneInstanceId` in the local engine, demote `localVirtualUserId` to a device-local directory number, and verify on OPPO with two account switch scenarios that shop info and login-state backups no longer串号.
</objective>

<must_haves>
- PH22-D01: APP, engine, and backend use the minimal identity rule.
- PH22-D02: `localVirtualUserId` is no longer a server-side identity check.
- PH22-D03: OPPO/Xiaomi real-device data compatibility is documented before code changes.
- PH22-D04: `/shops/report` with `cloneInstanceId` only matches current user + clone.
- PH22-D05: an already bound real platform shop ID cannot be overwritten by another real shop ID on the same clone.
- PH22-D06: login-state manifest carries and validates `cloneInstanceId`.
- PH22-D07: APP uploads login-state only after shop basic info report succeeds.
- PH22-D08: historical local user IDs, clone auth, scoped dirs, and existing openable shops remain compatible.
- PH22-D09: OPPO real-device dual-account verification covers 二公子 and 贺伟平 account switching without storing passwords in repo artifacts.
- PH22-D10: APP card reorder saves only one package/platform scope and does not submit mixed-platform shop IDs.
</must_haves>

<tasks>

<task id="22-01" type="research">
<title>Real-device compatibility matrix</title>
<read_first>
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/ScopedCloneStorage.kt`
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/CloneInstanceStore.kt`
- `.planning/phases/22-clone/22-RESEARCH.md`
</read_first>
<action>
Record the OPPO and Xiaomi observed clone mappings before implementation:
- package versions for main app and engine.
- scoped `accounts/<serverUserId>/cards/<cloneInstanceId>` presence.
- local virtual user differences for the same clone across devices/engines.
- auth meta and runtime symlink shape.
- known malformed legacy mapping evidence.
</action>
<acceptance_criteria>
- The compatibility finding explicitly supports `userId + cloneInstanceId` on the server.
- The finding explicitly rejects `localVirtualUserId` as a cross-device identity guard.
- No test passwords are written into planning docs.
</acceptance_criteria>
<verify>
- Inspect `.planning/phases/22-clone/22-CONTEXT.md` and `22-RESEARCH.md`.
</verify>
</task>

<task id="22-02" type="execute">
<title>Backend report identity guard</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java`
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopReportControllerTest.java`
- `admin/backend/src/main/java/com/duodian/admin/service/ShopService.java`
</read_first>
<action>
Update `/shops/report`:
- When `cloneInstanceId` is present, locate only by current `userId + cloneInstanceId`.
- If that lookup misses, return the existing "待登录店铺不存在，请先添加店铺卡片" error without package-pending fallback.
- Remove stored-vs-requested `localVirtualUserId` mismatch rejection; reject only negative local IDs.
- For verified real shop info, reject if the matched clone is already bound to a different real platform shop ID.
- Continue accepting pending `NEW-*` cards and same-shop refreshes.
- Keep clone validation code/hash ownership checks unchanged.
</action>
<acceptance_criteria>
- Cross-device local user ID changes no longer return "虚拟用户目录号校验失败".
- A real shop ID change on an existing clone is rejected without updating the shop.
- Clone-aware reports cannot bind to an unrelated pending shop by package.
- Existing pending-shop conversion still works.
</acceptance_criteria>
<verify>
- `cd admin/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Dtest=ShopReportControllerTest test`
</verify>
</task>

<task id="22-03" type="execute">
<title>Backend login-state manifest guard</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java`
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java`
</read_first>
<action>
Update login-state upload validation:
- Validate manifest `systemShopId` against the path shop ID when present.
- Validate manifest `cloneInstanceId` against the target shop's stored clone ID when present.
- Keep package/profile validation.
- Preserve compatibility for older clients that omit `cloneInstanceId`, but reject explicit mismatch.
</action>
<acceptance_criteria>
- Login-state upload with a matching clone succeeds.
- Upload with explicit wrong clone ID is rejected before blob persistence.
- Existing additional Phase 13 profiles still pass.
</acceptance_criteria>
<verify>
- `cd admin/backend && JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn -Dtest=ShopControllerTest test`
</verify>
</task>

<task id="22-04" type="execute">
<title>APP report/upload sequencing and local identity key</title>
<read_first>
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/LocalShopIdentityStore.kt`
</read_first>
<action>
Update APP behavior:
- Make `reportShop`/`completePendingShop` expose a success/failure callback.
- In `reportDetectedShopInfo`, upload login-state only inside the successful report callback.
- Remove the same-shop fast path that skips report only because the stored local user ID matches; the local user ID can differ per device.
- Add `cloneInstanceId` and platform shop ID to login-state manifest.
- Key local identity verification by current app user, system shop ID, package name, and clone ID; do not require server-stored localVirtualUserId to read the marker.
</action>
<acceptance_criteria>
- A failed `/shops/report` does not trigger login-state upload.
- A successful report uploads a manifest containing `systemShopId`, `packageName`, `profileId`, `cloneInstanceId`, and `platformShopId`.
- Local verified status survives a server-side `localVirtualUserId` value changed by another device.
</acceptance_criteria>
<verify>
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin --no-daemon`
</verify>
</task>

<task id="22-05" type="verify">
<title>OPPO dual-account real-device verification</title>
<read_first>
- `.planning/phases/22-clone/22-CONTEXT.md`
- `.planning/phases/22-clone/22-RESEARCH.md`
</read_first>
<action>
Install the current main app on OPPO using the intranet API configuration, then verify by alternating accounts:
- 二公子 account.
- 贺伟平 account.
- Refresh shop list and open relevant shops after each login.
- Monitor logcat for platform-side shop ID, system `shops.id`, `cloneInstanceId`, and `localVirtualUserId`.
- Confirm `/shops/report` accepts different local virtual user IDs for the same clone and rejects only true shop identity mismatch.
- Confirm login-state upload happens only after report success.
</action>
<acceptance_criteria>
- No repo/planning artifact contains account passwords.
- OPPO logs show account switching does not overwrite another user's shop card or login-state backup.
- Existing authorized/openable shops still open after the change.
- Any live platform authorization blocker is documented separately from code correctness.
</acceptance_criteria>
<verify>
- `adb -s 55J7JJWKTWKNHYZL logcat` filtered for shop report/upload markers.
- Manual OPPO headed-device flow with the two provided accounts.
</verify>
</task>

<task id="22-06" type="execute">
<title>APP platform-scoped shop card reorder</title>
<read_first>
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/ShopListAdapter.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt`
- `admin/backend/src/main/java/com/duodian/admin/service/ShopService.java`
</read_first>
<action>
Fix APP shop-card drag sorting so it matches the backend `/shops/order` safety boundary:
- Capture the dragged shop at long-press start.
- Let the visible drag interaction keep working in a mixed-platform list.
- Submit only the ordered shops with the dragged shop's normalized `packageName` and `platform.id` instead of all visible shops.
- Do not send a mixed-platform `/shops/order` request when the list contains other platforms.
- Keep the backend same-platform guard unchanged.
</action>
<acceptance_criteria>
- Reordering 京东秒送 cards in a mixed platform list no longer triggers "一次只能调整同一平台下的店铺排序".
- Dragging in a mixed-platform list does not call `/api/shops/order` with mixed platform IDs.
- Existing same-platform sort order still persists and reloads from the server.
</acceptance_criteria>
<verify>
- `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:compileDebugKotlin --no-daemon`
- OPPO headed-device reorder verification against `http://172.20.0.13:8006/api`.
</verify>
</task>

</tasks>

<artifacts_this_phase_produces>
- Updated backend report and upload identity guards.
- Updated APP sequencing and manifest identity fields.
- Platform-scoped APP shop-card reorder.
- Backend controller tests for Phase 22 failure modes.
- OPPO dual-account verification notes in the completion summary.
</artifacts_this_phase_produces>

<not_in_scope>
- Production `zhirang-dev` deployment.
- Changing Phase 19 remote browser profile storage.
- Rewriting clone billing or authorization token format.
- Writing test account passwords into repository files.
</not_in_scope>
