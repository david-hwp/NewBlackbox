# Phase 5 架构走查报告 — Engine IPC Split

> 审查日期: 2026-06-04
> 审查范围: `:Bcore` → Application 转型、`:engine-aidl` 提取、`:app` IPC 绑定层
> 审查深度: 架构级（模块依赖、运行时行为、安全边界）

---

## 严重 (Critical)

### ~~CRIT-1: `:app` 缺少 `BIND_ENGINE` 权限声明~~ ✅ 已修复

**位置**: `app/src/main/AndroidManifest.xml`

**问题**: `:Bcore` 的 AndroidManifest.xml 中声明了自定义权限，但 `:app` 未声明 `uses-permission`。当 app 尝试 `bindService()` 时，系统会因权限不足而拒绝。

**修复**: 在 `app/src/main/AndroidManifest.xml` 中添加了：
```xml
<uses-permission android:name="top.niunaijun.blackbox.engine.permission.BIND_ENGINE" />
```

---

## 警告 (Warning)

### ~~WARN-1: `EngineApp.onCreate()` 中 `startService()` 在 Android 8.0+ 可能崩溃~~ ✅ 已修复

**位置**: `Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineApp.kt:24`

**问题**: Engine APK 被安装后，如果没有前台 Activity 在运行，`EngineApp.onCreate()` 中调用 `startService()` 在 API 26+ 会抛出 `IllegalStateException`。

**修复**: 移除了 `onCreate()` 中的 `startService()`，改为依赖 `bindService()` 按需启动。绑定请求会自动拉起 Service，无需显式 start。

```kotlin
override fun onCreate() {
    super.onCreate()
    BlackBoxCore.get().doCreate()
    // BlackBoxEngineService is started on-demand via bindService()
}
```

### ~~WARN-2: ProGuard 可能混淆 AIDL 接口和实体类~~ ✅ 已确认无需修改

**位置**: `Bcore/proguard-rules.pro`

**问题**: Bcore 启用了 `minifyEnabled true`，担心 ProGuard 会混淆 AIDL 接口和实体类。

**结论**: 现有 `proguard-rules.pro` 中已有 `-keep class top.niunaijun.blackbox.** {*; }`，该规则已经完整覆盖了 `IBlackBoxEngine`、`engine-aidl` 中的所有实体类以及 `core.system` 下的 AIDL 接口。无需额外修改。

### WARN-3: `EngineInstallActivity` 验证绑定后立即 unbind，导致全局 EngineProxy 断开

**位置**: `app/src/main/java/top/niunaijun/blackboxa/view/splash/EngineInstallActivity.kt:201`

**问题**: `verifyEngineAndProceed()` 中：
```kotlin
if (connected) {
    connection.unbind(this@EngineInstallActivity)  // ← 这里会断开
}
```
`EngineConnection.unbind()` 会将静态变量 `engine` 设为 null，并调用 `EngineProxy.disconnect()`。

这意味着验证通过后，全局 `EngineProxy` 是断开状态。`MainActivity` 启动后必须重新执行绑定流程。如果 `MainActivity` 或其 ViewModel 没有主动调用 `EngineConnection.bind()`，所有 Engine API 调用都会返回默认值。

**修复 ✅**: 在 `MainActivity.onCreate()` 中添加了 `ensureEngineConnection()` 方法，在 Activity 启动时检查连接状态，如果未连接且 engine 已安装，自动重新绑定。

### ~~WARN-4: `file_paths.xml` 缺少外部存储共享路径~~ ✅ 已确认无需修改

**位置**: `app/src/main/res/xml/file_paths.xml`

**问题**: 当前只配置了 `files-path`、`cache-path`、`external-files-path`。如果用户从外部存储（如下载目录）选择 Engine APK 进行手动升级，`FileProvider` 可能无法生成 URI。

**结论**: 当前引擎 APK 是从 assets 复制到 `filesDir/engine/` 的，`files-path` 已经覆盖。如后续支持外部 APK 升级，再添加 `external-path`。

---

---

## 信息 (Info)

### INFO-1: `copyEngineApk` 仅处理 debug variant

**位置**: `app/build.gradle:64`

**问题**: `copyEngineApk` 从 `${project(':Bcore').buildDir}/outputs/apk/debug` 复制，且文件名匹配 `BlackBoxEngine_*_debug.apk`。构建 release 版 app 时，此任务会失败（找不到 debug engine APK）。

**建议**: 为 release variant 添加对应的 engine release 构建和复制逻辑，或暂时禁用 release 构建中的 `copyEngineApk`（在 release 中从 CI 产物目录复制）。

### INFO-2: `IBlackBoxEngine.aidl` 缺少 `oneway` 标注

**位置**: `engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl`

**问题**: 部分方法（如 `sendLogs`、`triggerShopIdExtract`）是异步/通知性质的，当前是同步调用。如果 Engine Service 响应缓慢，主线程调用方可能 ANR。

**建议**: 评估以下方法是否应标记为 `oneway`：
- `sendLogs`
- `triggerShopIdExtract`
- `clearPackage`
- `stopPackage`

### INFO-3: `LauncherActivity` 在 Engine APK 中可能仍有入口

**位置**: `Bcore/src/main/AndroidManifest.xml:540`

**问题**: `EngineApp` 中设置了 `isEnableLauncherActivity: false`，但 `LauncherActivity` 仍然在 manifest 中声明。需要确认 `ClientConfiguration.isEnableLauncherActivity()` 是否真的会禁用该 activity（通常需要动态修改 component enabled state）。

**建议**: 验证首次安装 engine APK 后，系统桌面是否会出现 "BlackBox Engine" 图标。如果出现，需要在 `EngineApp.onCreate()` 中显式禁用：
```kotlin
packageManager.setComponentEnabledSetting(
    ComponentName(this, LauncherActivity::class.java),
    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
    PackageManager.DONT_KILL_APP
)
```

---

## 验证清单（回归测试项）

| # | 验证项 | 通过标准 |
|---|--------|---------|
| 1 | Clean 全量构建 | `./gradlew clean :app:assembleDebug` 无错误 |
| 2 | Engine APK 安装 | `adb install` Bcore debug APK 成功，无 launcher 图标 |
| 3 | Host APK 安装 | `adb install` app debug APK 成功 |
| 4 | 首次启动流程 | 启动 app → 显示 EngineInstallActivity → 自动安装 engine → 跳转 MainActivity |
| 5 | Service 绑定 | MainActivity 中 `EngineProxy.isConnected()` 返回 true |
| 6 | 应用安装 | 通过 EngineProxy 安装一个 APK，能在虚拟环境中启动 |
| 7 | 权限验证 | 移除 `uses-permission BIND_ENGINE`，绑定应失败 |
| 8 | ProGuard 验证 | 构建 release 版 engine，确认 IPC 接口类未被混淆 |

---

## 总结

本次重构的核心架构目标（`:Bcore` 独立为 Application、`:engine-aidl` 提供共享接口、`:app` 通过 IPC 调用）**已正确实现**，且全量构建通过。

但在**运行时安全性**和**权限声明**方面存在 **1 个 Critical 遗漏**（app 缺少 BIND_ENGINE 权限），以及 **3 个 Warning**（ProGuard、startService 兼容性、绑定生命周期）。建议在进入 Phase 6 前修复 Critical 和 Warning 级别问题。
