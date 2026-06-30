---
phase: 24
plan: 24-PLAN
type: execute
wave: 1
depends_on:
  - .planning/phases/21-shop-order-ingestion/21-PLAN.md
  - .planning/phases/21-shop-order-ingestion/21-SUMMARY.md
  - .planning/phases/23-jd-order-ingestion/23-PLAN.md
  - .planning/phases/23-jd-order-ingestion/23-02-PLAN.md
  - .planning/phases/24-review-callout/24-CONTEXT.md
  - .planning/phases/24-review-callout/24-RESEARCH.md
files_modified:
  - admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java
  - admin/backend/src/main/java/com/duodian/admin/entity/ShopOrderReviewCallout.java
  - admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderReviewCalloutRepository.java
  - admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderRepository.java
  - admin/backend/src/main/java/com/duodian/admin/config/ReviewCalloutProperties.java
  - admin/backend/src/main/java/com/duodian/admin/service/ReviewCalloutPhoneResolver.java
  - admin/backend/src/main/java/com/duodian/admin/service/ReviewCalloutEligibilityService.java
  - admin/backend/src/main/java/com/duodian/admin/service/GookiCalloutClient.java
  - admin/backend/src/main/java/com/duodian/admin/service/ReviewCalloutService.java
  - admin/backend/src/main/java/com/duodian/admin/job/ReviewCalloutScheduler.java
  - admin/backend/src/main/resources/application.properties
  - admin/backend/src/test/java/com/duodian/admin/service/ReviewCalloutPhoneResolverTest.java
  - admin/backend/src/test/java/com/duodian/admin/service/ReviewCalloutEligibilityServiceTest.java
  - admin/backend/src/test/java/com/duodian/admin/service/GookiCalloutClientTest.java
  - admin/backend/src/test/java/com/duodian/admin/service/ReviewCalloutServiceTest.java
autonomous: false
requirements:
  - PH24-D01
  - PH24-D02
  - PH24-D03
  - PH24-D04
  - PH24-D05
  - PH24-D06
  - PH24-D07
  - PH24-D08
  - PH24-D09
  - PH24-D10
---

<objective>
Add a backend review-callout pipeline that scans delivered orders from authorized shops every 30 minutes, sends eligible customer name/phone batches to Gooki `POST /task/external/add`, and guarantees the same order is not sent more than once.
</objective>

<must_haves>
- PH24-D01: only delivered/completed takeout orders from authorized shops become callout candidates.
- PH24-D02: the integration uses the documented Gooki Open API create-task endpoint `POST /task/external/add`.
- PH24-D03: the backend scheduler runs every half hour and does not depend on an admin browser session.
- PH24-D04: a durable idempotency boundary prevents duplicate callouts for the same order.
- PH24-D05: orders without customer name and a valid callable phone are skipped with an auditable reason.
- PH24-D06: orders enter the candidate set when later ingestion updates them to delivered; cancelled/refunded/closed orders never send.
- PH24-D07: callout status, attempts, external task ID, batch ID, response summary, and errors are persisted without secrets.
- PH24-D08: Gooki credentials and task configuration come from runtime configuration only.
- PH24-D09: backend tests cover eligibility, phone normalization, idempotency, client contract, scheduler switch, and sensitive hygiene.
- PH24-D10: deployment verification is limited to the intranet test backend on `hewp@172.20.0.13`; do not touch online `zhirang-dev` app deployment without user approval.
</must_haves>

<tasks>

<task id="24-01" type="execute">
<title>Add durable review-callout outbox schema</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/config/SoftDeleteSchemaInitializer.java`
- `admin/backend/src/main/java/com/duodian/admin/entity/ShopOrder.java`
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderRepository.java`
- `.planning/phases/24-review-callout/24-CONTEXT.md`
</read_first>
<action>
Add persistent callout state:
- Create entity `ShopOrderReviewCallout` mapped to `shop_order_review_callouts`.
- Add `ensureShopOrderReviewCalloutTable()` to `SoftDeleteSchemaInitializer` and include `shop_order_review_callouts` in the soft-delete table list.
- Table fields:
  - `id BIGINT AUTO_INCREMENT PRIMARY KEY`.
  - `shop_order_id BIGINT NOT NULL`.
  - snapshots: `shop_id`, `user_id`, `channel_id`, `platform`, `platform_shop_id`, `platform_order_id`, `shop_name`, `customer_name`.
  - phone audit: `phone_source`, `phone_masked`, `phone_type`.
  - status fields: `status` with values `PENDING`, `SENDING`, `SENT`, `SKIPPED`, `FAILED`, `UNKNOWN`; `skip_reason`; `attempt_count`; `last_attempt_at`; `sent_at`.
  - Gooki fields: `external_task_id`, `request_batch_id`, `response_code`, `response_message`, `response_summary`, `last_error`.
  - locking fields: `locked_at`, `locked_by`.
  - `deleted`, `created_at`, `updated_at`.
- Add unique key `uk_review_callout_order (shop_order_id, deleted)`.
- Add indexes for `status`, `shop_id`, `platform`, `last_attempt_at`, `sent_at`, and `deleted`.
- Create `ShopOrderReviewCalloutRepository` with methods needed to find by `shopOrderId`, claim retryable records, and save state.
</action>
<acceptance_criteria>
- A clean backend startup creates `shop_order_review_callouts`.
- Existing databases receive missing columns/indexes through `SoftDeleteSchemaInitializer`.
- The unique key prevents two active callout rows for the same `shop_orders.id`.
- Entity lifecycle sets `deleted=0`, `createdAt`, and `updatedAt`.
- No field stores Gooki secret, bearer token, request headers, cookies, browser profile paths, or raw platform credentials.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ReviewCalloutServiceTest test`
- `git diff --check`
</verify>
</task>

<task id="24-02" type="execute">
<title>Implement delivered-order eligibility and phone resolution</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/entity/ShopOrder.java`
- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java`
- `admin/scripts/browser/fetcher/mtwm_orders.js`
- `admin/scripts/browser/fetcher/jdms_orders.js`
- `admin/scripts/browser/fetcher/tbwm_orders.js`
- `.planning/phases/24-review-callout/24-RESEARCH.md`
</read_first>
<action>
Create `ReviewCalloutPhoneResolver` and `ReviewCalloutEligibilityService`:
- Delivered eligibility returns true only when:
  - `completedAt` is non-null.
  - `status` or `statusText` contains a completion token: `用户已收餐`, `已完成`, `已送达`, `已收货`, `已收餐`, `送达`, `完成`.
  - `cancelledAt` and `refundedAt` are null.
  - `status`/`statusText` do not contain cancellation/refund/closed tokens: `取消`, `退款`, `退单`, `关闭`, `作废`.
- Phone resolver returns a structured result with:
  - `callable` boolean.
  - `normalizedPhone`.
  - `phoneSource` (`backup_phone`, `privacy_phone`, `raw_payload`, `none`).
  - `phoneType` (`FULL_MOBILE`, `PRIVACY_TRANSFER`, `MASKED`, `TAIL_ONLY`, `INVALID`, `MISSING`).
  - `skipReason`.
- Resolve full 11-digit mobile numbers from `backupPhone` then `privacyPhone`.
- Treat masked values containing `*` as not callable.
- Treat `customerPhoneTail` alone as `TAIL_ONLY` and not callable.
- Make privacy transfer support explicit through config `app.review-callout.allow-privacy-transfer`; when false, `13800000000 转 1234` is skipped as `UNSUPPORTED_PRIVACY_PHONE`; when true, normalize whitespace to `13800000000 转 1234`.
- Require non-blank `customerName`; skip as `MISSING_CUSTOMER_NAME` when absent.
</action>
<acceptance_criteria>
- Meituan `用户已收餐` with `completedAt` is eligible.
- TBWM `已送达` with `completedAt` is eligible.
- JD `已完成` with `completedAt` is eligible.
- Orders with `已取消`, `退款`, `cancelledAt`, or `refundedAt` are not eligible.
- Full 11-digit phones are callable and masked in audit output.
- Tail-only and masked phones are skipped with deterministic reasons.
- Privacy transfer numbers are skipped by default and only callable when `allow-privacy-transfer=true`.
- Missing customer name skips even if a phone exists.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ReviewCalloutPhoneResolverTest,ReviewCalloutEligibilityServiceTest test`
</verify>
</task>

<task id="24-03" type="execute">
<title>Add configurable Gooki Open API client</title>
<read_first>
- `docs/智能外呼机器人接口文档V1.8.2.docx`
- `admin/backend/src/main/resources/application.properties`
- `admin/backend/src/main/java/com/duodian/admin/service/FileStorageService.java`
- `.planning/phases/24-review-callout/24-RESEARCH.md`
</read_first>
<action>
Create runtime configuration and client:
- Add `ReviewCalloutProperties` with environment-backed keys:
  - `app.review-callout.enabled=${APP_REVIEW_CALLOUT_ENABLED:false}`.
  - `app.review-callout.gooki.base-url=${APP_REVIEW_CALLOUT_GOOKI_BASE_URL:https://open-api.gooki.com}`.
  - `app.review-callout.gooki.secret=${APP_REVIEW_CALLOUT_GOOKI_SECRET:}`.
  - optional username/password fallback keys if secret is absent.
  - `scene-id`, `device-id`, `device-group-id`, `task-owner-id`, `task-name-prefix`, `batch-size`, `retry`, `retry-interval`, `retry-connected`, `no-call`, `out-of-order`, `terminate-action`, `allow-privacy-transfer`, and `pre-call-filtering-json`.
- Create `GookiCalloutClient` using JDK `HttpClient` and `ObjectMapper`.
- Implement token acquisition:
  - prefer `GET /token/secret?secret=<urlencoded secret>`.
  - fallback to `GET /token?username=<urlencoded>&password=<urlencoded>` only when username/password are configured.
  - cache token in memory and refresh before reuse when a request returns token invalid/expired codes `1000001` or `1000002`.
- Implement create-task request to `POST /task/external/add`:
  - send header `Authorization: <token>`.
  - include `task_name`, `scene_id`, one of `device_id`/`device_group_id`, optional retry/no-call/order/task-owner/terminate/pre-call fields, and `phones`.
  - for each phone, include `phone` and `phonetic_variables` as a JSON string with keys `姓名`, `店铺名称`, `平台`, `系统店铺ID`, `平台订单ID`, `订单完成时间`, `本地外呼记录ID`.
- Return a typed result with `success`, `externalTaskId`, `code`, `message`, and sanitized response summary.
- Never log or persist the Gooki secret, token, or Authorization header.
</action>
<acceptance_criteria>
- Client builds `/token/secret` and `/task/external/add` requests matching the doc paths and methods.
- Create-task payload contains `phones[].phone` and `phones[].phonetic_variables` as a JSON string.
- Exactly one of `device_id` or `device_group_id` is required by configuration validation.
- Missing `scene_id` or missing line configuration disables sending with a clear error.
- Token invalid/expired response forces one token refresh and retry.
- Tests prove logs/response summary do not contain the configured secret or token.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=GookiCalloutClientTest test`
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 test -Dtest=ReviewCalloutPhoneResolverTest,ReviewCalloutEligibilityServiceTest,GookiCalloutClientTest`
</verify>
</task>

<task id="24-04" type="execute">
<title>Implement idempotent callout service and half-hour scheduler</title>
<read_first>
- `admin/backend/src/main/java/com/duodian/admin/repository/ShopOrderRepository.java`
- `admin/backend/src/main/java/com/duodian/admin/service/ShopOrderService.java`
- `admin/backend/src/main/java/com/duodian/admin/job/ShopExpirationScheduler.java`
- `admin/backend/src/main/resources/application.properties`
- `.planning/phases/24-review-callout/24-CONTEXT.md`
</read_first>
<action>
Implement `ReviewCalloutService` and `ReviewCalloutScheduler`:
- Add a repository query that finds candidate `ShopOrder` rows:
  - active rows only.
  - `completedAt` non-null.
  - authorized shop still exists and has `shop_authorization_status = 'AUTHORIZED'`.
  - no active `shop_order_review_callouts` row with `status in ('SENT', 'SENDING', 'UNKNOWN')` for that order.
  - order age window and batch limit come from config; default scan today's and recent orders only unless explicitly configured otherwise.
- For each candidate:
  - create or load the outbox row by `shopOrderId`.
  - run eligibility and phone resolver.
  - mark `SKIPPED` for non-eligible or non-callable orders with `skipReason`.
  - keep retryable `FAILED` rows under max attempts.
- Batch callable rows up to `app.review-callout.batch-size`.
- Before external call, set `status='SENDING'`, `lockedAt`, `lockedBy`, increment `attemptCount`, and set `lastAttemptAt`.
- When Gooki returns success, mark all rows in that request `SENT`, set `externalTaskId`, `requestBatchId`, `responseCode`, `responseMessage`, `responseSummary`, and `sentAt`.
- When Gooki returns a clear no-task-created failure, mark those rows `FAILED`, store sanitized error, and preserve them for future retry until max attempts.
- When the create-task request may have reached Gooki but the result is unknown, such as HTTP timeout, interrupted response, or process crash recovery from stale `SENDING`, mark rows `UNKNOWN`, store sanitized error, and never auto-retry them. This intentionally prefers missing a call over duplicate calls.
- Re-evaluate non-final contact-related `SKIPPED` rows only if the linked `shop_orders.updated_at` changed after the callout row, but never re-evaluate `SENT`, `SENDING`, or `UNKNOWN` rows automatically.
- Scheduler:
  - class `ReviewCalloutScheduler`.
  - method annotated `@Scheduled(cron = "0 */30 * * * *", zone = "Asia/Shanghai")`.
  - exits immediately and logs a short line when `app.review-callout.enabled=false`.
  - uses a local in-process guard so one backend instance does not overlap itself.
</action>
<acceptance_criteria>
- Running the service twice for the same delivered order sends it at most once.
- Ambiguous post-send failures become `UNKNOWN` and are not retried automatically.
- If an order is first ingested as in-progress and later updated to delivered, the later run sends it once.
- Cancelled/refunded orders are skipped and never sent.
- Missing phone/name creates a `SKIPPED` record with reason and does not call Gooki.
- Gooki success marks rows `SENT` with the same returned task ID for that batch.
- Gooki failure marks rows `FAILED` and allows retry under the configured attempt limit.
- Stale `SENDING` rows recover to `UNKNOWN`, not a new send.
- Scheduler disabled mode performs no candidate scan and no external call.
- Scheduler enabled mode delegates exactly once per tick when not already running.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ReviewCalloutServiceTest test`
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 test -Dtest=ShopOrderServiceTest,ReviewCalloutPhoneResolverTest,ReviewCalloutEligibilityServiceTest,GookiCalloutClientTest,ReviewCalloutServiceTest`
</verify>
</task>

<task id="24-05" type="verify">
<title>Verify locally and deploy only to intranet test backend</title>
<read_first>
- `admin/deploy.sh`
- `admin/docker-compose.yml`
- `.planning/STATE.md`
- `.planning/phases/24-review-callout/24-CONTEXT.md`
- `.planning/phases/24-review-callout/24-RESEARCH.md`
</read_first>
<action>
Complete verification and deployment notes:
- Run backend tests for order ingestion plus all review-callout tests.
- Build the backend package.
- Do not deploy, upgrade, restart, or otherwise change the online `zhirang-dev` app environment without explicit user approval.
- Deploy only the intranet development/test backend on `hewp@172.20.0.13` after tests pass.
- Configure test/safe runtime values:
  - `APP_REVIEW_CALLOUT_ENABLED=false` by default, or `APP_REVIEW_CALLOUT_NO_CALL=1` when performing a Gooki dry run with user-provided credentials.
  - real Gooki secrets stay in environment-specific secret files, not git.
- Use existing delivered order rows or insert a test delivered order in the intranet DB to verify:
  - disabled scheduler does not send.
  - manual service invocation or enabled scheduler creates `SKIPPED` for invalid phone data.
  - with a mock/safe Gooki configuration, one valid test order creates exactly one `SENT` row.
  - repeating the run does not create a duplicate send.
- Record deployment and verification evidence in a later `24-SUMMARY.md` after execution.
</action>
<acceptance_criteria>
- All backend review-callout tests pass locally.
- Backend package build succeeds.
- Intranet 13 backend starts with new schema.
- No production `zhirang-dev` app deployment/restart/update occurs.
- Intranet DB shows idempotent callout state for test orders.
- No logs or DB rows contain the real Gooki secret/token.
</acceptance_criteria>
<verify>
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 test -Dtest=ShopOrderServiceTest,ShopOrderControllerTest,ReviewCalloutPhoneResolverTest,ReviewCalloutEligibilityServiceTest,GookiCalloutClientTest,ReviewCalloutServiceTest`
- `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -Dmaven.repo.local=/tmp/zhirang-m2 package -DskipTests`
- Intranet manual verification on `hewp@172.20.0.13` only.
</verify>
</task>

</tasks>

<artifacts_this_phase_produces>
- `ShopOrderReviewCallout` JPA entity and `shop_order_review_callouts` table.
- `ShopOrderReviewCalloutRepository`.
- `ReviewCalloutProperties`.
- `ReviewCalloutPhoneResolver`.
- `ReviewCalloutEligibilityService`.
- `GookiCalloutClient`.
- `ReviewCalloutService`.
- `ReviewCalloutScheduler`.
- Backend tests for phone resolution, eligibility, Gooki request contract, idempotency, and scheduler behavior.
- Runtime configuration keys under `app.review-callout.*`.
- Follow-up Wave 2 plan `.planning/phases/24-review-callout/24-02-PLAN.md` for call-result callbacks, phone-balance deductions, and `PHONE_CONSUME` ledger visibility.
</artifacts_this_phase_produces>

<verification>
- Unit tests cover delivered-state detection across `mtwm`, `jdms`, and `tbwm` status vocabulary.
- Unit tests cover phone classification for full mobile, privacy transfer, masked, tail-only, invalid, and missing values.
- Service tests prove duplicate scheduler runs do not resend the same `shop_orders.id`.
- Client tests prove `POST /task/external/add` payload matches the documented Gooki contract.
- Local build passes before intranet deployment.
- Intranet verification is limited to `hewp@172.20.0.13`.
- Call-result callback, actual billing, and transaction-log visibility are verified by Wave 2, not by this send-only plan.
</verification>

<success_criteria>
- `shop_order_review_callouts` enforces one active callout row per `shop_orders.id`.
- Backend scheduler runs on a 30-minute cron when enabled and exits without sending when disabled.
- Delivered orders with customer name and callable phone are batched into Gooki `POST /task/external/add` requests.
- Delivered orders without callable contact data are recorded with deterministic skip reasons.
- The same order is never automatically sent twice, including repeated scheduler runs and ambiguous post-send failures.
- Gooki credentials/tokens are never written to git, `.planning`, database response summaries, or logs.
- Phase verification is performed only against the intranet development/test backend unless the user explicitly approves production changes.
- External call-result callbacks and phone-balance deductions are covered by Wave 2 before the feature is considered financially complete.
</success_criteria>

<threat_model>
- Secret leakage: Gooki secret/token must only live in runtime config and in-memory client state; tests must assert sanitized summaries.
- Duplicate calls: durable unique key on `shop_order_id`, service-level row claiming, and `UNKNOWN` for ambiguous outcomes prevent automatic repeat sends.
- Wrong customer contact: phone resolver rejects masked/tail-only values and defaults privacy transfer to disabled until explicitly verified.
- Premature calls: eligibility requires `completedAt` plus completion status and excludes cancelled/refunded/closed rows.
- Production blast radius: Phase 24 verification deploys only to intranet 13 and keeps callout disabled by default unless explicitly configured.
</threat_model>
