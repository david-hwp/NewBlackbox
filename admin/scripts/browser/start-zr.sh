#!/usr/bin/env bash
set -euo pipefail

DISPLAY_ID=:19
BIND_IP=0.0.0.0
XPRA_PORT=14500
CONTROL_PORT=14501
WIDTH=${ZR_WINDOW_WIDTH:-360}
HEIGHT=${ZR_WINDOW_HEIGHT:-520}
SCALE=${ZR_BROWSER_SCALE:-1.25}
VNC_PORT=59019
BASE_DIR=${ZR_BASE_DIR:-$HOME/data}
LOG_DIR="$BASE_DIR/logs"
PROFILE_ROOT="$BASE_DIR/profiles"
mkdir -p "$BASE_DIR" "$LOG_DIR" "$PROFILE_ROOT"

pkill -f "[z]r-browser-control.py" 2>/dev/null || true

ZR_BASE_DIR="$BASE_DIR" \
ZR_BIND_IP="$BIND_IP" \
ZR_XPRA_PORT="$XPRA_PORT" \
ZR_VNC_PORT="$VNC_PORT" \
ZR_WINDOW_WIDTH="$WIDTH" \
ZR_WINDOW_HEIGHT="$HEIGHT" \
DISPLAY_ID="$DISPLAY_ID" \
"$BASE_DIR/start-zr-display.sh"

nohup python3 "$BASE_DIR/zr-browser-control.py" \
  --host "$BIND_IP" \
  --port "$CONTROL_PORT" \
  --base-dir "$BASE_DIR" \
  --window-width "$WIDTH" \
  --window-height "$HEIGHT" \
  --browser-scale "$SCALE" \
  >"$LOG_DIR/zr-browser-control.log" 2>&1 &

sleep 1
ss -ltnp | egrep "($XPRA_PORT|$CONTROL_PORT|$VNC_PORT)" || true
