---
phase: 21
plan: 21-PLAN
type: execute
wave: 1
depends_on:
  - .planning/phases/19-xpra-shop-authorization/19-PLAN.md
  - .planning/phases/19-xpra-shop-authorization/19-04-PLAN.md
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ShopOrder.java
  - admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderRepository.java
  - admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java
  - admin/backend/src/main/java/com/duodian/admin/controller/ShopOrderController.java
  - admin/backend/src/main/java/com/duodian/admin/controller/dto/ShopOrderIngestRequest.java
  - admin/backend/src/main/java/com/duodian/admin/controller/dto/ShopOrderResponse.java
  - admin/backend/src/test/java/com/duodian/admin/controller/ShopOrderControllerTest.java
  - admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java
  - admin/scripts/browser/fetch_meituan_orders.py
  - admin/scripts/browser/test_fetch_meituan_orders.py
  - admin/frontend/src/router/index.js
  - admin/frontend/src/views/Layout.vue
  - admin/frontend/src/views/Shops.vue
  - admin/frontend/src/views/ShopOrders.vue
autonomous: false
requirements:
  - PH21-D01
  - PH21-D02
  - PH21-D03
  - PH21-D04
  - PH21-D05
  - PH21-D06
  - PH21-D07
  - PH21-D08
  - PH21-D09
  - PH21-D10
  - PH21-D11
  - PH21-D12
---

<objective>
Persist order data collected by the Phase 19 Meituan crawler into the admin database, expose a super-admin-only order query page, and add a super-admin-only “店铺订单” action in the shop list that opens the page pre-filtered to that shop for today from 00:00 to now.
</objective>

<must_haves>
- PH21-D01: crawler-collected shop orders are stored in the backend database, not only local JSON.
- PH21-D02: every order row references `shops.id` and snapshots platform shop/user/channel fields.
- PH21-D03: database fields cover detailed order identity, time, status, amount, customer, delivery, goods, and raw order payload data.
- PH21-D04: ingestion is idempotent by `shop_id + platform + platform_order_id`.
- PH21-D05: super-admin list API supports pagination and completed-time start/end filtering at minute precision.
- PH21-D06: crawler ingestion endpoint or equivalent secure channel validates shop/order data before saving.
- PH21-D07: new admin order page and route are super-admin-only.
- PH21-D08: order page has standard filters plus completed-time range.
- PH21-D09: shop list has a super-admin-only “店铺订单” action that jumps with shop ID and today 00:00-to-now filters.
- PH21-D10: shop list operation column stays fixed and existing actions remain usable.
- PH21-D11: order ingestion never stores authorization profile credentials, cookies, tokens, or browser storage dumps.
- PH21-D12: implementation includes backend tests, crawler parser/submission test, frontend build, and intranet headed-browser verification.
</must_haves>

<tasks>

<task id="21-01" type="execute">
<title>Backend order schema and entity model</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java`
- `admin/backend/src/main/java/com/duodian/admin/entity/Shop.java`
- `admin/backend/src/main/java/com/duodian/admin/entity/TransactionLog.java`
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopRepository.java`
</read_first>
<action>
Add persistent order storage:
- Create `ShopOrder` JPA entity mapped to `shop_orders`.
- Add `ensureShopOrderTable()` to `SoftDeleteSchemaInitializer` and include `shop_orders` in soft-delete/index handling.
- Use `BIGINT shop_id` to reference system `shops.id`; do not use platform `shopId` as the primary relationship.
- Add denormalized snapshots: `user_id`, `channel_id`, `platform`, `platform_name`, `platform_shop_id`, `shop_name`.
- Add detailed structured fields:
  - order identity: `platform_order_id`, `platform_order_no`, `order_sequence`, `source`.
  - times: `order_time_text`, `ordered_at`, `expected_delivery_at`, `completed_at`, `cancelled_at`, `refunded_at`, `fetched_at`, `last_seen_at`.
  - status: `status`, `status_text`, `order_type`, `tags_json`.
  - financials: `estimated_income`, `customer_paid_amount`, `merchant_income`, `original_amount`, `discount_amount`, `delivery_fee`, `package_fee`, `refund_amount`, `currency`.
  - customer/delivery: `customer_name`, `customer_phone_tail`, `privacy_phone`, `backup_phone`, `address`, `recipient_address`, `delivery_type`, `rider_name`, `rider_phone`, `remark`.
  - goods/debug: `item_summary`, `item_count`, `items_json`, `raw_text`, `raw_payload`, `ingest_batch_id`.
- Add unique key/index support for `shop_id + platform + platform_order_id + deleted`, plus indexes for `completed_at`, `shop_id`, `platform`, `status`, `channel_id`, and `user_id`.
- Keep `raw_payload` as order-business JSON only; explicitly exclude cookies, tokens, browser storage, profile directories, and auth signals.
</action>
<acceptance_criteria>
- `shop_orders` exists after backend startup on a clean DB.
- `ShopOrder` has detailed fields and normal lifecycle timestamps.
- A duplicate platform order for the same system shop can be found by repository methods for upsert.
- Indexes support the planned query filters.
- No entity/DTO field is intended to store profile credentials or browser storage.
</acceptance_criteria>
<verify>
- `cd admin/backend && mvn -Dtest=ShopOrderServiceTest test`
- `git diff --check`
</verify>
</task>

<task id="21-02" type="execute">
<title>Backend ingestion and query APIs</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/service/PermissionService.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/TransactionLogController.java`
- `admin/backend/src/main/java/com/duodian/admin/repository/TransactionLogRepository.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/PagedResponse.java`
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopControllerTest.java`
</read_first>
<action>
Add order service/API:
- Create ingestion DTOs such as `ShopOrderIngestRequest` and nested `OrderItem`.
- Create response DTO `ShopOrderResponse` that formats shop/user/channel snapshots and order details for the frontend.
- Create `ShopOrderRepository.searchOrders(...)` with filters:
  - system `shopId` (`shops.id`)
  - platform
  - status
  - shop name keyword
  - customer keyword across customer name, phone tail, privacy/backup phone, address
  - platform order ID/order no
  - `completedStart` and `completedEnd`, parsed as local date-time minute precision.
- Create `ShopOrderService.ingestBatch(...)`:
  - require a valid system shop.
  - derive user/channel/platform/shop snapshots from `Shop`.
  - reject orders missing `platform_order_id`.
  - normalize money to `BigDecimal`.
  - normalize date-times; accept null `completed_at`.
  - upsert by same system shop/platform/platform order ID.
  - preserve previously captured non-empty detail fields when a later crawl sees the same order but the page omits details such as phone, address, or amount.
  - update mutable state fields such as order status, fetched time, last seen time, and completion/cancel/refund timestamps on the same row as the order progresses from newly placed to rider handling to delivered.
  - return counts: received, inserted, updated, rejected.
- Create `ShopOrderController`:
  - `GET /shop-orders` requires `permissionService.requireSuperAdmin()` and returns `PagedResponse<ShopOrderResponse>`.
  - `POST /shop-orders/ingest` accepts a valid `X-External-Callback-Token` for crawler/external-system submissions, and retains super-admin JWT fallback for manual admin verification.
  - Store the real callback token only in environment-specific secret files. Do not write token values to git, GSD docs, logs, screenshots, or chat transcripts.
  - Do not accept caller-provided `user_id`/`channel_id` as authority.
</action>
<acceptance_criteria>
- Non-super-admin JWT callers are rejected from the order list and from ingestion unless they possess the external callback token.
- Invalid or missing external callback tokens are rejected before ingestion.
- Super-admin can list orders with pagination.
- Completed-time start/end filters include expected rows and exclude rows outside the minute range.
- Ingesting the same order twice updates the same row and reports updated count.
- Re-ingesting the same order with a later status updates that row without clearing previously captured phone/address/amount details.
- Ingestion derives shop/user/channel/platform from backend shop state.
- Invalid shop and missing order ID are rejected with clear API errors.
</acceptance_criteria>
<verify>
- `cd admin/backend && mvn -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `cd admin/backend && mvn test -Dtest=ShopControllerTest`
</verify>
</task>

<task id="21-03b" type="execute">
<title>Schedule all authorized Meituan shop order collection</title>
<read_first>
- `admin/scripts/browser/run_authorized_meituan_orders.py`
- `admin/scripts/browser/fetch_meituan_orders_node_cdp.js`
- `admin/scripts/browser/run_authorized_meituan_orders.sh`
</read_first>
<action>
- Add `GET /shop-orders/crawl-targets`, protected by `X-External-Callback-Token` or super-admin JWT, returning only the minimal fields needed by the crawler: system shop ID, owner phone, control profile shop ID, shop name, platform, and platform name.
- Limit automatic targets to `shop_authorization_status = AUTHORIZED` and supported Meituan Waimai platform `mtwm`.
- The scheduler fetches crawl targets from the intranet admin backend, runs one Node/CDP collection per target, writes per-shop snapshots under `order-output/shop-<systemShopId>/`, then posts each payload to `/shop-orders/ingest`.
- Before creating new files for a shop, delete regular files in that shop's output directory older than 7 days by default (`ZR_ORDER_OUTPUT_RETENTION_DAYS`; negative disables cleanup). Do not delete subdirectories or symlinks.
- The runtime wrapper sources `/home/ubuntu/data/secrets/phase21-orders.env`, uses `flock` to prevent overlapping runs, and is intended to run from cron every 30 minutes.
</action>
<acceptance_criteria>
- Every 30 minutes, the crawler server attempts to collect today's orders for all currently authorized supported shops.
- Unauthorized, unknown, failed, unsupported, or missing-owner-phone shops are skipped.
- Each shop writes separate local output files so multi-shop runs do not overwrite another shop's payload.
- Per-shop local output is pruned before each collection so files older than one week do not accumulate indefinitely.
- Scheduler logs aggregate received/inserted/updated/rejected counts without printing external callback tokens.
</acceptance_criteria>
<verify>
- `python3 -m py_compile admin/scripts/browser/run_authorized_meituan_orders.py admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `python3 admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `node --check admin/scripts/browser/fetch_meituan_orders_node_cdp.js`
- Manual on crawler server: run `/home/ubuntu/data/run_authorized_meituan_orders.sh` once and confirm `/api/shop-orders/ingest` returns success for authorized shops.
</verify>
</task>

<task id="21-03" type="execute">
<title>Wire the Meituan crawler to submit orders after extraction</title>
<read_first>
- `admin/scripts/browser/fetch_meituan_orders.py`
- `admin/scripts/browser/zr-browser-control.py`
- `.planning/phases/19-xpra-shop-authorization/19-04-PLAN.md`
</read_first>
<action>
Update the crawler script:
- Add CLI/env parameters:
  - `--backend-url` / `ZR_BACKEND_URL`
  - `--external-callback-token` / `ZR_EXTERNAL_CALLBACK_TOKEN`
  - legacy fallback `--backend-token` / `ZR_BACKEND_TOKEN` only for older backend deployments.
  - `--system-shop-id` / `ZR_SYSTEM_SHOP_ID`
  - keep existing `--shop-id` for Phase 19 profile selection if needed.
- Build an ingestion payload after parsing visible orders and before/after local JSON save.
- Preserve local JSON snapshot writing for diagnostics.
- Include all parsed fields in structured form plus `raw_payload` with sanitized order data.
- Add parser helpers to split existing `order_time` text into `ordered_at`, `expected_delivery_at`, and best-effort `completed_at`.
- Submit `POST /api/shop-orders/ingest` to the backend and log received/inserted/updated/rejected counts.
- Avoid logging or submitting cookies, local storage, profile paths, debug ports, or authorization probe signals.
</action>
<acceptance_criteria>
- Running the script with backend URL/token/system shop ID submits extracted orders to the backend.
- Script still works in local-output-only mode when backend parameters are absent, unless implementation chooses to make backend submission required for scheduled jobs.
- Unit/sample test proves existing Meituan card text maps to expected ingestion fields.
- Ingestion response is logged without sensitive credentials.
- The crawler sends `X-External-Callback-Token` when configured and never prints the token.
</acceptance_criteria>
<verify>
- `python3 -m py_compile admin/scripts/browser/fetch_meituan_orders.py`
- `python3 admin/scripts/browser/test_fetch_meituan_orders.py`
- Manual on crawler server against intranet backend for “极点披萨” if authorized profile is available.
</verify>
</task>

<task id="21-04" type="execute">
<title>Admin frontend order page and shop-list jump action</title>
<read_first>
- `admin/frontend/src/router/index.js`
- `admin/frontend/src/views/Layout.vue`
- `admin/frontend/src/views/Shops.vue`
- `admin/frontend/src/views/Logs.vue`
- `admin/frontend/src/utils/adminSession.js`
- `admin/frontend/src/utils/request.js`
</read_first>
<action>
Build the super-admin UI:
- Add `ShopOrders.vue` using the existing admin table style:
  - no hero/marketing layout.
  - filter bar with shop ID, shop name, platform, status, customer keyword, order keyword, completed-time range.
  - date-time range picker configured to minute precision.
  - paginated `el-table` with dense operational columns for shop, order identity, status, times, customer/delivery, amount, item summary, fetched time.
- Add route `/shop-orders` with `meta: { title: '店铺订单', superAdminOnly: true }`.
- Add a super-admin-only sidebar menu item for “店铺订单”.
- In `Shops.vue`, add a super-admin-only link button text exactly `店铺订单` in the existing fixed operation column.
- Clicking the shop-list button must navigate to `/shop-orders` with query params:
  - `shopId` = row system `id`
  - `completedStart` = local today at `00:00`
  - `completedEnd` = local current time rounded/formatted to minute precision.
- Widen or adjust the fixed operation column so “远程后台”, “店铺订单”, “编辑”, and “删除” remain usable without breaking table layout.
</action>
<acceptance_criteria>
- Non-super-admin cannot access `/shop-orders` through route guard.
- Super-admin sees sidebar entry and can query the order list.
- Order page reads query params on load and initializes filters.
- Completed-time filter submits minute-precision values to backend.
- Shop-list operation column remains fixed right and includes both “远程后台” and “店铺订单”.
- “店铺订单” jump sets shop ID and today 00:00-to-now completed-time filters.
</acceptance_criteria>
<verify>
- `cd admin/frontend && npm run build`
- Headed browser on intranet admin:
  - Login as super-admin.
  - Open `/shops`; confirm operation column is fixed and contains “远程后台” and “店铺订单”.
  - Click “店铺订单” for “极点披萨”; confirm `/shop-orders` opens with that system shop ID and today's time range.
  - Run a query and confirm rows match the selected filters after test data ingestion.
  - Attempt `/shop-orders` as a non-super-admin and confirm redirect/no access.
</verify>
</task>

<task id="21-05" type="execute">
<title>Dev/test deployment and end-to-end verification</title>
<read_first>
- `admin/docker-compose.yml`
- `admin/deploy.sh`
- `.planning/phases/19-xpra-shop-authorization/19-PLAN.md`
- `.planning/phases/19-xpra-shop-authorization/19-04-SUMMARY.md`
</read_first>
<action>
Deploy and verify only the intranet development/test environment:
- Backend/frontend target: Mac intranet admin server `hewp@172.20.0.13`, admin URL `http://172.20.0.13:8006`.
- Crawler/remote browser target remains the Phase 19 crawler server, reached through the existing dev/test topology.
- Do not deploy, upgrade, or restart the online `zhirang-dev` app environment without explicit user approval.
- Seed or ingest at least one order batch for the currently authorized “极点披萨” profile if the profile remains valid; otherwise use controlled backend ingestion test data and record that live crawl was blocked by authorization/profile state.
- Use a headed browser to verify the admin UI.
</action>
<acceptance_criteria>
- Intranet backend has `shop_orders` table and can ingest/list orders.
- Intranet frontend serves `/shop-orders`.
- “极点披萨” shop-list button opens the order page with prefilled shop/time filters.
- If live crawler ingestion is possible, orders appear after running the crawler; if not, backend test data proves UI/API and the blocker is documented.
- No production `zhirang-dev` app deployment is performed.
</acceptance_criteria>
<verify>
- `cd admin/backend && mvn -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `cd admin/frontend && npm run build`
- `python3 -m py_compile admin/scripts/browser/fetch_meituan_orders.py`
- Headed browser verification against `http://172.20.0.13:8006`.
</verify>
</task>

</tasks>

<artifacts_this_phase_produces>
- `shop_orders` database table and indexes.
- `ShopOrder` JPA entity.
- `ShopOrderRepository`.
- `ShopOrderService`.
- `ShopOrderController`.
- `ShopOrderIngestRequest` and `ShopOrderResponse` DTOs.
- Updated `fetch_meituan_orders.py` backend ingestion mode.
- `ShopOrders.vue` management page.
- `/shop-orders` super-admin route and sidebar entry.
- Super-admin-only `店铺订单` action in the shop list.
</artifacts_this_phase_produces>

<threat_model>

## Threat Model

| Threat | Severity | Mitigation |
|--------|----------|------------|
| Non-super-admin accesses customer order data | High | Require `permissionService.requireSuperAdmin()` on order APIs and `meta.superAdminOnly` on frontend route; hide sidebar and shop-list action for non-super-admin. |
| Crawler submits orders under the wrong shop | High | Ingestion uses system `shops.id`; backend derives user/channel/platform/shop snapshots from `Shop`, not caller-provided ownership fields. |
| Duplicate crawler runs create duplicate orders | Medium | Upsert by `shop_id + platform + platform_order_id`; tests assert row count stays stable on duplicate ingest. |
| Raw payload leaks cookies, tokens, profile data, or browser storage | High | Crawler builds a sanitized business-order payload only; backend DTO does not accept credential fields; logs redact token values. |
| Completed-time filter silently uses the wrong timestamp | Medium | Store separate `ordered_at`, `expected_delivery_at`, and `completed_at`; the UI/API filter only uses `completed_at`; null completed time is visible and not falsely matched. |
| PII exposure beyond operational need | Medium | Restrict page/API to super-admin; avoid exposing order data to APP/channel admins; keep only order-business fields needed for operations. |
| Production environment accidentally upgraded during verification | High | Verification task explicitly targets `172.20.0.13` intranet and forbids `zhirang-dev` app deployment without user approval. |

</threat_model>

<verification>
- `git diff --check`
- `cd admin/backend && mvn -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`
- `cd admin/backend && mvn test -Dtest=ShopControllerTest`
- `python3 -m py_compile admin/scripts/browser/fetch_meituan_orders.py`
- `python3 admin/scripts/browser/test_fetch_meituan_orders.py`
- `cd admin/frontend && npm run build`
- Intranet headed browser:
  - super-admin can open `/shop-orders`.
  - non-super-admin cannot open `/shop-orders`.
  - `/shops` operation column is fixed right and has “远程后台” plus “店铺订单”.
  - clicking “店铺订单” on “极点披萨” sets system shop ID and today 00:00-to-now completed-time filters.
  - completed-time range query returns only matching rows.
</verification>

<success_criteria>
- Crawler-collected order data is persisted in `shop_orders` with detailed structured fields and sanitized raw order payload.
- Every order row is associated with system `shops.id` and includes shop/user/channel/platform snapshots.
- Duplicate ingestion updates existing rows instead of inserting duplicates.
- Super-admin-only backend APIs list and ingest order data; non-super-admin access is rejected.
- Management backend has a super-admin-only “店铺订单” page with standard filters, pagination, and minute-precision completed-time range filtering.
- Shop list operation column includes a super-admin-only “店铺订单” button and remains fixed/right aligned with existing actions intact.
- Clicking the shop-list button jumps to the order page with the selected shop and today's 00:00-to-now filters pre-filled.
- Backend tests, crawler tests, frontend build, and intranet headed-browser verification pass or record a concrete external blocker.
- No online `zhirang-dev` app deployment occurs without explicit approval.
</success_criteria>

<output>
Create `.planning/phases/21-shop-order-ingestion/21-SUMMARY.md` after execution.
</output>
