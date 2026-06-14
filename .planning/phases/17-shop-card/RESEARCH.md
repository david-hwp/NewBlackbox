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
