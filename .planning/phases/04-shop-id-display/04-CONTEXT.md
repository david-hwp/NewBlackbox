# Phase 04: 店铺ID自动获取与展示 - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning
**Source:** discuss-phase

<domain>
## Phase Boundary

在 BlackBox 应用列表中，自动识别并展示已登录分身应用的店铺ID。
当用户安装并登录京东秒送商家、淘宝闪购商家、美团外卖商家等应用后，
BlackBox 应在应用名称后显示对应的店铺ID（如 `京东秒送商家-16364870`）。

### 范围
- ✅ 通用 `ShopIdExtractor` 接口设计
- ✅ 京东秒送商家 (`com.jd.mrd.jingming`) 店铺ID提取实现
- ✅ 应用列表 UI 展示改造
- ✅ 店铺信息持久化存储
- ✅ 自动触发机制（应用启动/Resume 时检测）

### 非范围（后续扩展）
- ❌ 淘宝闪购商家版 extractor（预留接口，本次不实现）
- ❌ 美团外卖商家版 extractor（预留接口，本次不实现）
- ❌ 店铺信息的网络同步/云端备份
- ❌ 多店铺切换支持
</domain>

<decisions>
## Implementation Decisions

### 架构设计
- **通用接口优先**：`ShopIdExtractor` 接口，`extract(context)` → `ShopInfo`
- **注册表模式**：`ShopIdExtractorRegistry`，按包名路由到对应 extractor
- **异步非阻塞**：店铺ID抓取在后台线程执行，不阻塞 UI

### 京东秒送商家实现策略
- **Primary**: 读取 SharedPreferences `JingmingAndroidClient.xml` 中的 `ge_tui_push_alias_bind_flag`
- **Fallback**: WebView DevTools Protocol 获取当前页面 URL，提取 `storeId` / `venderId` 参数
- **Last resort**: Logcat 抓取网络请求日志中的店铺相关字段

### 数据模型
- `AppInfo` 新增：`shopId` (String), `shopName` (String), `platform` (String)
- 存储：`BPackageManagerService` 内部数据库（SQLite）
- 展示格式：`{appName}-{shopId}`，无 shopId 时显示原名称

### 触发时机
- 应用首次启动后（`BActivityThread` 绑定完成）
- 应用从后台恢复到前台（`onResume` 时检测）
- 手动刷新（用户下拉应用列表）

### UI 展示
- 应用列表卡片：名称行显示 `京东秒送商家-16364870`
- 无 shopId：保持原样 `京东秒送商家`
- 加载中：显示 `京东秒送商家 (...)`

### 扩展预留
- 新增平台只需：1) 实现 `ShopIdExtractor` 接口 2) 注册到 Registry
- 不修改现有代码即可支持新平台
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### 项目结构
- `Bcore/src/main/java/top/niunaijun/blackbox/core/system/pm/BPackageManagerService.java` — 包管理服务，需扩展数据模型
- `Bcore/src/main/java/top/niunaijun/blackbox/entity/pm/AppInfo.java` — 应用信息实体
- `app/src/main/java/top/niunaijun/blackboxa/data/AppsRepository.kt` — 应用列表数据层
- `app/src/main/java/top/niunaijun/blackboxa/view/list/` — 应用列表 UI

### 已知技术路径
- `run-as top.niunaijun.blackbox` 可访问分身应用数据目录
- 京东数据路径：`blackbox/data/user/0/com.jd.mrd.jingming/shared_prefs/`
- 关键文件：`JingmingAndroidClient.xml` (storeId), WebView DevTools (URL 参数)
- 已验证：WebView DevTools 可获取 `https://miaosongim-pro.pf.jd.com/...?storeId=16364870`
</canonical_refs>

<specifics>
## Specific Ideas

### 京东店铺ID已知来源
- **SharedPreferences**: `ge_tui_push_alias_bind_flag` = `16364870,true`
- **WebView URL**: `storeId=16364870`, `venderId=509905`, `stationId=16364870`
- **店铺名称**: WebView URL 中的 `waiterPin` 参数（URL 编码）

### 多平台预留包名
| 平台 | 包名 | 状态 |
|------|------|------|
| 京东秒送商家 | `com.jd.mrd.jingming` | 本次实现 |
| 淘宝闪购商家 | `com.taobao.flashbuy.merchant` (预留) | 预留接口 |
| 美团外卖商家 | `com.sankuai.meituan.merchant` (预留) | 预留接口 |
</specifics>

<deferred>
## Deferred Ideas

- 淘宝闪购商家版 extractor 实现
- 美团外卖商家版 extractor 实现
- 店铺信息云端同步
- 多店铺/多门店切换支持
- 店铺ID手动编辑/修正功能
</deferred>

---

*Phase: 04-shop-id-display*
*Context gathered: 2026-06-03 via discuss-phase*
