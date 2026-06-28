# Phase 23: 京东秒送订单采集入库 - Summary

**Completed:** 2026-06-27
**Status:** Implemented with live intranet verification and one documented platform-risk limitation

## What Shipped

- 后端 `/shop-orders/crawl-targets` 支持返回已授权 `jdms` 店铺，保留原有 `mtwm` 行为。
- 定时脚本 `run_authorized_meituan_orders.py` 从美团单平台过滤改为平台分发：
  - `mtwm` -> `fetch_meituan_orders_node_cdp.js`
  - `jdms` -> `fetch_jd_orders_node_cdp.js`
- 新增京东秒送 Node/CDP 采集器：
  - 使用 Phase 19 `/open` profile 链路打开京东商家后台。
  - 默认进入 `https://store.jddj.com/plus/order/all`。
  - 监听结构化接口 `dsm.o2o.order.cater.pcAllOrderListQuery`。
  - 通过页面分页控件采集今日订单页。
  - 按 `basicVo.stationNo == ZR_SHOP_ID` 过滤，避免京东“全部门店”默认视图串店。
  - 输出 `jd_orders_<systemShopId>_<timestamp>.json`、latest snapshot 和 `jd_ingest_payload.json`。
- JD parser 映射现有 Phase 21 入库字段：平台订单号、页面序号、状态、下单/预计/完成/取消时间、收入、顾客、地址、配送、骑手、商品、备注、业务 raw payload。
- JD 采集器和后端 raw payload 均避免写入 cookie、token、authorization、profile、debugPort、storage 等敏感凭据。
- JD 采集不完整时会失败退出，不提交部分分页 payload，避免把平台风控/页面结构异常误报为成功。

## Live Evidence

- 已授权 JD profile：`phone=15200837196`, system shop `76`, platform shop `16081572`。
- 真实采样确认：
  - 京东商家首页存在 `订单管理`、`今日待办`、`全部订单`。
  - `今日待办` 路由为 `https://store.jddj.com/plus/order/index`。
  - `全部订单` 路由为 `https://store.jddj.com/plus/order/all`。
  - 订单列表结构化响应来自 `https://sff.jddj.com/api` 的 `pcAllOrderListQuery`。
- 单独 collector 验证曾完整采集 4 页：`rawCount=34`, `target orders=7`, `capturedPages=[1,2,3,4]`, no sensitive leak。
- 手动调度入库验证：
  - 目标列表：`{'total': 4, 'platforms': {'mtwm': 3, 'jdms': 1}, 'jdTargets': [76]}`。
  - 调度日志：`platform=jdms shop=76 ok ... received=6 inserted=6 updated=0 rejected=0`。
  - 内网 DB：`shop_id=76, platform=jdms, count=6`。

## Important Limitation

京东当前 profile 在连续翻页采集时触发了平台风控页：

- URL 跳到 `https://cfe.m.jd.com/privatedomain/risk_handler/...`
- 页面文案为 `验证一下，购物无忧 快速验证`

为避免错误提交部分数据，采集器现在会在预期分页未采齐时抛错并停止提交。现有已入库的 6 条 JD 订单保留；后续 cron 遇到同类风控会记录失败，不会覆盖或清空已有订单。

后续优化方向：

- 通过京东顶部“全部门店”选择器先切换到目标门店，减少分页数量。
- 或增加更温和的分页节奏/人工处理风控后的恢复流程。
- 不做自动绕过验证码或平台风控。

## Deployment

- 内网管理后台：`hewp@172.20.0.13`
  - 同步了 Phase 23 后端 service/test、脚本和本地预构建 jar。
  - 使用 `SKIP_ADMIN_BUILD=1 ./deploy.sh` 重建内网后端容器。
  - 前端使用已有 dist，未重新编译前端功能。
- 爬虫服务器：`ubuntu@192.168.0.210`，通过 `ubuntu@zhirang-dev` 跳转。
  - 同步到 `/home/ubuntu/data`：
    - `run_authorized_meituan_orders.py`
    - `test_run_authorized_meituan_orders.py`
    - `fetch_jd_orders_node_cdp.js`
    - `test_fetch_jd_orders_node_cdp.js`
  - 现有半小时 cron 继续调用 `/home/ubuntu/data/run_authorized_meituan_orders.sh`。

## Verification

- Backend:
  - `ShopOrderControllerTest`, `ShopOrderServiceTest`: 15 tests passed.
- Scheduler:
  - Python compile passed.
  - `test_run_authorized_meituan_orders.py` passed.
- JD collector:
  - `node --check fetch_jd_orders_node_cdp.js` passed.
  - `test_fetch_jd_orders_node_cdp.js` passed locally and on crawler server.
- Packaging:
  - `admin/backend` package with `-DskipTests` passed.
- Hygiene:
  - `git diff --check` passed.

## Environment Boundary

No production `zhirang-dev` online app deployment, upgrade, or restart was performed.
