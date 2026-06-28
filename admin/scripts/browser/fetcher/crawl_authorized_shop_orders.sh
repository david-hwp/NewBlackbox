#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=${ZR_BASE_DIR:-/home/ubuntu/data}
FETCHER_DIR=${ZR_FETCHER_DIR:-$BASE_DIR/fetcher}
ENV_FILE=${ZR_ORDERS_ENV:-$BASE_DIR/secrets/phase21-orders.env}
LOCK_FILE=${ZR_ORDERS_LOCK:-/tmp/authorized-shop-orders-crawl.lock}
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
  echo "[$(date -Is)] previous authorized shop order crawl is still running"
  exit 0
fi

python3 "$FETCHER_DIR/crawl_authorized_shop_orders.py" --base-dir "$BASE_DIR" --fetcher-dir "$FETCHER_DIR" "$@" \
  >>"$LOG_DIR/authorized-shop-orders-crawl.log" 2>&1
