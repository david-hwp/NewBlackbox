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
mkdir -p "$BASE_DIR" "$LOG_DIR" "$PROFILE_ROOT"

pkill -f "Xvfb $DISPLAY_ID" 2>/dev/null || true
pkill -f "xpra.*$DISPLAY_ID" 2>/dev/null || true
pkill -f "x11vnc.*$DISPLAY_ID" 2>/dev/null || true
pkill -f "fluxbox.*$DISPLAY_ID" 2>/dev/null || true
pkill -f "xterm.*ZR" 2>/dev/null || true
sleep 1

nohup Xvfb "$DISPLAY_ID" -screen 0 ${WIDTH}x${HEIGHT}x24 -ac +extension RANDR >"$LOG_DIR/xvfb.log" 2>&1 &
sleep 1

export DISPLAY=$DISPLAY_ID
nohup fluxbox >"$LOG_DIR/fluxbox.log" 2>&1 &
sleep 1

x11vnc -storepasswd zr-vnc "$BASE_DIR/vnc.pass" >/dev/null
nohup x11vnc -display "$DISPLAY_ID" -rfbauth "$BASE_DIR/vnc.pass" -listen "$BIND_IP" -rfbport "$VNC_PORT" -shared -forever -noxdamage -repeat >"$LOG_DIR/x11vnc.log" 2>&1 &

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
  >"$LOG_DIR/xpra.log" 2>&1 &

sleep 8
ss -ltnp | egrep "($XPRA_PORT|$VNC_PORT)" || true
