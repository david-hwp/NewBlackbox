# Phase 23 Verification

**Date:** 2026-06-27

## Local Commands

```bash
node --check admin/scripts/browser/fetch_jd_orders_node_cdp.js
node admin/scripts/browser/test_fetch_jd_orders_node_cdp.js
python3 -m py_compile admin/scripts/browser/run_authorized_meituan_orders.py admin/scripts/browser/test_run_authorized_meituan_orders.py
python3 admin/scripts/browser/test_run_authorized_meituan_orders.py
cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopOrderControllerTest,ShopOrderServiceTest test
cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -DskipTests package
git diff --check
```

Result: all passed.

## Crawler Server Commands

```bash
cd /home/ubuntu/data
NODE_PATH=/home/ubuntu/data/browser/node_modules node --check fetch_jd_orders_node_cdp.js
NODE_PATH=/home/ubuntu/data/browser/node_modules node test_fetch_jd_orders_node_cdp.js
python3 -m py_compile run_authorized_meituan_orders.py test_run_authorized_meituan_orders.py
python3 test_run_authorized_meituan_orders.py
```

Result: all passed.

## Live JD Verification

Target:

- system shop: `76`
- platform: `jdms`
- platform shop: `16081572`

Observed JD order source:

- route: `https://store.jddj.com/plus/order/all`
- API: `dsm.o2o.order.cater.pcAllOrderListQuery`
- structured list: `result.orderPage.resultList`
- target shop filter: `basicVo.stationNo == "16081572"`

Successful collector sample:

- captured pages: `1,2,3,4`
- raw orders: `34`
- target shop orders: `7`
- sensitive leak check: false

Manual scheduler run after intranet backend deployment:

- crawl targets: `mtwm=3`, `jdms=1`
- JD target: `76`
- JD ingest response: `received=6`, `inserted=6`, `updated=0`, `rejected=0`
- DB result: `shop_orders` has `6` rows for `shop_id=76 AND platform='jdms' AND deleted=0`

## Risk Finding

After repeated live paging tests, JD redirected the profile to a risk verification page:

- URL family: `https://cfe.m.jd.com/privatedomain/risk_handler/...`
- page text: `验证一下，购物无忧 快速验证`

The collector now treats incomplete pagination as a hard failure before stdout payload reporting, so the scheduler does not submit partial JD data when this happens.
