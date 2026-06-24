---
phase: 21
summary: 21-SUMMARY
status: completed
completed_at: "2026-06-24T19:16:00+08:00"
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
deployed_to:
  - hewp@172.20.0.13:/Users/hewp/Projects/personal/zhirang-zhanghaoguanjia/admin
  - http://172.20.0.13:8006
production_zhirang_dev_deployed: false
---

# Phase 21 Summary: Shop Order Ingestion

## Delivered

- Added `shop_orders` persistence with detailed order identity, shop/user/channel/platform snapshots, order timing, status, financials, customer/delivery fields, item fields, and sanitized raw business payload.
- Added idempotent backend ingestion keyed by `shop_id + platform + platform_order_id + deleted`; duplicate crawler submissions update existing rows.
- Added super-admin-only APIs:
  - `POST /api/shop-orders/ingest`
  - `GET /api/shop-orders`
- Added minute-precision completed-time filtering for order list queries.
- Updated `fetch_meituan_orders.py` so Meituan crawler output can still write local JSON and can also submit batches to the admin backend.
- Added `ShopOrders.vue`, `/shop-orders`, super-admin sidebar entry, and the shop-list operation button text `店铺订单`.
- Kept the shop-list operation column fixed right and preserved existing `远程后台`, `编辑`, and `删除` actions.
- Deployed only the intranet development/test admin environment on `hewp@172.20.0.13`.

## Verification

Local verification:

- `git diff --check`: passed.
- `/tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test`: passed, 8 tests.
- `python3 -m py_compile admin/scripts/browser/fetch_meituan_orders.py admin/scripts/browser/test_fetch_meituan_orders.py`: passed.
- `python3 admin/scripts/browser/test_fetch_meituan_orders.py`: passed.
- `cd admin/frontend && npm run build`: passed.
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -DskipTests package`: passed.

Intranet deployment verification:

- Ran `SKIP_ADMIN_BUILD=1 ./deploy.sh` on `hewp@172.20.0.13`.
- Docker services were recreated for admin backend/frontend; MySQL remained on existing data volume.
- Backend startup created/validated `shop_orders` and indexes.
- `curl http://172.20.0.13:8006/api/shop-orders` without token returned `401`.
- GStack headed browser used the existing super-admin session for user role `SUPER_ADMIN`.
- Inserted controlled Phase 21 UAT order for system shop `194` (`极点披萨`) through `/api/shop-orders/ingest`.
- Queried `/api/shop-orders?shopId=194&completedStart=2026-06-24 00:00&completedEnd=2026-06-24 19:11`; inserted row was returned.
- Clicked `店铺订单` from the `极点披萨` row in `/shops`; browser landed on `/shop-orders?shopId=194&completedStart=2026-06-24+00:00&completedEnd=2026-06-24+19:11`.
- Order page displayed `极点披萨`, the Phase 21 UAT order ID, customer, amount, item summary, and completed-time filters.
- DOM verification showed the shop-list operation header class `el-table-fixed-column--right` and row buttons `远程后台`, `店铺订单`, `编辑`, `删除`.
- Invalid ingest payload returned `code=400` with message `缺少系统店铺ID`.
- DB verification showed UAT raw payload had no `cookie`, `token`, or `authorization`, while non-sensitive business payload fields remained.

## Notes

- Live Meituan crawling was not forced during this phase; the authorized profile chain remains Phase 19 infrastructure. The implemented crawler path now submits extracted orders when supplied `ZR_BACKEND_URL`, `ZR_BACKEND_TOKEN`, and `ZR_SYSTEM_SHOP_ID`.
- No online `zhirang-dev` app deployment, upgrade, or restart was performed.
