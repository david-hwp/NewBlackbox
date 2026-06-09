# Attempt 03: WebView Sandbox Fix — 实现记录

**Date**: 2026-06-02
**Direction**: 方向 C — 修复 WebView 相关检测点
**Device**: Pixel Emulator (sdk_gphone64_arm64), Android API 36
**Status**: 编译通过 ✅ | E2E **无法验证** ⚠️ (Pixel 启动失败)

---

## 背景

JD 秒送的验证码组件使用 WebView 渲染。在 Honor 设备上，WebView 的 sandboxed process 可能绕过 BlackBox 的 Java 层 hooks，导致 JD SDK 检测到虚拟环境。

## 实现

### 修改文件

1. **`Bcore/src/main/java/top/niunaijun/blackbox/fake/service/WebViewProxy.java`**
   - **移除 User-Agent 中的 "BlackBox" 字样**
   - 原代码：在 User-Agent 中主动添加 "BlackBox"（会泄露虚拟环境信息）
   - 新代码：如果 User-Agent 包含 "BlackBox"，则移除它

2. **`Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IWebViewUpdateServiceProxy.java`**
   - **添加 `com.hihonor.webview` 到已知 WebView 包名列表**
   - Honor/Huawei 设备使用 `com.hihonor.webview` 作为 WebView provider
   - 之前只支持 Google、Huawei、Samsung、OnePlus 的 WebView

3. **`Bcore/src/main/java/top/niunaijun/blackbox/fake/service/WebViewFactoryProxy.java`**
   - **添加 `getDefaultWebViewPackage()` 动态检测方法**
   - 优先检测 `com.hihonor.webview`，然后是 `com.huawei.webview`，最后是 `com.google.android.webview`
   - 避免在 Honor 设备上硬编码使用 Google WebView

### 编译结果

```
BUILD SUCCESSFUL
APK: BlackBox_4.0.0_universal-debug.apk
```

### Pixel 模拟器 E2E

- APK 安装成功 ✅
- BlackBox 主界面正常显示 ✅
- 京东秒送图标存在 ✅
- **启动 JD 秒送仍然失败**（userfaultfd 问题，与方向 C 无关）❌

## 与已有 WebView 处理的对比

BlackBox 基线代码已包含以下 WebView 处理：
- `IPackageManagerProxy`: 移除 sandboxed process 的 `isolatedProcess` 标志
- `WebViewFactoryProxy`: 处理 WebView provider 包名和 ClassLoader
- `IWebViewUpdateServiceProxy`: 禁用多进程模式 (`isMultiProcessEnabled` 返回 false)

方向 C 在此基础上补充了：
1. **User-Agent 清理**（基线代码主动添加 "BlackBox"，会泄露环境）
2. **Honor WebView 包名识别**（基线不支持 `com.hihonor.webview`）
3. **动态 WebView 包名检测**（避免硬编码）

## 结论

- 方向 C 的实现是合理的，覆盖了 Honor 设备特有的 WebView 检测点
- **无法在 Pixel 模拟器上验证**，因为 Pixel 模拟器存在独立的启动问题（userfaultfd）
- 需要在 Honor 设备上验证效果
- **方向 C 判定为：实现完成，待 Honor 设备验证**

## 下一步

1. 回滚到基线
2. 继续尝试**方向 D**（Intent 查询过滤 + 系统属性伪装）
3. 等 Honor 设备可用时，一并验证方向 C 和 D

---

## 回滚命令

```bash
git checkout -- Bcore/src/main/java/top/niunaijun/blackbox/fake/service/WebViewProxy.java \
                 Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IWebViewUpdateServiceProxy.java \
                 Bcore/src/main/java/top/niunaijun/blackbox/fake/service/WebViewFactoryProxy.java
```
