# 多店管家 — 资源清单（Assets Spec）

> 版本：v1.0 | DPI 基准：mdpi(1×), hdpi(1.5×), xhdpi(2×), xxhdpi(3×), xxxhdpi(4×)

---

## 1. 平台 Logo（必须提供）

| 资源名 | 用途 | 尺寸（xhdpi） | 格式 | 来源 |
|--------|------|--------------|------|------|
| `ic_logo_meituan` | 美团外卖商家版 | 40dp × 40dp | PNG/WebP | 应用宝下载 |
| `ic_logo_qianniu` | 千牛/淘宝商家版 | 40dp × 40dp | PNG/WebP | 应用宝下载 |
| `ic_logo_jd` | 京东秒送商家版 | 40dp × 40dp | PNG/WebP | 应用宝下载 |
| `ic_logo_kuaishou` | 快手小店商家版 | 40dp × 40dp | PNG/WebP | 应用宝下载 |
| `ic_logo_xiaohongshu` | 小红书商家版 | 40dp × 40dp | PNG/WebP | 应用宝下载 |
| `ic_logo_koubei` | 阿里本地通 | 40dp × 40dp | PNG/WebP | 应用宝下载 |

**存放路径：**
```
res/drawable-xhdpi/
  ic_logo_meituan.png      (80×80px)
  ic_logo_qianniu.png      (80×80px)
  ic_logo_jd.png           (80×80px)
  ic_logo_kuaishou.png     (80×80px)
  ic_logo_xiaohongshu.png  (80×80px)
  ic_logo_koubei.png       (80×80px)
```

**裁剪要求：**
- 圆形裁剪，无白边
- 透明背景或白色背景
- 在圆形 ImageView 中使用 `android:scaleType="centerCrop"`
- 统一添加 1dp `#e2e8f0` 描边

---

## 2. 图标（SVG → Vector Drawable）

以下图标建议使用 **Vector Drawable**（Android Studio 导入 SVG），统一 24dp 视图框。

| 图标名 | 用途 | 所在页面 | 线条粗细 | 尺寸 |
|--------|------|---------|---------|------|
| `ic_home` | 首页 Tab | 底部导航 | 2px | 24dp |
| `ic_profile` | 我的 Tab | 底部导航 | 2px | 24dp |
| `ic_search` | 搜索 | 首页 | 2px | 16dp |
| `ic_edit` | 编辑 | 左滑菜单/用户名 | 2px | 18dp |
| `ic_check` | 自动续时/勾选 | 左滑菜单/三角标 | 2px | 18dp |
| `ic_delete` | 删除 | 左滑菜单 | 2px | 18dp |
| `ic_arrow_right` | 菜单箭头 | 个人中心菜单 | 2px | 18dp |
| `ic_arrow_back` | 返回 | 子页面标题栏 | 2px | 20dp |
| `ic_plus` | 添加 | 添加店铺按钮 | 2.5px | 18dp |
| `ic_gift` | 算力赠送 | 个人中心菜单 | 2px | 20dp |
| `ic_log` | 交易日志 | 个人中心菜单 | 2px | 20dp |
| `ic_password` | 修改密码 | 个人中心菜单 | 2px | 20dp |
| `ic_settings` | 系统设置 | 个人中心菜单 | 2px | 20dp |
| `ic_pencil` | 编辑用户名 | 用户名右侧 | 2px | 16dp |
| `ic_empty` | 空状态 | 首页/搜索 | 1.5px | 48dp |
| `ic_upload` | 上传图片 | 问题反馈 | 2px | 24dp |
| `ic_remove` | 删除图片 | 上传网格 | - | 12dp (文字 ×) |

**存放路径：**
```
res/drawable/
  ic_home.xml
  ic_profile.xml
  ic_search.xml
  ic_edit.xml
  ic_check.xml
  ic_delete.xml
  ic_arrow_right.xml
  ic_arrow_back.xml
  ic_plus.xml
  ...
```

---

## 3. 应用图标（Launcher Icon）

| 资源名 | 尺寸（各DPI） | 用途 |
|--------|-------------|------|
| `ic_launcher_foreground` | 108dp 视图框 | 自适应图标前景 |
| `ic_launcher_background` | 108dp 视图框 | 自适应图标背景 |

**设计要求：**
- 前景：绿色背景上的白色「多」字或品牌图形
- 背景：纯色 `#059669`
- 遵循 Android Adaptive Icon 规范（支持圆形/方形/圆角方形裁切）

---

## 4. 占位图/缺省图

| 资源名 | 用途 | 尺寸 | 说明 |
|--------|------|------|------|
| `placeholder_shop` | 店铺Logo加载失败 | 44dp × 44dp | 灰色圆形 + 平台首字 |
| `placeholder_empty` | 空状态图标 | 48dp × 48dp | 线框搜索/空盒子图标 |
| `placeholder_image` | 上传图片占位 | 自适应 | 4色循环彩色块 |

---

## 5. 颜色资源（已完整定义）

见 `design-spec.md` 第 1.5 节 `Android Theme 映射`。

**文件：** `res/values/colors.xml`

---

## 6. 字体资源

| 字体 | 用途 | 文件 |
|------|------|------|
| Roboto | 中文正文、标题 | 系统自带，无需打包 |
| Roboto Mono | 数字、手机号、ID | 系统自带，无需打包 |
| Noto Sans CJK SC | 中文优化（可选） | 如需自定义，放 `res/font/` |

**注意：** 如无特殊需求，直接使用系统默认字体即可，无需额外引入。

---

## 7. 动画资源

| 资源名 | 类型 | 用途 | 参数 |
|--------|------|------|------|
| `slide_up_sheet` | XML Animator | 底部抽屉弹出 | 见 interaction-spec |
| `slide_down_sheet` | XML Animator | 底部抽屉关闭 | 见 interaction-spec |
| `fade_in` | XML Animator | 遮罩/Toast 出现 | 150-200ms |
| `fade_out` | XML Animator | 遮罩/Toast 消失 | 150-200ms |
| `slide_in_right` | XML Animator | 页面进入 | 200ms |
| `slide_out_right` | XML Animator | 页面退出 | 200ms |
| `scale_press` | XML Animator | 按钮按下 | 100ms，scale 0.98 |

---

## 8. 已下载的 Logo 图片

以下图片已存在于项目 `assets/logo/` 目录，可直接用于开发参考：

```
assets/logo/
  meituan.png       → res/drawable-xhdpi/ic_logo_meituan.webp
  qianniu.png       → res/drawable-xhdpi/ic_logo_qianniu.webp
  jd.png            → res/drawable-xhdpi/ic_logo_jd.webp
  kuaishou.png      → res/drawable-xhdpi/ic_logo_kuaishou.webp
  xiaohongshu.png   → res/drawable-xhdpi/ic_logo_xiaohongshu.webp
  koubei.png        → res/drawable-xhdpi/ic_logo_koubei.webp
```

**转换建议：** 使用 Android Studio 的 `Convert to WebP` 功能压缩，减少包体积。

---

## 9. 资源尺寸对照表

### 图标尺寸（dp → px）

| dp | mdpi (1×) | hdpi (1.5×) | xhdpi (2×) | xxhdpi (3×) | xxxhdpi (4×) |
|-----|-----------|-------------|------------|-------------|--------------|
| 16 | 16 | 24 | 32 | 48 | 64 |
| 18 | 18 | 27 | 36 | 54 | 72 |
| 20 | 20 | 30 | 40 | 60 | 80 |
| 24 | 24 | 36 | 48 | 72 | 96 |
| 28 | 28 | 42 | 56 | 84 | 112 |
| 32 | 32 | 48 | 64 | 96 | 128 |
| 36 | 36 | 54 | 72 | 108 | 144 |
| 40 | 40 | 60 | 80 | 120 | 160 |
| 44 | 44 | 66 | 88 | 132 | 176 |
| 48 | 48 | 72 | 96 | 144 | 192 |
| 56 | 56 | 84 | 112 | 168 | 224 |
| 72 | 72 | 108 | 144 | 216 | 288 |

### Logo 图片尺寸（圆形显示，各 DPI）

| DPI | 尺寸 |
|-----|------|
| mdpi | 40 × 40 px |
| hdpi | 60 × 60 px |
| xhdpi | 80 × 80 px |
| xxhdpi | 120 × 120 px |
| xxxhdpi | 160 × 160 px |

---

## 10. 包体积预估

| 资源类型 | 预估大小 | 优化建议 |
|----------|---------|---------|
| 平台 Logo（6张 WebP）| ~150KB | 使用 WebP 格式 |
| Vector Icons（~20个）| ~50KB | 极简路径，复用 shape |
| 应用图标（5个DPI）| ~100KB | 使用 Adaptive Icon |
| 动画资源（XML）| ~10KB | 纯 XML，极小 |
| 字体 | 0KB | 使用系统字体 |
| **总计** | **~310KB** | - |
