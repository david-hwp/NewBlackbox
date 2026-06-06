# Phase 05: Engine IPC 拆分 - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning
**Source:** discuss-phase + eng-review

<domain>
## Phase Boundary

将 BlackBox 的 Bcore 引擎从 `com.android.library` 拆分为独立的 APK 插件，主管理 APK 通过 AIDL 跨进程调用 Engine。

### 范围
- ✅ 创建 `:engine-plugin` 模块（`com.android.application`）
- ✅ Bcore 源码迁移到 Engine APK
- ✅ 统一 AIDL 入口 `IBlackBoxEngine`
- ✅ 主 APK `EngineProxy` 代理层
- ✅ 动态加载 Engine（DexClassLoader + so 提取）
- ✅ Engine 无界面（无 Activity，纯 Service）
- ✅ 同签名 + 无 sharedUserId（竞品验证过的模式）
- ✅ 签名校验（Engine bindService 时校验主 APK 签名指纹）
- ✅ 主 APK 内置默认版本 Engine APK（assets 目录）
- ✅ 首次安装自动释放内置 Engine
- ✅ 在线升级机制（Engine APK 独立版本号，主 APK 下载更新）

### 非范围
- ❌ 应用图标加载（新 UI 不需要图标，使用默认占位图）
- ❌ 摇杆功能（RockerManager）—— Phase 5 不包含，Phase 7 或移除
- ❌ Activity 生命周期钩子（Engine 无界面，无需 onBefore/AfterMainActivityOnCreate）
- ❌ 用户登录（Phase 6）
- ❌ 分身计费（Phase 6）
- ❌ 数据上报（Engine 零上报，主 APK 负责）
</domain>

<decisions>
## Implementation Decisions

### 架构设计
- **同签名，无 sharedUserId**：竞品（小X分身）已验证的可行路径
- **Engine 无界面**：不声明 LAUNCHER Activity，不在最近任务显示
- **Engine 零业务逻辑**：只运行虚拟化内核，不感知用户/计费/上报
- **主 APK 动态加载**：DexClassLoader 加载 Engine classes，System.load() 加载 so
- **AIDL 统一入口**：`IBlackBoxEngine.aidl` 封装所有 Engine 能力

### 进程模型
- 主 APK 进程：UI + 业务逻辑
- Engine APK 进程：DaemonService + 虚拟 App 运行
- 虚拟 App 运行在 Engine 进程内（由 Engine 的 manifest 声明代理组件）

### Native 库加载
- Engine APK 打包 so 文件
- 主 APK 首次加载时从 Engine APK 提取 so 到私有目录
- 使用 `System.load(absolutePath)` 替代 `System.loadLibrary()`

### 资源访问
- Engine 的 `R.java` 资源通过 `createPackageContext()` 访问
- 添加 `ResourceBridge` 桥接层处理跨 APK 资源引用

### 签名策略
- 主 APK 和 Engine APK 使用相同 keystore 签名
- Engine `onBind()` 时校验调用者签名指纹
- 只允许主 APK package name 绑定 Engine Service

### 内置 Engine
- 主 APK `assets/engine/` 目录内置默认 Engine APK
- 首次启动时检测 Engine 是否已安装：
  - 未安装 → 从 assets 释放到私有目录 → 安装为独立应用
  - 已安装但版本旧 → 提示升级或自动升级（配置决定）
- `EngineInstaller` 负责释放、安装、签名校验

### 在线升级
- Engine 独立 versionCode / versionName
- 主 APK 启动时检查 Engine 版本（本地 vs 远程）
- 下载新 Engine APK → 签名校验 → 原子替换 → 重启加载
- 升级失败自动回滚到备份版本
- 升级流程与内置 Engine 释放共用同一套安装逻辑
</decisions>

<canonical_refs>
## Canonical References

### Engine 侧核心文件
- `Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java` — 引擎初始化入口
- `Bcore/src/main/java/top/niunaijun/blackbox/core/NativeCore.java` — Native 库加载
- `Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java` — 数据路径配置
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/ServiceManager.java` — 内部 AIDL 服务注册
- `Bcore/src/main/java/top/niunaijun/blackbox/fake/frameworks/BlackManager.java` — Facade 基类（已封装 RemoteException）

### 主 APK 侧调用点
- `app/src/main/java/top/niunaijun/blackboxa/app/App.kt` — Application 初始化
- `app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt` — BlackBoxCore 初始化封装
- `app/src/main/java/top/niunaijun/blackboxa/data/AppsRepository.kt` — 43 处 Bcore 调用
- `app/src/main/java/top/niunaijun/blackboxa/data/GmsRepository.kt` — GMS 相关调用
- `app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt` — sendLogs 调用

### AIDL 接口
- `Bcore/src/main/aidl/top/niunaijun/blackbox/core/system/pm/IBPackageManagerService.aidl`
- `Bcore/src/main/aidl/top/niunaijun/blackbox/core/system/am/IBActivityManagerService.aidl`
- `Bcore/src/main/aidl/top/niunaijun/blackbox/core/system/user/IBUserManagerService.aidl`
- `Bcore/src/main/aidl/top/niunaijun/blackbox/core/system/location/IBLocationManagerService.aidl`
</canonical_refs>

<specifics>
## Specific Ideas

### Engine APK 包名
`top.niunaijun.blackbox.engine`

### 主 APK 包名（不变）
`top.niunaijun.blackboxa`

### 模块依赖关系
```
:app (com.android.application)
  ├── :engine-aidl (com.android.library) — AIDL 接口共享
  └── :black-reflection (com.android.library) — 反射工具

:engine-plugin (com.android.application) — Engine APK
  ├── :black-reflection
  └── :compiler (annotationProcessor)

:engine-aidl (com.android.library) — 编译时共享
  └── 所有 AIDL 接口文件
```

### 关键 AIDL 接口设计
```aidl
interface IBlackBoxEngine {
    int getVersionCode();
    String getVersionName();
    
    // Core
    boolean launchApk(String packageName, int userId);
    InstallResult installPackageAsUser(String path, int userId);
    void uninstallPackageAsUser(String packageName, int userId);
    List<ApplicationInfo> getInstalledApplications(int flags, int userId);
    List<BUserInfo> getUsers();
    BUserInfo createUser(int userId);
    void deleteUser(int userId);
    
    // Facades
    IBPackageManagerService getPackageManager();
    IBActivityManagerService getActivityManager();
    IBLocationManagerService getLocationManager();
    IBUserManagerService getUserManager();
    
    // GMS
    boolean isSupportGms();
    boolean isInstallGms(int userId);
    InstallResult installGms(int userId);
    boolean uninstallGms(int userId);
    
    // Logging
    void sendLogs(String caption, boolean async);
    
    // ShopId (Phase 4)
    void registerShopCallback(IEngineShopCallback callback);
    void triggerShopIdExtract(String packageName, int userId);
    
    // Auth (for Phase 6)
    void registerSession(String sessionId, long expireAt);
    void unregisterSession();
    boolean isSessionActive();
    
    // Lifecycle
    void addServiceAvailableCallback(IServiceAvailableCallback callback);
}
```

### Native 库列表（需从 Engine APK 提取）
- `libblackbox.so` — 核心 Native Hook
- `libdobby.so` — Hook 框架
- `libxdl.so` — 动态符号解析
</specifics>

<deferred>
## Deferred Ideas

- 摇杆功能跨进程化（RockerManager）
- Engine 多实例支持（一个主 APK 绑定多个 Engine）
- Engine 插件市场（第三方 Engine 分发）
- 主 APK 与 Engine 不同签名（当前方案要求同签名）
</deferred>

---

*Phase: 05-engine-ipc*
*Context gathered: 2026-06-03 via discuss-phase + eng-review*
