# Phase 23 Wave 2: TBWM PC 授权检测与订单采集 - Summary

**Date:** 2026-06-28
**Status:** Local implementation completed; crawler fetcher scripts reorganized under `admin/scripts/browser/fetcher`

## What Changed

- 后端远程后台模式新增 `tbwm -> https://melody.shop.ele.me/`，授权登录模式继续使用 `https://melody.shop.ele.me/login`。
- 授权探测脚本 `zr-auth-probe.js` 增加 `melody.shop.ele.me` 平台信号：
  - 商家后台信号：商家中心、工作台、订单管理、商品管理、门店、经营数据等。
  - 登录页信号：账号登录、验证码登录、手机号/密码/验证码、支付宝/淘宝登录等。
  - TBWM 登录页 Logo 可能带“饿了么商家中心”，所以登录表单信号优先于后台文案。
- 订单采集目标白名单从 `mtwm/jdms` 扩展为 `mtwm/jdms/tbwm`。
- 采集脚本已统一移动到 `admin/scripts/browser/fetcher`：
  - 统一调度入口：`crawl_authorized_shop_orders.py` / `crawl_authorized_shop_orders.sh`
  - 美团外卖：`mtwm_orders.py` / `mtwm_orders.js`
  - 京东秒送：`jdms_orders.py` / `jdms_orders.js`
  - 淘宝闪购饿了么：`tbwm_orders.py` / `tbwm_orders.js`
- 半小时调度脚本新增平台分发：
  - `mtwm` -> `mtwm_orders.py`
  - `jdms` -> `jdms_orders.py`
  - `tbwm` -> `tbwm_orders.py`
- 旧的 `admin/scripts/browser/run_authorized_meituan_orders.sh` 保留为兼容 wrapper，转发到 `fetcher/crawl_authorized_shop_orders.sh`。
- 新增 TBWM Node/CDP 采集器：
  - 通过 Phase 19 `/open` 以 `mode=remote-backend` 打开 `https://melody.shop.ele.me/`。
  - 优先监听 Ele.me 域名 JSON 响应，递归寻找订单对象数组。
  - 映射现有 Phase 21 入库字段：平台订单号、状态、下单/预计/完成/取消/退款时间、金额、顾客、地址、配送、骑手、商品、备注、业务 raw payload。
  - 按响应中暴露的店铺/门店 ID 过滤目标店铺；如果响应没有门店 ID，则依赖 profile 隔离，不伪造跨店判断。
  - 对登录失效、风控验证、明确空订单、parser no-match 分别输出状态。
  - `parser-no-match/unknown/login-expired/verification-required` 不提交成功，避免假阳性。
- 新增 TBWM parser/collector 单元测试，覆盖字段映射、跨店过滤、嵌套订单数组提取、空单/登录页状态和敏感字段过滤。

## Verification Completed

- `node --check admin/scripts/browser/fetcher/mtwm_orders.js admin/scripts/browser/fetcher/jdms_orders.js admin/scripts/browser/fetcher/tbwm_orders.js admin/scripts/browser/zr-auth-probe.js`
- `node admin/scripts/browser/fetcher/test_mtwm_orders.js`
- `node admin/scripts/browser/fetcher/test_jdms_orders.js`
- `node admin/scripts/browser/fetcher/test_tbwm_orders.js`
- `node admin/scripts/browser/test_zr_auth_probe.js`
- `PYTHONPYCACHEPREFIX=/tmp/zhirang-pycache python3 -m py_compile admin/scripts/browser/fetcher/crawl_authorized_shop_orders.py admin/scripts/browser/fetcher/mtwm_orders.py admin/scripts/browser/fetcher/jdms_orders.py admin/scripts/browser/fetcher/tbwm_orders.py admin/scripts/browser/fetcher/test_crawl_authorized_shop_orders.py`
- `PYTHONPYCACHEPREFIX=/tmp/zhirang-pycache python3 admin/scripts/browser/fetcher/test_crawl_authorized_shop_orders.py`
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -DskipTests package`
- `git diff --check`

## Blocked Verification

- Java unit tests were attempted twice:
  - normal Maven test command.
  - with `MAVEN_OPTS='-Djdk.attach.allowAttachSelf=true -XX:+EnableDynamicAgentLoading'`.
- Both failed before business assertions because Mockito inline Byte Buddy mock maker could not self-attach under the current JetBrains JDK 21 process sandbox.
- This summary records local code reorganization and verification only.
- Intranet deployment and live TBWM profile collection remain a separate operator action after syncing the new `fetcher/` directory.

## Required Follow-Up When SSH Is Available

1. Resolve or configure the local `zhirang-dev` SSH alias.
2. Use the remembered crawler route:
   `ssh ubuntu@zhirang-dev 'ssh ubuntu@192.168.0.210 ...'`
3. Query intranet admin DB for authorized `platform='tbwm'` 罗家臭豆腐 shop ID/platform shop ID.
4. Deploy backend only to `hewp@172.20.0.13`.
5. Sync crawler scripts to `/home/ubuntu/data/fetcher` on `ubuntu@192.168.0.210`, plus the compatibility wrapper `/home/ubuntu/data/run_authorized_meituan_orders.sh`.
6. Run `/home/ubuntu/data/fetcher/crawl_authorized_shop_orders.sh` once manually, or use `/home/ubuntu/data/run_authorized_meituan_orders.sh` for cron compatibility.
7. Query `shop_orders` for the TBWM system shop ID and platform `tbwm`.

## Environment Boundary

No production `zhirang-dev` online app deployment, upgrade, or restart was performed.
