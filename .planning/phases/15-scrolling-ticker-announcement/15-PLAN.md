# Phase 15 Plan: 首页滚动播报公告

## 目标

在 App 首页 header 下方新增全宽滚动播报条，由后台“滚动播报”公告类型维护。用户进入首页后看到最新已发布播报，短内容静态展示，长内容慢速横向滚动；无播报时不占位。

## Wave 1: 公告类型贯通

- 后台公告管理页新增“滚动播报”类型筛选、发布、编辑和列表标签展示。
- 服务端公告类型保持复用 `announcements.type`，新增 `SCROLLING_TICKER` 常量并统一类型大小写规范。
- Android ViewModel 新增滚动播报 LiveData 和加载方法，复用 `AnnouncementRepository.getPublishedAnnouncements(type)`。

## Wave 2: 首页展示

- `activity_home.xml` 删除 header 下方旧分割线。
- 在 header 与主体区之间插入 `tickerBanner`，宽度 `match_parent`，默认 `gone`。
- 播报条使用浅绿色背景、深绿色文字，内容为一行公告正文。
- 长文案启用从右到左慢速轮播；短文案静态展示。
- 用户触摸播报条时暂停，触摸结束后恢复；辅助功能触摸探索开启时不自动滚动。

## Wave 3: 验证

- Android 编译：`export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"; JAVA_HOME=$(/usr/libexec/java_home -v 21) ANDROID_HOME="$ANDROID_SDK_ROOT" ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/29.0.13846066" ./gradlew :app:compileDebugKotlin`
- 后台前端构建：`npm run build` in `admin/frontend`
- 服务端测试：优先运行公告相关测试；如没有专门测试，运行后端单测 smoke。

## 验证结果

- 2026-06-12：Android `:app:compileDebugKotlin` 通过。
- 2026-06-12：后台前端 `npm run build` 通过。
- 2026-06-12：后端 `mvn -Dtest=AnnouncementControllerTest test` 通过。

## 验收标准

- 后台能创建、编辑、查询类型为“滚动播报”的公告。
- App 首页有已发布滚动播报时，header 下方出现全宽浅绿色播报条。
- 搜索框、平台列表和店铺列表整体下移，布局不重叠。
- 没有已发布滚动播报时，首页不显示空白占位。
- Header 与主体之间没有额外 1px 分割线。
- 编译和构建通过。
