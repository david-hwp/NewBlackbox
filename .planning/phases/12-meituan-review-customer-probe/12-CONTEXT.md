# Phase 12: 美团差评与顾客信息采集固化 - Context

**Gathered:** 2026-06-11
**Status:** Ready for planning
**Source:** `$gsd-plan-phase 12 --auto` from active goal

<domain>
## Phase Boundary

本阶段目标是把当前探索中的美团外卖商家版差评采集能力产品化到 Bcore 引擎中：用户成功进入已登录的美团分身后，自动利用分身登录态产生的日志/缓存/页面请求信号获取差评列表，并尽可能关联顾客信息（用户Id、手机号或虚拟号），最终按平台和店铺Id写入引擎自有目录。

本阶段不负责重做账号管家登录、渠道系统、计费体系，也不要求绕过平台风控主动构造登录请求。接口调用原则上由已登录的分身 App 自己发起；引擎只监听、解析、扫描和落盘。

</domain>

<decisions>
## Implementation Decisions

### 设备与数据安全
- OPPO 真机含用户真实登录态，安全红线是不删除 OPPO 上任何数据：禁止 `rm`、`pm clear`、卸载、覆盖安装、写入/推送到 OPPO 私有目录。
- OPPO 用于只读调研和前台操作采集；Pixel7/模拟器用于安装 debug 引擎和验证固化实现。
- 代码改动在 worktree 中完成，当前 `dev` 工作区已有其他改动，不做回滚或破坏性清理。

### 自动采集触发
- 引擎必须在虚拟 App 生命周期中启动美团探针，触发点优先放在 `AppLifecycleCallback.beforeCreateApplication` 或等价引擎生命周期。
- 采集使用真实虚拟进程上下文中的 `packageName/processName/userId`，避免 wrapper 或主 APK 传错 `userId` 导致不上报。
- 不能依赖用户手动打开 `EngineReviewProbeActivity` 才采集；probe Activity 可保留为调试工具。

### 数据源优先级
- 第一优先级：解析 `mt_flutter_knb` 的 `setStorage` / `user_comment_list_data_key`，提取评论列表缓存。
- 第二优先级：解析 `mt_network` 中 `/gw/customer/comment/list`、`commScore=3`、评论相关接口信号。
- 第三优先级：扫描虚拟私有目录和外部目录中的 KNB/SQLite/缓存文件，作为延迟兜底。
- 顾客信息关联优先关注订单列表/订单详情、`orderViewId`、IM 权益、IM 会话和 `contactCustomerEntrance` 相关信号。

### 输出与隐私
- 输出目录固定在引擎自有目录，目标结构为 `review-data/{platform}/{shopId}/reviews_yyyy-MM-dd.jsonl`。
- 每条记录必须包含标准化评论字段、采集来源、虚拟 userId、平台、店铺Id、时间戳和 `customerInfo`。
- `customerInfo` 至少包含 `userId`、`phone`、`virtualPhone`、`orderViewId`、`source`、`associationStatus`、`evidence`。
- 必须过滤 token、cookie、session、authorization、ticket、ck、al 等凭据片段，不能把登录凭据写入结果文件。

### the agent's Discretion
- 具体解析器可以按现有 `NetworkProbeManager` / `ReviewDataCollector` / `VirtualCacheScanner` 扩展，或重构为更清晰的子模块。
- 顾客信息无法确定时，可以输出 `associationStatus=pending/no_match/ambiguous`，但必须说明可用证据和缺口。
- UI 自动化只用于调研/验证，不作为引擎生产逻辑的一部分。

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Current Investigation
- `docs/network-probe/PROBE_FINDINGS_20260610.md` — OPPO 真机差评页面入口、关键接口、KNB storage 和调研缺口。
- `docs/network-probe/ORDER_INTERFACE_RESEARCH.md` — 差评顾客信息关联路径、订单/IM 接口假设、当前实现状态。
- `docs/meituan_negative_reviews_20260610_132529.json` — 粉面先生历史差评输出样例。

### Existing Engine Probe Code
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/NetworkProbeBootstrap.kt` — 美团探针生命周期接入点。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/NetworkProbeManager.kt` — process/tag logcat 采集管理。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/MeituanWaimaiNetworkProbePolicy.kt` — 美团信号分类、店铺/订单/KNB 信号提取。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/ReviewDataCollector.kt` — 差评解析、去重、写入流程。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/CustomerInfoResolver.kt` — 顾客信息关联扩展点。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/VirtualCacheScanner.kt` — 虚拟目录缓存扫描。
- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/ReviewOutputManager.kt` — 引擎目录输出管理。
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineReviewProbeActivity.kt` — 历史调试 probe Activity。

### Clone / Shop Context
- `.planning/phases/09-clone-auth-billing/09-CONTEXT.md` — cloneInstanceId、本地虚拟 userId、授权 token 设计边界。
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt` — 店铺卡片进入分身和准备环境流程。
- `app/src/main/java/com/zhirang/zhanghaoguanjia/engine/EngineProxy.kt` — 主 APK 与引擎 AIDL 能力边界。

</canonical_refs>

<specifics>
## Specific Ideas

- OPPO 当前不是 root，也没有 `su`；历史 `review-probe` 文件已证明 userId=0 的粉面先生分身能解析 10 条差评，但顾客信息字段仍未填充。
- 非 root OPPO 的分身私有目录无法通过 adb shell 直接读取；如果要迁移登录态到 Pixel7，必须有 root、debuggable engine、应用内导出能力，或使用只读外部目录作有限验证。
- Pixel7 可安装 debug 引擎并使用 root 采集，但必须先确认目标分身目录确实含可用登录态。
- 当前差评列表中 `orderId=0`、`userId=0` 被脱敏，顾客信息需要从订单详情/IM 权益/联系顾客链路补充。

</specifics>

<deferred>
## Deferred Ideas

- 主动构造美团接口请求并复用 token 直接拉取数据：除非后续明确证明安全可控，否则本阶段不作为主路径。
- 逆向 native 网络库并 hook 完整响应体：作为后续增强，不阻塞本阶段先完成日志/缓存/页面触发采集。
- 后台服务端展示、上传和告警：本阶段只保证引擎本地目录稳定落盘。

</deferred>

---

*Phase: 12-meituan-review-customer-probe*
*Context gathered: 2026-06-11 via auto plan-phase*
