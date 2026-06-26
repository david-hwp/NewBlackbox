# Phase 22 Summary: Clone identity isolation

**Date:** 2026-06-27
**Status:** Implemented on local codebase; backend deployed to intranet dev server; current main APP installed on OPPO test device.

## Completed

- Backend `/shops/report` now uses current token `userId + cloneInstanceId` as the clone-aware lookup boundary.
- Backend no longer treats `localVirtualUserId` as a cross-device identity guard; it only rejects invalid negative values.
- Backend rejects reports where the same clone is already bound to a different real platform shop ID.
- Login-state upload validates `systemShopId` and explicit `cloneInstanceId` in the manifest before writing backup data.
- APP uploads login-state only after shop basic info report succeeds.
- APP login-state manifest now carries `systemShopId`, `packageName`, `profileId`, `cloneInstanceId`, `platformShopId`, and local artifact metadata.
- APP local identity markers no longer require server-stored `localVirtualUserId` to match.
- APP shop-card reorder now saves only the dragged shop's `packageName + platform.id` scope, so mixed visible lists do not submit mixed-platform `/shops/order` payloads.

## Verification

- Backend Phase 22 controller tests passed earlier for report and login-state identity guards.
- APP `:app:compileDebugKotlin --no-daemon` passed.
- APP `ShopOrderScopeTest` passed, including a mixed-list 京东秒送 ordering case.
- APP `:app:assembleDebug --no-daemon` passed.
- OPPO installed the current main APP package and launched through `LoginActivity -> HomeActivity`.
- OPPO refresh logs showed `/api/shops/report` and `/api/shops/{id}/login-state` returning 200 without "虚拟用户目录号校验失败".

## Notes

- Production `zhirang-dev` was not updated.
- The backend isolated Maven rerun with a temporary local repository was stopped while downloading dependencies; no backend code changed in the later APP reorder wave.
- ADB did not reliably trigger the RecyclerView long-press drag gesture, so the reorder payload scope was verified with focused unit coverage and the OPPO runtime install.
- Test account passwords were not written to repository artifacts.
