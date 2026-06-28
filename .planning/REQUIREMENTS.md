# Requirements: zhirang-zhanghaoguanjia

**Defined:** 2026-06-24
**Core Value:** 管理多平台店铺账号、授权状态、迁移数据和运营数据，让管理员能在后台和 APP 中可靠地查看、维护和验证店铺能力。
**Scope Note:** This file was restored after earlier milestone requirements had been archived or omitted. It captures the active v1.3 requirements that still need GSD traceability, starting with Phase 20 and Phase 21.

## v1.3 Requirements

### Legacy Engine Migration

- [x] **PH20-D01**: 新包迁移必须只使用旧 engine 自导出和新 engine 自导入，不依赖 root、Magisk、adb 私有目录读取或新主包直连旧 engine AIDL。
- [x] **PH20-D02**: 旧 engine 数据迁移只能在新主包登录成功并加载当前用户店铺数据之后触发。
- [x] **PH20-D03**: 同一服务器用户的旧 engine 迁移必须有本地状态和服务端隐藏字段双重 one-shot 保护。
- [x] **PH20-D04**: 新 engine 导入必须按当前服务器用户和服务端店铺/cloneInstance 映射过滤，避免账号或店铺串数据。
- [x] **PH20-D05**: 旧导出 `.json` manifest 不能作为完成必需条件，稳定的最终 `.zip` 才是权威完成信号。
- [x] **PH20-D06**: 新 engine 导入器必须支持 Zip64 并拒绝 Zip Slip 路径。
- [x] **PH20-D07**: 管理后台不能展示或编辑隐藏的旧 engine 迁移完成字段。
- [ ] **PH20-D08**: release APK 必须使用 `com.zhirang.zhanghaoguanjia.new` 和 `com.zhirang.zhanghaoguanjia.new.engine`，并连接内网 API 环境。
- [x] **PH20-D09**: 小米 MIX 2S 验证必须证明迁移、旧店铺打开和同一用户不重复迁移。

### Shop Order Ingestion

- [x] **PH21-D01**: 爬虫脚本采集到的店铺订单数据必须入库，而不是只写本地 JSON 文件。
- [x] **PH21-D02**: 每条入库订单必须关联后台 `shops.id`，并保留平台店铺 ID、店铺名称、平台、用户和渠道快照，方便后续筛选与审计。
- [x] **PH21-D03**: 订单数据表字段必须尽可能详细地覆盖爬虫可采集信息，包括平台订单号、页面序号、订单时间、完成时间、状态、金额、顾客、联系方式、地址、商品和原始载荷。
- [x] **PH21-D04**: 订单入库必须按 `shop_id + platform + platform_order_id` 幂等 upsert，重复采集同一订单只更新最新字段和采集时间，不生成重复记录。
- [x] **PH21-D05**: 后端必须提供超管专用的订单列表 API，支持分页、常规筛选和按订单完成时间起止区间过滤，起止时间精确到分钟。
- [x] **PH21-D06**: 后端必须提供受控的订单采集入库 API 或等价安全通道，让爬虫脚本能提交订单批次，并拒绝无效店铺、无效订单号和越权请求。
- [x] **PH21-D07**: 管理后台必须新增“店铺订单”页面，只有超管可以访问和看到入口；非超管直接访问路由或 API 都应被拒绝。
- [x] **PH21-D08**: “店铺订单”页面必须展示分页订单列表和筛选栏，筛选项包括店铺 ID、店铺名称、平台、订单状态、顾客关键字、订单号以及订单完成时间起止区间。
- [x] **PH21-D09**: 店铺列表操作列必须增加超管可见的“店铺订单”按钮，点击后跳转到新页面并自动带上该店铺 ID、当天 00:00 到当前时刻的订单完成时间筛选条件。
- [x] **PH21-D10**: 店铺列表的操作列必须继续固定在右侧，新增“店铺订单”按钮后不能破坏已有“远程后台”、编辑、删除操作。
- [x] **PH21-D11**: 订单相关页面、API 和入库逻辑必须避免泄露授权 profile、cookie、token 或浏览器存储等敏感凭据；原始订单载荷只能保存业务订单内容。
- [x] **PH21-D12**: Phase 21 必须包含后端测试、爬虫解析/提交测试、前端构建，以及内网环境超管有头浏览器验证。

### Clone Identity Isolation

- [ ] **PH22-D01**: APP、engine 和服务端必须统一使用最小身份规则：服务端以当前 token 的 `userId + cloneInstanceId` 定位店铺卡片，本机以 `cloneInstanceId` 解析运行目录。
- [ ] **PH22-D02**: `localVirtualUserId` 必须降级为当前设备 engine 的本机目录号，不得再作为跨设备、跨账号或服务端店铺身份校验依据。
- [ ] **PH22-D03**: Phase 22 实现前必须用小米真机和 OPPO 真机当前真实数据验证兼容性，至少覆盖 `clone-instances.json`、scoped accounts 目录、runtime symlink、auth meta 和登录态 manifest。
- [ ] **PH22-D04**: `/shops/report` 必须通过 `AuthContext.userId + cloneInstanceId` 定位店铺；带 `cloneInstanceId` 的上报不得再按包名寻找任意 pending 店铺。
- [ ] **PH22-D05**: 已绑定真实平台店铺 ID 的 clone 不得被另一个平台店铺 ID 覆盖；检测到同一 clone 登录到不同店铺时必须拒绝并返回明确错误。
- [ ] **PH22-D06**: 登录态上传 manifest 必须携带并校验 `cloneInstanceId`，只允许写入同一 `userId + cloneInstanceId` 对应的店铺。
- [ ] **PH22-D07**: APP 必须保证店铺基础信息上报成功后才上传登录态，避免 `/shops/report` 失败但 `/login-state` 已覆盖服务端备份。
- [ ] **PH22-D08**: 历史 `shops.local_virtual_user_id`、授权 token、clone auth 和 scoped engine 目录必须保持兼容，不允许破坏既有可打开店铺。
- [ ] **PH22-D09**: 完成后必须在 OPPO 真机用二公子和贺伟平两个账号交替登录、刷新和打开店铺，验证店铺基础信息、登录态和 engine 目录不会互相覆盖；测试密码不得写入仓库。
- [ ] **PH22-D10**: APP 店铺卡片拖拽排序保存时必须只提交同一 `packageName + platform` 范围内的店铺 ID，京东秒送店铺不能因列表中混有其它平台而触发“一次只能调整同一平台下的店铺排序”。

### JD Order Ingestion

- [x] **PH23-D01**: 定时订单采集必须支持京东秒送 `jdms` 已授权店铺，不能只采集美团 `mtwm` 店铺。
- [x] **PH23-D02**: 京东订单采集必须复用 Phase 19 授权 profile 和 Phase 21 `/shop-orders/ingest` 入库契约，每条订单继续关联系统 `shops.id`。
- [x] **PH23-D03**: 京东采集器实现前必须先用真实已授权京东 profile 抓取订单页 DOM/API 样本，确认订单列表入口、字段来源、空订单状态和登录失效状态。
- [x] **PH23-D04**: 京东订单映射必须尽可能填充现有 `shop_orders` 字段，包括平台订单号、订单序号、状态、下单/完成/取消/退款时间、金额、顾客/地址/商品/配送信息和原始业务载荷。
- [x] **PH23-D05**: 京东重复采集必须按 `shop_id + platform + platform_order_id` 更新同一订单；订单状态从新下单、配送中到完成/取消/退款变化时不得新增重复行。
- [x] **PH23-D06**: 调度脚本必须支持按平台选择不同采集器，当前至少支持 `mtwm` 和 `jdms`，并继续跳过未授权、未知、失败或缺少用户手机号/profile ID 的店铺。
- [x] **PH23-D07**: 京东采集失败、登录态失效、页面结构变化或没有订单时必须写清楚诊断输出，但不能记录 cookie、token、profile 路径、浏览器存储或授权信号等凭据。
- [x] **PH23-D08**: Phase 23 必须包含京东 parser/collector 单元测试、调度器平台分发测试、后端目标列表测试，以及内网环境真实京东授权店铺的手动或自动化采集入库验证。
- [x] **PH23-D09**: 淘宝闪购饿了么 `tbwm` PC 远程后台授权检测必须支持 `melody.shop.ele.me`，已授权 profile 不能因为平台配置地址是登录页而误判未授权。
- [x] **PH23-D10**: `tbwm` 远程后台模式必须打开商家后台首页；用户选择授权时仍打开平台 PC 登录页，保持与 APP 授权流程一致。
- [x] **PH23-D11**: 订单采集目标接口必须返回已授权 `tbwm` 店铺，继续使用系统 `shops.id` 关联入库，并使用平台店铺 ID 或 `system-<id>` 作为 Phase 19 profile ID。
- [x] **PH23-D12**: 半小时定时脚本必须按平台分发到 `tbwm` 专用采集器，且不能破坏现有 `mtwm`、`jdms` 采集。
- [x] **PH23-D13**: `tbwm` 订单采集器必须复用 Phase 19 profile 和 Phase 21 `/shop-orders/ingest` 入库契约，重复采集同一订单时更新同一行。
- [x] **PH23-D14**: `tbwm` 订单映射必须尽可能填充现有订单字段，包括订单号、状态、下单/完成/取消/退款时间、金额、顾客/地址/商品/配送信息和原始业务载荷。
- [x] **PH23-D15**: `tbwm` 授权检测和订单采集日志/快照不得记录 cookie、token、请求头、浏览器存储、profile 路径、debug 端口或授权信号。
- [ ] **PH23-D16**: `tbwm` wave 必须完成本地测试，并在网络/SSH 可用时部署到内网 13 管理后台和 192.168.0.210 爬虫服务器，使用已授权饿了么罗家臭豆腐店铺做真实验证。

## v2 Requirements

### Order Analytics

- **PH21-V2-01**: 后续可以在订单入库基础上增加经营日报、复购分析、菜品排行和异常订单告警。
- [promoted to Phase 23] **PH21-V2-02**: 后续可以支持非美团平台订单采集器，但 Phase 21 只要求基于现有美团爬虫链路落地。
- **PH21-V2-03**: 后续可以为渠道管理员提供渠道范围订单统计，但 Phase 21 只开放给超管。

## Out of Scope

| Feature | Reason |
|---------|--------|
| 自动处理验证码或绕过平台风控 | Phase 21 只消费已授权 profile 能采集到的订单数据。 |
| 普通用户或渠道管理员查看订单 | 用户明确要求新页面和按钮都需要超管权限。 |
| 自动经营操作、接单、退款或修改订单 | 本 phase 只做采集、入库、查询展示。 |
| 存储平台 cookie/token/profile 数据 | 订单入库只保存业务订单字段和必要原始订单载荷，凭据仍留在 Phase 19 profile 链路。 |
| 秒级或毫秒级完成时间筛选 | 用户要求起止时间段精确到分钟即可。 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PH20-D01 | Phase 20 | Complete |
| PH20-D02 | Phase 20 | Complete |
| PH20-D03 | Phase 20 | Complete |
| PH20-D04 | Phase 20 | Complete |
| PH20-D05 | Phase 20 | Complete |
| PH20-D06 | Phase 20 | Complete |
| PH20-D07 | Phase 20 | Complete |
| PH20-D08 | Phase 20 | Pending |
| PH20-D09 | Phase 20 | Complete |
| PH21-D01 | Phase 21 | Complete |
| PH21-D02 | Phase 21 | Complete |
| PH21-D03 | Phase 21 | Complete |
| PH21-D04 | Phase 21 | Complete |
| PH21-D05 | Phase 21 | Complete |
| PH21-D06 | Phase 21 | Complete |
| PH21-D07 | Phase 21 | Complete |
| PH21-D08 | Phase 21 | Complete |
| PH21-D09 | Phase 21 | Complete |
| PH21-D10 | Phase 21 | Complete |
| PH21-D11 | Phase 21 | Complete |
| PH21-D12 | Phase 21 | Complete |
| PH22-D01 | Phase 22 | Pending |
| PH22-D02 | Phase 22 | Pending |
| PH22-D03 | Phase 22 | Pending |
| PH22-D04 | Phase 22 | Pending |
| PH22-D05 | Phase 22 | Pending |
| PH22-D06 | Phase 22 | Pending |
| PH22-D07 | Phase 22 | Pending |
| PH22-D08 | Phase 22 | Pending |
| PH22-D09 | Phase 22 | Pending |
| PH22-D10 | Phase 22 | Pending |
| PH23-D01 | Phase 23 | Complete |
| PH23-D02 | Phase 23 | Complete |
| PH23-D03 | Phase 23 | Complete |
| PH23-D04 | Phase 23 | Complete |
| PH23-D05 | Phase 23 | Complete |
| PH23-D06 | Phase 23 | Complete |
| PH23-D07 | Phase 23 | Complete |
| PH23-D08 | Phase 23 | Complete |
| PH23-D09 | Phase 23 Wave 2 | Complete |
| PH23-D10 | Phase 23 Wave 2 | Complete |
| PH23-D11 | Phase 23 Wave 2 | Complete |
| PH23-D12 | Phase 23 Wave 2 | Complete |
| PH23-D13 | Phase 23 Wave 2 | Complete |
| PH23-D14 | Phase 23 Wave 2 | Complete |
| PH23-D15 | Phase 23 Wave 2 | Complete |
| PH23-D16 | Phase 23 Wave 2 | Pending |

**Coverage:**
- v1.3 requirements: 46 total
- Mapped to phases: 46
- Unmapped: 0

---
*Requirements restored: 2026-06-24*
*Last updated: 2026-06-28 for Phase 23 Wave 2 TBWM planning*
