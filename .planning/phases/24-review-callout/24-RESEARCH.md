# Phase 24: 已送达订单好评外呼对接 - Research

**Date:** 2026-06-29
**Status:** Complete

## Research Question

How should delivered orders collected from authorized shops be sent to the Gooki outbound-call platform every 30 minutes without sending the same order more than once, and how should returned call results drive phone-balance deductions without duplicate charges?

## Key Findings

### 1. The user's "eighth interface" is the right first send path

`docs/智能外呼机器人接口文档V1.8.2.docx` lists Gooki Open API endpoints under `https://open-api.gooki.com`. The relevant send interface is:

- `POST /task/external/add` - 创建外呼任务.
- Required headers include `Content-Type: application/json` and `Authorization` with the obtained token.
- Required body fields include `task_name`, `scene_id`, one of `device_id` or `device_group_id`, and `phones`.
- `phones[].phone` is required.
- `phones[].phonetic_variables` is an optional JSON string and is returned later in call records as `params`.
- Response `data` is the external task ID.

Planning implication: implement a Gooki client that can create task batches and persist the returned task ID against local outbox rows.

### 2. Token acquisition and configuration must be separate from send logic

The doc supports token retrieval by account/password (`GET /token`) and by secret (`GET /token/secret`). The secret-based flow is better for backend automation because it avoids storing a password and returns a token with a documented default validity of 30 days.

Planning implication: configure `app.review-callout.gooki.secret` or equivalent runtime secret, cache token in memory with conservative refresh, and never write the real secret/token into repository files or logs.

### 3. Existing `shop_orders` has enough order identity fields

`ShopOrder` already stores:

- system relationship: `id`, `shop_id`, `user_id`, `channel_id`.
- platform identity: `platform`, `platform_shop_id`, `platform_order_id`, `platform_order_no`.
- state/time: `status`, `status_text`, `completed_at`, `cancelled_at`, `refunded_at`, `last_seen_at`.
- customer/contact: `customer_name`, `customer_phone_tail`, `privacy_phone`, `backup_phone`, address fields.
- sanitized raw order payload.

Planning implication: Phase 24 does not need to rewrite the order ingestion model. It should add an outbox/callout table referencing `shop_orders.id`.

### 4. Delivered-state detection should combine timestamp and status

Current platform collectors map completion-like states differently:

- Meituan: `用户已收餐`, `已完成`.
- TBWM: `已送达`, `已完成`, `已收货`, `已收餐`.
- JD collector currently derives `completed_at` when status is `用户已收餐` or `已完成`, and also inspects timeline text containing `送达`, `收餐`, or `完成`.

Planning implication: eligible orders should require `completed_at is not null` and completion-like status text, while excluding cancellation/refund fields/status. This avoids sending orders that are merely expected to be delivered.

### 5. The hard product risk is phone eligibility, not API shape

The Gooki doc documents `phone` as a mobile number. Existing order crawlers may capture:

- Full mobile number-like values.
- Platform privacy numbers such as `13600000000 转 1234`.
- Masked numbers such as `137****8809`.
- Tail-only values such as `1234`.

Tail-only or masked values are not callable. Privacy transfer numbers may be callable through platform bridges, but the external platform contract does not prove this. A safe first implementation must either:

- send only 11-digit full mobile numbers, or
- explicitly support and test `11-digit 转 extension` privacy numbers with Gooki before enabling them.

Planning implication: create a `ReviewCalloutPhoneResolver` with explicit result types (`FULL_MOBILE`, `PRIVACY_TRANSFER`, `MASKED`, `TAIL_ONLY`, `INVALID`) and skip unsupported values with an auditable reason.

### 6. Durable outbox is the simplest non-duplicate guarantee

The order ingestion pipeline is intentionally idempotent by `shop_id + platform + platform_order_id`, and status changes update the same row. A scheduler that directly queries and sends could double-send if it crashes after Gooki accepts the task but before local state updates, or if multiple app instances run.

Planning implication:

- Add `shop_order_review_callouts` with `shop_order_id` unique.
- Insert/select pending candidates transactionally before sending.
- Prefer at-most-once semantics: mark rows as `SENDING` before the create-task request, and mark ambiguous post-send failures as `UNKNOWN` with no automatic retry.
- Mark `SENT` only after Gooki returns `code=200` with a task ID.
- If an insert already exists for an order, skip creation.
- Use a scheduler lock or row claiming fields (`status`, `locked_at`, `attempt_count`) to avoid overlapping sends in multi-instance deployments.

### 7. Backend scheduled job is lower-risk than modifying crawler scripts

The crawler already runs every 30 minutes and ingests orders. The backend already has `@EnableScheduling` and one scheduled job. Placing callout in backend keeps credentials and idempotency close to the database and avoids spreading Gooki secrets to the crawler server.

Planning implication: implement `ReviewCalloutScheduler` in `admin/backend/src/main/java/com/duodian/admin/job`, disabled by default unless configured.

### 8. Gooki append-phone is useful later but not necessary for first delivery

The doc also has `POST /task/external/add/phone` to append numbers to an existing task. That is useful if operations want one long-running task per day. The user's request asks to send every half hour and pointed to the create-task interface.

Planning implication: first implementation creates a new time-bucketed task per scheduler batch. Keep append-phone as a future optimization.

### 9. The doc provides both pull and push paths for call results

The Gooki doc includes `GET /task/cdr/all2` for CDR query and customer-provided push sections for CDR/form data. CDR fields include external CDR ID, task ID, phone, real phone, call time, call state, duration, billed minute count, money, grade, remark, and `params`. The form push section includes `cdr_id`, `task_id`, `phone`, `call_time`, `grade`, `remark`, `columns`, `content`, and `params`.

Planning implication: Wave 2 should implement an inbound callback endpoint first and parse a tolerant DTO that accepts both CDR-like and form-push-like field names. `params` is critical because it can carry the local callout/order identity that Wave 1 sends in `phonetic_variables`.

### 10. Existing balance and transaction-log model can host phone deductions

The backend already stores `users.phone_minutes_balance`. `TransactionLog` already documents phone log types `PHONE_CONSUME`, `PHONE_OUT`, and `PHONE_IN`; management `Logs.vue` already includes `PHONE_CONSUME` filter/display mappings. The APP enum and row renderer also already support `PHONE_CONSUME`, but `LogsActivity` currently wires filter chips only for all/out/in/consume, so a visible “话费消耗” filter chip needs to be added.

Planning implication: do not create a separate user-facing accounting surface for review-callout charges. Add precise relationship fields or structured remarks to `TransactionLog`, create `PHONE_CONSUME` rows for successful deductions, and expose the missing APP filter entry.

### 11. Callback billing needs its own idempotency key

The send outbox prevents duplicate task creation, but it does not prevent duplicate billing because external callback systems commonly retry. The CDR ID is the best unique key when available. If a push lacks CDR ID, a deterministic fallback should combine external task ID, local callout ID, phone hash, and call time.

Planning implication: add a persisted callout result/CDR table with unique constraints and a separate deduction operation key. Callback processing should be idempotent even if the external platform sends the same result multiple times or sends a later richer payload for the same CDR.

### 12. Phone-balance deduction should be configurable and auditable

Gooki CDR includes external `money`, but the product's APP balance is called phone-minute balance and existing transfer/reclaim logic uses integer units. The user asked for fee-rate deduction, which means the implementation should not hard-code an external-money conversion into business logic.

Planning implication: create runtime billing properties such as enabled billable states, `rate_units_per_minute`, minimum charge, rounding mode, and optional external-money mode. Store billed minutes, external money, configured rate, and final internal deduction amount with each result and transaction log.

## Proposed Implementation Shape

1. Add schema/entity/repository for `shop_order_review_callouts`.
2. Add `ReviewCalloutEligibilityService`:
   - identify delivered orders.
   - exclude cancelled/refunded/closed orders.
   - resolve customer name and callable phone.
   - produce skip reasons.
3. Add `GookiCalloutClient`:
   - get/cache token.
   - build create-task request.
   - map local rows to `phones[]` and `phonetic_variables`.
   - sanitize logs and response snapshots.
4. Add `ReviewCalloutService` and scheduler:
   - run every 30 minutes when enabled.
   - claim unsent eligible orders.
   - batch by configured size.
   - persist `SENT`, `SKIPPED`, `FAILED`, or `UNKNOWN`.
   - never resend `SENT`.
5. Add focused tests:
   - delivered-state matching.
   - phone resolver.
   - duplicate outbox insert/skip.
   - Gooki request contract.
   - scheduler disabled mode.
   - sensitive values not persisted or logged.
6. Add callback/result ingestion:
   - external callback endpoint with independent token.
   - result/CDR table keyed by external CDR ID or deterministic fallback.
   - params-based correlation to local callout/order.
   - billable/not-billable classification.
7. Add phone-balance deduction and logs:
   - compute internal charge from configured rate and billed minutes.
   - deduct exactly once per external call result.
   - write `PHONE_CONSUME` transaction logs with shop/order/callout/CDR details.
   - record insufficient balance and unmatched callbacks without negative balances.
8. Add APP/admin log visibility:
   - APP “话费消耗” filter chip.
   - admin log response/detail fields if required for precise order/callout audit.

## Open Questions / Execution Checks

- Which Gooki `scene_id` should be used for the好评话术?
- Should the integration use `device_id` or `device_group_id`?
- Does Gooki accept platform privacy transfer numbers such as `13600000000 转 1234`? If not confirmed, Phase 24 should skip them.
- Should the first real environment create tasks with `no_call=1` for dry-run verification, then enable actual calls after user approval?
- Which callback URL and shared token should be configured in Gooki's customer-provided CDR push settings?
- What exact fee rate should the business use for review-callout phone-balance deduction? The implementation should make this runtime-configurable and safe to dry-run.

## Research Complete

Phase 24 is feasible as a focused backend extension: a durable review-callout outbox, a half-hour scheduled job, a configurable Gooki Open API client, an authenticated callback/result ingestion endpoint, idempotent phone-balance deduction, and tests around delivery state, phone eligibility, non-duplication, callback retries, and ledger visibility.
