# 竞品分析：小X分身（原多开分身）

> 调研时间：2026-05-25
> 调研对象：小X分身 APP（包名：com.bly.dkplat）
> 调研方式：ADB 逆向分析 + 动态进程监控 + 文件系统扫描

---

## 一、基本信息

| 属性 | 内容 |
|------|------|
| **应用名称** | 小X分身（原"多开分身"） |
| **宿主包名** | `com.bly.dkplat` |
| **插件/引擎包名** | `dkplugin.ctu.zjw` |
| **虚拟引擎** | Chaos 虚拟引擎（混沌引擎） |
| **核心库** | `libchaos.so`、`libchaos1.so` |
| **支持系统** | Android 5 - Android 14 |
| **架构特点** | 宿主+插件双 APK 架构 |

---

## 二、整体架构分析

### 2.1 双 APK 架构设计

小X分身采用了**宿主+插件**的双 APK 架构，这是其与大多数开源虚拟化框架（如 BlackBox、VirtualApp）最核心的架构差异：

```
┌─────────────────────────────────────────┐
│           用户层（UI/交互）                │
│         com.bly.dkplat (宿主)            │
│  ┌─────────────────────────────────┐    │
│  │      Chaos 虚拟引擎层            │    │
│  │   ┌─────────────────────────┐   │    │
│  │   │    虚拟 Framework       │   │    │
│  │   │  ┌─────────────────┐    │   │    │
│  │   │  │  虚拟文件系统    │    │   │    │
│  │   │  │  /data分区      │    │   │    │
│  │   │  │  ┌───────────┐  │    │   │    │
│  │   │  │  │ 目标应用   │  │    │   │    │
│  │   │  │  │ (官方APK)  │  │    │   │    │
│  │   │  │  └───────────┘  │    │   │    │
│  │   │  └─────────────────┘    │   │    │
│  │   └─────────────────────────┘   │    │
│  └─────────────────────────────────┘    │
└─────────────────────────────────────────┘
         ↓                    ↓
    内部安装模式          独立安装模式
    (插件进程运行)        (宿主进程运行)
```

### 2.2 宿主应用（com.bly.dkplat）职责

- **UI 界面**：主界面、应用列表、设置页面
- **广告 SDK**：集成了巨量引擎（Pangle）、快手广告、优量汇等多个广告平台
- **配置管理**：用户备注、应用排序、分身配置
- **独立安装数据存储**：`chaos/data/user/0/` 目录
- **插件管理**：下载、加载 `dkplugin.ctu.zjw` 插件 APK

### 2.3 插件/引擎应用（dkplugin.ctu.zjw）职责

- **Chaos 虚拟引擎核心**：包含 `libchaos.so` 和 `libchaos1.so`
- **内部安装数据存储**：`virtual/data/user/0/` 目录
- **Hook 系统服务**：PackageManager、ActivityManager 等
- **虚拟化运行时**：为分身应用提供独立的运行环境

---

## 三、核心库分析

### 3.1 插件 APK 的 Native 库

插件 `dkplugin.ctu.zjw`（3.4MB）包含以下核心 so 库：

| 库文件 | 大小 | 功能推测 |
|--------|------|----------|
| `libchaos.so` | ~312KB | Chaos 虚拟引擎主库，负责 Framework 层 Hook |
| `libchaos1.so` | ~351KB | Chaos 虚拟引擎辅助库，可能负责进程/内存管理 |
| `libsecsdk.so` | ~349KB | 安全 SDK，可能用于反调试、完整性校验 |
| `libabc.so` | ~72KB | 辅助库，具体功能不明 |

**关键发现**：插件 APK 只有 3.4MB，没有完整的操作系统镜像（`.img`、`.iso` 等），证明"虚拟OS"不是完整的 OS 虚拟化，而是**轻量级的 Framework 层运行时环境模拟**。

### 3.2 宿主 APK 的 Native 库

宿主 `com.bly.dkplat` 除了包含上述 Chaos 引擎库外，还包含大量广告 SDK 的 so 库（如 `libttmplayer_lite.so`、`libavmdl_lite.so` 等），说明其商业化程度较高。

---

## 四、数据目录结构深度分析

### 4.1 宿主应用数据目录（com.bly.dkplat）

```
/data/data/com.bly.dkplat/
├── shared_prefs/          # 宿主配置（广告、用户设置等）
├── files/
│   ├── dkplat/           # 核心配置文件（.member、.uuid）
│   ├── apks/             # 下载的插件 APK
│   │   └── 20260525_035101_dkplugin.ctu.zjw.apk
│   └── file_preferences.json
├── databases/
│   └── dkplat.db         # 核心数据库
│       ├── LastCreate    # 最近创建的分身记录
│       ├── PrivacyDev    # 分身设备配置
│       ├── ChaosCoreCache # 内核版本缓存
│       └── ...
└── chaos/                # 【独立安装数据目录】
    ├── data/
    │   ├── user/         # 用户数据（独立安装）
    │   │   └── 0/
    │   │       └── tv.danmaku.bili/   # bilibili 分身数据
    │   ├── user_de/      # 设备加密数据
    │   │   └── 0/
    │   │       └── tv.danmaku.bili/
    │   └── app/
    │       ├── lib/      # 应用 so 库提取
    │       │   └── tv.danmaku.bili/
    │       │       ├── libijk.so
    │       │       ├── libbili.so
    │       │       └── ...
    │       └── icon/     # 应用图标缓存
    │           └── 0/
    │               └── tv.danmaku.bili.png
    └── system/           # 虚拟系统配置
        ├── plugs.ini     # 插件注册表（二进制格式）
        ├── unabled.ini   # 禁用配置
        └── sort/         # 应用排序配置
```

### 4.2 插件应用数据目录（dkplugin.ctu.zjw）

```
/data/data/dkplugin.ctu.zjw/
├── shared_prefs/
│   └── *.xml            # 插件配置（IS_CHAOS_DEVICE=1 等）
└── virtual/             # 【内部安装数据目录】
    ├── data/
    │   ├── user/        # 用户数据（内部安装）
    │   │   └── 0/
    │   │       └── tv.danmaku.bili/   # bilibili 分身数据
    │   ├── user_de/     # 设备加密数据
    │   │   └── 0/
    │   │       └── tv.danmaku.bili/
    │   └── app/         # 应用元数据
    │       └── 0/
    │           ├── apk/     # （空目录）
    │           └── sign/    # （空目录）
    └── system/          # 虚拟系统配置
```

### 4.3 数据库关键表分析

**dkplat.db 中的关键表：**

| 表名 | 作用 | 关键数据 |
|------|------|----------|
| `LastCreate` | 记录最近创建的分身 | `tv.danmaku.bili\|1779652305099` |
| `PrivacyDev` | 分身隐私设备配置 | `tv.danmaku.bili\|0`（0=正常模式） |
| `ChaosCoreCache` | 内核版本更新历史 | 包含 v13.9 到 v34.0.2 的完整更新链 |
| `DvsConfig` | 分身配置（空表） | 预留配置表 |

**关键发现**：`LastCreate` 表中只有一条 `tv.danmaku.bili` 记录，说明小X分身**不允许同一应用同时以两种模式存在**。

---

## 五、进程模型深度分析

### 5.1 两种模式的同时运行状态

当用户同时启动"内部安装"和"独立安装"的 bilibili 分身时，进程列表如下：

```
USER     PID   PPID  NAME
─────────────────────────────────────────
u0_a247  2090   721  tv.danmaku.bili          ← 内部安装主进程
u0_a247  2769   721  tv.danmaku.bili:ijkservice
u0_a247  3214   721  tv.danmaku.bili:download
u0_a247  4157   721  tv.danmaku.bili:web

u0_a243  10293  721  tv.danmaku.bili          ← 独立安装主进程
u0_a243  10702  721  tv.danmaku.bili:ijkservice
u0_a243  11313  721  tv.danmaku.bili:download
```

**关键发现**：
- 两组进程的 **PPID 都是 721**（zygote64，Android 应用孵化器）
- **UID 不同**：u0_a247（插件）vs u0_a243（宿主）
- **进程名相同**：都是 `tv.danmaku.bili`
- 说明两组进程都是**由系统 zygote fork 出来的独立进程**

### 5.2 Activity Manager 中的任务记录

从 `dumpsys activity activities` 可以清晰看到两种模式的实现差异：

**内部安装的任务记录：**
```
userId=0 effectiveUid=u0a247 mCallingUid=u0a247 mCallingPackage=dkplugin.ctu.zjw
intent={cmp=dkplugin.ctu.zjw/com.bly.chaos.plugin.stub.ActivityStub$P0}
app=ProcessRecord{2090:dkplugin.ctu.zjw:p0/u0a247}
dataDir=/data/user/0/dkplugin.ctu.zjw
```

**独立安装的任务记录：**
```
userId=0 effectiveUid=u0a243 mCallingUid=u0a243 mCallingPackage=com.bly.dkplat
intent={cmp=com.bly.dkplat/com.bly.chaos.plugin.stub.ActivityStub$P0}
app=ProcessRecord{10293:com.bly.dkplat:p0/u0a243}
dataDir=/data/user/0/com.bly.dkplat
```

### 5.3 "Stub"（桩/代理）Activity 机制

两种模式都使用了 `ActivityStub$P0` 代理模式：

| 模式 | Activity 代理类 | 实际作用 |
|------|----------------|---------|
| 内部安装 | `dkplugin.ctu.zjw/com.bly.chaos.plugin.stub.ActivityStub$P0` | 接收 bilibili 的启动 intent，转发给真正的 MainActivity |
| 独立安装 | `com.bly.dkplat/com.bly.chaos.plugin.stub.ActivityStub$P0` | 同上，但运行在宿主进程中 |

**实现原理**：
1. 系统认为启动的是代理 Activity（已注册在 AndroidManifest.xml 中）
2. Chaos 引擎在内部将请求转发给目标应用的真正 Activity
3. 通过 Hook `Instrumentation` 和 `ActivityThread` 实现无缝转发

---

## 六、"内部安装" vs "独立安装"对比分析

### 6.1 实际差异总结

| 对比维度 | 内部安装 | 独立安装 |
|----------|----------|----------|
| **运行进程** | `dkplugin.ctu.zjw:p0`（插件进程） | `com.bly.dkplat:p0`（宿主进程） |
| **Linux UID** | u0_a247 | u0_a243 |
| **数据根目录** | `dkplugin.ctu.zjw/virtual/data/user/0/` | `com.bly.dkplat/chaos/data/user/0/` |
| **Activity 代理** | `dkplugin.ctu.zjw/ActivityStub$P0` | `com.bly.dkplat/ActivityStub$P0` |
| **系统感知** | 系统看到"插件在运行 bilibili" | 系统看到"宿主在运行 bilibili" |
| **so 库存放** | 插件 `virtual/data/app/lib/` | 宿主 `chaos/data/app/lib/` |
| **数据库记录** | uId=0（PrivacyDev 表） | uId=0（PrivacyDev 表） |

### 6.2 "虚拟OS"的真相

**"虚拟OS"不是完整的操作系统镜像**，而是 Chaos 引擎在 **Android Framework 层** 构建的**轻量级运行时虚拟化环境**：

1. **无完整 OS 镜像**：插件 APK 仅 3.4MB，设备上无 `.img`/`.iso` 文件
2. **Framework 层 Hook**：通过 `libchaos.so` Hook 系统服务（PackageManager、ActivityManager、文件系统等）
3. **运行时构建**：虚拟环境在应用启动时动态构建，而非预先安装
4. **两种模式共享同一套引擎**：只是挂载点不同（`virtual/` vs `chaos/`）

### 6.3 "系统安装流程"的真相

用户感受到的"独立安装走系统安装流程"，**实际上并非真正的系统级 APK 安装**：

- `pm list packages | grep bili` 仍然只显示一个 `tv.danmaku.bili`
- 没有生成新的包名或新的 APK 文件
- 是 Chaos 引擎**内部模拟了安装流程**：
  1. 提取 APK 中的 so 库到 `chaos/data/app/lib/`
  2. 创建独立的数据目录 `chaos/data/user/0/{packageName}/`
  3. 注册到内部的 `plugs.ini` 和 `sort/` 配置中
  4. 走一遍"看起来像系统安装"的 UI 流程

### 6.4 论坛逆向分析佐证

从 MT 管理器论坛的逆向讨论中，进一步验证了技术实现：

- **签名依赖**："主分身得用我打包的才行，有签名在里面，换了签名分身会打不开"
- **独立安装的签名机制**：独立安装模式确实会重新处理 APK 签名
- **360 加固检测**：新版 360 加固能检测 Hook，说明 Chaos 引擎使用了 inline Hook 技术
- **模块集成**：LSPosed 模块需要同时嵌入 APP 和分身才能生效

---

## 七、与 NewBlackbox 的架构对比

### 7.1 架构差异

| 维度 | 小X分身 | NewBlackbox |
|------|---------|-------------|
| **APK 数量** | 2 个（宿主+插件） | 1 个（单 APK） |
| **核心引擎** | Chaos 虚拟引擎（闭源） | BlackBox（开源，基于 VirtualApp） |
| **进程模型** | 双容器（宿主进程/插件进程） | 单容器（`{host}:p0`~`p49`） |
| **数据根目录** | `chaos/` + `virtual/`（两套） | `blackbox/`（单套） |
| **用户隔离** | 通过运行容器区分 | 通过 `userId`（`user/0/`、`user/1/`） |
| **Activity 代理** | `ActivityStub$P0` | `ProxyActivity$P0` |
| **商业化程度** | 高（广告、会员） | 低（开源项目） |
| **源码可见性** | 闭源 | 开源 |

### 7.2 数据目录对比

**小X分身**：
```
# 内部安装
dkplugin.ctu.zjw/virtual/data/user/0/tv.danmaku.bili/

# 独立安装
com.bly.dkplat/chaos/data/user/0/tv.danmaku.bili/
```

**NewBlackbox**：
```
# user 0
blackbox/data/user/0/tv.danmaku.bili/

# user 1（独立用户）
blackbox/data/user/1/tv.danmaku.bili/
```

### 7.3 进程名对比

**小X分身**：
```
# 内部安装
dkplugin.ctu.zjw:p0

# 独立安装
com.bly.dkplat:p0
```

**NewBlackbox**：
```
# 所有分身
top.niunaijun.blackboxa:p0
top.niunaijun.blackboxa:p1
...
```

---

## 八、可借鉴的技术点

### 8.1 双进程容器模型（高价值）

小X分身最大的创新是**在同一设备上同时运行两个虚拟化容器**（宿主进程和插件进程），这为"内部/独立"两种模式提供了底层支撑。

**对 NewBlackbox 的启发**：
- 可以通过在同一 APK 内创建**两套进程池**来模拟类似效果
- 例如：`:p0`~`:p24` 用于内部安装，`:i0`~`:i24` 用于独立安装
- 两套进程池使用不同的数据根目录（`blackbox/` vs `blackbox_ind/`）

### 8.2 应用 so 库提取机制

小X分身将 APK 中的 so 库提取到独立的 `chaos/data/app/lib/{packageName}/` 目录中，避免了每次启动时从 APK 中解压 so 库的性能开销。

**NewBlackbox 现状**：
- 目前 so 库直接从 APK 加载或存储在 `blackbox/data/app/{pkg}/lib/` 中
- 可以优化 so 库的缓存和加载策略

### 8.3 配置文件序列化格式

小X分身使用二进制格式存储 `plugs.ini` 和 `sort/` 配置，相比 XML/JSON 有更快的读写速度和更小的体积。

### 8.4 桌面快捷方式实现

小X分身的桌面快捷方式可以直接启动分身应用（通过宿主进程的 `ActivityStub$P0`），而不需要每次都经过主界面。

**NewBlackbox 已有基础**：
- `ShortcutActivity` 已存在于 AndroidManifest.xml 中
- 但功能可能不够完善

### 8.5 内核热更新机制

从 `ChaosCoreCache` 表可以看到，小X分身支持**内核热更新**：
- 内核版本从 v13.9 迭代到 v34.0.2
- 每个版本通过 zip 包下载更新
- 支持按品牌、SDK 版本、包名定向修复

**NewBlackbox 现状**：
- 目前无热更新机制
- 可以考虑将 Hook 逻辑模块化，支持动态更新

---

## 九、改造可行性评估

### 9.1 在 NewBlackbox 上实现"独立分身"的方案

#### 方案 A：强化多用户隔离（推荐）

**原理**：利用 NewBlackbox 已有的 `userId` 机制，为每个 userId 提供完全独立的系统配置。

**已实现的部分**：
```kotlin
// MainActivity.kt 中已有独立模式逻辑
if (config.mode == MODE_INDEPENDENT) {
    val newUserId = fragmentList.size
    BlackBoxCore.get().createUser(newUserId)
    // ...
}
```

**需要增强的部分**：
1. **独立系统配置**：为不同 `userId` 维护独立的设备指纹（IMEI、Android ID 等）
2. **独立 Settings 数据库**：每个 `userId` 有独立的系统 Settings
3. **进程标识区分**：让不同 `userId` 的分身使用不同的进程后缀

**工作量**：⭐⭐（小）
**效果**：⭐⭐⭐（中）

#### 方案 B：单 APK 内模拟双容器

**原理**：在同一 APK 内创建两套数据根目录和进程模型，模拟小X分身的"宿主/插件"效果。

**需要修改的核心文件**：
1. `BEnvironment.java`：支持 `sVirtualRootInternal` 和 `sVirtualRootIndependent`
2. `ProxyManifest.java`：新增独立安装的进程名（如 `:i0`）
3. `AndroidManifest.xml`：注册新的代理 Activity/Service
4. `BlackBoxCore.java`：启动时根据模式选择不同的容器

**工作量**：⭐⭐⭐（中）
**效果**：⭐⭐⭐⭐（好）

#### 方案 C：完全复刻双 APK 架构

**原理**：将 BlackBox 核心拆分为独立的插件 APK，宿主 APK 只保留 UI。

**挑战**：
1. 需要维护两个 APK 的签名兼容性
2. 宿主与插件的跨进程通信复杂
3. 插件 APK 需要动态下载和加载
4. 与现有单 APK 架构不兼容

**工作量**：⭐⭐⭐⭐⭐（极大）
**效果**：⭐⭐⭐⭐⭐（完全复刻）
**建议**：不推荐，投入产出比过低

### 9.2 改造优先级建议

| 优先级 | 改造项 | 理由 |
|--------|--------|------|
| P0 | 强化 `userId` 隔离 | 工作量小，已有基础代码，效果显著 |
| P1 | 独立设备指纹配置 | 提升"独立感"的关键，防检测需求强烈 |
| P2 | 优化 so 库缓存 | 提升启动性能 |
| P3 | 桌面快捷方式完善 | 提升用户体验 |
| P4 | 进程名区分 | 让系统层面也能感知到"独立性" |

---

## 十、附录

### 10.1 调研使用的 ADB 命令清单

```bash
# 查看包信息
adb shell pm path com.bly.dkplat
adb shell pm path dkplugin.ctu.zjw
adb shell pm list packages -f | grep -i bili

# 查看进程
adb shell ps -A | grep -i bili
adb shell ps -A -o USER,PID,PPID,NAME | grep -i bili

# 查看数据目录（需要 root）
adb shell su -c "ls -la /data/data/dkplugin.ctu.zjw/virtual/data/user/"
adb shell su -c "ls -la /data/data/com.bly.dkplat/chaos/data/user/"
adb shell su -c "find /data/data/com.bly.dkplat/chaos/ -maxdepth 3 -type d"

# 查看 Activity 信息
adb shell dumpsys activity | grep -i 'tv.danmaku.bili'
adb shell dumpsys activity activities | grep -B 2 -A 5 'tv.danmaku.bili'

# 查看内存信息
adb shell dumpsys meminfo tv.danmaku.bili

# 拉取数据库分析
adb shell su -c "cat /data/data/com.bly.dkplat/databases/dkplat.db" > /tmp/dkplat.db
sqlite3 /tmp/dkplat.db ".tables"
sqlite3 /tmp/dkplat.db "SELECT * FROM LastCreate;"
```

### 10.2 核心文件路径索引

| 文件/目录 | 作用 |
|-----------|------|
| `/data/data/com.bly.dkplat/chaos/` | 独立安装的虚拟数据根目录 |
| `/data/data/dkplugin.ctu.zjw/virtual/` | 内部安装的虚拟数据根目录 |
| `/data/data/com.bly.dkplat/databases/dkplat.db` | 宿主核心数据库 |
| `/data/data/com.bly.dkplat/shared_prefs/dkplat.xml` | 宿主核心配置 |
| `/data/app/dkplugin.ctu.zjw-*/base.apk` | 插件 APK（3.4MB） |
| `/data/app/dkplugin.ctu.zjw-*/lib/arm64/libchaos.so` | Chaos 引擎主库 |
| `/data/app/dkplugin.ctu.zjw-*/lib/arm64/libchaos1.so` | Chaos 引擎辅助库 |

### 10.3 竞品版本演进

从 `ChaosCoreCache` 表提取的内核版本历史：

| 版本号 | 内核 Code | 主要更新 |
|--------|-----------|----------|
| v13.9 | 228 | 初始版本，适配 Android 11 预览版 |
| v15.1 | 247 | 增加窗口模式运行 |
| v15.5 | 259 | 修复第三方登录问题 |
| v17.6.2 | 312 | 修复应用升级闪退 |
| v17.7 | 320 | 适配 Android 13 |
| v17.9 | 350 | 增加分身数据备份功能 |
| v18.1 | 370 | 兼容 Android 13 |
| v19.1 | 382 | 修复应用闪退 |
| v19.2 | 400 | 修复应用闪退 |
| v19.4 | 422 | 增加热更新机制 |
| v19.6 | 440 | 兼容 Android 14 |
| v19.7 | 450 | 兼容 Android 14，增加跨设备迁移 |
| v20.0 | 490 | UI 优化 |
| v30.5 | 503 | 修复应用闪退 |
| v30.7 | 508 | 修复应用闪退 |
| v31.2 | 530 | 修复应用闪退 |
| v31.3 | 542 | 修复应用闪退 |
| v31.4 | 550 | 修复应用闪退 |
| v31.6 | 580 | 修复应用闪退 |
| v31.7 | 600 | 修复应用闪退 |
| v32.1 | 610 | 增加谷歌服务支持 |
| v32.3 | 631 | 修复已知 bug |
| v33.0 | 638 | 修复应用闪退 |
| v33.1 | 643 | 修复应用闪退 |
| v33.3.3 | 653 | 兼容 Android 14 |
| v33.3.4 | 654 | 修复应用闪退 |
| v33.7.1 | 657 | 修复应用闪退 |
| v33.7.6 | 662 | 修复应用闪退 |
| v33.8 | 663 | 修复应用闪退 |
| v33.8.5 | 668 | 修复应用闪退 |
| v34.0.2 | 672 | 最新版本，修复闪退 |

---

> 本文档基于 2026-05-25 的实机逆向分析编写，所有数据来自 Xiaomi MIX 2S（Android 10）设备上运行的小X分身最新版本。
