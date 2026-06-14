# Phase 17: 店铺卡片新功能

## Wave 1: 话费分钟计费

目标：在现有算力余额和交易日志体系旁新增独立的“话费”余额，单位为分钟。话费不参与店铺扣算力、订阅覆盖和店铺有效期逻辑，只提供后台充值、APP 展示、用户之间赠送和赠送方取回。

### 数据与后端

- `users` 新增 `phone_minutes_balance`，默认 0，表示用户话费分钟余额。
- `transaction_logs.type` 继续作为交易类型字段，新增：
  - `PHONE_CONSUME`：话费消耗
  - `PHONE_OUT`：话费转出
  - `PHONE_IN`：话费转入
- 保留原算力类型 `CONSUME` / `OUT` / `IN`，避免影响 phase14 订阅和店铺扣点。
- 超管在后台编辑用户时可直接设置话费余额；余额增加写 `PHONE_IN`，余额减少写 `PHONE_CONSUME`。
- APP 话费转赠接口：
  - `POST /compute/phone-minutes/gift`
  - 同渠道用户之间按手机号转入，不能转给自己，余额不足时报错。
- APP 话费取回接口：
  - `GET /compute/phone-minutes/reclaim/latest`
  - `POST /compute/phone-minutes/reclaim`
  - 只按 `PHONE_OUT` 的最新赠送记录计算可取回数，扣除接收方之后产生的 `PHONE_CONSUME` 和赠送方已经产生的 `PHONE_IN` 取回记录，不受算力消耗影响。

### 管理后台

- 用户列表新增“话费余额(分钟)”列。
- 用户编辑弹窗新增“话费余额”输入框。
- 交易日志新增三类筛选和展示：话费消耗、话费转出、话费转入。
- Dashboard 最近交易同步展示新增话费交易类型。

### APP

- 首页 header 右侧去掉“交易日志”按钮，替换为 `话费余额X分钟`。
- “我的”页面个人信息卡片在算力余额后增加话费余额。
- “我的”页面“算力管理”改名为“交易中心”。
- 交易中心保留算力赠送、算力取回、交易日志，并在交易日志上方新增话费赠送、话费取回。
- 话费赠送/取回复用现有算力赠送/取回交互，进入时切换标题、余额、单位、接口和错误文案。

### 验证

- 后端：`mvn test`
- 后台前端：`npm run build`
- Android：`:app:compileDebugKotlin`
- 后续真机验收：
  - 后台给指定用户加话费后，APP 首页和我的页显示分钟余额。
  - 话费赠送后，转出方余额减少，接收方余额增加，交易日志出现 `PHONE_OUT` / `PHONE_IN`。
  - 话费取回后，赠送方余额增加，接收方余额减少，交易日志出现取回对应的 `PHONE_IN` / `PHONE_OUT`。

## Wave 2: 微信分身首次绑定 clone mapping 修复

目标：修复小米真机打开微信分身时，本地引擎在新分身首次绑定阶段因为缺少 `clone-instances.json` 映射而无法初始化 scoped storage 目录的问题。

### 问题结论

- 小米真机微信真实包名为 `com.tencent.mm`，launcher 为 `com.tencent.mm/.ui.LauncherUI`，当前失败不是包名错误。
- 日志已经进入本地店铺环境准备流程，失败点在本地引擎授权和分身目录初始化，不是服务器地址、网络或接口问题。
- 当前还不能判断为微信运行时兼容性失败，因为微信 Activity / ProxyActivity 尚未真正启动。
- 直接错误为：`Missing clone mapping for package=com.tencent.mm, userId=31`。
- 代码根因：`CloneInstanceStore.bindCloneUser()` 和 `writeAuthorization()` 在写入 clone mapping 前先调用 `ScopedCloneStorage.ensurePackageDirs()`；而 `ensurePackageDirs()` 通过 `BEnvironment.getDataDir()` 反查 `clone-instances.json`，新分身首次绑定时形成循环依赖。
- 第一层修复后，新增平台首次注册宿主包又暴露第二层问题：`PackageManagerCompat.generateApplicationInfo()` 在安装/包元数据阶段用 `userId=0` 生成 `ApplicationInfo.dataDir`，但 `userId=0` 是引擎系统用户，不是店铺分身用户，不能要求存在店铺 clone mapping。

### 修复范围

- 调整 `CloneInstanceStore.bindCloneUser()` 初始化顺序：确认本地虚拟用户存在后，先写入 `clone-instances.json`，再初始化 scoped package 目录。
- 调整 `CloneInstanceStore.writeAuthorization()` 初始化顺序：先写入 clone mapping，再创建 scoped 目录和授权文件，避免在同一锁内递归调用 `bindCloneUser()`。
- 调整 `PackageManagerCompat.generateApplicationInfo()`：仅在 `USER_SYSTEM(0)` 的安装/包元数据阶段缺少 clone mapping 时，使用 legacy 系统用户目录生成 `ApplicationInfo`；真实分身用户仍必须命中 scoped clone mapping。
- 保持现有 `ScopedCloneStorage` 路径规则不变：仍使用 `accounts/{serverUserId}/cards/{cloneInstanceId}/user/{localVirtualUserId}/{packageName}`。
- 不改服务器、主 APP 业务接口，也不把微信特殊化；该修复适用于所有新分身首次绑定。

### 验证

- Android：`:Bcore:compileDebugKotlin`
- Android：`:app:compileDebugKotlin`
- 小米真机验收：
  - 点击微信店铺卡片后不再出现 `Missing clone mapping for package=com.tencent.mm, userId=...`。
  - 新增平台首次注册包时不再因 `userId=0` 缺少 clone mapping 失败。
  - 引擎 `clone-instances.json` 中能查到 `packageName=com.tencent.mm`、对应 `userId` 和 `cloneInstanceId`。
  - 若后续进入微信 Activity 后仍失败，再按微信运行时兼容性单独定位。
