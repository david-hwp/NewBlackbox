# 美团外卖商家版差评数据引擎预研记录

记录日期：2026-06-10

目标：在账号管家引擎成功打开美团外卖商家版分身店铺后，通过引擎能力获取“粉面先生（龙华店）”的差评数据，并把结果写入引擎自己的目录，最后通过 ADB 只拉取引擎生成的结果文件。

约束：

- ADB 只用于定位 UI、观察前台进程、安装新版 engine、启动诊断入口、拉取 engine 自己写出的结果文件。
- 不通过 ADB 直接读取美团原包或 engine blackbox 内的美团私有数据目录。
- 不导出 token/cookie/session 等登录凭据；文档与结果文件都应脱敏。
- 不点击会修改商家状态的按钮，只使用评价页的只读筛选。

## 1. 运行上下文结论

设备：

- OPPO 真机序列号：`55J7JJWKTWKNHYZL`
- 型号：`PECM30`
- Android：12 / SDK 31

当前仓库分支：

- `dev`

相关包：

- engine 包名：`com.zhirang.zhanghaoguanjia.engine`
- 主 app 包名：`com.zhirang.zhanghaoguanjia`
- 美团外卖商家版系统包名：`com.sankuai.meituan.meituanwaimaibusiness`
- 美团外卖商家版版本：`7.37.0.4`，versionCode `700370004`

打开账号管家中的卡片“粉面先生（龙华店）”后，系统窗口与进程显示：

```text
mCurrentFocus = com.sankuai.meituan.meituanwaimaibusiness/com.sankuai.wme.flutter.FlutterBaseContainerActivity
mFocusedApp   = com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.proxy.ProxyActivity$P0
```

进程侧可以看到美团业务进程跑在 engine 的 UID 下，例如：

```text
u0_a252 ... com.zhirang.zhanghaoguanjia.engine
u0_a252 ... com.zhirang.zhanghaoguanjia.engine:black
u0_a252 ... com.sankuai.meituan.meituanwaimaibusiness
u0_a252 ... com.sankuai.meituan.meituanwaimaibusiness:dppushservice
```

结论：

- 分身美团不是系统原包独立运行，而是在 engine/BlackBox 的代理 Activity 与虚拟进程体系里运行。
- engine 能访问它自己私有目录下的 blackbox 虚拟数据树。
- 当前目标分身虚拟用户是 `userId=0`。
- 美团业务数据在 engine 进程可读的虚拟目录下：

```text
/data/user/0/com.zhirang.zhanghaoguanjia.engine/blackbox/data/user/0/com.sankuai.meituan.meituanwaimaibusiness
```

这个目录不能由 ADB 直接读取作为结果来源；只能由 engine 内部逻辑读取/解析。

## 2. ADB 定位到的评价页与接口

入口路径：

1. 在账号管家首页选择平台“美团外卖商家版”。
2. 打开店铺卡片“粉面先生（龙华店）”。
3. 进入美团商家版评价页后，页面顶部可见筛选项：

```text
全部
好评(含4-5星打分项)
中评(含3星打分项)
差评(含1-2星打分项)
```

4. 选择“差评(含1-2星打分项)”后，美团分身 app 发起评论列表请求。

定位到的核心接口：

```text
GET https://waimaieapp.meituan.com/gw/customer/comment/list?ignoreSetRouterProxy=true
```

差评筛选的关键参数：

```text
periodType=1
hasContent=-1
commType=-1
commScore=3
pageSize=10
beginTime=1778256000
endTime=1780934400
source=1
onlyAuditNotPass=0
pageNum=1
```

其中：

- `commScore=1`：实际观察对应好评。
- `commScore=3`：实际观察对应差评，即页面上的“差评(含1-2星打分项)”。
- `wmPoiId=15397100`：当前店铺 ID。
- `acctId=<REDACTED>`：账号 ID，结果/文档中不需要输出。
- `token=<REDACTED>`：登录态凭据，禁止写入文档、日志和结果文件。

请求命中后，美团 Flutter/KNB 层会把评论列表写入本地 storage key：

```text
user_comment_list_data_key
```

logcat 中观察到的写入形式：

```text
mt_flutter_knb setStorage
key   = user_comment_list_data_key
value = {"total":11,"pageSize":10,"pageNum":0,"list":[...]}
```

差评数据结构中可用字段：

```text
id
wmPoiId
userName
cleanComment / comment
commentType
orderCommentScore
productScore
tasteScore
packagingScore
deliveryCommentScore
poiName
createTime
orderDetails
criticFoodList
wmCommentReportInfo
hasAppeal
pictureUrls
```

识别差评的规则：

```text
orderCommentScore in 1..2
OR productScore in 1..2
OR tasteScore in 1..2
```

## 3. 推荐实现：复用分身 app 自己的请求

不要优先在 engine 进程里裸发 HTTP 请求。

原因：

- 美团请求 URL 包含 token、csec、设备参数、城市、版本、ABI 等大量上下文。
- 裸 `HttpURLConnection` 即使带上 URL，也可能缺少美团网络栈自动补齐的签名、风控、header 或 Cookie。
- 更稳定的方式是让已经登录的分身美团 app 自己发请求，然后 engine 只做只读拦截/解析/落盘。

建议的数据流：

```text
账号管家打开店铺分身
  -> engine ProxyActivity 启动美团商家版
  -> 美团 app 进入评价页并选择差评
  -> 美团 app 自己请求 /gw/customer/comment/list?commScore=3
  -> 美团 app 将响应写入 user_comment_list_data_key
  -> engine 探针读取虚拟数据目录中的 storage/cache/SQLite
  -> engine 解析 list 内 1-2 星评论
  -> engine 脱敏后写入自己的外部文件目录
  -> ADB 只 pull engine 生成的 JSON 结果文件
```

当前预研实现文件：

```text
Bcore/src/main/java/top/niunaijun/blackbox/engine/EngineReviewProbeActivity.kt
```

当前验证版 manifest 入口：

```xml
<activity
    android:name=".engine.EngineReviewProbeActivity"
    android:enabled="true"
    android:excludeFromRecents="true"
    android:exported="true"
    android:theme="@style/BTheme" />
```

说明：

- 原计划是默认 disabled，测试时通过 ADB `pm enable` 临时启用。
- OPPO 真机上 `shell pm enable` 被系统拒绝：

```text
SecurityException: Shell cannot change component state for com.zhirang.zhanghaoguanjia.engine/...EngineReviewProbeActivity
```

- 因此本次验证包改为 `android:enabled="true"`，直接启动诊断 Activity。
- 这只适合技术验证；生产化不能长期保留 exported 的诊断 Activity。

探针当前行为：

1. 调用 `BEnvironment.getDataDir(packageName, userId)` 找到分身美团虚拟数据目录。
2. 扫描非敏感候选文件和 SQLite：
   - 优先找 `user_comment_list_data_key`
   - 找 `/gw/customer/comment/list`
   - 找 `commScore=3`
   - 找 `orderCommentScore`、`cleanComment`、`评论/评价/差评`
3. 尝试解析 JSON payload：
   - 直接 JSON
   - 被转义的 JSON 字符串
   - SQLite 单元格中的 JSON
4. 从 `list` 中筛出当前店铺 `wmPoiId=15397100` 的 1-2 星评论。
5. 输出字段做脱敏：
   - 不输出 token/cookie/session/ticket/auth/password/secret/csrf/uuid/device 等字段。
   - 手机号替换为 `[PHONE]`。
   - 长随机字符串替换为 `[REDACTED]`。
   - 图片只输出 `pictureCount`，不输出图片 URL。
6. 写入 engine 外部文件目录。

当前探针也保留了一个 direct fetch 尝试：

```text
method = engine_direct_fetch_then_virtual_cache_parse
```

含义：

- 如果在虚拟数据里找到最近的完整评论接口 URL，会尝试由 engine 进程直接请求一次。
- 如果 direct fetch 失败或被风控拦截，继续 fallback 到分身 app 已经写入的本地缓存。
- 预期生产方案应以“分身 app 自己请求 + engine 解析结果”为主，不依赖 engine 裸请求成功。

## 4. 结果文件目录与 ADB 拉取方式

engine 内部写结果的代码路径：

```kotlin
val outDir = getExternalFilesDir("review-probe")
val outFile = File(outDir, "meituan_negative_reviews_$stamp.json")
```

设备上的可拉取目录：

```text
/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/review-probe/
```

结果文件名示例：

```text
meituan_negative_reviews_20260610_131500.json
```

ADB 只拉取这个 engine 生成的文件：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL pull \
  /sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/review-probe/ \
  ./review-probe-results/
```

结果 JSON 顶层结构：

```json
{
  "ok": true,
  "method": "engine_direct_fetch_then_virtual_cache_parse",
  "packageName": "com.sankuai.meituan.meituanwaimaibusiness",
  "userId": 0,
  "shopName": "粉面先生（龙华店）",
  "wmPoiId": 15397100,
  "target": {
    "endpoint": "/gw/customer/comment/list",
    "storageKey": "user_comment_list_data_key",
    "expectedScoreFilter": "commScore=3 / 1-2 star reviews"
  },
  "directFetch": {
    "enabled": true,
    "attempted": true,
    "success": false
  },
  "stats": {
    "scannedFiles": 0,
    "scannedSqliteDatabases": 0,
    "skippedFiles": 0,
    "cachedCommentUrlsFound": 0,
    "reviewCount": 0
  },
  "sourceCandidates": [],
  "parseNotes": [],
  "reviews": []
}
```

每条评论输出字段：

```json
{
  "source": "cache-or-sqlite-source",
  "commentId": 0,
  "wmPoiId": 15397100,
  "poiName": "粉面先生...",
  "userName": "匿名用户",
  "createTime": "2026-06-09",
  "comment": "脱敏后的评价内容",
  "scores": {
    "orderCommentScore": 1,
    "productScore": 1,
    "tasteScore": 1,
    "packagingScore": 3,
    "deliveryCommentScore": 5
  },
  "criticFoodList": [],
  "orderDetails": [],
  "pictureCount": 1,
  "hasAppeal": false,
  "reportStatus": "申诉"
}
```

## 5. 构建、安装、启动探针

构建新版 engine：

```bash
JAVA_HOME=/Users/heweiping/Library/Java/JavaVirtualMachines/azul-21.0.10/Contents/Home \
./gradlew :Bcore:assembleRelease \
  -PENGINE_VERSION_CODE=50017 \
  -PENGINE_VERSION_NAME=1.2.12-reviewprobe \
  --no-daemon
```

产物：

```text
Bcore/build/outputs/apk/release/FxEngine_1.2.12-reviewprobe_release.apk
```

当前构建 SHA-256：

```text
76723a6f42443cea2367d53c3014eb008cbfb67f8d09c58ec32b95814cad92b6
```

安装新版 engine，不卸载旧包：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL install -r \
  Bcore/build/outputs/apk/release/FxEngine_1.2.12-reviewprobe_release.apk
```

临时启用诊断 Activity：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL shell pm enable \
  com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineReviewProbeActivity
```

启动探针：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL shell am start \
  -n com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineReviewProbeActivity \
  --es packageName com.sankuai.meituan.meituanwaimaibusiness \
  --ei userId 0 \
  --es shopName '粉面先生（龙华店）' \
  --el wmPoiId 15397100 \
  --ei limit 20 \
  --ez directFetch true
```

拉取结果：

```bash
mkdir -p review-probe-results
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL pull \
  /sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/review-probe/ \
  review-probe-results/
```

测试完禁用诊断入口：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL shell pm disable \
  com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineReviewProbeActivity
```

OPPO 实测注意：

- OPPO 不允许 shell 修改该组件启停状态，所以上面的 `pm enable/disable` 在这台机器上不可用。
- 实测使用 debug 包快速验证：

```bash
JAVA_HOME=/Users/heweiping/Library/Java/JavaVirtualMachines/azul-21.0.10/Contents/Home \
./gradlew :Bcore:assembleDebug \
  -PENGINE_VERSION_CODE=50019 \
  -PENGINE_VERSION_NAME=1.2.12-reviewprobe-debug \
  --no-daemon
```

debug 产物：

```text
Bcore/build/outputs/apk/debug/FxEngine_1.2.12-reviewprobe-debug_debug.apk
```

debug 产物 SHA-256：

```text
68535ccd711173e26f0554b9bc38dedc1136239af3e7057f35f993e0ece671db
```

安装：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL install -r \
  Bcore/build/outputs/apk/debug/FxEngine_1.2.12-reviewprobe-debug_debug.apk
```

设备确认版本：

```text
versionCode=50019
versionName=1.2.12-reviewprobe-debug
lastUpdateTime=2026-06-10 13:23:54
```

实测启动探针：

```bash
/Users/heweiping/Library/Android/sdk/platform-tools/adb -s 55J7JJWKTWKNHYZL shell am start \
  -n com.zhirang.zhanghaoguanjia.engine/top.niunaijun.blackbox.engine.EngineReviewProbeActivity \
  --es packageName com.sankuai.meituan.meituanwaimaibusiness \
  --ei userId 0 \
  --es shopName '粉面先生（龙华店）' \
  --el wmPoiId 15397100 \
  --ei limit 20 \
  --ez directFetch true
```

实测 engine 写出的设备文件：

```text
/sdcard/Android/data/com.zhirang.zhanghaoguanjia.engine/files/review-probe/meituan_negative_reviews_20260610_132529.json
```

已拉回并放入本项目：

```text
docs/meituan_negative_reviews_20260610_132529.json
```

实测结果摘要：

```json
{
  "ok": true,
  "generatedAt": "2026-06-10 13:25:29",
  "durationMs": 16671,
  "method": "engine_direct_fetch_then_virtual_cache_parse",
  "packageName": "com.sankuai.meituan.meituanwaimaibusiness",
  "userId": 0,
  "shopName": "粉面先生（龙华店）",
  "wmPoiId": 15397100,
  "directFetch": {
    "enabled": true,
    "attempted": false,
    "reason": "No cached comment-list URL with auth parameters was found in virtual app data."
  },
  "stats": {
    "scannedFiles": 2849,
    "scannedSqliteDatabases": 19,
    "skippedFiles": 276,
    "cachedCommentUrlsFound": 0,
    "reviewCount": 10
  },
  "sources": [
    "files/cips/common/jsbridge_storage/kv"
  ]
}
```

结论：

- 已验证：新版 engine 可以在 OPPO 真机上读取分身美团已登录上下文产生的评价缓存，并把差评结果写到 engine 自己的外部目录。
- 已验证：ADB 只拉取 engine 生成的结果文件，没有直接读取 blackbox 内的美团私有数据目录。
- 本次 direct fetch 没有执行，因为虚拟数据中没有找到带完整鉴权参数的缓存 URL；实际成功路径是解析分身 app 自己写入的 KNB storage：`files/cips/common/jsbridge_storage/kv`。

## 6. 后续生产化建议

当前实现是诊断探针，不建议作为长期暴露的 Activity 直接上线。

生产化建议：

1. 不使用 exported 诊断 Activity。
2. 将“获取差评数据”做成 engine AIDL 能力，由主 app 通过签名权限调用。
3. 触发方式应是：
   - 主 app 选择店铺卡片。
   - engine 打开分身美团。
   - engine 或主 app 通过无障碍/页面路由进入评价页并选择差评。
   - 分身 app 自己完成请求。
   - engine 读取并解析 `user_comment_list_data_key` 或 hook 到 KNB storage 写入点。
4. 如果要更实时和稳定，优先 hook 分身进程里的以下点，而不是 engine 裸发 HTTP：
   - Flutter KNB `setStorage("user_comment_list_data_key", value)`
   - 美团 `mt_network` 请求完成回调
   - WebView/Flutter bridge 中评论页的数据落地函数
5. 结果文件只保留业务必要字段，不保留登录态、设备指纹、完整请求 URL、Cookie、token。
