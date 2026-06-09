# Phase 7 Research: 统一权限授权

## 1. Android 权限模型演进

### 1.1 运行时权限（Android 6.0+）
- `dangerous` 权限需要在运行时通过 `requestPermissions()` 申请
- 用户可以在设置中随时撤销
- 分身应用通过 BlackBox 引擎 Hook 系统服务，`checkSelfPermission` 返回值可被拦截

### 1.2 特殊权限（Android 11+）
- `MANAGE_EXTERNAL_STORAGE` — 所有文件访问，需跳转到系统设置页手动开启
- 已在 Phase 5 修复（manifest 声明 + 设置页跳转）

### 1.3 AppOps（应用操作权限）
- Android 底层通过 `AppOpsManager` 控制敏感操作（定位、录音、相机等）
- 即使 `PackageManager.PERMISSION_GRANTED`，AppOps 仍可能拒绝操作
- BlackBox 已 Hook `IAppOpsManagerProxy`，可统一放行

## 2. BlackBox 当前权限 Hook 覆盖

| 权限类别 | PackageManager 层 | ActivityManager 层 | AppOps 层 | 状态 |
|---------|------------------|-------------------|----------|------|
| 音频 (RECORD_AUDIO) | ✅ 已放行 | ✅ 已放行 | ✅ 已放行 | 完整 |
| 存储 (READ/WRITE) | ✅ 已放行 | ✅ 已放行 | ✅ 已放行 | 完整 |
| 媒体 (READ_MEDIA_*) | ✅ 已放行 | ✅ 已放行 | ✅ 已放行 | 完整 |
| 位置 (LOCATION) | ❌ 未放行 | ❌ 未放行 | ✅ 已放行 | 不完整 |
| 相机 (CAMERA) | ❌ 未放行 | ❌ 未放行 | ✅ 已放行 | 不完整 |
| 电话 (PHONE) | ❌ 未放行 | ❌ 未放行 | ❌ 未放行 | 缺失 |
| 联系人 (CONTACTS) | ❌ 未放行 | ❌ 未放行 | ❌ 未放行 | 缺失 |
| 短信 (SMS) | ❌ 未放行 | ❌ 未放行 | ❌ 未放行 | 缺失 |
| 蓝牙 (BLUETOOTH) | ❌ 未放行 | ❌ 未放行 | ❌ 未放行 | 缺失 |
| 通知 (POST_NOTIFICATIONS) | ⚠️ 部分 | ⚠️ 部分 | ❌ 未放行 | 不完整 |

**结论**：需要在 PackageManager 和 ActivityManager 层补全 LOCATION、CAMERA、PHONE、CONTACTS、SMS、BLUETOOTH 的自动放行逻辑。

## 3. 宿主 APK 权限声明现状

当前 `app/src/main/AndroidManifest.xml` 已声明：
- `READ_EXTERNAL_STORAGE`
- `WRITE_EXTERNAL_STORAGE` (maxSdkVersion=29)
- `MANAGE_EXTERNAL_STORAGE` ✅ Phase 5 新增
- `REQUEST_INSTALL_PACKAGES`
- `QUERY_ALL_PACKAGES`

**缺失**：
- `CAMERA`
- `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION`
- `RECORD_AUDIO`
- `READ_PHONE_STATE`
- `READ_CONTACTS`
- `BLUETOOTH_SCAN` / `BLUETOOTH_CONNECT`
- `POST_NOTIFICATIONS`

## 4. 竞品参考

### 小X分身
- 安装时一次性申请所有权限（相机、位置、麦克风、电话、存储）
- 分身应用内部不再弹窗请求权限
- 设置页面提供"权限管理"入口，可单独关闭某项权限

### DualSpace
- 类似策略：宿主统一申请，分身默认全部放行
- 若用户撤销某项权限，分身对应功能不可用

## 5. 技术方案决策

### 决策 1：引擎层统一放行 vs 宿主申请后借用
**选择**：两者结合。
- 引擎 Hook 层统一放行所有权限检查（最可靠，即使宿主没有也能让分身运行）
- 宿主 APK 同时声明并申请权限（确保硬件操作如相机预览、GPS 定位能真实工作）

### 决策 2：权限范围
**选择**：覆盖分身常见需求的全部权限：
- 位置、麦克风、电话、相机、文件访问（用户明确提到）
- 额外加上：联系人、短信、蓝牙、通知、传感器

### 决策 3：是否支持用户细粒度控制
**选择**：Phase 7 先实现"全部放行"基础版本。细粒度控制（设置页面开关）作为后续优化。

## 6. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 过度授权被应用商店拒绝 | 高 | 提供设置开关让用户可选，Google Play 可关闭自动放行 |
| 某些 ROM 对权限管控严格 | 中 | 在 MIUI/ColorOS 上测试，必要时加 ROM 特殊处理 |
| Android 15 权限模型变化 | 中 | 关注 Android 15 Beta，预留适配空间 |
