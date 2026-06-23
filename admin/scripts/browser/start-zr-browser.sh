#!/usr/bin/env bash
set -euo pipefail
DISPLAY_ID=${DISPLAY_ID:-:19}
URL=${ZR_AUTH_URL:-https://store.jddj.com/base/login}
BASE_DIR=${ZR_BASE_DIR:-$HOME/data}
LOG_DIR="$BASE_DIR/logs"
PROFILE_ROOT=${ZR_PROFILE_ROOT:-$BASE_DIR/profiles}
TRACE_FILE=${ZR_TRACE_FILE:-$LOG_DIR/browser-trace.jsonl}
USER_PHONE=${ZR_USER_PHONE:-unknown-phone}
SHOP_ID=${ZR_SHOP_ID:-unknown-shop}
SESSION_ID=${ZR_SESSION_ID:-default}
SESSION_LOG_DIR="$BASE_DIR/sessions/$SESSION_ID/logs"
WINDOW_WIDTH=${ZR_WINDOW_WIDTH:-360}
WINDOW_HEIGHT=${ZR_WINDOW_HEIGHT:-520}
BROWSER_SCALE=${ZR_BROWSER_SCALE:-1.25}
DEBUG_PORT=${ZR_DEBUG_PORT:-14502}
BROWSER_PROXY=${ZR_BROWSER_PROXY:-}
mkdir -p "$LOG_DIR" "$PROFILE_ROOT" "$SESSION_LOG_DIR"

safe_segment() {
  local raw="${1:-unknown}"
  local safe
  safe=$(printf '%s' "$raw" | tr -c 'A-Za-z0-9._-' '_')
  if [ -z "$safe" ]; then
    safe="unknown"
  fi
  printf '%s' "$safe"
}

SAFE_PHONE=$(safe_segment "$USER_PHONE")
SAFE_SHOP_ID=$(safe_segment "$SHOP_ID")
PROFILE_DIR="$PROFILE_ROOT/$SAFE_PHONE/$SAFE_SHOP_ID/chrome"
mkdir -p "$PROFILE_DIR"

trace() {
  local event="$1"
  local extra="${2:-}"
  local now
  now=$(date -Iseconds)
  printf '{"ts":"%s","event":"%s","phone":"%s","shopId":"%s","profileDir":"%s","url":"%s"%s}\n' \
    "$now" "$event" "$SAFE_PHONE" "$SAFE_SHOP_ID" "$PROFILE_DIR" "$URL" "$extra" >>"$TRACE_FILE"
}

CHROME=""
for candidate in \
  "$HOME/.cache/ms-playwright" \
  /root/.cache/ms-playwright \
  /usr/bin/chromium \
  /usr/bin/chromium-browser \
  /usr/bin/google-chrome \
  /usr/bin/google-chrome-stable; do
  if [ -x "$candidate" ] && [ ! -d "$candidate" ]; then
    CHROME="$candidate"
    break
  fi
  if [ -d "$candidate" ]; then
    CHROME=$(find "$candidate" -path "*/chrome-linux/chrome" -type f -perm -111 2>/dev/null | head -1 || true)
    if [ -n "$CHROME" ]; then
      break
    fi
  fi
done
if [ -z "$CHROME" ]; then
  trace "chromium_missing"
  echo "Chromium binary not found" >&2
  exit 1
fi

if curl -fsS "http://127.0.0.1:$DEBUG_PORT/json/version" >/dev/null 2>&1 \
  && ps -eo args= | grep 'chrome-linux/chrome' | grep -F -- "--user-data-dir=$PROFILE_DIR" | grep -F -- "--remote-debugging-port=$DEBUG_PORT" >/dev/null 2>&1; then
  export DISPLAY="$DISPLAY_ID"
  xdotool search --onlyvisible --class Chromium windowmove 0 0 windowsize "$WINDOW_WIDTH" "$WINDOW_HEIGHT" windowactivate \
    >"$SESSION_LOG_DIR/resize-window.log" 2>&1 || true
  trace "launch_reused" ',"debugPort":'"$DEBUG_PORT"
  exit 0
fi

trace "launch_requested"
ps -eo pid,args= | grep 'chrome-linux/chrome' | grep -F -- "--user-data-dir=$PROFILE_DIR" | awk '{print $1}' | xargs -r kill 2>/dev/null || true
sleep 1
if [ -L "$PROFILE_DIR/SingletonLock" ]; then
  LOCK_TARGET=$(readlink "$PROFILE_DIR/SingletonLock" || true)
  LOCK_PID=${LOCK_TARGET##*-}
  if [ -n "$LOCK_PID" ] && ! kill -0 "$LOCK_PID" 2>/dev/null; then
    rm -f "$PROFILE_DIR/SingletonLock" "$PROFILE_DIR/SingletonSocket" "$PROFILE_DIR/SingletonCookie"
    trace "stale_profile_lock_removed" ',"pid":'"$LOCK_PID"
  fi
fi
pkill -x xmessage 2>/dev/null || true
export DISPLAY="$DISPLAY_ID"
nohup "$CHROME" \
  --no-sandbox \
  --disable-dev-shm-usage \
  --disable-gpu \
  ${BROWSER_PROXY:+--proxy-server="$BROWSER_PROXY"} \
  --remote-debugging-address=127.0.0.1 \
  --remote-debugging-port="$DEBUG_PORT" \
  --force-device-scale-factor="$BROWSER_SCALE" \
  --window-size="$WINDOW_WIDTH,$WINDOW_HEIGHT" \
  --window-position=0,0 \
  --user-data-dir="$PROFILE_DIR" \
  --kiosk "$URL" \
  >"$SESSION_LOG_DIR/chrome-direct.log" 2>&1 &
CHROME_PID=$!
trace "launch_started" ',"pid":'"$CHROME_PID"
sleep 5
pkill -x xmessage 2>/dev/null || true
wmctrl -r "京东秒送商家端 - Chromium" -b add,fullscreen 2>/dev/null || true
wmctrl -a "京东秒送商家端 - Chromium" 2>/dev/null || true
xdotool search --onlyvisible --class Chromium windowmove 0 0 windowsize "$WINDOW_WIDTH" "$WINDOW_HEIGHT" windowactivate 2>/dev/null || true
trace "window_pinned" ',"pid":'"$CHROME_PID"
