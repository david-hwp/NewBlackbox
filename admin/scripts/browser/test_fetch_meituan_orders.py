#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from fetch_meituan_orders import (
    build_ingest_payload,
    order_to_ingest_item,
    parse_order_card,
    submit_orders,
)


ORDER_CARD_TEXT = """
#9
06-24 12:45前送达
用户已收餐
张三
门店新客
下单1次
隐私号码
13800000000 转 1234
备用号码
13900000000 转 5678
顾客电话
手机尾号1234
顾客地址
深圳市南山区科技园
预计收入
￥33.50
订单编号：1802176573697106215
"""


def test_parse_order_card_extracts_meituan_fields() -> dict:
    order = parse_order_card(ORDER_CARD_TEXT)

    assert order is not None
    assert order["order_no"] == "9"
    assert order["order_id"] == "1802176573697106215"
    assert order["order_time"] == "06-24 12:45"
    assert order["order_time_label"] == "前送达"
    assert order["customer_name"] == "张三"
    assert order["privacy_phone"] == "13800000000 转 1234"
    assert order["backup_phone"] == "13900000000 转 5678"
    assert order["customer_phone_tail"] == "1234"
    assert order["address"] == "深圳市南山区科技园"
    assert order["status"] == "用户已收餐"
    assert order["estimated_income"] == 33.5
    return order


def test_order_to_ingest_item_maps_completed_order(order: dict) -> dict:
    order["cookie"] = "secret"
    item = order_to_ingest_item(order, base_year=2026)

    assert item["platform_order_id"] == "1802176573697106215"
    assert item["platform_order_no"] == "9"
    assert item["expected_delivery_at"] == "2026-06-24T12:45:00"
    assert item["completed_at"] == "2026-06-24T12:45:00"
    assert item["status"] == "用户已收餐"
    assert item["estimated_income"] == 33.5
    assert item["customer_name"] == "张三"
    assert item["raw_payload"]["order_id"] == "1802176573697106215"
    assert "cookie" not in item["raw_payload"]
    return item


def test_build_ingest_payload(order: dict) -> dict:
    args = argparse.Namespace(system_shop_id=194)
    payload = build_ingest_payload(args, [order])

    assert payload["shopId"] == 194
    assert payload["source"] == "fetch_meituan_orders"
    assert payload["orders"][0]["platform_order_id"] == "1802176573697106215"
    return payload


def test_submit_orders_posts_backend_ingest(order: dict) -> None:
    captured = {}

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *_args):
            pass

        def do_POST(self):  # noqa: N802
            length = int(self.headers.get("Content-Length", "0"))
            captured["path"] = self.path
            captured["authorization"] = self.headers.get("Authorization")
            captured["body"] = json.loads(self.rfile.read(length).decode("utf-8"))
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(
                json.dumps(
                    {
                        "code": 200,
                        "data": {"received": 1, "inserted": 1, "updated": 0, "rejected": 0},
                    }
                ).encode("utf-8")
            )

    server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        args = argparse.Namespace(
            backend_url=f"http://127.0.0.1:{server.server_port}/api",
            backend_token="admin-token",
            system_shop_id=194,
        )
        submit_orders(args, [order])
    finally:
        server.shutdown()
        thread.join(timeout=5)

    assert captured["path"] == "/api/shop-orders/ingest"
    assert captured["authorization"] == "Bearer admin-token"
    assert captured["body"]["shopId"] == 194
    assert captured["body"]["orders"][0]["platform_order_id"] == "1802176573697106215"


def main() -> None:
    order = test_parse_order_card_extracts_meituan_fields()
    test_order_to_ingest_item_maps_completed_order(order)
    test_build_ingest_payload(order)
    test_submit_orders_posts_backend_ingest(order)
    print("fetch_meituan_orders parser and submit tests passed")


if __name__ == "__main__":
    main()
