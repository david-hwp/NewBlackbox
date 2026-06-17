#!/usr/bin/env bash
set -euo pipefail

DISPLAY_ID=${DISPLAY_ID:-:19}
BIND_IP=${ZR_BIND_IP:-0.0.0.0}
XPRA_PORT=${ZR_XPRA_PORT:-14500}
WIDTH=${ZR_WINDOW_WIDTH:-360}
HEIGHT=${ZR_WINDOW_HEIGHT:-520}
VNC_PORT=${ZR_VNC_PORT:-59019}
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
}

if pgrep -f "Xvfb $DISPLAY_ID" >/dev/null 2>&1; then
  export DISPLAY=$DISPLAY_ID
  xrandr --fb "${WIDTH}x${HEIGHT}" >"$SESSION_LOG_DIR/xrandr.log" 2>&1 || true
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
  if ! ss -ltn | grep -q ":$VNC_PORT "; then
    x11vnc -storepasswd zr-vnc "$SESSION_DIR/vnc.pass" >/dev/null
    nohup x11vnc -display "$DISPLAY_ID" -rfbauth "$SESSION_DIR/vnc.pass" -listen "$BIND_IP" -rfbport "$VNC_PORT" -shared -forever -noxdamage -repeat >"$SESSION_LOG_DIR/x11vnc.log" 2>&1 &
    sleep 1
  fi
  ss -ltnp | egrep "($XPRA_PORT|$VNC_PORT)" || true
  exit 0
fi

nohup Xvfb "$DISPLAY_ID" -screen 0 ${WIDTH}x${HEIGHT}x24 -ac +extension RANDR >"$SESSION_LOG_DIR/xvfb.log" 2>&1 &
sleep 1

export DISPLAY=$DISPLAY_ID
nohup fluxbox >"$SESSION_LOG_DIR/fluxbox.log" 2>&1 &
sleep 1

x11vnc -storepasswd zr-vnc "$SESSION_DIR/vnc.pass" >/dev/null
nohup x11vnc -display "$DISPLAY_ID" -rfbauth "$SESSION_DIR/vnc.pass" -listen "$BIND_IP" -rfbport "$VNC_PORT" -shared -forever -noxdamage -repeat >"$SESSION_LOG_DIR/x11vnc.log" 2>&1 &

start_xpra

sleep 8
ss -ltnp | egrep "($XPRA_PORT|$VNC_PORT)" || true
