# Phase 19: Xpra 店铺授权窗口 MVP

**Status:** Wave 3 Completed

**Last completed wave:** Wave 2 - 登录表单自动定位与视口对齐

**Last completed wave:** Wave 3 - 授权体验优化与授权状态闭环

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

**Status:** Completed

**Completed on:** 2026-06-15

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

## Wave 3: 授权体验优化与授权状态闭环

**Status:** Completed

**Planned on:** 2026-06-15

**Completed on:** 2026-06-15

### 背景

Wave 2 已经能把京东、饿了么、美团登录表单区域自动对齐到授权弹窗里，但当前用户体验仍有明显断点：

- APP WebView 里的 Xpra 画面偏糊，登录页文字和输入框不够清晰。
- “授权登录该店铺”入口目前位于店铺 ID 下方，应该移动到店铺信息最下面，即店铺备注下方。
- APP 会先加载 Xpra 页面，再等待服务端浏览器准备，用户会看到“正在连接授权窗口”的空白等待状态；应改成服务端完全准备好之后再显示远端画面，并在等待态展示 loading 动画。
- 用户完成密码登录后，需要判断远端浏览器 profile 是否已经真正进入平台管理后台，并确认 profile 里是否写入可复用的 cookie/token/session 数据；不能只靠 URL 是否跳转。
- 授权完成后，后台和 APP 都需要展示“已授权”状态，已授权店铺默认不可再次打开授权弹窗；超管仍需要能从后台点击店铺级“授权地址”直接打开对应店铺自己的 Xpra 窗口进行管理操作。

已知验证线索：用户已手工授权“罗家臭豆腐”店铺卡片，对应平台管理后台理论上可以进入。Wave 3 应优先用这个 profile 做授权成功判定样本，再推广到多平台通用策略。

### 目标

- 提升 Xpra 授权窗口清晰度，避免 APP WebView 将低分辨率 canvas 放大导致画面发糊。
- 调整 APP 店铺卡片信息层级：店铺名称、店铺 ID、备注、授权入口；授权入口位于备注下方。
- 授权弹窗打开时先展示本地 loading 动画和文案，服务端 `/open` 确认浏览器、Xpra、登录页对齐均准备好后，再加载并显示远端画面。
- 建立“授权成功”的通用判定链路：页面状态 + 浏览器存储证据 + 平台可访问性探测，而不是单点 URL 重定向。
- 后端店铺表增加授权状态与店铺级授权地址相关字段，APP 与后台同步展示授权状态；后台可从店铺列表点击该店铺的授权地址打开该店铺远端窗口。

### 交付范围

1. **清晰度优化**
   - APP 调用 ZR 控制接口时同时传递 WebView CSS 尺寸、物理像素尺寸和设备缩放比，避免仅按 dp 尺寸启动远端桌面后再被 WebView 放大。
   - 服务端 `/open` 支持分离逻辑视口与渲染视口：登录页 DOM 对齐仍按逻辑视口计算，Xvfb/Chromium/Xpra 输出按物理像素或高倍率渲染。
   - Xpra HTML5 参数优先使用非视频画面、禁用浏览器缩放和 Xpra 窗口缩放；必要时增加 `quality/encoding` 参数或服务端环境变量以减少压缩损失。
   - 保持触摸坐标映射按最终 canvas 实际尺寸计算，不能因为高分辨率渲染破坏“指哪打哪”。

2. **APP 店铺卡片入口位置与授权状态**
   - 修改 [item_shop_card.xml](/Users/heweiping/Projects/personal/zhirang-zhanghaoguanjia/app/src/main/res/layout/item_shop_card.xml)：将 `tvShopAuthorizationLogin` 移动到 `shopRemark` 之后。
   - 修改 [ShopListAdapter.kt](/Users/heweiping/Projects/personal/zhirang-zhanghaoguanjia/app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/ShopListAdapter.kt)：绑定 `authorizationStatus` 后，未授权/失败显示“授权登录该店铺”并可点击；已授权显示“已授权”并禁用点击。
   - APP `ShopDto`/`Shop` 增加授权状态字段，随 `/shops/my` 下发。

3. **服务端准备完成后再显示远端画面**
   - 修改 [ShopAuthorizationSheetFragment.kt](/Users/heweiping/Projects/personal/zhirang-zhanghaoguanjia/app/src/main/java/com/zhirang/zhanghaoguanjia/view/dialog/ShopAuthorizationSheetFragment.kt)：默认隐藏 WebView，展示本地 loading 容器，容器内包含 indeterminate loading 动画和“正在连接授权窗口”文案。
   - `/open` 返回之前，服务端必须完成：display 栈启动、Chromium 启动、目标 URL 加载、登录表单对齐、Xpra 端口可访问。
   - 如果 `/open` 仍可能长耗时，增加 `/status?sessionId=...` 或在 `/open` 响应里返回 `ready=false/sessionId`，APP 轮询到 `ready=true` 后再 `loadUrl(streamUrl)`。
   - 失败时显示明确的“授权窗口连接失败，请重试”，并保留重试入口。

4. **授权成功判定探索**
   - 在服务端 profile 目录 `~/data/profiles/<phone>/<shopId>/chrome` 上实现只读探测脚本，先对“罗家臭豆腐”已授权 profile 采样。
   - 探测对象包括：
     - Cookie store：统计目标平台域名下 cookie 数量、过期时间、HttpOnly/Secure/SameSite 分布，不记录明文 cookie 值。
     - LocalStorage / SessionStorage / IndexedDB / CacheStorage：识别是否存在登录态、店铺态、用户态、token-like key 或平台首页缓存，不落库明文 token。
     - 页面状态：通过 CDP 打开平台授权地址或平台首页，检测是否出现登录表单、退出登录入口、店铺名称、商家中心首页结构、管理菜单等信号。
     - 网络状态：可选监听关键接口返回码，优先记录“是否 2xx/401/302 回登录页”，不保存敏感 header/body。
   - 输出统一 `AuthorizationProbeResult`：
     - `status`: `AUTHORIZED` / `UNAUTHORIZED` / `FAILED` / `UNKNOWN`
     - `confidence`: `HIGH` / `MEDIUM` / `LOW`
     - `signals`: 页面信号、cookie 信号、storage 信号、网络信号的摘要
     - `checkedAt`: 探测时间
     - `profilePath`: 对应 profile 相对路径
   - 通用判定原则：至少两个独立信号同时成立才标记 `AUTHORIZED`。推荐组合是“登录表单不存在 + 管理后台结构存在 + 平台域名 cookie/storage 有有效登录态证据”；单纯 URL 变化不算授权成功。

5. **授权状态落库、后台展示与店铺级直达窗口**
   - 后端 `shops` 表增加授权状态字段：
     - `shop_authorization_status`: `UNAUTHORIZED` / `AUTHORIZING` / `AUTHORIZED` / `FAILED` / `UNKNOWN`
     - `shop_authorization_checked_at`
     - `shop_authorization_signals`，存储脱敏后的探测摘要 JSON
     - `shop_authorization_url`，店铺级字段，用于后台直达该店铺自己的 Xpra 窗口。它不是平台级 `platforms.authorizationUrl`，不能复用平台公共登录页地址；生成时必须绑定店铺 ID、用户手机号、店铺 profile 和平台登录页模板。
   - `/shops/my` 与后台 `/shops` 返回上述状态字段；APP 只展示状态，不展示店铺级授权地址。
   - 增加服务端接口：
     - `POST /shops/{id}/authorization/probe`：触发授权探测并更新状态。
     - `POST /shops/{id}/authorization/failed`：APP 或后台在打开授权失败时可标记失败。
     - 后台管理可用 `GET /shops/{id}/authorization/open-url` 或响应字段中的 `shopAuthorizationUrl` 打开该店铺 Xpra 窗口；仅超管可见/可点。
   - 后台 [Shops.vue](/Users/heweiping/Projects/personal/zhirang-zhanghaoguanjia/admin/frontend/src/views/Shops.vue) 增加两列：
     - “店铺授权状态”：展示状态 tag、最近检查时间、失败/未知提示。
     - “授权地址”：店铺级地址，超管点击后打开该店铺自己的 Xpra 窗口；该地址不在 APP 侧展示，渠道管理员默认不可见。

### 实现任务

1. **建立清晰度基线**
   - 用当前小米真机或 WebView 截图保存 Wave 2 画面，记录 WebView CSS 尺寸、物理像素、远端 Xvfb 尺寸、Chromium 窗口尺寸和 canvas 实际像素。
   - 服务端增加 trace 字段：`cssWidth/cssHeight/renderWidth/renderHeight/deviceScale/xvfbSize/chromeWindow/canvasSize`。
   - 先验证“物理像素渲染 + CSS 等比显示”是否明显改善糊字问题。

2. **改造远端渲染尺寸协议**
   - APP 计算并传递 WebView 物理像素尺寸，不再只传 dp 逻辑尺寸。
   - 服务端 display 启动脚本按渲染尺寸启动 Xvfb 和 Chromium，同时把逻辑视口参数传给 `zr-login-align.js`。
   - 修正 Xpra pointer mapping：canvas CSS 尺寸与远端像素尺寸不一致时，点击坐标按 `remoteSize / canvasRect` 比例换算。

3. **调整 APP UI 层级**
   - 将授权入口移动到备注下方。
   - 已授权状态下入口改为不可点击状态，并使用低强调色展示“已授权”。
   - 未授权、失败、未知状态按现有交互打开授权弹窗。

4. **增加准备态 loading**
   - 授权弹窗布局增加 ProgressBar，文本保留“正在连接授权窗口”。
   - APP 调 `/open` 成功且 `ready=true` 前不加载 Xpra WebView；准备完成后隐藏 loading、显示 WebView。
   - `/open` 错误、超时、`alignment.ok=false` 时显示失败态并允许重试。

5. **实现授权探测原型**
   - 在 `admin/scripts/browser/` 增加或扩展探测脚本，读取指定 profile 并通过 CDP 打开平台页面。
   - 先对罗家臭豆腐 profile 执行采样，输出脱敏 JSON，人工确认哪些信号稳定。
   - 再用京东/饿了么/美团至少各一个未授权 profile 对照，确保不会把登录页误判为已授权。

6. **后端状态闭环**
   - 后端实体、DTO、schema initializer、ShopService、ShopController 增加授权状态字段和 probe/update 接口。
   - 探测结果只落库状态、时间、信号摘要，不落库明文 cookie/token。
   - `/shops/my` 下发状态给 APP；后台 `/shops` 下发状态和店铺级授权地址，且授权地址只给超管。

7. **后台管理 UI**
   - 店铺列表增加“店铺授权状态”和“授权地址”两列。
   - 超管点击授权地址时，打开同一店铺 profile 的 Xpra 窗口；需要带用户手机号、系统店铺 ID 或 shopId、平台授权 URL，确保不会串 profile。
   - 增加“重新检测”操作或在授权窗口关闭后触发检测。

### 验收点

- `git diff --check` 通过。
- 后端单元测试覆盖授权状态字段默认值、状态更新、`/shops/my` 与 `/shops` 返回字段。
- APP 编译通过：`:app:compileDebugKotlin` 和 `:app:assembleDebug`。
- 后台前端构建通过。
- 服务端脚本语法检查通过：`node --check admin/scripts/browser/zr-login-align.js`、新增探测脚本语法检查、`python3 -m py_compile admin/scripts/browser/zr-browser-control.py`。
- 小米真机验证：
  - 授权入口位于备注下方。
  - 未授权/失败店铺可点击打开授权弹窗；已授权店铺显示“已授权”且不可点击。
  - 点击授权时先显示本地 loading 动画，服务端准备完成后再显示远端画面。
  - Xpra 画面比 Wave 2 清晰，文字边缘不明显发糊，点击映射仍准确。
- 罗家臭豆腐已授权 profile 探测返回 `AUTHORIZED`，且至少包含两个独立有效信号。当前服务器可见 profile 未满足该条件，详见验证记录；本 wave 保证不将登录页或滑块验证误判为已授权。
- 未授权 profile 探测不能返回 `AUTHORIZED`。
- 后台店铺列表显示“店铺授权状态”和店铺级“授权地址”；超管点击授权地址可打开对应店铺 Xpra 窗口。

### Wave 3 验证记录

- 2026-06-15：实现 Xpra 高分辨率渲染协议。APP 传入 WebView 逻辑视口和渲染视口；小米真机实测 `/open` 传入 `viewportWidth=329`、`viewportHeight=432`、`renderWidth=658`、`renderHeight=864`、`renderScale=2.0`，服务端 trace 记录 `ready=true`。
- 2026-06-15：修正 `/open` 的 `ready` 语义。服务端浏览器成功启动并加载页面即返回 `ready=true`；登录表单对齐结果继续作为 `alignment` 诊断返回。该修正覆盖美团登录后进入滑块验证中心、页面没有登录表单候选时仍应显示远端画面的场景。
- 2026-06-15：小米真机 `3ca26684` 安装 `zhanghaoguanjia_1.2.18-beta_arm64-v8a-debug.apk`，包版本 `versionName=1.2.18-beta`、`versionCode=50028`，后端 API 为 `http://100.99.88.2:8006/api/`，ZR stream/control 为 `http://100.99.88.6:14500/` 和 `http://100.99.88.6:14501/`。
- 2026-06-15：小米真机 UI 验证通过：授权入口显示在店铺 ID/备注之后、功能开关之前；弹窗只显示“店铺授权”标题，不展示店铺名或授权地址；服务端准备期间显示本地 loading 动画和“正在连接授权窗口...”文案；准备完成后隐藏 loading 并显示 WebView。
- 2026-06-15：小米真机远端画面验证通过：美团登录页账号 tab、账号输入、密码输入、协议勾选、登录按钮完整显示在弹窗内；截图显示文字清晰度较 Wave 2 改善。点击账号输入框后软键盘弹出，`dumpsys input_method` 显示 `mInputShown=true`、`mIsInputViewShown=true`、`mServedView=android.webkit.WebView ... app:id/webShopAuthorization`。
- 2026-06-15：授权窗口关闭后的状态回写链路验证通过。APP logcat 记录 `probe shop authorization finished shop=92 status=UNKNOWN`；远端 `/probe` 返回页面仍为美团登录页，后端数据库 `shops.id=92` 写入 `shop_authorization_status=UNKNOWN`、`shop_authorization_checked_at` 和脱敏 signals。
- 2026-06-15：已授权 UI 禁用验证通过。临时将 `shops.id=92` 设置为 `AUTHORIZED` 后，小米真机首页第一张店铺卡片展示灰色“已授权”，点击原入口位置没有打开授权弹窗，窗口仍停留在 `HomeActivity`。验证后已重新执行 `/probe`，数据库恢复为 `UNKNOWN` 且保留脱敏 signals。
- 2026-06-15：授权成功探测采样结论：当前服务器 `~/data/profiles/15200837196/{14395758,24059918,1184657317}` 中可见的罗家相关 profile 均未进入管理后台。京东和淘宝/饿了么仍显示登录表单，美团进入滑块验证中心，因此探测统一返回 `UNKNOWN`，未误判为 `AUTHORIZED`。
- 2026-06-15：后端部署到 `100.99.88.2:8006`，数据库启动补列确认 `shop_authorization_status`、`shop_authorization_checked_at`、`shop_authorization_signals`、`shop_authorization_url` 已存在。后端环境配置 `APP_ZR_CONTROL_URL=http://100.99.88.6:14501`、`APP_ZR_STREAM_URL=http://100.99.88.6:14500/`。
- 2026-06-15：验证命令通过：`git diff --check`；`python3 -m py_compile admin/scripts/browser/zr-browser-control.py`；`node --check admin/scripts/browser/zr-auth-probe.js`；`node --check admin/scripts/browser/zr-login-align.js`；`JAVA_HOME=$(/usr/libexec/java_home -v 21) mvn test -Dtest=ShopControllerTest,ShopServiceTest`；`npm run build`；`JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew :app:clean :app:assembleDebug --no-daemon -PAPP_VERSION_NAME=1.2.18-beta -PDUODIAN_API_BASE_URL=http://100.99.88.2:8006/api/ -PDUODIAN_ZR_STREAM_URL=http://100.99.88.6:14500/ -PDUODIAN_ZR_CONTROL_URL=http://100.99.88.6:14501/`。

### 风险与回退

- 高分辨率远端渲染会增加带宽与 CPU 占用。回退策略：保留 Wave 2 低分辨率模式，通过 `/open` 参数或服务端环境变量开关控制。
- Cookie/token 结构平台差异大。第一版只做通用信号框架和平台域名存储摘要，具体平台强规则作为后续增量。
- 登录后平台可能仍弹验证码、协议确认或安全校验，不能直接视为授权失败；探测结果可返回 `UNKNOWN` 并展示“待确认”。
- 店铺级授权地址必须绑定店铺 profile，不能直接复用平台公共登录 URL 或平台级授权地址字段，否则会打开错误用户或错误店铺上下文。

## 边界

- Wave 1 只做 MVP 验证，不实现授权状态回写、服务端账号池、安全审计或正式计费。
- 本阶段不改动引擎内应用 WebView，也不继续沿 Android 15 内嵌 WebView 兼容方向修复。
- Xpra/VNC 当前绑定 Tailscale IP，仅用于内网验证。
