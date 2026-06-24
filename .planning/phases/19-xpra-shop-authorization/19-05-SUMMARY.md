---
phase: 19-xpra-shop-authorization
summary: 05
type: execution-summary
wave: 5
completed_at: 2026-06-24
requirements:
  - PHASE-19-WAVE-5-VNC-DEBUG-GATE
---

# Wave 5 Summary: VNC Debug Gate

## Outcome

VNC/x11vnc is now an explicit debug-only option in the Phase 19 remote browser display stack. Default management-backend remote shop sessions and crawler automation continue to use Xvfb, Xpra, and Chromium without starting a VNC server.

## Changes

- Added `ZR_ENABLE_VNC`, defaulting to disabled, in `admin/scripts/browser/start-zr-display.sh`.
- Preserved opt-in VNC behavior for `ZR_ENABLE_VNC=1`, `true`, `yes`, or `on`.
- Stopped same-display legacy `x11vnc` processes when the display script runs with VNC disabled.
- Added git-managed `admin/scripts/browser/zr-browser.service` for the new crawler/control service.
- Removed obsolete `admin/scripts/browser/sync-to-server.sh`.
- Renamed the crawler server systemd service from `phase19-zr-browser.service` to `zr-browser.service`.

## Deployment

- New crawler server `192.168.0.210` was updated through `zhirang-dev`.
- Runtime script deployed to `/home/ubuntu/data/start-zr-display.sh`.
- systemd unit deployed to `/etc/systemd/system/zr-browser.service`.
- Old `phase19-zr-browser.service` unit was disabled, removed, and reset from failed state.
- No online `zhirang-dev` application deployment or restart was performed.

## Verification

- Local syntax and compile checks:
  - `bash -n admin/scripts/browser/start-zr-display.sh admin/scripts/browser/start-zr-browser.sh admin/scripts/browser/start-zr.sh`
  - `python3 -m py_compile admin/scripts/browser/zr-browser-control.py admin/scripts/browser/fetch_meituan_orders.py`
- Crawler server default state:
  - `zr-browser.service` is active and enabled.
  - Xpra control port `14501` is listening.
  - Xpra base stream port `14500` is listening.
  - `x11vnc_processes=0`.
  - `vnc_listeners=0`.
  - No active/unit-file/runtime service script still uses the `phase19` service name.
- Explicit debug opt-in:
  - Temporary `ZR_ENABLE_VNC=1` session on display `:778` started Xpra on `15778`.
  - The same debug session started one `x11vnc` listener on `16778`.
  - Debug VNC listener was cleaned after verification.
- Real remote backend chain:
  - Opened the authorized “极点披萨” profile session with `phone=15200837196` and `shopId=system-194`.
  - Control service returned `open_ok=True` and `ready=True`.
  - Xpra stream port `15318` returned HTTP 200.
  - Chromium CDP port `17318` returned a valid websocket debugger URL.
  - Intranet management backend proxy `http://172.20.0.13:8006/zr-stream/15318/` returned HTTP 200.
  - The real shop session still had `x11vnc_processes=0` and `vnc_listeners=0`.

## Notes

- Browser-level Playwright was not available in the repo dependencies; verification used HTTP/CDP automation against the real running services.
- Existing Phase 21 worktree changes were left untouched and are not part of this wave.
