#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import re
import socket
import subprocess
import threading
import time
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse
from urllib.request import urlopen

DEFAULT_AUTH_URL = "https://store.jddj.com/base/login"
DEFAULT_WINDOW_WIDTH = 360
DEFAULT_WINDOW_HEIGHT = 520
DEFAULT_BROWSER_SCALE = 1.25
DEFAULT_RENDER_SCALE = 1.0
MIN_WINDOW_WIDTH = 240
MIN_WINDOW_HEIGHT = 320
MAX_WINDOW_WIDTH = 3840
MAX_WINDOW_HEIGHT = 2160
MIN_BROWSER_SCALE = 0.8
MAX_BROWSER_SCALE = 2.0
MIN_RENDER_SCALE = 1.0
MAX_RENDER_SCALE = 3.0
SESSION_SLOT_COUNT = 400
DISPLAY_BASE = 90
XPRA_PORT_BASE = 15000
VNC_PORT_BASE = 16000
DEBUG_PORT_BASE = 17000


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


def stable_slot(phone: str, shop_id: str) -> int:
    digest = hashlib.sha256(f"{phone}/{shop_id}".encode("utf-8")).hexdigest()
    return int(digest[:8], 16) % SESSION_SLOT_COUNT


def port_available(port: int) -> bool:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
        sock.settimeout(0.2)
        return sock.connect_ex(("127.0.0.1", port)) != 0


def load_slot_registry(path: str) -> dict[str, int]:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            raw = json.load(handle)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {}
    if not isinstance(raw, dict):
        return {}
    registry: dict[str, int] = {}
    for session_id, slot in raw.items():
        try:
            parsed_slot = int(slot)
        except (TypeError, ValueError):
            continue
        if 0 <= parsed_slot < SESSION_SLOT_COUNT:
            registry[str(session_id)] = parsed_slot
    return registry


def save_slot_registry(path: str, registry: dict[str, int]) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp_path = f"{path}.tmp"
    with open(tmp_path, "w", encoding="utf-8") as handle:
        json.dump(registry, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    os.replace(tmp_path, path)


def load_session_registry(path: str) -> dict:
    try:
        with open(path, "r", encoding="utf-8") as handle:
            raw = json.load(handle)
    except (FileNotFoundError, json.JSONDecodeError, OSError):
        return {}
    return raw if isinstance(raw, dict) else {}


def save_session_registry(path: str, registry: dict) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp_path = f"{path}.tmp"
    with open(tmp_path, "w", encoding="utf-8") as handle:
        json.dump(registry, handle, ensure_ascii=False, sort_keys=True, indent=2)
    os.replace(tmp_path, path)


def debug_port_alive(debug_port: int) -> bool:
    try:
        with urlopen(f"http://127.0.0.1:{debug_port}/json/version", timeout=1.5) as response:
            payload = json.loads(response.read().decode("utf-8"))
            return bool(payload.get("webSocketDebuggerUrl"))
    except Exception:
        return False


def xpra_stream_alive(xpra_port: int, timeout: float = 1.5) -> bool:
    try:
        with urlopen(f"http://127.0.0.1:{xpra_port}/", timeout=timeout) as response:
            return response.status == 200
    except Exception:
        return False


def current_debug_page_url(debug_port: int) -> str | None:
    try:
        with urlopen(f"http://127.0.0.1:{debug_port}/json/list", timeout=1.5) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except Exception:
        return None
    if not isinstance(payload, list):
        return None
    for page in payload:
        if not isinstance(page, dict):
            continue
        url = str(page.get("url") or "")
        if url and not url.startswith("about:") and not url.startswith("devtools:"):
            return url
    return None


def equivalent_page_url(current_url: str | None, target_url: str) -> bool:
    if not current_url:
        return False
    return current_url.rstrip("/") == target_url.rstrip("/")


def active_chrome_matches_profile(profile_dir: str, debug_port: int) -> bool:
    try:
        completed = subprocess.run(
            ["ps", "-eo", "args="],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=2,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return False
    user_data_arg = f"--user-data-dir={profile_dir}"
    debug_arg = f"--remote-debugging-port={debug_port}"
    return any(
        "chrome-linux/chrome" in line
        and user_data_arg in line
        and debug_arg in line
        for line in completed.stdout.splitlines()
    )


def kill_chrome_for_profile(profile_dir: str, debug_port: int | None = None) -> None:
    try:
        completed = subprocess.run(
            ["ps", "-eo", "pid=,args="],
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            text=True,
            timeout=2,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return
    user_data_arg = f"--user-data-dir={profile_dir}"
    debug_arg = f"--remote-debugging-port={debug_port}" if debug_port is not None else None
    pids: list[str] = []
    for line in completed.stdout.splitlines():
        stripped = line.strip()
        if not stripped:
            continue
        parts = stripped.split(maxsplit=1)
        if len(parts) != 2:
            continue
        pid, args = parts
        if "chrome" not in args or user_data_arg not in args:
            continue
        if debug_arg is not None and debug_arg not in args:
            continue
        pids.append(pid)
    if pids:
        subprocess.run(["kill", *pids], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


@dataclass
class BrowserSession:
    phone: str
    shop_id: str
    session_id: str
    display_id: str
    xpra_port: int
    vnc_port: int
    debug_port: int
    profile_dir: str


class BrowserControlHandler(BaseHTTPRequestHandler):
    server_version = "ZRBrowserControl/1.0"

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.respond_json(200, {"ok": True})
            return
        if parsed.path == "/probe":
            self.handle_probe(parse_qs(parsed.query))
            return
        if parsed.path != "/open":
            self.respond_json(404, {"ok": False, "error": "not_found"})
            return

        query = parse_qs(parsed.query)
        phone = safe_segment(query.get("phone", [""])[0], "unknown-phone")
        shop_id = safe_segment(query.get("shopId", [""])[0], "unknown-shop")
        session = self.browser_session(phone, shop_id)
        url = query.get("url", [DEFAULT_AUTH_URL])[0].strip() or DEFAULT_AUTH_URL
        mode = query.get("mode", ["authorization-login"])[0].strip() or "authorization-login"
        viewport_width = clamp_int(
            query.get("width", [str(DEFAULT_WINDOW_WIDTH)])[0],
            DEFAULT_WINDOW_WIDTH,
            MIN_WINDOW_WIDTH,
            MAX_WINDOW_WIDTH,
        )
        viewport_height = clamp_int(
            query.get("height", [str(DEFAULT_WINDOW_HEIGHT)])[0],
            DEFAULT_WINDOW_HEIGHT,
            MIN_WINDOW_HEIGHT,
            MAX_WINDOW_HEIGHT,
        )
        render_scale = clamp_float(
            query.get("renderScale", [str(DEFAULT_RENDER_SCALE)])[0],
            DEFAULT_RENDER_SCALE,
            MIN_RENDER_SCALE,
            MAX_RENDER_SCALE,
        )
        render_width = clamp_int(
            query.get("renderWidth", [str(round(viewport_width * render_scale))])[0],
            round(viewport_width * render_scale),
            MIN_WINDOW_WIDTH,
            MAX_WINDOW_WIDTH,
        )
        render_height = clamp_int(
            query.get("renderHeight", [str(round(viewport_height * render_scale))])[0],
            round(viewport_height * render_scale),
            MIN_WINDOW_HEIGHT,
            MAX_WINDOW_HEIGHT,
        )
        scale = clamp_float(
            query.get("scale", [str(self.server.browser_scale)])[0],
            self.server.browser_scale,
            MIN_BROWSER_SCALE,
            MAX_BROWSER_SCALE,
        )

        with self.session_lock(session):
            os.makedirs(session.profile_dir, exist_ok=True)
            display_ok, display_recreated = self.ensure_display_size(session, render_width, render_height)
            if not display_ok:
                self.respond_json(500, {"ok": False, "error": "display_resize_failed"})
                return
            if display_recreated:
                kill_chrome_for_profile(session.profile_dir, session.debug_port)
                time.sleep(1)
            current_url = current_debug_page_url(session.debug_port)
            reused = (
                (not display_recreated)
                and self.session_browser_alive(session)
                and equivalent_page_url(current_url, url)
            )
            if (
                not display_recreated
                and current_url is not None
                and self.session_browser_alive(session)
                and not equivalent_page_url(current_url, url)
            ):
                append_trace(
                    self.server.trace_file,
                    {
                        "event": "open_reuse_rejected",
                        "phone": phone,
                        "shopId": shop_id,
                        "sessionId": session.session_id,
                        "displayId": session.display_id,
                        "debugPort": session.debug_port,
                        "currentUrl": current_url,
                        "targetUrl": url,
                        "mode": mode,
                    },
                )
                kill_chrome_for_profile(session.profile_dir, session.debug_port)
                time.sleep(1)
            if reused:
                self.pin_existing_window(session, render_width, render_height)
                stream_ready = self.wait_for_stream(session)
                if not stream_ready:
                    self.respond_json(502, {"ok": False, "ready": False, "error": "stream_not_ready"})
                    return
                self.record_session(
                    session,
                    url,
                    render_width,
                    render_height,
                    viewport_width,
                    viewport_height,
                    render_scale,
                    scale,
                    "reused",
                )
                append_trace(
                    self.server.trace_file,
                    {
                        "event": "open_reused",
                        "phone": phone,
                        "shopId": shop_id,
                        "sessionId": session.session_id,
                        "displayId": session.display_id,
                        "xpraPort": session.xpra_port,
                        "debugPort": session.debug_port,
                        "profileDir": session.profile_dir,
                        "url": url,
                        "width": render_width,
                        "height": render_height,
                        "viewportWidth": viewport_width,
                        "viewportHeight": viewport_height,
                        "renderScale": render_scale,
                        "scale": scale,
                        "mode": mode,
                        "client": self.client_address[0],
                    },
                )
                self.respond_json(
                    200,
                    self.open_response(
                        True,
                        True,
                        session,
                        render_width,
                        render_height,
                        viewport_width,
                        viewport_height,
                        render_scale,
                        scale,
                        {"ok": True, "reason": "reused"},
                        0,
                        "reused_existing_browser",
                    ),
                )
                return

            append_trace(
                self.server.trace_file,
                {
                    "event": "open_request",
                    "phone": phone,
                    "shopId": shop_id,
                    "sessionId": session.session_id,
                    "displayId": session.display_id,
                    "xpraPort": session.xpra_port,
                    "debugPort": session.debug_port,
                    "profileDir": session.profile_dir,
                    "url": url,
                    "width": render_width,
                    "height": render_height,
                    "viewportWidth": viewport_width,
                    "viewportHeight": viewport_height,
                    "renderWidth": render_width,
                    "renderHeight": render_height,
                    "renderScale": render_scale,
                    "scale": scale,
                    "mode": mode,
                    "client": self.client_address[0],
                },
            )

            env = os.environ.copy()
            env.update(
                {
                    "DISPLAY_ID": session.display_id,
                    "ZR_SESSION_ID": session.session_id,
                    "ZR_USER_PHONE": phone,
                    "ZR_SHOP_ID": shop_id,
                    "ZR_AUTH_URL": url,
                    "ZR_BASE_DIR": self.server.base_dir,
                    "ZR_PROFILE_ROOT": self.server.profile_root,
                    "ZR_TRACE_FILE": self.server.trace_file,
                    "ZR_WINDOW_WIDTH": str(render_width),
                    "ZR_WINDOW_HEIGHT": str(render_height),
                    "ZR_VIEWPORT_WIDTH": str(viewport_width),
                    "ZR_VIEWPORT_HEIGHT": str(viewport_height),
                    "ZR_RENDER_WIDTH": str(render_width),
                    "ZR_RENDER_HEIGHT": str(render_height),
                    "ZR_RENDER_SCALE": str(render_scale),
                    "ZR_BROWSER_SCALE": str(scale),
                    "ZR_DEBUG_PORT": str(session.debug_port),
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
                        "sessionId": session.session_id,
                        "profileDir": session.profile_dir,
                        "width": render_width,
                        "height": render_height,
                        "viewportWidth": viewport_width,
                        "viewportHeight": viewport_height,
                        "renderWidth": render_width,
                        "renderHeight": render_height,
                        "renderScale": render_scale,
                        "scale": scale,
                    },
                )
                self.respond_json(504, {"ok": False, "error": "browser_start_timeout"})
                return

            ok = completed.returncode == 0
            stream_ready = self.wait_for_stream(session) if ok else False
            if ok:
                self.record_session(
                    session,
                    url,
                    render_width,
                    render_height,
                    viewport_width,
                    viewport_height,
                    render_scale,
                    scale,
                    "started",
                )
            alignment = self.align_login_form(
                env,
                phone,
                shop_id,
                session.profile_dir,
                viewport_width,
                viewport_height,
                render_width,
                render_height,
                scale,
            ) if ok and mode == "authorization-login" else {
                "ok": False,
                "reason": "remote_backend_no_login_alignment" if ok else "browser_start_failed",
            }
            ready = ok and stream_ready
            append_trace(
                self.server.trace_file,
                {
                    "event": "open_finished",
                    "phone": phone,
                    "shopId": shop_id,
                    "sessionId": session.session_id,
                    "displayId": session.display_id,
                    "xpraPort": session.xpra_port,
                    "debugPort": session.debug_port,
                    "profileDir": session.profile_dir,
                    "returnCode": completed.returncode,
                    "ready": ready,
                    "streamReady": stream_ready,
                    "width": render_width,
                    "height": render_height,
                    "viewportWidth": viewport_width,
                    "viewportHeight": viewport_height,
                    "renderWidth": render_width,
                    "renderHeight": render_height,
                    "renderScale": render_scale,
                    "scale": scale,
                    "alignment": alignment,
                },
            )
            self.respond_json(
                200 if ready else 502,
                self.open_response(
                    ready,
                    ready,
                    session,
                    render_width,
                    render_height,
                    viewport_width,
                    viewport_height,
                    render_scale,
                    scale,
                    alignment,
                    completed.returncode,
                    completed.stdout[-2000:],
                ),
            )

    def handle_probe(self, query: dict) -> None:
        phone = safe_segment(query.get("phone", [""])[0], "unknown-phone")
        shop_id = safe_segment(query.get("shopId", [""])[0], "unknown-shop")
        session = self.browser_session(phone, shop_id)
        url = query.get("url", [DEFAULT_AUTH_URL])[0].strip() or DEFAULT_AUTH_URL
        env = os.environ.copy()
        env.update(
            {
                "ZR_AUTH_URL": url,
                "ZR_PROFILE_DIR": session.profile_dir,
                "ZR_TRACE_FILE": self.server.trace_file,
                "ZR_DEBUG_PORT": str(session.debug_port),
            }
        )
        append_trace(
            self.server.trace_file,
            {
                "event": "authorization_probe_requested",
                "phone": phone,
                "shopId": shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "debugPort": session.debug_port,
                "profileDir": session.profile_dir,
                "url": url,
                "client": self.client_address[0],
            },
        )
        if not os.path.exists(session.profile_dir):
            self.respond_json(
                200,
                {
                    "ok": True,
                    "status": "UNAUTHORIZED",
                    "confidence": "HIGH",
                    "signals": {
                        "sessionId": session.session_id,
                        "displayId": session.display_id,
                        "streamPort": session.xpra_port,
                        "debugPort": session.debug_port,
                        "profilePath": session.profile_dir,
                        "reason": "profile_missing",
                    },
                },
            )
            return
        with self.session_lock(session):
            if not (debug_port_alive(session.debug_port) and active_chrome_matches_profile(session.profile_dir, session.debug_port)):
                bootstrap = self.bootstrap_probe_browser(session, url)
                if not bootstrap.get("ok"):
                    self.respond_json(
                        500,
                        {
                            "ok": False,
                            "status": "FAILED",
                            "confidence": "LOW",
                            "error": bootstrap.get("error", "probe_browser_bootstrap_failed"),
                            "signals": {
                                "sessionId": session.session_id,
                                "displayId": session.display_id,
                                "streamPort": session.xpra_port,
                                "debugPort": session.debug_port,
                                "profilePath": session.profile_dir,
                                "bootstrap": bootstrap,
                            },
                        },
                    )
                    return
        try:
            completed = subprocess.run(
                [self.server.probe_script],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=25,
                check=False,
            )
        except subprocess.TimeoutExpired:
            self.respond_json(504, {"ok": False, "status": "FAILED", "error": "probe_timeout"})
            return
        payload = parse_json_payload(completed.stdout) or {
            "ok": False,
            "status": "FAILED",
            "error": "invalid_probe_output",
            "output": completed.stdout[-2000:],
        }
        signals = payload.get("signals")
        if not isinstance(signals, dict):
            signals = {}
            payload["signals"] = signals
        signals["sessionId"] = session.session_id
        signals["displayId"] = session.display_id
        signals["streamPort"] = session.xpra_port
        signals["debugPort"] = session.debug_port
        signals["profilePath"] = session.profile_dir
        payload.setdefault("returnCode", completed.returncode)
        self.respond_json(200 if payload.get("ok") else 500, payload)

    def bootstrap_probe_browser(self, session: BrowserSession, url: str) -> dict:
        append_trace(
            self.server.trace_file,
            {
                "event": "authorization_probe_bootstrap_requested",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "debugPort": session.debug_port,
                "profileDir": session.profile_dir,
                "url": url,
            },
        )
        display_ok, display_recreated = self.ensure_display_size(
            session,
            DEFAULT_WINDOW_WIDTH,
            DEFAULT_WINDOW_HEIGHT,
        )
        if not display_ok:
            return {"ok": False, "error": "display_unavailable"}
        if display_recreated:
            kill_chrome_for_profile(session.profile_dir, session.debug_port)
            time.sleep(1)
        env = os.environ.copy()
        env.update(
            {
                "DISPLAY_ID": session.display_id,
                "ZR_SESSION_ID": session.session_id,
                "ZR_USER_PHONE": session.phone,
                "ZR_SHOP_ID": session.shop_id,
                "ZR_AUTH_URL": url,
                "ZR_BASE_DIR": self.server.base_dir,
                "ZR_PROFILE_ROOT": self.server.profile_root,
                "ZR_TRACE_FILE": self.server.trace_file,
                "ZR_WINDOW_WIDTH": str(DEFAULT_WINDOW_WIDTH),
                "ZR_WINDOW_HEIGHT": str(DEFAULT_WINDOW_HEIGHT),
                "ZR_VIEWPORT_WIDTH": str(DEFAULT_WINDOW_WIDTH),
                "ZR_VIEWPORT_HEIGHT": str(DEFAULT_WINDOW_HEIGHT),
                "ZR_RENDER_WIDTH": str(DEFAULT_WINDOW_WIDTH),
                "ZR_RENDER_HEIGHT": str(DEFAULT_WINDOW_HEIGHT),
                "ZR_RENDER_SCALE": str(DEFAULT_RENDER_SCALE),
                "ZR_BROWSER_SCALE": str(self.server.browser_scale),
                "ZR_DEBUG_PORT": str(session.debug_port),
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
                    "event": "authorization_probe_bootstrap_failed",
                    "phone": session.phone,
                    "shopId": session.shop_id,
                    "sessionId": session.session_id,
                    "reason": "browser_start_timeout",
                },
            )
            return {"ok": False, "error": "browser_start_timeout"}
        ok = (
            completed.returncode == 0
            and debug_port_alive(session.debug_port)
            and active_chrome_matches_profile(session.profile_dir, session.debug_port)
        )
        stream_ready = self.wait_for_stream(session, attempts=6) if completed.returncode == 0 else False
        if ok:
            self.record_session(
                session,
                url,
                DEFAULT_WINDOW_WIDTH,
                DEFAULT_WINDOW_HEIGHT,
                DEFAULT_WINDOW_WIDTH,
                DEFAULT_WINDOW_HEIGHT,
                DEFAULT_RENDER_SCALE,
                self.server.browser_scale,
                "probe-started",
            )
        append_trace(
            self.server.trace_file,
            {
                "event": "authorization_probe_bootstrap_finished",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "debugPort": session.debug_port,
                "returnCode": completed.returncode,
                "ok": ok,
                "streamReady": stream_ready,
            },
        )
        return {
            "ok": ok,
            "returnCode": completed.returncode,
            "streamReady": stream_ready,
            "output": completed.stdout[-2000:],
        }

    def align_login_form(
        self,
        env: dict,
        phone: str,
        shop_id: str,
        profile_dir: str,
        viewport_width: int,
        viewport_height: int,
        render_width: int,
        render_height: int,
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
                "width": render_width,
                "height": render_height,
                "viewportWidth": viewport_width,
                "viewportHeight": viewport_height,
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
        alignment.setdefault("viewportWidth", viewport_width)
        alignment.setdefault("viewportHeight", viewport_height)
        alignment.setdefault("renderWidth", render_width)
        alignment.setdefault("renderHeight", render_height)
        return alignment

    def browser_session(self, phone: str, shop_id: str) -> BrowserSession:
        session_id = f"{phone}_{shop_id}"
        profile_dir = os.path.join(self.server.profile_root, phone, shop_id, "chrome")
        with self.server.locks_guard:
            slot = self.server.session_slots.get(session_id)
            if slot is None:
                start_slot = stable_slot(phone, shop_id)
                slot = start_slot
                for offset in range(SESSION_SLOT_COUNT):
                    candidate = (start_slot + offset) % SESSION_SLOT_COUNT
                    owner = self.server.slot_owners.get(candidate)
                    xpra_port = XPRA_PORT_BASE + candidate
                    debug_port = DEBUG_PORT_BASE + candidate
                    ports_free = port_available(xpra_port) and port_available(debug_port)
                    ports_owned_by_profile = active_chrome_matches_profile(profile_dir, debug_port)
                    if owner in (None, session_id) and (owner == session_id or ports_free or ports_owned_by_profile):
                        slot = candidate
                        self.server.session_slots[session_id] = slot
                        self.server.slot_owners[slot] = session_id
                        save_slot_registry(self.server.session_slot_file, self.server.session_slots)
                        break
                else:
                    self.server.session_slots[session_id] = slot
                    self.server.slot_owners[slot] = session_id
                    save_slot_registry(self.server.session_slot_file, self.server.session_slots)
        return BrowserSession(
            phone=phone,
            shop_id=shop_id,
            session_id=session_id,
            display_id=f":{DISPLAY_BASE + slot}",
            xpra_port=XPRA_PORT_BASE + slot,
            vnc_port=VNC_PORT_BASE + slot,
            debug_port=DEBUG_PORT_BASE + slot,
            profile_dir=profile_dir,
        )

    def session_lock(self, session: BrowserSession):
        with self.server.locks_guard:
            lock = self.server.session_locks.get(session.session_id)
            if lock is None:
                lock = threading.Lock()
                self.server.session_locks[session.session_id] = lock
            return lock

    def session_browser_alive(self, session: BrowserSession) -> bool:
        return (
            debug_port_alive(session.debug_port)
            and active_chrome_matches_profile(session.profile_dir, session.debug_port)
            and xpra_stream_alive(session.xpra_port)
        )

    def pin_existing_window(self, session: BrowserSession, width: int, height: int) -> None:
        env = os.environ.copy()
        env["DISPLAY"] = session.display_id
        subprocess.run(
            ["xdotool", "search", "--onlyvisible", "--class", "Chromium", "windowmove", "0", "0",
             "windowsize", str(width), str(height), "windowactivate"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            timeout=3,
            check=False,
        )

    def record_session(
        self,
        session: BrowserSession,
        url: str,
        render_width: int,
        render_height: int,
        viewport_width: int,
        viewport_height: int,
        render_scale: float,
        scale: float,
        state: str,
    ) -> None:
        with self.server.locks_guard:
            registry = self.server.session_registry
            registry[session.session_id] = {
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "profileDir": session.profile_dir,
                "displayId": session.display_id,
                "streamPort": session.xpra_port,
                "vncPort": session.vnc_port,
                "debugPort": session.debug_port,
                "url": url,
                "width": render_width,
                "height": render_height,
                "viewportWidth": viewport_width,
                "viewportHeight": viewport_height,
                "renderScale": render_scale,
                "scale": scale,
                "state": state,
                "updatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
            }
            save_session_registry(self.server.session_registry_file, registry)

    def open_response(
        self,
        ok: bool,
        ready: bool,
        session: BrowserSession,
        render_width: int,
        render_height: int,
        viewport_width: int,
        viewport_height: int,
        render_scale: float,
        scale: float,
        alignment: dict,
        return_code: int,
        output: str,
    ) -> dict:
        return {
            "ok": ok,
            "ready": ready,
            "profileDir": session.profile_dir,
            "streamPort": session.xpra_port,
            "streamPath": f"/zr-stream/{session.xpra_port}/",
            "streamUrl": f"/zr-stream/{session.xpra_port}/",
            "directStreamUrl": f"http://{self.server.public_host}:{session.xpra_port}/",
            "displayId": session.display_id,
            "debugPort": session.debug_port,
            "width": render_width,
            "height": render_height,
            "viewportWidth": viewport_width,
            "viewportHeight": viewport_height,
            "renderWidth": render_width,
            "renderHeight": render_height,
            "renderScale": render_scale,
            "scale": scale,
            "alignment": alignment,
            "returnCode": return_code,
            "output": output,
        }

    def wait_for_stream(self, session: BrowserSession, attempts: int = 8) -> bool:
        for attempt in range(1, attempts + 1):
            if xpra_stream_alive(session.xpra_port):
                append_trace(
                    self.server.trace_file,
                    {
                        "event": "stream_ready",
                        "phone": session.phone,
                        "shopId": session.shop_id,
                        "sessionId": session.session_id,
                        "xpraPort": session.xpra_port,
                        "attempt": attempt,
                    },
                )
                return True
            time.sleep(0.5)
        append_trace(
            self.server.trace_file,
            {
                "event": "stream_not_ready",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "xpraPort": session.xpra_port,
                "attempts": attempts,
            },
        )
        return False

    def read_display_size(self, display_id: str) -> dict:
        env = os.environ.copy()
        env["DISPLAY"] = display_id
        try:
            completed = subprocess.run(
                ["xrandr", "-q"],
                env=env,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                timeout=3,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as error:
            return {"ok": False, "error": str(error), "output": ""}
        output = completed.stdout or ""
        match = re.search(r"current\s+(\d+)\s+x\s+(\d+),\s+maximum\s+(\d+)\s+x\s+(\d+)", output)
        if not match:
            return {"ok": False, "returnCode": completed.returncode, "output": output[-2000:]}
        return {
            "ok": completed.returncode == 0,
            "width": int(match.group(1)),
            "height": int(match.group(2)),
            "maxWidth": int(match.group(3)),
            "maxHeight": int(match.group(4)),
            "returnCode": completed.returncode,
            "output": output[-2000:],
        }

    def run_display_script(self, session: BrowserSession, width: int, height: int, force_recreate: bool) -> subprocess.CompletedProcess:
        env = os.environ.copy()
        env.update(
            {
                "ZR_BASE_DIR": self.server.base_dir,
                "ZR_SESSION_ID": session.session_id,
                "DISPLAY_ID": session.display_id,
                "ZR_XPRA_PORT": str(session.xpra_port),
                "ZR_VNC_PORT": str(session.vnc_port),
                "ZR_WINDOW_WIDTH": str(width),
                "ZR_WINDOW_HEIGHT": str(height),
                "ZR_FORCE_DISPLAY_RECREATE": "1" if force_recreate else "0",
            }
        )
        return subprocess.run(
            [self.server.display_script],
            env=env,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            timeout=45 if force_recreate else 35,
            check=False,
        )

    def ensure_display_size(self, session: BrowserSession, width: int, height: int) -> tuple[bool, bool]:
        current_size = self.server.session_sizes.get(session.session_id)
        actual_before = self.read_display_size(session.display_id)
        if (
            current_size == (width, height)
            and actual_before.get("width") == width
            and actual_before.get("height") == height
            and xpra_stream_alive(session.xpra_port)
        ):
            return True, False
        append_trace(
            self.server.trace_file,
            {
                "event": "display_resize_requested",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "xpraPort": session.xpra_port,
                "vncPort": session.vnc_port,
                "fromWidth": current_size[0] if current_size else None,
                "fromHeight": current_size[1] if current_size else None,
                "actualBefore": actual_before,
                "width": width,
                "height": height,
            },
        )
        try:
            completed = self.run_display_script(session, width, height, False)
        except subprocess.TimeoutExpired:
            append_trace(
                self.server.trace_file,
                {
                    "event": "display_resize_timeout",
                    "phone": session.phone,
                    "shopId": session.shop_id,
                    "sessionId": session.session_id,
                    "width": width,
                    "height": height,
                },
            )
            return False, False
        actual_after = self.read_display_size(session.display_id)
        size_ok = (
            completed.returncode == 0
            and actual_after.get("width") == width
            and actual_after.get("height") == height
            and xpra_stream_alive(session.xpra_port)
        )
        append_trace(
            self.server.trace_file,
            {
                "event": "display_resize_finished",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "xpraPort": session.xpra_port,
                "vncPort": session.vnc_port,
                "width": width,
                "height": height,
                "returnCode": completed.returncode,
                "actualAfter": actual_after,
                "output": completed.stdout[-2000:],
            },
        )
        implicit_recreate = "display_recreated=1" in (completed.stdout or "")
        if size_ok:
            self.server.session_sizes[session.session_id] = (width, height)
            return True, implicit_recreate

        append_trace(
            self.server.trace_file,
            {
                "event": "display_recreate_requested",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "xpraPort": session.xpra_port,
                "vncPort": session.vnc_port,
                "width": width,
                "height": height,
                "previousReturnCode": completed.returncode,
                "actualBeforeRecreate": actual_after,
            },
        )
        try:
            recreated = self.run_display_script(session, width, height, True)
        except subprocess.TimeoutExpired:
            append_trace(
                self.server.trace_file,
                {
                    "event": "display_recreate_timeout",
                    "phone": session.phone,
                    "shopId": session.shop_id,
                    "sessionId": session.session_id,
                    "width": width,
                    "height": height,
                },
            )
            return False, True
        actual_recreated = self.read_display_size(session.display_id)
        recreate_ok = (
            recreated.returncode == 0
            and actual_recreated.get("width") == width
            and actual_recreated.get("height") == height
            and xpra_stream_alive(session.xpra_port)
        )
        append_trace(
            self.server.trace_file,
            {
                "event": "display_recreate_finished",
                "phone": session.phone,
                "shopId": session.shop_id,
                "sessionId": session.session_id,
                "displayId": session.display_id,
                "xpraPort": session.xpra_port,
                "vncPort": session.vnc_port,
                "width": width,
                "height": height,
                "returnCode": recreated.returncode,
                "actualAfter": actual_recreated,
                "output": recreated.stdout[-2000:],
            },
        )
        if recreate_ok:
            self.server.session_sizes[session.session_id] = (width, height)
        return recreate_ok, True

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
    parser.add_argument("--public-host", default=os.environ.get("ZR_PUBLIC_HOST", "47.112.170.106"))
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), BrowserControlHandler)
    server.base_dir = args.base_dir
    server.profile_root = os.path.join(args.base_dir, "profiles")
    server.trace_file = os.path.join(args.base_dir, "logs", "browser-trace.jsonl")
    server.session_slot_file = os.path.join(args.base_dir, "sessions", "slots.json")
    server.session_registry_file = os.path.join(args.base_dir, "sessions", "registry.json")
    server.start_script = os.path.join(args.base_dir, "start-zr.sh")
    server.display_script = os.path.join(args.base_dir, "start-zr-display.sh")
    server.browser_script = os.path.join(args.base_dir, "start-zr-browser.sh")
    server.aligner_script = os.path.join(args.base_dir, "zr-login-align.js")
    server.probe_script = os.path.join(args.base_dir, "zr-auth-probe.js")
    server.display_id = args.display_id
    server.window_width = args.window_width
    server.window_height = args.window_height
    server.browser_scale = args.browser_scale
    server.public_host = args.public_host
    server.session_sizes = {}
    server.session_locks = {}
    server.session_slots = load_slot_registry(server.session_slot_file)
    server.slot_owners = {slot: session_id for session_id, slot in server.session_slots.items()}
    server.session_registry = load_session_registry(server.session_registry_file)
    server.locks_guard = threading.Lock()
    os.makedirs(server.profile_root, exist_ok=True)
    append_trace(server.trace_file, {"event": "control_started", "host": args.host, "port": args.port})
    server.serve_forever()


if __name__ == "__main__":
    main()
