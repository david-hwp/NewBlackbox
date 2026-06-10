#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ALLOWED_KEYS = {
    "JOB_ID",
    "CHANNEL_CODE",
    "APP_APPLICATION_ID",
    "ENGINE_APPLICATION_ID",
    "ENGINE_DATA_ROOT_NAME",
    "API_BASE_URL",
    "APP_NAME",
    "ENGINE_NAME",
    "SOURCE_RELEASE_BRANCH",
    "CHANNEL_RELEASE_BRANCH",
    "APP_VERSION_NAME",
    "APP_VERSION_CODE",
    "ENGINE_VERSION_NAME",
    "ENGINE_VERSION_CODE",
    "REPO_URL",
    "BACKEND_BASE_URL",
    "PROGRESS_CALLBACK_URL",
    "COMPLETE_CALLBACK_URL",
    "JOB_TOKEN",
    "ADMIN_AUTHORIZATION_TOKEN",
    "DRY_RUN",
    "PUSH_RELEASE_BRANCH",
    "LOCK_DIR",
    "WORK_ROOT",
}

REQUIRED_KEYS = {
    "JOB_ID",
    "CHANNEL_CODE",
    "APP_APPLICATION_ID",
    "ENGINE_APPLICATION_ID",
    "APP_NAME",
    "ENGINE_NAME",
    "SOURCE_RELEASE_BRANCH",
    "CHANNEL_RELEASE_BRANCH",
    "APP_VERSION_NAME",
    "APP_VERSION_CODE",
    "ENGINE_VERSION_NAME",
    "ENGINE_VERSION_CODE",
    "REPO_URL",
    "BACKEND_BASE_URL",
    "PROGRESS_CALLBACK_URL",
    "COMPLETE_CALLBACK_URL",
    "JOB_TOKEN",
    "ADMIN_AUTHORIZATION_TOKEN",
}


class ReleaseWorkerHandler(BaseHTTPRequestHandler):
    server_version = "DuodianReleaseWorker/1.0"

    def do_POST(self):
        if self.path != self.server.release_path:
            self.send_json(404, {"error": "not_found"})
            return
        authorization = self.headers.get("Authorization", "")
        if authorization != f"Bearer {self.server.worker_token}":
            self.send_json(401, {"error": "unauthorized"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self.send_json(400, {"error": "invalid_content_length"})
            return
        if length <= 0 or length > self.server.max_body_bytes:
            self.send_json(413, {"error": "invalid_body_size"})
            return
        try:
            payload = json.loads(self.rfile.read(length).decode("utf-8"))
        except Exception:
            self.send_json(400, {"error": "invalid_json"})
            return
        if not isinstance(payload, dict):
            self.send_json(400, {"error": "invalid_payload"})
            return

        env = {}
        for key, value in payload.items():
            if key not in ALLOWED_KEYS:
                self.send_json(400, {"error": f"unsupported_key:{key}"})
                return
            if value is None:
                continue
            if not isinstance(value, str):
                self.send_json(400, {"error": f"invalid_value:{key}"})
                return
            env[key] = value
        missing = sorted(key for key in REQUIRED_KEYS if not env.get(key))
        if missing:
            self.send_json(400, {"error": "missing_required", "keys": missing})
            return

        job_id = env.get("JOB_ID", "")
        channel_code = env.get("CHANNEL_CODE", "")
        log(f"accepted job={job_id} channel={channel_code} dryRun={env.get('DRY_RUN', 'false')}")
        thread = threading.Thread(
            target=self.server.run_release,
            args=(env,),
            name=f"release-job-{job_id}",
            daemon=True,
        )
        thread.start()
        self.send_json(202, {"status": "accepted", "jobId": job_id, "channelCode": channel_code})

    def log_message(self, fmt, *args):
        log("%s - %s" % (self.address_string(), fmt % args))

    def send_json(self, status, payload):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


class ReleaseWorkerServer(ThreadingHTTPServer):
    def __init__(self, server_address, handler_class, args):
        super().__init__(server_address, handler_class)
        self.worker_token = args.token
        self.release_path = args.path
        self.script = args.script.resolve()
        self.workdir = args.workdir.resolve() if args.workdir else None
        self.max_body_bytes = args.max_body_bytes

    def run_release(self, env):
        job_id = env.get("JOB_ID", "")
        channel_code = env.get("CHANNEL_CODE", "")
        child_env = os.environ.copy()
        child_env.update(env)
        try:
            subprocess.run(
                [str(self.script)],
                cwd=str(self.workdir) if self.workdir else None,
                env=child_env,
                check=False,
            )
        except Exception as exc:
            log(f"failed to start job={job_id} channel={channel_code}: {exc}")


def log(message):
    print(f"[release-worker] {message}", file=sys.stderr, flush=True)


def parse_args():
    repo_root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description="Local HTTP bridge for Duodian release jobs")
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=19091)
    parser.add_argument("--path", default="/release")
    parser.add_argument("--token", default=os.environ.get("RELEASE_WORKER_TOKEN", "phase11-local-worker"))
    parser.add_argument("--script", type=Path, default=repo_root / "admin/scripts/release-channel-apk.sh")
    parser.add_argument("--workdir", type=Path, default=repo_root)
    parser.add_argument("--max-body-bytes", type=int, default=64 * 1024)
    return parser.parse_args()


def main():
    args = parse_args()
    if args.host not in {"127.0.0.1", "localhost"}:
        raise SystemExit("release worker should bind to 127.0.0.1/localhost only")
    if not args.script.is_file():
        raise SystemExit(f"script not found: {args.script}")
    if not os.access(args.script, os.X_OK):
        raise SystemExit(f"script is not executable: {args.script}")
    server = ReleaseWorkerServer((args.host, args.port), ReleaseWorkerHandler, args)
    log(f"listening on http://{args.host}:{args.port}{args.path}")
    server.serve_forever()


if __name__ == "__main__":
    main()
