# Phase 7 Context: 统一权限授权

## 背景

当前 BlackBox 在权限管理方面存在用户体验问题：

1. **宿主 APK 权限不完整**：Manifest 仅声明了存储、网络、安装包权限，缺少 CAMERA、LOCATION、PHONE 等常用权限
2. **分身权限检查未统一放行**：引擎 Hook 层对音频、存储有自动放行，但位置、相机、电话等权限在 `PackageManager.checkPermission` 层面仍未统一处理
3. **用户需要逐个授权**：每次打开分身应用时，系统可能弹出权限请求，用户体验差

## 目标

实现"一次授权，全部分身可用"的权限体验：
- 宿主 APK 启动时统一申请所有必要权限
- 引擎 Hook 层自动放行分身应用的所有常见权限检查
- 用户在设置页面可查看/管理已授权权限

## 前置条件

- Phase 5 (Engine IPC Split) 已完成 — 宿主与引擎通过 AIDL 通信
- 当前分支：`feature/phase5-engine-ipc`
- 代码位置：
  - 宿主 Manifest: `app/src/main/AndroidManifest.xml`
  - 宿主权限请求: `app/src/main/java/top/niunaijun/blackboxa/view/main/MainActivity.kt`
  - 引擎 Hook: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IPackageManagerProxy.java`
  - 引擎 Hook: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IActivityManagerProxy.java`
  - 引擎 AppOps: `Bcore/src/main/java/top/niunaijun/blackbox/fake/service/IAppOpsManagerProxy.java`

## 约束

- 不能破坏现有 Phase 5 的 AIDL 通信
- 新增权限必须在 Android 6.0 - 15+ 全版本兼容
- `MANAGE_EXTERNAL_STORAGE` 已在 Phase 5 期间修复（manifest 声明已添加）
