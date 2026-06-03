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
