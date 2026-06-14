#!/usr/bin/env python3
import argparse
import json
import os
import re
import subprocess
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

DEFAULT_AUTH_URL = "https://store.jddj.com/base/login"
DEFAULT_WINDOW_WIDTH = 360
DEFAULT_WINDOW_HEIGHT = 520
DEFAULT_BROWSER_SCALE = 1.25
MIN_WINDOW_WIDTH = 240
MIN_WINDOW_HEIGHT = 320
MAX_WINDOW_WIDTH = 1600
MAX_WINDOW_HEIGHT = 2400
MIN_BROWSER_SCALE = 0.8
MAX_BROWSER_SCALE = 2.0


def safe_segment(value: str, fallback: str) -> str:
    value = (value or "").strip()
    value = re.sub(r"[^A-Za-z0-9._-]+", "_", value)
    return value or fallback


def append_trace(trace_file: str, payload: dict) -> None:
    payload = {"ts": time.strftime("%Y-%m-%dT%H:%M:%S%z"), **payload}
    os.makedirs(os.path.dirname(trace_file), exist_ok=True)
    with open(trace_file, "a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n")


def parse_json_payload(output: str) -> dict | None:
    output = (output or "").strip()
    if not output:
        return None
    try:
        return json.loads(output)
    except json.JSONDecodeError:
        pass
    start = output.find("{")
    end = output.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        return json.loads(output[start:end + 1])
    except json.JSONDecodeError:
        return None


def clamp_int(value: str, fallback: int, minimum: int, maximum: int) -> int:
    try:
        parsed = int(value)
    except (TypeError, ValueError):
        return fallback
    return max(minimum, min(maximum, parsed))


def clamp_float(value: str, fallback: float, minimum: float, maximum: float) -> float:
    try:
        parsed = float(value)
    except (TypeError, ValueError):
        return fallback
    return max(minimum, min(maximum, parsed))


class BrowserControlHandler(BaseHTTPRequestHandler):
    server_version = "ZRBrowserControl/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.respond_json(200, {"ok": True})
            return
        if parsed.path != "/open":
            self.respond_json(404, {"ok": False, "error": "not_found"})
            return

        query = parse_qs(parsed.query)
        phone = safe_segment(query.get("phone", [""])[0], "unknown-phone")
        shop_id = safe_segment(query.get("shopId", [""])[0], "unknown-shop")
        url = query.get("url", [DEFAULT_AUTH_URL])[0].strip() or DEFAULT_AUTH_URL
        width = clamp_int(
            query.get("width", [str(DEFAULT_WINDOW_WIDTH)])[0],
            DEFAULT_WINDOW_WIDTH,
            MIN_WINDOW_WIDTH,
            MAX_WINDOW_WIDTH,
        )
        height = clamp_int(
            query.get("height", [str(DEFAULT_WINDOW_HEIGHT)])[0],
            DEFAULT_WINDOW_HEIGHT,
            MIN_WINDOW_HEIGHT,
            MAX_WINDOW_HEIGHT,
        )
        scale = clamp_float(
            query.get("scale", [str(self.server.browser_scale)])[0],
            self.server.browser_scale,
            MIN_BROWSER_SCALE,
            MAX_BROWSER_SCALE,
        )

        profile_dir = os.path.join(self.server.profile_root, phone, shop_id, "chrome")
        os.makedirs(profile_dir, exist_ok=True)
        if not self.ensure_display_size(width, height):
            self.respond_json(500, {"ok": False, "error": "display_resize_failed"})
            return

        append_trace(
            self.server.trace_file,
            {
                "event": "open_request",
                "phone": phone,
                "shopId": shop_id,
                "profileDir": profile_dir,
                "url": url,
                "width": width,
                "height": height,
                "scale": scale,
                "client": self.client_address[0],
            },
        )

        env = os.environ.copy()
        env.update(
            {
                "DISPLAY_ID": self.server.display_id,
                "ZR_USER_PHONE": phone,
                "ZR_SHOP_ID": shop_id,
                "ZR_AUTH_URL": url,
                "ZR_BASE_DIR": self.server.base_dir,
                "ZR_PROFILE_ROOT": self.server.profile_root,
                "ZR_TRACE_FILE": self.server.trace_file,
                "ZR_WINDOW_WIDTH": str(width),
                "ZR_WINDOW_HEIGHT": str(height),
                "ZR_BROWSER_SCALE": str(scale),
                "ZR_DEBUG_PORT": str(self.server.debug_port),
                "ZR_SKIP_CONTROL": "1",
            }
        )
        try:
            completed = subprocess.run(
                [self.server.browser_script],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=20,
                check=False,
            )
        except subprocess.TimeoutExpired:
            append_trace(
                self.server.trace_file,
                {
                    "event": "open_timeout",
                    "phone": phone,
                    "shopId": shop_id,
                    "profileDir": profile_dir,
                    "width": width,
                    "height": height,
                    "scale": scale,
                },
            )
            self.respond_json(504, {"ok": False, "error": "browser_start_timeout"})
            return

        ok = completed.returncode == 0
        alignment = self.align_login_form(env, phone, shop_id, profile_dir, width, height, scale) if ok else {
            "ok": False,
            "reason": "browser_start_failed",
        }
        append_trace(
            self.server.trace_file,
            {
                "event": "open_finished",
                "phone": phone,
                "shopId": shop_id,
                "profileDir": profile_dir,
                "returnCode": completed.returncode,
                "width": width,
                "height": height,
                "scale": scale,
                "alignment": alignment,
            },
        )
        self.respond_json(
            200 if ok else 500,
            {
                "ok": ok,
                "profileDir": profile_dir,
                "width": width,
                "height": height,
                "scale": scale,
                "alignment": alignment,
                "returnCode": completed.returncode,
                "output": completed.stdout[-2000:],
            },
        )

    def align_login_form(
        self,
        env: dict,
        phone: str,
        shop_id: str,
        profile_dir: str,
        width: int,
        height: int,
        scale: float,
    ) -> dict:
        aligner = getattr(self.server, "aligner_script", "")
        if not aligner or not os.path.exists(aligner):
            return {"ok": False, "reason": "aligner_missing"}
        append_trace(
            self.server.trace_file,
            {
                "event": "login_align_requested",
                "phone": phone,
                "shopId": shop_id,
                "profileDir": profile_dir,
                "width": width,
                "height": height,
                "scale": scale,
            },
        )
        try:
            completed = subprocess.run(
                [aligner],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=18,
                check=False,
            )
        except subprocess.TimeoutExpired:
            append_trace(
                self.server.trace_file,
                {
                    "event": "login_align_failed",
                    "phone": phone,
                    "shopId": shop_id,
                    "profileDir": profile_dir,
                    "reason": "timeout",
                },
            )
            return {"ok": False, "reason": "timeout"}
        payload = parse_json_payload(completed.stdout)
        if payload is None:
            payload = {
                "ok": False,
                "alignment": {
                    "reason": "invalid_aligner_output",
                    "returnCode": completed.returncode,
                    "output": completed.stdout[-2000:],
                },
            }
        alignment = payload.get("alignment", payload)
        alignment.setdefault("returnCode", completed.returncode)
        return alignment

    def ensure_display_size(self, width: int, height: int) -> bool:
        if self.server.current_width == width and self.server.current_height == height:
            return True
        append_trace(
            self.server.trace_file,
            {
                "event": "display_resize_requested",
                "fromWidth": self.server.current_width,
                "fromHeight": self.server.current_height,
                "width": width,
                "height": height,
            },
        )
        env = os.environ.copy()
        env.update(
            {
                "ZR_BASE_DIR": self.server.base_dir,
                "ZR_WINDOW_WIDTH": str(width),
                "ZR_WINDOW_HEIGHT": str(height),
            }
        )
        try:
            completed = subprocess.run(
                [self.server.display_script],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=35,
                check=False,
            )
        except subprocess.TimeoutExpired:
            append_trace(
                self.server.trace_file,
                {"event": "display_resize_timeout", "width": width, "height": height},
            )
            return False
        ok = completed.returncode == 0
        append_trace(
            self.server.trace_file,
            {
                "event": "display_resize_finished",
                "width": width,
                "height": height,
                "returnCode": completed.returncode,
                "output": completed.stdout[-2000:],
            },
        )
        if ok:
            self.server.current_width = width
            self.server.current_height = height
        return ok

    def respond_json(self, status: int, payload: dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, fmt: str, *args) -> None:
        append_trace(self.server.trace_file, {"event": "http_log", "message": fmt % args})


def main() -> None:
    parser = argparse.ArgumentParser(description="ZR remote browser controller")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=14501)
    parser.add_argument("--base-dir", default=os.path.expanduser("~/data"))
    parser.add_argument("--display-id", default=":19")
    parser.add_argument("--window-width", type=int, default=DEFAULT_WINDOW_WIDTH)
    parser.add_argument("--window-height", type=int, default=DEFAULT_WINDOW_HEIGHT)
    parser.add_argument("--browser-scale", type=float, default=DEFAULT_BROWSER_SCALE)
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), BrowserControlHandler)
    server.base_dir = args.base_dir
    server.profile_root = os.path.join(args.base_dir, "profiles")
    server.trace_file = os.path.join(args.base_dir, "logs", "browser-trace.jsonl")
    server.start_script = os.path.join(args.base_dir, "start-zr.sh")
    server.display_script = os.path.join(args.base_dir, "start-zr-display.sh")
    server.browser_script = os.path.join(args.base_dir, "start-zr-browser.sh")
    server.aligner_script = os.path.join(args.base_dir, "zr-login-align.js")
    server.display_id = args.display_id
    server.window_width = args.window_width
    server.window_height = args.window_height
    server.browser_scale = args.browser_scale
    server.debug_port = 14502
    server.current_width = args.window_width
    server.current_height = args.window_height
    os.makedirs(server.profile_root, exist_ok=True)
    append_trace(server.trace_file, {"event": "control_started", "host": args.host, "port": args.port})
    server.serve_forever()


if __name__ == "__main__":
    main()
