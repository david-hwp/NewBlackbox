#!/usr/bin/env bash
set -euo pipefail

SERVER=${1:-root@aliyun}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [ "${2:-}" ]; then
  REMOTE_DIR="$2"
else
  REMOTE_DIR='~/data'
fi

ssh "$SERVER" "mkdir -p $REMOTE_DIR $REMOTE_DIR/logs $REMOTE_DIR/profiles"
scp \
  "$SCRIPT_DIR/start-zr.sh" \
  "$SCRIPT_DIR/start-zr-display.sh" \
  "$SCRIPT_DIR/start-zr-browser.sh" \
  "$SCRIPT_DIR/zr-browser-control.py" \
  "$SCRIPT_DIR/zr-login-align.js" \
  "$SERVER:$REMOTE_DIR/"
ssh "$SERVER" "chmod +x $REMOTE_DIR/start-zr.sh $REMOTE_DIR/start-zr-display.sh $REMOTE_DIR/start-zr-browser.sh $REMOTE_DIR/zr-browser-control.py $REMOTE_DIR/zr-login-align.js"
