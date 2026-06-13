# Phase 15 Context: 首页滚动播报公告

## 用户确认

- 首页 header 下方、搜索店铺输入框上方新增滚动播报条。
- 播报条宽度直接跟 header 保持一致，占满整个屏幕宽度。
- 店铺内容和平台列表整体顺势下移。
- 不要额外分割线，滚动播报条本身就是 header 与主体内容的分隔。
- 公告后台新增“滚动播报”公告类型；管理员操作和 App 加载逻辑跟普通公告一致。
- 显示方式只有一行文字；手机宽度放不下全部文字时，从右往左慢速轮播剩余内容。

## 现有链路

- 后台公告管理页：`admin/frontend/src/views/Announcements.vue`
- 服务端公告接口：`admin/backend/src/main/java/com/duodian/admin/controller/AnnouncementController.java`
- 公告实体：`admin/backend/src/main/java/com/duodian/admin/entity/Announcement.java`
- Android 公告拉取：`HomeViewModel.loadLatestAnnouncement()` 和 `AnnouncementRepository.getPublishedAnnouncements(type)`
- Android 首页布局：`app/src/main/res/layout/activity_home.xml`
- Android 首页观察和展示：`app/src/main/java/com/zhirang/zhanghaoguanjia/view/home/HomeActivity.kt`

## 约束

- 不新增独立公告接口；继续复用 `/announcements?published=true&type=...`。
- 不让引擎参与播报展示。
- 不打断首页首屏操作，不弹窗展示滚动播报。
- 如果服务端没有已发布滚动播报，首页不占位。
- Header 下方旧 1px 分割线需要移除。
- Android 构建使用 Java 21。
