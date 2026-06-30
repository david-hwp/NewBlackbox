# Phase 24: 已送达订单好评外呼对接 - Context

**Gathered:** 2026-06-29
**Status:** Ready for planning
**Source:** User request to connect delivered takeout orders to the review-callout platform using `docs/智能外呼机器人接口文档V1.8.2.docx`

<domain>

## Phase Boundary

Phase 24 consumes orders already persisted by the Phase 21/23 order ingestion pipeline. It does not add new order crawlers, does not change platform authorization/profile behavior, and does not create a new customer-service workflow. Its job is to select delivered takeout orders from `shop_orders`, safely send eligible customer names and callable phone numbers to the Gooki outbound-call platform for review prompting, guarantee that the same order is not sent twice, receive actual outbound-call results, and deduct user phone balance with auditable transaction logs.

The implementation should live primarily in the admin backend because the backend already owns `shop_orders`, `@Scheduled` jobs, database schema initialization, and environment/system-parameter configuration. The crawler server should continue collecting orders every half hour; the backend callout job should independently scan newly delivered orders every half hour.

Wave 1 sends eligible delivered orders. Wave 2 receives external callback results and creates phone-balance deductions. These are intentionally separate so the send path can be deployed without pretending a task creation response is the final call result.

</domain>

<decisions>

## Implementation Decisions

### D-24-01 Delivered Orders Only

Only orders that are actually delivered/completed may be sent to the review callout platform. Candidate detection must require a non-null `completed_at` and a completion-like status such as `用户已收餐`, `已完成`, `已送达`, or `已收货`. Orders with cancellation/refund timestamps or cancellation/refund/closed status text are excluded.

### D-24-02 Use Gooki Create Task as the First Integration Path

The local doc's eighth interface is Gooki `POST /task/external/add`. It creates an outbound-call task and accepts a `phones` array. The first integration should create task batches through this endpoint rather than inventing a separate bridge. The client still needs token acquisition through `/token/secret` or `/token` depending on configured credentials.

### D-24-03 Half-Hour Backend Scheduler

The callout job runs every 30 minutes in the backend using Spring scheduling. It should have an `enabled` configuration switch so local/test environments can build and run without calling Gooki. It should not depend on an admin page being open.

### D-24-04 Durable Outbox for Idempotency

Add a durable callout/outbox table keyed by `shop_order_id`. The phase must prefer at-most-once behavior over guaranteed delivery because the Gooki create-task API does not document an external idempotency key. Rows are claimed and marked sending before the external request is sent. A successful send records Gooki task ID and marks the row `SENT`; later scheduler runs must skip it. If the request outcome is ambiguous after it may have reached Gooki, mark the row `UNKNOWN` and do not auto-retry. Failed attempts may retry only when no task could have been created, such as configuration validation or token acquisition before the create-task request.

### D-24-05 Conservative Phone Eligibility

`shop_orders` currently may contain `privacy_phone`, `backup_phone`, `customer_phone_tail`, and platform-specific raw payloads. The Gooki doc only documents `phones[].phone` as a mobile phone number. Until verified otherwise, a value with only a tail or masked digits is not callable. A value like `13800000000 转 1234` must be treated as a platform privacy/transfer number and only sent if we deliberately support and test that format with Gooki; otherwise skip with reason `UNSUPPORTED_PRIVACY_PHONE`.

### D-24-06 Transparent Audit Without Secret Leakage

Persist enough callout state to audit what happened: local outbox ID, `shop_orders.id`, system shop ID, platform, platform order ID, customer name, phone type used, masked phone, status, attempt count, Gooki task ID, request batch ID, response code/message, and last error. Do not persist Gooki secret, token, full request headers, cookies, browser profile paths, or unredacted logs.

### D-24-07 Config Stays Runtime-Only

Gooki base URL, secret or username/password, scene ID, device ID or device group ID, task owner ID, task name prefix, batch size, retry settings, pre-call filtering, and enable flag come from environment variables or system parameters. Real credentials must not be written to git, `.planning`, screenshots, logs, or chat transcripts.

### D-24-08 Keep Environment Boundary

Implementation and deployment verification target the intranet development/test admin backend on `hewp@172.20.0.13`. Do not deploy, upgrade, or restart the online `zhirang-dev` app environment without explicit user approval.

### D-24-09 External Result Callback Is the Source of Actual Call Outcome

Creating a Gooki task only proves the phone number was submitted. The actual connected/not-connected state, call duration, billed minutes, money, grade, remark, and customer response must come from Gooki CDR/result push or an equivalent future external callback. The backend must expose a dedicated callback endpoint with its own shared callback token/signature and return the success shape expected by the external platform.

### D-24-10 Correlate by Local Callout Identity, Not Customer Text

Wave 1 must put a local identifier into `phones[].phonetic_variables`; the Gooki CDR/result API later returns this value as `params`. Wave 2 must primarily correlate callbacks through that local callout ID and system order ID. Phone number, customer name, or shop name can be used only as diagnostics/fallback validation, never as the primary order identity.

### D-24-11 Deduct Phone Balance Exactly Once per External Call Result

Each billable external call result must map to one and only one phone-balance deduction. The idempotency key should be the external CDR ID when present, otherwise a stable fallback from external task ID, local callout ID, phone hash, and call time. Duplicate retries from the external platform must update the stored result if needed but must not deduct twice.

### D-24-12 Phone Consumption Must Use the Existing Transaction Ledger

The backend already has `users.phone_minutes_balance` and `TransactionLog` phone log types (`PHONE_CONSUME`, `PHONE_OUT`, `PHONE_IN`). Wave 2 should write successful review-callout deductions as `PHONE_CONSUME` transaction logs rather than creating a disconnected accounting table. The log must retain enough relationship fields or remarks to identify user, channel, platform, shop, order, callout row, external CDR, call time, billing base, rate, and amount.

### D-24-13 Balance Protection and Failed Deduction Visibility

If callback data cannot be matched to a local callout/order, if the call is not billable, or if the user lacks enough phone balance, the callback still must be stored with a deterministic status. User balance must not go negative. Insufficient-balance and unmatched-callback states must be visible for admin investigation instead of being silently dropped.

</decisions>

<canonical_refs>

## Canonical References

Downstream agents MUST read these before planning or implementing.

### Planning and API Contract

- `.planning/REQUIREMENTS.md` - Phase 24 requirement IDs `PH24-D01` through `PH24-D19`.
- `.planning/ROADMAP.md` - Phase 24 roadmap, dependencies, and environment boundary.
- `docs/智能外呼机器人接口文档V1.8.2.docx` - Gooki outbound-call Open API, especially token, scene/device lookup, create-task, append-phone, CDR/query, CDR push, and form push interfaces.

### Existing Order Pipeline

- `.planning/phases/21-shop-order-ingestion/21-PLAN.md` - order table/API/scheduler design and idempotent ingestion semantics.
- `.planning/phases/21-shop-order-ingestion/21-SUMMARY.md` - shipped Phase 21 behavior.
- `.planning/phases/23-jd-order-ingestion/23-PLAN.md` - multi-platform scheduler extension and JD semantics.
- `.planning/phases/23-jd-order-ingestion/23-02-PLAN.md` - TBWM extension and current fetcher naming layout.
- `admin/backend/src/main/java/com/duodian/admin/entity/ShopOrder.java` - current persisted order fields.
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderRepository.java` - current order search/upsert repository.
- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java` - current ingestion, crawl target, status preservation, and sanitization behavior.
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java` - schema bootstrap pattern.
- `admin/backend/src/main/java/com/duodian/admin/job/ShopExpirationScheduler.java` - existing Spring scheduled job pattern.
- `admin/backend/src/main/resources/application.properties` - environment-backed app configuration pattern.

### Existing Balance and Log Pipeline

- `admin/backend/src/main/java/com/duodian/admin/entity/User.java` - current `phoneMinutesBalance` field.
- `admin/backend/src/main/java/com/duodian/admin/entity/TransactionLog.java` - current transaction fields and phone log type conventions.
- `admin/backend/src/main/java/com/duodian/admin/service/ComputeService.java` - current compute and phone-balance mutation patterns.
- `admin/backend/src/main/java/com/duodian/admin/service/TransactionLogService.java` - current transaction-log creation/query service.
- `admin/backend/src/main/java/com/duodian/admin/repository/TransactionLogRepository.java` - existing admin/app log query filtering.
- `admin/backend/src/main/java/com/duodian/admin/controller/TransactionLogController.java` - existing `/logs` and `/logs/my` APIs.
- `admin/backend/src/main/java/com/duodian/admin/controller/dto/TransactionLogResponse.java` - admin log response shape.
- `admin/frontend/src/views/Logs.vue` - management transaction-log list and `PHONE_CONSUME` filter support.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/bean/LogEntry.kt` - APP log type enum already includes `PHONE_CONSUME`.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/logs/LogsActivity.kt` - APP transaction-log filter chip wiring.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/logs/LogsAdapter.kt` - APP transaction-log row rendering for phone consumption.
- `app/src/main/java/com/zhirang/zhanghaoguanjia/view/logs/LogsViewModel.kt` - APP transaction-log API mapping and fallback descriptions.

### Tests and Runtime

- `admin/backend/src/test/java/com/duodian/admin/service/ShopOrderServiceTest.java` - order ingestion test style and sample statuses.
- `admin/backend/src/test/java/com/duodian/admin/controller/ShopOrderControllerTest.java` - callback token/API test style.
- `admin/scripts/browser/fetcher/README.md` - current order crawler runtime root and half-hour cron context.

</canonical_refs>

<specifics>

## Specific Ideas

- Suggested table name: `shop_order_review_callouts`.
- Suggested unique key: `uk_review_callout_order (shop_order_id, deleted)`.
- Suggested statuses: `PENDING`, `SENDING`, `SENT`, `SKIPPED`, `FAILED`, `UNKNOWN`.
- Suggested result statuses: `CALLBACK_RECEIVED`, `NOT_BILLABLE`, `DEDUCTED`, `DUPLICATE_IGNORED`, `INSUFFICIENT_BALANCE`, `UNMATCHED`, `DEDUCTION_FAILED`.
- Suggested phone source order: verified full `backup_phone`, verified full `privacy_phone`, future raw-payload full phone extraction if explicitly supported; never `customer_phone_tail` alone.
- Suggested scheduled cron: `0 */30 * * * *` with `zone = "Asia/Shanghai"`.
- Suggested task name: `<configured prefix>-yyyyMMdd-HHmm`, with batches capped by configurable `app.review-callout.batch-size`.
- Suggested `phonetic_variables` JSON keys: `姓名`, `店铺名称`, `平台`, `系统店铺ID`, `系统订单ID`, `平台订单ID`, `订单完成时间`, `本地外呼记录ID`.
- Suggested callback endpoint: `POST /external/review-callouts/gooki/callback` or equivalent external namespace outside JWT-protected admin routes.
- Suggested callback auth: `X-External-Callback-Token` or `token` query fallback backed by runtime-only configuration; reject missing or wrong token before parsing business data.
- Suggested billing model: store external `money` as audit evidence, but deduct internal phone balance using configurable `rate_units_per_minute * billed_minutes` with an optional minimum charge unless the business later decides to use external money directly.
- Suggested transaction log type: `PHONE_CONSUME`.
- Suggested transaction-log precision fields: add columns for `shop_order_id`, `review_callout_id`, `external_task_id`, `external_cdr_id`, and `called_at` if remarks alone are not enough for admin filtering and audit.

</specifics>

<deferred>

## Deferred Ideas

- Admin UI for manually retrying callout records.
- Polling Gooki CDR when callback push is unavailable. The preferred Wave 2 path is inbound callback first.
- Manual replay of already sent orders.
- Business rules for avoiding quiet hours beyond Gooki task start/end configuration.
- Platform-specific deeper customer-phone enrichment if list/order pages only expose masked or tail numbers.

</deferred>

---

*Phase: 24-review-callout*
*Context gathered: 2026-06-29 from user request, Gooki doc extraction, and Phase 21/23 code inspection*
