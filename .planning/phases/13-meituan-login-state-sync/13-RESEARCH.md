# Phase 13 Research: Meituan login-state sync

Date: 2026-06-11

## Goal

Use ADB first to determine which Meituan Waimai merchant clone data is sufficient to move an already logged-in shop from an OPPO device to a Xiaomi device, before writing backup, upload, or restore code.

Target shop for this round: 罗家臭豆腐.

## Devices

- OPPO: `55J7JJWKTWKNHYZL`, model `PECM30`.
- Xiaomi: `3ca26684`, model `MIX_2S`.
- Engine package: `com.zhirang.zhanghaoguanjia.engine`.
- Meituan merchant package: `com.sankuai.meituan.meituanwaimaibusiness`.

## OPPO Source

The OPPO engine was originally installed as `1.2.12-release`, which is not debuggable, so `run-as com.zhirang.zhanghaoguanjia.engine` failed. The OPPO release engine and the Xiaomi debug engine used the same Android debug signing certificate, so the OPPO engine was temporarily replaced with the same-signed debug build to enable `run-as` and the clone export Activity without clearing private app data.

The active 罗家臭豆腐 data was found under virtual user `3`.

Evidence:

- `blackbox/data/user/3/com.sankuai.meituan.meituanwaimaibusiness` exists on OPPO and is about 367 MB.
- Local database scan found 罗家臭豆腐 records in user 3:
  - `wm_poi_id=24059918`, `poi_name=罗家臭豆腐（小吃·炸串·万家丽宇宙中心店）`
  - `wm_poi_id=8419595`, `poi_name=罗家臭豆腐(大学城店)`
- Direct-launching OPPO user 3 opened Meituan merchant `MainActivity` and showed the order page.

Full package-scoped export from OPPO user 3:

| Item | Value |
| --- | ---: |
| Export time | `2026-06-11 16:45:01` |
| Zip file | `tmp/phase13-oppo-live/clone_data_com.sankuai.meituan.meituanwaimaibusiness_u3_20260611_164439.zip` |
| Zip bytes | `125,950,070` |
| Raw app data bytes | `371,497,662` |
| Raw DE data bytes | `128` |
| Raw external data bytes | `1,230,188` |
| Raw total bytes | `372,727,978` |

This full export is still too large for the intended server path, so it was only used as the source for smaller candidates.

## Candidate Results

All candidate zips were generated from the OPPO user 3 export and remapped into Xiaomi virtual user `11` for ADB validation. Before every candidate, Xiaomi user 11 package data was cleared.

| Candidate | Included high-level data | Files | Raw bytes | Zip bytes | Xiaomi result |
| --- | --- | ---: | ---: | ---: | --- |
| A | `shared_prefs/**` only | 25 | 36,983 | 18,462 | Failed. Opened login/register entry. |
| B | A + non-message core DBs, excluding `aidata*` | 63 | 569,463 | 51,289 | Failed. Opened login/register entry. |
| C | B + IM DBs, `files/im/**`, `files/horn/**`, selected small files | 1,018 | 11,179,011 | 1,475,436 | Failed. Opened login/register entry. |
| D | C + WebView cookies/local storage/IndexedDB and other small non-CIPS files | 1,064 | 11,520,001 | 1,600,666 | Failed on rerun. Opened login/register entry. |
| E | D + selected CIPS KV/state files | 1,459 | 18,919,200 | 2,108,466 | Passed. User entered 罗家臭豆腐 shop on Xiaomi. |
| F | Selected `cache/cips/**` and `files/cips/**` state only | 396 in the first hand-built candidate; 475 in the engine-generated upload artifact | about 10,588 KB on Xiaomi ext4 for the first hand-built candidate | 548,271 hand-built; 451,209 engine-generated source upload | Passed on the actual 罗家臭豆腐 Xiaomi clone directory. |

Important interpretation:

- A/B/C prove that ordinary preferences, SQLite state, IM DBs, and horn config are not enough.
- D proves that adding WebView local state is still not enough.
- E was the first successful candidate in this test round and proved that the OPPO source data plus Xiaomi restore path can carry a live Meituan login state.
- F proves that the current minimum accepted Meituan Waimai merchant login-state package is selected CIPS state only. The effective login/account/device/shop state carriers are in `cache/cips/**` and `files/cips/**`; E remains useful as a fallback and diagnostic package, not as the default upload baseline.

## Clean Rerun

After the first E success, the Xiaomi test target was cleaned and E was imported again to rule out residue from previous runs.

Command effect:

- Force-stopped `com.zhirang.zhanghaoguanjia.engine`.
- Removed Xiaomi `user11` data for `com.sankuai.meituan.meituanwaimaibusiness` under `blackbox/data/user/11`, `blackbox/data/user_de/11`, and external virtual storage.
- Confirmed `CLEAN_OK`.
- Re-imported `E-with-cips-kv.zip`.

Clean rerun import result:

| Field | Value |
| --- | ---: |
| Imported data size on Xiaomi user 11 | `30008 KB` |
| Imported file count | `1459` |
| Imported top-level dir count at max depth 2 | `15` |
| Initial launch focus | `com.sankuai.meituan.meituanwaimaibusiness.modules.main.MainActivity` |
| Login/register page shown | No |
| Screenshot evidence | `tmp/phase13-xiaomi-results/E-with-cips-kv-clean-rerun-after-enter.png` |

The clean rerun confirms E does not depend on leftover Xiaomi user 11 data from A-D tests.

## Actual Shop Directory Validation

After the Xiaomi host Meituan app was upgraded to the same version as OPPO, both devices reported:

| Device | versionCode | versionName |
| --- | ---: | --- |
| OPPO | `700370004` | `7.37.0.4` |
| Xiaomi | `700370004` | `7.37.0.4` |

The failed retry was not caused by a Meituan APK version mismatch. The real issue was target directory selection: the 罗家臭豆腐 shop card on Xiaomi was bound to `localVirtualUserId=22`, while earlier manual retries had been written to test users such as `3` or `11`.

The correct Xiaomi target path for this card was:

```text
blackbox/data/user/22/com.sankuai.meituan.meituanwaimaibusiness
```

When the fresh OPPO-derived E package was rebuilt for `user22` and released directly into that package directory, the user confirmed it could log in. The release was checked to avoid:

- `virtual-data-user-3` source paths
- `blackbox/data/user/3` or `blackbox/data/user/11` target paths
- nested `com.sankuai.meituan.meituanwaimaibusiness/com.sankuai.meituan.meituanwaimaibusiness`
- nested `blackbox` directories under the package directory

## CIPS-only Validation

After E passed on the correct `user22` shop directory, a fresh CIPS-only package was generated from the latest OPPO full export using the F allowlist. It contained only:

```text
cache/cips/**
files/cips/**
```

Release details:

| Field | Value |
| --- | ---: |
| Target path | `blackbox/data/user/22/com.sankuai.meituan.meituanwaimaibusiness` |
| Zip file | `tmp/phase13-login-candidates-oppo-live/F-cips-only-delta-u22-fresh.zip` |
| Zip bytes | `548,271` |
| File count | `396` |
| First-level target dirs | `cache`, `files` |
| Staged size on Xiaomi | `10,588 KB` |

The previously successful E directory was backed up before this release, then the target package directory was replaced by the F package. The user confirmed that this CIPS-only package can log in to 罗家臭豆腐 on Xiaomi.

## Successful E Data Shape

Top contributors in successful E:

| Path group | Files | Raw bytes | Notes |
| --- | ---: | ---: | --- |
| `databases/3054919255_message_db.db` | 1 | 8,970,240 | IM/session history from source account, retained in E. |
| `files/cips/**` selected state | 372 | 7,385,088 | Critical difference from D. |
| `databases/1793373326_message_db.db` | 1 | 1,159,168 | IM/session history from source account, retained in E. |
| `files/horn/**` | 950 | 439,180 | Config, including login/page/account switches. |
| `app_webview_*/Default/**` | 30 | 218,158 | Cookies, IndexedDB, local/session storage. |
| `cache/cips/**` selected state | 24 | 49,152 | Small CIPS state/cache only. |
| `shared_prefs/**` | 25 | 36,983 | Normal Android preferences. |

Data that was not needed in the successful E package:

- `app_flutter/flap_bundles/**`
- `code_cache/**`
- `cache/**`, except selected `cache/cips/**` state
- `files/dynlib/**`
- `files/cips/**/assets/**`
- `files/cips/**/ddload/assets/**`
- `files/cips/**/mrn_dio/**`
- large offline/resource files such as `*.dio`, `*.so`, `*.zip`, images, and generated code cache
- `databases/aidata*`
- package external data. The successful E zip contained no external-data entries.

## Current Conclusion

For Phase 13 implementation, the engine should export and upload the F/CIPS-only family for Meituan Waimai merchant:

1. CIPS state only: account/login/oneid/device/user/shop/waimai/xm/IM-related KV or small state records under `cache/cips/**` and `files/cips/**`.
2. Restore into the target shop card's current `localVirtualUserId` directory, not an assumed user id from OPPO or a previous test.
3. Explicitly exclude non-CIPS app DBs, IM history DBs, WebView state, Horn config, full cache, resource bundles, and external data unless a future regression proves a fallback is needed.

The expected production zip should be around 0.45-0.55 MB compressed for the tested 罗家臭豆腐 case, with about 10.6 MB on-device extracted size, instead of 120 MB compressed / 372 MB raw full package data. Keep E as a manual fallback profile if CIPS-only fails on another Meituan account or app version.

## App/Backend Restore Validation

After the implementation was added, the Xiaomi card directories were cleaned and the real app/backend path was tested instead of manual ADB release:

| Shop | Package | Backend profile | Server artifact | Xiaomi target | Restore log | Launch result |
| --- | --- | --- | ---: | --- | --- | --- |
| Meituan | `com.sankuai.meituan.meituanwaimaibusiness` | `meituan-waimai-cips-f` | `451,209` bytes on first download | `accounts/10/cards/clone-0c0e.../user/22/...` | `restoreLoginState downloaded` then `restored` | Opened `MainActivity`; privacy agreement appeared, not username/password login. |
| JD | `com.jd.mrd.jingming` | `jd-jingming-prefs-d` | `31,599` bytes on first download | `accounts/10/cards/CLN1-...jingming.../user/15/...` | `restoreLoginState downloaded` then `restored` | Opened `com.jd.mrd.cater.CaterMainActivity`; UI showed `订单`, `售后`, `自动接单`. |
| Ele.me | `me.ele.napos` | `ele-napos-prefs-e-min` | `73,396` bytes on first download | `accounts/10/cards/CLN1-...napos.../user/2/...` | `restoreLoginState downloaded` then `restored` | Reached `me.ele.napos.module.main.module.main.activity.HomeTabActivity`. |

The Meituan target directory after server restore and launch contained 477 files, about 11,420 KB, including 451 files under `files/cips`. This matches the CIPS-only data shape plus app-generated runtime files and confirms the restore went to Xiaomi `user22`, not the OPPO source `user3`.

The first implementation attempt exposed a bug: if `preparedShopUsers` already had a card user binding, clicking the card could bypass login-state restore and launch an empty app state after the target directory was cleaned. The fix is now part of Wave 4: prepared-user caches cannot skip restore when the backend has a login-state artifact.

After successful launch, the app exported and uploaded refreshed artifacts from Xiaomi. The resulting backend blob sizes became:

| Shop | Profile | Refreshed size |
| --- | --- | ---: |
| Meituan | `meituan-waimai-cips-f` | `458,798` bytes |
| JD | `jd-jingming-prefs-d` | `32,369` bytes |
| Ele.me | `ele-napos-prefs-e-min` | `73,814` bytes |

This size drift is expected because the target app updates some login/device/runtime state during launch; all refreshed artifacts remain under the 2 MB server limit.

## JD and Ele.me Validation

Additional ADB research was run for the other two logged-in 罗家臭豆腐 platform clones. The test order for these platforms is `E -> D -> C`, where E means the smallest hand-picked login/account/device/shop state files, D means all `shared_prefs/**`, and C means D plus core DB files. Graphic CAPTCHA, slider, and security verification are counted as login-state success; only username/password/SMS login entry is failure. Start from E and escalate only when the current candidate reaches username/password/SMS login.

### Device and Card Mapping

Both source and target devices had matching host app versions before validation:

| Platform | Package | OPPO version | Xiaomi version |
| --- | --- | --- | --- |
| 京东秒送 | `com.jd.mrd.jingming` | `9.58.1 / 905810` | `9.58.1 / 905810` |
| 淘宝闪购饿了么 | `me.ele.napos` | `14.4.4 / 140404` | `14.4.4 / 140404` |

The Xiaomi 罗家 card bindings used for release were:

| Platform | Target user | Package |
| --- | ---: | --- |
| 京东秒送 | `15` | `com.jd.mrd.jingming` |
| 淘宝闪购饿了么 | `2` | `me.ele.napos` |

### JD Source and Result

The first JD candidate inspected earlier, OPPO `user18`, was not the logged-in 罗家 source. It repeatedly showed login-page traces and empty `storeId`. After the user logged into JD, OPPO `run-as` directory scanning found the actual 罗家 source under `user28`.

Source evidence:

| Field | Value |
| --- | --- |
| OPPO source user | `28` |
| Export | `tmp/phase13-oppo-jd-ele-live/clone_data_com.jd.mrd.jingming_u28_20260611_214141.zip` |
| Full export zip bytes | `138,236,467` |
| Source shop evidence | `storeId=14395758`, `storeName=罗家臭豆腐(东瓜山店)` in `shared_prefs/JingmingAndroidClient.xml` |

JD candidates tested on Xiaomi `user15`:

| Candidate | Included data | Files | Zip bytes | Xiaomi result |
| --- | --- | ---: | ---: | --- |
| E-min | 10 hand-picked login/account/device/shop prefs | 10 | `18,344` | Failed. Opened `com.jd.mrd.jingming.login.LoginFusionActivity` with `密码登录` / `验证码登录`. |
| D | all `shared_prefs/**` | 49 | `38,570` | Passed. Opened `com.jd.mrd.cater.CaterMainActivity`; UI showed `订单`, `预订单`, and `店铺`, with no username/password/SMS login terms. |
| C | `shared_prefs/** + databases/**` | 72 | `167,741` | Not needed because D passed. |

JD conclusion: the default profile is D, all `shared_prefs/**`. Do not include DBs, files, WebView, React/native bundles, cache, or external data unless a future real-device regression proves D insufficient.

### Ele.me Source and Result

The Ele.me source was valid on the first export.

Source evidence:

| Field | Value |
| --- | --- |
| OPPO source user | `2` |
| Export | `tmp/phase13-oppo-jd-ele-first/clone_data_me.ele.napos_u2_20260611_212516.zip` |
| Full export zip bytes | `177,963,836` |
| Source shop evidence | `shopId=1184657317`, `shopName=luojia6688`, `userId=5329558971` in shared prefs |

Ele.me candidates tested on Xiaomi `user2`:

| Candidate | Included data | Files | Zip bytes | Xiaomi result |
| --- | --- | ---: | ---: | --- |
| E-min | fixed allowlist plus dynamic `user_*` login/account/device/shop prefs | 27 | `73,396` | Passed. Opened `me.ele.napos.module.main.module.main.activity.HomeTabActivity`, with no username/password/SMS login terms. |
| D | all `shared_prefs/**` | 71 | `98,825` | Not needed because E-min passed. |
| C | `shared_prefs/** + databases/**` | 92 | `143,579` | Not needed because E-min passed. |

Ele.me conclusion: the default profile is E-min, a selected shared-pref allowlist. Do not include all prefs, DBs, WebView, cache, resources, logs, external data, or full `files/**` by default.

## 2026-06-12 Final OPPO-to-Xiaomi Run

After code implementation, the validation was repeated through the real app/backend flow with both devices on `1.2.13-phase13`.

### Scoped Migration

On Xiaomi, the first launch after installing the new app and engine started `EngineCloneDataMigrationActivity`. The engine log reported a successful migration and the target cards existed under account-scoped paths:

| Platform | Scoped card path | Active target user |
| --- | --- | ---: |
| Meituan | `accounts/10/cards/clone-0c0e.../user/22/com.sankuai.meituan.meituanwaimaibusiness` | `22` |
| JD | `accounts/10/cards/CLN1-...jingming.../user/15/com.jd.mrd.jingming` | `15` |
| Ele.me | `accounts/10/cards/CLN1-...napos.../user/2/me.ele.napos` | `2` |

The old flat paths under `blackbox/data/user/<id>/<package>` were symlinks to the scoped paths. No nested package directory was present. Existing card launches still worked after migration: Meituan reached `MainActivity`, JD reached `CaterMainActivity`, and Ele.me reached `HomeTabActivity`.

On OPPO, the same migration Activity also completed. The OPPO source card data used for upload was under:

| Platform | OPPO source path/user used by final upload |
| --- | ---: |
| Meituan | `accounts/10/cards/clone-0c0e.../user/3/...` |
| JD | `accounts/10/cards/CLN1-...jingming.../user/15/...` |
| Ele.me | `accounts/10/cards/CLN1-...napos.../user/2/...` |

### Source Uploads

The OPPO app opened each source card and uploaded the current login-state artifact to the local backend:

| Shop id | Platform | Profile | Uploaded bytes | Updated at | Manifest user |
| --- | --- | --- | ---: | --- | ---: |
| `4` | Meituan | `meituan-waimai-cips-f` | `460,451` | `2026-06-12 00:26:24` | `3` |
| `5` | JD | `jd-jingming-prefs-d` | `32,367` | `2026-06-12 00:20:00` | `15` |
| `6` | Ele.me | `ele-napos-prefs-e-min` | `73,814` | `2026-06-12 00:21:12` | `2` |

These uploads prove the implemented export path can generate server-sized artifacts from the source device. All three stayed far below the 2 MB backend limit.

### Clean Xiaomi Restores

Before final restore, the Xiaomi scoped package directories for the target cards were removed. The app process was force-stopped to clear in-memory prepared-user caches. Each card was then opened from the Xiaomi app logged in as `二公子` (`152****7196`).

| Shop id | Platform | Restored profile | Download log | Target result |
| --- | --- | --- | --- | --- |
| `4` | Meituan | `meituan-waimai-cips-f` | `GET /shops/4/login-state`, `460,451` bytes, then `restoreLoginState restored` | Opened Meituan splash / `MainActivity` path without username/password/SMS login. |
| `5` | JD | `jd-jingming-prefs-d` | `GET /shops/5/login-state`, `32,367` bytes, then `restoreLoginState restored` | Opened `com.jd.mrd.cater.CaterMainActivity`. |
| `6` | Ele.me | `ele-napos-prefs-e-min` | `GET /shops/6/login-state`, `73,814` bytes, then `restoreLoginState restored` | Opened `me.ele.napos.module.main.module.main.activity.HomeTabActivity`. |

The Meituan artifact later drifted to `461,299` bytes after Xiaomi launched and re-uploaded a refreshed CIPS package. That drift is expected because the platform app mutates device/runtime state after launch.

### Final Profile Decision

The implementation should keep the smallest accepted profile per package:

- Meituan Waimai merchant: CIPS/F, `meituan-waimai-cips-f`.
- JD: start from E, but E-min failed on username/password/SMS login, so default to D, `jd-jingming-prefs-d`.
- Ele.me: E-min passed, so default to `ele-napos-prefs-e-min`.

Escalation remains `E -> D -> C`: only move up when the current package lands on username/password/SMS login. CAPTCHA, slider, or platform security verification are still considered login-state success.

## 2026-06-12 Shop Identity Revalidation

After the source-of-truth chain was corrected, Xiaomi was revalidated with app
and engine `1.2.13-phase13` installed at `2026-06-12 04:19`. The chain under
test was:

1. Main app loads server history through `/api/shops/my`.
2. Main app explicitly calls engine AIDL for the selected card's
   `packageName + localVirtualUserId`.
3. Engine reads the current phone clone directory only.
4. Main app compares the returned `ShopInfo` with server history and posts
   `/api/shops/report` only when a verified id/name update exists.

The engine-side `triggerShopIdExtract` semantics were tightened during this
revalidation: if the current read cannot find both a real shop id and a real
shop name, it returns `null`. It no longer returns stale stored `ShopInfo` as
current evidence.

Pull-to-refresh evidence:

| Platform | Xiaomi card user | Extractor evidence | Server/UI result |
| --- | ---: | --- | --- |
| Meituan | `22` | After `GET /api/shops/my`, engine logged `MeituanWaimaiShopIdExtractor: Extracted verified shop identity from CIPS files/cips/common/com.sankuai.meituan.meituanwaimaibusiness.modules.main.request.model.PoiInfo/kv`; `triggerShopIdExtract ... found=true`. | Server already matched `24059918 / 罗家臭豆腐（小吃·炸串·万家丽宇宙中心店）`; no report needed. |
| JD | `15` | After `GET /api/shops/my`, engine logged `JDShopIdExtractor: Extracted shopId=14395758, shopName=罗家臭豆腐(东瓜山店) from JingmingAndroidClient.xml`; `triggerShopIdExtract ... found=true`. | Server already matched `14395758 / 罗家臭豆腐(东瓜山店)`; no report needed. |
| Ele.me | `2` | After `GET /api/shops/my`, engine logged `EleNaposShopIdExtractor: Extracted shopId=1184657317, shopName=luojia6688 from NAPOS_LTRACKER_SP.xml`; `triggerShopIdExtract ... found=true`, then main app posted `/api/shops/report` with HTTP 200. | Server row `6` changed from `phase13-ele-luojia / identityVerified=false` to `1184657317 / luojia6688 / identityVerified=true`; UI showed `店铺ID: 1184657317` and content description `luojia6688已登录`. |

Pending-card evidence:

- Meituan pending card `8` on Xiaomi user `23` returned `triggerShopIdExtract ... found=false`.
- JD pending card `7` on Xiaomi user `3` returned `triggerShopIdExtract ... found=false`.
- UI showed pending cards with `店铺ID: -` and `未登录`; the grey treatment was local main-app rendering based on `identityVerified=false`.

Click+10s evidence:

- Clicking JD shop `5` launched the restored virtual app and reached
  `com.jd.mrd.cater.CaterMainActivity`.
- About 10 seconds later, the main app timer called the engine and the log
  showed `triggerShopIdExtract completed for com.jd.mrd.jingming, userId=15,
  found=true`.
- There was no engine lifecycle callback or timer that proactively reported
  shop identity to the main app.

This confirms the final ownership model: the phone clone directory is the live
information source; server records are history and storage; the main app is the
orchestrator; the engine is a passive current-data reader.
