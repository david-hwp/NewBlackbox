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
INSTALL_ENGINE_ONLY=false
CLEAR_DATA=false
CLICK_GET_CODE=true
ROOT_DIAG=true
KEEP_LOGCAT=false
DIRECT_JD=false
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
  --install-engine-only  Build/install only the engine APK.
  --clear-data           Clear Account Manager, engine, and JD package data first.
  --no-click             Stop at JD phone-login page before clicking get-code.
  --no-root-diag         Do not run adb root or pull root-only diagnostics.
  --direct-jd            Start the fixed JD clone through the engine e2e launcher.
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
        --install-engine-only) INSTALL_ENGINE_ONLY=true; shift ;;
        --clear-data) CLEAR_DATA=true; shift ;;
        --no-click) CLICK_GET_CODE=false; shift ;;
        --no-root-diag) ROOT_DIAG=false; shift ;;
        --direct-jd) DIRECT_JD=true; shift ;;
        --keep-logcat) KEEP_LOGCAT=true; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage; exit 2 ;;
    esac
done

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
JD_USER_ID="${E2E_JD_USER_ID:-${JD_USER_ID:-0}}"
LOGIN_PHONE="${E2E_LOGIN_PHONE:-${LOGIN_PHONE:-13265710803}}"
JD_LOGIN_PHONE="${E2E_JD_LOGIN_PHONE:-${JD_LOGIN_PHONE:-$LOGIN_PHONE}}"
POLL_INTERVAL_MS="${POLL_INTERVAL_MS:-300}"
SHORT_TIMEOUT_MS="${SHORT_TIMEOUT_MS:-5000}"
MEDIUM_TIMEOUT_MS="${MEDIUM_TIMEOUT_MS:-12000}"
LONG_TIMEOUT_MS="${LONG_TIMEOUT_MS:-35000}"
CAPTCHA_CAPTURE_SECONDS="${CAPTCHA_CAPTURE_SECONDS:-10}"
DIALOG_BUTTON_TEXTS="${DIALOG_BUTTON_TEXTS:-知道了|确定|允许|同意|继续|以后再说|取消|OK|Allow|While using the app|Only this time|关闭应用|等待}"

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
        if [[ "$INSTALL_ENGINE_ONLY" == true ]]; then
            log "Building engine debug APK"
            ./gradlew :Bcore:assembleDebug --no-daemon
        else
            log "Building debug APKs"
            ./gradlew :app:assembleDebug --no-daemon
        fi
    fi

    local app_apk engine_apk jd_apk_path
    app_apk="$(latest_apk "$ROOT_DIR/app/build/outputs/apk/debug" '*universal-debug.apk')"
    [[ -n "$app_apk" ]] || app_apk="$(latest_apk "$ROOT_DIR/app/build/outputs/apk/debug" '*arm64-v8a-debug.apk')"
    engine_apk="$(latest_apk "$ROOT_DIR/Bcore/build/outputs/apk/debug" 'FxEngine_*.apk')"
    jd_apk_path="$ROOT_DIR/$JD_APK"

    [[ -f "$engine_apk" ]] || fail "Engine debug APK not found"

    if [[ "$INSTALL_ENGINE_ONLY" != true ]]; then
        [[ -f "$app_apk" ]] || fail "App debug APK not found"
        log "Installing $(basename "$app_apk")"
        adb install -r -d "$app_apk" >/dev/null
    fi
    log "Installing $(basename "$engine_apk")"
    adb install -r -d "$engine_apk" >/dev/null

    if [[ "$INSTALL_ENGINE_ONLY" != true ]] && ! adb_shell pm list packages "$JD_PACKAGE" | grep -q "$JD_PACKAGE"; then
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
    adb_shell am force-stop "$JD_PACKAGE" >/dev/null 2>&1 || true
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

capture_root_diag() {
    local name="$1"
    capture_foreground "$name"
    adb logcat -d -b all > "$ROOT_CAPTURE_DIR/${name}_logcat_all.txt" 2>&1 || true

    if [[ "$ROOT_DIAG" != true ]]; then
        return 0
    fi

    local pid
    pid="$(find_virtual_jd_pid "$ROOT_CAPTURE_DIR/${name}_ps.txt")"
    printf '%s\n' "$pid" > "$ROOT_CAPTURE_DIR/${name}_virtual_jd_pid.txt"
    if [[ -n "$pid" ]]; then
        adb_shell debuggerd -b "$pid" > "$ROOT_CAPTURE_DIR/${name}_debuggerd_${pid}.txt" 2>&1 || true
        adb_shell cat "/proc/$pid/status" > "$ROOT_CAPTURE_DIR/${name}_proc_status_${pid}.txt" 2>&1 || true
        adb_shell ls -la "/proc/$pid/fd" > "$ROOT_CAPTURE_DIR/${name}_proc_fd_${pid}.txt" 2>&1 || true
        adb_shell cat "/proc/$pid/maps" > "$ROOT_CAPTURE_DIR/${name}_proc_maps_${pid}.txt" 2>&1 || true
    fi
    adb_shell dumpsys webviewupdate > "$ROOT_CAPTURE_DIR/${name}_webviewupdate.txt" 2>&1 || true
    adb_shell dumpsys package "$JD_PACKAGE" > "$ROOT_CAPTURE_DIR/${name}_jd_package.txt" 2>&1 || true
    adb_shell ls -l /data/anr > "$ROOT_CAPTURE_DIR/${name}_anr_listing.txt" 2>&1 || true
    mkdir -p "$ROOT_CAPTURE_DIR/${name}_anr" "$ROOT_CAPTURE_DIR/${name}_tombstones"
    adb pull /data/anr "$ROOT_CAPTURE_DIR/${name}_anr" >/dev/null 2>&1 || true
    adb pull /data/tombstones "$ROOT_CAPTURE_DIR/${name}_tombstones" >/dev/null 2>&1 || true
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
    checkpoint "01_account_home"
}

launch_direct_jd_clone() {
    log "Launching JD clone through engine e2e launcher"
    adb_shell am force-stop "$JD_PACKAGE" >/dev/null 2>&1 || true
    adb_shell am force-stop "$ENGINE_PACKAGE" >/dev/null 2>&1 || true
    adb_shell am start -W \
        -n "$ENGINE_PACKAGE/top.niunaijun.blackbox.engine.EngineE2eLaunchActivity" \
        --es pkg "$JD_PACKAGE" \
        --ei userId "$JD_USER_ID" \
        > "$ARTIFACT_DIR/start_direct_jd.txt" 2>&1 || true
    wait_selector "jd_login_surface" res "${JD_PACKAGE}:id/tv_jd_phone_type" "$LONG_TIMEOUT_MS" ||
        wait_selector "jd_login_surface_text" contains "验证码登录" "$LONG_TIMEOUT_MS" ||
        fail "JD clone login surface did not appear"
    checkpoint "03_jd_login_surface"
    capture_root_diag "03_jd_login_surface"
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
    checkpoint "02_jd_category"
}

open_new_shop_card() {
    log "Opening new JD shop card"
    tap_selector "new_shop" contains "新增店铺" "$MEDIUM_TIMEOUT_MS" ||
        fail "Existing new-shop card not found"
    wait_selector "jd_login_surface" res "${JD_PACKAGE}:id/tv_jd_phone_type" "$LONG_TIMEOUT_MS" ||
        wait_selector "jd_login_surface_text" contains "验证码登录" "$LONG_TIMEOUT_MS" ||
        fail "JD clone login surface did not appear"
    checkpoint "03_jd_login_surface"
    capture_root_diag "03_jd_login_surface"
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
    checkpoint "04_before_get_code"
    capture_root_diag "04_before_get_code"
}

click_get_code_and_capture() {
    if [[ "$CLICK_GET_CODE" != true ]]; then
        log "Stopped before clicking get-code"
        return 0
    fi

    log "Clicking JD get-code and capturing immediate diagnostics"
    printf '%s\n' "$(now_ms)" > "$ROOT_CAPTURE_DIR/05_click_get_epoch_ms.txt"
    adb_shell log -t E2E_JD_CAPTCHA "MARK_BEFORE_GET_CODE $(now_ms)" >/dev/null 2>&1 || true
    if [[ -s "$ROOT_CAPTURE_DIR/get_code_button_xy.txt" ]]; then
        local x y
        read -r x y < "$ROOT_CAPTURE_DIR/get_code_button_xy.txt"
        log "Tap saved get-code coordinate at $x,$y"
        adb_shell input tap "$x" "$y"
    else
        tap_selector "get_code_click" res "${JD_PACKAGE}:id/tv_jd_get_code" "$SHORT_TIMEOUT_MS" ||
            tap_selector "get_code_click_text" contains "获取验证码" "$SHORT_TIMEOUT_MS" ||
            fail "Get-code button disappeared before click"
    fi
    adb_shell log -t E2E_JD_CAPTCHA "MARK_AFTER_GET_CODE $(now_ms)" >/dev/null 2>&1 || true

    local delay label
    for delay in 0.4 1.0 2.0 3.0; do
        sleep "$delay"
        label="05_after_get_${delay}s"
        checkpoint "$label"
        capture_root_diag "$label"
    done

    local end=$((CAPTCHA_CAPTURE_SECONDS > 3 ? CAPTCHA_CAPTURE_SECONDS : 3))
    sleep "$((end - 3))"
    checkpoint "05_after_get_${end}s"
    capture_root_diag "05_after_get_${end}s"
}

main() {
    log "Artifacts: $ARTIFACT_DIR"
    log "Target profile: $PROFILE_NAME serial=$SERIAL"
    prepare_device
    if [[ "$INSTALL_APKS" == true ]]; then
        build_and_install
    fi
    if [[ "$DIRECT_JD" == true ]]; then
        launch_direct_jd_clone
    else
        launch_account_manager
        select_jd_category
        open_new_shop_card
    fi
    prepare_jd_phone_login
    click_get_code_and_capture
    STATUS=0
    write_summary "$STATUS" "completed"
    log "Done. Artifacts: $ARTIFACT_DIR"
}

main "$@"
