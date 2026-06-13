# Phase 13 Plan: Meituan Login-State Sync

Status: closed on 2026-06-13 with OPPO-to-Xiaomi device validation. Core Meituan/JD/Ele.me profiles and three Wave 6 additional platforms are accepted; Douyin Laike is documented as a legacy investigation item and is not marked supported.

## Intent

Move an already logged-in Meituan Waimai merchant clone from one device to another without uploading the full 170 MB+ engine export. The validated default package is the CIPS-only profile for `com.sankuai.meituan.meituanwaimaibusiness`.

## Wave 0: User and Card Scoped Clone Directories

This wave must land before every login-state sync wave.

Problem: the current BlackBox virtual user directory is a flat local user id namespace. Repair-shop flows and same-device login under different accounts can rebind shop cards to new `localVirtualUserId` values. If restore code writes to a stale user id, a shop card can open the wrong clone data or look logged out even when valid data exists elsewhere.

Required model:

- Engine clone data directories must use 店铺管家 user id as the parent isolation level.
- Under each 店铺管家 user id, each business card / `cloneInstanceId` owns its own `user` sub-clone directory namespace.
- The logical directory shape is `accounts/<serverUserId>/cards/<cloneInstanceId>/user/<localVirtualUserId>/<packageName>/...`, with equivalent scoped roots for user-de, external data, profiles, and auth metadata where needed.
- A shop card restore resolves its active clone binding from `serverUserId + packageName + cloneInstanceId`, then writes only to that binding's scoped `user/<localVirtualUserId>` package directory.
- Repair-shop must clear only the data for that card binding and must not accidentally reuse another account's or card's data directory.
- Same physical device with different logged-in 店铺管家 accounts must not share or overwrite clone mappings.
- Restore code must reject packages that would create nested package directories, source-user paths, or write into a user id that does not match the active card binding.
- A new engine data migration Activity must move already-installed historical flat clone data into the scoped account/card directory model.
- After installing or upgrading to the new engine, the main app must trigger this migration immediately and show a blocking/progress prompt such as "正在迁移分身数据" so users do not open shops while historical login-state data is moving.

Implementation tasks:

1. Audit `CloneInstanceStore`, `ensureCloneUser`, `bindCloneUser`, `clearCloneUser`, repair-shop, restore-shop, and launch paths.
2. Introduce a stable scoped clone key: `serverUserId | packageName | cloneInstanceId`.
3. Change `BEnvironment` / engine path resolution for clone app data from flat `blackbox/data/user/<id>` access to scoped account/card roots, while preserving BlackBox runtime compatibility through a single resolution layer.
4. Add `EngineCloneDataMigrationActivity` in the engine package. It scans existing flat user, user_de, external, profile, clone-auth, and clone-instance metadata, computes the owning `serverUserId + cloneInstanceId`, and moves data into the scoped directory model.
5. Make migration idempotent and crash-safe: write a migration state file, move through staging paths, fsync/rename where practical, and keep rollback backups until verification passes.
6. Trigger the migration immediately after engine install/upgrade from the main app. Display a blocking progress dialog while migration is running and a clear failure state if migration cannot complete.
7. Ensure all directory writes and deletes resolve through the scoped key and the current `localVirtualUserId`.
8. Add guardrails before restore: target scoped virtual user exists, package is installed for that scoped user, target package path equals the card binding, and no nested paths are present.
9. Add tests for two 店铺管家 accounts on one device, two cards for the same package, repair-shop remapping, stale user id rejection, and idempotent migration rerun.

Acceptance:

- Repairing one shop does not delete or rebind another shop using the same platform package.
- Logging into a different 店铺管家 account on the same device does not reuse the previous account's clone directory mapping.
- A restore attempt to `user3` fails when the active card binding is `user22`.
- Upgrading from the historical flat directory layout migrates already-installed clone data into `serverUserId/card/user` scoped directories without losing existing login state.
- The first launch after engine install/upgrade visibly runs migration before users can open shop cards.
- Re-running migration after success is a no-op.
- If migration fails, original historical data is preserved and the user is told to retry instead of silently clearing data.
- The app can still launch existing Phase 9 authorized clones.

Device validation on 2026-06-12:

- Xiaomi installed `1.2.13-phase13` app and engine, then first launch started `EngineCloneDataMigrationActivity` and completed migration before normal card use.
- Scoped directories were verified for account `10`: Meituan card under `accounts/10/cards/clone-0c0e.../user/22/...`, JD under `accounts/10/cards/CLN1-...jingming.../user/15/...`, and Ele.me under `accounts/10/cards/CLN1-...napos.../user/2/...`.
- Legacy flat paths such as `blackbox/data/user/22/com.sankuai.meituan.meituanwaimaibusiness`, `blackbox/data/user/15/com.jd.mrd.jingming`, and `blackbox/data/user/2/me.ele.napos` were symlinks to scoped account/card paths, with no nested package directory.
- After migration, clicking the existing Xiaomi cards still opened Meituan `MainActivity`, JD `CaterMainActivity`, and Ele.me `HomeTabActivity`.

## Wave 1: Meituan CIPS-only Export Profile

Implement the default Meituan Waimai login-state export profile from `13-STANDARD.md`.

Tasks:

1. Add an engine-side export profile for `com.sankuai.meituan.meituanwaimaibusiness`.
2. Export logical roots, not ADB absolute paths.
3. Include only selected `data/files/cips/**` and `data/cache/cips/**` state records.
4. Exclude CIPS resource bundles, non-CIPS DBs, IM history DBs, WebView state, Horn config, external data, and full cache by default.
5. Write a manifest with file count, byte size, source user id, profile id, and hash. Do not log token/cookie/session values.

Acceptance:

- 罗家臭豆腐 export is about 0.45-0.55 MB compressed and about 400-500 CIPS state files for the tested data shape.
- Exported zip contains no `blackbox/data/user/<id>` paths.
- Exported zip contains no `virtual-data-user-*` paths.
- Server restore onto Xiaomi downloads `shops/4/login-state` and restores the artifact into the active card binding `accounts/10/cards/<clone>/user/22/...`, not the OPPO source `user3`.

## Wave 2: JD and Ele.me Login-State Profiles

Implement the verified JD and Ele.me login-state profiles from the OPPO-to-Xiaomi ADB research. These profiles are separate from Meituan CIPS; do not generalize CIPS to non-Meituan packages.

Target packages:

```text
com.jd.mrd.jingming
me.ele.napos
```

Verified scope:

- Test and fallback order is `E -> D -> C`, where E is the smallest hand-picked login/account/device/shop state file set, D is all `shared_prefs/**`, and C is D plus core DB files. Start at E and only escalate when the current candidate reaches username/password/SMS login.
- Treat graphic CAPTCHA, slider, and security verification as login-state success. Only username/password/SMS login entry is failure.
- JD source is OPPO `user28`, not `user18`. Evidence: `storeId=14395758`, `storeName=罗家臭豆腐(东瓜山店)` in `shared_prefs/JingmingAndroidClient.xml`.
- JD target is Xiaomi 罗家京东 card `localVirtualUserId=15`.
- Ele.me source is OPPO `user2`. Evidence: `shopId=1184657317`, `shopName=luojia6688`, `userId=5329558971` in shared prefs.
- Ele.me target is Xiaomi 罗家饿了么 card `localVirtualUserId=2`.
- Device versions were aligned before validation: JD `9.58.1 / 905810`, Ele.me `14.4.4 / 140404`.
- Release must clear only the active card-bound package directory and must avoid source-user paths, nested package dirs, and stale virtual users.

Tasks:

1. Add engine-side export profile `jd-jingming-prefs-d` for `com.jd.mrd.jingming`.
   - Include all files under `data/shared_prefs/**`.
   - Exclude `databases/**`, WebView state, React/native resource bundles, `files/**`, `cache/**`, and external data by default.
2. Add engine-side export profile `ele-napos-prefs-e-min` for `me.ele.napos`.
   - Include only the verified shared-pref allowlist in `13-STANDARD.md`.
   - Exclude DBs, WebView, cache, resources, logs, external data, and full `files/**`.
3. Export logical roots, not ADB absolute paths.
4. Write a manifest with package, platform, profile id, source user id, file count, raw bytes, zip bytes, and hash. Do not log token/cookie/session values.
5. Implement fallback selection only when the minimum profile opens username/password/SMS login:
   - JD starts at E. The verified E-min failed, so JD default is D.
   - JD fallback: escalate from D to C (`shared_prefs/** + databases/**`) only if D fails on a real device.
   - Ele.me fallback: escalate from E-min to D (`shared_prefs/**`), then C (`shared_prefs/** + databases/**`) only if needed.

Acceptance:

- JD D profile opens `com.jd.mrd.cater.CaterMainActivity` and shows business text such as `订单` / `店铺`, with no username/password/SMS login terms.
- JD E-min is not accepted because it opened `com.jd.mrd.jingming.login.LoginFusionActivity` with `密码登录` / `验证码登录`.
- Ele.me E-min opens `me.ele.napos.module.main.module.main.activity.HomeTabActivity`, with no username/password/SMS login terms.
- Production export sizes should be close to the verified shapes: JD D about 49 files / 31-38 KB compressed; Ele.me E-min about 27 files / 72 KB compressed.
- No test or restore writes to a stale Xiaomi virtual user or creates nested package directories.
- Server restore validation passed through the real app/backend path: `shops/5/login-state` downloaded 31,599 bytes to Xiaomi `user15`; `shops/6/login-state` downloaded 73,396 bytes to Xiaomi `user2`.
- Final OPPO-to-Xiaomi validation passed on 2026-06-12 after OPPO source upload and Xiaomi target directory cleanup:
  - Meituan `meituan-waimai-cips-f`: OPPO user `3` uploaded `460,451` bytes; Xiaomi user `22` downloaded and restored the CIPS artifact, then launched Meituan without username/password/SMS login.
  - JD `jd-jingming-prefs-d`: OPPO user `15` uploaded `32,367` bytes; Xiaomi user `15` downloaded and restored the D-profile artifact, then launched JD `CaterMainActivity`.
  - Ele.me `ele-napos-prefs-e-min`: OPPO user `2` uploaded `73,814` bytes; Xiaomi user `2` downloaded and restored the E-min artifact, then launched Ele.me `HomeTabActivity`.

## Wave 3: Backend Storage

Store the login-state artifact for each shop/card.

Tasks:

1. Add a blob/object field for the login-state artifact, or an object-storage pointer if blob size limits are risky.
2. Store manifest metadata alongside the artifact.
3. Scope records by shop/card identity, not only by platform package.
4. Reject artifacts whose manifest package/profile does not match the shop platform.
5. Do not use HTTP BODY logging for login-state upload/download. ZIP bodies may contain cookies, tokens, account ids, or other login secrets.

Acceptance:

- Upload and download preserve bytes and hash.
- One user's artifact cannot be fetched by another user.
- Multiple cards for the same package can store independent artifacts.
- Login-state upload/download request logs contain method, URL, and status only; they must not dump multipart ZIP bodies or bearer tokens.

## Wave 4: Restore on New Device

Restore the CIPS-only artifact during the existing shop recovery flow.

Tasks:

1. Resolve target clone binding through Wave 0 scoped mapping.
2. Ensure target virtual user and package install state exist before file release.
3. Clear only the target package directory for that card binding.
4. Release logical `data/` paths into `BEnvironment.getDataDir(packageName, targetUserId)`.
5. Rebind clone auth metadata and launch through the authorized path.
6. Do not let in-memory prepared-user caches bypass login-state restore. If a shop has a server artifact, restore must run before launch even when the local clone binding already exists.

Acceptance:

- OPPO 罗家臭豆腐 CIPS-only artifact restores onto Xiaomi and opens directly without manual login.
- Restore cannot write to a stale virtual user id.
- Restore leaves no nested package directory or source-device user directory.
- Clearing the Xiaomi target package directory and reopening the card re-downloads and restores the server artifact instead of launching an empty generated app state.

## Wave 5: Verified Shop Identity and Shop Info API

Add verified shop-identity extraction for the supported login-state profiles. Shop id and shop name must never be invented by the app or backend; they can only become authoritative after the engine reads them from the target app's data files or platform interface.

Source-of-truth chain:

- Phone clone data is the live source. The server only returns historical shop records and stores verified reports from the main app.
- Pull-to-refresh is app-orchestrated: main app loads history from the server through `/shops/my`, then calls the engine for the selected platform/card bindings, compares the returned `ShopInfo`, and reports verified changes through `/shops/report`.
- Shop-card click is also app-orchestrated: after launch/restore, the main app waits 10 seconds and calls the engine for that card's current `packageName + localVirtualUserId`.
- The engine no longer proactively reports shop identity to the main app and no longer scans identities from clone lifecycle callbacks, resume hooks, timers, or background workers.
- `triggerShopIdExtract(packageName, userId)` returns only the current extraction result from phone clone data. If this specific read cannot find both a real id and a real name, it returns null; it must not reuse stale `ShopInfo` cache as evidence.
- Server history, APK install state, and generated pending card labels are not shop-identity sources.
- Grey logo state is local main-app rendering based on `identityVerified`; the server does not need to return a grey icon.

Tasks:

1. Implement Meituan, JD, and Ele.me shop-info extraction against the same verified contract:
   - id must be a real platform id, not `NEW-*`, `phase13-*`, `-`, generated text, or display fallback.
   - name must come from the same app data/interface extraction pass, not from the card placeholder.
   - extraction failure must leave the backend row as pending/unverified.
2. Normalize shop id/name/platform fields to the same `ShopInfo` contract across platforms.
3. Store/update the backend shop row only through the main app report path after successful app-triggered engine extraction.
4. Allow unverified `/shops/report` calls only for clone/card metadata binding such as `cloneInstanceId`, package, and `localVirtualUserId`; they must not update `shopId`, `shopName`, or `identityVerified`.
5. Prevent manual shop edit APIs from creating or overwriting `shopId`/`shopName`; manual edits may only change operational fields such as auto-renew.
6. Add explicit visual state in the app:
   - pending or unverified shop cards show platform logo in grey.
   - verified post-login shop cards show the normal color platform logo.
   - unverified cards display `店铺ID: -` instead of placeholder/generated ids.

Acceptance:

- Meituan, JD, and Ele.me shop cards can populate stable shop id/name after login only when the engine extracts both fields.
- Backend `/shops/report` rejects or ignores reports without a real id and a non-placeholder name.
- Backend `PUT /shops/{id}` cannot fabricate or overwrite shop identity fields.
- New/pending cards remain grey until verified extraction succeeds; after verified report and list refresh, the same card logo turns color.
- Pull-to-refresh and click+10s sync are the only shop-identity refresh triggers; engine-side lifecycle auto-scan and platform-wide batch refresh APIs are absent from the app flow.
- A cleaned or logged-out phone directory returns null from `triggerShopIdExtract`; stale engine cache does not turn a card color or create a server report.
- Existing login-state restore behavior is unchanged.

## Wave 6: Additional Platform Login-State Profiles

Add login-state sync support for the first logged-in OPPO shop card on these four platforms:

```text
淘宝闪购零售版
美团经营宝
携程商家版
抖音来客
```

Validation contract:

- Use OPPO as the only source device and Xiaomi as the restore target.
- Resolve source and target by the actual platform package, system shop id, `cloneInstanceId`, and active `localVirtualUserId`; platform shop ids are evidence only and must not participate in upload, download, restore, or card binding.
- Test packages from the smallest file profile upward: `E -> D -> C -> B -> A`. Stop as soon as the restored Xiaomi card no longer reaches username/password/SMS login.
- Treat graphic CAPTCHA, slider, device-risk, or platform security verification as login-state success. Only username/password/SMS/manual account login entry is failure.
- Do not generalize Meituan Waimai CIPS rules to other packages. Each package must get its own verified profile and backend whitelist entry.
- Keep every accepted upload under the login-state artifact size limit. If a candidate exceeds the limit, reduce the allowlist or record the platform as blocked instead of uploading a full clone export.
- Clear only the active Xiaomi card-bound package directory before each restore attempt; never write OPPO source-user paths or nested package directories.
- Run the four platform investigations in parallel where device state allows, but each platform's own escalation remains ordered from E upward.

Tasks:

1. Identify the exact OPPO package name, source virtual user id, system shop id, clone id, and first-card login evidence for each of the four platforms.
2. Identify or create the matching Xiaomi card binding for each platform and verify the target package version is compatible with OPPO.
3. Generate E candidates using the smallest platform-specific login/account/device/shop files found in `shared_prefs`, small auth files, and platform-specific state directories.
4. If E fails, escalate to D, C, B, then A using the smallest broader sets that explain the missing login state. Do not skip directly to full package data.
5. Add engine-side profile ids only for the smallest accepted candidate per platform.
6. Add backend profile allowlist entries for the new package/profile pairs.
7. Add or update tests so package/profile mismatches are rejected and accepted platform profiles can be uploaded by system shop id.
8. Record final source/target mappings, candidate sizes, selected profile ids, and Xiaomi launch evidence in `13-RESEARCH.md` and `13-STANDARD.md`.

Acceptance:

- Each supported platform has a real-device accepted minimum profile, selected from E first and escalated only as needed.
- Xiaomi restore launches the corresponding platform app into an already-authenticated or security-verification state, not username/password/SMS login.
- Backend upload/download continues to use our system shop id only; platform shop id never controls artifact storage.
- The restored target directory is the Xiaomi active card binding and contains no source-device user path or nested package directory.
- Platforms that cannot pass within the size limit are explicitly documented with the smallest failing candidate and are not marked supported.

Wave 6 closeout on 2026-06-13:

| Platform | Package | Accepted profile | Support status |
| --- | --- | --- | --- |
| 淘宝闪购零售版 | `com.baidu.lbs.xinlingshou` | `ele-retail-prefs-e-min` | Completed. |
| 美团经营宝 | `com.sankuai.meituan.merchant` | `meituan-merchant-cips-e-min` | Completed. |
| 携程商家版 | `com.Hotel.EBooking` | `ctrip-ebooking-prefs-mmkv-e-min` | Completed. |
| 抖音来客 | `com.bytedance.ls.merchant` | none accepted | Legacy item. Do not mark supported. |

### Legacy Item: Douyin Laike Clone Login-State Restore

Target clone investigated:

- Clone instance id: `CLN1-15200837196-com.bytedance.ls.merchant-N1-U25-R672c8b60`.
- Server user id: `10`.
- Xiaomi active virtual user id: `25`.
- Xiaomi card root: `blackbox/accounts/10/cards/CLN1-15200837196-com.bytedance.ls.merchant-N1-U25-R672c8b60`.

Exploration already performed:

- Started from the minimum profile direction and escalated through broader Douyin Laike candidates. E-min and expanded variants failed. D/C candidates could reach partial token/account activity but still ended in platform logout or username/SMS login.
- Tested app-private A/full package data from the earlier OPPO export; it briefly entered Douyin Laike `MainActivity` but business requests returned authentication failure and the app navigated to the phone login page.
- Re-exported the complete OPPO card directory on 2026-06-13, including scoped `auth/`, `user/25/...`, and `user_de/25/...`. The tar was `/tmp/dylk-oppo-card-full-20260613-203818.tar`, about 875 MB on host; after releasing to Xiaomi the card root was about 929 MB and contained 9,588 files.
- Before the final Xiaomi test, killed the Douyin Laike, main app, and engine processes; cleared the target Xiaomi card directory and scoped external directories; extracted the OPPO card tar directly under `blackbox/accounts/10/cards`; then direct-launched through `ShortcutActivity --es pkg com.bytedance.ls.merchant --ei userId 25`.

Final observed result:

- UI dump: `/tmp/dylk-20260613-204323-fresh-card-full.xml`.
- Logcat evidence: `/tmp/dylk-20260613-204323-fresh-card-full.log`.
- Screenshot: `/tmp/dylk-20260613-204323-fresh-card-full.png`.
- Result summary: `LOGIN_TERMS=true`, `HOME_TERMS=false`; visible page was the Douyin Laike phone login screen.
- Logs showed `SplashActivity` -> `MainActivity` -> duplicated `LoginActivity`, repeated `merchantAccountModel is null`, `updateActiveAccount null`, `passport/user/logout`, and business API failures with `4000100 / 用户鉴权失败`.

Current conclusion:

- Complete clone card data is still insufficient for this Douyin Laike account to survive cross-device restore.
- The remaining dependency is likely outside the exportable clone card directory or is server/device bound, for example Android Keystore/TEE material, ByteDance device/TicketGuard/MSSDK state, or server-side invalidation after device migration.
- Do not enable Douyin Laike as a supported login-state sync platform until a future phase finds the missing device-bound state and passes the same Xiaomi restore test.

## Verification

- Unit tests for scoped clone mapping and stale restore rejection.
- Unit tests for CIPS-only allowlist and exclude rules.
- ADB verification on Xiaomi using the actual card-bound virtual user directory.
- Regression check that Phase 9 clone authorization still blocks invalid clone ids and still launches valid cards.
- Backend `ShopControllerTest` covers upload/download and manifest validation.
- Android debug build verifies app, engine, and AIDL compatibility.

Latest device verification summary:

| Check | Evidence |
| --- | --- |
| OPPO source upload | `shops/4/login-state` manifest `localVirtualUserId=3`, `460,451` bytes; `shops/5/login-state` manifest `localVirtualUserId=15`, `32,367` bytes; `shops/6/login-state` manifest `localVirtualUserId=2`, `73,814` bytes. |
| Xiaomi clean restore | Target package dirs were removed before restore. Logs showed `restoreLoginState downloaded` and `restoreLoginState restored` for shops `4`, `5`, and `6`. |
| Target launch | Meituan reached `com.sankuai.meituan.meituanwaimaibusiness.modules.main.MainActivity`; JD reached `com.jd.mrd.cater.CaterMainActivity`; Ele.me reached `me.ele.napos.module.main.module.main.activity.HomeTabActivity`. |
| File-size policy | All production artifacts stayed below the 2 MB backend limit; no full 170 MB+ engine export is required. |
| Shop identity pull-to-refresh | Xiaomi logs showed `GET /api/shops/my` before engine extraction; Meituan read CIPS `PoiInfo/kv`, JD read `JingmingAndroidClient.xml`, and Ele.me read `NAPOS_LTRACKER_SP.xml`. Only Ele.me posted `/shops/report` because it changed from pending to `1184657317 / luojia6688`. |
| Shop-card click + 10s | Clicking JD shop `5` launched `com.jd.mrd.cater.CaterMainActivity`; about 10 seconds later the main app called `triggerShopIdExtract` for `com.jd.mrd.jingming userId=15` and got `found=true`. No lifecycle-driven engine report was observed. |
