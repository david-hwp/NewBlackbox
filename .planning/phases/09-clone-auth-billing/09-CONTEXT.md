# Phase 09: Clone 授权计费与长期令牌 - Context

**Gathered:** 2026-06-07
**Status:** Completed (2026-06-08)
**Source:** side-conversation design discussion
**Release Closeout:** `1.2.3-release`

<completion_notes>
## Completion Notes

- Phase 09 is complete on `future-phase9` after the `1.2.3-release` closeout.
- The final implementation keeps clone identity and billing tied to `cloneInstanceId`; shop name and shop ID remain display/update fields only.
- The APP writes clone meta and authorization token through the engine AIDL path, and the engine stores them under its own `clone-auth/{cloneInstanceId}/` system directory.
- Store repair is local-device-only: it resets the local clone environment and does not upload server mutations that would affect another device.
- Final release verification included release APK build success, server version/announcement publication, and Xiaomi real-device smoke testing for the repair swipe dialog and engine responsiveness regression.
</completion_notes>

<domain>
## Phase Boundary

本阶段实现新的店铺分身授权与计费模型：所有新增扣费、续期扣费、打开授权都以服务端签发的 `cloneInstanceId` 为准；店铺名称和店铺ID只作为展示信息，可由引擎识别上报，也可由用户手工编辑。

### 范围
- 新增店铺时服务端签发可解析 `cloneInstanceId`
- 服务端保存不返回 APP 的 clone 校验随机码
- 服务端签发长期 `authorizationToken`
- 续期后返回新的 `authorizationToken`
- APP 通过 AIDL 将 clone meta 和 `authorizationToken` 交给引擎
- 引擎将 clone meta 和 `authorizationToken` 写入引擎系统目录，不写入分身 App 可见数据目录
- 引擎打开分身前只校验本地授权 token
- 新增和续期扣费均基于 `cloneInstanceId`
- 店铺信息上报和手工编辑不触发扣费
- 删除店铺时删除对应分身目录，并清理引擎系统目录中的授权记录

### 非范围
- 不依赖 Android Keystore
- 不让引擎联网校验
- 不把服务端私钥、clone 校验随机码或 HMAC secret 下发到 APP/引擎
- 不把店铺名称、店铺ID放入 `authorizationToken`
- 不做复杂套餐、支付收银台、退款风控实时吊销
</domain>

<decisions>
## Locked Decisions

### Clone ID
- `cloneInstanceId` 由服务端生成，APP 不生成最终值。
- 格式必须可解析：
  ```text
  CLN1-{phone}-{packageName}-N{cloneSequence}-U{localVirtualUserId}-R{randomDigest}
  ```
- `phone` 使用完整手机号。
- `cloneSequence` 表示同一用户同一平台应用下第几个分身。
- `localVirtualUserId` 表示本机虚拟 User 目录号。
- `randomDigest` 是服务端随机码摘要，随机码原文只保存在服务器。

### 授权令牌
- `authorizationToken` 是服务端私钥签名的长期授权令牌。
- 建议算法为 `RS256` / `SHA256withRSA`，避免 Android 版本差异引入兼容性问题。
- token claims 包含：
  - `typ = clone_auth`
  - `serverUserId`
  - `phone`
  - `cloneInstanceId`
  - `packageName`
  - `localVirtualUserId`
  - `credentialVersion`
  - `authStartAt`
  - `authExpireAt`
  - `iat`
  - `exp`
  - `jti`
- token 不包含：
  - `shopName`
  - `shopId`
  - `platformName`
  - 任何店铺识别详情

### 本地授权存储
- clone meta 独立保存非敏感元信息。
- `authorizationToken` 独立保存。
- `cloneInstanceId` 是全局唯一值，授权目录只按 `cloneInstanceId` 建一级，不再按 `serverUserId` 或 `packageName` 分层。
- 两者必须保存在引擎系统目录，例如：
  ```text
  {BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/
    meta.json
    auth.token
  ```
- 不允许把 clone meta 或 `authorizationToken` 写入分身 App 可见目录，例如 `data/user/{localVirtualUserId}/{packageName}/`。
- 续期只需原子覆盖引擎系统目录中的 `auth.token`。
- 删除店铺时必须同时删除分身目录和引擎系统目录中的授权记录。

### 引擎职责
- 引擎只负责本地验签、claim 匹配、有效期判断和启动放行。
- 引擎不保存私钥。
- 引擎不依赖 Android Keystore。
- 引擎不做联网请求。

### 扣费职责
- 新增扣费只看 `cloneInstanceId`。
- 续期扣费只看 `cloneInstanceId`。
- 店铺信息识别上报只更新展示字段。
- 手工修改店铺名称/店铺ID只更新展示字段。
</decisions>

<canonical_refs>
## Canonical References

### 后端
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopController.java`
- `admin/backend/src/main/java/com/duodian/admin/controller/ShopReportController.java`
- `admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java`
- `admin/backend/src/main/java/com/duodian/admin/entity/Shop.java`
- `admin/backend/src/main/resources/schema.sql`

### APK
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeViewModel.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/data/ShopRepository.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/network/ApiService.kt`
- `app/src/main/java/com/zhirang/zhanghaoguanjia/bean/dto/ShopDto.kt`

### 引擎
- `engine-aidl/src/main/aidl/top/niunaijun/blackbox/engine/IBlackBoxEngine.aidl`
- `Bcore/src/main/java/top/niunaijun/blackbox/engine/BlackBoxEngineService.kt`
- `Bcore/src/main/java/top/niunaijun/blackbox/core/env/BEnvironment.java`
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/installer/CreateUserExecutor.java`
</canonical_refs>

<specifics>
## Specific Requirements

### 服务端 create 响应
新增店铺扣费成功后必须返回：
```json
{
  "shop": {
    "id": 456,
    "shopName": "User[7]-未知",
    "shopId": "NEW-...",
    "cloneInstanceId": "CLN1-..."
  },
  "authorizationToken": "eyJ...",
  "authStartAt": "...",
  "authExpireAt": "...",
  "balance": 8
}
```

### 引擎系统目录授权文件
```text
{BEnvironment.getSystemDir()}/clone-auth/{cloneInstanceId}/
  meta.json
  auth.token
```

`meta.json`：
```json
{
  "version": 1,
  "cloneInstanceId": "CLN1-...",
  "packageName": "com.jd.mrd.jingming",
  "serverUserId": 123,
  "phone": "<user_phone>",
  "localVirtualUserId": 7,
  "publicKeyId": "rsa_2026_01"
}
```

### 分身 App 目录约束
- `data/user/{localVirtualUserId}/{packageName}/` 下不得写入 `.clone_meta.json`、`.clone_auth.token` 或其他包含 clone/token 语义的文件。
- 引擎如需重建映射，优先扫描引擎系统目录中的 clone-auth 记录。
- 只有在明确验证不会被目标 App 访问或扫描的情况下，才允许在分身目录写入不可识别、不可解析的最小哨兵文件；Phase 9 默认不写。

### 续期
- 续期成功后服务端返回新的 `authorizationToken`。
- APP 通过 AIDL 把新 token 交给引擎。
- 引擎原子覆盖系统目录中的 `auth.token`。
- 引擎下一次打开时直接使用新 token 判断授权有效期。
</specifics>

<deferred>
## Deferred Ideas

- 实时吊销：如果未来要做到删除/禁用实时生效，可增加短期启动令牌或引擎联网校验。
- 密钥轮换自动拉取：Phase 9 先支持 `publicKeyId` 和内置公钥映射，后续可做公钥配置接口。
- 多设备登录态同步：本阶段只保证 clone 身份可重建，本地 App 登录态不做备份同步。
</deferred>
