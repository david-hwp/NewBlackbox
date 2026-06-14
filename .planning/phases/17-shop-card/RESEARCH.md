# Phase 17 Research: 微信分身分享目标识别

## 2026-06-14 OPPO 真机验证记录

### 结论

- 分身微信可以通过主 APP 店铺卡片里的微信按钮调起微信自带分享链路。
- 分享目标不能只靠普通 `logcat` 推断，微信不会稳定输出联系人备注名。
- 微信内部从联系人选择页跳转到发送页时，`Intent` extras 中会携带稳定目标字段：
  - `Select_Conv_User=wxid_4661706616414`
- 本次人工操作选中的联系人显示名为：`贺伟平`。
- 因此本次分享目标可判定为：`贺伟平 / wxid_4661706616414`。

### 关键证据

- 测试设备：OPPO 真机。
- 分身包名：`com.tencent.mm`。
- 分身本地用户：`userId=20`。
- 分身目录：
  - `blackbox/accounts/51/cards/CLN1-15200831111-com.tencent.mm-N1-U20-R2a5f2357/user/20/com.tencent.mm`
- 第二轮日志文件：
  - `/tmp/oppo_wechat_ui_probe_20260614_043227.log`
- 关键日志：
  - `SendAppMessageWrapperUI` 启动时出现 `extra[Select_Conv_User]=java.lang.String{wxid_4661706616414}`
  - `SelectConversationUI` 页面 UI 文本中出现 `贺伟平`
  - 同一流程中的分享内容为 `测试从店铺管家分享到分身微信`

### 自动化方案

- 主 APP 只负责发起分身微信分享请求。
- 引擎在微信分身内部监控 Activity 跳转：
  - `ShareImgUI`
  - `SelectConversationUI`
  - `SendAppMessageWrapperUI`
- 引擎优先从 `SendAppMessageWrapperUI` 的 `Intent` extras 中读取：
  - `Select_Conv_User`
- 引擎在 `SelectConversationUI` 或确认发送页读取当前页面可见文本，用于把 `wxid` 映射为用户可读的备注/昵称。
- 建议后续把调试日志改成结构化事件，写入主 APP 可读取的一次性结果：
  - `packageName`
  - `cloneUserId`
  - `selectedUser`
  - `displayName`
  - `shareText`
  - `timestamp`
- 主 APP 读取并上报后立即删除本地事件文件，避免引擎长期保存业务数据。

### 风险与边界

- `EnMicroMsg.db` 当前不是普通明文 SQLite，不能直接用 `sqlite3` 查询联系人表。
- `Select_Conv_User` 是稳定的微信内部目标 ID，但备注名需要结合 UI 文本或其它可读缓存反查。
- UI 文本里可能同时出现多个候选联系人，正式实现时需要在用户点击联系人后、进入 `SendAppMessageWrapperUI` 前后建立最近选择上下文，避免把列表中其它联系人误配给最终 `wxid`。
- 本方案只记录用户本次主动选择并发送的目标，不做全量通讯录采集。

## 2026-06-14 跨进程回调问题补充

### 现象

- OPPO 真机上，美团第一张店铺卡片点击微信分享并选择“贺伟平”后，分身微信日志能看到：
  - `Select_Conv_User=wxid_4661706616414`
  - `SendAppMessageWrapperUI`
- 但主 APP 私有目录没有生成：
  - `files/wechat-share-targets/shops/<systemShopId>/last.json`
- 店铺卡片因此没有展示微信联系人信息，主 APP 日志最终出现捕获超时。

### 根因

- 初始实现把捕获会话注册在 `BlackBoxEngineService` 所在进程 `com.zhirang.zhanghaoguanjia.engine`。
- 分身微信捕获事件通过 `BActivityManagerService.dispatchWechatShareTarget()` 进入 `com.zhirang.zhanghaoguanjia.engine:black`。
- `WechatShareTargetDispatcher` 是进程内静态监听器，不能跨进程共享监听列表。
- 因此事件到达 `:black` 时找不到主 APP 正在等待的 capture session，主 APP 最终超时。
- 后续修复必须让 capture session 注册在 `:black` ActivityManager 服务进程，而不是引擎 AIDL 服务本进程。

### 结论

- `BlackBoxEngineService.startWechatShareCapture()` 和 `cancelWechatShareCapture()` 需要通过 `BlackBoxCore.get().getService(ServiceManager.ACTIVITY_MANAGER)` 获取远程 `:black` binder。
- 不能使用 `ServiceManager.getService(ServiceManager.ACTIVITY_MANAGER)`，该调用在引擎进程内只会返回本进程单例，仍然造成跨进程静态监听失效。
- 主 APP 按系统店铺 ID 保存接收方；引擎只做一次性捕获和解析，不长期保存接收方业务数据。

## 2026-06-14 OPPO 回归阻塞记录

### 已验证链路

- 主 APP 使用内网服务地址 `http://172.20.0.13:8006/api/` 构建并安装到 OPPO。
- 美团第一张非微信店铺卡片已展示新增功能栏：
  - `分享` 按钮。
  - `差评定位`、`经营日报`、`外呼好评`、`评价申诉`、`私域吸粉` 五个占位开关。
- 点击美团店铺卡片的 `微信` 按钮后，主 APP 能找到微信工具卡并调用引擎：
  - 微信 clone：`CLN1-15200831111-com.tencent.mm-N1-U20-R2a5f2357`
  - local user：`20`
  - 分身目录：`blackbox/accounts/51/cards/CLN1-15200831111-com.tencent.mm-N1-U20-R2a5f2357/user/20/com.tencent.mm`
- 捕获会话已注册到 `com.zhirang.zhanghaoguanjia.engine:black`：
  - `BActivityManagerService startWechatShareCapture pid=... package=com.tencent.mm userId=20`
  - `WechatShareCaptureManager wechat share capture started package=com.tencent.mm userId=20`
- 主 APP 直接调用 `EngineProxy.startActivityAsUser()` 启动分身微信分享，不再额外调用 `prepareLaunch()`，避免触发 `killAllOtherProcessesGlobal` 导致微信进程被清理。

### 当前阻塞

- 分身微信 `ShareImgUI` 被成功拉起后，微信内部立即跳转到：
  - `com.tencent.mm.plugin.account.ui.SimpleLoginUI`
- UI dump 显示当前页面标题为 `登录微信`，页面包含 `微信号/QQ号/邮箱登录`、`账号`、`密码`、`同意并登录`。
- 当前流程没有进入 `SelectConversationUI` / `SendAppMessageWrapperUI`，所以没有出现 `Select_Conv_User`，主 APP 私有目录也没有生成：
  - `files/wechat-share-targets/shops/<systemShopId>/last.json`
- 当前微信分身目录里能看到 `MicroMsg/36d6799f4e5b829ad69318cd3d597c95/EnMicroMsg.db`、`account.bin`、`autoauth.cfg` 等账号相关文件，但搜索不到本 wave 验收目标中的 `wxid_4661706616414` 和 `贺伟平`。

### 判断

- 当前失败点不是卡片 UI、AIDL 回调注册或分身启动 API。
- 当前 OPPO 上这张微信工具卡的本地会话已被微信判定为未登录，或者现存分身数据不是此前能选择 `贺伟平` 的那份完整有效会话。
- Phase 17 已按当前收敛范围标记为 `Completed`。完整一键分享到微信联系人保留为遗留研究项；后续需要在微信工具卡恢复已登录态后重新点击美团第一张卡片 `微信`，选择 `贺伟平`，确认生成 `last.json` 并展示 `贺伟平 / wxid_4661706616414 / 个人微信`，再验证 `分享` 按钮发送测试文本。
