---
phase: 11-channel-promotion-system
status: planned
created_at: "2026-06-09T15:30:00+08:00"
source: user-request
branch: dev
---

# Phase 11: 渠道推广完整体系 - Context

## User Request

当前后台已经能通过 `apkChannel` 获取用户注册渠道。下一版本需要规划一套完整渠道能力：

- 不同渠道有自己的 APK 发布、用户注册、算力扣点体系。
- 总算力由超级管理员管理。
- 每个渠道有自己的渠道管理员。
- 超级管理员给渠道管理员/渠道分配总算力，渠道管理员再给该渠道用户分配。
- 渠道管理员登录后台后只能看到自己渠道下的用户、交易，并能发布自己渠道公告。
- 超级管理员可以设置不同渠道 APK 的图标、名称、渠道标识。
- APK 仍由超级管理员管理和上传到不同渠道。
- 不同渠道 APK 使用不同 release 分支管理；后台发布渠道 APK 时，超级管理员选择渠道、主版本并填写发布公告，发布脚本自动合并主 release 分支到渠道 release 分支，检查渠道图标、应用名称、引擎名称一致性，打包、上传文件并回调后台完成发布状态。
- APP 侧只展示当前渠道发布的公告。
- 不同渠道注册的用户算力不能互通。

## Current Implementation Summary

### Backend

- `users.apk_channel` 已存在，默认 `main`。
- APP 注册接口 `POST /auth/register` 已接收 `RegisterRequest.apkChannel` 并保存到用户。
- `GET /auth/me` 返回 `User.apkChannel`，APP 端 `UserDto` 已包含 `apkChannel`。
- 用户角色当前只有字符串 `ADMIN/USER`，没有超级管理员/渠道管理员分层。
- `AuthContext` 只保存 `userId`，JWT 只包含 `userId` 和 `phone`，没有角色和渠道 claim。
- 后台多数管理接口只校验“已登录”，没有统一角色授权层；例如公告、版本、平台、日志等接口需要在本阶段补上角色边界。
- `app_versions`、`announcements`、`transaction_logs`、`shops`、`feedbacks`、`compute_deductions` 目前没有 `channel_id`，只能通过用户或 `apk_channel` 间接推断，无法稳定做渠道级审计和隔离。
- 算力余额当前保存在 `users.compute_balance`，用户间赠送通过 `ComputeService.giftCompute`，没有渠道池。
- 注册赠送 3 点不可转赠算力目前不消耗任何渠道总池。
- APP 版本发布和校验当前按全局 `versionCode` 查询，未按渠道隔离。
- 公告当前只按 `published/type/title` 过滤，未按渠道隔离。

### Admin Frontend

- 登录后把 `admin_token` 和 `admin_user` 存在 `localStorage`。
- 侧边栏所有菜单对所有登录用户可见，没有角色和渠道能力控制。
- 用户列表已显示 `apkChannel`，但没有渠道管理页面、渠道筛选或渠道管理员视图。
- 主 APK 版本页、公告页、用户页、店铺页、交易日志页都还没有渠道维度。
- 当前没有渠道 APK 发布任务、分支命名规范、自动打包脚本、发布状态回调接口，也没有渠道资源一致性校验。

### Android APP

- `app/build.gradle` 已支持 `BuildConfig.APK_CHANNEL`，默认 `main`，可通过 `-PDUODIAN_APK_CHANNEL=...` 打包。
- `UserRepository.register()` 已把 `BuildConfig.APK_CHANNEL` 传给服务器。
- 登录请求当前没有渠道字段，意味着同手机号跨渠道登录无法隔离。
- `AnnouncementRepository.getPublishedAnnouncements()` 和 `AppVersionRepository.getPublishedVersions()` 当前没有传渠道参数，后端也未按渠道过滤。
- 主 APK 升级逻辑已要求登录态，并会按发布版本 `versionCode > currentVersion` 检查，但版本列表是全局的。

## Phase Boundary

### In Scope

- 新增渠道一等实体和渠道算力池。
- 角色升级为 `SUPER_ADMIN / CHANNEL_ADMIN / USER`，兼容旧 `ADMIN`。
- 后端所有管理查询和写操作加渠道作用域。
- APP 登录、注册、公告、版本检查、包校验都进入渠道语义。
- 管理后台新增渠道管理、渠道算力分配、渠道 APK 发布视图调整。
- 渠道管理员后台视图只显示本渠道数据，并能发布本渠道公告、给本渠道用户分配算力。
- 超级管理员管理渠道、渠道管理员、渠道总算力、渠道 APK 发布记录。
- 超级管理员发起渠道 APK 发布任务，服务端记录发布状态，脚本完成合并、校验、打包、上传和回调。
- 数据库迁移：把现有数据全部归入 `main` 渠道，保持线上兼容。
- 加测试覆盖渠道隔离、算力流转、版本/公告过滤。

### Out of Scope

- 本阶段不做多租户独立数据库；所有渠道仍共用同一套后台服务和数据库。
- 后台 Web 不直接执行任意 shell；自动打包必须通过固定白名单脚本或受控 CI worker 执行，传参只能是渠道、主版本、公告内容等受校验字段。
- Android 应用名、launcher 图标和引擎通知名称属于 APK 编译产物，必须在渠道 release 分支中以资源文件或 Gradle 参数形式固定下来，发布脚本负责一致性校验。
- 不改变 Phase 9 的 `cloneInstanceId`、授权 token、店铺恢复和续期核心机制，只给相关数据补渠道边界。
- 不让渠道管理员发布或上传引擎 APK；引擎版本仍为全局超级管理员能力。

## Locked Decisions

- 渠道主键使用 `channels.id`，对外标识使用唯一 `channels.code`；`users.apk_channel` 保留为兼容字段，但新增 `users.channel_id` 作为可信关联。
- `main` 是默认渠道，所有历史数据迁移到 `main`。
- 旧 `ADMIN` 角色迁移为 `SUPER_ADMIN`；新增 `CHANNEL_ADMIN`。
- APP 登录和注册必须携带渠道标识，后端按 `channel + phone` 定位用户。允许同一个手机号在不同渠道注册成不同用户。
- 用户算力余额仍在 `users.compute_balance`，但渠道管理员给用户分配算力必须从 `channels.compute_balance` 扣减。
- 用户之间赠送算力只能在同一渠道内发生，跨渠道一律拒绝。
- 注册赠送算力由渠道配置控制，并从渠道算力池扣减；渠道余额不足时注册成功但不赠送，或按渠道配置拒绝注册，具体实现默认采用“注册成功、不赠送并记录原因”，避免推广链路被算力池耗尽直接阻断。
- `transaction_logs`、`compute_deductions`、`shops`、`feedbacks`、`announcements`、`app_versions` 都要带 `channel_id`，查询不依赖实时 join 才能稳定审计。
- 公告支持 `GLOBAL` 和 `CHANNEL` 作用域：APP 默认看本渠道公告和超级管理员发布的全局公告；渠道管理员只能发布本渠道公告。
- 主 APK 版本发布必须绑定渠道；APP 只看到当前渠道的发布版本。包校验也必须带渠道上下文。
- 渠道 APK 分支命名采用 `release/channel/{channelCode}`；主 release 分支命名采用当前项目已有 release 规范，例如 `release/{versionName}`。发布脚本将主 release 合并到渠道 release 分支，不反向合并渠道定制资源到主 release。
- 渠道定制资源至少包含：主 APK 渠道标识、应用名称、launcher 图标、引擎 APK 显示名称/通知名称。脚本校验代码/资源与 `channels` 配置一致后才允许打包。
- 渠道 APK 发布使用状态机：`PENDING -> MERGING -> VALIDATING -> BUILDING -> UPLOADING -> COMPLETED`，失败进入 `FAILED` 并记录错误日志。

## Risks

- 当前后台缺少统一角色授权，直接新增渠道页面但不补授权会导致普通 APP 用户调用管理接口，这是 P0 风险。
- 从全局手机号唯一切到 `channel + phone` 唯一需要谨慎迁移索引，否则线上注册/登录会受影响。
- 算力池迁移如果没有审计流水，后续无法解释渠道余额和用户余额差异。
- Android 渠道品牌资源是编译期资源，后台配置不能自动改变已构建 APK 的名称和图标。
- 如果 APP 登录不携带渠道，仅靠 token 中旧用户的 `apkChannel` 不足以支持同手机号多渠道账号。
- 后台触发构建属于高风险能力，必须避免将渠道管理员输入直接拼进 shell，必须限定可执行脚本、工作目录、分支命名和回调认证。

## Acceptance Criteria

- 超级管理员可以创建渠道、设置渠道名称、APK 展示名、渠道标识、图标、状态、注册赠送策略和渠道算力池。
- 超级管理员可以创建渠道管理员并绑定渠道。
- 超级管理员可以发起渠道 APK 发布任务；任务自动合并主 release 到渠道 release、校验渠道定制、打包、上传并生成渠道版本记录和版本公告。
- 渠道管理员登录后台后只能看到本渠道用户、店铺、交易、反馈、公告和本渠道只读 APK 版本信息。
- 渠道管理员可以从本渠道算力池给本渠道用户分配算力，并生成用户交易日志和渠道算力流水。
- 跨渠道用户赠送算力、管理员分配算力、店铺访问、公告访问、版本升级检查都会被拒绝或过滤。
- APP A 渠道只显示 A 渠道公告和 A 渠道 APK 更新；APP B 渠道不可见。
- 已有 `main` 渠道用户、店铺、日志、公告、主 APK 版本在迁移后继续可用。
