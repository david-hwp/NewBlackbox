# Phase 1 调研报告：单实例运行模式

## 研究目标
实现"同时只运行一个分身应用"功能：当用户打开一个新的分身应用时，自动杀掉之前运行的其他分身应用进程，以节省系统内存。

## 现有架构分析

### 1. 进程管理 (BProcessManagerService)

**关键文件**: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BProcessManagerService.java`

核心数据结构：
```java
// 按 buid (用户+应用ID) 分组存储进程
private final Map<Integer, Map<String, ProcessRecord>> mProcessMap = new HashMap<>();

// 所有活动进程的列表
private final List<ProcessRecord> mPidsSelfLocked = new ArrayList<>();
```

已有的进程操作方法：
- `startProcessLocked(String packageName, String processName, int userId, int bpid, int callingPid)` — 启动新进程
- `killAllByPackageName(String packageName)` — 杀掉某包名的所有进程
- `killPackageAsUser(String packageName, int userId)` — 杀掉某用户下某包名的进程
- `onProcessDie(ProcessRecord record)` — 进程死亡回调，清理 mProcessMap 和 mPidsSelfLocked
- `findProcessByPid(int pid)` — 按 PID 查找进程
- `getPackageProcessAsUser(String packageName, int userId)` — 获取某用户下某包名的进程列表

**ProcessRecord** (`Bcore/src/main/java/top/niunaijun/blackbox/core/system/ProcessRecord.java`)：
- `info: ApplicationInfo` — 应用信息
- `processName: String` — 进程名
- `pid: int` — 实际进程ID
- `buid: int` / `bpid: int` — 虚拟 UID/PID
- `userId: int` — 用户ID
- `kill()` — 调用 `Process.killProcess(pid)` 杀掉进程

### 2. 应用启动流程

完整调用链（UI 启动）：

```
AppsFragment.onItemClick()
  → AppsViewModel.launchApk(packageName, userId)
    → AppsRepository.launchApk(packageName, userId)
      → BlackBoxCore.launchApk(packageName, userId)
        → onBeforeMainLaunchApk() (AppLifecycleCallback)
        → getBPackageManager().getLaunchIntentForPackage()
        → startActivity(launchIntent, userId)
          → [如果启用 LauncherActivity]
            → LauncherActivity.launch(intent, userId)
              → 显示启动画面
              → BActivityManager.startActivity(intent, userId)
          → [否则直接]
            → BActivityManager.startActivity(intent, userId)
        → BActivityManagerService.startActivity()
          → ActivityStack.startActivityLocked()
            → startActivityProcess()
              → BProcessManagerService.startProcessLocked()
```

**关键拦截点**: `BlackBoxCore.launchApk()` 是最佳拦截点——它在启动任何新应用前被调用，且已持有所有需要的上下文。

### 3. Activity 管理 (ActivityStack)

**关键文件**: `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/ActivityStack.java`

- `mTasks: Map<Integer, TaskRecord>` — 按任务ID存储任务
- `TaskRecord` 包含 `activities: List<ActivityRecord>`
- `ActivityRecord` 引用 `processRecord: ProcessRecord`
- `synchronizeTasks()` — 与系统 RecentTasks 同步
- `finishAllActivity(int userId)` — 完成某用户所有 Activity

### 4. 设置系统

**关键文件**:
- `app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt` — 配置加载器，使用 `AppSharedPreferenceDelegate` 存储设置
- `app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt` — 设置界面
- `app/src/main/res/xml/setting.xml` — 设置 XML
- `Bcore/src/main/java/top/niunaijun/blackbox/app/configuration/ClientConfiguration.java` — 客户端配置抽象类

现有设置模式：
1. `BlackBoxLoader` 中用 `AppSharedPreferenceDelegate` 声明 preference
2. `ClientConfiguration` 匿名子类中读取 preference
3. `SettingFragment` 中添加 SwitchPreferenceCompat 并绑定变更事件
4. 修改后提示用户重启（`toast(R.string.restart_module)`）

### 5. 生命周期回调

**关键文件**: `Bcore/src/main/java/top/niunaijun/blackbox/app/configuration/AppLifecycleCallback.java`

已有的回调点：
- `beforeMainLaunchApk(String packageName, int userid)` — 主进程启动 APK 前
- `beforeCreateApplication(...)` / `afterApplicationOnCreate(...)` — Application 创建前后
- `beforeMainActivityOnCreate(Activity)` / `afterMainActivityOnCreate(Activity)` — Activity 创建前后

## 可行方案对比

### 方案 A: 在 BlackBoxCore.launchApk() 中拦截（推荐）

**实现**: 在 `BlackBoxCore.launchApk()` 方法开头，检查单实例模式开关。如果开启，遍历所有运行中的虚拟应用进程，杀掉除目标应用外的所有进程。

**优点**:
- 集中管理，逻辑清晰
- 只在用户主动从 UI 启动应用时触发
- 不修改底层进程管理逻辑，风险低
- 可以利用 `AppLifecycleCallback.beforeMainLaunchApk()` 做扩展

**缺点**:
- 如果应用通过其他方式启动（如广播、服务等），不会触发
- 但这类情况对"用户主动打开"场景影响不大

### 方案 B: 在 BProcessManagerService.startProcessLocked() 中拦截

**实现**: 在创建新进程时，自动杀掉所有其他应用的进程。

**优点**:
- 更底层，覆盖所有进程创建路径
- 包括后台服务、广播接收器触发的进程创建

**缺点**:
- 可能影响系统内部服务的正常运行
- 过于激进，可能导致意外行为
- 难以区分"用户主动打开"和"系统后台唤醒"

### 方案 C: 通过 AppLifecycleCallback 实现

**实现**: 在 `BlackBoxLoader.addLifecycleCallback()` 的匿名子类中，重写 `beforeMainLaunchApk()` 方法。

**优点**:
- 完全符合现有架构模式
- 不需要修改 BlackBoxCore

**缺点**:
- `beforeMainLaunchApk()` 目前只是在循环中调用回调，没有返回值来控制是否继续启动
- 需要修改回调接口签名才能阻止/控制启动流程

## 推荐方案

**采用方案 A**，具体实现分为以下部分：

### 核心逻辑

1. **在 `ClientConfiguration` 中添加配置接口**:
   ```java
   public boolean isSingleInstanceMode() { return false; }
   ```

2. **在 `BlackBoxLoader` 中实现配置**:
   - 添加 `mSingleInstanceMode` preference
   - 在 `ClientConfiguration` 匿名子类中返回该值

3. **在 `BProcessManagerService` 中添加批量 kill 方法**:
   ```java
   public void killAllOtherProcesses(String keepPackageName, int userId)
   ```
   - 遍历 `mPidsSelfLocked`
   - 杀掉所有 `packageName != keepPackageName` 的进程
   - 清理 `mProcessMap` 和通知

4. **在 `BlackBoxCore.launchApk()` 中集成**:
   ```java
   if (mClientConfiguration.isSingleInstanceMode()) {
       BProcessManagerService.get().killAllOtherProcesses(packageName, userId);
   }
   ```

### UI 层

5. **在 `SettingFragment` 中添加开关**:
   - 在 `setting.xml` 添加 `SwitchPreferenceCompat`，key="single_instance_mode"
   - 在 `SettingFragment` 中绑定变更事件
   - 修改后提示重启

### Activity 栈清理（可选增强）

6. **在 `ActivityStack` 中添加清理方法**:
   ```java
   public void finishAllActivitiesExcept(String keepPackageName, int userId)
   ```
   - 遍历 `mTasks`，finish 所有非保留应用的 Activity

## 风险评估

| 风险 | 可能性 | 影响 | 缓解措施 |
|------|--------|------|----------|
| 杀掉进程导致数据丢失 | 中 | 高 | 只在用户主动打开新应用时触发，给用户明确的设置开关 |
| Activity 栈残留 | 中 | 低 | 进程已死，Activity 自然无法响应；可额外清理 ActivityStack |
| 通知残留 | 低 | 低 | `onProcessDie()` 已自动清理通知 |
| 用户误开启 | 低 | 中 | 设置项默认关闭，修改后提示重启 |

## 内存节省预估

以典型分身应用（淘宝、美团外卖商家版）为例：
- 每个应用通常占用 100-300MB RAM
- 同时运行 2-3 个分身应用时，内存占用 200-900MB
- 开启单实例模式后，同一时间最多只保留 1 个应用 + 系统服务
- 预计可节省 100-600MB RAM，取决于同时运行的应用数量
