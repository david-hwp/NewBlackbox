# 多店管家 — Android 开发设计交付物

> 产品：多店管家（多平台店铺管理 APP）
> 版本：v1.0
> 目标平台：Android（基准 412dp × 900dp）
> 交付日期：2026-06-05

---

## 交付物清单

```
deliverables/
├── README.md              ← 本文件，交付物总览
├── design-spec.md         ← 设计系统规范（颜色、字体、间距、组件）
├── screen-specs.md        ← 5 个屏幕逐像素标注
├── interaction-spec.md    ← 交互规范（手势、状态、动画、数据）
└── assets-spec.md         ← 资源清单（切图、图标、字体、尺寸）
```

---

## 快速导航

| 文档 | 内容 | 开发人员 |
|------|------|---------|
| **design-spec.md** | 颜色系统、字体规范、间距网格、组件参数、Android Theme XML | UI 开发 + 视觉 QA |
| **screen-specs.md** | 5 个屏幕的完整布局结构、逐元素尺寸颜色标注、状态矩阵 | UI 开发 |
| **interaction-spec.md** | 手势规则、状态机、动画曲线、数据模型、错误处理 | 交互开发 + 后端对接 |
| **assets-spec.md** | 6 个平台 Logo、20+ 图标、应用图标、尺寸对照表 | 资源管理 + 切图 |

---

## 5 个屏幕总览

| # | 屏幕 | 文件 | 核心功能 |
|---|------|------|---------|
| 1 | **登录页** | `screens/login.html` → `LoginActivity` | 账号密码登录 |
| 2 | **首页** | `screens/home.html` → `HomeActivity` | 左侧平台栏 + 右侧店铺列表 + 左滑操作 |
| 3 | **个人中心** | `screens/profile.html` → `ProfileActivity` | 用户信息 + 菜单 + 问题反馈 + 底部导航 |
| 4 | **算力赠送** | `screens/trade.html` → `GiftActivity` | 赠送算力 + 二次确认 |
| 5 | **交易日志** | `screens/logs.html` → `LogsActivity` | 筛选 + 按日期分组的交易记录 |

---

## 设计系统速查

### 主色调
- **Accent（主操作色）**：`#059669`（科技绿）
- **背景**：`#f5f6f8`
- **卡片/表面**：`#ffffff`
- **主文字**：`#1e293b`
- **次要文字**：`#475569`
- **辅助文字**：`#64748b`
- **边框/分割**：`#e2e8f0`
- **危险**：`#dc2626`
- **警告**：`#f59e0b`

### 字体
- **中文**：Roboto / Noto Sans CJK SC（系统默认）
- **数字/等宽**：Roboto Mono
- **字号范围**：10sp ~ 26sp

### 圆角
- 小：6dp（标签）
- 中：10dp（搜索框、图标背景）
- 大：12dp（卡片、按钮、输入框）
- 超大：16dp（用户卡片）
- 抽屉：20dp

### 触摸目标
- **最小**：48dp × 48dp
- **按钮高度**：44dp ~ 52dp

---

## 开发优先级建议

```
P0 — 核心路径（MVP）
  ├─ 登录页 UI + 登录逻辑
  ├─ 首页：平台切换 + 店铺列表渲染
  ├─ 首页：店铺卡片左滑 + 删除 + 编辑 + 自动续时
  ├─ 个人中心：用户信息展示 + 底部导航
  └─ 交易日志：列表展示 + 筛选

P1 — 增值功能
  ├─ 算力赠送：表单 + 二次确认
  ├─ 个人中心：修改用户名 + 修改密码
  ├─ 问题反馈：文本 + 图片上传
  └─ 搜索过滤

P2 — 优化项
  ├─ 首次使用滑动提示
  ├─ 空状态占位
  ├─ 骨架屏 loading
  └─ 下拉刷新 + 上拉加载
```

---

## 相关文件

- **原型预览**：`compute-manager-android.html`
- **独立屏幕**：`screens/login.html`、`screens/home.html`、`screens/profile.html`、`screens/trade.html`、`screens/log.html`
- **Logo 素材**：`assets/logo/`（6 个平台商家版 Logo）

---

*由 Open Design 生成，可直接用于 Android 原生开发。*
