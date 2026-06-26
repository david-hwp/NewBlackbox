#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=${ZR_BASE_DIR:-/home/ubuntu/data}
ENV_FILE=${ZR_ORDERS_ENV:-$BASE_DIR/secrets/phase21-orders.env}
LOCK_FILE=${ZR_ORDERS_LOCK:-/tmp/phase21-authorized-meituan-orders.lock}
LOG_DIR=${ZR_ORDERS_LOG_DIR:-$BASE_DIR/logs}

mkdir -p "$LOG_DIR"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

exec 9>"$LOCK_FILE"
if ! flock -n 9; then
  echo "[$(date -Is)] previous phase21 authorized order crawl is still running"
  exit 0
fi

python3 "$BASE_DIR/run_authorized_meituan_orders.py" "$@" \
  >>"$LOG_DIR/phase21-authorized-meituan-orders.log" 2>&1
