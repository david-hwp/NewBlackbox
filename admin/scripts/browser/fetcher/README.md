# Order Fetcher Scripts

This directory contains the scheduled order collection chain.

## Entrypoint

- `crawl_authorized_shop_orders.sh`
- `crawl_authorized_shop_orders.py`

The shell entrypoint loads `/home/ubuntu/data/secrets/phase21-orders.env`, takes the crawl lock, and runs the Python scheduler. The scheduler loads authorized crawl targets from the admin backend and dispatches each shop by platform.

## Platform Fetchers

Each platform has a Python launcher and a same-name Node/CDP collector:

- `mtwm_orders.py` / `mtwm_orders.js` - Meituan Waimai
- `jdms_orders.py` / `jdms_orders.js` - JD Miaosong
- `tbwm_orders.py` / `tbwm_orders.js` - Taobao Flash Sale / Ele.me

The Python launcher is the scheduler-facing platform entrypoint. The JavaScript file performs browser/CDP collection and writes the ingest payload path to stdout as JSON.

## Compatibility

`../run_authorized_meituan_orders.sh` remains as a compatibility wrapper for existing cron entries and forwards to `fetcher/crawl_authorized_shop_orders.sh`.
