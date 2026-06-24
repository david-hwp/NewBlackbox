#!/usr/bin/env python3
"""
Fetch today's Meituan merchant orders for a shop using the ZR remote browser session.

Runs on the same host as zr-browser-control.py, connects to the remote Chromium via CDP,
navigates to the order-history page, reveals phone/address per order card, extracts
order data and writes deduplicated JSON to /tmp.

Example cron entry (every 30 minutes):
    */30 * * * * cd /root/data && python3 fetch_meituan_orders.py \
        --control-url http://127.0.0.1:14501 \
        --phone 15200837196 \
        --shop-id system-194 \
        --shop-name 极点披萨 \
        --output-dir /tmp >> /tmp/fetch_meituan_orders.log 2>&1
"""

from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.request
from datetime import datetime
from pathlib import Path
from typing import Any


def log(msg: str) -> None:
    print(f"[{datetime.now().isoformat()}] {msg}", flush=True)


def ensure_browser_session(control_url: str, phone: str, shop_id: str) -> dict[str, Any]:
    """Call ZR /open so the remote browser/profile for this shop is alive."""
    url = (
        f"{control_url}/open"
        f"?phone={phone}"
        f"&shopId={shop_id}"
        f"&url=https%3A%2F%2Fwaimaie.meituan.com%2Fnew_fe%2Flogin_gw%23%2Flogin"
        f"&width=996&height=1120"
        f"&renderWidth=996&renderHeight=1120"
        f"&renderScale=1&scale=1.25"
    )
    log(f"calling ZR /open: phone={phone} shop_id={shop_id}")
    req = urllib.request.Request(url, method="GET")
    with urllib.request.urlopen(req, timeout=90) as resp:
        payload = json.loads(resp.read().decode("utf-8"))
    if not payload.get("ok"):
        raise RuntimeError(f"ZR /open failed: {payload}")
    log(f"session ready, debug_port={payload.get('debugPort')}, profile={payload.get('profileDir')}")
    return payload


def parse_order_card(text: str) -> dict[str, Any] | None:
    """Parse one order card's innerText into structured fields."""
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    if not lines:
        return None
    full_text = "|".join(lines)

    # Order number like #9
    m_no = re.search(r"#(\d+)", full_text)
    order_no = m_no.group(1) if m_no else None

    # Order ID: 订单编号：1802176573697106215
    m_id = re.search(r"订单编号[：:]\s*(\d+)", full_text)
    order_id = m_id.group(1) if m_id else None

    if not order_id:
        return None

    # Time: 06-20 12:45前送达 or 06-20 12:19下单
    m_time = re.search(r"(\d{2}-\d{2}\s+\d{2}:\d{2})(前送达|下单)?", full_text)
    order_time = m_time.group(1) if m_time else None
    order_time_label = m_time.group(2) if m_time else None

    # Customer name: usually right after status tags, before 门店新客/发起聊天/下单N次
    # Heuristic: scan segments left-to-right, pick the first short Chinese token
    # that is not a known UI label.
    customer_name = None
    exclude_labels = {"门店新客", "发起聊天", "美团顾客"}
    # Also exclude any segment matching "下单X次"
    parts = [p.strip() for p in full_text.split("|") if p.strip()]
    for i, p in enumerate(parts):
        if p in exclude_labels or re.fullmatch(r"下单\d+次", p):
            # name should be the segment immediately before this label
            if i > 0:
                candidate = parts[i - 1]
                if re.fullmatch(r"[一-龥·]{1,6}", candidate):
                    customer_name = candidate
                    break
    # Fallback: first short Chinese segment that is not a known label
    if not customer_name:
        for p in parts:
            if p in exclude_labels or re.fullmatch(r"下单\d+次", p) or "查看" in p or "配送" in p:
                continue
            if re.fullmatch(r"[一-龥·]{1,6}", p):
                customer_name = p
                break

    # Phones
    privacy_phone = None
    m_privacy = re.search(r"隐私号码\|(\d{11}\s*转\s*\d{4})", full_text)
    if m_privacy:
        privacy_phone = re.sub(r"\s+", " ", m_privacy.group(1))

    backup_phone = None
    m_backup = re.search(r"备用号码\|(\d{11}\s*转\s*\d{4})", full_text)
    if m_backup:
        backup_phone = re.sub(r"\s+", " ", m_backup.group(1))

    customer_tail = None
    m_tail = re.search(r"顾客电话\|手机尾号(\d{4})", full_text)
    if m_tail:
        customer_tail = m_tail.group(1)

    # Address: between 顾客地址 and the next order/detail label.
    address = None
    m_addr = re.search(
        r"顾客地址\|([^|]+?)(?=\|(?:已出餐|骑手|订单已|备注|隐私号码|顾客电话|预计收入|顾客商品实付|订单编号|商品|餐品|$))",
        full_text,
    )
    if m_addr:
        address = m_addr.group(1).strip()

    # Status: after order_no/time, before customer name; common status phrases
    status = None
    status_candidates = ["骑手已取餐", "用户已收餐", "已取消", "待接单", "待配送", "已完成", "已退款"]
    for cand in status_candidates:
        if cand in full_text:
            status = cand
            break

    # Amount: 预计收入 or 顾客商品实付
    amount = None
    m_amount = re.search(r"预计收入\|￥([\d.]+)", full_text)
    if m_amount:
        amount = float(m_amount.group(1))

    return {
        "order_no": order_no,
        "order_id": order_id,
        "order_time": order_time,
        "order_time_label": order_time_label,
        "customer_name": customer_name,
        "privacy_phone": privacy_phone,
        "backup_phone": backup_phone,
        "customer_phone_tail": customer_tail,
        "address": address,
        "status": status,
        "estimated_income": amount,
        "raw_text": full_text[:800],
        "fetched_at": datetime.now().isoformat(),
    }


def parse_order_minute(value: str | None, base_year: int | None = None) -> str | None:
    """Convert Meituan's MM-DD HH:mm text to backend LocalDateTime JSON."""
    if not value:
        return None
    text = value.strip()
    year = base_year or datetime.now().year
    try:
        parsed = datetime.strptime(f"{year}-{text}", "%Y-%m-%d %H:%M")
        return parsed.strftime("%Y-%m-%dT%H:%M:%S")
    except ValueError:
        return None


def order_to_ingest_item(order: dict[str, Any], base_year: int | None = None) -> dict[str, Any]:
    """Map parsed order data to backend /shop-orders/ingest item shape."""
    order_time = order.get("order_time")
    order_time_label = order.get("order_time_label")
    normalized_time = parse_order_minute(order_time, base_year)
    status = order.get("status")
    completed_statuses = {"用户已收餐", "已完成"}
    item = {
        "platform_order_id": order.get("order_id"),
        "platform_order_no": order.get("order_no"),
        "order_sequence": order.get("order_no"),
        "order_time_text": order_time,
        "ordered_at": normalized_time if order_time_label == "下单" else None,
        "expected_delivery_at": normalized_time if order_time_label == "前送达" else None,
        "completed_at": normalized_time if status in completed_statuses and normalized_time else None,
        "fetched_at": order.get("fetched_at"),
        "status": status,
        "status_text": status,
        "estimated_income": order.get("estimated_income"),
        "customer_name": order.get("customer_name"),
        "privacy_phone": order.get("privacy_phone"),
        "backup_phone": order.get("backup_phone"),
        "customer_phone_tail": order.get("customer_phone_tail"),
        "address": order.get("address"),
        "recipient_address": order.get("address"),
        "raw_text": order.get("raw_text"),
        "raw_payload": {
            key: value for key, value in order.items()
            if key not in {"cookie", "cookies", "token", "authorization", "profileDir", "debugPort"}
        },
    }
    return {key: value for key, value in item.items() if value is not None}


def build_ingest_payload(args: argparse.Namespace, orders: list[dict[str, Any]]) -> dict[str, Any]:
    return {
        "shopId": args.system_shop_id,
        "source": "fetch_meituan_orders",
        "ingestBatchId": datetime.now().strftime("meituan-%Y%m%d-%H%M%S"),
        "orders": [order_to_ingest_item(order) for order in orders],
    }


def submit_orders(args: argparse.Namespace, orders: list[dict[str, Any]]) -> None:
    if not args.backend_url or not args.system_shop_id:
        log("backend submission skipped: --backend-url or --system-shop-id not set")
        return
    payload = build_ingest_payload(args, orders)
    endpoint = args.backend_url.rstrip("/") + "/shop-orders/ingest"
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json"}
    if args.backend_token:
        headers["Authorization"] = "Bearer " + args.backend_token
    req = urllib.request.Request(endpoint, data=body, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=60) as resp:
        result = json.loads(resp.read().decode("utf-8"))
    if result.get("code") != 200:
        raise RuntimeError(f"backend ingest failed: {result.get('message') or result}")
    data = result.get("data") or {}
    log(
        "backend ingest ok: "
        f"received={data.get('received', 0)} "
        f"inserted={data.get('inserted', 0)} "
        f"updated={data.get('updated', 0)} "
        f"rejected={data.get('rejected', 0)}"
    )


def load_existing_orders(output_dir: Path, shop_name: str) -> dict[str, dict[str, Any]]:
    """Load the latest deduplicated file keyed by order_id."""
    latest = output_dir / f"meituan_orders_{shop_name}.json"
    if not latest.exists():
        return {}
    try:
        with open(latest, "r", encoding="utf-8") as f:
            data = json.load(f)
        if isinstance(data, list):
            return {o["order_id"]: o for o in data if o.get("order_id")}
        if isinstance(data, dict) and "orders" in data:
            return {o["order_id"]: o for o in data["orders"] if o.get("order_id")}
    except Exception as e:
        log(f"failed to load existing orders: {e}")
    return {}


def save_orders(
    output_dir: Path,
    shop_name: str,
    orders: list[dict[str, Any]],
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)

    meta = {
        "shop_name": shop_name,
        "generated_at": datetime.now().isoformat(),
        "count": len(orders),
        "orders": orders,
    }

    # Timestamped snapshot
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    snap_path = output_dir / f"meituan_orders_{shop_name}_{ts}.json"
    with open(snap_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    log(f"snapshot saved: {snap_path}")

    # Latest deduplicated file
    latest_path = output_dir / f"meituan_orders_{shop_name}.json"
    with open(latest_path, "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    log(f"latest saved: {latest_path}")


def fetch_orders(args: argparse.Namespace) -> None:
    from playwright.sync_api import sync_playwright, TimeoutError as PWTimeout

    session = ensure_browser_session(args.control_url, args.phone, args.shop_id)
    debug_port = session["debugPort"]
    cdp_url = f"http://127.0.0.1:{debug_port}"

    p = sync_playwright().start()
    browser = p.chromium.connect_over_cdp(cdp_url)
    context = browser.contexts[0]
    page = context.pages[0] if context.pages else context.new_page()

    order_url = "https://waimaie.meituan.com/new_fe/orderbusiness#/order/history"
    try:
        page.goto(order_url, wait_until="domcontentloaded", timeout=30000)
        page.wait_for_timeout(3000)
    except PWTimeout:
        log("page load timeout, continuing anyway")

    # Dismiss new-order popup if present
    for popup_text in ["我知道了", "不再提示"]:
        for el in page.locator(f"text={popup_text}").all():
            try:
                if el.is_visible():
                    el.click()
                    page.wait_for_timeout(300)
            except Exception:
                pass

    # Wait until order cards appear
    try:
        page.locator("text=/#/").first.wait_for(timeout=15000)
    except PWTimeout:
        log("order list did not appear")
        browser.close()
        p.stop()
        return

    # Reveal phone/address for every visible order card
    order_labels = [el.inner_text().strip() for el in page.locator("text=/#/").all()]
    log(f"found order labels: {order_labels}")

    def reveal_order(label: str) -> None:
        """Scroll order card into view and click all '点击查看' reveal buttons."""
        try:
            page.locator(f"text={label}").first.evaluate(
                """el => {
                    let cur = el;
                    for (let i = 0; i < 10; i++) {
                        cur = cur.parentElement;
                        if (!cur) break;
                        const t = cur.innerText || '';
                        if (t.includes('顾客地址')) break;
                    }
                    if (!cur) return 0;
                    cur.scrollIntoView({block: 'center'});
                    const btns = cur.querySelectorAll('*');
                    let clicked = 0;
                    for (const b of btns) {
                        if ((b.innerText || '').trim() === '点击查看' && b.offsetParent !== null) {
                            b.click();
                            clicked++;
                        }
                    }
                    return clicked;
                }"""
            )
        except Exception as e:
            log(f"reveal click failed for {label}: {e}")

    for label in order_labels:
        reveal_order(label)
    page.wait_for_timeout(1500)

    # Second pass: some cards (e.g. at bottom) may not have revealed on first click
    for label in order_labels:
        card = page.locator(f"text={label}").first
        try:
            still_hidden = card.evaluate(
                """el => {
                    let cur = el;
                    for (let i = 0; i < 10; i++) {
                        cur = cur.parentElement;
                        if (!cur) break;
                        const t = cur.innerText || '';
                        if (t.includes('顾客地址')) break;
                    }
                    return cur ? (cur.innerText || '').includes('隐私号码|点击查看') || (cur.innerText || '').includes('顾客地址|点击查看') : false;
                }"""
            )
            if still_hidden:
                reveal_order(label)
        except Exception:
            pass
    page.wait_for_timeout(1500)

    # Extract each order card
    new_orders: list[dict[str, Any]] = []
    for label in order_labels:
        try:
            card_text = page.locator(f"text={label}").first.evaluate(
                """el => {
                    let cur = el;
                    for (let i = 0; i < 10; i++) {
                        cur = cur.parentElement;
                        if (!cur) break;
                        const t = cur.innerText || '';
                        if (t.includes('顾客地址')) break;
                    }
                    return cur ? cur.innerText : '';
                }"""
            )
            parsed = parse_order_card(card_text)
            if parsed and parsed.get("order_id"):
                new_orders.append(parsed)
            else:
                log(f"could not parse order {label}")
        except Exception as e:
            log(f"extract failed for {label}: {e}")

    browser.close()
    p.stop()

    # Deduplicate against existing latest file
    existing = load_existing_orders(args.output_dir, args.shop_name)
    for o in new_orders:
        existing[o["order_id"]] = o

    final_orders = sorted(existing.values(), key=lambda x: (x.get("order_time") or "", x.get("order_no") or ""), reverse=True)
    save_orders(args.output_dir, args.shop_name, final_orders)
    submit_orders(args, new_orders)
    log(f"total unique orders: {len(final_orders)}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Fetch Meituan merchant orders via ZR remote browser")
    parser.add_argument("--control-url", default=os.environ.get("ZR_CONTROL_URL", "http://127.0.0.1:14501"))
    parser.add_argument("--phone", default=os.environ.get("ZR_SHOP_PHONE", ""))
    parser.add_argument("--shop-id", default=os.environ.get("ZR_SHOP_ID", ""))
    parser.add_argument("--shop-name", default=os.environ.get("ZR_SHOP_NAME", "shop"))
    parser.add_argument("--output-dir", type=Path, default=Path(os.environ.get("OUTPUT_DIR", "/tmp")))
    parser.add_argument("--backend-url", default=os.environ.get("ZR_BACKEND_URL", ""))
    parser.add_argument("--backend-token", default=os.environ.get("ZR_BACKEND_TOKEN", ""))
    parser.add_argument("--system-shop-id", type=int, default=int(os.environ.get("ZR_SYSTEM_SHOP_ID", "0") or "0"))
    args = parser.parse_args()

    if not args.phone or not args.shop_id:
        log("--phone and --shop-id are required")
        return 1
    if args.backend_url and not args.system_shop_id:
        log("--system-shop-id is required when --backend-url is set")
        return 1

    fetch_orders(args)
    return 0


if __name__ == "__main__":
    sys.exit(main())
