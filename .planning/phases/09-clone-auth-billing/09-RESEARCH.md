# Phase 09 Research: Clone 授权计费与长期令牌

## Research Summary

本阶段的核心问题不是店铺信息识别，而是如何把“扣费依据”和“打开授权”稳定绑定到分身实例。结论是：使用服务端签发的 `cloneInstanceId` 作为唯一主键；服务端签发长期 `authorizationToken`；引擎只验证 token，不参与复杂业务逻辑。

## Options Considered

### Option A: APP 本地生成 cloneId

优点：
- 实现简单。
- 不需要服务端参与 ID 生成。

缺点：
- APP 可被篡改后伪造 cloneId。
- 无法在服务端保存不返回 APP 的随机校验码来绑定 cloneId。
- 可审计性弱。

结论：不采用。

### Option B: 公钥加密 cloneId

优点：
- 看起来能隐藏 cloneId 内容。

缺点：
- 公钥是公开的，任何人都能用公钥加密伪造内容。
- “加密”不能替代“签名”或服务端保存随机码。
- 引擎仍然需要判断密文是否真实来自服务器。

结论：不作为安全核心。

### Option C: 服务端签名长期授权 token

优点：
- 私钥只在服务端，APP/引擎无法伪造。
- 引擎只需公钥验签，工作量低。
- 不依赖 Android Keystore，不受 Android 系统版本差异影响。
- token 可包含授权起止时间，APP 和引擎都可本地解析。

缺点：
- 长期 token 在到期前不能实时吊销。
- 删除店铺必须删除本地分身目录才能让 token 自然消失。

结论：采用。

## Final Technical Direction

### Identity

`cloneInstanceId` 由服务端生成：
```text
CLN1-{phone}-{packageName}-N{cloneSequence}-U{localVirtualUserId}-R{randomDigest}
```

服务端同时保存：
- `clone_validation_code`：随机码原文，不返回 APP
- `clone_validation_hash`：`SHA-256(cloneInstanceId + ":" + clone_validation_code)`
- `clone_sequence`
- `local_virtual_user_id`

### Authorization Token

`authorizationToken` 使用 `RS256` / `SHA256withRSA` 签名，claims：
```json
{
  "typ": "clone_auth",
  "serverUserId": 123,
  "phone": "13265710803",
  "cloneInstanceId": "CLN1-...",
  "packageName": "com.jd.mrd.jingming",
  "localVirtualUserId": 7,
  "credentialVersion": 3,
  "authStartAt": 1710000000,
  "authExpireAt": 1712592000,
  "iat": 1710000000,
  "exp": 1712592000,
  "jti": "random-id"
}
```

明确不放入：
- `shopName`
- `shopId`
- 平台展示名称
- 店铺识别结果

### Local Storage Placement

不要把 clone meta 或 token 放进分身 App 的可见数据目录。目标平台 App 可能做反沙箱/反多开检测，扫描自身数据目录中的隐藏 JSON、token 文件或异常文件名。

最终采用：
```text
{BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/
  meta.json
  auth.token
```

`cloneInstanceId` 已经全局唯一，因此授权存储不再按 `serverUserId/packageName` 建多层目录；其它字段只作为 token/meta 校验项。

实现约束：
- APP 不直接写分身 App 数据目录。
- APP 通过 AIDL 把 meta/token 交给引擎。
- 引擎负责写入、原子替换、读取、扫描和删除授权记录。
- `data/user/{localVirtualUserId}/{packageName}/` 下默认不写 clone/token 相关文件，降低被目标 App 扫描识别的风险。

### Engine Validation

引擎打开分身前：
1. 根据全局唯一 `cloneInstanceId` 定位引擎系统目录中的 clone 授权记录
2. 读取 `meta.json`
3. 读取 `auth.token`
4. 用 `publicKeyId` 查找服务端公钥
5. 验证 token 签名
6. 校验 `typ == clone_auth`
7. 校验 `phone/serverUserId/cloneInstanceId/packageName/localVirtualUserId` 与 meta 和启动目标一致
8. 校验 `authStartAt <= now < authExpireAt`
9. 通过后启动分身

### Billing

新增扣费：
```text
CREATE:{cloneInstanceId}:{operationKey}
```

续期扣费：
```text
RENEW:{cloneInstanceId}:{operationKey}
```

用 `compute_deductions` 表保证幂等，不再通过店铺 ID 判断是否扣费。

## Risks

### Local long-lived token cannot be immediately revoked

删除店铺时 APP 必须请求引擎删除分身目录并清理引擎系统目录中的授权记录。管理员禁用或风控删除如果不触达设备，旧 token 可能在到期前仍可用。

缓解：
- token 默认有效期与服务期一致。
- 首页/列表同步时拉最新店铺状态。
- 后续可引入短期 launch token 做高安全模式。

### Device clock tampering

引擎本地验 `authExpireAt` 依赖设备时间。

缓解：
- Phase 9 先接受此风险。
- 后续可在 APP 每次联网同步时写入服务器时间偏移，或切到短期 launch token。

### Anti-sandbox detection risk

如果把 `.clone_meta.json` 或 `.clone_auth.token` 放在分身 App 数据目录，目标 App 可能通过扫描隐藏文件、JSON 文件名或 token 内容识别多开环境。

缓解：
- Phase 9 明确禁止在分身 App 可见目录写 clone/token 文件。
- 引擎系统目录作为唯一授权存储。
- 恢复映射时扫描引擎系统目录，不扫描分身 App 目录中的 clone 语义文件。

### Key rotation

公钥更换会导致旧 token 无法验证。

缓解：
- token header/meta 带 `publicKeyId`。
- 引擎保留多个公钥直到旧 token 全部过期。

## Verification Focus

- 篡改引擎系统目录中的 `auth.token` 后引擎拒绝打开
- token 过期后引擎拒绝打开
- token claims 与 meta 不匹配时引擎拒绝打开
- 新增和续期重复请求不会重复扣费
- 上报/手改店铺名称和店铺ID不会扣费
- 续期返回新 token，引擎覆盖系统目录 token 文件后可继续打开
- 分身 App 数据目录不存在 clone/token 语义文件
