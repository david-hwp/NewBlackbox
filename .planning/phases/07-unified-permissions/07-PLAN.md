# Phase 7 Plan: 统一权限授权

## 目标

实现"一次授权，全部分身可用"的权限体验：
1. 宿主 APK Manifest 补全所有分身常用权限声明
2. 宿主 APK 启动时统一弹窗申请运行时权限
3. 引擎 Hook 层在 `PackageManager`、`ActivityManager`、`AppOpsManager` 三个层面统一自动放行所有常见权限
4. 分身应用 `PackageInfo.requestedPermissionsFlags` 自动标记为已授权

## 前置条件

- [x] Phase 5 Engine IPC Split 已完成（当前分支 `feature/phase5-engine-ipc`）
- [x] `MANAGE_EXTERNAL_STORAGE` manifest 声明已修复
- [ ] 本 Phase 7 实施前确保 `feature/phase5-engine-ipc` 已合并或基于其上开发

## 任务分解

### Wave 1: Manifest 补全（宿主 APK 权限声明）

**任务 1.1**: 在 `app/src/main/AndroidManifest.xml` 中补全所有分身常用权限声明
- **文件**: `app/src/main/AndroidManifest.xml`
- **位置**: 在现有 `uses-permission` 块中追加
- **新增权限列表**:
  ```xml
  <!-- 位置 -->
  <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
  <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

  <!-- 麦克风/音频 -->
  <uses-permission android:name="android.permission.RECORD_AUDIO" />
  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

  <!-- 相机 -->
  <uses-permission android:name="android.permission.CAMERA" />

  <!-- 电话 -->
  <uses-permission android:name="android.permission.READ_PHONE_STATE" />
  <uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
  <uses-permission android:name="android.permission.CALL_PHONE" />

  <!-- 联系人 -->
  <uses-permission android:name="android.permission.READ_CONTACTS" />
  <uses-permission android:name="android.permission.WRITE_CONTACTS" />

  <!-- 短信 -->
  <uses-permission android:name="android.permission.SEND_SMS" />
  <uses-permission android:name="android.permission.READ_SMS" />
  <uses-permission android:name="android.permission.RECEIVE_SMS" />

  <!-- 蓝牙 -->
  <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
  <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
  <uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

  <!-- 通知 -->
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

  <!-- 传感器 -->
  <uses-permission android:name="android.permission.BODY_SENSORS" />
  <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
  ```
- **验证**: `./gradlew :app:processDebugManifest` 无报错，生成的 manifest 包含上述权限

---

### Wave 2: 引擎 Hook 层统一放行

**任务 2.1**: 在 `IPackageManagerProxy.java` 中新增统一权限判断方法
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java`
- **位置**: 在 `isNotificationOrXiaomiPermission()` 方法之后新增 `isAutoGrantedPermission()`
- **实现**: 整合现有 `isAudioPermission()` + `isStorageOrMediaPermission()` + `isNotificationOrXiaomiPermission()`，并新增 LOCATION、CAMERA、PHONE、CONTACTS、SMS、BLUETOOTH、SENSORS 分支
- **验证**: 编译通过，无重复方法名

**任务 2.2**: 替换 `SimpleAudioPermissionHook.hook()` 中的分段判断
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java`
- **位置**: `SimpleAudioPermissionHook` 类（~439行）
- **修改**: 将原有的 `isAudioPermission()` → `isStorageOrMediaPermission()` → `isNotificationOrXiaomiPermission()` 三段判断替换为单一的 `isAutoGrantedPermission(permission)` 判断
- **验证**: `checkPermission("android.permission.CAMERA", pkg)` 返回 `PERMISSION_GRANTED`

**任务 2.3**: 替换 `CheckSelfPermission.hook()` 中的分段判断
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java`
- **位置**: `CheckSelfPermission` 类（~469行）
- **修改**: 同上，统一使用 `isAutoGrantedPermission()`
- **验证**: `checkSelfPermission("android.permission.ACCESS_FINE_LOCATION", pkg)` 返回 `PERMISSION_GRANTED`

**任务 2.4**: 扩展 `GetPackageInfo` 中的 `requestedPermissionsFlags` 标记逻辑
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java`
- **位置**: `GetPackageInfo` 类（~144行）
- **修改**: 将现有的仅音频权限标记扩展为：
  ```java
  for (int i = 0; i < packageInfo.requestedPermissions.length; i++) {
      String perm = packageInfo.requestedPermissions[i];
      if (perm != null && isAutoGrantedPermission(perm)) {
          packageInfo.requestedPermissionsFlags[i] |= PackageInfo.REQUESTED_PERMISSION_GRANTED;
      }
  }
  ```
- **验证**: 分身应用调用 `getPackageInfo()` 后，所有声明权限的 `REQUESTED_PERMISSION_GRANTED` 标志位为 1

**任务 2.5**: 在 `IActivityManagerProxy.java` 中统一 `checkPermission` 放行逻辑
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IActivityManagerProxy.java`
- **位置**: `checkPermission` 类（~776行）
- **修改**: 将现有的 `isAudioPermission()` + `isStorageOrMediaPermission()` 替换为统一方法。由于 `IActivityManagerProxy` 和 `IPackageManagerProxy` 不在同一个文件，需要将 `isAutoGrantedPermission()` 的逻辑同步到 `IActivityManagerProxy`，或提取到公共工具类
- **方案**: 在 `IActivityManagerProxy` 中新增私有 `isAutoGrantedPermission()` 方法（与 IPackageManagerProxy 保持一致）
- **验证**: `ActivityManager.checkPermission("android.permission.READ_PHONE_STATE", ...)` 返回 `PERMISSION_GRANTED`

**任务 2.6**: 扩展 `IAppOpsManagerProxy` 的 AppOps 放行
- **文件**: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IAppOpsManagerProxy.java`
- **位置**: `isMediaStorageOrAudioOp()` 方法（~217行）
- **修改**: 将现有 `isMediaStorageOrAudioOp()` 重命名为 `isAutoGrantedOp()`，并确认已覆盖 LOCATION、CAMERA、PHONE、CONTACTS、SMS、BLUETOOTH 等操作
- **当前覆盖**: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `CAMERA`, `BODY_SENSORS`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS` 已在 `isMediaStorageOrAudioOp()` 中
- **缺失补充**: `READ_PHONE_STATE`, `READ_CONTACTS`, `SEND_SMS`, `READ_SMS` 对应的 AppOps 字符串
- **验证**: AppOps `OP_READ_PHONE_STATE` / `OP_READ_CONTACTS` / `OP_SEND_SMS` 等返回 `MODE_ALLOWED`

---

### Wave 3: 宿主启动时统一申请

**任务 3.1**: 在 `MainActivity.kt` 中新增统一运行时权限申请
- **文件**: `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt`
- **位置**: 在 `checkStoragePermission()` 之后新增 `checkAllPermissions()`
- **实现**:
  ```kotlin
  companion object {
      private const val ALL_PERMISSIONS_REQUEST_CODE = 1001
  }

  private val ALL_PERMISSIONS = arrayOf(
      Manifest.permission.CAMERA,
      Manifest.permission.RECORD_AUDIO,
      Manifest.permission.ACCESS_FINE_LOCATION,
      Manifest.permission.ACCESS_COARSE_LOCATION,
      Manifest.permission.READ_PHONE_STATE,
      Manifest.permission.READ_CONTACTS,
      Manifest.permission.BLUETOOTH_SCAN,
      Manifest.permission.BLUETOOTH_CONNECT,
      Manifest.permission.POST_NOTIFICATIONS
  )

  private fun checkAllPermissions() {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          val denied = ALL_PERMISSIONS.filter {
              ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
          }
          if (denied.isNotEmpty()) {
              ActivityCompat.requestPermissions(this, denied.toTypedArray(), ALL_PERMISSIONS_REQUEST_CODE)
          }
      }
  }
  ```
- **调用时机**: 在 `onCreate()` 中 `checkStoragePermission()` 之后调用 `checkAllPermissions()`
- **验证**: 首次启动时弹出权限申请对话框，包含相机、位置、麦克风等权限

**任务 3.2**: 处理权限申请结果回调
- **文件**: `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt`
- **位置**: `onRequestPermissionsResult()` 方法
- **修改**: 增加对 `ALL_PERMISSIONS_REQUEST_CODE` 的处理分支
- **验证**: 用户授予/拒绝后，日志正确记录

---

### Wave 4: 构建与验证

**任务 4.1**: 构建验证
- **命令**: `./gradlew :app:assembleDebug --no-daemon`
- **预期**: BUILD SUCCESSFUL

**任务 4.2**: 安装到模拟器/真机
- **命令**:
  ```bash
  ADB="..."
  $ADB install -r -d app/build/outputs/apk/debug/BlackBox_4.1.0_universal-debug.apk
  ```
- **步骤**:
  1. 卸载旧引擎 APK（若存在）
  2. 安装新宿主 APK
  3. 启动应用，完成引擎安装流程
  4. 安装一个分身应用（如京东秒送商家）

**任务 4.3**: 功能验证 — 分身权限检查
- **测试方法**: 在分身应用中调用以下代码，验证返回值：
  ```java
  // 位置
  checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PERMISSION_GRANTED
  // 相机
  checkSelfPermission("android.permission.CAMERA") == PERMISSION_GRANTED
  // 电话
  checkSelfPermission("android.permission.READ_PHONE_STATE") == PERMISSION_GRANTED
  // 麦克风
  checkSelfPermission("android.permission.RECORD_AUDIO") == PERMISSION_GRANTED
  ```
- **验证方式**: logcat 查看 `IPackageManagerProxy` / `IActivityManagerProxy` 日志中是否出现 "Granting permission: ..."
- **预期**: 所有权限检查返回 `PERMISSION_GRANTED`（0）

**任务 4.4**: 功能验证 — 宿主统一弹窗
- **测试方法**: 清除应用数据后重新启动 BlackBox
- **预期**: 首次启动弹出统一权限申请对话框，包含相机、位置、麦克风、电话、联系人等权限

---

## 变更文件清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `app/src/main/AndroidManifest.xml` | 修改 | 新增 15+ 个权限声明 |
| `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java` | 修改 | 新增 `isAutoGrantedPermission()`，替换现有权限判断逻辑，扩展 `GetPackageInfo` |
| `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IActivityManagerProxy.java` | 修改 | 同步 `isAutoGrantedPermission()` 到 `checkPermission` |
| `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IAppOpsManagerProxy.java` | 修改 | 扩展 `isMediaStorageOrAudioOp()` 覆盖缺失的 AppOps |
| `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt` | 修改 | 新增 `checkAllPermissions()` 和权限申请回调处理 |

## 回滚计划

若任何验证失败：
1. `git checkout --` 回滚上述 5 个文件
2. 清理构建: `./gradlew clean`
3. 重新构建安装基线版本

## 验收标准

- [ ] `app/src/main/AndroidManifest.xml` 包含 CAMERA、LOCATION、PHONE、CONTACTS、SMS、BLUETOOTH、NOTIFICATION、SENSORS 权限声明
- [ ] 首次启动 BlackBox 时弹出统一权限申请弹窗（包含相机、位置、麦克风、电话等）
- [ ] 分身应用调用 `checkSelfPermission()` 对任意常见权限返回 `PERMISSION_GRANTED`
- [ ] 分身应用调用 `getPackageInfo()` 后所有声明权限的 `REQUESTED_PERMISSION_GRANTED` 标志为 true
- [ ] `./gradlew :app:assembleDebug` BUILD SUCCESSFUL
- [ ] Mix 2S 真机验证通过（安装分身 + 权限检查）
- [ ] 模拟器验证通过
