#!/usr/bin/env bash
set -euo pipefail

BASE_DIR=${ZR_BASE_DIR:-/home/ubuntu/data}
FETCHER_DIR=${ZR_FETCHER_DIR:-$BASE_DIR/fetcher}

exec "$FETCHER_DIR/crawl_authorized_shop_orders.sh" "$@"
