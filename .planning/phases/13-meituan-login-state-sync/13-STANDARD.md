# Phase 13 Standard: Meituan login-state export package

Status: accepted from OPPO to Xiaomi ADB validation on 2026-06-11 and real app/backend validation on 2026-06-12.

This document defines the data package that the engine should export and upload for Meituan Waimai merchant login-state sync. The standard is based on the successful CIPS-only candidate from 罗家臭豆腐 OPPO user 3 to the actual Xiaomi shop clone user 22 validation.

The same restore contract now also defines the verified JD and Ele.me profiles in this phase. CIPS remains Meituan-specific; JD and Ele.me use their own shared-preference profiles.

## Scope

Target package:

```text
com.sankuai.meituan.meituanwaimaibusiness
```

The export is package-scoped and virtual-user-scoped. Production zips must not hardcode a source or target virtual user id in entry names. The engine should write logical roots and restore them through scoped `BEnvironment` directory resolution:

| Logical root | Source | Restore target |
| --- | --- | --- |
| `data/` | Scoped data dir for source `serverUserId + cloneInstanceId + userId + packageName` | Scoped data dir for target `serverUserId + cloneInstanceId + userId + packageName` |
| `de-data/` | Scoped DE data dir for source binding | Scoped DE data dir for target binding |
| `external-data/` | Scoped external data dir for source binding | Scoped external data dir for target binding |

The ADB test zips used Xiaomi-specific paths such as `blackbox/data/user/11/...` only for manual validation. Do not keep that path format in production.

Phase 13 restore must run on top of the scoped clone directory model introduced in Wave 0:

```text
accounts/<serverUserId>/cards/<cloneInstanceId>/user/<localVirtualUserId>/<packageName>/
```

Use the equivalent scoped roots for DE data, external data, profiles, and clone auth metadata when those data classes are restored or migrated. Historical flat directories such as `blackbox/data/user/<localVirtualUserId>/<packageName>` must be migrated by the engine upgrade migration Activity before login-state sync can restore new artifacts.

## Default Include Rules

Apply these rules relative to `data/`.

### CIPS State Only

The default production artifact for `com.sankuai.meituan.meituanwaimaibusiness` is the F/CIPS-only profile. It contains only selected CIPS state records from:

```text
files/cips/**
cache/cips/**
```

Include files under those roots when the path is a small state/KV record and the lower-case relative path contains one of:

```text
/kv
/obj
oneid
login
account
user
wm
wmb
waimai
shark
dx_login
at_me_info
xm_
uuid
device
jsbridge_storage
horn_config
pre_network_cache
msc_init_cache
```

Known important CIPS families seen in the successful package include:

```text
files/cips/common/epassport_common/**
files/cips/common/homepage_passport/**
files/cips/common/com.sankuai.meituan.waimaib.account.poi.bean.MineVo/**
files/cips/common/com.sankuai.meituanwaimaibusiness.db.green.User/**
files/cips/common/com.sankuai.meituanwaimaibusiness.db.green.Poi/**
files/cips/common/xm_<account_id>_4/**
files/cips/common/xm_AT_ME_INFO_<account_id>/**
files/cips/common/waimai_platform/**
files/cips/common/wmb_router_replace/**
files/cips/common/wmb_store_order_router_replace/**
files/cips/common/wmb_store_im_router_replace/**
files/cips/common/wmb_store_operating_router_replace/**
files/cips/common/wmb_store_quickbuy_router_replace/**
files/cips/common/jsbridge_storage/**
cache/cips/common/jsbridge_storage/**
```

The exact account ids in `xm_<account_id>_4` and `xm_AT_ME_INFO_<account_id>` are dynamic. Match by prefix/pattern, not by fixed numeric id.

### Fallback E Profile

Keep the broader E profile as a manual fallback or diagnostic profile only. Do not use it as the default upload artifact while CIPS-only remains valid.

The E fallback may include:

```text
shared_prefs/**
databases/**
files/horn/**
files/im/**
files/libCachedImageData.db
files/qqdybiz
app_webview*/Default/Cookies*
app_webview*/Default/IndexedDB/**
app_webview*/Default/Local Storage/**
app_webview*/Default/Session Storage/**
app_webview*/Default/Web Data*
app_webview*/Default/databases/**
app_webview*/pref_store
app_webview*/variations_seed
app_webview*/variations_stamp
app_webview*/webview_data.lock
```

When using E fallback, include database files and journals except `aidata*`. Required examples from the successful E package:

```text
databases/1793373326_message_db.db
databases/1793373326_message_db.db-journal
databases/1793373326_imkit_personal_db.db
databases/1793373326_im_extra.db
databases/3054919255_message_db.db
databases/3054919255_message_db.db-journal
databases/3054919255_imkit_personal_db.db
databases/3054919255_im_extra.db
databases/waimaie
databases/kitefly.db
databases/com.sankuai.meituan.meituanwaimaibusinessMTLocationDb.db
```

`files/im/**` may be absent on some accounts; include it when present.

For E fallback WebView state, exclude:

```text
app_webview*/BrowserMetrics*
app_webview*/*.pma
```

## Exclude Rules

Exclude these paths even if they are present in the full clone export:

```text
shared_prefs/**
databases/**
databases/aidata*
app_webview*/**
files/horn/**
files/im/**
app_flutter/**
code_cache/**
cache/**
files/dynlib/**
app_so_lib/**
```

`cache/cips/**` and `files/cips/**` are the only default exceptions. The broader paths above are allowed only when deliberately using the E fallback profile.

Exclude CIPS resources:

```text
files/cips/**/assets/**
cache/cips/**/assets/**
files/cips/**/mrn_dio/**
files/cips/**/ddload/assets/**
files/cips/**/codecache/**
files/cips/**/mmpackage/**
```

Exclude files with these extensions under CIPS:

```text
*.dio
*.zip
*.png
*.jpg
*.jpeg
*.webp
*.so
*.chs
*.js
```

Do not include `external-data/` by default for Meituan login-state sync. The successful E package for 罗家臭豆腐 contained no external-data entries. If future tests require external files, add a separate allowlist and keep `external-data/cache/**` and `external-data/**/cips/**` excluded.

## Platform Scope

CIPS is not a universal Android login-state mechanism. Treat the CIPS rules above as the verified Meituan Waimai merchant profile only.

- Other Meituan-family apps may have CIPS or similar component state, but each package needs its own validation.
- Ele.me, JD, Douyin, and other non-Meituan platforms must get separate login-state profiles.
- Reuse the ADB validation method across platforms, not the Meituan CIPS file list.

## JD Profile

Target package:

```text
com.jd.mrd.jingming
```

Verified source and target:

| Field | Value |
| --- | --- |
| OPPO source user | `28` |
| Source evidence | `storeId=14395758`, `storeName=罗家臭豆腐(东瓜山店)` |
| Xiaomi target user | `15` |
| Version | `9.58.1 / 905810` |

Default stable profile id:

```text
jd-jingming-prefs-d
```

Historical alias accepted by backend/engine for compatibility:

```text
phase13-jd-jingming-prefs-d-20260611
```

Include only:

```text
data/shared_prefs/**
```

Verified candidate shape:

| Field | Value |
| --- | ---: |
| Files | `49` |
| Raw bytes | `78,941` |
| Zip bytes | `38,570` |

Xiaomi result: opening the restored package entered `com.jd.mrd.cater.CaterMainActivity` and showed business text such as `订单`, `预订单`, and `店铺`; it did not open username/password/SMS login.

Do not include by default:

```text
data/databases/**
data/files/**
data/cache/**
data/app_webview*/**
data/app_reactnative*/**
data/app_extracted_dongCore/**
data/code_cache/**
external-data/**
```

Rejected smaller E-min attempt:

```text
shared_prefs/AppPrefs.xml
shared_prefs/JingmingAndroidClient.xml
shared_prefs/JdAndroidUUID.xml
shared_prefs/app_session_monitor.xml
shared_prefs/c07fc52b4382f1fa.xml
shared_prefs/jd_ma_sdk.xml
shared_prefs/jma_sp_file.xml
shared_prefs/jspush_data.xml
shared_prefs/verify_sp_file.xml
shared_prefs/dong_core_data_sp.xml
```

That E-min package opened `com.jd.mrd.jingming.login.LoginFusionActivity` with `密码登录` / `验证码登录`, so it is not accepted.

Fallback: only if D opens username/password/SMS login on a future real-device test, escalate to C by adding `data/databases/**`. Do not jump directly to full package export.

## Ele.me Profile

Target package:

```text
me.ele.napos
```

Verified source and target:

| Field | Value |
| --- | --- |
| OPPO source user | `2` |
| Source evidence | `NAPOS_LTRACKER_SP.xml` current `shopId=1184657317`, plus `app_sp_config.xml` `DD_SHOP` object `id=1184657317`, `name=罗家臭豆腐·长沙一绝(东瓜山店)` |
| Xiaomi target user | `2` |
| Version | `14.4.4 / 140404` |

Default stable profile id:

```text
ele-napos-prefs-e-min
```

Historical alias accepted by backend/engine for compatibility:

```text
phase13-ele-napos-prefs-e-min-20260611
```

Include these fixed files under `data/shared_prefs/` when present:

```text
ACCS_BIND_default.xml
ACCS_SDK.xml
AGOO_BIND.xml
Agoo_AppStore.xml
AltriaXDevice.xml
Alvin2.xml
MtopConfigStore.xml
NAPOS_LTRACKER_SP.xml
SharedPreferenceAdiu.xml
UTCommon.xml
app_sp_config.xml
me.ele.foundation.xml
me.ele.napos_preferences.xml
me_ele_napos.xml
sgPrefs.xml
sp_eleme_foundation.xml
sp_eleme_needle_unsafe.xml
vkeyid_profiles_v3.xml
vkeyid_profiles_v4.xml
vkeyid_settings.xml
```

Also include dynamic user-scoped files:

```text
user_*_sp_config_.xml
user_*_rest_*_sp_config.xml
```

The `user_<id>_...` files contain account-specific ids. In production, discover and include matching files that belong to the current source login instead of hardcoding `5329558971`.

Verified candidate shape:

| Field | Value |
| --- | ---: |
| Files | `27` |
| Raw bytes | `370,825` |
| Zip bytes | `73,396` |

Xiaomi result: opening the restored package entered `me.ele.napos.module.main.module.main.activity.HomeTabActivity`; it did not open username/password/SMS login.

Do not include by default:

```text
data/databases/**
data/files/**
data/cache/**
data/app_u4_webview/**
data/app_webview*/**
data/app_SGLib/**
data/app_u4sdk/**
data/app_zcache/**
data/app_res_download/**
data/app_res_preset/**
external-data/**
```

Fallback order: if E-min opens username/password/SMS login, escalate to D by including all `data/shared_prefs/**`. If D still fails, escalate to C by adding `data/databases/**`. Do not jump directly to full package export.

## Package Manifest

Every exported login-state zip should include a manifest JSON with at least:

```json
{
  "schemaVersion": 1,
  "packageName": "com.sankuai.meituan.meituanwaimaibusiness",
  "platform": "meituan",
  "sourceUserId": 3,
  "createdAt": "2026-06-11T18:30:25+08:00",
  "includeRules": "phase13-meituan-waimai-cips-only-f-20260611",
  "rawBytes": 10842112,
  "zipBytes": 548271,
  "fileCount": 475,
  "sha256": "<zip sha256>"
}
```

Do not write token, cookie, or session values into logs, filenames, or manifest fields.

HTTP logging must not dump login-state request or response bodies. Login-state ZIP files are authentication material and can include cookies, token-like state, account ids, and device state. The app may log method, URL, status, profile id, byte count, and hash prefix for debugging, but not the binary payload or bearer token.

## Restore Rules

Before restore:

1. Force-stop the engine or target virtual process.
2. Ensure the engine data migration Activity has completed successfully after install/upgrade. Do not restore into the historical flat directory layout.
3. Resolve the target shop card's current scoped binding: `serverUserId + cloneInstanceId + packageName + localVirtualUserId`. Do not assume the OPPO source user id or an old Xiaomi test user id.
4. Ensure the target package is installed for that scoped virtual user.
5. Remove the target package's existing app data for that scoped virtual user, or restore into an empty target user.
6. Ignore source-device `localVirtualUserId` when choosing the restore destination. The source user is evidence only; the destination is the target card's active scoped binding.
7. If the local app has cached a prepared user id for the card, still run restore before launch when the backend says `hasLoginState=true`.

Restore mapping:

```text
data/<rel>          -> scoped BEnvironment data dir for serverUserId/cloneInstanceId/targetUserId/packageName/<rel>
de-data/<rel>       -> scoped BEnvironment DE data dir for serverUserId/cloneInstanceId/targetUserId/packageName/<rel>
external-data/<rel> -> scoped BEnvironment external data dir for serverUserId/cloneInstanceId/targetUserId/packageName/<rel>
```

After restore:

1. Set directories to owner-readable/writable/executable.
2. Set files to owner-readable/writable.
3. Rebind clone auth metadata for the target shop as Phase 9 already does.
4. Launch through authorized clone launch, not direct debug launch.

Implementation guardrail: local prepared-user caches such as `preparedShopUsers` may speed up launch only after the login-state key for `serverUserId + packageName + cloneInstanceId + targetUserId` has been restored in the current app process. They must not skip restore after repair-shop, directory cleanup, app restart, or backend artifact updates.

## Acceptance Check

For Meituan login-state sync to be accepted:

- Full package export is not used as the upload artifact.
- Upload artifact follows the F/CIPS-only allowlist above.
- Artifact size is expected to be around 0.45-0.55 MB compressed for the tested 罗家臭豆腐 case. File count may drift with CIPS state, but should stay in the same small-package family rather than becoming a full cache export.
- Restoring the artifact onto a clean Xiaomi virtual user can open the Meituan merchant shop without manual SMS/password login.
- Restore writes directly into the package directory for the shop card's current virtual user, with no nested package directory or source-user path left behind.
- A-D are not accepted because they returned to login/register entry in real-device testing; E is accepted only as fallback because CIPS-only has now passed on the actual shop directory.

For JD and Ele.me login-state sync to be accepted:

- Start from E, then D, then C. Do not jump to larger files until the current candidate reaches username/password/SMS login.
- JD E-min is rejected; JD D is accepted because it reaches `com.jd.mrd.cater.CaterMainActivity` and business order UI.
- Ele.me E-min is accepted because it reaches `me.ele.napos.module.main.module.main.activity.HomeTabActivity`.
- Both profiles restore through server blobs into the active Xiaomi card directory and do not write source-user paths.

## Verified Shop Identity Standard

Shop identity is a verified engine output, not user-entered or generated application data.

The phone clone data is the source of truth for current shop identity. The
server stores historical shop records and accepts reports from the main app; it
does not discover current platform shop identity on its own and does not return
alternate grey icons. APK install state and backend records are runtime inputs
and history baselines only; they are not authoritative shop-identity sources.

Required sync ownership:

1. On shop-card pull-to-refresh, the main app first requests the user's
   historical shop records from the server through `/shops/my`. This response
   is only the reconciliation baseline.
2. After that server response is applied, the main app calls the engine for the
   current selected platform/card bindings. The engine reads local clone files
   or uses the existing shop login state/platform interface for the specific
   `packageName + localVirtualUserId`, then returns `ShopInfo` to the main app.
3. `triggerShopIdExtract(packageName, userId)` means "read the current phone
   data now". If the engine cannot read both a verified id and a verified name
   in this call, it returns `null`. It must not fall back to stale stored
   `ShopInfo` cache as a successful current probe.
4. The main app compares server history with engine output. If a real
   `shopId + shopName` update is found, the main app reports it to the server
   through `/shops/report`.
5. On shop-card click, the main app launches/restores the card as usual, then
   waits 10 seconds and calls the engine for that card's current
   `packageName + localVirtualUserId`. Any verified change is again reported by
   the main app.
6. The engine must not proactively scan shop identities from clone lifecycle
   callbacks, timers, background workers, or app resume hooks. Its role is a
   passive local reader behind explicit main-app AIDL calls.
7. The main app renders pending/unverified cards with a local grey treatment.
   The backend only returns identity fields such as `identityVerified`; it does
   not need to provide a grey logo asset.

Rules:

1. `shopId` and `shopName` may be stored as authoritative only after the engine extracts both values from the target platform app's data files or platform interface.
2. The app and backend may create pending card placeholders such as `NEW-*` ids and `新增店铺-[n]` names, but these placeholders are never accepted as real shop identity. Temporary research labels such as `phase13-*` are also invalid.
3. Client fallback names such as `${platform}-${shopId}`, `User[...]`, `未知...`, manually entered names, or generated readable labels are not valid identity evidence.
4. `/shops/report` is the only write path that can convert a pending card to a verified shop identity. Ordinary `PUT /shops/{id}` edits must not overwrite `shopId` or `shopName`.
5. A verified report requires:
   - nonblank `packageName`;
   - clone ownership validation when `cloneInstanceId` is already assigned;
   - real `shopId`, not `-`, not `NEW-*`, and not `phase13-*`;
   - nonblank `shopName`, not a pending placeholder, research label, or generated fallback.
6. An unverified `/shops/report` request may bind or repair clone metadata such as `cloneInstanceId`, package, and `localVirtualUserId`, but it must not replace `shopId`, `shopName`, or set `identityVerified=true`.
7. The app must keep unverified cards visually grey. The logo becomes color only after the backend returns `identityVerified=true` for the card and the main app locally chooses the color treatment.
8. If extraction finds only an id or only a name, the engine must not mark the shop as verified. The app keeps the card pending until the next user-triggered pull-to-refresh or the next shop-click 10-second sync.

Current platform identity evidence:

| Platform | Package | Accepted identity source |
| --- | --- | --- |
| Meituan Waimai merchant | `com.sankuai.meituan.meituanwaimaibusiness` | Prefer CIPS files `files/cips/common/com.sankuai.meituan.meituanwaimaibusiness.modules.main.request.model.PoiInfo/kv`, `files/cips/common/com.sankuai.meituanwaimaibusiness.db.green.Poi/kv`, and `files/cips/common/com.sankuai.meituan.retail.poi.RetailPoiInfo/kv`. Accept active POI JSON only when it contains a real `wmPoiId`/`wmPoiIdStr`/`poiId` plus `poiName`/`poi_name`/`wmPoiName`. |
| JD Jingming | `com.jd.mrd.jingming` | `shared_prefs/JingmingAndroidClient.xml` containing both `storeId=14395758` and `storeName=罗家臭豆腐(东瓜山店)`, or an equivalent small shared-prefs file with the same verified id/name pair. GeTui alias id alone is not accepted as verified identity. |
| Ele.me Napos | `me.ele.napos` | `shared_prefs/NAPOS_LTRACKER_SP.xml` provides the current numeric `shopId`. The verified shop name must come from a shop object with the same id, such as `shared_prefs/app_sp_config.xml` `DD_SHOP` containing `id=1184657317` and `name=罗家臭豆腐·长沙一绝(东瓜山店)`, or an equivalent `user_*_rest_<shopId>_sp_config.xml` shop/store/restaurant object. `user_name`, `username`, and `switch_login_user_info.shopName` are account/login fields and must not be accepted as `shopName`. |

## Final Real App/Backend Validation

Final validation was rerun on 2026-06-12 with the implemented app/backend flow, not manual ADB package release:

1. Install `1.2.13-phase13` app and engine on Xiaomi and OPPO.
2. Trigger the engine migration Activity so historical flat data is moved under `accounts/<serverUserId>/cards/<cloneInstanceId>/...`.
3. Open each OPPO 罗家臭豆腐 card once to make the app export and upload the platform-specific login-state profile.
4. Delete only the target Xiaomi scoped package directory for the same card.
5. Cold-start the Xiaomi app as account `二公子` / `152****7196`, click the card, and require a server `GET /shops/{id}/login-state`, `restoreLoginState restored`, and target app launch.

Accepted source uploads:

| Shop id | Platform | Source device/user | Stable profile | Uploaded bytes | Manifest local user |
| --- | --- | --- | --- | ---: | ---: |
| `4` | Meituan | OPPO `user3` | `meituan-waimai-cips-f` | `460,451` | `3` |
| `5` | JD | OPPO `user15` | `jd-jingming-prefs-d` | `32,367` | `15` |
| `6` | Ele.me | OPPO `user2` | `ele-napos-prefs-e-min` | `73,814` | `2` |

Accepted Xiaomi clean restores:

| Shop id | Target card binding | Downloaded bytes | Restore result | Launch result |
| --- | --- | ---: | --- | --- |
| `4` | `accounts/10/cards/clone-0c0e.../user/22/com.sankuai.meituan.meituanwaimaibusiness` | `460,451` first clean run; `461,299` after refreshed upload | `restoreLoginState restored` | Meituan `MainActivity` / splash path, no username/password/SMS login. |
| `5` | `accounts/10/cards/CLN1-...jingming.../user/15/com.jd.mrd.jingming` | `32,367` | `restoreLoginState restored` | JD `com.jd.mrd.cater.CaterMainActivity`. |
| `6` | `accounts/10/cards/CLN1-...napos.../user/2/me.ele.napos` | `73,814` | `restoreLoginState restored` | Ele.me `me.ele.napos.module.main.module.main.activity.HomeTabActivity`. |

This final validation confirms that the implementation uses the smallest accepted platform profile in the required order: Meituan CIPS/F, JD D because E failed, and Ele.me E-min because E passed. It also confirms that source virtual user ids are manifest evidence only; restore always targets the active Xiaomi card binding.

Verified shop identity flow was also rerun on Xiaomi after installing
`1.2.13-phase13` at `2026-06-12 04:19`:

| Trigger | Platform/card | Evidence |
| --- | --- | --- |
| Pull-to-refresh | Ele.me shop `6`, Xiaomi user `2` | Superseded evidence: the first extractor version logged `shopName=luojia6688` from account fields. This is invalid because Xiaomi `app_sp_config.xml` also contains `DD_SHOP.id=1184657317` and `DD_SHOP.name=罗家臭豆腐·长沙一绝(东瓜山店)`, while `switch_login_user_info.shopName`/`username` are login-account fields. The accepted extractor source is now the same-id `DD_SHOP`/shop object name only. |
| Pull-to-refresh | Meituan shop `4`, Xiaomi user `22` | Main app first logged `GET /api/shops/my`, then engine logged `MeituanWaimaiShopIdExtractor: Extracted verified shop identity from CIPS ...PoiInfo/kv`; no report was needed because server history already matched `24059918 / 罗家臭豆腐（小吃·炸串·万家丽宇宙中心店）`. |
| Pull-to-refresh | JD shop `5`, Xiaomi user `15` | Main app first logged `GET /api/shops/my`, then engine logged `JDShopIdExtractor: Extracted shopId=14395758, shopName=罗家臭豆腐(东瓜山店) from JingmingAndroidClient.xml`; no report was needed because server history already matched. |
| Shop-card click + 10 seconds | JD shop `5`, Xiaomi user `15` | After card launch reached `com.jd.mrd.cater.CaterMainActivity`, the main app timer called the engine and `triggerShopIdExtract completed ... found=true`. The engine did not proactively report or scan from lifecycle callbacks. |

The same UI dump showed unverified pending cards as locally grey/disabled with
`店铺ID: -` and content description `未登录`, while verified cards used the
normal color treatment and content description `已登录`. The backend did not
return a grey icon asset.
