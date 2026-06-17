#!/usr/bin/env bash
set -euo pipefail

DISPLAY_ID=:19
BIND_IP=0.0.0.0
CONTROL_PORT=14501
WIDTH=${ZR_WINDOW_WIDTH:-360}
HEIGHT=${ZR_WINDOW_HEIGHT:-520}
SCALE=${ZR_BROWSER_SCALE:-1.25}
BASE_DIR=${ZR_BASE_DIR:-$HOME/data}
LOG_DIR="$BASE_DIR/logs"
PROFILE_ROOT="$BASE_DIR/profiles"
mkdir -p "$BASE_DIR" "$LOG_DIR" "$PROFILE_ROOT"

CONTROL_PIDS=$(ps -eo pid,args | awk '/zr-browser-control.py/ && !/awk/ {print $1}')
if [ -n "$CONTROL_PIDS" ]; then
  kill $CONTROL_PIDS 2>/dev/null || true
  sleep 1
  kill -9 $CONTROL_PIDS 2>/dev/null || true
fi

nohup python3 "$BASE_DIR/zr-browser-control.py" \
  --host "$BIND_IP" \
  --port "$CONTROL_PORT" \
  --base-dir "$BASE_DIR" \
  --window-width "$WIDTH" \
  --window-height "$HEIGHT" \
  --browser-scale "$SCALE" \
  >"$LOG_DIR/zr-browser-control.log" 2>&1 &

sleep 1
ss -ltnp | egrep "($CONTROL_PORT)" || true
