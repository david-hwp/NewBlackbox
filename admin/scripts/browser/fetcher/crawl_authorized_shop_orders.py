#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any


HEADER_NAME = "X-External-Callback-Token"
SUPPORTED_PLATFORM_FETCHERS = {
    "mtwm": "mtwm_orders.py",
    "jdms": "jdms_orders.py",
    "tbwm": "tbwm_orders.py",
}


def log(message: str) -> None:
    print(f"[{datetime.now().isoformat(timespec='seconds')}] {message}", flush=True)


def json_request(
    url: str,
    token: str,
    *,
    method: str = "GET",
    payload: dict[str, Any] | None = None,
    proxy: str = "",
    timeout: int = 90,
) -> dict[str, Any]:
    data = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {HEADER_NAME: token}
    if data is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({"http": proxy, "https": proxy}) if proxy else urllib.request.ProxyHandler({}))
    try:
        with opener.open(req, timeout=timeout) as resp:
            body = resp.read().decode("utf-8")
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed with http={exc.code}: {body[:300]}") from exc
    try:
        result = json.loads(body)
    except json.JSONDecodeError as exc:
        raise RuntimeError(f"{method} {url} returned non-json body: {body[:300]}") from exc
    if result.get("code") != 200:
        raise RuntimeError(f"{method} {url} failed: {result.get('message') or result}")
    return result


def load_targets(args: argparse.Namespace) -> list[dict[str, Any]]:
    endpoint = args.backend_url.rstrip("/") + "/shop-orders/crawl-targets"
    result = json_request(endpoint, args.external_callback_token, proxy=args.http_proxy)
    targets = result.get("data") or []
    if not isinstance(targets, list):
        raise RuntimeError("crawl targets response data is not a list")
    return [target for target in targets if is_supported_target(target)]


def is_supported_target(target: dict[str, Any]) -> bool:
    return bool(
        target.get("systemShopId")
        and target.get("userPhone")
        and target.get("controlShopId")
        and target.get("platform") in SUPPORTED_PLATFORM_FETCHERS
    )


def cleanup_old_shop_files(shop_output: Path, retention_days: int, *, now: datetime | None = None) -> int:
    if retention_days < 0 or not shop_output.exists():
        return 0
    cutoff = (now or datetime.now()) - timedelta(days=retention_days)
    removed = 0
    for path in shop_output.iterdir():
        try:
            if not path.is_file() or path.is_symlink():
                continue
            mtime = datetime.fromtimestamp(path.stat().st_mtime)
            if mtime < cutoff:
                path.unlink()
                removed += 1
        except OSError as exc:
            log(f"cleanup skipped path={path}: {exc}")
    return removed


def with_default_node_path(env: dict[str, str], base_dir: Path) -> dict[str, str]:
    default_node_path = str(base_dir / "browser" / "node_modules")
    current = env.get("NODE_PATH", "")
    if not current:
        env["NODE_PATH"] = default_node_path
        return env
    parts = current.split(os.pathsep)
    if default_node_path not in parts:
        env["NODE_PATH"] = current + os.pathsep + default_node_path
    return env


def run_platform_fetcher(args: argparse.Namespace, target: dict[str, Any]) -> dict[str, Any]:
    system_shop_id = str(target["systemShopId"])
    platform = str(target.get("platform") or "")
    fetcher_name = SUPPORTED_PLATFORM_FETCHERS.get(platform)
    if not fetcher_name:
        raise RuntimeError(f"unsupported platform: {platform}")
    shop_output = args.output_dir / f"shop-{system_shop_id}"
    shop_output.mkdir(parents=True, exist_ok=True)
    removed = cleanup_old_shop_files(shop_output, args.output_retention_days)
    if removed:
        log(f"shop={system_shop_id} cleaned old output files={removed}")
    env = with_default_node_path(os.environ.copy(), args.base_dir)
    env.update(
        {
            "ZR_BASE_DIR": str(args.base_dir),
            "ZR_CONTROL_URL": args.control_url,
            "ZR_SYSTEM_SHOP_ID": system_shop_id,
            "ZR_SHOP_PHONE": str(target["userPhone"]),
            "ZR_SHOP_ID": str(target["controlShopId"]),
            "ZR_SHOP_NAME": str(target.get("shopName") or f"shop-{system_shop_id}"),
            "ZR_PLATFORM": platform,
            "OUTPUT_DIR": str(shop_output),
        }
    )
    fetcher_script = args.fetcher_dir / fetcher_name
    completed = subprocess.run(
        ["python3", str(fetcher_script)],
        env=env,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=args.collect_timeout_seconds,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(completed.stderr.strip() or f"platform fetcher exited {completed.returncode}")
    for line in reversed([line.strip() for line in completed.stdout.splitlines() if line.strip()]):
        try:
            payload = json.loads(line)
            if isinstance(payload, dict) and payload.get("payload"):
                return payload
        except json.JSONDecodeError:
            continue
    raise RuntimeError("platform fetcher did not report payload path")


def submit_payload(args: argparse.Namespace, payload_path: Path) -> dict[str, Any]:
    with payload_path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    endpoint = args.backend_url.rstrip("/") + "/shop-orders/ingest"
    return json_request(
        endpoint,
        args.external_callback_token,
        method="POST",
        payload=payload,
        proxy=args.http_proxy,
    )


def run(args: argparse.Namespace) -> int:
    targets = load_targets(args)
    by_platform: dict[str, int] = {}
    for target in targets:
        platform = str(target.get("platform") or "")
        by_platform[platform] = by_platform.get(platform, 0) + 1
    log(f"authorized order crawl targets={len(targets)} platforms={by_platform}")
    failures = 0
    totals = {"received": 0, "inserted": 0, "updated": 0, "rejected": 0}
    for target in targets:
        system_shop_id = target.get("systemShopId")
        try:
            log(f"collecting shop={system_shop_id} controlShopId={target.get('controlShopId')}")
            collection = run_platform_fetcher(args, target)
            result = submit_payload(args, Path(collection["payload"]))
            data = result.get("data") or {}
            for key in totals:
                totals[key] += int(data.get(key) or 0)
            log(
                f"platform={target.get('platform')} shop={system_shop_id} ok labels={collection.get('labels', 0)} "
                f"orders={collection.get('orders', 0)} "
                f"received={data.get('received', 0)} inserted={data.get('inserted', 0)} "
                f"updated={data.get('updated', 0)} rejected={data.get('rejected', 0)}"
            )
        except Exception as exc:
            failures += 1
            log(f"platform={target.get('platform')} shop={system_shop_id} failed: {exc}")
    log(
        f"done targets={len(targets)} failures={failures} "
        f"received={totals['received']} inserted={totals['inserted']} "
        f"updated={totals['updated']} rejected={totals['rejected']}"
    )
    return 1 if failures else 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect today's orders for all authorized supported shops")
    base_dir = Path(os.environ.get("ZR_BASE_DIR", "/home/ubuntu/data"))
    default_fetcher_dir = Path(os.environ.get("ZR_FETCHER_DIR", str(Path(__file__).resolve().parent)))
    parser.add_argument("--base-dir", type=Path, default=base_dir)
    parser.add_argument("--fetcher-dir", type=Path, default=default_fetcher_dir)
    parser.add_argument("--backend-url", default=os.environ.get("ZR_BACKEND_URL", "http://100.99.88.2:8006/api"))
    parser.add_argument("--external-callback-token", default=os.environ.get("ZR_EXTERNAL_CALLBACK_TOKEN", ""))
    parser.add_argument("--http-proxy", default=os.environ.get("ZR_TS_HTTP_PROXY", ""))
    parser.add_argument("--control-url", default=os.environ.get("ZR_CONTROL_URL", "http://127.0.0.1:14501"))
    parser.add_argument("--output-dir", type=Path, default=Path(os.environ.get("OUTPUT_DIR", str(base_dir / "order-output"))))
    parser.add_argument(
        "--output-retention-days",
        type=int,
        default=int(os.environ.get("ZR_ORDER_OUTPUT_RETENTION_DAYS", "7")),
        help="Delete per-shop output files older than this many days before writing new files; negative disables cleanup.",
    )
    parser.add_argument("--collect-timeout-seconds", type=int, default=int(os.environ.get("ZR_COLLECT_TIMEOUT_SECONDS", "180")))
    args = parser.parse_args()
    if not args.external_callback_token:
        parser.error("--external-callback-token or ZR_EXTERNAL_CALLBACK_TOKEN is required")
    return args


if __name__ == "__main__":
    sys.exit(run(parse_args()))
