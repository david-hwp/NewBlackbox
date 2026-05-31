# Phase 1 执行计划：单实例运行模式

## 阶段目标
实现"同时只运行一个分身应用"功能。当用户从 UI 启动一个新的分身应用时，自动杀掉之前运行的其他分身应用进程，以节省系统内存。

## 前置条件
- 已阅读 `CLAUDE.md` 了解项目架构
- 已阅读 `RESEARCH.md` 了解现有代码结构和方案分析
- 当前分支: `dev-hwp`

## 任务清单

### Task 1: 添加设置存储和 ClientConfiguration 接口
**目标**: 添加单实例模式配置项，让核心层能够读取设置。

**文件变更**:
1. `Bcore/src/main/java/top/niunaijun/blackbox/app/configuration/ClientConfiguration.java`
   - 添加 `isSingleInstanceMode()` 方法，默认返回 `false`

2. `app/src/main/java/top/niunaijun/blackboxa/view/main/BlackBoxLoader.kt`
   - 添加 `mSingleInstanceMode` preference (使用 `AppSharedPreferenceDelegate`)
   - 添加 `singleInstanceMode()` getter
   - 添加 `invalidSingleInstanceMode(boolean)` setter
   - 在 `ClientConfiguration` 匿名子类中重写 `isSingleInstanceMode()`

3. `app/src/main/java/top/niunaijun/blackboxa/app/AppManager.kt`
   - （可选）如需从外部访问，添加便捷方法

**验收标准**:
- `BlackBoxCore.get().mClientConfiguration.isSingleInstanceMode()` 能正确读取 preference 值
- 默认值为 `false`

---

### Task 2: 在 BProcessManagerService 中添加批量进程清理方法
**目标**: 提供"杀掉所有其他运行中的应用"的底层能力。

**文件变更**:
`Bcore/src/main/java/top/niunaijun/blackbox/core/system/BProcessManagerService.java`

**新增方法**:
```java
public void killAllOtherProcesses(String keepPackageName, int userId)
```

**实现要点**:
1. 获取 `mProcessLock` 锁
2. 遍历 `mPidsSelfLocked`，收集所有 `packageName != keepPackageName` 的 `ProcessRecord`
3. 对每个要杀的进程：
   - 调用 `record.kill()` (调用 `Process.killProcess(pid)`)
   - 从 `mProcessMap` 中移除（按 `buid` 找到对应 Map，移除 `processName` 条目，如为空则移除整个 `buid`）
   - 调用 `BNotificationManagerService.get().deletePackageNotification(record.getPackageName(), record.userId)` 清理通知
4. 从 `mPidsSelfLocked` 中移除所有被杀进程
5. 添加 Slog 日志记录操作结果

**注意事项**:
- 必须加锁，防止并发修改
- 使用临时列表收集要杀的进程，避免在遍历时修改原列表
- 不杀掉与 `keepPackageName` 相同的进程（包括同一应用的多进程）
- 不区分 userId，杀掉所有用户下的其他应用（因为内存压力是全系统的）

**验收标准**:
- 方法能正确杀掉所有非目标应用的进程
- 被杀进程的 Binder 死亡回调正常触发（但已通过手动清理完成主要状态维护）
- `mProcessMap` 和 `mPidsSelfLocked` 状态一致

---

### Task 3: 在 BlackBoxCore.launchApk() 中集成单实例逻辑
**目标**: 在启动新应用前，根据设置自动清理其他运行中的应用。

**文件变更**:
`Bcore/src/main/java/top/niunaijun/blackbox/BlackBoxCore.java`

**修改点**:
在 `launchApk(String packageName, int userId)` 方法中，在 `onBeforeMainLaunchApk()` 调用之后，获取启动 Intent 之前，添加：

```java
public boolean launchApk(String packageName, int userId) {
    onBeforeMainLaunchApk(packageName, userId);

    // 单实例模式：杀掉其他正在运行的分身应用
    if (mClientConfiguration != null && mClientConfiguration.isSingleInstanceMode()) {
        Slog.d(TAG, "Single instance mode: killing other running apps before launching " + packageName);
        try {
            BProcessManagerService.get().killAllOtherProcesses(packageName, userId);
        } catch (Exception e) {
            Slog.e(TAG, "Failed to kill other running apps in single instance mode", e);
        }
    }

    // ... 原有代码继续
}
```

**验收标准**:
- 设置关闭时（默认），启动行为不变
- 设置开启时，启动新应用前其他分身应用进程被正确杀掉
- 不影响目标应用自身的启动
- 有日志记录单实例模式的触发

---

### Task 4: 添加 UI 设置开关
**目标**: 让用户可以在设置界面开启/关闭单实例模式。

**文件变更**:

1. `app/src/main/res/xml/setting.xml`
   - 在 `<PreferenceCategory app:title="@string/other">` 内添加：
   ```xml
   <SwitchPreferenceCompat
       app:key="single_instance_mode"
       app:title="@string/single_instance_mode"
       app:summary="@string/single_instance_mode_summary" />
   ```

2. `app/src/main/res/values/strings.xml`（以及 values-zh 等本地化文件）
   - 添加：
   ```xml
   <string name="single_instance_mode">Single Instance Mode</string>
   <string name="single_instance_mode_summary">Only keep one clone app running at a time to save memory</string>
   ```

3. `app/src/main/java/top/niunaijun/blackboxa/view/setting/SettingFragment.kt`
   - 在 `onCreatePreferences()` 中添加单实例模式 preference 的初始化和变更监听：
   ```kotlin
   invalidHideState {
       val singleInstancePreference: Preference = (findPreference("single_instance_mode")!!)
       val mSingleInstanceMode = AppManager.mBlackBoxLoader.singleInstanceMode()
       singleInstancePreference.setDefaultValue(mSingleInstanceMode)
       singleInstancePreference
   }
   ```
   - 在 `invalidHideState` 的 `onPreferenceChangeListener` 中添加：
   ```kotlin
   "single_instance_mode" -> {
       AppManager.mBlackBoxLoader.invalidSingleInstanceMode(tmpHide)
   }
   ```

**验收标准**:
- 设置页面显示"单实例模式"开关
- 开关状态与 preference 同步
- 切换后提示用户重启（复用现有的 `toast(R.string.restart_module)`）
- 默认值为关闭

---

### Task 5: （可选增强）清理 ActivityStack 中残留记录
**目标**: 杀掉进程后，同时清理 ActivityStack 中对应应用的 Activity 记录。

**文件变更**:

1. `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/ActivityStack.java`
   - 添加方法：
   ```java
   public void finishAllActivitiesExcept(String keepPackageName, int userId)
   ```
   - 遍历 `mTasks`，对所有 `activity.info.packageName != keepPackageName` 的 ActivityRecord：
     - 设置 `finished = true`
     - 尝试调用 `activity.processRecord.bActivityThread.finishActivity(activity.token)`（如果进程还存在）
   - 从 `task.activities` 中移除已 finish 的 Activity
   - 如果 task 变为空，从 `mTasks` 中移除

2. `Bcore/src/main/java/top/niunaijun/blackbox/core/system/am/BActivityManagerService.java`
   - 暴露清理接口：
   ```java
   public void finishAllActivitiesExcept(String keepPackageName, int userId) {
       UserSpace userSpace = getOrCreateSpaceLocked(userId);
       synchronized (userSpace.mStack) {
           userSpace.mStack.finishAllActivitiesExcept(keepPackageName, userId);
       }
   }
   ```

3. `Bcore/src/main/java/top/niunaijun/blackbox/core/system/BProcessManagerService.java`
   - 在 `killAllOtherProcesses()` 方法中，杀掉进程后调用：
   ```java
   try {
       BActivityManagerService.get().finishAllActivitiesExcept(keepPackageName, userId);
   } catch (Exception e) {
       Slog.w(TAG, "Failed to finish activities in single instance mode", e);
   }
   ```

**验收标准**:
- 杀掉其他应用进程后，ActivityStack 中不再保留这些应用的 Activity 记录
- `mTasks` 和 `TaskRecord.activities` 状态一致

---

## 依赖关系

```
Task 1 (配置接口)
  ↓
Task 2 (进程清理) ──→ Task 3 (launchApk 集成)
  ↓                      ↓
Task 4 (UI 开关) ←───────┘
  ↓
Task 5 (Activity 清理，可选)
```

## 测试验证计划

### 手动测试步骤

1. **默认行为验证**:
   - 安装两个分身应用（如 A 和 B）
   - 打开 A，再打开 B
   - 验证两者可以同时运行（默认设置关闭）

2. **单实例模式开启**:
   - 进入设置，开启"单实例模式"
   - 重启应用（或按提示操作）
   - 打开 A，确认 A 正常运行
   - 返回，打开 B
   - 验证：
     a. A 的进程已被杀掉（通过 `adb shell ps | grep <host_pkg>` 或系统设置中查看运行中应用）
     b. B 正常启动
     c. 系统内存占用明显下降

3. **同一应用多进程**:
   - 打开一个带有后台服务的应用
   - 确认其主进程和服务进程都正常运行
   - 打开另一个应用
   - 验证前一个应用的所有进程都被杀掉

4. **边界情况**:
   - 在没有其他应用运行时打开应用（不应崩溃）
   - 快速连续点击两个应用（防重复触发）
   - 杀掉进程后重新打开同一应用（应正常启动）

### 日志验证
- 查看 logcat 中 "Single instance mode" 相关日志
- 确认 `killAllOtherProcesses` 的日志输出（杀掉了哪些进程）

## 风险与回滚

| 风险 | 缓解措施 |
|------|----------|
| 杀掉进程导致数据未保存 | 这是预期行为，类似系统内存不足时的处理；用户通过开关自愿开启 |
| 清理逻辑有 bug 导致崩溃 | 所有清理逻辑包裹 try-catch；修改可独立回滚每个 Task |
| 并发问题 | 所有操作在已有锁（`mProcessLock`）保护下进行 |

**回滚方式**: 关闭设置开关即可恢复原有行为；如需代码回滚，只需 revert Task 3 的修改（`launchApk` 中的条件判断）。

## 成功标准

1. ✅ 设置界面出现"单实例模式"开关，默认关闭
2. ✅ 开关开启后，从 UI 启动新分身应用时，之前运行的其他分身应用进程被自动杀掉
3. ✅ 被杀应用的通知被自动清理
4. ✅ 系统内存占用显著降低（可通过系统设置或 adb 验证）
5. ✅ 关闭开关后，恢复原有可同时运行多个分身应用的行为
6. ✅ 所有修改不影响现有功能
