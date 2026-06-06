# Phase 06: 用户登录 + 分身计费 - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning
**Source:** discuss-phase + eng-review

<domain>
## Phase Boundary

在主 APK 中实现强制登录和按分身应用实例计费的功能。计费单元为（登录用户 + 平台 + 店铺ID）三元组。

### 范围
- ✅ 强制登录（无游客模式）
- ✅ 登录 UI（手机号/微信）
- ✅ Token 管理与刷新
- ✅ Engine 会话注册（无感知授权）
- ✅ 店铺ID提取结果接收（Engine → 主 APK AIDL 回调）
- ✅ 扣点网关（启动分身前校验令牌）
- ✅ 令牌存储（加密 SharedPreferences）
- ✅ 新店铺扣点确认弹窗
- ✅ 令牌过期续费弹窗
- ✅ 服务器 API 设计（扣点、校验、查询）
- ✅ 设备信息收集（用于多端管控）

### 非范围
- ❌ Engine 侧任何计费逻辑（Engine 零业务逻辑）
- ❌ Engine 侧任何数据上报（Engine 零上报）
- ❌ 支付收银台集成（假设服务器处理支付，主 APK 只调接口）
- ❌ 复杂套餐体系（先实现单点计费，后续扩展）
- ❌ 店铺信息云端同步
</domain>

<decisions>
## Implementation Decisions

### 计费模型
- **按应用实例计费**：每个（用户ID + 平台 + 店铺ID）三元组是一个独立计费单元
- **扣点时机**：分身 App 登录后 Engine 提取到店铺ID → 主 APK 收到回调 → 上报服务器扣点
- **令牌结构**：{userId, platform, shopId, expireAt}
- **令牌存储**：EncryptedSharedPreferences，防本地篡改
- **校验策略**：本地快速校验（UX）+ 服务器最终校验（安全）

### 登录模型
- **强制登录**：无游客模式，未登录无法使用任何功能
- **登录方式**：手机号验证码 + 微信授权（Phase 6 先做手机号，微信 Phase 7）
- **Token 管理**：JWT Access Token + Refresh Token，自动刷新
- **会话同步**：登录后向 Engine 注册 sessionId（Engine 只存储 session 状态，不存储敏感信息）

### 店铺ID流转
- Engine 异步提取店铺ID（ShopIdManager）
- 提取完成后通过 `IEngineShopCallback.onShopExtracted()` 回调主 APK
- 主 APK 收到回调 → 检查令牌状态 → 新店铺则弹窗扣点

### 启动流程
```
用户点击启动分身
    → BillingGate.checkAndLaunch()
        → 检查登录状态（未登录 → 跳转登录页）
        → 检查本地令牌（有效 → 直接启动）
        → 无令牌 → 启动 Engine → 等待店铺ID回调
            → 收到店铺ID → 弹窗"确认扣点"
            → 用户确认 → 上报服务器 → 存储令牌 → 继续运行
```

### 安全边界
- 所有计费逻辑在主 APK，Engine 不感知
- 服务器记录所有扣点记录作为最终防线
- 本地令牌篡改会被服务器校验发现
- 多端登录：同一账号不同设备各自扣点（或按设备限制）
</decisions>

<canonical_refs>
## Canonical References

### 主 APK 侧
- `app/src/main/java/top/niunaijun/blackboxa/engine/EngineShopCallback.kt` — 接收 Engine 店铺ID回调（Phase 5）
- `app/src/main/java/top/niunaijun/blackboxa/engine/EngineProxy.kt` — Engine AIDL 调用封装（Phase 5）
- `Bcore/src/main/java/top/niunaijun/blackbox/entity/pm/ShopInfo.java` — 店铺信息数据类
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/ShopIdManager.java` — 店铺ID提取器

### 需要新增的模块
- `app/src/main/java/top/niunaijun/blackboxa/biz/auth/` — 登录模块
- `app/src/main/java/top/niunaijun/blackboxa/biz/billing/` — 计费模块
</canonical_refs>

<specifics>
## Specific Ideas

### 平台识别映射
| 包名关键词 | 平台标识 |
|-----------|---------|
| `jd` | `jd` |
| `taobao`, `tmall` | `taobao` |
| `meituan` | `meituan` |
| `pdd`, `duoduo` | `pdd` |
| 其他 | `unknown` |

### 服务器 API
```
POST /api/v1/auth/login      — 手机号登录
POST /api/v1/auth/refresh    — Token 刷新
GET  /api/v1/auth/profile    — 用户信息

POST /api/v1/billing/deduct  — 扣点
GET  /api/v1/billing/tokens  — 查询用户令牌列表
POST /api/v1/billing/verify  — 校验令牌有效性
```

### 令牌有效期
- 默认 30 天（由服务器配置）
- 到期前 3 天提示续费
- 过期后 7 天宽限期（只读访问，不可新操作）

### 弹窗设计
- 新店铺："检测到店铺 {shopName}，确认扣点以继续使用？"
- 过期续费："店铺 {shopName} 已过期，续费后可继续使用"
- 未登录："请先登录"
</specifics>

<deferred>
## Deferred Ideas

- 微信登录
- 支付宝登录
- 复杂套餐（月卡/年卡/不限量）
- 代理/分销体系
- 优惠券/折扣码
- 发票系统
- 多设备同步（同一账号多端共享令牌）
</deferred>

---

*Phase: 06-auth-billing*
*Context gathered: 2026-06-03 via discuss-phase + eng-review*
