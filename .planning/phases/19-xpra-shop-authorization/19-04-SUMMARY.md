---
phase: 19-xpra-shop-authorization
plan: 04
status: completed
completed_at: 2026-06-24
environment:
  admin: hewp@172.20.0.13
  crawler: ubuntu/root@192.168.0.210 via zhirang-dev
---

# Wave 4 Summary

## Delivered

- Shop management now exposes one super-admin operation button named `远程后台`; the old separate `授权地址` column and standalone `检测` action are not part of the shop list flow.
- Clicking `远程后台` probes authorization first and writes the fresh status/check time back before deciding what to open.
- Fresh `AUTHORIZED` shops open `/shop-authorization/:id?mode=remote-backend`.
- Non-authorized probe results show `该店铺未授权，是否进行授权？`; choosing `否` closes without opening a remote page, choosing `是` opens `/shop-authorization/:id?mode=authorization-login`.
- The remote window observes iframe/container size and requests a matching Xvfb/Chromium/Xpra size.
- The crawler control service validates real `xrandr` size, recreates undersized displays, relaunches Chrome when needed, and rejects reuse when the existing Chrome URL does not match the requested target.
- `remote-backend` mode skips the old login-form alignment script. `authorization-login` mode still uses the platform PC login page and login alignment.
- Meituan `remote-backend` opens the full merchant backend entry `https://waimaie.meituan.com/`, not the order micro-frontend deep link. This fixes missing left menu/header in the remote backend.

## Deployment

- Deployed admin backend/frontend to the intranet Mac dev/test environment at `http://172.20.0.13:8006`.
- Deployed crawler scripts to `192.168.0.210:/home/ubuntu/data` and restarted `phase19-zr-browser.service`.
- Did not deploy or restart the online `zhirang-dev` application environment.

## Verification

- `git diff --check`
- `cd admin/frontend && npm run build`
- `python3 -m py_compile admin/scripts/browser/zr-browser-control.py`
- `bash -n admin/scripts/browser/start-zr-display.sh admin/scripts/browser/start-zr-browser.sh`
- Remote Maven tests on the Mac through Docker:
  - `mvn -q test -Dtest=ShopControllerTest,ShopServiceTest`
- Remote admin compose status: backend/frontend/mysql/backup containers are up; frontend is exposed on `0.0.0.0:8006`.
- Crawler service status: `phase19-zr-browser.service` is active.
- Headed browser verification through local Chrome DevTools:
  - `极点披萨` opens `http://172.20.0.13:8006/shop-authorization/194?mode=remote-backend`.
  - The iframe stream is same-origin `/zr-stream/15318/` with fixed Xpra client params.
  - Remote Meituan page URL is `https://waimaie.meituan.com/`.
  - The remote page shows the Meituan merchant header and left menu, including `商家首页`, `订单管理`, `商品管理`, `顾客管理`, and `店铺设置`.
  - Unauthorized shop `新增店铺-[9]` shows the expected confirmation prompt; `否` opens nothing, `是` opens `mode=authorization-login`.
- Final crawler display check for `极点披萨`: `DISPLAY=:408 xrandr` reports `current 1440 x 789`.
- Final authorization probe for `极点披萨`: `AUTHORIZED / HIGH`.

## Evidence

- Final headed-browser screenshot: `/tmp/phase19-authorized-remote-backend-menu-final.png`.
- Verification log artifact: `/tmp/phase19-verify-result.json`.
