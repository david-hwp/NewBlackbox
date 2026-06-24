#!/usr/bin/env bash
set -euo pipefail

DISPLAY_ID=${DISPLAY_ID:-:19}
BIND_IP=${ZR_BIND_IP:-0.0.0.0}
XPRA_PORT=${ZR_XPRA_PORT:-14500}
WIDTH=${ZR_WINDOW_WIDTH:-360}
HEIGHT=${ZR_WINDOW_HEIGHT:-520}
VNC_PORT=${ZR_VNC_PORT:-59019}
ENABLE_VNC=${ZR_ENABLE_VNC:-0}
FORCE_RECREATE=${ZR_FORCE_DISPLAY_RECREATE:-0}
BASE_DIR=${ZR_BASE_DIR:-$HOME/data}
LOG_DIR="$BASE_DIR/logs"
PROFILE_ROOT="$BASE_DIR/profiles"
SESSION_ID=${ZR_SESSION_ID:-default}
SESSION_DIR="$BASE_DIR/sessions/$SESSION_ID"
SESSION_LOG_DIR="$SESSION_DIR/logs"
mkdir -p "$BASE_DIR" "$LOG_DIR" "$PROFILE_ROOT"
mkdir -p "$SESSION_DIR" "$SESSION_LOG_DIR"

xpra_http_ready() {
  curl -fsS --max-time 2 "http://127.0.0.1:$XPRA_PORT/" >/dev/null 2>&1
}

display_ready() {
  local socket="/tmp/.X11-unix/X${DISPLAY_ID#:}"
  [ -S "$socket" ] && DISPLAY="$DISPLAY_ID" xrandr -q >/dev/null 2>&1
}

start_xpra() {
  nohup xpra shadow "$DISPLAY_ID" \
    --bind-tcp="$BIND_IP:$XPRA_PORT" \
    --html=on \
    --tcp-auth=none \
    --daemon=no \
    --notifications=no \
    --mdns=no \
    --pulseaudio=no \
    --resize-display=no \
    --desktop-scaling=off \
    >"$SESSION_LOG_DIR/xpra.log" 2>&1 &
  echo $! >"$SESSION_LOG_DIR/xpra.pid"
}

vnc_enabled() {
  case "$ENABLE_VNC" in
    1|true|TRUE|yes|YES|on|ON) return 0 ;;
    *) return 1 ;;
  esac
}

stop_vnc() {
  stop_pid_file "$SESSION_LOG_DIR/x11vnc.pid"
  pkill -f "x11vnc.*-display $DISPLAY_ID" 2>/dev/null || true
}

start_vnc_if_enabled() {
  if ! vnc_enabled; then
    stop_vnc
    return 0
  fi
  if ! ss -ltn | grep -q ":$VNC_PORT "; then
    x11vnc -storepasswd zr-vnc "$SESSION_DIR/vnc.pass" >/dev/null
    nohup x11vnc -display "$DISPLAY_ID" -rfbauth "$SESSION_DIR/vnc.pass" -listen "$BIND_IP" -rfbport "$VNC_PORT" -shared -forever -noxdamage -repeat >"$SESSION_LOG_DIR/x11vnc.log" 2>&1 &
    echo $! >"$SESSION_LOG_DIR/x11vnc.pid"
    sleep 1
  fi
}

list_listeners() {
  if vnc_enabled; then
    ss -ltnp | egrep "($XPRA_PORT|$VNC_PORT)" || true
  else
    ss -ltnp | grep ":$XPRA_PORT " || true
  fi
}

verify_display_size() {
  DISPLAY="$DISPLAY_ID" xrandr -q >"$SESSION_LOG_DIR/xrandr-current.log" 2>&1
  grep -q "current ${WIDTH} x ${HEIGHT}" "$SESSION_LOG_DIR/xrandr-current.log"
}

stop_pid_file() {
  local pid_file="$1"
  if [ -s "$pid_file" ]; then
    local pid
    pid=$(cat "$pid_file" 2>/dev/null || true)
    if [ -n "$pid" ]; then
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
  fi
}

stop_display_stack() {
  stop_pid_file "$SESSION_LOG_DIR/xpra.pid"
  stop_vnc
  stop_pid_file "$SESSION_LOG_DIR/fluxbox.pid"
  stop_pid_file "$SESSION_LOG_DIR/xvfb.pid"
  pkill -f "xpra.*$DISPLAY_ID" 2>/dev/null || true
  pkill -f "Xvfb $DISPLAY_ID" 2>/dev/null || true
  sleep 1
  rm -f "/tmp/.X${DISPLAY_ID#:}-lock" "/tmp/.X11-unix/X${DISPLAY_ID#:}"
}

if [ "$FORCE_RECREATE" != "1" ] && display_ready; then
  export DISPLAY=$DISPLAY_ID
  if ! xrandr --fb "${WIDTH}x${HEIGHT}" >"$SESSION_LOG_DIR/xrandr.log" 2>&1; then
    FORCE_RECREATE=1
  fi
fi

if [ "$FORCE_RECREATE" != "1" ] && display_ready; then
  export DISPLAY=$DISPLAY_ID
  xdotool search --onlyvisible --class Chromium windowmove 0 0 windowsize "$WIDTH" "$HEIGHT" windowactivate \
    >"$SESSION_LOG_DIR/resize-window.log" 2>&1 || true
  if ! xpra_http_ready; then
    pkill -f "xpra.*$DISPLAY_ID" 2>/dev/null || true
    sleep 1
    start_xpra
    for _ in 1 2 3 4 5; do
      xpra_http_ready && break
      sleep 1
    done
  fi
  start_vnc_if_enabled
  verify_display_size
  list_listeners
  exit 0
fi

echo "display_recreated=1"
stop_display_stack
nohup Xvfb "$DISPLAY_ID" -screen 0 ${WIDTH}x${HEIGHT}x24 -ac +extension RANDR >"$SESSION_LOG_DIR/xvfb.log" 2>&1 &
echo $! >"$SESSION_LOG_DIR/xvfb.pid"
sleep 1

export DISPLAY=$DISPLAY_ID
nohup fluxbox >"$SESSION_LOG_DIR/fluxbox.log" 2>&1 &
echo $! >"$SESSION_LOG_DIR/fluxbox.pid"
sleep 1

start_vnc_if_enabled

start_xpra

sleep 8
verify_display_size
xpra_http_ready
list_listeners
