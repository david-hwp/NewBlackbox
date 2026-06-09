# 基线 E2E 测试结果（Pixel 模拟器）— 修正版

**Date**: 2026-06-02
**Device**: Pixel Emulator (sdk_gphone64_arm64)
**Android**: API 36 (Android 16)
**Branch**: feature-hwp (baseline)
**Resolution**: 1080x2424

---

## 测试步骤

1. 编译基线 APK 并安装到 Pixel 模拟器 ✅
2. 启动 BlackBox，京东秒送商家已添加 ✅
3. 启动京东秒送 ❌ **启动失败**

## 关键发现

### 1. 启动失败 — 黑屏/ANR

从 BlackBox 点击京东秒送图标启动 JD 应用后：
- 屏幕变黑
- 约 5-10 秒后出现 ANR 对话框（"BlackBox 没有响应"）
- 点击"等待"后，屏幕仍然保持黑屏
- 等待 60+ 秒后，JD 登录页仍然无法显示

### 2. 根本原因分析（来自 logcat）

```
# 启动超时
14:17:12.586 W ActivityTaskManager: Launch timeout has expired, giving up wake lock!

# userfaultfd 不支持（Android 15/16 变化）
14:17:31.815 W .blackbox:black: userfaultfd: MOVE ioctl seems unsupported: Connection timed out
14:17:37.670 W jun.blackbox:p1: userfaultfd: MOVE ioctl seems unsupported: Connection timed out

# SELinux 拒绝
14:17:02.977 W jd.mrd.jingming: avc: denied { read } for name="tcp" dev="proc"
14:17:03.281 W jd.mrd.jingming: avc: denied { read } for name="max_map_count" dev="proc"
```

**核心问题**: BlackBox 的虚拟应用启动机制在 Android 16 (Pixel 模拟器) 上存在兼容性问题：
1. `userfaultfd` 系统调用在 Pixel 模拟器上不被支持
2. 虚拟应用启动超时（>10秒）
3. SELinux 策略限制了 /proc 访问

### 3. 验证码功能本身是正常的

当 JD 应用**已经成功加载**时（通过其他方式保持运行状态），验证码功能可以正常工作：
- "验证码登录"标签可以正常点击切换 ✅
- 手机号可以输入 ✅
- 点击"获取验证码"后，"安全验证"对话框（滑块拼图）正常出现 ✅

这说明 JD SDK 在 Pixel 模拟器上**没有**检测到虚拟环境并禁用验证码功能。

### 4. 与 Honor 设备的差异

| 设备 | 启动 | 验证码 |
|------|------|--------|
| Pixel 模拟器 (Android 16) | ❌ 启动失败（黑屏/ANR） | 如果加载成功则 ✅ 正常 |
| Honor ABR-AN00 (Android 16) | ✅ 启动正常 | ❌ 验证码被禁用 |

这说明 Pixel 模拟器和 Honor 设备上遇到的问题**不是同一个问题**：
- Pixel: BlackBox 兼容性问题（启动失败）
- Honor: JD SDK 反作弊检测（验证码被禁用）

## 坐标修正

之前的测试脚本使用了错误的坐标（基于 1920x1080）。正确的 Pixel 模拟器坐标（1080x2424）：

| 元素 | 坐标 |
|------|------|
| 京东秒送图标 | (135, 394) |
| 验证码登录标签 | (538, 451) |
| 手机号输入框 | (603, 626) |
| 获取验证码按钮 | (937, 639) |
| 验证码输入框 | (603, 794) |

## 结论

- **基线版本在 Pixel 模拟器上启动 JD 失败**，这是 BlackBox 本身的 Android 16 兼容性问题
- 一旦启动成功，验证码功能是正常的
- Pixel 模拟器可能**不适合**作为验证 JD captcha evasion 的主要测试平台
- 需要在 Honor 设备上直接验证修改效果

## 下一步

1. 尝试修复 BlackBox 在 Android 16 上的启动问题（userfaultfd 兼容性）
2. 或者直接在 Honor 设备上测试各个方向
3. 继续尝试方向 C（WebView sandboxed process）或 D（Intent 查询过滤 + 系统属性伪装）
