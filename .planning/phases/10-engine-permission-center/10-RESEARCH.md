---
phase: 10-engine-permission-center
status: planning
created_at: "2026-06-08T04:20:00+08:00"
---

# Phase 10: 引擎权限中心 - Research

## Current Finding

抖音来客实名认证人脸识别失败的直接原因不是虚拟 App 内部授权 UI，而是系统服务校验真实宿主 UID。虚拟环境里目标 App 发起相机或录音访问时，请求最终落到引擎进程对应的真实包名和 UID；因此引擎 APK 必须具备对应运行时权限，并且 AppOps 不能处于 ignore/deny。

小米真机验证结果：

- 引擎未授权前，`CAMERA` / `RECORD_AUDIO` 运行时权限缺失，AppOps 为拒绝状态。
- 通过引擎侧权限 Activity 授权后，运行时权限变为 granted，AppOps 变为 foreground/allowed。
- 授权后抖音来客可进入登录/实名认证流程，不再出现系统层权限拒绝日志。

## Android Permission Model Implication

- 虚拟 App 的权限 hook 只能影响目标 App 在虚拟环境中查询到的授权结果。
- 相机、录音、定位、媒体、蓝牙等能力最终仍由 Android 系统服务检查真实调用 UID。
- 对这类能力，引擎 APK 是真实调用方，必须具备宿主权限。
- 对特殊权限，例如悬浮窗、所有文件访问、安装未知应用，不能通过普通 `requestPermissions` 一次性解决，需要独立设置入口。

## Implementation Direction

### Main APK

主 APK 负责做“业务侧决策”：

- 基础权限：按 SDK 生成一组可批量请求的危险权限。
- 平台权限：优先读取目标平台包 Manifest 的 `requestedPermissions`，过滤为引擎宿主真正需要的危险权限。
- 平台兜底：抖音来客固定补齐 `CAMERA` / `RECORD_AUDIO`，避免 Manifest 不可读或权限声明被动态拆分时漏判。
- 缺失判断：通过 `PackageManager.checkPermission` 和 `AppOpsManager` 校验引擎包实际状态。
- 触发权限中心：用显式 Intent 打开引擎 APK 的权限 Activity。

### Engine APK

引擎 APK 负责做“系统权限执行”：

- 接收主 APK 传入的一组权限。
- 过滤当前系统版本不支持或 Manifest 未声明的权限。
- 一次性调用 `requestPermissions`。
- 回调后复核运行时权限与 AppOps。
- 仍缺权限时给出设置页兜底。

## Risks

- Android 会按权限组拆分系统弹窗，因此用户可能看到多个系统弹窗；本阶段只能保证业务流程是一次性发起一组权限。
- 用户拒绝基础权限后，如果首页反复提示会造成干扰；必须按引擎版本记录一次提示状态。
- 读取平台 Manifest 依赖目标平台包在宿主系统可见或已安装；读取不到时必须使用兼容兜底，不阻塞其他平台。
- AppOps 常量在不同 Android 版本上覆盖不一致；实现应只对关键权限做 AppOps 复核，无法映射的权限以运行时授权为准。

