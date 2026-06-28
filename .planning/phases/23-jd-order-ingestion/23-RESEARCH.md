# Phase 23: 京东秒送订单采集入库 - Research

**Date:** 2026-06-27
**Status:** Complete

## Research Question

What has to change so the existing half-hour scheduled order ingestion pipeline collects authorized JD 秒送 shops, not only Meituan shops?

## Key Findings

### 1. Phase 21 backend and UI are already platform-capable

The `shop_orders` schema and `ShopOrderIngestRequest` are not Meituan-specific. They store `platform`, `platform_name`, `platform_shop_id`, platform order IDs, status/time/amount/customer/delivery/goods fields, and sanitized raw payload. Ingestion derives the platform from the backend `Shop`, so a JD payload submitted for a `jdms` shop will be stored as JD without adding a new endpoint.

Planning implication: Phase 23 should not create a separate JD table or page. The main backend change is crawl-target eligibility, not a schema rewrite.

### 2. Current target selection excludes JD in two places

`ShopOrderService` has `SUPPORTED_ORDER_CRAWL_PLATFORMS = Set.of("mtwm")`. The scheduler `run_authorized_meituan_orders.py` also filters targets with `target.get("platform") == "mtwm"`. Existing tests assert JD is rejected.

Planning implication: support must be added both server-side and scheduler-side. Server can return `mtwm` and `jdms`; scheduler must dispatch by platform and test that JD is accepted.

### 3. The current scheduler is structurally reusable

`run_authorized_meituan_orders.py` already handles backend token auth, target loading, per-shop output directories, one-week cleanup, collector process execution, payload submission, and aggregate counts. Those behaviors are platform-independent except for script naming and the hard-coded Meituan collector.

Planning implication: generalize the scheduler rather than duplicating it. Keep the existing wrapper working for cron compatibility, but introduce platform collector mapping such as `mtwm -> fetch_meituan_orders_node_cdp.js` and `jdms -> fetch_jd_orders_node_cdp.js`.

### 4. The Node/CDP collector pattern is the right starting point

`fetch_meituan_orders_node_cdp.js` uses `/open` to start the right profile, connects to the returned CDP port, navigates to a platform URL, extracts visible order cards, writes local snapshots and a backend ingest payload. This matches what JD needs, and it reuses the Phase 19 profile isolation facts.

Planning implication: create a JD-specific Node/CDP collector with the same output contract:

- stdout final line JSON includes payload path, order count, labels/counts, and diagnostics.
- local snapshot under `order-output/shop-<systemShopId>/`.
- payload JSON with `shopId`, `source`, `ingestBatchId`, and `orders`.

### 5. JD page/API shape must be discovered from a live authorized profile

The recently fixed JD PC authorization probe proved that the authorized profile reaches `https://store.jddj.com/` and displays merchant-home text including `商家首页`, `订单管理`, `商品管理`, `全部门店`, and `今日有效订单`. It did not inspect the order-list route or API payload shape.

Planning implication: the first implementation task must use the authorized JD profile to capture sanitized page text, visible links/routes, and network responses related to order lists. The parser should be based on observed evidence, not guessed selectors.

### 6. Ingestion update semantics already cover order-status changes

`ShopOrderService.ingestBatch` finds existing rows by `shop_id + platform + platform_order_id`, then sets mutable state fields such as `status`, `fetchedAt`, `lastSeenAt`, and timestamp fields when present. It uses `setIfPresent`, which preserves old non-empty details when later payloads omit a field.

Planning implication: JD collector only needs to produce a stable platform order ID and current status/time fields. The existing service should update the same row as status evolves from newly placed to completed/cancelled/refunded.

### 7. Credential hygiene is already enforced but collector snapshots still matter

Backend raw payload sanitization strips sensitive key names such as cookie, token, authorization, storage, profile, debugPort, and shopAuthorizationSignals. However collectors must still avoid writing these fields to local output and logs.

Planning implication: JD collector diagnostics should keep sanitized DOM text/order JSON and parser warnings only. Tests should assert raw payload excludes credential-like fields before submission.

## Proposed Implementation Shape

1. Capture JD samples with an authorized profile:
   - open `https://store.jddj.com/`.
   - inspect order-related menu routes and network responses.
   - save sanitized sample fixtures into tests if small and safe.

2. Generalize scheduled collection:
   - rename internals to authorized platform orders.
   - target filter accepts `mtwm` and `jdms`.
   - per-platform collector script mapping.
   - logs include platform and system shop ID, never token/profile/debug details.

3. Add JD collector:
   - navigate to discovered JD order page/API.
   - parse list-visible order fields and optional details.
   - produce Phase 21 ingest payload.
   - support no-order and login-expired diagnostics.

4. Verify end to end on intranet:
   - backend target list returns authorized JD shop 76 when it is `AUTHORIZED`.
   - direct scheduler run writes JD payload and posts to `/shop-orders/ingest`.
   - `/shop-orders?shopId=76&platform=jdms` shows rows if live orders exist, or a documented no-order outcome if the platform has no today orders.

## Research Complete

Phase 23 is feasible as a focused extension of Phase 21: backend crawl-target eligibility, platform-dispatch scheduler, JD CDP collector/parser, and intranet live verification.
