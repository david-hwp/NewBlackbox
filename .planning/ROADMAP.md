# NewBlackbox 路线图

## Phase 1: 单实例运行模式 ✅ 已完成 (2026-05-30)

**目标**: 实现"同时只运行一个分身应用"功能，在打开新分身应用时自动杀掉之前运行的其他分身应用，以节省系统内存。

**关键交付物**:

- ✅ `ClientConfiguration.isSingleInstanceMode()` — 配置接口，默认关闭
- ✅ `BProcessManagerService.killAllOtherProcesses()` — 批量杀掉其他应用进程 + 通知清理
- ✅ `BlackBoxCore.launchApk()` 单实例集成 — 启动前自动清理（try-catch 保护）
- ✅ UI 设置开关 — Settings 页面添加 Single Instance Mode 开关
- ✅ `ActivityStack.finishAllActivitiesExcept()` + `BActivityManagerService` 暴露接口 — Activity 记录清理

**验证**: `./gradlew :app:compileDebugJavaWithJavac :app:compileDebugKotlin` — BUILD SUCCESSFUL

**计划文档**: [.planning/PLAN.md](.planning/PLAN.md)
**调研文档**: [.planning/research/RESEARCH.md](.planning/research/RESEARCH.md)

## Phase 2: 虚拟环境内置 Google WebView

**目标**: 将 `com.google.android.webview` APK 打包进 BlackBox，在虚拟环境中自动部署，使所有分身应用统一使用 Google WebView，彻底解决 Honor/Huawei/Xiaomi 等厂商 WebView 兼容性问题。

**关键交付物**:

- `assets/webview/` — 内置 Google WebView APK（~40MB）
- `BWebViewInstaller` — 虚拟环境 WebView 自动安装器
- `WebViewFactoryProxy` / `IWebViewUpdateServiceProxy` 增强 — Hook 指向内置 WebView
- 构建脚本 — 自动下载/校验 WebView APK
- 设置开关 — 允许用户选择"使用内置 WebView"或"系统默认"

**计划文档**: [.planning/PLAN-phase2.md](.planning/PLAN-phase2.md)
**调研文档**: [.planning/research/RESEARCH-phase2.md](.planning/research/RESEARCH-phase2.md)

## Phase 3: TBD

## Phase 4: 店铺ID自动获取与展示

**目标**: BlackBox 自动获取分身应用登录后的店铺Id，获取成功后展示在分身名称后面，格式如：`京东秒送商家-16364870`。设计需支持多平台扩展（京东、淘宝闪购、美团外卖等）。

**关键交付物**:

- `ShopIdExtractor` 通用接口 — `extract(context)` 返回 `ShopInfo{shopId, shopName, platform}`
- `JDShopIdExtractor` — 京东秒送商家实现（SharedPreferences / WebView DevTools 抓取）
- `ShopIdExtractorRegistry` — 按包名路由到对应 Extractor（`com.jd.mrd.jingming` → JD extractor）
- `AppInfo` 数据模型扩展 — 新增 `shopId` / `shopName` / `platform` 字段
- 自动触发机制 — 应用启动/Resume 时异步抓取，首次登录后自动更新
- UI 展示层 — 应用列表页面在分身名称后展示 `-{shopId}`，无 shopId 时显示原名称
- 持久化存储 — 店铺ID存入 BPackageManagerService 的内部数据库

**验证**: 安装京东秒送商家分身并登录后，应用列表显示 `京东秒送商家-16364870`

## Phase 8: 主 APK 改造为多店管家

**目标**: 基于 `docs/原型设计` 中的 5 屏交互设计，将 BlackBox 主 APK 从虚拟引擎管理工具改造为"多店管家"多平台店铺管理 APP。

**关键交付物**:

- `Theme.Duodian` — 全新主题系统（科技绿 `#059669` + 完整色阶）
- `LoginActivity` — 账号密码登录页
- `HomeActivity` — 首页（左侧平台栏 + 右侧店铺列表 + 左滑操作）
- `ProfileActivity` — 个人中心（用户信息 + 菜单 + 底部导航）
- `GiftActivity` — 算力赠送 + 二次确认弹窗
- `LogsActivity` — 交易日志（筛选 + 按日期分组）
- 自定义 Swipe RecyclerView — 店铺卡片左滑展开编辑/自动续时/删除
- `BaseBottomSheetFragment` — 底部抽屉组件封装
- 数据模型扩展 — `Platform` / `Shop` / `UserProfile` / `LogEntry`
- 导航流重构 — 登录 → 首页 ↔ 子页

**保留能力**:
- Phase 5 Engine IPC（`engine/` 包完整保留）
- Phase 4 ShopId 提取（驱动店铺列表数据）
- Phase 1 单实例模式（迁移到系统设置）

**验证**: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL + 5 屏 UI 像素级匹配设计规范

**计划文档**: [.planning/phases/08-main-app-rewrite/08-PLAN.md](.planning/phases/08-main-app-rewrite/08-PLAN.md)
**调研文档**: [.planning/phases/08-main-app-rewrite/08-RESEARCH.md](.planning/phases/08-main-app-rewrite/08-RESEARCH.md)

### Phase 8 Wave 2: 后台管理服务端 API

**目标**: 为多店管家 Android APP 和 admin Web 后台提供统一后端服务，实现用户鉴权、店铺管理、算力计费、交易日志、用户反馈等 8 个核心接口。

**关键交付物**:

- `POST /api/auth/login` — 手机号+密码登录，返回用户信息+token
- `GET /api/shops/my` — 查询当前用户店铺列表
- `POST /api/shops/report` — APK 上报店铺数据，自动扣减算力
- `POST /api/compute/gift` — 算力赠送（事务保证双方余额变更）
- `POST /api/feedbacks` — 用户反馈（文本+图片+日志文件上传）
- `GET /api/logs/my` — 交易日志查询（支持 type 过滤+分页）
- `PUT /api/users/me/username` — 修改用户名
- `PUT /api/users/me/password` — 修改登录密码

**技术栈**: Spring Boot 3.2 + JPA + MySQL

**验证**: 8 个接口全部可通过 curl 正常调用，响应格式符合 `ApiResponse<T>` 规范

**计划文档**: [.planning/phases/08-main-app-rewrite/08-02-PLAN.md](.planning/phases/08-main-app-rewrite/08-02-PLAN.md)

### Phase 8 Wave 3: APK 与后端接口对接

**目标**: 在 Android APP 端建立 Retrofit + OkHttp 网络层，对接 Wave 2 后端 API，实现完整的数据流（登录 → 店铺列表 → 算力赠送 → 交易日志 → 用户反馈）。

**关键交付物**:

- `RetrofitClient` + `ApiService` — 8 个 API 接口的 Retrofit 定义
- `TokenManager` — EncryptedSharedPreferences 存储 token 和用户信息
- `AuthInterceptor` — 自动注入 Authorization Header
- `UserRepository` / `ShopRepository` / `ComputeRepository` / `LogRepository` / `FeedbackRepository` — Repository 层
- `LoginViewModel` / `HomeViewModel` / `GiftViewModel` / `LogsViewModel` / `ProfileViewModel` — ViewModel 层对接
- Activity 层数据绑定 — 各页面观察 LiveData 驱动 UI

**验证**: 8 个接口全部可从前端正常调用，联调通过

**计划文档**: [.planning/phases/08-main-app-rewrite/08-03-PLAN.md](.planning/phases/08-main-app-rewrite/08-03-PLAN.md)

## Phase 9: Clone 授权计费与长期令牌 ✅ 已完成 (2026-06-08)

**目标**: 将新增店铺、续期、打开授权从店铺名称/店铺ID迁移到服务端签发的 `cloneInstanceId`，并由服务端签发长期授权令牌、引擎本地验签放行。

**关键交付物**:

- ✅ 服务端生成可解析 `cloneInstanceId`，包含手机号、平台包名、店铺序号、本机虚拟 User 目录号和随机摘要
- ✅ 服务端保存不返回 APP 的 clone 校验随机码，扣费幂等键绑定 `cloneInstanceId`
- ✅ 新增店铺和续期按 `cloneInstanceId` 扣费，店铺信息上报和手工编辑不触发扣费
- ✅ 服务端签发 `authorizationToken`，token 不包含 `shopName`、`shopId` 等展示字段
- ✅ APP 在新增、续期、恢复店铺时通过 AIDL 将 clone meta 和 token 写入引擎
- ✅ 引擎将 `meta.json` 和 `auth.token` 保存到系统目录 `clone-auth/{cloneInstanceId}/`，不写入分身 App 可见数据目录
- ✅ 引擎打开店铺前本地验签、校验 claim 和有效期，缺失/过期/篡改/不匹配时拒绝打开
- ✅ 平台级店铺恢复、创建进度弹窗、左滑本地修复、交易日志平台名称展示等 Phase 9 验收项完成

**发布闭环**: `1.2.0-release` 完成主体 clone 授权计费；`1.2.1-release` 修复恢复缓存；`1.2.2-release` 完善进入/修复交互；`1.2.3-release` 修复本地修复不影响服务器数据并完成最终收口。

**验证**: `./gradlew :app:assembleRelease --no-daemon` BUILD SUCCESSFUL；服务器已发布主 APK 版本记录和版本公告；小米真机完成 1.2.3 左滑修复弹窗与引擎无响应回归验证。

**计划文档**: [.planning/phases/09-clone-auth-billing/09-PLAN.md](.planning/phases/09-clone-auth-billing/09-PLAN.md)
**上下文文档**: [.planning/phases/09-clone-auth-billing/09-CONTEXT.md](.planning/phases/09-clone-auth-billing/09-CONTEXT.md)

## Phase 10: 引擎权限中心 🚧 进行中

**目标**: 将抖音来客相机/录音兼容逻辑升级为通用“引擎权限中心”，由主 APK 统一判断目标平台需要的宿主引擎权限，引擎 APK 一次性申请基础危险权限并复核 AppOps，减少后续分身打开时因宿主权限不足导致的相机、人脸识别、录音、定位等失败。

**关键交付物**:

- ⏳ 主 APK 增加 `EnginePermissionCenter`，统一计算基础权限、平台 Manifest 权限和抖音来客兼容兜底权限
- ⏳ 引擎 APK 增加通用权限申请 Activity，支持一次传入一组权限并在授权后复核运行时权限和 AppOps
- ⏳ 基础权限按引擎版本只主动提示一次，用户拒绝后不在首页反复弹窗；打开具体店铺时只补请求该平台仍缺失的必要权限
- ⏳ 抖音来客不再走独立硬编码流程，合并为权限中心的首个特定平台兼容场景
- ⏳ 保持其他平台启动行为不变，不引入新的服务端 API 或扣费逻辑变更
- ⏳ 编译主 APK 和引擎 APK，并在真机/模拟器上验证权限中心、抖音来客打开和原有平台回归

**计划文档**: [.planning/phases/10-engine-permission-center/10-PLAN.md](.planning/phases/10-engine-permission-center/10-PLAN.md)
**上下文文档**: [.planning/phases/10-engine-permission-center/10-CONTEXT.md](.planning/phases/10-engine-permission-center/10-CONTEXT.md)
**调研文档**: [.planning/phases/10-engine-permission-center/10-RESEARCH.md](.planning/phases/10-engine-permission-center/10-RESEARCH.md)

## Phase 10.1: 算力取回 📋 已规划

**目标**: 在 APP 侧新增“算力取回”能力，让用户可以查询自己最近一次赠送给指定手机号的算力记录，并在对方尚未消耗的范围内取回部分或全部算力。

**关键交付物**:

- 后端新增算力取回查询接口：按当前登录用户和接收方手机号查询最新赠送日志，返回赠送数量、交易时间、对方消耗算力和可取回算力。
- 后端新增算力取回执行接口：事务内重新计算可取回数量，校验通过后扣减接收方余额、增加当前用户余额，并写入双方交易日志。
- APP “我的 -> 算力管理”中在“算力赠送”下方新增“算力取回”入口。
- APP 新增算力取回页面：手机号输入 + 查询按钮 + 小票式结果卡片 + 底部取回按钮。
- 取回弹窗默认填入可取回数量，APP 侧校验不得超过可取回算力个数；后端校验失败提示“对方新增了消耗，请重新查询可取回算力”。
- 无赠送记录时提示“未查询到您给对方的赠送记录”。

**计划文档**: [.planning/phases/10.1-compute-reclaim/10.1-PLAN.md](.planning/phases/10.1-compute-reclaim/10.1-PLAN.md)
**上下文文档**: [.planning/phases/10.1-compute-reclaim/10.1-CONTEXT.md](.planning/phases/10.1-compute-reclaim/10.1-CONTEXT.md)
**调研文档**: [.planning/phases/10.1-compute-reclaim/10.1-RESEARCH.md](.planning/phases/10.1-compute-reclaim/10.1-RESEARCH.md)

## Phase 11: 渠道推广完整体系 ✅ 已完成 (2026-06-14)

**目标**: 将现有 `apkChannel` 注册标识升级为完整渠道推广体系，支持不同渠道拥有独立 APK 发布、用户注册、公告、主 APK 升级和用户算力隔离，同时由超级管理员统一管理渠道、渠道管理员和渠道总算力池。

**关键交付物**:

- `channels` 渠道实体；渠道可分配算力复用绑定的 `CHANNEL` 用户余额与现有交易日志
- `SUPER_ADMIN / CHANNEL_ADMIN / USER` 角色体系与统一权限服务
- 渠道管理员仅可查看和管理本渠道用户、店铺、交易、反馈和公告
- 超级管理员可创建渠道、绑定渠道管理员、配置渠道 APK 名称/图标/标识、给渠道总池分配算力
- 渠道管理员从渠道算力池给本渠道用户分配算力，跨渠道算力互通被禁止
- 公告按 `GLOBAL/CHANNEL` 作用域隔离，APP 只展示本渠道和全局公告
- 主 APK 版本发布和 checksum 校验按渠道隔离，APP 只检测本渠道发布版本
- 渠道 APK 使用独立 `release/channel/{channelCode}` 分支管理，后台发起发布任务后由固定脚本合并主 release、校验渠道图标/名称/引擎名称、打包、上传并回调完成
- APP 登录、注册、公告、版本检查和包校验携带渠道上下文
- 历史数据迁移到 `main` 或保留已有 `apk_channel` 对应渠道，保证线上兼容

**计划文档**: [.planning/phases/11-channel-promotion-system/11-PLAN.md](.planning/phases/11-channel-promotion-system/11-PLAN.md)
**上下文文档**: [.planning/phases/11-channel-promotion-system/11-CONTEXT.md](.planning/phases/11-channel-promotion-system/11-CONTEXT.md)
**调研文档**: [.planning/phases/11-channel-promotion-system/11-RESEARCH.md](.planning/phases/11-channel-promotion-system/11-RESEARCH.md)

**完成总结**: Phase 11 渠道推广体系已完成本地验收；`phase11-checked` 分支额外完成 Android 15 WebView 兼容最小化复验。Pixel 9 / Android 15 上 JD 登录 WebView 能进入登录页，DongCore WebView 网络探测和系统 WebView sandbox 启动正常；短信验证码触发链路在手机号输入正确后进入倒计时，但 5 秒内仍未出现真实滑动验证码组件，后续问题应继续聚焦验证码业务 WebView 渲染链路。

## Phase 12: 美团差评与顾客信息采集固化 📋 已规划

**目标**: 确保美团外卖商家版分身登录态数据在 Pixel7 / OPPO 调研环境中可验证可复用，并将“用户进入已登录分身后自动获取差评数据、关联顾客信息（用户Id、手机号或虚拟号）、按平台和店铺Id落盘到引擎目录”的能力固化到 Bcore 引擎。

**关键交付物**:

- 在 worktree 中完成引擎侧美团探针，避免污染当前 `dev` 工作区；OPPO 真机只允许只读采集和前台操作，不清数据、不卸载、不删除文件。
- 用户成功进入美团外卖商家版分身后，引擎自动启动采集，不依赖用户手动运行 probe Activity。
- 采集差评列表：识别 `/gw/customer/comment/list`、`user_comment_list_data_key`、`commScore=3` 等信号，解析 1-2 星评论并标准化字段。
- 关联顾客信息：解析订单列表/订单详情/IM 权益/IM 会话日志和缓存，尽可能填充 `customerInfo.userId`、`customerInfo.phone`、`customerInfo.virtualPhone`、`customerInfo.orderViewId` 和来源状态。
- 结果落盘到引擎自有目录，按 `review-data/{platform}/{shopId}/reviews_yyyy-MM-dd.jsonl` 分目录存储，不导出 token、cookie、session 等敏感凭据。
- 支持 Pixel7 验证路径：先确认分身登录态数据能正常使用，再验证引擎自动采集输出；无法从非 root OPPO 直接迁移私有数据时，保留 OPPO 只读调研路径作为证据来源。
- 补充测试和验证脚本，覆盖缓存扫描、日志解析、顾客信息关联、重复评论去重、敏感字段过滤和设备实测。

**验证**: 在 Pixel7 或 OPPO 已登录美团分身上进入粉面先生店铺评价链路后，引擎目录生成差评 jsonl；每条差评包含标准字段和 `customerInfo`，顾客信息不可确定时必须明确 `associationStatus` 和证据来源；单元测试和 debug 构建通过。

**计划文档**: [.planning/phases/12-meituan-review-customer-probe/12-PLAN.md](.planning/phases/12-meituan-review-customer-probe/12-PLAN.md)
**上下文文档**: [.planning/phases/12-meituan-review-customer-probe/12-CONTEXT.md](.planning/phases/12-meituan-review-customer-probe/12-CONTEXT.md)
**调研文档**: [.planning/phases/12-meituan-review-customer-probe/12-RESEARCH.md](.planning/phases/12-meituan-review-customer-probe/12-RESEARCH.md)

## Phase 13: 登录态跨设备同步 ✅ 已关闭 (2026-06-13)

**目标**: 将已登录平台分身的关键登录态从源设备导出、上传并恢复到新设备分身目录，实现同一店铺账号在多设备上直接打开店铺，避免同步 170MB+ 全量分身缓存。

**当前结论**:

- OPPO 罗家臭豆腐分身数据定位到虚拟 user 3；完整包级导出压缩约 120MB、原始约 372MB，不适合作为上传物。
- 小米真机逐档验证 A/B/C/D/E 后，E 档证明 OPPO 登录态可跨设备恢复；进一步在罗家臭豆腐实际卡片绑定的 `localVirtualUserId=22` 分身目录验证 CIPS-only 档位可直接登录。
- 当前美团最小可用集合是 CIPS 登录态/设备态/账号态组合：仅 `cache/cips/**` 与 `files/cips/**` 选中状态文件，压缩约 0.5MB；E 档保留为手工回退/诊断档。
- 所有功能 wave 前必须先补分身目录隔离 wave：分身数据目录以店铺管家用户 ID 做父级目录，下面按业务卡片/cloneInstance 创建 `user` 子分身目录；新版引擎新增迁移 Activity，安装/升级成功后第一时间提示并迁移历史 flat 分身数据，避免历史登录态丢失。
- Wave 2 已按最小档 `E -> D -> C` 验证：京东秒送 `E` 失败后升到 `D` 通过，默认 profile 为 `jd-jingming-prefs-d`；淘宝闪购饿了么 `E` 通过，默认 profile 为 `ele-napos-prefs-e-min`。
- 2026-06-12 已完成 OPPO 源端上传 + 小米清空目标分身目录后恢复验证：美团 `shops/4/login-state` 从 OPPO user3 上传约 460KB 并恢复到 Xiaomi user22；京东 `shops/5/login-state` 从 OPPO user15 上传约 32KB 并恢复到 Xiaomi user15；饿了么 `shops/6/login-state` 从 OPPO user2 上传约 74KB 并恢复到 Xiaomi user2；三者均未进入用户名/密码/SMS 登录页。
- 后续引擎导出上传必须按 Phase 13 标准文档的 package-specific profile 生成逻辑包，并在恢复时映射到目标卡片当前 `localVirtualUserId`，不能硬编码 OPPO source user 或 ADB 测试 user 路径。
- Wave 5 增加可信店铺身份约束：店铺 ID/名称只能由引擎从目标平台数据文件或接口提取后上报；未验证身份的店铺卡片 logo 保持灰色，验证成功并由后台返回 `identityVerified=true` 后才显示彩色。
- Wave 6 已关闭：淘宝闪购零售版 `com.baidu.lbs.xinlingshou`、美团经营宝 `com.sankuai.meituan.merchant`、携程商家版 `com.Hotel.EBooking` 已按各自最小 profile 标记为完成；抖音来客 `com.bytedance.ls.merchant` 的 `CLN1-15200837196-com.bytedance.ls.merchant-N1-U25-R672c8b60` 分身即使释放完整 OPPO card 包仍跳手机登录页，作为遗留事项记录，不纳入已支持平台。
- 2026-06-14 追加完成 Wave 6 店铺身份提取修复：淘宝闪购零售版“花果山水果”从 XML 转义 `shop_info` 提取；美团经营宝“启程台球厅”从 CIPS `shop_info/kv` 的 `dp_shop_id`/`mt_shop_id`/`shop_name` 提取。OPPO 验收通过，登录态上传门槛仍保持“真实店铺 ID + 真实店铺名称”。

**计划文档**: [.planning/phases/13-meituan-login-state-sync/13-PLAN.md](.planning/phases/13-meituan-login-state-sync/13-PLAN.md)
**标准文档**: [.planning/phases/13-meituan-login-state-sync/13-STANDARD.md](.planning/phases/13-meituan-login-state-sync/13-STANDARD.md)
**调研文档**: [.planning/phases/13-meituan-login-state-sync/13-RESEARCH.md](.planning/phases/13-meituan-login-state-sync/13-RESEARCH.md)

## Phase 14: 用户订阅计费体系 ✅ 已完成 (2026-06-13)

**目标**: 在现有算力扣费机制上叠加用户维度订阅体系，支持新用户 30 天免费订阅、后台管理员按月/季/年升级订阅用户、订阅期内店铺创建/续期写 0 扣费交易日志，订阅过期后自动恢复店铺维度算力计费。

**关键交付物**:

- `users` 增加订阅状态、订阅套餐和订阅到期时间字段；`/auth/me` 和登录响应返回订阅信息。
- 新注册用户自动赠送 30 天订阅体验，并保留原注册赠送算力兼容历史算力体系；首次登录后 APP 展示免费体验卡提示。
- 后台用户管理支持管理员将用户设置为月度、季度、年度订阅用户或关闭订阅；老用户升级订阅不退还历史已扣算力。
- 创建店铺、续期店铺和打开过期店铺时复用现有算力扣费链路：活跃订阅用户写交易日志但 amount=0，不减少算力余额；普通用户和订阅过期用户继续扣 1 点算力。
- APP 首页手机号下方展示订阅到期时间；普通用户展示“升级为订阅用户，解锁无上限店铺特权”。
- APP 店铺卡片对活跃订阅用户隐藏店铺维度剩余天数；普通用户和订阅过期用户继续展示。
- 用户点击店铺卡片前校验订阅/店铺有效期；订阅已到期且店铺已到期时提示“订阅已到期，继续使用将扣除1点算力”，确认并由服务器扣费续期后才能进入。

**计划文档**: [.planning/phases/14-subscription-billing/14-PLAN.md](.planning/phases/14-subscription-billing/14-PLAN.md)
**上下文文档**: [.planning/phases/14-subscription-billing/14-CONTEXT.md](.planning/phases/14-subscription-billing/14-CONTEXT.md)
**调研文档**: [.planning/phases/14-subscription-billing/14-RESEARCH.md](.planning/phases/14-subscription-billing/14-RESEARCH.md)

**验收记录**: 小米 MIX 2S 已完成 `1.2.14-beta` 回归：普通用户到期店铺确认后扣 1 点续期；有效订阅用户打开到期店铺不扣点且隐藏店铺剩余天数；订阅到期用户看到到期提示，确认后扣 1 点续期。

## Phase 15: 首页滚动播报公告 ✅ 已完成 (2026-06-12)

**目标**: 在 APP 首页 header 下方新增全宽滚动播报条，由服务端新增“滚动播报”公告类型维护。播报条无额外分割线，作为 header 与主体内容之间的自然分隔；无已发布播报时不占位。

**关键交付物**:

- 后台公告管理新增“滚动播报”类型筛选、发布、编辑和列表展示。
- 服务端公告类型复用现有 `announcements.type` 字段，支持 `SCROLLING_TICKER` 查询和保存。
- APP 首页新增 header 下方全宽浅绿色播报条，搜索框、平台列表和店铺列表整体下移。
- 短播报静态展示，长播报一行横向慢速滚动；触摸暂停，辅助功能触摸探索开启时不自动滚动。
- 移除 header 下方旧 1px 分割线，滚动播报条本身承担视觉分隔。

**验证**: `:app:compileDebugKotlin`、后台前端 `npm run build`、后端 `mvn -Dtest=AnnouncementControllerTest test` 均通过。

**计划文档**: [.planning/phases/15-scrolling-ticker-announcement/15-PLAN.md](.planning/phases/15-scrolling-ticker-announcement/15-PLAN.md)
**上下文文档**: [.planning/phases/15-scrolling-ticker-announcement/15-CONTEXT.md](.planning/phases/15-scrolling-ticker-announcement/15-CONTEXT.md)
**调研文档**: [.planning/phases/15-scrolling-ticker-announcement/15-RESEARCH.md](.planning/phases/15-scrolling-ticker-announcement/15-RESEARCH.md)

## Phase 16: 主 App 登录态数据中心重构 ✅ 已完成 (2026-06-13)

**目标**: 将登录态备份、同步元数据和业务判断从引擎目录迁移到主 App 数据目录，由主 App 作为店铺卡片登录态数据中心；引擎降级为 AIDL 能力层，只负责分身运行、店铺信息采集、登录态导出/释放等原子能力。

**关键交付物**:

- 主 App 新增登录态暂存与元数据仓库，按系统店铺 ID 短暂保存待上报登录态 blob，并长期保存 manifest、sha256、createdAt、uploadedAt、profile、platformPackage、cloneInstanceId、virtualUserId 和最近一次店铺信息采集摘要。
- 引擎 AIDL 收口为无业务决策的能力接口：采集店铺信息、导出登录态、释放登录态、清空分身数据、准备启动和查询分身基础映射；引擎不再决定是否上传服务器、是否下载服务器登录态、是否覆盖本地登录态。
- 店铺信息采集成为统一入口：只有采集到有效店铺信息后，主 App 才触发登录态导出和本地保存；采集失败视为未确认登录，不导出、不上传登录态。
- 主 App 统一处理服务器同步：上传时按系统店铺 ID 上报，服务器只保存更新的登录态文件；下拉刷新只更新店铺基本信息，不触发登录态释放；修复店铺时才从主 App 本地或服务器最新登录态释放到引擎分身目录。
- 服务器登录态只有两个场景可以释放并覆盖本地：新手机/本地没有可用分身登录态时用于首次恢复，或用户主动触发“修复店铺”；除此之外始终使用用户手机当前分身本地登录态，普通打开、下拉刷新、后台同步和店铺信息采集都不能用服务器登录态覆盖本机分身。
- 服务器侧店铺信息和登录态平时只接受各设备上报并做备份；只有新设备本地无可用数据、用户主动修复这两个场景，服务器数据才作为恢复来源参与释放。
- 数据清理规则：引擎采集到的店铺信息和登录态导出结果只通过 AIDL 返回，不在引擎侧额外落盘；主 App 上报成功后立即删除原始采集 blob/zip，只保留必要元数据、校验值和上报记录。
- 保留引擎必需的虚拟化运行数据：虚拟用户、虚拟包安装信息、cloneInstanceId 到 virtualUserId 映射、授权 token 和分身实时运行目录仍在引擎侧；但这些不再承载登录态同步的业务真相。
- 增加迁移与兼容：升级后主 App 从现有服务器/引擎导出链路补齐自己的本地登录态索引，避免已登录店铺因为数据归属调整而丢失可恢复能力。

**验证**: `:app:compileDebugKotlin`、`ShopControllerTest`/`ShopServiceTest` 通过；小米 MIX 2S 安装 `1.2.14-beta` 主 App 和引擎后，用二公子账号 shop=53 罗家臭豆腐验证：普通打开不触发 `restoreLoginState`，未获取店铺信息时不导出上传；主动“修复店铺”触发 `reason=repair`，按系统店铺 ID 下载 462736 字节登录态并恢复到 user3；再次普通打开进入美团外卖商家版订单页，10 秒后店铺信息采集 `found=true`，服务端登录态更新到 463616 字节且 raw staging 无残留。

**计划文档**: [.planning/phases/16-app/16-PLAN.md](.planning/phases/16-app/16-PLAN.md)
**上下文文档**: [.planning/phases/16-app/16-CONTEXT.md](.planning/phases/16-app/16-CONTEXT.md)
**调研文档**: [.planning/phases/16-app/16-RESEARCH.md](.planning/phases/16-app/16-RESEARCH.md)

## Phase 19: Xpra 店铺授权窗口 MVP 🚧 进行中

**目标**: 为店铺卡片增加独立“授权登录该店铺”入口，打开“店铺授权”底部抽屉，通过 Xpra HTML5 显示服务端虚拟桌面中的授权登录页，验证服务器窗口流式显示和输入交互可行性。

**关键交付物**:

- 店铺 ID 下方新增“授权登录该店铺”可点击文字，不改动“私域吸粉”高级功能入口。
- 新增 `ShopAuthorizationSheetFragment`，约 2/3 高度的可下滑抽屉内 WebView 加载服务端 Xpra HTML5 地址。
- Aliyun Ubuntu 服务器运行 Xvfb/fluxbox/Chromium/Xpra/VNC，服务端 Chromium 以 `360x520` kiosk 窗口打开京东外卖登录页。
- 服务端脚本源纳入 `admin/scripts/browser/`；服务器运行根目录为 `~/data`，日志和浏览器 profile 统一放在该目录下。
- ZR 控制接口 `http://100.99.88.6:14501/` 按用户手机号、店铺 ID 和 APP WebView 实际尺寸启动独立 Chromium profile；Xpra 默认指向 `http://100.99.88.6:14500/`。
- 构建并安装 `1.2.18-beta` 到小米真机进行 MVP 验证；后端 API 默认指向 `http://100.99.88.2:8006/api/`。

**计划文档**: [.planning/phases/19-xpra-shop-authorization/19-PLAN.md](.planning/phases/19-xpra-shop-authorization/19-PLAN.md)

### Phase 20: 新包名无感迁移发布

**目标**: 使用新包名发布 release APK，让旧包名用户安装并登录新包后自动迁移旧 engine 分身数据到新 engine。迁移必须无 root、无 Magisk、普通用户无感；由于新包首次安装没有本地用户信息，迁移只能在第一次登录成功并拉取当前用户店铺后触发，且同一服务器用户只允许执行一次旧引擎迁移。

**关键交付物**:

- 新主包/新引擎使用 `com.zhirang.zhanghaoguanjia.new` / `com.zhirang.zhanghaoguanjia.new.engine` 打 release 包并连接内网环境。
- 新主包登录成功后读取本地迁移状态和服务端 `legacyEngineMigrated` 状态；只有当前用户未迁移时弹出阻塞式“正在迁移数据”进度框。
- 新主包检测旧 engine 是否存在并能 resolve `EngineCloneDataExportActivity`；存在时拉起旧 engine 自导出，轮询旧 engine `clone-export` 目录，等待 `.tmp` 变成稳定 `.zip`，不依赖旧 manifest。
- 新 engine 新增 rootless 导入入口，支持 Zip64，按当前登录用户和服务端店铺/cloneInstance 精确导入旧 engine 中属于该用户的分身数据、clone mapping、clone auth 和外部数据。
- 服务端 `users` 增加不在管理后台展示的迁移完成字段，登录响应和 `/auth/me` 返回该字段；迁移成功后由新主包调用内部 API 标记完成。
- 本地 TokenManager/SharedPreferences 记录每个服务器用户的旧引擎迁移结果；同一用户后续登录不再触发旧 engine 数据迁移。
- 小米真机验证完整安装、登录、导出、导入、新引擎打开旧店铺、二次登录不重复迁移。

**验证**: 小米 MIX 2S 安装新包名 release APK 后，使用二公子账号登录，APP 弹出迁移进度并调用旧 engine 导出 Activity，生成并导入旧分身数据到新 engine；迁移完成后新引擎能打开该用户旧店铺且不串到其他账号店铺；退出重登同一用户不会再次迁移；无 root/Magisk 权限参与产品流程。

**计划文档**: [.planning/phases/20-new-package-migration-release/20-PLAN.md](.planning/phases/20-new-package-migration-release/20-PLAN.md)
**上下文文档**: [.planning/phases/20-new-package-migration-release/20-CONTEXT.md](.planning/phases/20-new-package-migration-release/20-CONTEXT.md)
**调研文档**: [.planning/phases/20-new-package-migration-release/20-RESEARCH.md](.planning/phases/20-new-package-migration-release/20-RESEARCH.md)

### Phase 21: 店铺订单入库与超管查询 ✅ 已完成 (2026-06-24)

**目标**: 将 Phase 19 爬虫脚本采集到的店铺订单数据入库，订单必须关联后台系统店铺 ID，并在管理后台新增超管专用“店铺订单”查询页面；店铺列表操作列新增超管可见“店铺订单”按钮，点击后跳转订单页并自动带上当前店铺以及当天 00:00 到此刻的订单完成时间筛选。

**Requirements**: PH21-D01, PH21-D02, PH21-D03, PH21-D04, PH21-D05, PH21-D06, PH21-D07, PH21-D08, PH21-D09, PH21-D10, PH21-D11, PH21-D12
**Depends on:** Phase 19 remote browser/profile chain; Phase 20 current intranet backend/frontend baseline
**Plans:** 1 plan

**关键交付物**:

- 新增 `shop_orders` 数据表、后端实体/仓储/服务/API，字段尽可能详细覆盖订单身份、状态、金额、顾客、配送、商品、时间和原始业务载荷。
- 订单入库按后台 `shops.id` 关联店铺，并快照用户、渠道、平台、平台店铺 ID 和店铺名称。
- 订单采集入库必须按 `shop_id + platform + platform_order_id` 幂等 upsert，重复采集更新已有记录。
- 更新 `fetch_meituan_orders.py`，在保留本地 JSON 诊断输出的同时，把采集订单批量提交到后端入库接口。
- 新增超管专用“店铺订单”页面，支持分页、店铺/平台/状态/顾客/订单号等常规筛选，并支持订单完成时间起止范围筛选，精确到分钟。
- 店铺列表操作列新增超管可见“店铺订单”按钮，保持操作列固定；点击后自动设置系统店铺 ID 和当天 00:00 到当前分钟的完成时间筛选。
- 使用内网开发测试管理后台和有头浏览器验证超管访问、非超管拦截、筛选和从店铺列表跳转。

**验证**: 后端 `ShopOrderControllerTest`/`ShopOrderServiceTest` 8 条通过，爬虫 parser/submission 测试通过，前端 `npm run build` 通过；已部署到内网开发测试管理后台 `http://172.20.0.13:8006`。使用正在运行的 GStack 有头浏览器验证：超管可从“极点披萨”店铺行点击“店铺订单”进入 `/shop-orders?shopId=194&completedStart=2026-06-24 00:00&completedEnd=当前分钟`，页面展示验收订单；店铺列表操作列保持 fixed right；未登录 API 返回 401，无效入库返回 400；raw payload 中 `cookie/token/authorization` 被清洗。

**计划文档**: [.planning/phases/21-shop-order-ingestion/21-PLAN.md](.planning/phases/21-shop-order-ingestion/21-PLAN.md)
**上下文文档**: [.planning/phases/21-shop-order-ingestion/21-CONTEXT.md](.planning/phases/21-shop-order-ingestion/21-CONTEXT.md)
**调研文档**: [.planning/phases/21-shop-order-ingestion/21-RESEARCH.md](.planning/phases/21-shop-order-ingestion/21-RESEARCH.md)
**完成总结**: [.planning/phases/21-shop-order-ingestion/21-SUMMARY.md](.planning/phases/21-shop-order-ingestion/21-SUMMARY.md)

### Phase 22: Clone 身份唯一性收口与跨账号防串号

**目标**: 将 APP、engine 和服务端的店铺身份判定收敛到同一套最小规则：服务端以 `AuthContext.userId + cloneInstanceId` 定位店铺卡片，本机以 `cloneInstanceId` 解析运行目录；`localVirtualUserId` 只作为当前设备 engine 的本机目录号，不再作为跨设备或跨账号的店铺身份字段。该 phase 必须先用小米真机和 OPPO 真机中的真实分身数据验证兼容性，确认不会破坏现有授权和登录态后再执行代码改造。

**Requirements**: PH22-D01, PH22-D02, PH22-D03, PH22-D04, PH22-D05, PH22-D06, PH22-D07, PH22-D08, PH22-D09, PH22-D10
**Depends on:** Phase 13 login-state sync; Phase 16 main-app login-state data center; Phase 20 scoped engine storage; current OPPO/Xiaomi real-device clone data
**Plans:** 1 plan

**关键交付物**:

- 真机数据兼容性报告：从小米和 OPPO 读取当前 `clone-instances.json`、`accounts/<userId>/cards/<cloneInstanceId>`、runtime symlink、auth meta 和登录态 manifest，证明新唯一性规则能覆盖现有数据。
- 服务端 `/shops/report` 改为优先且强制通过当前 token 用户的 `userId + cloneInstanceId` 定位店铺；禁止同一 clone 上报不同真实平台店铺 ID 时覆盖原店铺。
- 服务端停止把 `shops.local_virtual_user_id` 作为跨设备校验依据；上报时只允许记录/兼容本机目录号，不因不同设备目录号不同而拒绝。
- 登录态上传 manifest 增加并校验 `cloneInstanceId`，只允许写入同一 `userId + cloneInstanceId` 对应的店铺；恢复时仍映射到当前设备解析出的本机目录。
- APP 上报店铺信息和上传登录态必须携带同一份 `cloneInstanceId`，并在 `/shops/report` 成功后再上传登录态，避免基础信息失败但登录态已写入。
- 引擎目录解析继续使用 `accounts/{serverUserId}/cards/{cloneInstanceId}` 作为物理目录根；运行时查目录不得依赖服务端保存的 `localVirtualUserId` 作为权威身份。
- 保留旧字段和旧数据兼容，不做破坏性清表或丢弃现有授权；历史 `shops.local_virtual_user_id` 只作为展示/诊断兼容字段。
- APP 店铺卡片排序保存按 `packageName + platform` 范围提交，京东秒送混排列表不能触发后端同平台排序保护。
- OPPO 真机双账号验收：使用二公子账号和贺伟平账号来回切换登录，打开/刷新对应店铺，验证不会互相覆盖店铺基础信息和登录态；密码只来自执行会话，不写入仓库。
- 回归验证覆盖小米真机和 OPPO 真机，以及后端单元测试、APP/engine 编译和登录态上报链路测试。

**验证**: Wave 1 必须先输出小米与 OPPO 当前真实数据矩阵，确认 `userId + cloneInstanceId` 能唯一定位店铺且 `localVirtualUserId` 差异只存在于设备本地；实现后在 OPPO 真机用二公子和贺伟平两个账号交替登录、刷新和打开店铺，确认服务端店铺基础信息、登录态和 engine 目录不串号。

### Phase 23: 京东秒送订单采集入库 ✅ 已完成 (2026-06-27)

**目标**: 将 Phase 21 已落地的订单入库/查询能力从美团外卖扩展到京东秒送，让半小时定时脚本采集所有已授权 `jdms` 店铺的今日订单并通过现有 `/shop-orders/ingest` 入库，订单继续关联后台系统 `shops.id`，并在现有“店铺订单”页面中可按平台/店铺筛选查看。

**Requirements**: PH23-D01, PH23-D02, PH23-D03, PH23-D04, PH23-D05, PH23-D06, PH23-D07, PH23-D08
**Depends on:** Phase 19 remote browser/profile chain; Phase 21 shop order ingestion; current JD remote backend authorization detection
**Plans:** 1 plan

**关键交付物**:

- 后端 `/shop-orders/crawl-targets` 支持返回已授权京东秒送 `jdms` 店铺，同时保留美团 `mtwm` 行为。
- 定时调度脚本从美团单平台改为按平台分发采集器，当前支持 `mtwm` 和 `jdms`，并继续跳过未授权、未知、失败或缺少 profile 信息的店铺。
- 新增京东秒送 Node/CDP 订单采集器，基于真实已授权 JD profile 先抓取订单页 DOM/API 样本，再实现字段解析和入库 payload 映射。
- 京东订单通过现有 `shop_orders` 表和 `/shop-orders/ingest` 入库，按 `shop_id + platform + platform_order_id` 幂等 upsert，订单状态变化更新同一行。
- 京东采集输出和 raw payload 只保存业务订单数据，不记录 cookie、token、profile 路径、浏览器 storage、CDP debugPort 或授权信号。
- 内网开发测试环境验证：`172.20.0.13` 管理后台和 `192.168.0.210` 爬虫服务器手动跑一次调度，确认京东授权店铺被尝试采集并能在“店铺订单”页面按 `platform=jdms` 查看。

**验证**: 后端 `ShopOrderControllerTest`/`ShopOrderServiceTest` 通过；调度器 Python 测试通过；京东 Node/CDP parser 测试通过；爬虫服务器手动运行调度并验证 JD 目标，内网 `shop_orders` 已插入 `shop_id=76/platform=jdms` 的 6 条订单；不部署或重启线上 `zhirang-dev` app 环境。

**已知限制**: 京东连续翻页采集会触发平台风控页 `验证一下，购物无忧 快速验证`。采集器已改为分页不完整时失败退出，不提交部分 payload，避免误报成功或覆盖已有订单；后续优化应优先做单店筛选后再采集，减少分页次数。

**计划文档**: [.planning/phases/23-jd-order-ingestion/23-PLAN.md](.planning/phases/23-jd-order-ingestion/23-PLAN.md)
**上下文文档**: [.planning/phases/23-jd-order-ingestion/23-CONTEXT.md](.planning/phases/23-jd-order-ingestion/23-CONTEXT.md)
**调研文档**: [.planning/phases/23-jd-order-ingestion/23-RESEARCH.md](.planning/phases/23-jd-order-ingestion/23-RESEARCH.md)
**完成总结**: [.planning/phases/23-jd-order-ingestion/23-SUMMARY.md](.planning/phases/23-jd-order-ingestion/23-SUMMARY.md)
