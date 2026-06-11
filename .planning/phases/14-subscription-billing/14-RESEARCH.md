# Phase 14: 用户订阅计费体系 - Research

**Date:** 2026-06-12
**Mode:** Inline codebase research

## Existing Backend Surface

- `User` currently stores `computeBalance`, `nonTransferableComputeBalance`, `shopCount`, `platformCount`, `apkChannel`, `lastLoginAt`; no subscription fields exist.
- `AuthController.register` creates a user with 3 compute and 3 non-transferable compute, then writes `新用户注册赠送算力，不可转赠` transaction log.
- `UserService.update` is the admin user update path. It updates username/avatar/compute balances and writes compute adjustment logs. It currently does not restrict callers by role; the controller exposes `/users/{id}` for any authenticated user, so Phase 14 should gate user admin mutations to `ADMIN`.
- `ComputeService.deductComputeForCloneCreate` and `deductComputeForCloneRenew` are the central shop create/renew billing paths. They already write `compute_deductions` idempotency records and transaction logs.
- `ShopController.createCloneShop` always calls `deductComputeForCloneCreate`; `renew` always calls `deductComputeForCloneRenew`; `issueAuthorizationToken` blocks expired shops with HTTP-style ApiResponse code `402`.
- `ShopResponse.from` calculates remaining days via `ShopExpiration.remainingDays(shop)`, so hiding remaining days for subscribers should be an APP presentation decision, not a shop response mutation.

## Existing Android Surface

- `UserDto` and `TokenManager` persist the logged-in user; adding nullable subscription fields is backwards compatible with existing stored JSON.
- `LoginActivity` registers then asks the user to login manually. A local pending-gift flag keyed by phone/user can show the one-time subscription gift prompt after the first successful login without backend popup state.
- `HomeViewModel.refreshUserInfoFromServer` already refreshes `/auth/me` on resume and saves the user. This is the right source for subscription state.
- `HomeActivity.onShopClick` checks `shop.remainingDays <= 0` and calls `renewExpiredShopBeforeOpen`. This is the right point to distinguish active subscription, normal expired shop prompt, and subscription-expired prompt.
- `ShopListAdapter` always renders `remainingDaysBadge`; adding adapter state `showRemainingDays` will satisfy “订阅制用户不显示店铺维度有效期”.

## Existing Admin Frontend Surface

- `admin/frontend/src/views/Users.vue` is a single Element Plus page with list filters, table columns, and add/edit dialog.
- User update currently sends the whole `form` to `PUT /users/{id}`. Adding subscription plan controls in the same dialog matches the existing management workflow.

## Implementation Implications

- Model subscription state on `users`, not shops. Store at least `subscription_plan` and `subscription_expires_at`; active state is computed as `subscription_expires_at > now`.
- Keep the transaction log table unchanged: 0-amount `CONSUME` logs are enough to record subscription-covered shop operations without affecting existing log list contracts.
- Idempotency must still work for subscription-covered create/renew operations. `compute_deductions.amount` should be `0` for active subscription paths and point to the 0-amount transaction log.
- Backend responses that already include user snapshots (`/auth/login`, `/auth/me`, `CloneShopCreateResponse`, `ShopRenewResponse`) should carry the new user fields automatically once `User` has them.
- `issueAuthorizationToken` should allow active subscribers to open shops even when the shop-level authorization has expired. For non-subscribers and expired subscribers, keep the 402 behavior so the APP forces confirm-and-renew first.

