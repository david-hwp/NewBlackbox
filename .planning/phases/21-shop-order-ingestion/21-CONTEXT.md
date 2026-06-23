# Phase 21: 店铺订单入库与超管查询 - Context

**Gathered:** 2026-06-24
**Status:** Ready for planning
**Source:** User request in `$gsd-plan-phase 21`

<domain>

## Phase Boundary

Phase 21 将 Phase 19 远端浏览器链路中的爬虫订单采集结果写入后台数据库，并给管理后台新增一个超管可见的“店铺订单”查询页面。爬虫采集到的订单数据必须关联系统店铺 `shops.id`，字段尽可能详细；管理后台店铺列表操作列新增“店铺订单”按钮，超管点击后跳转订单页并自动设置该店铺和当天时间范围筛选。

本 phase 不负责重做 Phase 19 授权/远程后台链路，不自动处理验证码，不实现平台经营动作，不向普通用户或渠道管理员开放订单查询。

</domain>

<decisions>

## Implementation Decisions

### D-21-01 Order Data Persistence
- 爬虫脚本采集到的订单数据必须入库，不能只写本地 JSON 文件。
- 数据必须关联后台 `shops.id`，并保留平台店铺 ID、店铺名称、平台、用户 ID、渠道 ID 的采集时快照。
- 订单表字段要尽可能详细，至少覆盖现有爬虫能解析出的平台订单号、页面序号、订单时间、完成时间、状态、预计收入、顾客、隐私号、备用号、电话尾号、地址、原始文本和采集时间。
- 入库必须幂等；同一店铺、同一平台、同一平台订单号重复采集时更新而不是新增重复行。

### D-21-02 Ingestion Contract
- 后端需要提供爬虫可调用的订单采集提交能力，或等价安全通道。
- 提交时必须校验系统店铺存在，并由后端从 `shops` 表补齐用户、渠道、平台和店铺快照，避免爬虫伪造归属字段。
- 入库 payload 必须允许扩展 raw JSON，便于后续补字段；但不能保存 cookie、token、浏览器 profile 路径或 storage dump 等凭据。

### D-21-03 Super Admin Order Page
- 管理后台新增“店铺订单”页面，只有超管能在导航中看到、通过路由访问、通过 API 读取。
- 页面需要常规筛选项和分页列表。常规筛选包括店铺 ID、店铺名称、平台、订单状态、顾客关键字、订单号。
- 页面还必须提供订单完成时间起止筛选，精确到分钟即可。

### D-21-04 Shop List Entry
- 店铺列表操作列新增文案为“店铺订单”的按钮。
- 该按钮只有超管可见，点击后跳转到“店铺订单”页面。
- 跳转时自动带上当前行的系统店铺 ID，并设置完成时间起止范围为当天 00:00 到点击时刻。
- 店铺列表操作列必须继续固定在右侧，不能破坏现有“远程后台”、编辑、删除按钮。

### D-21-05 Verification
- 完成后要能在内网开发测试管理后台验证，服务器信息沿用当前项目事实：`172.20.0.13` 是 Mac 内网开发测试管理后台服务器，SSH 用户 `hewp`。
- 不得在没有用户明确同意时部署或升级 `zhirang-dev` 线上应用环境。
- 需要使用有头浏览器自动化验证超管进入新页面、筛选、从店铺列表跳转的行为。

### the agent's Discretion
- 数据库表名、DTO 命名、API 路径、字段类型和爬虫鉴权方式由实现时根据现有后端风格选择。
- 订单完成时间从现有页面文本中可能无法总是准确区分，第一版可以用明确完成状态/送达时间解析结果优先，缺失时保留 nullable 并在列表筛选中只匹配有完成时间的订单。
- UI 不需要做复杂分析大盘；Phase 21 的页面是后台表格查询页。

</decisions>

<canonical_refs>

## Canonical References

Downstream agents MUST read these before planning or implementing.

### Planning
- `.planning/REQUIREMENTS.md` — Phase 21 requirement IDs `PH21-D01` through `PH21-D12`.
- `.planning/ROADMAP.md` — Phase 21 roadmap entry and environment boundaries.
- `.planning/phases/19-xpra-shop-authorization/19-PLAN.md` — Phase 19 authorization/remote browser standard plan index and environment facts.
- `.planning/phases/19-xpra-shop-authorization/19-04-PLAN.md` — Current admin “远程后台” route/button behavior and dev/test topology constraints.

### Backend
- `admin/backend/src/main/java/com/duodian/admin/entity/Shop.java` — system shop identity, user/channel/platform snapshots, authorization state.
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java` — current schema evolution pattern.
- `admin/backend/src/main/java/com/duodian/admin/service/PermissionService.java` — super-admin and admin/channel access gates.
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java` — shop list, authorization endpoints, and super-admin API examples.
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/PagedResponse.java` — pagination response contract.

### Frontend
- `admin/frontend/src/router/index.js` — route meta `superAdminOnly` pattern.
- `admin/frontend/src/views/Layout.vue` — super-admin-only sidebar entries.
- `admin/frontend/src/views/Shops.vue` — shop table filters, fixed operation column, and existing “远程后台” button.
- `admin/frontend/src/views/Logs.vue` — existing admin table/filter/pagination page pattern.
- `admin/frontend/src/utils/adminSession.js` — frontend role helpers and channel text helpers.

### Crawler
- `admin/scripts/browser/fetch_meituan_orders.py` — current Meituan order extraction script and field source.
- `admin/scripts/browser/zr-browser-control.py` — Phase 19 remote browser control service used by the crawler.

</canonical_refs>

<specifics>

## Specific Ideas

- Proposed backend table: `shop_orders`.
- Proposed unique key: `(shop_id, platform, platform_order_id, deleted)`.
- Proposed API paths:
  - `POST /shop-orders/ingest` for crawler batch ingestion.
  - `GET /shop-orders` for super-admin list query.
- Proposed frontend route: `/shop-orders`.
- Proposed shop list button text: `店铺订单`.
- Proposed default jump query from shop list:
  - `shopId=<shops.id>`
  - `completedStart=<today yyyy-MM-dd 00:00>`
  - `completedEnd=<current yyyy-MM-dd HH:mm>`

</specifics>

<deferred>

## Deferred Ideas

- Cross-platform order ingestion beyond the current Meituan crawler.
- Order analytics, reports, alerts, or charts.
- Non-super-admin access to order data.
- Automated order actions such as accepting, refunding, messaging, or printing.

</deferred>

---

*Phase: 21-shop-order-ingestion*
*Context gathered: 2026-06-24 via user-provided plan-phase requirements*
