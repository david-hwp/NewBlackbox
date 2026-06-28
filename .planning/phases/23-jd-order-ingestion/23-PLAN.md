---
phase: 23
plan: 23-PLAN
type: execute
wave: 1
depends_on:
  - .planning/phases/19-xpra-shop-authorization/19-PLAN.md
  - .planning/phases/21-shop-order-ingestion/21-PLAN.md
  - .planning/phases/21-shop-order-ingestion/21-SUMMARY.md
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java
  - admin/backend/src/test/java/com/duodian/admin/controller/ShopOrderControllerTest.java
  - admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java
  - admin/scripts/browser/run_authorized_meituan_orders.py
  - admin/scripts/browser/run_authorized_meituan_orders.sh
  - admin/scripts/browser/test_run_authorized_meituan_orders.py
  - admin/scripts/browser/fetch_jd_orders_node_cdp.js
  - admin/scripts/browser/test_fetch_jd_orders_node_cdp.js
autonomous: false
requirements:
  - PH23-D01
  - PH23-D02
  - PH23-D03
  - PH23-D04
  - PH23-D05
  - PH23-D06
  - PH23-D07
  - PH23-D08
---

<objective>
Extend the Phase 21 scheduled order ingestion pipeline so authorized JD 秒送 (`jdms`) shops are collected and persisted into the existing `shop_orders` table with the same idempotent update semantics as Meituan.
</objective>

<must_haves>
- PH23-D01: scheduled order collection supports authorized JD 秒送 shops, not only Meituan.
- PH23-D02: JD uses Phase 19 profiles and Phase 21 `/shop-orders/ingest`, with orders associated to system `shops.id`.
- PH23-D03: a real authorized JD profile is inspected before final parser implementation.
- PH23-D04: JD mapping fills the existing order identity/status/time/amount/customer/delivery/goods/raw business fields as far as the page/API exposes them.
- PH23-D05: repeated JD crawls update the same `shop_id + platform + platform_order_id` row as order status changes.
- PH23-D06: scheduler dispatches by platform and skips unauthorized/unsupported/incomplete targets.
- PH23-D07: diagnostics are useful but do not leak cookies, tokens, profile paths, browser storage, debug ports, or auth signals.
- PH23-D08: tests cover JD parser/collector, scheduler platform dispatch, backend target eligibility, and intranet live verification.
</must_haves>

<tasks>

<task id="23-01" type="research">
<title>Capture JD authorized order-page evidence</title>
<read_first>
- `admin/scripts/browser/zr-browser-control.py`
- `admin/scripts/browser/zr-auth-probe.js`
- `admin/scripts/browser/fetch_meituan_orders_node_cdp.js`
- `.planning/phases/23-jd-order-ingestion/23-CONTEXT.md`
</read_first>
<action>
Using the intranet crawler server and an already authorized JD profile:
- Open the JD merchant backend from `https://store.jddj.com/` through `/open` with `mode=remote-backend`.
- Use CDP/Playwright to inspect order-related menu entries, routes, visible DOM text, and network responses.
- Identify the stable order-list entry point and the best data source:
  - Prefer structured JSON/XHR payloads when they contain order IDs/status/times/amounts/items/customer/delivery fields.
  - Fall back to DOM parsing only when structured payloads are unavailable or incomplete.
- Capture sanitized samples sufficient for parser tests:
  - at least one order row/detail when the profile has orders.
  - no-order page/state if there are no today orders.
  - login-expired state signature if the profile is no longer authorized.
- Do not persist cookies, tokens, request headers, profile paths, debug ports, browser storage, or authorization probe signals.
</action>
<acceptance_criteria>
- The chosen JD order route/API is documented in the execution notes or summary.
- Parser fixtures contain only sanitized order-business data or page text.
- The implementation does not proceed by guessing selectors without evidence.
- If live JD has no current orders, the no-order evidence is still sufficient to build empty-state handling, and parser tests use sanitized historical/sample payload where available.
</acceptance_criteria>
<verify>
- Manual CDP inspection on crawler server for shop 76 / platform `jdms` if still authorized.
- `node --check` on any temporary inspection script before using it.
</verify>
</task>

<task id="23-02" type="execute">
<title>Backend crawl-target eligibility includes JD</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopOrderController.java`
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopRepository.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/AuthorizedShopOrderCrawlTarget.java`
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopOrderControllerTest.java`
- `admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java`
</read_first>
<action>
Update backend crawl targets:
- Change supported order crawl platforms from only `mtwm` to `mtwm` and `jdms`.
- Keep target eligibility limited to `shop_authorization_status = AUTHORIZED`.
- Keep minimal target fields: system shop ID, owner phone, control profile shop ID, shop name, platform, platform name.
- Preserve external callback token or super-admin JWT protection on `/shop-orders/crawl-targets`.
- Do not change order table schema or order list UI APIs unless live JD evidence proves an existing field mapping bug.
</action>
<acceptance_criteria>
- Authorized JD shops appear in `/shop-orders/crawl-targets`.
- Unauthorized/UNKNOWN/FAILED JD shops do not appear.
- Existing authorized Meituan target behavior is unchanged.
- Missing owner phone still excludes the target.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `git diff --check`
</verify>
</task>

<task id="23-03" type="execute">
<title>Generalize scheduled authorized order collection</title>
<read_first>
- `admin/scripts/browser/run_authorized_meituan_orders.py`
- `admin/scripts/browser/run_authorized_meituan_orders.sh`
- `admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `admin/scripts/browser/fetch_meituan_orders_node_cdp.js`
</read_first>
<action>
Refactor the scheduler without breaking the existing cron entry:
- Keep the existing `run_authorized_meituan_orders.sh` callable, but update internals/log wording to authorized platform orders when practical.
- Replace `is_supported_target(... platform == "mtwm")` with supported platform dispatch for `mtwm` and `jdms`.
- Introduce a collector mapping:
  - `mtwm` -> existing Meituan Node/CDP collector.
  - `jdms` -> new JD Node/CDP collector.
- Pass common environment variables to each collector:
  - `ZR_SYSTEM_SHOP_ID`, `ZR_SHOP_PHONE`, `ZR_SHOP_ID`, `ZR_SHOP_NAME`, `ZR_CONTROL_URL`, `OUTPUT_DIR`.
  - Add `ZR_PLATFORM` for collector diagnostics.
- Keep per-shop output directories and one-week cleanup.
- Keep non-overlapping run lock and secret env file behavior.
- Make logs include platform/system shop ID/counts, but never callback token, profile path, debug port, cookie, or storage content.
</action>
<acceptance_criteria>
- Scheduler accepts valid `mtwm` and `jdms` targets.
- Scheduler rejects unsupported platforms and targets missing system shop ID, owner phone, or control shop ID.
- Scheduler calls the correct collector script per platform.
- Existing Meituan collection still works.
- JD collector failures increment failures for that shop without stopping other platforms.
</acceptance_criteria>
<verify>
- `python3 -m py_compile admin/scripts/browser/run_authorized_meituan_orders.py admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `python3 admin/scripts/browser/test_run_authorized_meituan_orders.py`
</verify>
</task>

<task id="23-04" type="execute">
<title>Implement JD Node/CDP collector and parser</title>
<read_first>
- `admin/scripts/browser/fetch_meituan_orders_node_cdp.js`
- `admin/scripts/browser/test_fetch_meituan_orders.py`
- `.planning/phases/23-jd-order-ingestion/23-RESEARCH.md`
</read_first>
<action>
Create `fetch_jd_orders_node_cdp.js` and focused tests:
- Use `/open` to launch the JD profile with the route discovered in task `23-01`.
- Connect over CDP and collect orders from the chosen source.
- Emit the same payload contract as the Meituan Node collector:
  - local snapshot JSON under the per-shop output directory.
  - `jd_orders_<systemShopId>_<timestamp>.json` and latest file.
  - `jd_ingest_payload.json` containing `shopId`, `source`, `ingestBatchId`, and `orders`.
  - final stdout JSON with payload path and counts.
- Map JD fields into `ShopOrderIngestRequest.OrderItem`:
  - `platform_order_id`, `platform_order_no`, `order_sequence`.
  - `status`, `status_text`, `order_type`, `tags`.
  - `ordered_at`, `expected_delivery_at`, `completed_at`, `cancelled_at`, `refunded_at` when available.
  - `estimated_income`, `customer_paid_amount`, `merchant_income`, `original_amount`, `discount_amount`, `delivery_fee`, `package_fee`, `refund_amount`, `currency`.
  - `customer_name`, `customer_phone_tail`, `privacy_phone`, `backup_phone`, `address`, `recipient_address`.
  - `delivery_type`, `rider_name`, `rider_phone`, `remark`.
  - `item_summary`, `item_count`, `items_json`, `raw_text`, sanitized `raw_payload`.
- Detect and report:
  - authorized empty order list.
  - login-expired page.
  - parser no-match/page-structure change.
- Ensure raw payload and logs omit sensitive credential/profile/browser fields.
</action>
<acceptance_criteria>
- JD parser unit tests pass with sanitized fixture data from task `23-01`.
- No-order and login-expired states return clear diagnostics and do not submit fake orders.
- A payload with the same `platform_order_id` is stable across repeated parses.
- Collector final stdout can be consumed by the generalized scheduler.
- `raw_payload` contains JD order business fields only.
</acceptance_criteria>
<verify>
- `node --check admin/scripts/browser/fetch_jd_orders_node_cdp.js`
- `node admin/scripts/browser/test_fetch_jd_orders_node_cdp.js`
- `python3 admin/scripts/browser/test_run_authorized_meituan_orders.py`
</verify>
</task>

<task id="23-05" type="execute">
<title>JD ingestion update semantics tests</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java`
- `admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/ShopOrderIngestRequest.java`
</read_first>
<action>
Add or extend backend tests for JD-specific payloads:
- Ingest a `jdms` shop order once and assert one row is inserted with platform `jdms`.
- Re-ingest the same JD order ID with a later status/time and assert the same row is updated.
- Verify omitted optional detail fields on the second crawl do not clear previously stored customer/address/amount/item fields.
- Verify raw payload sanitization removes sensitive JD-like keys if present.
</action>
<acceptance_criteria>
- JD upsert semantics are proven independently from Meituan parser tests.
- Existing Meituan ingestion tests continue to pass.
- Sensitive raw-payload stripping applies to JD payloads.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderServiceTest test`
</verify>
</task>

<task id="23-06" type="verify">
<title>Deploy to intranet and run live JD collection</title>
<read_first>
- `admin/deploy.sh`
- `admin/docker-compose.yml`
- `admin/scripts/browser/run_authorized_meituan_orders.sh`
- `.planning/phases/23-jd-order-ingestion/23-CONTEXT.md`
</read_first>
<action>
Deploy and verify only the development/test topology:
- Backend target: Mac intranet admin server `hewp@172.20.0.13`.
- Crawler target: Huawei crawler server `ubuntu@192.168.0.210`, reached through `ubuntu@zhirang-dev`.
- Do not deploy, upgrade, or restart the online `zhirang-dev` app environment without explicit user approval.
- Sync updated crawler scripts to `/home/ubuntu/data` on the crawler server.
- Run the scheduler once manually.
- Confirm authorized JD target(s), especially shop 76 if still authorized, are attempted.
- Confirm ingest response counts and query `/shop-orders` for `platform=jdms` and the relevant system shop ID.
- If there are no JD orders today, record no-order evidence rather than fabricating live data.
- If the JD profile is no longer authorized, record the authorization blocker separately from code correctness.
</action>
<acceptance_criteria>
- Intranet backend returns authorized crawl targets for both `mtwm` and `jdms` when such shops are authorized.
- Manual scheduler run handles JD targets and produces sanitized per-shop output.
- For a JD shop with available orders, rows are inserted or updated in `shop_orders`.
- For no-order or login-expired outcomes, logs clearly identify the outcome without leaking credentials.
- Existing Meituan scheduled collection remains runnable.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `python3 -m py_compile admin/scripts/browser/run_authorized_meituan_orders.py admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `node --check admin/scripts/browser/fetch_meituan_orders_node_cdp.js admin/scripts/browser/fetch_jd_orders_node_cdp.js`
- `node admin/scripts/browser/test_fetch_jd_orders_node_cdp.js`
- Manual crawler server run of `/home/ubuntu/data/run_authorized_meituan_orders.sh`.
- Admin API or UI query for `/shop-orders?platform=jdms&shopId=<systemShopId>`.
</verify>
</task>

</tasks>

<artifacts_this_phase_produces>
- Backend target eligibility for authorized JD order crawling.
- Generalized platform-dispatch scheduled order collector.
- JD Node/CDP order collector and parser.
- Sanitized JD parser fixtures/tests.
- Scheduler tests for `mtwm` and `jdms` dispatch.
- Backend tests for JD upsert/status-update semantics.
- Intranet live verification notes in `23-SUMMARY.md`.
</artifacts_this_phase_produces>

<not_in_scope>
- New `shop_orders` schema or separate JD order table.
- New management UI page; existing `/shop-orders` must show JD rows via platform/shop filters.
- Taobao/Ele.me order collection.
- JD order operations such as accept, cancel, refund, print, or message.
- Captcha/risk-control bypass.
- Production `zhirang-dev` app deployment without explicit user approval.
</not_in_scope>

<threat_model>

## Threat Model

| Threat | Severity | Mitigation |
|--------|----------|------------|
| JD collector leaks profile credentials or browser tokens | High | Collector snapshots/logs only include sanitized order-business payloads; backend raw-payload sanitizer remains active; tests assert sensitive keys are excluded. |
| Scheduler submits JD orders under the wrong shop | High | Target uses system `shops.id`; backend derives user/channel/platform snapshots; collector receives per-target system shop ID and profile shop ID separately. |
| Duplicate JD crawls create duplicate rows | Medium | Existing upsert key `shop_id + platform + platform_order_id`; add JD-specific update tests. |
| Page structure changes silently produce bad orders | Medium | First task captures evidence; parser reports no-match diagnostics; tests pin sanitized fixtures. |
| Login-expired JD profile is treated as zero orders | Medium | Collector distinguishes login-expired signatures from authorized empty order lists. |
| Existing Meituan cron breaks during generalization | High | Preserve wrapper compatibility and run Meituan scheduler tests plus one manual run where possible. |
| Production app environment accidentally restarted | High | Verification explicitly targets intranet 13 and crawler 210; production `zhirang-dev` app deployment is out of scope without user approval. |

</threat_model>

<verification>
- `git diff --check`
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `python3 -m py_compile admin/scripts/browser/run_authorized_meituan_orders.py admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `python3 admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `node --check admin/scripts/browser/fetch_meituan_orders_node_cdp.js admin/scripts/browser/fetch_jd_orders_node_cdp.js`
- `node admin/scripts/browser/test_fetch_jd_orders_node_cdp.js`
- Manual intranet verification:
  - `/shop-orders/crawl-targets` includes authorized JD targets.
  - scheduler dispatches JD collector.
  - JD rows are inserted/updated in `shop_orders` when live orders exist.
  - `/shop-orders` filters can show `platform=jdms` rows for the selected shop.
  - no-order/login-expired states are documented if they block live rows.
</verification>

<success_criteria>
- Authorized JD 秒送 shops are included in scheduled order collection targets.
- Scheduler dispatches `mtwm` and `jdms` to the correct collectors and keeps per-shop output cleanup.
- JD collector can use an authorized Phase 19 profile to collect order data or classify no-order/login-expired states.
- JD order payloads persist through existing `/shop-orders/ingest` with `shops.id` association.
- Repeated JD crawls update existing rows instead of inserting duplicates.
- Raw payloads, logs, fixtures, and summaries do not expose profile credentials, cookies, tokens, browser storage, debug ports, or auth signals.
- Backend tests, scheduler tests, JD parser tests, and intranet live verification pass or document a concrete platform/profile blocker.
- No production `zhirang-dev` app deployment occurs without explicit approval.
</success_criteria>

<output>
Create `.planning/phases/23-jd-order-ingestion/23-SUMMARY.md` after execution.
</output>
