# Phase 23: 京东秒送订单采集入库 - Context

**Gathered:** 2026-06-27
**Status:** Ready for planning
**Source:** User request after Phase 21 scheduled crawler only collected Meituan authorized orders

<domain>

## Phase Boundary

Phase 23 extends the existing Phase 21 order ingestion pipeline from Meituan-only scheduled collection to include authorized JD 秒送 (`jdms`) shops. It must reuse the Phase 19 remote browser/profile chain, the Phase 21 `shop_orders` database table, `/shop-orders/ingest` API, and the existing super-admin order page.

This phase is not a new order-management product surface. It does not redesign `shop_orders`, does not add a separate JD order page, does not perform platform actions such as accepting or refunding orders, and does not bypass JD login/risk controls. It only consumes data visible to an already authorized JD merchant profile and persists business order data through the same backend contract used by Meituan.

</domain>

<decisions>

## Implementation Decisions

### D-23-01 JD as a First-Class Scheduled Crawl Platform

The scheduled authorized-order crawler must support `jdms` in addition to `mtwm`. The backend crawl-target list should return authorized shops for both supported platforms, and the scheduler should dispatch each target to the correct platform collector.

### D-23-02 Reuse Phase 21 Ingestion Contract

JD orders must be submitted to the existing `/shop-orders/ingest` endpoint. The payload uses system `shops.id` as `shopId`, and backend ingestion derives user/channel/platform/shop snapshots from the `shops` table. JD-specific fields should map into existing `ShopOrderIngestRequest.OrderItem` fields and sanitized `raw_payload`.

### D-23-03 Evidence Before Parser Finalization

The JD merchant backend page shape and network API must be inspected with a real authorized profile before final parser implementation. The first implementation task must capture sanitized DOM/text/network samples from an authorized JD shop, including the no-order case and login-expired case where feasible.

### D-23-04 Existing Order Table Semantics Stay the Same

The unique identity remains `shop_id + platform + platform_order_id`. Repeated JD crawls update the same row as order status changes. Nullable fields are allowed when JD does not expose a value in the list view, but later richer crawls must fill missing details without clearing existing non-empty fields.

### D-23-05 Diagnostics Without Credentials

Collector snapshots may contain sanitized page text, order-business JSON, counts, selected URL, and parser warnings. They must not include cookies, tokens, browser storage, profile paths, CDP debug ports, authorization probe signals, or request headers containing credentials.

### D-23-06 Environment Boundary

Implementation and verification target the intranet development/test admin backend on `hewp@172.20.0.13` and the crawler server `ubuntu@192.168.0.210` reachable through `ubuntu@zhirang-dev`. Do not deploy, upgrade, or restart the online `zhirang-dev` app environment without explicit user approval.

</decisions>

<canonical_refs>

## Canonical References

Downstream agents MUST read these before planning or implementing.

### Planning

- `.planning/REQUIREMENTS.md` - Phase 23 requirement IDs `PH23-D01` through `PH23-D08`.
- `.planning/ROADMAP.md` - Phase 21/22/23 roadmap context and environment boundaries.
- `.planning/phases/21-shop-order-ingestion/21-CONTEXT.md` - order ingestion scope and super-admin UI constraints.
- `.planning/phases/21-shop-order-ingestion/21-PLAN.md` - current order table/API/scheduler/frontend plan.
- `.planning/phases/21-shop-order-ingestion/21-SUMMARY.md` - shipped Phase 21 behavior and verification evidence.
- `.planning/phases/19-xpra-shop-authorization/19-PLAN.md` - remote browser/profile architecture.
- `.planning/phases/19-xpra-shop-authorization/19-05-PLAN.md` - PC remote backend authorization/opening behavior.

### Backend

- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java` - supported crawl platform set and ingestion semantics.
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopOrderController.java` - crawl targets and ingest endpoints.
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopRepository.java` - authorized crawl target query.
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/AuthorizedShopOrderCrawlTarget.java` - scheduler target shape.
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/ShopOrderIngestRequest.java` - collector payload contract.
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopOrderControllerTest.java`
- `admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java`

### Crawler

- `admin/scripts/browser/run_authorized_meituan_orders.py` - current scheduled target loading, filtering, cleanup, collector invocation, and ingest submission.
- `admin/scripts/browser/run_authorized_meituan_orders.sh` - current cron wrapper, lock, secret env, and logs.
- `admin/scripts/browser/fetch_meituan_orders_node_cdp.js` - current Playwright/CDP collector pattern.
- `admin/scripts/browser/zr-browser-control.py` - `/open` control API and profile/session isolation.
- `admin/scripts/browser/zr-auth-probe.js` - JD authorized-page signals for `https://store.jddj.com/`.
- `admin/scripts/browser/test_run_authorized_meituan_orders.py`
- `admin/scripts/browser/test_fetch_meituan_orders.py`

</canonical_refs>

<specifics>

## Specific Ideas

- Rename or generalize the scheduler from Meituan-specific naming toward authorized platform order collection while preserving backwards-compatible wrapper paths if cron already calls them.
- Add `fetch_jd_orders_node_cdp.js` or equivalent, following the existing Node/CDP collector pattern.
- JD opening URL should start from the authorized merchant home/order entry on `https://store.jddj.com/`, then navigate to the order list/history route discovered during sampling.
- The first parser can support list-visible fields only, but must keep sanitized raw order JSON/text so later detail enrichment can update the same rows.
- Candidate source values:
  - `source`: `fetch_jd_orders_node_cdp`
  - `ingestBatchId`: `jdms-<systemShopId>-<timestamp>`
  - `platform_order_id`: JD order ID/order number discovered from DOM or API.

</specifics>

<deferred>

## Deferred Ideas

- Taobao/Ele.me PC order collection.
- JD order detail deep crawling when list rows do not expose full customer/delivery/goods fields.
- Order analytics, reports, alerts, printing, or operational actions.
- Channel-admin or normal-user order access.
- Production rollout or production `zhirang-dev` app deployment.

</deferred>

---

*Phase: 23-jd-order-ingestion*
*Context gathered: 2026-06-27 from user request and Phase 21 code inspection*
