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

## Phase 11: 渠道推广完整体系 📋 已规划

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
