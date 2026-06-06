# Phase 2 调研：虚拟环境内置 Google WebView

## 问题定义

不同手机厂商使用各自的 WebView 实现，导致 BlackBox 虚拟环境中的分身应用出现兼容性问题：
- **Honor/Huawei**: `com.hihonor.webview` / `com.huawei.webview`
- **Xiaomi**: 系统 WebView 可能缺失或版本过旧
- **Samsung**: `com.samsung.android.webview`
- **OnePlus**: 可能有定制化实现

当前 BlackBox 通过 hook `IWebViewUpdateService` 和 `WebViewFactory` 来适配，但每新增一个 ROM 都需要手动适配。

## 方案分析：Bundle Google WebView APK

### 核心原理

Android WebView 的加载流程：

```
App: new WebView(context)
  ↓
WebViewFactory.getProvider()          ← 我们在这里 hook
  ↓
getWebViewProviderClass()             ← 返回 WebView 实现类
  ↓
getWebViewProviderClassLoader()       ← 加载 APK 的 ClassLoader
  ↓
加载 com.google.android.webview 中的 Chromium WebView
```

如果我们能在 `getWebViewProviderClass()` 中返回内置 APK 的 WebView 实现类，所有虚拟 App 就会统一使用同一个 WebView。

### 技术可行性

**可行。** 当前代码已经有 3 个 Proxy 类拦截 WebView 初始化：

| 文件 | 作用 |
|------|------|
| `WebViewFactoryProxy.java` | Hook `getWebViewProviderClass()`, `getWebViewProviderPackage()`, `getWebViewProviderClassLoader()` |
| `IWebViewUpdateServiceProxy.java` | Hook `getCurrentWebViewPackage()`, `getValidWebViewPackages()`, `isWebViewPackage()` |
| `WebViewProxy.java` | Hook WebView 构造方法，配置数据目录隔离 |

当前 `WebViewFactoryProxy` 的 fallback 已经返回 `com.google.android.webview`，但问题是虚拟环境中这个包不一定存在。

### 实现方案

**方案 A：内置 APK + DexClassLoader（推荐）**

1. 将 `com.google.android.webview` APK 放入 `app/src/main/assets/webview/webview.apk`
2. App 首次启动时，将 APK 复制到 `/data/data/{host}/files/bwebview/webview.apk`
3. 创建一个 DexClassLoader 指向这个 APK
4. 在 `WebViewFactoryProxy.getWebViewProviderClass()` 中，使用这个 ClassLoader 加载 `com.android.webview.chromium.WebViewChromiumFactoryProvider`
5. 在 `WebViewFactoryProxy.getWebViewProviderPackage()` 中返回 `com.google.android.webview`

**方案 B：虚拟环境安装**

1. 将 WebView APK 放入 assets
2. 使用 `BPackageInstallerService` 将 APK "安装"到虚拟环境
3. Hook 指向虚拟环境中的包

问题：WebView 需要系统级权限（sharedUserId 等），普通 APK 安装不一定能工作。

**方案 C：GeckoView（已排除）**

API 不兼容（`org.mozilla.geckoview.GeckoView` ≠ `android.webkit.WebView`），需要重写所有 WebView API，不可行。

### 推荐方案：方案 A

理由：
- 不依赖虚拟环境的包管理系统
- 直接控制 WebView 的类加载
- APK 体积可控（~40MB）
- 与现有 hook 体系兼容

## 关键代码路径

### 1. WebViewFactoryProxy 增强点

```java
// WebViewFactoryProxy.java
@ProxyMethod("getWebViewProviderClass")
public static class GetWebViewProviderClass extends MethodHook {
    @Override
    protected Object hook(Object who, Method method, Object[] args) throws Throwable {
        // 如果用户开启了"使用内置 WebView"
        if (BlackBoxCore.get().getClientConfiguration().isUseBuiltinWebView()) {
            ClassLoader loader = BWebViewLoader.getClassLoader();
            if (loader != null) {
                return loader.loadClass("com.android.webview.chromium.WebViewChromiumFactoryProvider");
            }
        }
        // fallback 到原有逻辑
        return method.invoke(who, args);
    }
}
```

### 2. IWebViewUpdateServiceProxy 增强点

```java
// 在 getCurrentWebViewPackage() 中
if (builtinMode) {
    PackageInfo info = new PackageInfo();
    info.packageName = "com.google.android.webview";
    info.versionName = BWebViewLoader.getVersion();
    return info;
}
```

### 3. 数据目录隔离

`WebViewProxy.java` 已经为每个虚拟 App 创建了独立的 WebView 数据目录（`webview_{userId}_{pid}`），内置 WebView 可以继续使用这个机制。

## Google WebView APK 获取

| 来源 | 方法 | 可靠性 |
|------|------|--------|
| Android Studio Emulator | 从模拟器提取 `/system/app/WebViewGoogle/` | 高 |
| APKMirror | 下载 `com.google.android.webview` | 高（需验证签名） |
| AOSP 构建 | 自行编译 `external/chromium-webview` | 高（工作量大） |
| Gradle 依赖 | `org.chromium:chromium-webview` 不存在 | 不可行 |

**推荐**：从 APKMirror 下载稳定版，构建时校验 SHA256。

## 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|----------|
| 内置 APK 体积过大（40MB+） | 高 | 中 | ABI 分离，只打包目标架构 |
| WebView 更新滞后 | 中 | 低 | 定期更新内置 APK 版本 |
| ClassLoader 冲突 | 低 | 高 | 严格隔离 ClassLoader 命名空间 |
| 某些 ROM 阻止非系统 WebView | 中 | 高 | 保留系统 WebView fallback |
| 安全更新缺失 | 中 | 中 | 用户可选择切换回系统 WebView |

## 设置开关设计

在 `ClientConfiguration` 中新增：

```java
public boolean isUseBuiltinWebView() {
    return false;  // 默认关闭，让用户手动开启
}
```

在 Settings 页面添加 Switch：
- 开启后需要重启 BlackBox
- 首次开启时自动解压内置 APK

## 工作量估计

| 任务 | 文件 | 工作量 |
|------|------|--------|
| BWebViewLoader（APK 解压 + ClassLoader 管理） | 新建 | 4h |
| WebViewFactoryProxy 增强 | 修改 | 2h |
| IWebViewUpdateServiceProxy 增强 | 修改 | 2h |
| ClientConfiguration 接口扩展 | 修改 | 1h |
| Settings UI 开关 | 修改 | 1h |
| 构建脚本（APK 下载/校验） | 新建 | 2h |
| 测试（多 ROM 验证） | — | 4h |
| **总计** | | **~16h** |
