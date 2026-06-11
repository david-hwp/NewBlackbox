# Phase 12 标准：美团差评与订单关联

日期：2026-06-11
状态：draft standard
依据：`MeituanOrderAssociationStore`

## 结论

当前差评和订单的关联，不是根据骑手、配送员、配送状态判断，也不是根据“这次手机里抓到了哪些订单”直接判断。

正式命中必须同时满足：

- 历史订单覆盖完整。
- 差评有可解析的 `createTime`。
- 差评暴露了菜品信号：`orderDetails[].foodName` 或 `criticFoodList[]`。
- 订单日期落在差评日前 3 天到差评当天。
- 菜品文本匹配得分达标。
- 第一候选比第二候选有足够分差。

OPPO 生成的 13 条订单数据只是过滤后的候选订单样本，不是“已经关联上的差评订单”。

## 差评定义

评论满足以下任一条件即进入差评集合：

- `orderCommentScore` 在 `1..2`
- `productScore` 在 `1..2`
- `tasteScore` 在 `1..2`

这与当前 `ReviewDataCollectorTest` 和 `VirtualCacheScannerTest` 中的提取逻辑一致。

## 历史订单覆盖标准

不能假设手机当前订单缓存覆盖了所有差评可关联订单。正式关联前，必须先补齐历史订单。

覆盖范围：

```text
start = 最早差评 createTime - REVIEW_ORDER_LOOKBACK_DAYS
end = 今天
REVIEW_ORDER_LOOKBACK_DAYS = 3
```

如果覆盖不完整，不能输出 `matched`，必须输出：

```json
{
  "associationStatus": "pending_coverage",
  "note": "history orders must be pulled from earliest negative review coverage window before association"
}
```

实现入口：`MeituanOrderAssociationStore.enrichReview(...)` 会先调用 `MeituanHistoryOrderTrigger.reviewCoverageStatus(...)`，覆盖不完整时直接返回 `pending_coverage`。

## 通过探针获取美团订单数据

订单数据由引擎内置探针在美团外卖商家版分身进程内获取，不能从宿主侧直接构造带登录态的 HTTP 请求。

启动链路：

1. `EngineApp.attachBaseContext(...)` 调用 `NetworkProbeBootstrap.install(...)`。
2. `NetworkProbeBootstrap` 注册 `AppLifecycleCallback`，在美团分身创建应用前启动探针。
3. `BActivityThread.onBeforeCreateApplication(...)` 对 `com.sankuai.meituan.meituanwaimaibusiness` 自动执行：
   - `NetworkProbeManager.start(...)`
   - `MeituanOrderResponseHook.install(...)`
   - `MeituanHistoryOrderTrigger.maybeStart(...)`
   - `MeituanPrivateInfoTrigger.maybeStart(...)`
   - 已知店铺 ID 时调度 `MeituanOrderAssociationStore.scheduleResponseIndexBackfill(...)`

探针有两条数据通道：

- logcat 通道：`NetworkProbeManager` 启动进程级和 tag 级 logcat reader，监听 `mt_network`、`mt_flutter_knb`、`WMCoreJSBridge`、`WMFlutter` 等 tag，把相关业务信号写入普通探针日志。
- mt_network 响应 hook 通道：`MeituanOrderResponseHook` 通过美团自己的 `com.sankuai.mtnetwork.IFlutterRequestInterceptor` 捕获成功响应体。订单关联以这个通道为准，因为它能拿到订单列表/详情的响应 JSON。

普通探针日志落盘路径：

```text
network-probe/meituan-waimai/{shopId}/{yyyy-MM-dd}.jsonl
```

响应 hook 落盘路径：

```text
network-probe/meituan-waimai/{shopId}/responses/responses_{yyyy-MM-dd}.jsonl
```

响应文件每行是一个 `network_response` 记录，至少包含：

- `timestamp`
- `timestampMs`
- `platform`
- `shopId`
- `packageName`
- `processName`
- `userId`
- `payload.url`
- `payload.requestBody`
- `payload.requestParams`
- `payload.requestHeaderKeys`
- `payload.response`

`requestHeaderKeys` 只保留 header key 列表，不输出 cookie、token、authorization 等敏感值。

订单响应 hook 当前接受这些目标 URL：

- `/gw/api/unified/r/order/`
- `api/order/v5/privacy/get`
- `/gw/customer/comment/`
- `/gw/api/im/rights/`
- `/api/im/rights/`

订单索引只从以下响应中解析订单：

- `/gw/api/unified/r/order/list/page/history`
- `/gw/api/unified/r/order/detail`

历史订单主动补齐方式：

1. 差评数据落盘后，`ReviewDataCollector` 会调用 `MeituanHistoryOrderTrigger.ensureCoverageFromReviews(context, shopId)`。
2. 触发器根据最早差评日期生成 `history-trigger.json`：

```text
network-probe/meituan-waimai/history-trigger.json
```

3. 美团主进程启动并且 mt_network 类可用后，`MeituanHistoryOrderTrigger` 通过美团自己的 mt_network Flutter plugin 发请求，不导出登录凭据。
4. 请求接口：

```text
POST https://eapi.waimai.meituan.com/gw/api/unified/r/order/list/page/history
```

5. 每个日期按 `allOrder` 分页拉取，分页参数默认：

```text
pageSize = 10
maxPagesPerDay = 80
```

6. 每个日期额外拉取 `cancelOrder` 第 1 页，用于覆盖取消订单入口。
7. 每页响应由 `MeituanOrderResponseHook` 捕获并写入 `responses_yyyy-MM-dd.jsonl`。
8. `MeituanOrderAssociationStore.recordNetworkResponse(...)` 实时解析响应；也可以后续用 `indexExistingResponses(context, shopId, rebuild)` 重新扫描响应文件。

历史订单触发器的进度日志路径：

```text
network-probe/meituan-waimai/history-trigger/{runId}.jsonl
```

进度中会记录：

- `start`
- `page`
- `date_complete`
- `date_incomplete`
- `association_backfill`
- `complete`
- `incomplete`
- `fatal`

订单索引落盘路径：

```text
network-probe/meituan-waimai/{shopId}/orders/order-index.jsonl
```

`order-index.jsonl` 中每行是一个解析后的订单记录，核心字段包括：

- `orderViewId`
- `shopId`
- `orderDate`
- `orderTimeFmt`
- `wmUserId`
- `recipientPhone`
- `recipientPhoneShow`
- `virtualPhone`
- `virtualPhoneShow`
- `privacyPhoneStatus`
- `isUsePrivacyPhone`
- `privateInfoStatus`
- `privateInfoMessage`
- `dayseq`
- `foodNames`
- `searchText`
- `source`
- `timestampMs`

订单解析规则：

- 响应体必须是 JSON。
- 从 `data.orderList[]` 遍历订单。
- `commonInfo` 和 `orderInfo` 是字符串 JSON，需要再次解析。
- 订单号优先取 `commonInfo.wm_order_id_view`，再取 `commonInfo.wmOrderViewId`，最后从原始文本中正则提取。
- `orderDate` 优先从请求体 `startDate=yyyy-MM-dd` 提取；否则从 `poiPushDay` 格式化。
- 菜品名从 `orderInfo` 递归收集，写入 `foodNames`，并生成归一化 `searchText`。
- 顾客和隐私号字段从 `orderInfo` 中按 key 模糊提取。

历史订单覆盖完成后，必须执行：

- `MeituanOrderAssociationStore.indexExistingResponses(context, shopId)`：把响应文件补索引到 `order-index.jsonl`。
- `MeituanOrderAssociationStore.backfillReviewFiles(context, shopId)`：用订单索引回填已有差评文件。
- `MeituanPrivateInfoTrigger.ensureForMatchedReviews(context, shopId)`：只对已经 `matched` 且缺联系方式的订单补隐私号。

人工操作触发方式：

- 打开美团商家版分身并进入订单页，会自然产生 `/gw/api/unified/r/order/...` 响应，hook 会自动捕获。
- 进入历史订单或让系统写入历史覆盖配置后，触发器会通过美团进程自己的 mt_network 拉取指定日期窗口。
- 拉取 OPPO/真机文件时，应取回 `network-probe/meituan-waimai/{shopId}/responses/`、`orders/order-index.jsonl` 和 `history-trigger/`，这样才能同时复核原始响应、订单索引和覆盖进度。

禁止事项：

- 不从宿主侧伪造美团登录请求。
- 不把 cookie、token、authorization、session 等凭据写入文档或输出文件。
- 不把普通探针日志中只有 `orderViewId` 的 KNB 信号当作完整订单数据。
- 不把配送动态、骑手信息或物流状态当作订单关联证据。

## 商家主动联系顾客的 IM 会话验证

小米真机验证记录：

- 设备：Xiaomi MIX 2S，Android 10。
- 引擎：`1.2.12-xiaomi-livechat-debug`，`versionCode=50020`。
- 时间：2026-06-11 11:11 至 11:13。
- 操作：启动美团外卖商家版分身，进入订单列表，点击订单卡片上的顾客会话入口。
- 证据目录：`docs/network-probe/xiaomi-mix2s-livechat-20260611/`。

本次验证必须忽略页面上的运营提示、引导文案和 UI 文案，只以网络请求、响应 hook、订单索引为证据。

已抓到的 IM 相关请求：

- `/gw/api/im/rights/v2/top/tips`：由 `MeituanOrderResponseHook` 捕获到 `status=200` 响应。
- `https://api.neixin.cn/msg/api/pub/v1/chatlist/detail`：普通日志中出现请求体：

```json
{"fields":["ext"],"ps":[{"u":137688184292,"chid":1001,"pu":2075223174,"sid":""}]}
```

同一段日志还出现会话 key：

```text
137688184292_2075223174_1001_3
```

结论：

- 商家侧进入实时会话时，可以在 IM 请求中看到会话侧标识：`u`、`pu`、`chid`、`sid`，以及由它们组成的 session key。
- 这些字段只能先定义为 IM 会话标识，不能直接定义为美团订单号、顾客订单 ID 或 `wmUserId`。
- 本次小米数据中，`137688184292`、`2075223174` 等 IM 标识没有出现在 `15397100/orders/order-index.jsonl`、`15397100/responses/responses_2026-06-11.jsonl`、`unknown/orders/order-index.jsonl` 或 `unknown/responses/responses_2026-06-11.jsonl` 中。
- 因此，当前不能用 IM 会话标识直接匹配订单号；它只能作为后续排查线索。

IM 标识要升级为订单关联依据，必须先抓到桥接证据，至少满足以下任一条件：

- 同一个请求或响应里同时出现 IM session tuple 和 `orderViewId`。
- 同一个请求或响应里同时出现 IM session tuple 和差评 `commentId`。
- 同一个请求或响应里同时出现 IM session tuple 和订单侧 `wmUserId`，且该 `wmUserId` 已在订单索引中可复核。
- 后续接口明确把 `u`、`pu`、`chid`、`sid` 映射到订单或评论对象。

后续重点抓取入口：

- 差评页“联系顾客”路径下是否触发 `/gw/customer/comment/im/realtime/rights` 或同类接口。
- 实时会话初始化参数里是否出现 `IMAnonymousChatInfo`、`SessionParams` 或同类对象。
- `chatlist/detail` 的响应 `ext` 是否返回订单号、评论 ID、订单侧用户 ID。

在出现上述桥接证据前，Phase 12 的差评订单关联仍然只能使用历史覆盖、差评日期、订单日期和菜品文本打分作为主判断依据。

## 映射输入

差评侧输入：

- `createTime`
- `orderDetails[].foodName`
- `criticFoodList[]`

订单侧输入：

- `orderViewId`
- `orderDate`
- `orderTimeFmt`
- `wmUserId`
- `foodNames`
- 归一化后的 `searchText`
- 顾客电话、隐私号、手机号尾号等字段仅作为输出或后续联系信息，不作为当前主匹配依据。

IM 会话侧输入：

- `u`
- `pu`
- `chid`
- `sid`
- session key

IM 会话标识默认不作为主匹配依据。只有抓到上文定义的订单/评论桥接证据后，才能把它加入候选关联链路。

当前实现的主判断依据是日期窗口和菜品文本，不是金额、电话尾号、顾客标签、IM 会话标识、骑手或配送信息。

## 候选订单窗口

对于差评日期 `reviewDate`，只允许以下范围内的订单成为候选：

```text
reviewDate - 3 天 <= orderDate <= reviewDate
```

不在这个窗口内的订单，即使菜名相似，也不会进入候选。

## 菜品文本归一化

差评菜品和订单菜品都会归一化后再比较：

- 转小写。
- 去掉空白。
- 去掉常见标点和分隔符，包括中英文括号、逗号、顿号、斜杠、冒号、连字符、加号、引号等。
- 提取第一个括号前的基础菜名作为 `base`。
- 提取括号中的规格、口味、粉面类型、汤/干拌等选项作为变体。

示例：

```text
豆豉蒸排骨粉/面（份量）（扁粉（手工粉））（汤）
=> 豆豉蒸排骨粉面份量扁粉手工粉汤
```

## 打分规则

每个差评菜品会和候选订单里的菜品文本打分：

- 完整归一化菜名相等，或订单搜索文本包含完整归一化菜名：`24`
- 基础菜名长度至少为 3，且出现在订单菜品或订单搜索文本中：`8`
- 已有菜品命中后，每命中一个括号选项：`+3`
- 已有菜品命中后，每缺失一个括号选项：`-1`

以下低价值选项不参与变体判断：

- `1`
- `1个`
- `1人份`
- `标准`
- `份量`

订单总分是所有命中的差评菜品分数之和。

## 命中判定

当前常量：

- `MIN_MATCH_SCORE = 8`
- `UNIQUE_MATCH_MARGIN = 6`

候选排序规则：

1. 分数更高。
2. 命中的差评菜品更多。
3. `orderDate` 更新。
4. `orderTimeFmt` 更新。

输出状态：

- `matched`：最高分至少为 `8`，且最高分比第二名至少高 `6`。
- `ambiguous`：有候选订单，但最高候选不够唯一。
- `pending`：缺少可用日期、缺少菜品信号，或 3 天窗口内没有正分候选。
- `pending_coverage`：历史订单覆盖不完整。

只有 `matched` 能当作已建立差评和订单的关联关系。`ambiguous` 只能作为人工复核或后续补充信号的候选。

## 为什么 OPPO 是这 13 条数据

OPPO 过滤文件：

```text
docs/network-probe/oppo-55J7JJWKTWKNHYZL-20260611-080628/network-probe/meituan-waimai/15397100/responses/responses_2026-06-11.order-review-filtered.jsonl
```

这 13 条的来源是：

- 原始文件共有 14 行响应。
- 其中 12 行来自 `/gw/api/unified/r/order/detail/batch`。
- 其中 2 行来自 `/gw/api/unified/r/order/list/interval`。
- 按 `orderViewId` 去重后剩 13 个唯一订单。
- 同一个订单重复出现时，优先保留信息更完整的 `detail_batch` 记录。

因此，“为什么是这几条”的答案是：它们是 OPPO 抓到的订单详情/订单列表响应中，去重后保留下来的订单记录。它们不是因为已经和差评命中才被选出来。

要把其中某一条叫作“差评关联订单”，还必须拿具体差评对象跑完整的覆盖检查、日期窗口、菜品打分和唯一性判定。

## 订单过滤字段标准

过滤文件可以保留这些字段，因为它们对订单关联、复核或后续联系有用：

- 订单标识：`orderViewId`、`wmOrderId`、`wmUserId`、`dayseq`
- 订单时间：`orderDate`、`orderTime`、`orderTimeFmt`
- 顾客线索：姓名、手机号尾号、隐私号字段
- 菜品线索：菜名、数量、商品 ID、明细 ID、规格选项
- 备注和去地址后的复制文本
- 新客、复购等顾客标签
- 非配送类金额摘要
- 售后、退款、责任判定摘要
- 来源元数据：原始文件、行号、URL、抓取时间、重复次数

必须排除这些字段：

- 骑手/配送员姓名、电话、位置、评分、换人、取消、按钮和状态。
- 配送轨迹、配送动态、物流状态。
- 配送费、配送补贴、配送预期管理。
- 和差评订单关联无关的 UI 展示字段。

骑手、配送员、物流字段不能作为 Phase 12 的关联证据。

## 输出结构

`customerInfo` 必须包含：

- `associationStatus`
- `source`
- `score`
- `note`
- `candidates`
- `orderCoverage`

当存在最佳候选，且状态为 `matched` 或 `ambiguous` 时，可以补充：

- `userId`
- `phone`
- `phoneShow`
- `virtualPhone`
- `virtualPhoneShow`
- `privacyPhoneStatus`
- `isUsePrivacyPhone`
- `privateInfoStatus`
- `privateInfoMessage`
- `privateInfoUpdatedAt`
- `orderViewId`
- `orderDate`
- `orderTimeFmt`
- `dayseq`

`candidates` 最多保留前 8 个候选，并写入 `matchedFoods` 和 `foodNames`，保证关联结果可审计。

## 禁止命中规则

出现以下任一情况，不允许输出 `matched`：

- 历史订单覆盖不完整。
- `createTime` 缺失或不可解析。
- `orderDetails` 和 `criticFoodList` 都为空。
- 3 天窗口内没有菜品正分候选。
- 多个订单菜品/日期信号接近，最高候选没有超过唯一性分差。
- 只有骑手、配送员、物流信息重合。
- 只有 IM 会话标识重合，但没有能桥接到订单号、评论 ID 或订单侧 `wmUserId` 的响应证据。
- 只有金额、手机号尾号、顾客标签重合，但没有菜品和日期证据。

## 实现引用

- `Bcore/src/main/java/top/niunaijun/blackbox/networkprobe/MeituanOrderAssociationStore.kt`
- `MeituanOrderAssociationStore.enrichReview(...)`
- `MeituanOrderAssociationStore.matchReviewToOrders(...)`
- `MeituanOrderAssociationStore.scoreOrder(...)`
- `MeituanOrderAssociationStore.reviewFoodNames(...)`
- `MeituanHistoryOrderTrigger.reviewCoverageStatus(...)`
