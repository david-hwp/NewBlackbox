# JD 秒送验证码 LoadFail 问题调查记录

## 调查时间
2026-05-31

## 设备信息
- Honor ABR-AN00 (MagicOS, Android 16)
- WebView provider: com.hihonor.webview v1.0.2.300

---

## 关键发现

### 1. 问题本质：不是 WebView 加载失败，是本地验证拒绝
- 点击"获取验证码"后约 3 秒出现 Toast：**"验证失败，请重试"**
- **JD 进程 TCP 连接数为 0**，说明请求根本没有发送到服务器
- 这是 JD 验证码 SDK **本地检测**到虚拟环境后直接拒绝，不是网络/服务器问题

### 2. 非分身版 vs 分身版的关键差异

| 维度 | 非分身版 JD | BlackBox 分身版 JD |
|------|-----------|------------------|
| sandboxed process | **无** | 有 5+ 个 `com.hihonor.webview:sandboxed_process0` |
| SELinux 上下文 | N/A | `u:r:isolated_app:s0:c512,c768` |
| 网络连接 | 正常 HTTPS 到 42.204.250.142:443 | JD 主进程有连接，sandboxed process 0 连接 |
| 验证码表现 | 正常弹出滑动拼图 | "验证失败，请重试" |

### 3. JD SDK 可能的检测手段（按可能性排序）

1. **已安装应用列表检测**（最高优先级）
   - `pm list packages` 输出包含 `package:top.niunaijun.blackbox`
   - 即使通过 `IPackageManagerProxy` hook，JD 可能使用 shell 命令绕过
   - **已修改**：`getInstalledPackages()` / `getInstalledApplications()` 过滤掉 host pkg
   - **仍需验证**：是否还需要 hook `Runtime.exec()` 拦截 `pm list packages`

2. **进程列表检测**
   - `ActivityManager.getRunningAppProcesses()` 可能看到 BlackBox 进程
   - BlackBox 的 `BActivityManagerService.getRunningAppProcesses()` 已限制为返回调用者自身进程
   - 但 JD 可能通过 `ps -A` 等 shell 命令获取全局进程列表

3. **文件系统检测**
   - `/data/app/` 中存在 BlackBox 的 APK 目录
   - `/data/data/top.niunaijun.blackbox/` 目录存在

4. **WebView sandboxed process 检测**
   - 非分身版 JD **没有**创建 `com.hihonor.webview:sandboxed_process0`
   - 分身版创建了 5+ 个 isolated sandboxed process
   - Honor 的 `webview_zygote` 直接创建进程，绕过 Java 层 hook
   - 尝试修改 `IPackageManagerProxy.getServiceInfo()` 移除 `isolatedProcess` flag 未生效
   - 尝试修改 `WebView.setDataDirectorySuffix()` 未生效
   - 尝试创建 `/data/local/tmp/chrome-command-line --no-sandbox` 未生效

5. **包名/进程名伪装**
   - BlackBox 已在 `BActivityThread` 中伪装包名为虚拟应用包名 `com.jd.mrd.jingming`
   - 用户提出：能否进一步伪装，让 JD SDK 完全无法区分分身/非分身

### 4. 已尝试的修复（及结果）

| 修复 | 文件 | 结果 |
|-----|------|------|
| 移除 WebView sandbox `isolatedProcess` flag | `IPackageManagerProxy.java` | ❌ sandboxed process 仍然是 isolated |
| 修改 sandbox service `processName` | `IPackageManagerProxy.java` | ❌ 未生效 |
| 修改 `WebView.setDataDirectorySuffix()` | `BActivityThread.java` | ❌ sandboxed process 仍然存在 |
| 创建 `chrome-command-line --no-sandbox` | `/data/local/tmp/` | ❌ Honor WebView 不读取该文件 |
| 过滤 `getInstalledPackages()` 中的 BlackBox | `IPackageManagerProxy.java` | 🔄 待测试 |
| 过滤 `getInstalledApplications()` 中的 BlackBox | `IPackageManagerProxy.java` | 🔄 待测试 |

### 5. 测试脚本改进

- `e2e_honor/lib/honor_test_utils.sh` 已改为 UI Automator 动态定位元素
- `input_phone_number()` 添加隐藏键盘步骤（`KEYCODE_BACK`）
- `jd_captcha.sh` 检测逻辑区分 "验证失败" / "LoadFail" / "安全验证"

---

## 下一步方向

### 方向 A：Hook `Runtime.exec()` 拦截 shell 命令（高优先级）
JD SDK 很可能直接执行 `pm list packages` 或 `ps -A` 来检测虚拟环境。需要：
1. 在 `RuntimeHook` 或新增 hook 中拦截 `Runtime.exec()`
2. 过滤掉输出中的 `top.niunaijun.blackbox` 相关行

### 方向 B：伪装非分身环境（用户提出）
进一步伪装以下特征：
- `/proc/self/cmdline` → 确保只显示 `com.jd.mrd.jingming`
- `/data/data/` 目录访问 → 重定向或隐藏 BlackBox 目录
- 系统属性 → 移除可能暴露虚拟化的属性

### 方向 C：解决 WebView sandboxed process 问题
- 研究 Honor `webview_zygote` 的进程创建机制
- 尝试在 native 层 hook `fork()` 阻止创建 isolated process
- 或尝试 Honor 特有的命令行参数文件路径

### 方向 D：hook `queryIntentActivities` 等其他检测点
如果 JD SDK 通过 Intent 查询检测 BlackBox 的 Activity/Service，需要：
- 在 `IPackageManagerProxy` 中过滤 `queryIntentActivities`、`queryIntentServices` 等

---

## 参考截图

- `e2e_honor/screenshots/clone_current.png` — 非分身版正常验证码弹窗（用户对照）
- `e2e_honor/screenshots/05_captcha_delayed_*` — 分身版测试结果
