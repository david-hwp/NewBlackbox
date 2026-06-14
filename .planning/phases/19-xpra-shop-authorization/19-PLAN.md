# Phase 19: Xpra 店铺授权窗口 MVP

## 目标

在不改动现有“私域吸粉”高级功能入口的前提下，为店铺卡片增加独立的“授权登录该店铺”入口。用户点击后，主 APK 打开名为“店铺授权”的弹窗，弹窗内实时显示服务端虚拟桌面的授权登录窗口，先用平台配置的授权地址验证服务器画面流式传输和基础交互可行性。

## Wave 1: 最小可行性验证

### 交付范围

- 首页店铺卡片在店铺 ID 下方增加可点击文字“授权登录该店铺”。
- 点击该文字打开“店铺授权”弹窗，不触发“私域吸粉”开关或高级功能扣费流程。
- 授权弹窗默认居中显示，宽度接近全屏，高度约占屏幕 2/3；软键盘弹出时由系统 resize，避免遮挡输入框。
- 弹窗头部只展示“店铺授权”标题，不展示店铺名、授权地址或其它说明。
- 弹窗内通过 WebView 加载 Xpra HTML5 入口，显示服务端虚拟桌面。
- Aliyun Ubuntu 服务器运行 `Xvfb :19`、`fluxbox`、Chromium、Xpra HTML5 和 VNC 查看通道。
- 后台支持平台表增加“授权地址”字段；服务端 Chromium 根据 APP `/open` 请求传入的 `url` 打开对应平台登录页，APP 侧只负责显示服务器画面和转发用户输入。
- APP 打开授权弹窗前先调用 ZR 控制接口，按当前用户手机号和店铺 ID 启动独立 Chromium profile，避免不同用户/店铺授权数据混淆。
- APP 将授权 WebView 的逻辑视口 `width/height` 和浏览器缩放 `scale` 传给 ZR 控制接口；服务器按该尺寸重启 Xvfb/Xpra/VNC 显示栈并启动同尺寸 Chromium，适配不同设备弹窗 WebView 大小。
- WebView 默认不主动弹出软键盘；仅当用户点击京东登录页用户名/密码输入区域时，才聚焦 Xpra 的隐藏输入板以调起键盘。

### 当前服务器拓扑

- 服务器：`ssh root@aliyun`
- Tailscale IP：`100.99.88.6`
- Xpra HTML5：`http://100.99.88.6:14500/`
- ZR 控制接口：`http://100.99.88.6:14501/`
- VNC 查看端口：`100.99.88.6:59019`
- 本仓库脚本源：`admin/scripts/browser/`
- 服务器运行根目录：`~/data`
- 服务器启动脚本：`~/data/start-zr.sh`
- 服务器浏览器脚本：`~/data/start-zr-browser.sh`
- 服务器控制脚本：`~/data/zr-browser-control.py`
- 服务器显示栈脚本：`~/data/start-zr-display.sh`
- 服务器日志目录：`~/data/logs`
- 浏览器数据目录：`~/data/profiles/<用户手机号>/<店铺ID>/chrome`
- 浏览器默认缩放：`1.25`

### 当前 APK 网络配置

- 后端 API：`http://100.99.88.2:8006/api/`
- 授权窗口 Xpra：`http://100.99.88.6:14500/`
- 授权控制接口：`http://100.99.88.6:14501/`
- 说明：小米真机已启用 Tailscale；后端服务器沿用原 `8006` 端口，只将 IP 从 `172.20.0.13` 切到 Tailscale IP `100.99.88.2`。

### 源与安装结论

Ubuntu 主源已切到阿里云 `mirrors.aliyun.com/ubuntu`，Docker apt 源已切到 `mirrors.aliyun.com/docker-ce`。Docker daemon 也已有镜像加速器。后续慢点主要来自 NodeSource、Tailscale 这类三方 HTTPS 源，或 Playwright 下载 Chromium 的浏览器二进制通道，它们不受 Ubuntu 主 apt 源影响。

## 验收点

- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 能构建 `1.2.18-beta` 调试 APK。
- 小米真机安装后，首页店铺卡片出现“授权登录该店铺”。
- 点击后打开只显示“店铺授权”标题的弹窗，并加载 `http://100.99.88.6:14500/` 的 Xpra HTML5 页面。
- 支持平台后台页面可以配置“授权地址”；APP 不展示该地址，只通过 `/open?url=...` 透传给 ZR 控制接口。
- 服务器端虚拟桌面保持可通过 VNC 查看，便于人工观察同一个授权窗口。

## 验证记录

- 2026-06-15：合入最新 `origin/dev`，当前 Phase 19 分支快进到 dev 后恢复 Xpra MVP 改动。
- 2026-06-15：`git diff --check` 通过。
- 2026-06-15：`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:assembleDebug --no-daemon -PAPP_VERSION_NAME=1.2.18-beta -PDUODIAN_API_BASE_URL=http://100.99.88.2:8006/api/ -PDUODIAN_ZR_STREAM_URL=http://100.99.88.6:14500/ -PDUODIAN_ZR_CONTROL_URL=http://100.99.88.6:14501/` 通过。
- 2026-06-15：已安装 `app/build/outputs/apk/debug/zhanghaoguanjia_1.2.18-beta_arm64-v8a-debug.apk` 到小米真机 `3ca26684`，包版本为 `versionName=1.2.18-beta`、`versionCode=50027`。
- 2026-06-15：阿里云 Xpra 服务端确认 `Xvfb :19` 为 `360x520`，Chromium 窗口为 `360x520+0+0`，Xpra 监听 `100.99.88.6:14500`，VNC 监听 `100.99.88.6:59019`。
- 2026-06-15：服务端启动脚本已迁入本仓库 `admin/scripts/browser/`，服务器运行产物统一放在 `~/data`，包括脚本、日志、VNC 密码和浏览器 profile。
- 2026-06-15：`~/data/start-zr.sh` 启动后确认 `14500`、`14501`、`59019` 监听正常；`/open?phone=13265710803&shopId=test-shop` 成功生成 `/root/data/profiles/13265710803/test-shop/chrome`，trace 写入 `/root/data/logs/browser-trace.jsonl`。
- 2026-06-15：`/open?phone=15200837196&shopId=15397100&width=996&height=1120` 验证通过；Xvfb 尺寸变为 `996x1120`，Chromium 窗口为 `996x1120+0+0`，控制服务保持在 `14501`。
- 2026-06-15：授权入口调整为居中弹窗；APP UI 只显示“店铺授权”标题，平台授权地址仅作为后台配置和 `/open` 请求参数使用。
- 2026-06-15：后台支持平台表新增 `authorization_url`，后台页面显示和编辑“授权地址”；后端启动初始化器会为既有数据库补列。

## 边界

- Wave 1 只做 MVP 验证，不实现授权状态回写、服务端账号池、安全审计或正式计费。
- 本阶段不改动引擎内应用 WebView，也不继续沿 Android 15 内嵌 WebView 兼容方向修复。
- Xpra/VNC 当前绑定 Tailscale IP，仅用于内网验证。
