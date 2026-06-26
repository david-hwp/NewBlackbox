# Phase 22: Clone 身份唯一性收口与跨账号防串号 - Research

**Date:** 2026-06-26
**Status:** Complete

## Research Question

Can the project safely stop using `localVirtualUserId` as a server identity check and instead use `AuthContext.userId + cloneInstanceId` for backend shop reports and login-state backups, while preserving existing OPPO/Xiaomi clone data?

## Key Findings

### 1. Current engine storage already has account-scoped clone roots

`ScopedCloneStorage.dataDir(serverUserId, cloneInstanceId, packageName, userId)` resolves runtime data under:

`accounts/<serverUserId>/cards/<cloneInstanceId>/user/<localVirtualUserId>/<packageName>`

`CloneInstanceStore` uses the mapping key:

`serverUserId|packageName|cloneInstanceId`

Planning implication: the local runtime model already has the two important stable dimensions: server user and clone instance. `localVirtualUserId` is an allocation result inside that scope, not the stable identity.

### 2. Real devices prove local user IDs are not portable

OPPO and Xiaomi real-device data showed the same business clone can be backed by different local virtual user IDs depending on device, engine package generation, migration state, and allocation history. This is expected after cross-device backup/restore and new-package migration.

Planning implication: any server comparison of `request.localVirtualUserId` against `shops.local_virtual_user_id` creates false negatives for valid devices. The backend may store the last-seen local ID for diagnostics, but must not reject on mismatch.

### 3. `/shops/report` currently falls back too broadly

When a report carries `cloneInstanceId`, the current backend first tries `findByUserIdAndCloneInstanceId`, then may fall back to platform shop ID or pending shop by package. That fallback is useful for old clients, but unsafe for the new clone-aware client because a clone report can attach to a different card if the direct clone lookup misses.

Planning implication: for clone-aware requests, missing `userId + cloneInstanceId` must be a hard error. Legacy fallback can remain only when the request has no `cloneInstanceId`.

### 4. Current overwrite behavior is too permissive

Existing tests intentionally allowed a clone bound to one real platform shop ID to be overwritten by another real platform shop ID. That is the opposite of the Phase 22 safety rule. If a clone reports a different real shop ID, the right behavior is reject and preserve current shop fields/login-state backup.

Planning implication: update backend behavior and tests so a real-shop-ID change on the same clone is rejected unless the stored shop is still pending/placeholder.

### 5. APP upload sequencing can still overwrite login-state after failed report

`HomeActivity.reportDetectedShopInfo` calls `viewModel.completePendingShop(...)` and immediately calls `exportAndUploadLoginState(...)`. `completePendingShop` is async and returns no success/failure signal. Therefore `/shops/report` can fail while `/shops/{id}/login-state` still accepts a backup for the target system shop ID.

Planning implication: make `reportShop`/`completePendingShop` return success through a callback and only upload login-state after success.

### 6. Login-state manifest lacks clone binding

The upload manifest already carries `systemShopId`, `packageName`, `profileId`, `localVirtualUserId`, timestamp, and bytes. It does not carry `cloneInstanceId` in the current upload path. Backend validation checks package/profile but not system shop or clone identity.

Planning implication: add `cloneInstanceId` to the APP manifest and validate `systemShopId` plus `cloneInstanceId` in the backend upload path. Preserve missing-clone manifest compatibility for older clients, but reject explicit mismatches.

## Compatibility Assessment

The proposed change is compatible with current real-device data because:

- Existing clone IDs are already stored on shop cards.
- Existing engine mappings are already scoped by server user and clone ID.
- Existing authorization token validation remains clone-based.
- Existing login-state restore still targets the current device's local virtual user after resolving the clone locally.
- Server `local_virtual_user_id` can continue to be populated, so admin/debug views do not lose data.

The main behavior change is intentional: mismatched local virtual user ID no longer blocks valid cross-device reports, while mismatched clone/shop identity now blocks unsafe overwrites.

## Research Complete

Phase 22 is feasible as one safety-focused vertical change: backend report identity, backend login-state manifest guard, APP report/upload sequencing, and OPPO dual-account real-device verification.
