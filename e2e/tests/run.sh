#!/usr/bin/env bash
# Fast JD captcha diagnostic runner.
#
# The script prepares a target device, opens Account Manager directly, advances
# to the JD clone phone-login page, clicks "get code", and captures root
# diagnostics immediately after the click.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ADB_BIN="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
PROFILE="pixel9"
PROFILE_FILE=""
SERIAL=""
RUN_ID=""
SKIP_BUILD=false
INSTALL_APKS=true
CLEAR_DATA=false
CLICK_GET_CODE=true
ROOT_DIAG=true
HEAVY_ROOT_DIAG=false
CDP_RELOAD_PROBE=false
CDP_ENABLED=true
DEBUGGERD_DIAG=false
KEEP_LOGCAT=false
STATUS=1

usage() {
    cat <<'USAGE'
Usage:
  e2e/tests/run.sh --serial emulator-5556 --profile pixel9
  e2e/tests/run.sh --serial emulator-5554 --profile pixel8
  e2e/tests/run.sh --serial <xiaomi_serial> --profile xiaomi

Options:
  --serial <id>          Target adb serial. Auto-selected from profile if omitted.
  --profile <name|path>  pixel8, pixel9, xiaomi, or an env file path. Default: pixel9.
  --skip-build           Reuse existing APKs.
  --no-install           Do not install Account Manager / engine APKs.
  --clear-data           Clear Account Manager, engine, and JD package data first.
  --no-click             Stop at JD phone-login page before clicking get-code.
  --no-root-diag         Do not run adb root or pull root-only diagnostics.
  --no-cdp               Do not attach to WebView DevTools during get-code.
  --heavy-root-diag      Pull /data/anr and /data/tombstones after get-code.
  --debuggerd-diag       Capture debuggerd -b for the virtual JD process.
  --cdp-reload-probe     Try a CDP cache-disabled reload probe after observing WebView.
  --keep-logcat          Do not clear logcat at start.
  -h, --help             Show this help.
USAGE
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --serial) SERIAL="$2"; shift 2 ;;
        --profile) PROFILE="$2"; shift 2 ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --no-install) INSTALL_APKS=false; shift ;;
        --clear-data) CLEAR_DATA=true; shift ;;
        --no-click) CLICK_GET_CODE=false; shift ;;
        --no-root-diag) ROOT_DIAG=false; shift ;;
        --no-cdp) CDP_ENABLED=false; shift ;;
        --heavy-root-diag) HEAVY_ROOT_DIAG=true; shift ;;
        --debuggerd-diag) DEBUGGERD_DIAG=true; shift ;;
        --cdp-reload-probe) CDP_RELOAD_PROBE=true; shift ;;
        --keep-logcat) KEEP_LOGCAT=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    esac
done

CLI_CDP_RELOAD_PROBE="$CDP_RELOAD_PROBE"
CLI_HEAVY_ROOT_DIAG="$HEAVY_ROOT_DIAG"
CLI_DEBUGGERD_DIAG="$DEBUGGERD_DIAG"

if [[ "$PROFILE" == */* ]]; then
    PROFILE_FILE="$PROFILE"
else
    PROFILE_FILE="$ROOT_DIR/e2e/profile/${PROFILE}.env"
fi

if [[ ! -f "$PROFILE_FILE" ]]; then
    echo "Profile file not found: $PROFILE_FILE" >&2
    exit 1
fi

# shellcheck disable=SC1090
source "$PROFILE_FILE"

PROFILE_NAME="${PROFILE_NAME:-$PROFILE}"
DEVICE_KIND="${DEVICE_KIND:-emulator}"
AVD_NAME_CONTAINS="${AVD_NAME_CONTAINS:-}"
APP_PACKAGE="${APP_PACKAGE:-com.zhirang.zhanghaoguanjia}"
APP_LAUNCH_ACTIVITY="${APP_LAUNCH_ACTIVITY:-com.zhirang.zhanghaoguanjia.view.home.HomeActivity}"
ENGINE_PACKAGE="${ENGINE_PACKAGE:-com.zhirang.zhanghaoguanjia.engine}"
JD_PACKAGE="${JD_PACKAGE:-com.jd.mrd.jingming}"
JD_APK="${JD_APK:-e2e_honor/jd_apk/jd_base.apk}"
LOGIN_PHONE="${E2E_LOGIN_PHONE:-${LOGIN_PHONE:-13265710803}}"
JD_LOGIN_PHONE="${E2E_JD_LOGIN_PHONE:-${JD_LOGIN_PHONE:-$LOGIN_PHONE}}"
POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-300}"
SHORT_TIMEOUT_MS="${SHORT_TIMEOUT_MS:-5000}"
MEDIUM_TIMEOUT_MS="${MEDIUM_TIMEOUT_MS:-12000}"
LONG_TIMEOUT_MS="${LONG_TIMEOUT_MS:-35000}"
CAPTCHA_CAPTURE_SECONDS="${CAPTCHA_CAPTURE_SECONDS:-10}"
CDP_CAPTURE_SECONDS="${CDP_CAPTURE_SECONDS:-6}"
CDP_RELOAD_PROBE="${CDP_RELOAD_PROBE:-false}"
HEAVY_ROOT_DIAG="${HEAVY_ROOT_DIAG:-false}"
DEBUGGERD_DIAG="${DEBUGGERD_DIAG:-false}"
[[ "$CLI_CDP_RELOAD_PROBE" == true ]] && CDP_RELOAD_PROBE=true
[[ "$CLI_HEAVY_ROOT_DIAG" == true ]] && HEAVY_ROOT_DIAG=true
[[ "$CLI_DEBUGGERD_DIAG" == true ]] && DEBUGGERD_DIAG=true
DIALOG_BUTTON_TEXTS="${DIALOG_BUTTON_TEXTS:-稍后|知道了|确定|允许|同意|继续|以后再说|取消|OK|Allow|While using the app|Only this time|关闭应用|等待}"

RUN_ID="${RUN_ID:-${PROFILE_NAME}_jd_captcha_$(date +%Y%m%d_%H%M%S)}"
ARTIFACT_DIR="$ROOT_DIR/e2e/artifacts/$RUN_ID"
UI_DIR="$ARTIFACT_DIR/ui"
ROOT_CAPTURE_DIR="$ARTIFACT_DIR/root"
SUMMARY_FILE="$ARTIFACT_DIR/run-summary.json"
mkdir -p "$UI_DIR" "$ROOT_CAPTURE_DIR"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }

now_ms() {
    python3 - <<'PY'
import time
print(int(time.time() * 1000))
PY
}

json_escape() {
    local value="${1:-}"
    value="${value//\\/\\\\}"
    value="${value//\"/\\\"}"
    value="${value//$'\n'/\\n}"
    value="${value//$'\r'/\\r}"
    value="${value//$'\t'/\\t}"
    printf '%s' "$value"
}

write_summary() {
    local status="$1"
    local message="${2:-}"
    cat > "$SUMMARY_FILE" <<JSON
{
  "runId": "$(json_escape "$RUN_ID")",
  "profile": "$(json_escape "$PROFILE_NAME")",
  "serial": "$(json_escape "$SERIAL")",
  "status": $status,
  "message": "$(json_escape "$message")",
  "artifactsDir": "$(json_escape "$ARTIFACT_DIR")"
}
JSON
}

fail() {
    log "FAIL: $*"
    STATUS=1
    write_summary "$STATUS" "$*"
    exit "$STATUS"
}

finish() {
    local exit_status=$?
    if [[ ! -f "$SUMMARY_FILE" ]]; then
        write_summary "$exit_status" "script exited"
    fi
}
trap finish EXIT

if [[ ! -x "$ADB_BIN" ]] && ! command -v "$ADB_BIN" >/dev/null 2>&1; then
    echo "adb not found: $ADB_BIN" >&2
    exit 1
fi

select_serial() {
    local serial avd chars
    while read -r serial; do
        [[ -z "$serial" ]] && continue
        avd=$("$ADB_BIN" -s "$serial" shell getprop ro.boot.qemu.avd_name </dev/null 2>/dev/null | tr -d '\r' || true)
        chars=$("$ADB_BIN" -s "$serial" shell getprop ro.build.characteristics </dev/null 2>/dev/null | tr -d '\r' || true)
        if [[ "$DEVICE_KIND" == "physical" ]]; then
            [[ "$chars" != *"emulator"* ]] && { echo "$serial"; return 0; }
        elif [[ -n "$AVD_NAME_CONTAINS" ]]; then
            [[ "$avd" == *"$AVD_NAME_CONTAINS"* ]] && { echo "$serial"; return 0; }
        else
            [[ "$chars" == *"emulator"* ]] && { echo "$serial"; return 0; }
        fi
    done < <("$ADB_BIN" devices | awk '$2 == "device" {print $1}')
    return 1
}

if [[ -z "$SERIAL" ]]; then
    SERIAL="$(select_serial || true)"
fi
[[ -n "$SERIAL" ]] || fail "No device selected by profile '$PROFILE_NAME'"

adb() {
    "$ADB_BIN" -s "$SERIAL" "$@"
}

adb_shell() {
    adb shell "$@"
}

ensure_java_home() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        return 0
    fi
    if command -v /usr/libexec/java_home >/dev/null 2>&1; then
        local java_home
        java_home=$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)
        if [[ -n "$java_home" ]]; then
            export JAVA_HOME="$java_home"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi
}

latest_apk() {
    local dir="$1" pattern="$2"
    find "$dir" -name "$pattern" -type f 2>/dev/null | sort | tail -1
}

build_and_install() {
    cd "$ROOT_DIR"
    if [[ "$SKIP_BUILD" != true ]]; then
        ensure_java_home
        log "Building debug APKs"
        ./gradlew :app:assembleDebug --no-daemon
    fi

    local app_apk engine_apk jd_apk_path
    app_apk="$(latest_apk "$ROOT_DIR/app/build/outputs/apk/debug" '*universal-debug.apk')"
    [[ -n "$app_apk" ]] || app_apk="$(latest_apk "$ROOT_DIR/app/build/outputs/apk/debug" '*arm64-v8a-debug.apk')"
    engine_apk="$(latest_apk "$ROOT_DIR/Bcore/build/outputs/apk/debug" 'FxEngine_*.apk')"
    jd_apk_path="$ROOT_DIR/$JD_APK"

    [[ -f "$app_apk" ]] || fail "App debug APK not found"
    [[ -f "$engine_apk" ]] || fail "Engine debug APK not found"

    log "Installing $(basename "$app_apk")"
    adb install -r -d "$app_apk" >/dev/null
    log "Installing $(basename "$engine_apk")"
    adb install -r -d "$engine_apk" >/dev/null

    if ! adb_shell pm list packages "$JD_PACKAGE" | grep -q "$JD_PACKAGE"; then
        [[ -f "$jd_apk_path" ]] || fail "JD package missing and APK not found: $jd_apk_path"
        log "Installing $(basename "$jd_apk_path")"
        adb install -r -d "$jd_apk_path" >/dev/null
    fi
}

root_device() {
    if [[ "$ROOT_DIAG" != true ]]; then
        return 0
    fi
    log "Enabling adb root"
    adb root >/dev/null || true
    adb wait-for-device
    adb_shell id > "$ROOT_CAPTURE_DIR/id.txt" 2>&1 || true
    adb_shell getenforce > "$ROOT_CAPTURE_DIR/selinux.txt" 2>&1 || true
    if ! grep -q 'uid=0(root)' "$ROOT_CAPTURE_DIR/id.txt"; then
        fail "adb root did not produce uid=0"
    fi
}

prepare_device() {
    adb wait-for-device
    root_device
    adb_shell input keyevent 224 >/dev/null 2>&1 || true
    adb_shell wm dismiss-keyguard >/dev/null 2>&1 || true
    adb_shell am force-stop "$ENGINE_PACKAGE" >/dev/null 2>&1 || true
    adb_shell am force-stop "$APP_PACKAGE" >/dev/null 2>&1 || true
    if [[ "$KEEP_LOGCAT" != true ]]; then
        adb logcat -c >/dev/null 2>&1 || true
    fi
    if [[ "$CLEAR_DATA" == true ]]; then
        log "Clearing app data"
        adb_shell pm clear "$APP_PACKAGE" >/dev/null 2>&1 || true
        adb_shell pm clear "$ENGINE_PACKAGE" >/dev/null 2>&1 || true
        adb_shell pm clear "$JD_PACKAGE" >/dev/null 2>&1 || true
    fi
}

dump_ui() {
    local name="$1"
    local remote="/data/local/tmp/e2e_${name}.xml"
    adb_shell uiautomator dump "$remote" >/dev/null 2>"$UI_DIR/${name}_dump.err" || true
    adb pull "$remote" "$UI_DIR/${name}.xml" >/dev/null 2>&1 || true
    adb_shell rm -f "$remote" >/dev/null 2>&1 || true
}

screenshot() {
    local name="$1"
    local remote="/data/local/tmp/e2e_${name}.png"
    adb_shell screencap -p "$remote" >/dev/null 2>&1 || true
    adb pull "$remote" "$UI_DIR/${name}.png" >/dev/null 2>&1 || true
    adb_shell rm -f "$remote" >/dev/null 2>&1 || true
}

capture_post_click_burst() {
    local remote="/data/local/tmp/e2e_captcha_burst_$$"
    local out_dir="$UI_DIR/05_after_get_burst"
    mkdir -p "$out_dir"
    adb_shell rm -rf "$remote" >/dev/null 2>&1 || true
    adb_shell mkdir -p "$remote" >/dev/null 2>&1 || true
    adb_shell "
        sleep 0.15; screencap -p '$remote/0150ms.png';
        sleep 0.15; screencap -p '$remote/0300ms.png';
        sleep 0.20; screencap -p '$remote/0500ms.png';
        sleep 0.30; screencap -p '$remote/0800ms.png';
        sleep 0.30; screencap -p '$remote/1100ms.png';
        sleep 0.40; screencap -p '$remote/1500ms.png';
        sleep 0.50; screencap -p '$remote/2000ms.png';
        sleep 1.00; screencap -p '$remote/3000ms.png'
    " >/dev/null 2>&1 || true
    adb pull "$remote/." "$out_dir" >/dev/null 2>&1 || true
    adb_shell rm -rf "$remote" >/dev/null 2>&1 || true
}

start_cdp_capture() {
    local name="$1"
    local out_dir="$ROOT_CAPTURE_DIR/${name}_cdp"
    mkdir -p "$out_dir"
    if [[ "$CDP_ENABLED" != true ]] || (( CDP_CAPTURE_SECONDS <= 0 )); then
        printf 'cdp disabled\n' > "$out_dir/skipped.txt"
        return 0
    fi
    if ! command -v node >/dev/null 2>&1; then
        printf 'node not found\n' > "$out_dir/skipped.txt"
        return 0
    fi

    (
        ADB_BIN="$ADB_BIN" SERIAL="$SERIAL" OUT_DIR="$out_dir" JD_PACKAGE="$JD_PACKAGE" CAPTURE_MS="$((CDP_CAPTURE_SECONDS * 1000))" CDP_RELOAD_PROBE="$CDP_RELOAD_PROBE" node <<'NODE'
const fs = require('fs');
const { execFileSync } = require('child_process');

const adb = process.env.ADB_BIN;
const serial = process.env.SERIAL;
const outDir = process.env.OUT_DIR;
const jdPackage = process.env.JD_PACKAGE;
const captureMs = Number(process.env.CAPTURE_MS || 6000);
const reloadProbe = process.env.CDP_RELOAD_PROBE === 'true';
const basePort = 9322;
const start = Date.now();
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const jsonFile = (name, value) => fs.writeFileSync(`${outDir}/${name}.json`, JSON.stringify(value, null, 2));

fs.mkdirSync(outDir, { recursive: true });

function adbRun(args, timeout = 2000) {
  return execFileSync(adb, ['-s', serial, ...args], { encoding: 'utf8', timeout });
}

function safeName(value) {
  return String(value).replace(/[^A-Za-z0-9_.-]/g, '_').slice(0, 120);
}

async function fetchJson(url, timeoutMs = 500) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(url, { signal: controller.signal });
    return await res.json();
  } finally {
    clearTimeout(timer);
  }
}

function devtoolsSockets() {
  const unix = adbRun(['shell', 'cat /proc/net/unix'], 1200);
  const found = new Set();
  for (const match of unix.matchAll(/@(webview_devtools_remote_\d+)/g)) {
    found.add(match[1]);
  }
  return Array.from(found);
}

function forwardSocket(socket, index) {
  const port = basePort + index;
  try { adbRun(['forward', '--remove', `tcp:${port}`], 1000); } catch (_) {}
  adbRun(['forward', `tcp:${port}`, `localabstract:${socket}`], 1500);
  return port;
}

class CdpSession {
  constructor(socket, port, target) {
    this.socket = socket;
    this.port = port;
    this.target = target;
    this.id = 0;
    this.pending = new Map();
    this.events = [];
    this.states = [];
    this.key = safeName(`${socket}_${target.id}`);
    this.ws = new WebSocket(target.webSocketDebuggerUrl.replace('127.0.0.1:9222', `127.0.0.1:${port}`));
    this.ready = new Promise((resolve, reject) => {
      this.ws.addEventListener('open', resolve, { once: true });
      this.ws.addEventListener('error', reject, { once: true });
    });
    this.ws.addEventListener('message', event => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const cb = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        cb(msg);
      } else if (msg.method) {
        this.events.push({ atMs: Date.now() - start, ...msg });
      }
    });
  }

  send(method, params = {}) {
    const id = ++this.id;
    this.ws.send(JSON.stringify({ id, method, params }));
    return new Promise(resolve => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        resolve({ error: { message: 'timeout' } });
      }, 1000);
      this.pending.set(id, msg => {
        clearTimeout(timer);
        resolve(msg);
      });
    });
  }

  async init() {
    await this.ready;
    await this.send('Runtime.enable');
    await this.send('Log.enable');
    await this.send('Network.enable', { maxTotalBufferSize: 2097152, maxResourceBufferSize: 524288 });
    await this.send('Page.enable');
  }

  async frameTree(label) {
    const msg = await this.send('Page.getFrameTree');
    jsonFile(`${this.key}_${label}_frame_tree`, msg);
    return msg;
  }

  async sample(label) {
    const expr = `(() => {
      const safe = fn => { try { return fn(); } catch (e) { return 'ERR:' + e.name + ':' + e.message; } };
      const de = document.documentElement;
      const body = document.body;
      const resources = safe(() => performance.getEntriesByType('resource').map(e => ({
        name: e.name,
        initiatorType: e.initiatorType,
        startTime: Math.round(e.startTime),
        duration: Math.round(e.duration),
        transferSize: e.transferSize,
        encodedBodySize: e.encodedBodySize,
        decodedBodySize: e.decodedBodySize,
        responseStatus: e.responseStatus || 0
      })));
      return {
        at: Date.now(),
        href: safe(() => location.href),
        title: safe(() => document.title),
        readyState: safe(() => document.readyState),
        hasDocumentElement: !!de,
        hasBody: !!body,
        bodyText: body ? (body.innerText || body.textContent || '').slice(0, 2000) : null,
        html: de ? de.outerHTML.slice(0, 30000) : null,
        scripts: safe(() => Array.from(document.scripts).map(s => s.src || s.textContent.slice(0, 300)).slice(0, 100)),
        images: safe(() => Array.from(document.images).map(img => ({
          src: img.src,
          complete: img.complete,
          naturalWidth: img.naturalWidth,
          naturalHeight: img.naturalHeight
        })).slice(0, 100)),
        resources,
        keys: safe(() => Object.keys(window).filter(k => /jd|jcap|captcha|risk|verify|slider|jma|jra|jdd/i.test(k)).sort())
      };
    })()`;
    const msg = await this.send('Runtime.evaluate', {
      expression: expr,
      returnByValue: true,
      awaitPromise: true,
      timeout: 1000
    });
    const value = msg.result && msg.result.result ? msg.result.result.value || msg.result : msg;
    this.states.push({ label, atMs: Date.now() - start, value });
    jsonFile(`${this.key}_${label}`, value);
  }

  async reloadProbe() {
    const before = await this.frameTree('before_reload_probe');
    const frame = before && before.result && before.result.frameTree && before.result.frameTree.frame;
    const url = frame && (frame.unreachableUrl || frame.url || this.target.url);
    if (!url || !/^https?:\/\//.test(url)) {
      jsonFile(`${this.key}_reload_probe`, { skipped: true, reason: 'no http url', url, before });
      return;
    }
    const sep = url.includes('?') ? '&' : '?';
    const probeUrl = `${url}${sep}bb_cdp_probe=${Date.now()}`;
    const setCache = await this.send('Network.setCacheDisabled', { cacheDisabled: true });
    const navigate = await this.send('Page.navigate', { url: probeUrl });
    await sleep(1200);
    await this.sample('after_reload_probe');
    const after = await this.frameTree('after_reload_probe');
    jsonFile(`${this.key}_reload_probe`, { url, probeUrl, setCache, navigate, before, after });
  }

  close() {
    try { this.ws.close(); } catch (_) {}
  }
}

async function main() {
  const sessions = new Map();
  const snapshots = [];
  let socketIndex = 0;
  while (Date.now() - start < captureMs) {
    let sockets = [];
    try {
      sockets = devtoolsSockets();
    } catch (e) {
      snapshots.push({ atMs: Date.now() - start, error: String(e.message || e) });
    }

    for (const socket of sockets) {
      let port = socketIndexByName[socket];
      if (!port) {
        port = forwardSocket(socket, socketIndex++);
        socketIndexByName[socket] = port;
      }
      try {
        const targets = await fetchJson(`http://127.0.0.1:${port}/json`, 700);
        snapshots.push({ atMs: Date.now() - start, socket, port, targets });
        for (const target of targets) {
          if (target.type !== 'page' || !target.webSocketDebuggerUrl) continue;
          const key = `${socket}:${target.id}`;
          if (sessions.has(key)) continue;
          const session = new CdpSession(socket, port, target);
          sessions.set(key, session);
          session.init()
            .then(async () => {
              await session.sample('initial');
              await session.frameTree('initial');
              if (reloadProbe) {
                await session.reloadProbe();
              }
            })
            .catch(e => {
              fs.writeFileSync(`${outDir}/${session.key}_init.err`, String(e.stack || e));
            });
        }
      } catch (e) {
        snapshots.push({ atMs: Date.now() - start, socket, port, targetError: String(e.message || e) });
      }
    }

    const elapsed = Date.now() - start;
    if (elapsed > 300 && elapsed % 300 < 130) {
      await Promise.all(Array.from(sessions.values()).map(s => s.sample(`t${elapsed}`).catch(() => {})));
    }
    await sleep(100);
  }

  await Promise.all(Array.from(sessions.values()).map(s => s.sample('final').catch(() => {})));
  for (const session of sessions.values()) {
    jsonFile(`${session.key}_events`, session.events);
    jsonFile(`${session.key}_states`, session.states);
    session.close();
  }
  jsonFile('target_snapshots', snapshots);
  jsonFile('summary', {
    jdPackage,
    captureMs,
    sockets: Object.keys(socketIndexByName),
    sessions: Array.from(sessions.values()).map(s => ({
      socket: s.socket,
      port: s.port,
      target: s.target,
      key: s.key,
      eventCount: s.events.length,
      stateCount: s.states.length
    }))
  });
}

const socketIndexByName = {};
main().catch(e => {
  fs.writeFileSync(`${outDir}/fatal.err`, String(e.stack || e));
  process.exitCode = 1;
});
NODE
    ) > "$out_dir/stdout.txt" 2>"$out_dir/stderr.txt" &
    CAPTURE_CDP_PID=$!
    printf '%s\n' "$CAPTURE_CDP_PID" > "$ROOT_CAPTURE_DIR/${name}_cdp_pid.txt"
}

finish_cdp_capture() {
    local name="$1"
    local pid_file="$ROOT_CAPTURE_DIR/${name}_cdp_pid.txt"
    [[ -s "$pid_file" ]] || return 0
    local pid
    pid="$(cat "$pid_file")"
    local i
    for i in $(seq 1 30); do
        if ! kill -0 "$pid" >/dev/null 2>&1; then
            wait "$pid" >/dev/null 2>&1 || true
            return 0
        fi
        sleep 0.2
    done
    kill "$pid" >/dev/null 2>&1 || true
    wait "$pid" >/dev/null 2>&1 || true
}

capture_foreground() {
    local name="$1"
    adb_shell dumpsys activity activities > "$ROOT_CAPTURE_DIR/${name}_activity.txt" 2>&1 || true
    adb_shell dumpsys window windows > "$ROOT_CAPTURE_DIR/${name}_window.txt" 2>&1 || true
    adb_shell ps -A > "$ROOT_CAPTURE_DIR/${name}_ps.txt" 2>&1 || true
}

checkpoint() {
    local name="$1"
    screenshot "$name"
    dump_ui "$name"
    capture_foreground "$name"
}

xml_query_script='
import re, sys
path, mode, pattern = sys.argv[1:4]
data = open(path, "r", encoding="utf-8", errors="ignore").read()
nodes = re.findall(r"<node\b[^>]*>", data)
def attr(node, name):
    m = re.search(name + r"=\"([^\"]*)\"", node)
    return m.group(1) if m else ""
def center(bounds):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds)
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return ((x1 + x2) // 2, (y1 + y2) // 2)
for node in nodes:
    text = attr(node, "text")
    rid = attr(node, "resource-id")
    desc = attr(node, "content-desc")
    cls = attr(node, "class")
    ok = False
    if mode == "res":
        ok = rid == pattern or rid.endswith("/" + pattern)
    elif mode == "text":
        ok = text == pattern
    elif mode == "contains":
        ok = pattern in text or pattern in desc
    elif mode == "class":
        ok = cls == pattern
    if ok:
        c = center(attr(node, "bounds"))
        if c:
            print(f"{c[0]} {c[1]} {rid} {text} {cls}")
            sys.exit(0)
sys.exit(1)
'

node_center_from_dump() {
    local xml="$1" mode="$2" pattern="$3"
    python3 -c "$xml_query_script" "$xml" "$mode" "$pattern"
}

find_selector_once() {
    local name="$1" mode="$2" pattern="$3"
    dump_ui "find_${name}"
    [[ -s "$UI_DIR/find_${name}.xml" ]] || return 1
    node_center_from_dump "$UI_DIR/find_${name}.xml" "$mode" "$pattern"
}

sleep_poll() {
    awk "BEGIN { printf \"%.3f\", $POLL_INTERVAL_MS / 1000 }" | xargs sleep
}

tap_selector() {
    local name="$1" mode="$2" pattern="$3" timeout_ms="${4:-$MEDIUM_TIMEOUT_MS}"
    local start now line x y
    start=$(now_ms)
    while true; do
        dump_ui "probe_${name}"
        if [[ -s "$UI_DIR/probe_${name}.xml" ]]; then
            if line="$(node_center_from_dump "$UI_DIR/probe_${name}.xml" "$mode" "$pattern" 2>/dev/null)"; then
                x="$(awk '{print $1}' <<<"$line")"
                y="$(awk '{print $2}' <<<"$line")"
                log "Tap $name at $x,$y ($line)"
                adb_shell input tap "$x" "$y"
                return 0
            fi
        fi
        now=$(now_ms)
        if (( now - start >= timeout_ms )); then
            return 1
        fi
        sleep_poll
    done
}

wait_selector() {
    local name="$1" mode="$2" pattern="$3" timeout_ms="${4:-$MEDIUM_TIMEOUT_MS}"
    local start now
    start=$(now_ms)
    while true; do
        dump_ui "wait_${name}"
        if [[ -s "$UI_DIR/wait_${name}.xml" ]] &&
            node_center_from_dump "$UI_DIR/wait_${name}.xml" "$mode" "$pattern" >/dev/null 2>&1; then
            return 0
        fi
        now=$(now_ms)
        if (( now - start >= timeout_ms )); then
            return 1
        fi
        sleep_poll
    done
}

dismiss_dialogs() {
    local IFS='|'
    local label line x y
    dump_ui "dialog_probe"
    [[ -s "$UI_DIR/dialog_probe.xml" ]] || return 0
    for label in $DIALOG_BUTTON_TEXTS; do
        if line="$(node_center_from_dump "$UI_DIR/dialog_probe.xml" text "$label" 2>/dev/null ||
            node_center_from_dump "$UI_DIR/dialog_probe.xml" contains "$label" 2>/dev/null)"; then
            x="$(awk '{print $1}' <<<"$line")"
            y="$(awk '{print $2}' <<<"$line")"
            log "Dismiss dialog $label at $x,$y"
            adb_shell input tap "$x" "$y"
            sleep_poll
            return 0
        fi
    done
}

find_virtual_jd_pid() {
    local ps_file="$1"
    awk -v pkg="$JD_PACKAGE" '$0 ~ pkg { print $2; exit }' "$ps_file" 2>/dev/null || true
}

capture_root_only_state() {
    local name="$1"
    local engine_data="/data/user/0/$ENGINE_PACKAGE"
    local webview_data="${engine_data}/app_webview_u0_${JD_PACKAGE//./_}"
    local virtual_jd_data="${engine_data}/blackbox/data/user/0/$JD_PACKAGE"

    adb_shell getprop > "$ROOT_CAPTURE_DIR/${name}_getprop.txt" 2>&1 || true
    adb_shell dumpsys gfxinfo "$JD_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_jd_gfxinfo.txt" 2>&1 || true
    adb_shell dumpsys gfxinfo "$ENGINE_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_engine_gfxinfo.txt" 2>&1 || true
    adb_shell dumpsys SurfaceFlinger --list > "$ROOT_CAPTURE_DIR/${name}_surfaceflinger_list.txt" 2>&1 || true
    adb_shell dumpsys webviewupdate > "$ROOT_CAPTURE_DIR/${name}_webviewupdate.txt" 2>&1 || true
    adb_shell dumpsys connectivity > "$ROOT_CAPTURE_DIR/${name}_connectivity.txt" 2>&1 || true
    adb_shell dumpsys netpolicy > "$ROOT_CAPTURE_DIR/${name}_netpolicy.txt" 2>&1 || true
    adb_shell dumpsys netd > "$ROOT_CAPTURE_DIR/${name}_netd.txt" 2>&1 || true
    adb_shell cmd connectivity airplane-mode > "$ROOT_CAPTURE_DIR/${name}_airplane_mode.txt" 2>&1 || true
    adb_shell cat /proc/net/unix > "$ROOT_CAPTURE_DIR/${name}_proc_net_unix.txt" 2>&1 || true
    adb_shell cat /proc/net/tcp > "$ROOT_CAPTURE_DIR/${name}_proc_net_tcp.txt" 2>&1 || true
    adb_shell cat /proc/net/tcp6 > "$ROOT_CAPTURE_DIR/${name}_proc_net_tcp6.txt" 2>&1 || true
    adb_shell cmd appops get "$ENGINE_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_engine_appops.txt" 2>&1 || true
    adb_shell cmd appops get "$JD_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_jd_appops.txt" 2>&1 || true

    adb_shell ls -la "$engine_data" > "$ROOT_CAPTURE_DIR/${name}_engine_data_ls.txt" 2>&1 || true
    adb_shell ls -la "$engine_data/files" > "$ROOT_CAPTURE_DIR/${name}_engine_files_ls.txt" 2>&1 || true
    adb_shell ls -la "$engine_data/files/crash_logs" > "$ROOT_CAPTURE_DIR/${name}_engine_crash_logs_ls.txt" 2>&1 || true
    adb_shell ls -la "$webview_data" > "$ROOT_CAPTURE_DIR/${name}_jd_webview_data_ls.txt" 2>&1 || true
    adb_shell find "$webview_data" -maxdepth 2 -type f -printf '%p %s\n' > "$ROOT_CAPTURE_DIR/${name}_jd_webview_files.txt" 2>&1 || true
    adb_shell ls -laR "$virtual_jd_data" > "$ROOT_CAPTURE_DIR/${name}_virtual_jd_data_ls.txt" 2>&1 || true

    mkdir -p "$ROOT_CAPTURE_DIR/${name}_engine_crash_logs"
    adb pull "$engine_data/files/crash_logs" "$ROOT_CAPTURE_DIR/${name}_engine_crash_logs" >/dev/null 2>&1 || true
}

capture_root_diag() {
    local name="$1"
    capture_foreground "$name"
    local pid
    pid="$(find_virtual_jd_pid "$ROOT_CAPTURE_DIR/${name}_ps.txt")"
    printf '%s\n' "$pid" > "$ROOT_CAPTURE_DIR/${name}_virtual_jd_pid.txt"
    if [[ -n "$pid" && "$DEBUGGERD_DIAG" == true ]]; then
        adb_shell debuggerd -b "$pid" > "$ROOT_CAPTURE_DIR/${name}_debuggerd_${pid}.txt" 2>&1 || true
        adb_shell cat "/proc/$pid/status" > "$ROOT_CAPTURE_DIR/${name}_proc_status_${pid}.txt" 2>&1 || true
        adb_shell ls -la "/proc/$pid/fd" > "$ROOT_CAPTURE_DIR/${name}_proc_fd_${pid}.txt" 2>&1 || true
        adb_shell cat "/proc/$pid/maps" > "$ROOT_CAPTURE_DIR/${name}_proc_maps_${pid}.txt" 2>&1 || true
    elif [[ -n "$pid" ]]; then
        adb_shell cat "/proc/$pid/status" > "$ROOT_CAPTURE_DIR/${name}_proc_status_${pid}.txt" 2>&1 || true
    fi
    adb logcat -d -b all > "$ROOT_CAPTURE_DIR/${name}_logcat_all.txt" 2>&1 || true
    adb_shell dumpsys package "$JD_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_jd_package.txt" 2>&1 || true
    adb_shell ls -l /data/anr > "$ROOT_CAPTURE_DIR/${name}_anr_listing.txt" 2>&1 || true
    if [[ "$HEAVY_ROOT_DIAG" == true ]]; then
        mkdir -p "$ROOT_CAPTURE_DIR/${name}_anr" "$ROOT_CAPTURE_DIR/${name}_tombstones"
        adb pull /data/anr "$ROOT_CAPTURE_DIR/${name}_anr" >/dev/null 2>&1 || true
        adb pull /data/tombstones "$ROOT_CAPTURE_DIR/${name}_tombstones" >/dev/null 2>&1 || true
    fi
    capture_root_only_state "$name"
}

launch_account_manager() {
    log "Launching Account Manager directly"
    adb_shell am force-stop "$ENGINE_PACKAGE" >/dev/null 2>&1 || true
    adb_shell am force-stop "$APP_PACKAGE" >/dev/null 2>&1 || true
    adb_shell am start -W -n "$APP_PACKAGE/$APP_LAUNCH_ACTIVITY" > "$ARTIFACT_DIR/start_app.txt" 2>&1 || true
    wait_account_home_with_dialogs "$SHORT_TIMEOUT_MS" || {
        adb_shell monkey -p "$APP_PACKAGE" -c android.intent.category.LAUNCHER 1 >> "$ARTIFACT_DIR/start_app.txt" 2>&1 || true
    }
    wait_account_home_with_dialogs "$LONG_TIMEOUT_MS" || fail "Account Manager home did not appear"
    dump_ui "01_account_home"
}

wait_account_home_with_dialogs() {
    local timeout_ms="$1"
    local start now
    start=$(now_ms)
    while true; do
        dump_ui "wait_account_home"
        if [[ -s "$UI_DIR/wait_account_home.xml" ]]; then
            if node_center_from_dump "$UI_DIR/wait_account_home.xml" res "${APP_PACKAGE}:id/rvPlatforms" >/dev/null 2>&1 ||
                node_center_from_dump "$UI_DIR/wait_account_home.xml" contains "交易日志" >/dev/null 2>&1; then
                return 0
            fi
            dismiss_dialogs
        fi
        now=$(now_ms)
        if (( now - start >= timeout_ms )); then
            return 1
        fi
        sleep_poll
    done
}

select_jd_category() {
    log "Selecting JD category"
    tap_selector "jd_category" text "京东秒送" "$MEDIUM_TIMEOUT_MS" ||
        tap_selector "jd_category_contains" contains "京东" "$MEDIUM_TIMEOUT_MS" ||
        fail "JD category not found"
    wait_selector "shops" res "${APP_PACKAGE}:id/rvShops" "$MEDIUM_TIMEOUT_MS" ||
        fail "JD shop list not visible"
    dump_ui "02_jd_category"
}

open_new_shop_card() {
    log "Opening new JD shop card"
    if ! tap_selector "new_shop" contains "新增店铺" "$SHORT_TIMEOUT_MS"; then
        log "No new-shop card visible, creating one"
        tap_selector "add_shop" res "${APP_PACKAGE}:id/btnAddShop" "$MEDIUM_TIMEOUT_MS" ||
            tap_selector "add_shop_text" contains "添加店铺" "$MEDIUM_TIMEOUT_MS" ||
            fail "Add-shop button not found"
        wait_selector "new_shop_after_add" contains "新增店铺" "$LONG_TIMEOUT_MS" ||
            fail "New shop card did not appear"
        tap_selector "new_shop_after_add" contains "新增店铺" "$MEDIUM_TIMEOUT_MS" ||
            fail "New shop card could not be opened"
    fi
    wait_selector "jd_login_surface" res "${JD_PACKAGE}:id/tv_jd_phone_type" "$LONG_TIMEOUT_MS" ||
        wait_selector "jd_login_surface_text" contains "验证码登录" "$LONG_TIMEOUT_MS" ||
        fail "JD clone login surface did not appear"
    dump_ui "03_jd_login_surface"
    capture_foreground "03_jd_login_surface"
}

prepare_jd_phone_login() {
    log "Preparing JD phone-code login"
    tap_selector "phone_tab" res "${JD_PACKAGE}:id/tv_jd_phone_type" "$MEDIUM_TIMEOUT_MS" ||
        tap_selector "phone_tab_text" contains "验证码登录" "$MEDIUM_TIMEOUT_MS" ||
        true
    local get_code_line get_code_x get_code_y
    get_code_line="$(find_selector_once "get_code_preinput" res "${JD_PACKAGE}:id/tv_jd_get_code" 2>/dev/null ||
        find_selector_once "get_code_preinput_text" contains "获取验证码" 2>/dev/null)" ||
        fail "JD get-code button not visible before phone input"
    get_code_x="$(awk '{print $1}' <<<"$get_code_line")"
    get_code_y="$(awk '{print $2}' <<<"$get_code_line")"
    printf '%s %s\n' "$get_code_x" "$get_code_y" > "$ROOT_CAPTURE_DIR/get_code_button_xy.txt"
    tap_selector "jd_phone_input" res "${JD_PACKAGE}:id/jd_phone_et" "$MEDIUM_TIMEOUT_MS" ||
        tap_selector "jd_phone_input_text" contains "请输入手机号" "$MEDIUM_TIMEOUT_MS" ||
        fail "JD phone input not found"
    adb_shell input keyevent 123 >/dev/null 2>&1 || true
    adb_shell input text "$JD_LOGIN_PHONE"
    adb_shell input keyevent 4 >/dev/null 2>&1 || true
    sleep 0.3
    screenshot "04_before_get_code"
    dump_ui "04_before_get_code"
    capture_foreground "04_before_get_code"
}

click_get_code_and_capture() {
    if [[ "$CLICK_GET_CODE" != true ]]; then
        log "Stopped before clicking get-code"
        return 0
    fi

    log "Clicking JD get-code and capturing immediate diagnostics"
    printf '%s\n' "$(now_ms)" > "$ROOT_CAPTURE_DIR/05_click_get_epoch_ms.txt"
    start_cdp_capture "05_after_get"
    local live_line
    if live_line="$(find_selector_once "get_code_click_live" res "${JD_PACKAGE}:id/tv_jd_get_code" 2>/dev/null ||
        find_selector_once "get_code_click_live_text" contains "获取验证码" 2>/dev/null)"; then
        local x y
        x="$(awk '{print $1}' <<<"$live_line")"
        y="$(awk '{print $2}' <<<"$live_line")"
        log "Tap live get-code coordinate at $x,$y ($live_line)"
        printf '%s %s\n' "$x" "$y" > "$ROOT_CAPTURE_DIR/get_code_button_xy_live.txt"
        adb_shell input tap "$x" "$y"
    elif [[ -s "$ROOT_CAPTURE_DIR/get_code_button_xy.txt" ]]; then
        local x y
        read -r x y < "$ROOT_CAPTURE_DIR/get_code_button_xy.txt"
        log "Tap saved get-code coordinate at $x,$y"
        adb_shell input tap "$x" "$y"
    else
        tap_selector "get_code_click" res "${JD_PACKAGE}:id/tv_jd_get_code" "$SHORT_TIMEOUT_MS" ||
            tap_selector "get_code_click_text" contains "获取验证码" "$SHORT_TIMEOUT_MS" ||
            fail "Get-code button disappeared before click"
    fi

    capture_post_click_burst
    finish_cdp_capture "05_after_get"
    checkpoint "05_after_get_post_burst"
    capture_root_diag "05_after_get_post_burst"

    if (( CAPTCHA_CAPTURE_SECONDS > 0 )); then
        sleep "$CAPTCHA_CAPTURE_SECONDS"
        checkpoint "05_after_get_${CAPTCHA_CAPTURE_SECONDS}s"
        capture_root_diag "05_after_get_${CAPTCHA_CAPTURE_SECONDS}s"
    fi
}

main() {
    log "Artifacts: $ARTIFACT_DIR"
    log "Target profile: $PROFILE_NAME serial=$SERIAL"
    prepare_device
    if [[ "$INSTALL_APKS" == true ]]; then
        build_and_install
    fi
    launch_account_manager
    select_jd_category
    open_new_shop_card
    prepare_jd_phone_login
    click_get_code_and_capture
    STATUS=0
    write_summary "$STATUS" "completed"
    log "Done. Artifacts: $ARTIFACT_DIR"
}

main "$@"
