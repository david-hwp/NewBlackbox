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

## Wave 1 验收点

- `:app:compileDebugKotlin` 通过。
- `:app:assembleDebug` 能构建 `1.2.18-beta` 调试 APK。
- 小米真机安装后，首页店铺卡片出现“授权登录该店铺”。
- 点击后打开只显示“店铺授权”标题的弹窗，并加载 `http://100.99.88.6:14500/` 的 Xpra HTML5 页面。
- 支持平台后台页面可以配置“授权地址”；APP 不展示该地址，只通过 `/open?url=...` 透传给 ZR 控制接口。
- 服务器端虚拟桌面保持可通过 VNC 查看，便于人工观察同一个授权窗口。

## Wave 1 验证记录

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

## Wave 2: 登录表单自动定位与视口对齐

### 背景

Wave 1 已证明 Xpra 远端浏览器可以在 APP 弹窗内显示并交互，但不同平台登录页的用户名、密码、验证码和登录按钮初始位置不同。继续在 APP 侧按固定百分比判断输入框区域会失效，也会让用户在饿了么、美团这类桌面布局页面中先看到空白或非登录区域。

2026-06-15 的 Playwright DOM 探测结论：

- 京东秒送 `https://store.jddj.com/base/login`：`360x520` 视口内已能看到账号、密码和登录按钮。用户名约在 `x=52 y=190`，密码约在 `x=52 y=238`，登录按钮约在 `x=15 y=346`。
- 饿了么 `https://melody.shop.ele.me/login`：登录表单位于桌面页面右侧。用户名约在 `x=577 y=153`，密码约在 `x=577 y=218`，登录按钮约在 `x=565 y=274`。需要自动横向对齐到表单区域。
- 美团 `https://waimaie.meituan.com/new_fe/login_gw#/login`：登录表单居中偏右偏下。用户名约在 `x=262 y=270`，密码约在 `x=262 y=330`，登录按钮约在 `x=232 y=479`。需要自动横向和纵向对齐，并确保登录按钮不贴近弹窗底部。

### 目标

- 用户点击“授权登录该店铺”后，弹窗首次显示时就能看到该平台登录页的账号/手机号输入框、密码/验证码输入框、登录按钮。
- APP 侧不按平台硬编码坐标，也不展示授权地址。
- 服务器端根据页面 DOM 实时识别登录表单区域，并按 APP 传入的 WebView 逻辑视口尺寸自动对齐远端 Chrome 页面。
- 自动对齐只在页面初次加载和登录方式切换后执行，用户开始输入、拖动验证码或点击页面后不得持续强制重排。
- 对齐失败时保留 Wave 1 的原始全页显示行为，不阻断用户手工操作。

### 交付范围

- 在 `admin/scripts/browser/` 的 ZR 浏览器启动/控制脚本中增加登录表单定位能力。
- `/open` 保持接收 `phone`、`shopId`、`width`、`height`、`scale`、`url`；必要时可增加可选参数 `align=login`，默认启用登录表单对齐。
- 服务器端对 Chromium 页面执行 DOM 探测，识别可见的 `input`、`textarea`、`button[type=submit]`、`role=button` 和包含“登录/账号/密码/手机号/验证码/获取验证码”的元素。
- 计算用户名/手机号输入框、密码/验证码输入框、登录按钮及登录方式 tab 的联合包围盒，增加安全边距后得到目标显示区域。
- 优先使用 `window.scrollTo(left, top)` 对齐；如果页面禁止滚动或使用固定桌面布局，则注入一次性 CSS 平移，把目标表单区域移动到当前远端窗口左上方可见范围内。
- 对齐结果写入 `~/data/logs/browser-trace.jsonl`，记录平台 URL、viewport、候选元素、最终 `offsetX/offsetY`、对齐策略和失败原因。
- 保持现有 profile 目录规则：`~/data/profiles/<用户手机号>/<店铺ID>/chrome`。

### 实现任务

1. **表单候选识别**
   - 在服务器端实现可重试 DOM 探测，页面打开后等待最多 10 秒。
   - 候选元素必须可见，且 `getBoundingClientRect()` 宽高有效。
   - 输入框评分优先级：`password` > `tel/text/number/email`，并结合 `placeholder/name/id/class/aria-label` 中的中文和英文关键词。
   - 登录按钮评分优先级：`button[type=submit]`、文本包含“登录”、类名或属性包含 `login`。

2. **登录框有效区域计算**
   - 先选择“账号/手机号输入框 + 密码/验证码输入框 + 登录按钮”组合，再沿 DOM ancestor 向上寻找共同登录容器。
   - 登录容器必须包含登录方式 tab、输入框、登录按钮，以及可见的忘记密码、协议或注册入口等登录框内容。
   - 计算登录框有效可见内容的两个点：`visibleBox.leftTop` 和 `visibleBox.rightBottom`。这两个点定义要展示到 WebView 内的完整登录区域，而不是只展示输入控件最小包围盒。
   - 如果当前是验证码登录模式，只要能识别手机号输入框、验证码输入框或“获取验证码”按钮，也视为有效表单区域。

3. **视口对齐策略**
   - 使用一次性 CSS transform 将 `visibleBox.leftTop/rightBottom` 定义的登录框区域完整平移并缩放到当前远端窗口内。
   - 默认把登录框控制在 WebView 宽度约 `72%`、高度约 `66%`，居中偏上显示，避免登录框被放得过大、比例不自然。
   - 不使用页面 zoom 作为默认方案，避免再次破坏 Xpra 坐标映射和浏览器全局字体比例。
   - 对齐完成后标记 `window.__zrLoginAligned = true`，用户交互开始后不再循环调整。

4. **控制接口与日志**
   - `/open` 返回体增加 `alignment` 摘要，便于调试；APP 可忽略该字段。
   - trace 中新增事件：`login_align_probe`、`login_align_applied`、`login_align_failed`。
   - 对失败原因做分类：`no_candidates`、`timeout`、`navigation_failed`、`cross_origin_frame_only`、`script_error`。

5. **APP 协作边界**
   - APP 继续只传 WebView 逻辑宽高和授权 URL，不增加平台坐标表。
   - 现有 Xpra 坐标映射、防缩放、触摸拖动补丁保留。
   - 后续若要让键盘只在真实输入框上弹出，应优先消费服务器端 alignment 结果或 Xpra 当前 canvas 坐标，而不是恢复固定区域猜测。

### 验收点

- 本地 `git diff --check` 通过。
- `admin/scripts/browser/` 同步到 `root@aliyun:~/data` 后，`~/data/start-zr.sh` 能启动 `14500`、`14501`、`59019`。
- 分别调用以下三个 URL 的 `/open`，均返回 `ok=true`，trace 中出现 `login_align_applied` 或明确的 `login_align_failed`：
  - `https://store.jddj.com/base/login`
  - `https://melody.shop.ele.me/login`
  - `https://waimaie.meituan.com/new_fe/login_gw#/login`
- 通过 Xpra 或 VNC 观察三个平台：授权窗口首次出现时可见账号/手机号输入框、密码/验证码输入框和登录按钮。
- 小米真机安装后，点击京东、饿了么、美团店铺的“授权登录该店铺”，弹窗内首屏均对齐到登录表单区域。
- 用户点击输入框时不发生 WebView 自动放大，点击位置仍能映射到远端页面；验证码滑块出现时仍可拖动。

### 风险与回退

- 有些平台可能把登录表单放在跨域 iframe 内，服务器脚本无法读取内部 DOM。回退策略是只对可访问 DOM 做对齐，失败则保留原始全页显示并记录 `cross_origin_frame_only`。
- 平台页面可能在加载后异步切换布局。对齐脚本最多重试 10 秒；用户开始交互后不再自动调整，避免打断验证码和输入。
- CSS 平移会影响页面视觉位置，但浏览器命中测试会随 transform 变化；需要在小米真机重点验证点击映射。
- 本 wave 不处理账号托管、验证码识别、授权结果回写、自动登录或登录态同步。

### Wave 2 验证记录

- 2026-06-15：新增服务器端 `zr-login-align.js`，通过 Chromium DevTools Protocol 探测登录页 DOM，自动选择账号/密码或验证码/登录按钮，并优先滚动、必要时注入一次性 CSS 平移。
- 2026-06-15：`start-zr-browser.sh` 增加本地 `127.0.0.1:14502` remote debugging；`zr-browser-control.py` 在 `/open` 启动 Chrome 后执行对齐脚本，并在响应和 `~/data/logs/browser-trace.jsonl` 中写入 `alignment`。
- 2026-06-15：`admin/scripts/browser/` 已同步到 `root@aliyun:~/data`，`~/data/start-zr.sh` 重启后确认 `14500`、`14501`、`59019` 监听正常，`/health` 返回 `{"ok": true}`。
- 2026-06-15：服务端直接调用 `/open` 验证三平台均通过：
  - 京东秒送 `https://store.jddj.com/base/login`：`alignment.ok=true`，`strategy=panel-fit-transform`，`visibleBox.leftTop=(403,40)`、`visibleBox.rightBottom=(797,516)`，最终 viewport 区域约 `259x313`，`selectedControlsVisible=true`。
  - 饿了么 `https://melody.shop.ele.me/login`：`alignment.ok=true`，`strategy=panel-fit-transform`，`visibleBox.leftTop=(464,0)`、`visibleBox.rightBottom=(990,438)`，最终 viewport 区域约 `259x216`，`selectedControlsVisible=true`。
  - 美团 `https://waimaie.meituan.com/new_fe/login_gw#/login`：`alignment.ok=true`，`strategy=panel-fit-transform`，`visibleBox.leftTop=(184,142)`、`visibleBox.rightBottom=(616,649)`，最终 viewport 区域约 `259x304`，`selectedControlsVisible=true`。
- 2026-06-15：根据真机反馈，旧版只围绕账号/密码/登录按钮的最小包围盒会裁掉“账号登录/验证码登录”tab，且登录框局部放大比例不自然。已改为整体登录框有效区域识别，并将默认显示比例收敛到 WebView 宽度 `72%`、高度 `66%`，保留 tab、输入框、按钮和协议区。
- 2026-06-15：根据京东真机反馈，修复只显示顶部 logo、无输入框和登录按钮时仍被误判成功的问题。对齐脚本现在保留页面原始文档宽度，注入 wrapper 后重新读取候选控件位置，并要求账号/密码/登录按钮全部落在最终 `visibleBox` 内，否则 `alignment.ok=false`。
- 2026-06-15：为店铺卡片“授权登录该店铺”增加 `contentDescription` 和 `importantForAccessibility`，不改变视觉 UI，只用于 ADB/UIAutomator 精确定位授权入口，避免自动化误点店铺打开或删除区域。
- 2026-06-15：小米真机 `3ca26684` 安装 `1.2.18-beta`，网络配置为 `API=http://100.99.88.2:8006/api/`、`ZR stream=http://100.99.88.6:14500/`、`ZR control=http://100.99.88.6:14501/`。自动化验证美团、饿了么、京东三平台均触发 `/open`，服务器 trace 返回 `alignment.ok=true` 且 `allSelectedVisible=true`。
- 2026-06-15：OPPO 真机 `55J7JJWKTWKNHYZL` 已安装 `1.2.18-beta`。该设备未安装 Tailscale、无 `100.99.88.0/24` 路由，不能直接访问 `100.99.88.2` 或 `100.99.88.6`；验证时在 `172.20.0.13` 启动临时 TCP 转发到 `100.99.88.6:14500/14501`，并安装仅用于 OPPO 验证的 `172.20.0.13` 地址包。
- 2026-06-15：OPPO 真机在 `172.20.0.13` 验证网络下自动化验证美团、饿了么、京东三平台均通过；三平台 trace 均返回 `alignment.ok=true` 且 `allSelectedVisible=true`。OPPO 上系统会弹出“账号管家想要打开店铺管家引擎”确认框，验证脚本已勾选“始终允许打开”并继续。
- 2026-06-15：最终重新构建主配置 APK，恢复为 `API=http://100.99.88.2:8006/api/`、`ZR stream=http://100.99.88.6:14500/`、`ZR control=http://100.99.88.6:14501/`。

## 边界

- Wave 1 只做 MVP 验证，不实现授权状态回写、服务端账号池、安全审计或正式计费。
- 本阶段不改动引擎内应用 WebView，也不继续沿 Android 15 内嵌 WebView 兼容方向修复。
- Xpra/VNC 当前绑定 Tailscale IP，仅用于内网验证。
