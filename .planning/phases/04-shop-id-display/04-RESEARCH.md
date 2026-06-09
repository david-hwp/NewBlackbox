# Phase 04 Research: 店铺ID自动获取与展示

**Date:** 2026-06-03
**Phase:** 04 - 店铺ID自动获取与展示
**Researcher:** Claude (based on prior investigation)

---

## 1. 技术路径验证

### 1.1 京东秒送商家店铺ID来源（已验证）

| 来源 | 方法 | 可靠性 | 示例值 | 备注 |
|------|------|--------|--------|------|
| **SharedPreferences (storeId)** | 直接读取 `JingmingAndroidClient.xml` 中的 `storeId` 和 `storeName` | ⭐⭐⭐⭐⭐ 极高 | `storeId=14395758`, `storeName=罗家臭豆腐(东瓜山店)` | **当前主要策略**，登录后稳定写入，直接读取无依赖 |
| **SharedPreferences (ge_tui)** | 读取 `ge_tui_push_alias_bind_flag` = `shopId,true` | ⭐⭐⭐ 高 | `ge_tui_push_alias_bind_flag=14395758,true` | **Fallback**，同文件不同 key，兼容新旧版本 |
| **WebView DevTools** | `adb forward tcp:9224 localabstract:webview_devtools_remote_*` | ⭐⭐ 中 | URL 参数 `storeId=14395758&venderId=509905` | 需要应用在前台且 WebView 页面打开，不稳定 |
| **Logcat** | 抓取应用日志中的网络请求 | ⭐⭐ 中 | 需要过滤大量噪音 | 可作为兜底 |

**⚠️ 重要发现**: 分身版京东秒送商家（9.57.1）登录后，`JingmingAndroidClient.xml` 中稳定存在以下字段：
- `<string name="storeId">14395758</string>` — 店铺ID
- `<string name="storeName">罗家臭豆腐(东瓜山店)</string>` — 店铺名称
- `<string name="ge_tui_push_alias_bind_flag">14395758,true</string>` — 个推别名（同时存在）

**推荐策略**: Primary = 直接读取 `storeId` + `storeName`（最稳定），Fallback = `ge_tui_push_alias_bind_flag`，Last resort = WebView DevTools

### 1.2 BlackBox 数据访问机制

```
/data/data/top.niunaijun.blackbox/blackbox/data/user/0/{package}/
```

- 需要 `run-as top.niunaijun.blackbox` 权限（BlackBox 是 debuggable）
- 分身应用本身不是 debuggable，不能直接 `run-as com.jd.mrd.jingming`
- 文件权限：`u0_a106` 用户组，其他应用无读权限

### 1.3 WebView DevTools Protocol 使用

```bash
adb forward tcp:9224 localabstract:webview_devtools_remote_{pid}
curl http://localhost:9224/json/list
```

返回页面列表，包含 `url` 字段。京东商家版 WebView URL 示例：
```
https://miaosongim-pro.pf.jd.com/pages/IM/chat/?...
&storeId=16364870&venderId=509905&stationId=16364870
```

---

## 2. 关键代码位置

### 2.1 应用信息数据层

| 文件 | 作用 |
|------|------|
| `Bcore/.../entity/pm/AppInfo.java` | 应用信息实体，需扩展 `shopId`/`shopName` |
| `Bcore/.../core/system/pm/BPackageManagerService.java` | 包管理服务，数据库操作 |
| `Bcore/.../core/system/pm/BPackageSettings.java` | 包设置存储 |
| `Bcore/.../app/BActivityThread.java` | 应用生命周期，触发提取时机 |

### 2.2 UI 层

| 文件 | 作用 |
|------|------|
| `app/.../data/AppsRepository.kt` | 应用列表数据仓库 |
| `app/.../view/list/AppsAdapter.kt` (或类似) | 应用列表 Adapter |
| `app/.../view/main/MainActivity.kt` | 主页面 |

### 2.3 已知数据库结构

`BPackageManagerService` 使用内部 SQLite 数据库存储应用元数据。需要确认：
- 当前 `AppInfo` 表结构
- 如何安全地添加新字段（数据库迁移）

---

## 3. 架构方案

### 3.1 ShopIdExtractor 接口

```java
public interface ShopIdExtractor {
    /** 返回该 Extractor 支持的分身应用包名 */
    String getTargetPackage();

    /** 尝试提取店铺信息 */
    ShopInfo extract(Context context, String userId);

    /** 返回提取策略的优先级（数值越小优先级越高） */
    int getPriority();
}

public class ShopInfo {
    public String shopId;      // 店铺ID（如 16364870）
    public String shopName;    // 店铺名称（如 罗家臭豆腐_长沙一觉）
    public String platform;    // 平台标识（如 jd, taobao, meituan）
    public long extractedAt;   // 提取时间戳
}
```

### 3.2 注册表

```java
public class ShopIdExtractorRegistry {
    private static final Map<String, ShopIdExtractor> EXTRACTORS = new HashMap<>();

    static {
        register(new JDShopIdExtractor());  // 京东
        // register(new TaobaoShopIdExtractor());  // 预留
        // register(new MeituanShopIdExtractor()); // 预留
    }

    public static ShopIdExtractor getExtractor(String packageName) {
        return EXTRACTORS.get(packageName);
    }
}
```

### 3.3 触发时机

1. **应用启动完成** — `BActivityThread.bindApplication()` 后异步执行
2. **Activity Resume** — 监听 `onResume`，间隔 >= 30s 才重新触发
3. **手动刷新** — 用户下拉应用列表时

---

## 4. 风险与限制

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 京东更新 APK 后数据存储方式变化 | 提取失败 | 多层 fallback（storeId → ge_tui alias → WebView DevTools） |
| SharedPreferences 文件不存在 | 无法读取 | 延迟到应用启动后再触发提取，确保文件已生成 |
| 提取操作耗时 | UI 卡顿 | 异步线程（HandlerThread），无网络/复杂解析操作，通常 < 100ms |
| 风控检测（simulatorSwitch） | 账号异常 | 不修改应用数据，只读取 XML 文件 |
| 多用户场景 | 数据混乱 | 按 userId 隔离存储（BEnvironment.getDataDir） |
| 应用名称显示截断 | 显示不完整 | 当前 RV 布局限制，可后续优化为两行显示或滚动 |

---

## 5. 验证策略

### 5.1 单元测试
- `JDShopIdExtractorTest` — 模拟 SharedPreferences XML 解析
- `ShopIdExtractorRegistryTest` — 注册表路由正确性

### 5.2 E2E 测试
- 安装京东秒送商家 → 登录 → 检查应用列表显示 `京东秒送商家-16364870`
- 重启 BlackBox → 确认店铺ID持久化
- 清除京东数据 → 确认店铺ID消失或显示为加载中

### 5.3 性能测试
- 100 个应用列表滚动流畅度
- 提取操作耗时 < 500ms（SharedPreferences 路径）

---

## RESEARCH COMPLETE
