# Phase 21: 店铺订单入库与超管查询 - Research

**Date:** 2026-06-24
**Status:** Complete

## Research Question

What do we need to know to plan order ingestion from the Phase 19 crawler into the admin database, with a super-admin-only order page and shop-list jump entry?

## Key Findings

### 1. Current crawler only writes local JSON

`admin/scripts/browser/fetch_meituan_orders.py` connects to the existing ZR remote browser session through `/open`, navigates to the Meituan order history page, reveals phone/address fields, parses each visible order card, deduplicates by `order_id`, and writes snapshots plus a latest file under the configured output directory.

Current parsed fields are:

- `order_no`
- `order_id`
- `order_time`
- `customer_name`
- `privacy_phone`
- `backup_phone`
- `customer_phone_tail`
- `address`
- `status`
- `estimated_income`
- `raw_text`
- `fetched_at`

Planning implication: Phase 21 can start with these fields and add parser slots for richer data, while keeping `raw_payload`/`raw_text` so future parser improvements can backfill.

### 2. The backend uses Spring Boot, JPA repositories, and startup schema repair

The admin backend has entity/repository/service/controller layers under `admin/backend/src/main/java/com/duodian/admin`. Existing schema evolution is centralized in `SoftDeleteSchemaInitializer`, which creates tables and adds missing columns/indexes at startup rather than using Flyway/Liquibase migrations.

Planning implication: add `ShopOrder` entity, repository, service, controller/DTOs, and `ensureShopOrderTable()` in `SoftDeleteSchemaInitializer`. This keeps the change consistent with the existing deployment style.

### 3. Shop identity and permission data already exist

`Shop` includes:

- system `id`
- `userId`
- `channelId`
- `shopName`
- platform-side `shopId`
- `platform`
- `platformName`
- `packageName`
- `shopAuthorizationStatus`
- `shopAuthorizationUrl` for super-admin only responses

Planning implication: order rows should store `shop_id` as a foreign/reference value to `shops.id`, and copy denormalized snapshots (`platform_shop_id`, `shop_name`, `platform`, `platform_name`, `user_id`, `channel_id`) at ingestion time. Queries can join to `shops` if needed, but snapshots keep historical display stable after shop edits.

### 4. Super-admin gates exist in both backend and frontend

Backend `PermissionService.requireSuperAdmin()` is already used for advanced settings and the Phase 19 authorization open-url path. Frontend route metadata supports `meta: { superAdminOnly: true }`, and `Layout.vue` hides super-admin-only menu entries with `isSuperAdmin`.

Planning implication: `ShopOrderController` must call `permissionService.requireSuperAdmin()` for list and ingest endpoints unless a narrower crawler credential is added. Frontend `/shop-orders` route must include `superAdminOnly: true`, and the shop-list button must render only when `isSuperAdmin`.

### 5. Existing admin list pages provide a good UI pattern

`Shops.vue` and `Logs.vue` both use Element Plus cards, inline filter bars, `el-table`, `PagedResponse`, and `el-pagination`. `Shops.vue` already has a fixed right operation column with the “远程后台” button.

Planning implication: create `ShopOrders.vue` following the same dense admin-table pattern, not a dashboard or landing page. Add a sidebar item only for super-admin. The shop-list operation column width likely needs to grow enough to fit “远程后台 / 店铺订单 / 编辑 / 删除” without wrapping awkwardly.

### 6. Time filtering needs minute precision and completed-time semantics

The user specifically asked for start/end filtering on order completion time, precise to minutes. Existing crawler text has `order_time` from a broad regex such as `06-20 12:45前送达` or `06-20 12:19下单`, and `status` may say `用户已收餐`, `已完成`, or similar.

Planning implication: persist separate normalized times:

- `ordered_at` for 下单 time when parseable.
- `expected_delivery_at` for `xx前送达` when parseable.
- `completed_at` for completed/received status time when parseable.
- `order_time_text` for the original parsed text.

The Phase 21 filter uses only `completed_at`. If the crawler cannot infer completion time, keep it null and preserve `order_time_text`/`raw_text`; those orders will not match completed-time range queries until parser support improves.

### 7. Existing tests are controller/service focused

Backend tests use JUnit + Mockito for controllers/services. Frontend has a build command but no visible unit-test suite for Vue views. Prior Phase 19 verification used frontend build plus headed-browser/manual verification for admin workflows.

Planning implication:

- Backend: add `ShopOrderControllerTest`/`ShopOrderServiceTest` focused on super-admin gating, ingestion validation, upsert, and completed-time range query.
- Crawler: add a Python parser/unit test or at least a deterministic script test for sample card text to API payload mapping.
- Frontend: run `npm run build`; use headed browser automation against the intranet admin to verify super-admin route, filters, and shop-list button navigation.

## Proposed Data Model

`shop_orders` should include detailed structured columns plus raw payload:

- Identity: `id`, `shop_id`, `platform`, `platform_order_id`, `platform_order_no`, `platform_shop_id`, `shop_name`, `platform_name`, `user_id`, `channel_id`.
- Status/time: `status`, `status_text`, `order_time_text`, `ordered_at`, `expected_delivery_at`, `completed_at`, `cancelled_at`, `refunded_at`, `fetched_at`, `last_seen_at`.
- Financials: `estimated_income`, `customer_paid_amount`, `merchant_income`, `original_amount`, `discount_amount`, `delivery_fee`, `package_fee`, `refund_amount`, `currency`.
- Customer/delivery: `customer_name`, `customer_phone_tail`, `privacy_phone`, `backup_phone`, `address`, `recipient_address`, `delivery_type`, `rider_name`, `rider_phone`, `remark`.
- Goods: `item_summary`, `item_count`, `items_json`.
- Source/debug: `source`, `source_profile`, `raw_text`, `raw_payload`, `ingest_batch_id`, `created_at`, `updated_at`, `deleted`.

PII note: phone/address fields are business-order data, but profile credentials are not. Do not store cookies, tokens, local profile paths, storage dumps, or browser logs in `raw_payload`.

## Validation Architecture

### Backend validation

- `SoftDeleteSchemaInitializer` creates `shop_orders` and indexes.
- `ShopOrderRepository` search query filters by shop, platform, status, customer keyword, order number, shop name, and `completed_at` range.
- `ShopOrderService.ingestBatch` rejects missing shop, missing platform order ID, and empty payloads.
- Re-ingesting the same `(shop_id, platform, platform_order_id)` updates the row and does not increase count.
- Non-super-admin users are rejected from list and ingest APIs unless an explicit crawler credential path is implemented.

### Crawler validation

- Parser test proves existing Meituan card text maps into the new ingestion DTO.
- Crawler supports `--backend-url`, `--token` or `ZR_BACKEND_TOKEN`, and `--system-shop-id`.
- Successful run logs count submitted/inserted/updated and still writes local latest JSON for diagnostics.

### Frontend validation

- `/shop-orders` route is `superAdminOnly`.
- Sidebar item and shop-list “店铺订单” action are visible only for super-admin.
- The order page accepts query params and initializes filters from them.
- Date-time picker uses minute precision and submits `completedStart` / `completedEnd`.
- From `/shops`, clicking “店铺订单” for a shop lands on `/shop-orders` with that shop ID and today's 00:00 to now.

## Research Complete

Phase 21 is plannable as one vertical admin feature with four implementation slices: backend schema/API, crawler ingestion, frontend order page/shop button, and intranet headed-browser verification.
