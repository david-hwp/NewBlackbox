---
phase: 10-engine-permission-center
status: implemented
created_at: "2026-06-08T04:20:00+08:00"
completed_at: "2026-06-08T23:25:00+08:00"
source: user-request
branch: feature-phase10
---

# Phase 10: 引擎权限中心 - Context

## User Request

从 `dev` 最新代码新建 `feature-phase10` 分支，把当前抖音来客的兼容逻辑合入本阶段，并升级为“引擎权限中心”：

- 先规划，再自动执行。
- 默认基础权限按照建议申请。
- 基础权限最好只弹一次，一次把一组基础权限全获取。
- 抖音来客的代码可以合到这次 phase 的 feature 分支里。

## Problem

分身 App 在虚拟环境中发起相机、录音、定位等能力时，目标 App 的权限 hook 只能影响虚拟 App 看到的授权状态；Android 系统服务最终仍会校验真实宿主 UID，也就是引擎 APK 的运行时权限和 AppOps 状态。抖音来客实名认证人脸识别失败就是这个问题的首个明确案例：虚拟 App 认为自己有权限，但宿主引擎缺少相机/录音权限时，系统层仍会拒绝。

## Phase Boundary

### In Scope

- 主 APK 增加统一权限中心，负责：
  - 计算基础引擎权限。
  - 从平台 App Manifest 读取目标平台声明的危险权限。
  - 为抖音来客补齐相机/录音兜底权限。
  - 判断引擎 APK 当前是否具备对应运行时权限和 AppOps。
  - 发起引擎权限申请 Activity。
- 引擎 APK 增加通用权限申请 Activity，负责：
  - 接收一组待申请权限。
  - 一次性调用运行时权限申请。
  - 授权后复核权限和 AppOps。
  - 必要时引导用户进入应用权限设置页。
- 基础权限主动提示只按引擎版本执行一次，避免首页、横竖屏或返回主页反复打扰用户。
- 店铺打开时只补请求该平台实际缺失的必要权限。
- 保留 Phase 9 的 clone 授权、扣费、token、恢复、修复店铺逻辑。

### Out of Scope

- 不新增后端接口。
- 不改变平台数据结构、店铺数据结构、扣费逻辑或公告/版本发布逻辑。
- 不一次性申请所有 Android 特殊权限，例如悬浮窗、所有文件访问、安装未知应用、无障碍权限。
- 不改引擎包名；本阶段只处理权限中心和抖音来客兼容。
- 不对其他平台加入特定启动补丁，除非权限中心本身需要。

## Locked Decisions

- 基础权限以危险权限为主，按 SDK 过滤：
  - `CAMERA`
  - `RECORD_AUDIO`
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`
  - Android 13+: `POST_NOTIFICATIONS`
  - Android 13+: `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`
  - Android 12+: `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`
  - Android 12 以下按需要使用 `READ_EXTERNAL_STORAGE`，Android 10 及以下可包含 `WRITE_EXTERNAL_STORAGE`
- 特殊权限未来单独做设置流，不混入基础权限批量申请。
- 基础权限“只弹一次”按已安装引擎版本记录；用户拒绝后不在首页反复弹。
- 具体平台打开时，如果该平台仍缺权限，可以再次发起针对该平台的补充申请。
- Android 可能按权限组拆分系统弹窗；业务层的“一次”定义为一次权限中心流程一次性传入一组权限。

## Acceptance Criteria

- 首页进入后，如果当前引擎版本缺基础权限，只触发一次基础权限中心流程；横竖屏、返回主页不重复弹。
- 打开抖音来客店铺时，如果引擎缺相机/录音权限，先进入权限中心；授权后再打开店铺。
- 抖音来客相机/录音授权状态在真实设备上显示为引擎 APK 已授权，AppOps 不再阻塞。
- 打开其他平台店铺时，不因为抖音来客补丁改变原有启动路径。
- 主 APK 与引擎 APK 均能编译通过。

## Completion Notes

- 主 APK 新增 `EnginePermissionCenter`，统一计算基础权限、平台 Manifest 权限和抖音来客兜底权限。
- 引擎 APK 的 `EnginePermissionActivity` 已从固定相机/录音申请改为通用权限列表申请。
- 基础权限按已安装引擎版本记录，不会在首页重启后重复弹。
- 店铺卡片和桌面快捷入口都接入同一个权限中心，避免直接启动绕过抖音来客权限补齐。
- 小米 MIX 2S 验证：主 APK 成功启动引擎权限中心，系统按权限组完成定位/存储授权，重启主 APK 后未再次弹出权限中心。
