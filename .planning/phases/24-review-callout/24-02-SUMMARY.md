# Phase 24 Wave 2: 好评外呼回调与话费扣费 - Summary

**Date:** 2026-06-30
**Status:** Local implementation completed; intranet deployment deferred until Wave 1 sender/outbox is deployed

## What Shipped

- 新增外呼回调入口 `POST /external/review-callouts/gooki/callback`：
  - 使用独立 `X-External-Callback-Token` 鉴权。
  - 可配置是否启用 query `token` 兼容模式。
  - 回调功能默认关闭，必须通过环境变量开启。
  - 同时支持 JSON 和 `application/x-www-form-urlencoded` 回调提交。
  - 接收成功返回 Gooki 兼容响应 `{"code":200,"message":"OK"}`。
- 新增回调配置：
  - `APP_REVIEW_CALLOUT_CALLBACK_ENABLED`
  - `APP_REVIEW_CALLOUT_CALLBACK_TOKEN`
  - `APP_REVIEW_CALLOUT_CALLBACK_ACCEPT_QUERY_TOKEN`
  - `APP_REVIEW_CALLOUT_BILLING_MODE`
  - `APP_REVIEW_CALLOUT_BILLING_RATE_UNITS_PER_MINUTE`
  - `APP_REVIEW_CALLOUT_BILLING_MINIMUM_CHARGE_UNITS`
- 新增回调结果持久化表 `shop_order_review_callout_results`：
  - 保存本地外呼/订单/店铺/用户关联。
  - 保存外部 CDR、任务 ID、通话状态、接通状态、通话时间、通话时长、计费分钟、外部费用、等级、备注。
  - 保存脱敏后的 `params_summary` 和 `raw_summary`。
  - 保存 `DEDUCTED / NOT_BILLABLE / INSUFFICIENT_BALANCE / UNMATCHED` 等计费状态。
- 补充最小 outbox 实体和表 `shop_order_review_callouts`，供 Wave 2 回调关联本地外呼记录；完整发送调度仍属于 Wave 1。
- 回调关联逻辑优先读取 Gooki `params` 中的本地身份：
  - `本地外呼记录ID`
  - `reviewCalloutId`
  - `review_callout_id`
  - `系统订单ID`
  - `shopOrderId`
  - `shop_order_id`
- 回调去重逻辑：
  - 优先使用外部 `cdrId` 生成 SHA-256 幂等键，避免外部异常长字段撑爆库字段。
  - 没有 CDR 时使用任务 ID、本地外呼 ID、手机号 hash 和外呼时间生成 fallback key。
  - 重复回调直接返回已有结果，不重复扣费。
- 回调审计摘要会递归脱敏 `token`、密钥、cookie、profile/storage/debug 相关字段和手机号；原始 `params` 仍只在内存中用于本地 ID 关联。
- 新增 `ReviewCalloutBillingService`：
  - 默认 `callState=2` 或状态文案包含接通信号时才计费。
  - 优先使用外部计费分钟；否则按通话秒数向上取整到分钟。
  - 默认按 `max(minimum_charge_units, billed_minutes * rate_units_per_minute)` 扣除。
  - 外部 `money` 仅作为审计字段；只有显式配置 `EXTERNAL_MONEY` 才按外部费用扣费。
- 新增 `ComputeService.consumePhoneMinutesForReviewCallout()`：
  - 使用用户行锁扣减 `phone_minutes_balance`。
  - 余额不足时不扣费、不写流水、不允许负数。
  - 成功扣费写入 `TransactionLog` 的 `PHONE_CONSUME` 流水。
- 扩展 `transaction_logs`：
  - `shop_order_id`
  - `review_callout_id`
  - `external_task_id`
  - `external_cdr_id`
  - `called_at`
  - `billing_rate`
- 管理后台交易日志新增“外呼关联”展示，能看到订单 ID、外呼 ID、外部话单、外呼时间和费率。
- APP 交易日志新增“话费消耗”筛选 chip，并继续复用已有 `PHONE_CONSUME` 负数展示。

## Verification Completed

- Backend targeted tests:
  - `ReviewCalloutCallbackControllerTest`
  - `ReviewCalloutCallbackServiceTest`
  - `ReviewCalloutBillingServiceTest`
  - `ComputeServiceTest`
  - `TransactionLogControllerTest`
- Command:
  - `cd admin/backend && /tmp/apache-maven-3.9.9/bin/mvn -q -Dmaven.repo.local=/tmp/zhirang-m2 -Dtest=ReviewCalloutCallbackControllerTest,ReviewCalloutCallbackServiceTest,ReviewCalloutBillingServiceTest,ComputeServiceTest,TransactionLogControllerTest test`
- Management frontend:
  - `cd admin/frontend && npm run build`
- APP debug build:
  - First attempt failed because `/usr/libexec/java_home -v 21` resolved only the registered Zulu 8 JDK.
  - Successful command: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :app:assembleDebug --no-daemon`
- Hygiene:
  - `git diff --check`

## Verification Notes

- APP build emitted existing project warnings about duplicate Bcore manifest permissions, NDK `ndk.dir`, Kotlin deprecations, and osmdroid string formatting. None were introduced by this wave.
- Frontend build emitted existing Rollup chunk-size and pure-annotation warnings. The build completed successfully.
- No live intranet callback smoke test was run in this wave because Wave 1 sender/outbox scheduling is not yet deployed. The callback service is covered by focused unit/controller tests with duplicate, unmatched, insufficient-balance, and billing cases.

## Deployment Boundary

- No production `zhirang-dev` online app deployment, upgrade, or restart was performed.
- No intranet `hewp@172.20.0.13` deployment was performed in this wave. The Wave 2 plan says intranet deployment should happen after Wave 1 sender/outbox is deployed there, so deployment remains a follow-up.

## Follow-Up

1. Implement/deploy Phase 24 Wave 1 sender/outbox and half-hour Gooki task creation.
2. Enable callback settings only in intranet runtime environment with a real token.
3. Run a live synthetic callback smoke test against intranet 13:
   - invalid token creates no row.
   - first valid connected callback deducts once and writes one `PHONE_CONSUME` log.
   - identical replay returns success and does not deduct again.
   - insufficient balance stores callback result without negative balance.
4. Verify APP and management backend can both see the same `PHONE_CONSUME` row after intranet deployment.
