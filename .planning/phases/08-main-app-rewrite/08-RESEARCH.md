# Phase 8 调研：主 APK 改造为多店管家

## 1. 背景与目标

将 BlackBox 主 APK（`app` 模块）从"虚拟引擎管理工具"改造为"多店管家"多平台店铺管理 APP。

改造原则：
- **保留引擎能力**：Phase 5 的 Engine IPC（`engine/` 包）必须完整保留，作为后台服务运行
- **全新 UI 层**：基于 `docs/原型设计` 的 5 屏设计，完全替换现有 `MainActivity` + `AppsFragment` 界面
- **数据打通**：复用 `AppInfo` 中的 `shopId`/`shopName`/`platform` 字段（Phase 4 成果）
- **渐进式改造**：先实现 UI 外壳，再逐步迁移引擎管理功能到设置页

---

## 2. 现有代码结构分析

### 2.1 当前入口与主界面

| 文件 | 当前职责 | 改造后 |
|------|---------|--------|
| `EngineInstallActivity` | LAUNCHER 入口，首次安装引擎 | 保留，启动后跳转到 LoginActivity |
| `MainActivity` | ViewPager2 + 多个 AppsFragment | 改为 `HomeActivity`（首页） |
| `AppsFragment` | 网格展示分身应用 | 改为店铺列表，支持左滑操作 |
| `ListActivity` | 选择 APK 安装 | 保留，从"添加店铺"入口进入 |
| `SettingActivity` | 设置页 | 扩展为系统设置子页 |

### 2.2 现有数据模型

```kotlin
// AppInfo.kt — 已有 Phase 4 扩展字段
data class AppInfo(
    val name: String,
    val icon: Drawable?,
    val packageName: String,
    val sourceDir: String,
    val isXpModule: Boolean,
    val shopId: String? = null,      // ← Phase 4 已添加
    val shopName: String? = null,    // ← Phase 4 已添加
    val platform: String? = null     // ← Phase 4 已添加
)
```

**复用分析**：
- ✅ `shopId` / `shopName` / `platform` 可直接用于店铺展示
- ✅ `packageName` 用于点击店铺时唤起对应分身应用
- ✅ `icon` 可用作店铺 Logo 占位
- ⚠️ 需要新增：剩余天数、自动续时状态、算力相关字段

### 2.3 引擎层（必须保留）

```
app/src/main/java/top/niunaijun/blackboxa/engine/
├── EngineConnection.kt      # AIDL 连接
├── EngineInstaller.kt       # 引擎安装
├── EngineInstallReceiver.kt # 包监听
├── EngineLoader.kt          # 加载器初始化
├── EngineProxy.kt           # 核心代理（API 门面）
├── EngineUpgradeManager.kt  # 升级管理
└── EngineVersionChecker.kt  # 版本检查
```

**保留策略**：
- `EngineProxy` 提供所有引擎 API，新 UI 层直接调用
- `EngineInstallActivity` 保持为 LAUNCHER，但启动后流转到 LoginActivity
- 引擎升级弹窗保留，但改为从个人中心「系统设置」触发检查

---

## 3. 原型设计映射

### 3.1 屏幕 → Activity 映射

| 原型屏幕 | 新 Activity | 对应原型文件 |
|---------|------------|-------------|
| 登录页 | `LoginActivity` | `screens/login.html` |
| 首页 | `HomeActivity` | `screens/home.html` |
| 个人中心 | `ProfileActivity` | `screens/profile.html` |
| 算力赠送 | `GiftActivity` | `screens/trade.html` |
| 交易日志 | `LogsActivity` | `screens/logs.html` |

### 3.2 首页布局结构映射

现有：`MainActivity` → ViewPager2 → `AppsFragment` (GridLayoutManager 4列)

新设计：
```
HomeActivity
├── 顶部栏 (56dp) — Logo + 多店管家 | 我的账号
├── 算力信息条 (40dp) — 算力余额 200 | [交易日志]
├── 左右分栏
│   ├── 左侧平台栏 (92dp) — 美团/淘宝/京东...平台切换
│   └── 右侧内容区 — 搜索框 + 店铺列表 (RecyclerView)
│       └── 店铺卡片 — 左滑展开编辑/自动续时/删除
└── 底部添加店铺栏 (52dp) — [+ 添加店铺]
```

### 3.3 需要新增的数据模型

```kotlin
// 平台枚举
data class Platform(
    val id: String,           // "meituan", "taobao", "jd"...
    val name: String,         // "美团外卖"
    val logoRes: Int,         // R.drawable.ic_logo_meituan
    val packageName: String   // "com.sankuai.meituan.merchant"
)

// 店铺（基于 AppInfo 扩展）
data class Shop(
    val appInfo: AppInfo,     // 底层分身应用信息
    val shopName: String,     // 店铺名称（优先显示，非应用名）
    val shopId: String,       // 店铺ID
    val platform: Platform,   // 所属平台
    val remainingDays: Int,   // 剩余天数
    val autoRenew: Boolean,   // 自动续时状态
    val createdAt: Long       // 添加时间
)

// 用户信息
data class UserProfile(
    val username: String,
    val phone: String,
    val avatarText: String,   // 头像文字（如"管"）
    val shopCount: Int,
    val platformCount: Int,
    val computeBalance: Int
)

// 交易日志
data class LogEntry(
    val id: String,
    val type: LogType,        // CONSUME / OUT / IN
    val date: String,         // "2026-05-19"
    val time: String,         // "16:30:00"
    val amount: Int,
    val platform: String?,
    val shopName: String?,
    val fromPhone: String?,
    val fromName: String?,
    val toPhone: String?,
    val toName: String?
)

enum class LogType { CONSUME, OUT, IN }
```

---

## 4. 关键交互实现分析

### 4.1 店铺卡片左滑（最复杂交互）

**技术方案**：自定义 `RecyclerView.ItemTouchHelper.SimpleCallback` 或使用第三方库（如 `AndroidSwipeLayout` / 自定义 `ItemDecoration`）

**实现要点**：
- 卡片内容层 `translateX` 实时跟随手指
- 右侧固定操作区（编辑 68dp / 自动续时 68dp / 删除 68dp）
- 垂直移动 > 水平移动 → 取消滑动，交给 RecyclerView 滚动
- 滑动结束阈值：68dp（1/3 操作区宽度）
- 收起其他：滑动任一卡片时，记录当前展开 position，其他收起

**推荐方案**：自定义 `SwipeableItemTouchHelper` + `FrameLayout` 双层结构（内容层 + 操作层）

### 4.2 底部抽屉（编辑/删除/修改密码）

**技术方案**：`BottomSheetDialogFragment`

**复用点**：
- 所有抽屉共用 `BaseBottomSheetFragment`
- 动画统一：translateY 300ms, `cubic-bezier(0.32, 0.72, 0, 1)`
- 遮罩：黑色 40% 透明度 fade in/out 200ms

### 4.3 平台切换

**实现**：左侧 `RecyclerView`（垂直）+ 右侧 `RecyclerView`（垂直店铺列表）
- 平台项选中态：背景 `#e8f5e9` + 左侧 3dp 绿色指示条
- 切换平台时，右侧 Adapter swap data，无动画

---

## 5. 资源需求

### 5.1 图标（Vector Drawable）

根据 `assets-spec.md`，需新增约 20 个 Vector Drawable：
- Tab 图标：`ic_home`, `ic_profile`
- 操作图标：`ic_search`, `ic_edit`, `ic_check`, `ic_delete`, `ic_plus`
- 导航图标：`ic_arrow_right`, `ic_arrow_back`
- 菜单图标：`ic_gift`, `ic_log`, `ic_password`, `ic_settings`, `ic_pencil`
- 状态图标：`ic_empty`, `ic_upload`

### 5.2 平台 Logo

`assets/logo/` 已有 6 张 PNG，需转换为各 DPI 的 WebP：
- meituan, qianniu, jd, kuaishou, xiaohongshu, koubei

### 5.3 颜色与主题

需替换 `res/values/colors.xml` 和 `res/values/themes.xml`：
- 主色调从当前 BlackBox 主题改为 `#059669` 科技绿
- 背景改为 `#f5f6f8`
- 文字色阶按 `design-spec.md` 定义

---

## 6. 依赖分析

### 6.1 现有依赖可用

| 依赖 | 用途 | 新设计是否复用 |
|------|------|--------------|
| Material Dialogs | 弹窗 | 复用（登录错误、删除确认等） |
| ViewBinding | 视图绑定 | 复用 |
| ViewModel + LiveData | MVVM | 复用 |
| RecyclerView + RVAdapter | 列表 | 复用（需适配 Swipe） |
| kotlinx.coroutines | 异步 | 复用 |

### 6.2 可能需要新增

| 库 | 用途 | 是否必须 |
|---|------|---------|
| `androidx.swiperefreshlayout` | 下拉刷新（交易日志） | P2 |
| `com.google.android.material:material` (已存在) | BottomSheet | 已有 |

---

## 7. 风险评估

| 风险 | 等级 | 缓解措施 |
|------|------|---------|
| 左滑手势与引擎现有触摸拦截冲突 | 🔴 高 | 隔离 Swipe 逻辑，不依赖全局 TouchListener |
| 现有 Xposed/Hook 功能被误删 | 🟡 中 | 保留 `view/osmdroid`, `view/xp` 等目录，仅移除入口 |
| 主题色全面替换导致编译错误 | 🟡 中 | 增量替换，保留旧主题作为 `Theme.BlackBox.Legacy` |
| 引擎 IPC 在新生命周期中失效 | 🔴 高 | `HomeActivity.onCreate` 中显式调用 `ensureEngineConnection()` |
| 店铺数据来源（服务端 vs 本地） | 🟡 中 | MVP 阶段使用本地 Mock 数据 + AppInfo 映射 |

---

## 8. 与前期 Phase 的关联

| Phase | 成果 | Phase 8 如何使用 |
|-------|------|-----------------|
| Phase 1 | 单实例模式 | 保留，从设置页控制 |
| Phase 4 | ShopIdExtractor | 直接驱动店铺列表数据展示 |
| Phase 5 | Engine IPC | 核心依赖，`EngineProxy` 唤起分身应用 |
| Phase 6 | 鉴权计费 | `UserProfile` 数据来源，算力余额展示 |
| Phase 7 | 统一权限 | 保留，首次启动时统一申请 |

---

## 9. 结论

Phase 8 本质上是一次 **UI 层全面重写**，底层引擎能力（Phase 5）和数据提取能力（Phase 4）可直接复用。

**核心工作量**：
1. 5 个新 Activity + 对应 XML Layout
2. 自定义 Swipe RecyclerView Item
3. 底部抽屉组件封装
4. 主题色全面替换
5. 数据模型扩展 + Mock 数据层
6. 导航流重构（登录 → 首页 ↔ 子页）

**推荐采用 MVP 模式**：先实现「登录页 + 首页 + 个人中心」核心路径，再叠加「算力赠送 + 交易日志」。
