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

