# Phase 12 Research: 美团差评与顾客信息采集固化

**Date:** 2026-06-11
**Mode:** Inline research based on current codebase, OPPO read-only evidence, and existing docs.

## Current Evidence

- OPPO 真机型号 `PECM30`，Android 12，`ro.build.type=user`，adb shell 是普通 shell，`su -c id` 无 root 输出。
- OPPO 已安装 `com.zhirang.zhanghaoguanjia` 和 `com.zhirang.zhanghaoguanjia.engine`，版本均为 `1.2.12-release`。
- OPPO 外部目录存在历史 probe 输出：`/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/review-probe/*.json`。
- 只读拉取到本地的历史结果显示：粉面先生（龙华店）`wmPoiId=15397100`、`userId=0`，多次成功解析 10 条差评。
- 历史差评样例字段包含 `commentId`、`wmPoiId`、`userName`、`comment`、`orderDetails`、`scores`、`reportStatus`，但没有真实 `orderId`、真实顾客 `userId`、手机号或虚拟号。

## Key Technical Findings

### 1. 差评列表可从 KNB storage / cache 获取

已有 `EngineReviewProbeActivity` 和 `MeituanReviewProbe` 能扫描虚拟私有目录，识别 `user_comment_list_data_key`，并提取 1-2 星评论。历史 JSON 已证明该路径在 OPPO 已登录态可用。

需要产品化的问题不是“能否解析差评”，而是：

- 自动触发时机不应依赖调试 Activity。
- 当前扫描主要依赖指定 `userId`；主 APK/wrapper 传错时会采不到。
- 输出目录需要统一到引擎自有系统目录，而不是临时 review-probe 调试文件。

### 2. 顾客信息不在评论列表中直接暴露

调研文档和样例表明 `/gw/customer/comment/list?commScore=3` 返回的 `orderId` 与 `userId` 常为 0，评论用户名可能匿名化。顾客信息需要通过其他信号补齐。

可行信号：

- `orderViewId`：订单增量同步日志、订单列表接口、订单详情接口可能携带。
- IM 权益/会话：`/gw/customer/comment/im/realtime/rights`、`/gw/api/im/rights/`、`api.neixin.cn/msg/api/chat/` 可能携带联系顾客权限、用户标识或虚拟号。
- 评论字段：`contactCustomerEntrance`、`showOrderInfo`、`showOrderInfoTime` 可作为是否可联系/可查看订单的辅助证据。
- 时间与商品匹配：差评 `createTime` + `orderDetails` 可与订单列表做弱关联，但存在歧义。

### 3. OPPO 私有数据不能直接迁移到 Pixel7

OPPO 无 root，`run-as com.zhirang.zhanghaoguanjia.engine` 失败过，因为 release 包不可 debuggable。adb 只能安全读取外部目录，无法读 `/data/user/0/com.zhirang.../blackbox/data/user/...` 私有登录态。

因此 Pixel7 验证必须二选一：

- 用 OPPO 前台现有登录态做只读实测，收集日志/外部结果。
- 让 Pixel7 自己建立可用登录态或通过未来应用内导出能力迁移，不能依赖 adb 直接拉 OPPO 私有目录。

### 4. 当前代码骨架已接近可固化

worktree 中已存在以下模块：

- `NetworkProbeBootstrap` 生命周期安装。
- `NetworkProbeManager` process-scoped / tag-scoped logcat 读取。
- `MeituanWaimaiNetworkProbePolicy` 信号分类和敏感字段过滤。
- `ReviewDataCollector` 差评解析、去重和输出。
- `CustomerInfoResolver` 顾客关联预留。
- `VirtualCacheScanner` 虚拟目录扫描。
- `ReviewOutputManager` 结果落盘。

主要缺口：

- `CustomerInfoResolver` 目前基本只输出 pending，没有实际解析 userId/phone/virtualPhone。
- `ReviewDataCollector` 对 IM/订单响应的处理还偏记录信号，未形成可关联的顾客索引。
- `VirtualCacheScanner` 主要扫私有目录，需要确认是否也扫描 external data 作为调试/迁移兜底。
- 需要测试覆盖敏感字段过滤和多来源关联。

## Architecture Recommendation

采用“被动采集 + 多来源证据合并”架构：

1. 分身进程启动后，`NetworkProbeBootstrap` 按真实 `userId` 启动采集。
2. `NetworkProbeManager` 捕获进程内 `mt_network`、`mt_flutter_knb`、IM/订单相关日志，并写入结构化 `ProbeRecord`。
3. `ReviewDataCollector` 从 `comment_cache/comment_list_negative_api` 解析差评。
4. `CustomerInfoResolver` 维护按 `shopId` 分组的临时索引：
   - `commentId -> review`
   - `orderViewId -> order/customer candidate`
   - `im session/right -> customer candidate`
   - `time window + orderDetails -> weak candidate`
5. 输出时将强匹配、弱匹配和未匹配都写清楚，避免把猜测当事实。

## Validation Strategy

### Unit Tests

- `MeituanWaimaiNetworkProbePolicyTest`
  - 分类 `/gw/customer/comment/list?commScore=3`
  - 提取 `wmPoiId`、`orderViewId`、comment id
  - 过滤 cookie/token/session/ck/al
- `ReviewDataCollectorTest`
  - 从 KNB `setStorage` 提取差评
  - 去重 append
  - 输出 `customerInfo` schema
- `CustomerInfoResolverTest`
  - 从订单/IM 权益样例解析 userId/phone/virtualPhone
  - 强匹配、弱匹配、歧义和 pending 状态。
- `VirtualCacheScannerTest`
  - 扫 KNB/SQLite/外部目录样例。

### Device Verification

- OPPO：只读/前台操作，进入粉面先生分身 -> 店铺 -> 顾客评价 -> 差评/联系顾客，保存本地 Mac logcat 与截图/XML，不删除任何 OPPO 数据。
- Pixel7：安装 debug 引擎，使用可用登录态分身，进入相同链路，检查引擎目录 `review-data/meituan-waimai/{shopId}/reviews_*.jsonl`。

## Risks

- 美团 release 版 logcat 可能只输出请求不输出响应；此时需要更多依赖 KNB storage 或缓存扫描。
- 虚拟号可能只在点击“联系顾客”后短暂出现，并可能受平台权限/有效期限制。
- 订单列表与差评通过时间/商品匹配有歧义，必须标注 `associationStatus=ambiguous`。
- OPPO 无 root 限制私有数据迁移，不能承诺从 OPPO 到 Pixel7 完整迁移登录态，除非后续引入应用内导出或获得 root/debuggable 包。

## Research Complete

Phase 12 可以进入计划执行。优先级是先把现有探针固化为自动运行和可靠输出，再补顾客信息关联解析，最后用 OPPO/Pixel7 做端到端验证。
