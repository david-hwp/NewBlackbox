# Phase 23 Wave 2 Verification

**Date:** 2026-06-28
**Scope:** TBWM PC authorization detection and scheduled order collector support

## Passed

```bash
node --check admin/scripts/browser/fetcher/mtwm_orders.js admin/scripts/browser/fetcher/jdms_orders.js admin/scripts/browser/fetcher/tbwm_orders.js admin/scripts/browser/zr-auth-probe.js
node admin/scripts/browser/fetcher/test_mtwm_orders.js
node admin/scripts/browser/fetcher/test_jdms_orders.js
node admin/scripts/browser/fetcher/test_tbwm_orders.js
node admin/scripts/browser/test_zr_auth_probe.js
PYTHONPYCACHEPREFIX=/tmp/zhirang-pycache python3 -m py_compile admin/scripts/browser/fetcher/crawl_authorized_shop_orders.py admin/scripts/browser/fetcher/mtwm_orders.py admin/scripts/browser/fetcher/jdms_orders.py admin/scripts/browser/fetcher/tbwm_orders.py admin/scripts/browser/fetcher/test_crawl_authorized_shop_orders.py
PYTHONPYCACHEPREFIX=/tmp/zhirang-pycache python3 admin/scripts/browser/fetcher/test_crawl_authorized_shop_orders.py
cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -DskipTests package
git diff --check
```

## Java Test Blocker

Attempted:

```bash
cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopControllerTest,ShopOrderServiceTest test
cd admin/backend && MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading' /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ShopControllerTest,ShopOrderServiceTest test
```

Result: both failed before assertions because Mockito inline Byte Buddy mock maker could not self-attach to the current JetBrains JDK 21 VM in this environment.

## Crawler Deployment Note

The crawler fetcher layout is now:

```text
admin/scripts/browser/fetcher/
  crawl_authorized_shop_orders.py
  crawl_authorized_shop_orders.sh
  mtwm_orders.py / mtwm_orders.js
  jdms_orders.py / jdms_orders.js
  tbwm_orders.py / tbwm_orders.js
```

Deploy by syncing `fetcher/` to `/home/ubuntu/data/fetcher` and keeping `/home/ubuntu/data/run_authorized_meituan_orders.sh` as the compatibility wrapper, or updating cron to `/home/ubuntu/data/fetcher/crawl_authorized_shop_orders.sh`.
