# 多店管家 — Android 设计系统规范

> 交付版本：v1.0 | 目标平台：Android (Pixel 标准 412dp × 900dp) | 设计基准：1dp = 1px

---

## 1. 颜色系统

### 1.1 中性色阶

| Token | Hex | OKLch | 用途 |
|-------|-----|-------|------|
| `--bg` | `#f5f6f8` | oklch(97% 0.003 250) | 页面背景 |
| `--surface` | `#ffffff` | oklch(100% 0 0) | 卡片、面板、输入框背景 |
| `--fg` | `#1e293b` | oklch(30% 0.02 250) | 主标题、正文 |
| `--fg-2` | `#475569` | oklch(50% 0.018 250) | 次要文字、标签 |
| `--muted` | `#64748b` | oklch(60% 0.015 250) | 辅助文字、图标默认态 |
| `--meta` | `#94a3b8` | oklch(75% 0.012 250) | 占位符、禁用态、hint |
| `--border` | `#e2e8f0` | oklch(90% 0.008 250) | 分割线、边框、描边 |

### 1.2 品牌色

| Token | Hex | 用途 |
|-------|-----|------|
| `--accent` | `#059669` | 主按钮、选中态、成功态、Tab激活、开关开启 |
| `--accent-hover` | `#047857` | 主按钮按下态 |
| `--accent-light` | `#d1fae5` | 聚焦光环阴影色、选中背景、轻量提示背景 |

### 1.3 语义色

| Token | Hex | 用途 |
|-------|-----|------|
| `--danger` | `#dc2626` | 删除按钮、错误提示、剩余天数 ≤ 3 天 |
| `--danger-light` | `#fee2e2` | 危险态轻量背景 |
| `--warn` | `#f59e0b` | 警告、剩余天数 ≤ 7 天 |
| `--warn-bg` | `#fefce8` | 警告提示背景 |
| `--warn-fg` | `#a16207` | 警告提示文字 |
| `--info-bg` | `#e0f2fe` | 信息提示背景 |
| `--info-fg` | `#0284c7` | 信息提示文字 |

### 1.4 平台品牌色（仅用于左侧栏图标背景，实际用Logo图片替代）

| 平台 | Hex | 对应CSS变量 |
|------|-----|-------------|
| 美团外卖 | `#ff4d4f` | `--brand-meituan` |
| 淘宝/千牛 | `#fa8c16` | `--brand-taobao` |
| 京东闪送 | `#2f54eb` | `--brand-jd` |
| 快手小店 | `#52c41a` | `--brand-kuaishou` |
| 小红书 | `#eb2f96` | `--brand-xhs` |
| 阿里本地通 | `#fa541c` | `--brand-ali` |

### 1.5 Android Theme 映射

```xml
<!-- res/values/colors.xml -->
<color name="bg">#f5f6f8</color>
<color name="surface">#ffffff</color>
<color name="fg">#1e293b</color>
<color name="fg_2">#475569</color>
<color name="muted">#64748b</color>
<color name="meta">#94a3b8</color>
<color name="border">#e2e8f0</color>
<color name="accent">#059669</color>
<color name="accent_hover">#047857</color>
<color name="accent_light">#d1fae5</color>
<color name="danger">#dc2626</color>
<color name="danger_light">#fee2e2</color>
<color name="warn">#f59e0b</color>
<color name="warn_bg">#fefce8</color>
<color name="warn_fg">#a16207</color>
<color name="info_bg">#e0f2fe</color>
<color name="info_fg">#0284c7</color>
```

---

## 2. 字体系统

### 2.1 字体栈

| 用途 | Font Stack | Android 对应 |
|------|-----------|-------------|
| 正文 / 标题 | `-apple-system, BlinkMacSystemFont, 'Inter', 'Segoe UI', system-ui, sans-serif` | `Roboto` (默认) 或 `Noto Sans CJK SC` |
| 数字 / 等宽 | `'JetBrains Mono', ui-monospace, Menlo, monospace` | `Roboto Mono` |

### 2.2 字号规范

| Token | Size | Line Height | Weight | Letter Spacing | 用途 |
|-------|------|-------------|--------|----------------|------|
| `--text-xs` | 10px | 1.4 | 400 | 0 | 顶部meta标签、平台计数 |
| `--text-sm` | 11px | 1.4 | 400 | 0 | 平台名称、滑动按钮文字、badge |
| `--text-base` | 12px | 1.4 | 400 | 0 | 店铺ID、剩余天数、筛选标签、时间 |
| `--text-md` | 13px | 1.5 | 500 | 0 | 表单标签、分组标题、Toast、日志详情 |
| `--text-lg` | 14px | 1.5 | 500 | 0 | 正文、店铺名称、菜单项、表单输入 |
| `--text-xl` | 15px | 1.4 | 700 | -0.01em | 页面标题、顶部栏品牌名 |
| `--text-2xl` | 16px | 1.3 | 600 | -0.015em | 账号手机号、算力余额、输入框 |
| `--text-3xl` | 17px | 1.3 | 700 | -0.02em | 用户名、抽屉标题 |
| `--text-4xl` | 18px | 1.2 | 700 | -0.02em | 抽屉主标题、确认弹窗标题 |
| `--text-5xl` | 20px | 1.1 | 700 | -0.02em | 大金额数字（日志右侧） |
| `--text-6xl` | 24px | 1.1 | 700 | -0.02em | APP名称（登录页） |
| `--text-mono-lg` | 26px | 1.1 | 700 | 0.02em | 日志金额（等宽） |

### 2.3 Android TextAppearance 样式

```xml
<!-- res/values/styles.xml -->
<style name="TextAppearance.Duodian.Title1" parent="TextAppearance.Material3.TitleLarge">
    <item name="android:textSize">24sp</item>
    <item name="android:textStyle">bold</item>
    <item name="android:letterSpacing">-0.02</item>
</style>

<style name="TextAppearance.Duodian.Title2" parent="TextAppearance.Material3.TitleMedium">
    <item name="android:textSize">18sp</item>
    <item name="android:textStyle">bold</item>
    <item name="android:letterSpacing">-0.02</item>
</style>

<style name="TextAppearance.Duodian.Headline" parent="TextAppearance.Material3.HeadlineSmall">
    <item name="android:textSize">17sp</item>
    <item name="android:textStyle">bold</item>
</style>

<style name="TextAppearance.Duodian.Body1" parent="TextAppearance.Material3.BodyLarge">
    <item name="android:textSize">16sp</item>
    <item name="android:textStyle">bold</item>
</style>

<style name="TextAppearance.Duodian.Body2" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:textSize">14sp</item>
    <item name="android:textStyle">normal</item>
</style>

<style name="TextAppearance.Duodian.Caption" parent="TextAppearance.Material3.BodySmall">
    <item name="android:textSize">12sp</item>
    <item name="android:textColor">@color/muted</item>
</style>

<style name="TextAppearance.Duodian.Mono" parent="TextAppearance.Material3.BodyMedium">
    <item name="android:fontFamily">monospace</item>
    <item name="android:textSize">16sp</item>
    <item name="android:textStyle">bold</item>
    <item name="android:letterSpacing">0.02</item>
</style>

<style name="TextAppearance.Duodian.MonoLarge" parent="TextAppearance.Duodian.Mono">
    <item name="android:textSize">26sp</item>
</style>
```

---

## 3. 间距系统

### 3.1 基础间距 Token

| Token | Value | 用途 |
|-------|-------|------|
| `--space-1` | 4px | 微小间距、icon与文字间隙 |
| `--space-2` | 6px | 紧凑元素间距 |
| `--space-3` | 8px | 小组件内边距、筛选标签间隙 |
| `--space-4` | 10px | 卡片内紧凑间距 |
| `--space-5` | 12px | 标准卡片内边距、卡片间距 |
| `--space-6` | 14px | 卡片主体padding |
| `--space-7` | 16px | 页面水平padding、表单字段间距 |
| `--space-8` | 20px | 卡片padding、抽屉padding |
| `--space-9` | 24px | Logo区域padding、大卡片padding |
| `--space-10` | 28px | 抽屉底部安全区padding |

### 3.2 圆角系统

| Token | Value | 用途 |
|-------|-------|------|
| `--radius-sm` | 6px | 筛选标签、小按钮 |
| `--radius-md` | 10px | 搜索框、输入框、菜单图标背景、上传区域 |
| `--radius-lg` | 12px | 卡片、按钮、表单区域、账号卡片 |
| `--radius-xl` | 16px | Logo图标、用户头像背景 |
| `--radius-2xl` | 20px | 底部抽屉圆角 |
| `--radius-full` | 50% | 圆形头像、店铺Logo、平台图标 |

---

## 4. 阴影与Elevation

| 层级 | 阴影值 | 用途 |
|------|--------|------|
| Flat | none | 大部分卡片（靠边框区分） |
| Subtle | `0 -4px 20px rgba(0,0,0,0.04)` | 登录页底部表单区域上浮 |
| Toast | `0 4px 20px rgba(0,0,0,0.15)` | Toast提示 |
| Drawer | `0 -8px 32px rgba(0,0,0,0.12)` | 底部抽屉顶部阴影 |

---

## 5. 组件规范

### 5.1 按钮

#### 主按钮 `.btn-primary`
- 宽度：100%（撑满容器）
- 高度：52dp（padding 16px 垂直，约 52dp 总高）
- 背景：`--accent` (#059669)
- 文字：白色，16sp，Weight 600
- 圆角：12dp
- 按下态：背景变为 `--accent-hover` (#047857)，scale 0.98
- 禁用态：背景 `#a7f3d0`，文字白色 50% 透明度

#### 次要按钮 `.btn-secondary` / `.sheet-btn.cancel`
- 宽度：flex: 1（等分）
- 高度：48dp
- 背景：`--bg` (#f5f6f8)
- 文字：`--fg-2` (#475569)，15sp，Weight 600
- 圆角：12dp
- 按下态：背景 `--border` (#e2e8f0)

#### 危险按钮 `.sheet-btn.danger` / `.delete-sheet-btn.confirm`
- 同主按钮尺寸
- 背景：`--danger` (#dc2626)
- 按下态：#b91c1c

#### 底部栏按钮 `.add-shop-btn`
- 宽度：100%
- 高度：44dp（padding 12px 垂直）
- 背景：`--accent`
- 文字：白色，14sp，Weight 600
- 圆角：10dp

### 5.2 输入框

#### 文本输入 `.input`
- 宽度：100%
- 高度：48dp（padding 14px 垂直 + 1.5px 边框）
- 背景：`--bg` (#f5f6f8)
- 边框：1.5px solid `--border` (#e2e8f0)
- 圆角：12dp
- 文字：16sp，Weight 400，`--fg`
- 占位符：14sp，`--meta` (#94a3b8)
- 聚焦态：边框 `--accent` (#059669)，外发光 `box-shadow: 0 0 0 3px #d1fae5`
- 禁用态：背景 `#f1f5f9`，文字 `--meta`

### 5.3 卡片

#### 店铺卡片 `.swipe-card`
- 宽度：100%（内容区撑满）
- 背景：`--surface` (白色)
- 边框：1px solid `--border`
- 圆角：12dp
- 内边距：14px（主体区域）
- 左侧滑动操作区：宽 204dp（3个按钮 × 68dp）

#### 用户信息卡片 `.user-card`
- 背景：白色
- 边框：1px solid `--border`
- 圆角：16dp
- padding：24px 20px

#### 账号信息卡片 `.my-account`
- 背景：白色
- 边框：1px solid `--border`
- 圆角：12dp
- padding：14px 16px

### 5.4 菜单项 `.menu-item`
- 宽度：100%
- 高度：56dp（padding 16px 垂直）
- 背景：白色
- 底边框：1px solid `--border`
- 文字：15sp，Weight 500，`--fg`
- 按下态：背景 `--bg`

#### 菜单图标 `.menu-icon`
- 尺寸：36dp × 36dp
- 圆角：10dp
- 图标：20dp × 20dp，居中

| 菜单项 | 背景色 | 图标色 |
|--------|--------|--------|
| 算力赠送 | `#ecfdf5` | `#059669` |
| 交易日志 | `#eff6ff` | `#3b82f6` |
| 修改密码 | `#fef3c7` | `#f59e0b` |
| 系统设置 | `#eff6ff` | `#3b82f6` |

### 5.5 Badge / 标签

#### 日志类型 Badge
- padding：2px 8px
- 圆角：6dp
- 字号：11sp，Weight 600

| 类型 | 背景 | 文字 |
|------|------|------|
| 消耗 | `#e0f2fe` | `#0284c7` |
| 转出 | `#fef3c7` | `#d97706` |
| 转入 | `#d1fae5` | `#059669` |

#### 自动续时状态（右上角三角标）
- 绿色三角形：`border-top: 28px solid #059669`
- 白色勾号：11sp，Weight 800，位于三角形内右上角

### 5.6 底部导航 `.bottom-nav`
- 高度：56dp + safe-area-inset-bottom
- 背景：白色
- 顶边框：1px solid `--border`
- Tab 项：垂直排列 icon + label，24dp icon + 11sp label
- 未选中：`--muted` (#64748b)
- 选中：`--accent` (#059669)

### 5.7 底部抽屉 `.sheet-panel` / `.delete-sheet` / `.edit-sheet`
- 宽度：100%
- 圆角：20dp 20dp 0 0
- 背景：白色
- padding：20px 16px 28px
- 动画：从底部 translateY(100%) → translateY(0)，300ms，`cubic-bezier(0.32, 0.72, 0, 1)`
- 遮罩：黑色 40% 透明度，fade in/out 200ms

### 5.8 Toast
- 背景：rgba(0,0,0,0.75)
- 文字：白色，14sp
- padding：12px 24px
- 圆角：10dp
- 位置：屏幕正中央
- 持续时间：1800ms
- 动画：淡入淡出

### 5.9 搜索框 `.search-box`
- 高度：40dp
- 背景：白色
- 边框：1px solid `--border`
- 圆角：10dp
- padding：8px 12px
- 左侧搜索 icon：16dp × 16dp，`--meta` 色

### 5.10 开关（Toggle）

#### 自动续时开关（左滑菜单内）
- 轨道宽：40dp，高：24dp
- 轨道圆角：12dp（pill 形状）
- 开启态：背景 `--accent`
- 关闭态：背景 `--border`
- 滑块：20dp 圆形，白色，shadow 2dp
- 动画：200ms ease

---

## 6. 布局网格

### 6.1 屏幕基准

- **设计宽度**：412dp（Pixel 6/7 标准宽度）
- **设计高度**：900dp（含状态栏，不含系统导航栏）
- **安全区顶部**：24dp（状态栏高度）
- **安全区底部**：16dp + navigation bar 高度（用 `WindowInsets` 动态获取）

### 6.2 首页左右分栏

```
┌─────────────────────────────────────┐ ← 顶部栏 (56dp)
│ Logo + 多店管家    我的账号 138****  │
├─────────────────────────────────────┤ ← 算力信息条 (40dp)
│ 算力余额 200              [交易日志] │
├──────────┬──────────────────────────┤
│          │  🔍 搜索店名/ID           │
│  美团外卖 │ ┌──────────────────────┐ │
│  [图标]   │ │ [logo] 店铺名    7天  │ │
│   2家    │ │ ID: xxx              │ │
│          │ └──────────────────────┘ │
│  淘宝网购 │ ...                      │
│  [图标]   │                          │
│   1家    │                          │
│          │                          │
│    ...   │                          │
│          │                          │
├──────────┴──────────────────────────┤
│          [+ 添加店铺]                │ ← 底部按钮栏 (52dp)
└─────────────────────────────────────┘
```

- **左侧平台栏**：92dp 固定宽
- **右侧内容区**：flex: 1（412 - 92 = 320dp 可用宽）
- **内容区水平padding**：12dp
- **卡片间距**：10dp
