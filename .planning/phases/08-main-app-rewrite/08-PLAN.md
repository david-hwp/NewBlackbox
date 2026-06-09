# Phase 8 计划：主 APK 改造为多店管家

## 目标

将 BlackBox 主 APK（`app` 模块）从虚拟引擎管理工具改造为"多店管家"多平台店铺管理 APP，基于 `docs/原型设计` 中的 5 屏交互设计规范实现全新 UI 层，同时完整保留 Phase 5 Engine IPC 能力和 Phase 4 ShopId 提取能力。

**范围**：`app` 模块 UI 层全面重写 + 数据模型扩展 + 主题替换。不涉及 `Bcore` 引擎层修改。

---

## 任务分解

### P0 — 核心路径（MVP，必须先完成）

#### Task 1: 主题与资源基础替换
- **文件**:
  - `app/src/main/res/values/colors.xml` — 替换为多店管家色阶
  - `app/src/main/res/values/themes.xml` — 新建 `Theme.Duodian` 主题
  - `app/src/main/res/values/styles.xml` — 添加 TextAppearance 样式
  - 保留旧主题 `Theme.BlackBox` 作为 Legacy（不删除，防止编译中断）
- **交付标准**: `app:assembleDebug` 编译通过，主题色显示为 `#059669`

#### Task 2: 数据模型扩展
- **文件**:
  - `app/src/main/java/.../bean/Platform.kt` — 平台枚举（美团/淘宝/京东/快手/小红书/阿里）
  - `app/src/main/java/.../bean/Shop.kt` — 店铺数据模型（包装 AppInfo + 扩展字段）
  - `app/src/main/java/.../bean/UserProfile.kt` — 用户信息
  - `app/src/main/java/.../bean/LogEntry.kt` — 交易日志数据模型
  - `app/src/main/java/.../data/ShopRepository.kt` — 店铺数据仓库（Mock 层 + EngineProxy 映射）
- **交付标准**: 数据类可编译，Repository 返回 Mock 数据列表

#### Task 3: 图标与图片资源导入
- **文件**:
  - `app/src/main/res/drawable/ic_*.xml` — 约 20 个 Vector Drawable（按 assets-spec.md）
  - `app/src/main/res/drawable-*/ic_logo_*.webp` — 6 个平台 Logo（多 DPI）
  - `app/src/main/res/drawable/placeholder_*.xml` — 占位图
- **交付标准**: 所有图标可在 Android Studio 预览中正常显示

#### Task 4: 登录页 `LoginActivity`
- **文件**:
  - `app/src/main/java/.../view/login/LoginActivity.kt`
  - `app/src/main/res/layout/activity_login.xml`
- **功能**:
  - 账号/密码输入框（带聚焦态边框变绿 + 外发光）
  - 登录按钮（52dp 高，主按钮样式）
  - 加载态（按钮文字变为「登录中…」）
  - 错误态（边框变红 + 错误提示）
  - 校验：非空校验
  - 登录成功后跳转 `HomeActivity`
- **交付标准**: UI 像素级匹配 screen-spec.md，点击登录可跳转

#### Task 5: 首页 `HomeActivity`（最复杂屏幕）
- **文件**:
  - `app/src/main/java/.../view/home/HomeActivity.kt`
  - `app/src/main/java/.../view/home/PlatformSidebarAdapter.kt`
  - `app/src/main/java/.../view/home/ShopListAdapter.kt`
  - `app/src/main/java/.../view/home/ShopSwipeHelper.kt` — 左滑手势处理
  - `app/src/main/java/.../view/home/HomeViewModel.kt`
  - `app/src/main/res/layout/activity_home.xml`
  - `app/src/main/res/layout/item_platform.xml`
  - `app/src/main/res/layout/item_shop_card.xml`
  - `app/src/main/res/layout/item_shop_swipe_actions.xml`
- **功能**:
  - 顶部栏（Logo + 多店管家 | 我的账号）
  - 算力信息条（算力余额 + 交易日志按钮）
  - 左侧平台栏（92dp，6 个平台，选中态绿色指示条）
  - 右侧店铺列表（搜索框 + RecyclerView）
  - 店铺卡片左滑（编辑/自动续时/删除）
  - 底部「添加店铺」按钮 → 跳转 `ListActivity`
  - 点击店铺卡片 → Toast「正在打开…」→ `EngineProxy.launchApk()`
- **交付标准**:
  - 左右分栏布局正确
  - 平台切换即时刷新列表
  - 左滑展开/收起动画流畅（280ms, cubic-bezier）
  - 搜索框实时过滤（店铺名或 ID）

#### Task 6: 底部抽屉组件封装
- **文件**:
  - `app/src/main/java/.../view/base/BaseBottomSheetFragment.kt`
  - `app/src/main/java/.../view/dialog/EditShopSheetFragment.kt`
  - `app/src/main/java/.../view/dialog/DeleteShopSheetFragment.kt`
- **功能**:
  - 通用抽屉动画（从底部 slide up 300ms）
  - 编辑店铺：输入框预填 + 保存校验
  - 删除确认：文案 + 确认/取消按钮
- **交付标准**: 所有抽屉动画参数匹配 interaction-spec.md

#### Task 7: 个人中心 `ProfileActivity`
- **文件**:
  - `app/src/main/java/.../view/profile/ProfileActivity.kt`
  - `app/src/main/java/.../view/profile/ProfileViewModel.kt`
  - `app/src/main/res/layout/activity_profile.xml`
- **功能**:
  - 用户卡片（头像 + 用户名 + 手机号 + 统计行）
  - 算力管理菜单（算力赠送、交易日志）
  - 账号设置菜单（修改密码、系统设置）
  - 问题反馈区（文本输入 + 图片上传占位）
  - 底部导航（首页/我的）
- **交付标准**: UI 匹配设计规范，菜单点击可跳转

#### Task 8: 应用入口与导航流重构
- **文件**:
  - `app/src/main/AndroidManifest.xml` — 调整 Activity 声明和 intent-filter
- **改动**:
  - `EngineInstallActivity` 保持 LAUNCHER，但完成后跳转 `LoginActivity`
  - `LoginActivity` → 登录成功 → `HomeActivity`
  - `HomeActivity` ←→ `ProfileActivity`（底部导航）
  - `HomeActivity` → `ListActivity`（添加店铺）
  - `ProfileActivity` → `GiftActivity` / `LogsActivity` / `SettingActivity`
- **交付标准**: 应用启动后进入登录页，核心导航路径无阻断

---

### P1 — 增值功能（P0 完成后开发）

#### Task 9: 算力赠送 `GiftActivity`
- **文件**:
  - `app/src/main/java/.../view/gift/GiftActivity.kt`
  - `app/src/main/res/layout/activity_gift.xml`
- **功能**:
  - 账号信息卡片（手机号 + 算力余额）
  - 对方手机号 + 赠送数量输入
  - 表单校验（非空、数量 > 0）
  - 二次确认底部弹窗（遮罩 + 信息展示 + 确认/取消）
  - 赠送成功反馈（按钮变绿 + 1.5s 后重置）
- **交付标准**: 表单校验正确，弹窗动画流畅

#### Task 10: 交易日志 `LogsActivity`
- **文件**:
  - `app/src/main/java/.../view/logs/LogsActivity.kt`
  - `app/src/main/java/.../view/logs/LogsAdapter.kt`
  - `app/src/main/java/.../view/logs/LogsViewModel.kt`
  - `app/src/main/res/layout/activity_logs.xml`
  - `app/src/main/res/layout/item_log_entry.xml`
- **功能**:
  - 顶部筛选标签（全部/转出/转入/消耗）
  - 按日期分组列表
  - 日志卡片（Badge + 交易信息 + 金额 + 时间）
  - 金额颜色规则（消耗 info / 转出 warn / 转入 accent）
- **交付标准**: 筛选切换即时响应，日期分组正确

#### Task 11: 修改用户名/密码抽屉
- **文件**:
  - `app/src/main/java/.../view/dialog/EditUsernameSheetFragment.kt`
  - `app/src/main/java/.../view/dialog/ChangePasswordSheetFragment.kt`
- **功能**:
  - 修改用户名：输入框 + 保存
  - 修改密码：双输入框 + 三项校验（非空、≥6位、一致）
- **交付标准**: 校验失败时 Toast/红色文字提示

---

### P2 — 优化项（可延后）

#### Task 12: 首次使用滑动提示
- 首页首次加载时显示「左滑可编辑…」浮动提示
- 2.5s 后自动消失，SharedPreferences 标记

#### Task 13: 空状态与骨架屏
- 搜索无结果时显示空状态图标 + 文案
- 列表加载时显示骨架屏占位

#### Task 14: 下拉刷新与上拉加载
- 交易日志支持下拉刷新
- 店铺列表支持上拉加载更多

#### Task 15: 动画资源 XML 化
- `res/anim/` 或 `res/animator/` 中定义交互动画
- 页面转场动画（登录→首页淡入上滑，子页推入推出）

---

## 依赖关系图

```
Task 1 (主题资源)
    ↓
Task 2 (数据模型) ←────→ Task 3 (图标资源)
    ↓                       ↓
    └──────────→ Task 4 (登录页) ──→ Task 8 (入口导航)
                        ↓
              Task 5 (首页) ←──→ Task 6 (抽屉组件)
                        ↓
              Task 7 (个人中心)
                        ↓
              Task 9 (算力赠送) / Task 10 (交易日志)
                        ↓
              Task 11 (修改用户名/密码)
```

---

## 文件清单汇总

### 新增文件（约 30+）

```
app/src/main/java/top/niunaijun/blackboxa/
├── bean/
│   ├── Platform.kt
│   ├── Shop.kt
│   ├── UserProfile.kt
│   └── LogEntry.kt
├── data/
│   └── ShopRepository.kt
├── view/
│   ├── login/
│   │   └── LoginActivity.kt
│   ├── home/
│   │   ├── HomeActivity.kt
│   │   ├── HomeViewModel.kt
│   │   ├── PlatformSidebarAdapter.kt
│   │   ├── ShopListAdapter.kt
│   │   └── ShopSwipeHelper.kt
│   ├── profile/
│   │   ├── ProfileActivity.kt
│   │   └── ProfileViewModel.kt
│   ├── gift/
│   │   └── GiftActivity.kt
│   ├── logs/
│   │   ├── LogsActivity.kt
│   │   ├── LogsAdapter.kt
│   │   └── LogsViewModel.kt
│   └── dialog/
│       ├── BaseBottomSheetFragment.kt
│       ├── EditShopSheetFragment.kt
│       ├── DeleteShopSheetFragment.kt
│       ├── EditUsernameSheetFragment.kt
│       └── ChangePasswordSheetFragment.kt

app/src/main/res/
├── layout/
│   ├── activity_login.xml
│   ├── activity_home.xml
│   ├── activity_profile.xml
│   ├── activity_gift.xml
│   ├── activity_logs.xml
│   ├── item_platform.xml
│   ├── item_shop_card.xml
│   ├── item_shop_swipe_actions.xml
│   └── item_log_entry.xml
├── drawable/
│   ├── ic_home.xml
│   ├── ic_profile.xml
│   ├── ic_search.xml
│   ├── ic_edit.xml
│   ├── ic_check.xml
│   ├── ic_delete.xml
│   ├── ic_arrow_right.xml
│   ├── ic_arrow_back.xml
│   ├── ic_plus.xml
│   ├── ic_gift.xml
│   ├── ic_log.xml
│   ├── ic_password.xml
│   ├── ic_settings.xml
│   ├── ic_pencil.xml
│   ├── ic_empty.xml
│   ├── ic_upload.xml
│   ├── placeholder_shop.xml
│   ├── placeholder_empty.xml
│   └── placeholder_image.xml
├── drawable-xhdpi/ (and other dpi)
│   ├── ic_logo_meituan.webp
│   ├── ic_logo_qianniu.webp
│   ├── ic_logo_jd.webp
│   ├── ic_logo_kuaishou.webp
│   ├── ic_logo_xiaohongshu.webp
│   └── ic_logo_koubei.webp
└── values/
    └── styles.xml (新增 TextAppearance 样式)
```

### 修改文件

```
app/src/main/
├── AndroidManifest.xml — 调整 Activity 入口和 intent-filter
├── java/.../view/main/MainActivity.kt — 重命名为/替换为 HomeActivity
├── java/.../view/apps/AppsFragment.kt — 功能迁移到 ShopListAdapter
├── res/values/colors.xml — 替换色阶
├── res/values/themes.xml — 新建主题
└── res/values/strings.xml — 新增中文文案
```

---

## 验证标准

### 编译验证
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew :app:assembleDebug --no-daemon
# 必须 BUILD SUCCESSFUL
```

### UI 验证
1. 启动应用进入登录页，UI 像素级匹配 `screen-specs.md`
2. 登录后进入首页，左右分栏布局正确
3. 点击平台切换，右侧列表即时刷新
4. 左滑卡片展开编辑/自动续时/删除按钮
5. 点击店铺卡片，Toast 提示并唤起对应分身应用
6. 底部导航切换首页/个人中心

### 功能验证
1. 搜索框输入关键词，列表实时过滤
2. 点击「添加店铺」跳转 APK 选择页
3. 个人中心菜单项可正常跳转
4. 算力赠送表单校验正确
5. 交易日志按日期分组显示正确

---

## 回滚计划

若改造过程中发现引擎 IPC 不兼容新 Activity 生命周期：
1. 保留旧 `MainActivity` 作为 `LegacyMainActivity`
2. 在 `HomeActivity` 中通过 `EngineProxy` 重新初始化连接
3. 最坏情况：UI 层回滚到旧版，新设计作为 `Flavor` 分支维护

---

## 时间估算

| 优先级 | 任务数 | 预估工时 |
|--------|--------|---------|
| P0 | 8 个 | 3-4 天 |
| P1 | 3 个 | 2 天 |
| P2 | 4 个 | 1-2 天 |
| **总计** | **15 个** | **6-8 天** |
