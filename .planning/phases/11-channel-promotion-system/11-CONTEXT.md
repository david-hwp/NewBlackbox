---
phase: 11-channel-promotion-system
status: planned
created_at: "2026-06-09T15:30:00+08:00"
updated_at: "2026-06-10T00:00:00+08:00"
source: user-request
branch: dev
---

# Phase 11: 渠道推广完整体系 - Context

## User Request

当前后台已经能通过 `apkChannel` 获取用户注册渠道。下一版本要把渠道能力做完整：

- 不同渠道有自己的 APK 发布、用户注册、公告、版本升级和算力分配体系。
- 总算力仍由超级管理员管理；超级管理员给渠道管理员用户增加算力，渠道管理员再分配给本渠道用户。
- 渠道管理员登录后台后只能看到自己渠道下的用户、交易、店铺、反馈和公告。
- 超级管理员可以设置不同渠道 APK 的图标、名称、渠道标识、主 APK 包名、引擎 APK 包名和引擎展示信息。
- APK 和引擎 APK 均由超级管理员通过统一发布任务构建、上传和发布到指定渠道。
- `main` 渠道也走同一套自动发布任务，现有手工发布流程只作为应急维护入口。
- APP 侧只展示当前渠道发布的公告、主 APK 版本和引擎版本。
- 不同渠道注册的用户算力不能互通。
- 每个渠道使用不同 `applicationId/packageName` 时，主 APK 和引擎 APK 的系统数据目录应天然隔离。

## Current Implementation Summary

### Backend

- `users.apk_channel` 已存在，默认 `main`。
- APP 注册接口 `POST /auth/register` 已接收 `RegisterRequest.apkChannel` 并保存到用户。
- `GET /auth/me` 返回 `User.apkChannel`，APP 端 `UserDto` 已包含 `apkChannel`。
- 用户角色当前只有字符串 `ADMIN/USER`，没有超级管理员/渠道管理员分层。
- `AuthContext` 只保存 `userId`，JWT 只包含 `userId` 和 `phone`，没有角色和渠道 claim。
- 后台多数管理接口只校验“已登录”，没有统一角色授权层；公告、版本、平台、日志等接口需要在本阶段补上角色边界。
- `app_versions`、`engine_versions`、`announcements`、`transaction_logs`、`shops`、`feedbacks`、`compute_deductions` 目前没有 `channel_id`，无法稳定做渠道级审计和隔离。
- 算力余额当前保存在 `users.compute_balance`，管理后台编辑用户算力已会写用户交易日志。
- 注册赠送 3 点不可转赠算力当前不消耗任何渠道总池；Phase 11 保持“推广体验赠送不纳入渠道算力记账”的业务口径。
- APP 版本发布、引擎版本发布和包校验当前按全局版本查询，未按渠道隔离。
- 公告当前只按 `published/type/title` 过滤，未按渠道隔离。

### Admin Frontend

- 登录后把 `admin_token` 和 `admin_user` 存在 `localStorage`。
- 侧边栏所有菜单对所有登录用户可见，没有角色和渠道能力控制。
- 用户列表已显示 `apkChannel`，但没有渠道管理页面、渠道筛选或渠道管理员视图。
- 主 APK 版本页、引擎版本页、公告页、用户页、店铺页、交易日志页都还没有渠道维度。
- 当前没有统一发布任务、参数化打包脚本、发布状态回调接口，也没有渠道资源一致性校验。

### Android APP / Engine

- `app/build.gradle` 已支持 `BuildConfig.APK_CHANNEL`，默认 `main`，可通过 `-PDUODIAN_APK_CHANNEL=...` 打包。
- 主 APK `applicationId` 当前固定为 `com.zhirang.zhanghaoguanjia`。
- 引擎 APK `applicationId` 当前固定为 `com.zhirang.zhanghaoguanjia.engine`。
- 主 APK 多处硬编码引擎包名和绑定权限，渠道化包名时必须参数化。
- `UserRepository.register()` 已把 `BuildConfig.APK_CHANNEL` 传给服务器。
- 登录请求当前没有渠道字段，请求头也没有统一携带 `X-Apk-Channel`。
- APP 没有统一服务端错误处理层，渠道禁用等全局错误需要集中展示。
- 引擎分身目录应确认基于当前引擎 `applicationId/context.packageName`，不能写死旧包名或公共固定目录。

## Phase Boundary

### In Scope

- 新增渠道一等实体，以 `channels.id` 作为可信租户边界，`channels.code` 作为 APK 渠道标识。
- 角色升级为 `SUPER_ADMIN / CHANNEL / USER`，兼容旧 `ADMIN -> SUPER_ADMIN`；`CHANNEL` 即渠道管理员用户类型。
- 后端所有管理查询和写操作加渠道作用域。
- APP 登录、注册、公告、主 APK 版本、引擎版本、包校验都进入渠道语义。
- APP 通过统一 OkHttp 拦截器给所有 API 请求添加 `X-Apk-Channel: BuildConfig.APK_CHANNEL`，旧 APK 无请求头时后端 fallback 到 `main`。
- 渠道禁用时，后端对 APK 请求统一返回：`该产品暂不可用，请联系：xxx（渠道管理员的手机号）`；APP 统一展示服务端错误。
- 管理后台新增渠道管理、渠道管理员绑定、渠道用户/交易/店铺/反馈/公告过滤、统一发布任务视图。
- 渠道管理员后台视图只显示本渠道数据，并能用自己的算力余额给本渠道用户分配算力。
- 超级管理员通过用户列表按用户类型过滤到 `CHANNEL` 用户，再用现有算力调整逻辑给渠道管理员增加算力，复用用户交易日志。
- 公告、主 APK 版本、引擎 APK 版本均强制绑定渠道；旧 APK 查询不到渠道时只看 `main`。
- 超级管理员发起统一发布任务，支持 `main` 和非 `main` 渠道；脚本完成临时目录创建、分支准备、资源校验、主 APK 和引擎 APK 打包、上传和回调。
- 渠道主 APK 和引擎 APK 的 `applicationId/packageName`、展示名称、图标和通知名称都参数化。
- 数据库迁移：把现有数据全部归入 `main` 渠道，保持线上兼容。
- 新增 Wave 2 验证渠道 `applicationId/packageName` 改动后，主 APK、引擎 APK、引擎映射和分身数据目录按渠道隔离。
- 加测试覆盖渠道隔离、算力分配、版本/公告过滤、包校验和发布任务。
- Phase 11 验收使用生产数据库导出的现有数据快照恢复到本地 Docker MySQL，尽量还原线上环境；不得让测试写操作直连生产数据库。
- APK 验收安装到小米真机；测试包服务器地址临时通过 `-PDUODIAN_API_BASE_URL=http://<本机Tailscale-IP>:<本地后台端口>/api/` 指向本地 Docker 服务，提交前必须恢复为正式地址并确认代码中无本机 Tailscale IP 残留。

### Out of Scope

- 本阶段不做多租户独立数据库；所有渠道仍共用同一套后台服务和数据库。
- 不把 Phase 9 的 `cloneInstanceId` 加入渠道信息；渠道本地数据冲突先通过不同主 APK/引擎 APK `applicationId` 隔离，服务端 clone 标识渠道化另起后续 phase。
- 不改变 Phase 9 的授权 token、店铺恢复和续期核心机制，只给相关数据补渠道边界。
- 后台 Web 不直接执行任意 shell；自动打包必须通过固定白名单脚本或受控 worker 执行，参数必须强校验。
- 手工主 APK/引擎版本维护页面可以保留为应急入口，但正常公开发版必须走统一发布任务。

## Locked Decisions

- `main` 是默认渠道，所有历史数据迁移到 `main`。
- `users.apk_channel` 保留为兼容字段，但新增 `users.channel_id` 作为可信关联。
- 旧 `ADMIN` 角色迁移为 `SUPER_ADMIN`；新增 `CHANNEL` 角色代表渠道管理员。
- 渠道管理员必须从已注册用户下拉选择，不能手工填写手机号；下拉框显示用户名和手机号，并支持按用户名/手机号模糊搜索。
- 管理后台渠道管理员手机号全局唯一，渠道管理员账号由超级管理员绑定渠道，登录时不选择渠道。
- APP 新版本所有 API 请求都带 `X-Apk-Channel`；后端兼容旧 APK，缺少请求头时 fallback 到 `main`。
- 已登录 token 和 `X-Apk-Channel` 不一致时，Phase 11 不做单独强制退出/拒绝逻辑，后续渠道本地分身目录完全隔离后再处理。
- 同一个手机号允许在不同渠道注册成不同普通用户；同一渠道内不允许重复 active 用户。
- 渠道管理员的用户余额就是其可分配算力池。
- 注册赠送算力用于推广体验，不纳入渠道算力记账，也不受渠道管理员余额影响。
- 用户之间赠送算力只能在同一渠道内发生，跨渠道一律拒绝。
- `transaction_logs`、`compute_deductions`、`shops`、`feedbacks`、`announcements`、`app_versions`、`engine_versions` 都要带 `channel_id`。
- 公告和版本发布都必须绑定渠道，不再保留 APP 可见的 `GLOBAL` 公告语义；旧 APK 只看到 `main`。
- 版本发布公告标题固定为 `新版本发布`。
- 主 APK 和引擎 APK 版本发布、下载、校验都必须绑定渠道；APP 只看到当前渠道的发布版本。
- 统一发布任务同时支持 `main` 和非 `main` 渠道；`main` 默认保留当前包名：
  - 主 APK: `com.zhirang.zhanghaoguanjia`
  - 引擎 APK: `com.zhirang.zhanghaoguanjia.engine`
- 非 `main` 渠道通过参数配置主 APK 包名、引擎 APK 包名、渠道标识、展示名、图标和引擎通知名称。
- 发布脚本每次运行创建临时干净目录，结束后清理；管理后台提供重试按钮，重试也走同一套流程。
- 发布脚本由后端以当前登录用户身份签发短期发布 token 调用，上传和回调时同时带该 token 与 `X-Apk-Channel`；token 不得打印、落盘或进入日志。
- 渠道禁用时，APK 请求统一返回禁用提示；渠道管理员仍可登录后台，只读查看渠道状态、用户、余额和交易记录，但不能分配算力、发公告或发版本。

## Risks

- 当前后台缺少统一角色授权，直接新增渠道页面但不补授权会导致普通 APP 用户调用管理接口，这是 P0 风险。
- 从全局手机号唯一切到 `channel + phone` active 唯一需要谨慎迁移索引，否则线上注册/登录会受影响。
- 管理员手机号全局唯一与普通用户跨渠道同手机号并存，需要在登录解析和服务层唯一性校验中明确区分。
- 如果遗漏某个管理接口的渠道过滤，渠道管理员可能看到或修改其他渠道数据。
- Android 渠道包名、引擎包名和绑定权限必须同步参数化；只改主 APK 包名会导致主 APK 绑定不到对应渠道引擎。
- 如果引擎数据根目录写死旧包名或公共路径，不同渠道 APK 仍可能互相影响。
- 后台触发构建属于高风险能力，必须避免将渠道管理员输入直接拼进 shell，必须限定脚本、工作目录、分支命名和回调认证。

## Acceptance Criteria

- 超级管理员可以创建渠道，设置渠道名称、渠道标识、状态、主 APK 包名、引擎 APK 包名、展示名、图标、引擎通知名称和注册赠送配置。
- 超级管理员可以从已注册用户中选择并绑定渠道管理员。
- 超级管理员可以在用户列表按用户类型过滤 `CHANNEL` 用户，并通过现有算力调整逻辑给渠道管理员增加算力且生成交易日志。
- 渠道管理员登录后台后只能看到本渠道用户、店铺、交易、反馈、公告和本渠道只读版本信息。
- 渠道管理员可以用自己的算力余额给本渠道用户分配算力，并生成用户交易日志。
- 统一发布任务可发布 `main` 和非 `main` 渠道，成功后生成渠道主 APK 版本、渠道引擎版本和渠道版本公告。
- 渠道 APK 和渠道引擎 APK 使用参数化包名构建；不同渠道安装后系统数据目录隔离。
- 跨渠道用户赠送算力、管理员分配算力、店铺访问、公告访问、版本升级检查都会被拒绝或过滤。
- APP A 渠道只显示 A 渠道公告、A 渠道主 APK 更新和 A 渠道引擎更新；APP B 渠道不可见。
- 渠道禁用后，APK 侧统一展示禁用提示和渠道管理员手机号。
- 已有 `main` 渠道用户、店铺、日志、公告、主 APK 版本、引擎版本在迁移后继续可用。
- Phase 11 最终验收必须从生产导出现有数据快照并恢复到本地 Docker 环境后完成，不能用空库或生产服务代替。
- APK 最终验收必须安装到小米真机，临时后端地址使用本机 Tailscale IP 指向本地 Docker 的 `/api/` 代理。
- 提交前必须把 APK 后端地址恢复为正式默认值 `http://dpgj.zrnh.cn/api/`，并用搜索确认无本机 Tailscale IP、临时端口或临时 base URL 残留。
