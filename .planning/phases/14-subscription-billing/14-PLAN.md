# Phase 14: 用户订阅计费体系 - Plan

**Status:** Planned
**Created:** 2026-06-12

## Goal

Add a user-level subscription billing layer that is compatible with the existing compute billing system. Active subscribers get unlimited shop create/renew/open coverage during the subscription window, while expired subscribers and normal users automatically fall back to the current 1-compute shop billing behavior.

## Wave 1 - Backend Subscription Domain

1. Add `subscription_plan`, `subscription_expires_at`, and `subscription_updated_at` to `users`.
2. Update `schema.sql`, `data.sql`, and `SoftDeleteSchemaInitializer` so fresh and existing databases get the fields.
3. Add `User` helper methods for active subscription and normalized plan handling.
4. Update registration to grant 30 days subscription in addition to the existing 3 non-transferable compute gift.
5. Add subscription transaction/audit remarks for registration and admin upgrades if this can reuse `transaction_logs` without schema churn.

**Acceptance:**
- New users returned by `/auth/me` have active subscription for about 30 days.
- Existing users with null subscription fields behave as ordinary compute-billed users.

## Wave 2 - Backend Billing Semantics

1. Teach `ComputeService.deductComputeForCloneCreate` and `deductComputeForCloneRenew` to detect active subscriptions.
2. For active subscribers, create idempotency records and `CONSUME` transaction logs with `amount=0`, do not reduce `computeBalance`, and return success with `deducted=false`.
3. For normal users and expired subscribers, keep existing 1-compute behavior unchanged.
4. Update `ShopController.issueAuthorizationToken` so active subscribers can open expired shop-level authorizations; normal/expired users still receive `402` and must renew.
5. Keep `renew` as the single server-side “confirm and deduct/cover before entry” operation used by APP.

**Acceptance:**
- Active subscriber creates/renews shops with 0 balance change and a transaction log.
- Expired subscriber with insufficient compute gets `402` on renew.
- Active subscriber can enter an expired shop without seeing a shop-level expiry block.

## Wave 3 - Admin Management

1. Gate `/users`, `/users/{id}`, create/update/delete user management endpoints to `ADMIN` callers.
2. Add a DTO/API path for subscription updates, or safely extend admin `PUT /users/{id}` so only admins can set subscription fields.
3. Support plan values `NONE`, `MONTHLY`, `QUARTERLY`, `YEARLY`; setting a paid plan writes expiry as now + 1/3/12 months.
4. Update `Users.vue` list and edit dialog to show subscription status and set plan.

**Acceptance:**
- Admin can upgrade a user to monthly/quarterly/yearly subscription from the backend UI.
- Ordinary authenticated users cannot mutate another user's subscription or balance through `/users/{id}`.

## Wave 4 - Android App UX

1. Extend `UserDto` with subscription fields and computed active state.
2. Add HomeViewModel subscription status LiveData derived from stored/refreshed user.
3. Add a small subscription line under the phone number in `activity_home.xml`.
4. Hide `remainingDaysBadge` in `ShopListAdapter` while the user has an active subscription.
5. Change expired-shop click prompt:
   - Active subscription: open directly after token/authorization refresh.
   - Expired subscription or normal user: prompt existing compute renewal.
   - If the stored user has subscription fields but is expired, use exact message `订阅已到期，继续使用将扣除1点算力`.
6. Add one-time first-login-after-registration gift dialog above public announcements, using a local pending registration flag.

**Acceptance:**
- Active subscribers see subscription expiry under phone and no shop remaining-days badges.
- Normal/expired users see the upgrade text and shop remaining-days badges.
- First login after registration shows the subscription gift message once.
- Expired subscribers must confirm before server renew/deduct and entering a shop.

## Wave 5 - Verification

1. Add backend unit tests for registration gift, subscription-covered clone create/renew, expired fallback, auth-token behavior, and user admin gating.
2. Build backend tests with JDK 21.
3. Compile Android debug Kotlin with the configured API base URL.
4. Build admin frontend.
5. Run an end-to-end smoke test against local backend if time permits: register -> login -> `/auth/me` subscription fields -> admin update -> create/renew shop response.

**Commands:**

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd admin/backend && mvn -Dtest=AuthControllerTest,UserServiceTest,ComputeServiceTest,ShopControllerTest test
./gradlew :app:compileDebugKotlin -PDUODIAN_API_BASE_URL=http://127.0.0.1:8006/api/
cd admin/frontend && npm run build
```

