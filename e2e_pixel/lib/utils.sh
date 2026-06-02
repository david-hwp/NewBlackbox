#!/usr/bin/env bash
# e2e_pixel/lib/utils.sh — Pixel Emulator E2E test utilities
# shellcheck disable=SC2034

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
LOG_DIR="$SCRIPT_DIR/logs"
REPORT_DIR="$SCRIPT_DIR/reports"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$SCRIPT_DIR/report_${TIMESTAMP}.md"

# ADB path resolution
find_adb() {
    for path in \
        "$HOME/Library/Android/sdk/platform-tools/adb" \
        "/opt/homebrew/share/android-commandlinetools/platform-tools/adb" \
        "/Applications/wechatwebdevtools.app/Contents/Resources/bin/adb-macos/adb" \
        adb; do
        if [[ -x "$path" ]] || command -v "$path" &>/dev/null; then
            echo "$path"
            return 0
        fi
    done
    echo "adb"
}

ADB="${ADB:-$(find_adb)}"
# Support ANDROID_SERIAL for multi-device environments
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    ADB="$ADB -s $ANDROID_SERIAL"
fi
PACKAGE="top.niunaijun.blackbox"
APK_DIR="$PROJECT_ROOT/app/build/outputs/apk/debug"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[PASS]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $*"; }

# ── Report ──
init_report() {
    mkdir -p "$LOG_DIR" "$SCREENSHOT_DIR"
    cat > "$REPORT_FILE" <<EOF
# Pixel Emulator E2E Test Report

- **Date**: $(date '+%Y-%m-%d %H:%M:%S')
- **Device**: $($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r' || echo "unknown")
- **Brand**: $($ADB shell getprop ro.product.brand 2>/dev/null | tr -d '\r' || echo "unknown")
- **Android**: $($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r' || echo "unknown") (API $($ADB shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r' || echo "?"))
- **ROM**: $($ADB shell getprop ro.build.display.id 2>/dev/null | tr -d '\r' || echo "unknown")
- **Branch**: $(cd "$PROJECT_ROOT" && git branch --show-current 2>/dev/null || echo "?")
- **Commit**: $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD 2>/dev/null || echo "?")

## Results

| # | Test | Status | Duration |
|---|------|--------|----------|
EOF
    echo "$REPORT_FILE"
}

report_pass() {
    local idx="$1" name="$2" dur="$3"
    printf "| %s | %s | ✅ PASS | %ss |\n" "$idx" "$name" "$dur" >> "$REPORT_FILE"
}

report_fail() {
    local idx="$1" name="$2" dur="$3"
    printf "| %s | %s | ❌ FAIL | %ss |\n" "$idx" "$name" "$dur" >> "$REPORT_FILE"
}

# ── Device Info ──
collect_device_info() {
    local out="$LOG_DIR/${TIMESTAMP}_device_info.txt"
    {
        echo "=== Device Properties ==="
        $ADB shell getprop | grep -E "ro\.(product|build)" | sort
        echo ""
        echo "=== WebView Status ==="
        $ADB shell dumpsys webviewupdate 2>/dev/null || echo "dumpsys webviewupdate unavailable"
        echo ""
        echo "=== Installed WebView Packages ==="
        $ADB shell pm list packages | grep -i webview
        echo ""
        echo "=== GMS Packages ==="
        $ADB shell pm list packages | grep -E "com\.google\.android\.(gms|webview|chrome)" || echo "No GMS packages found"
        echo ""
        echo "=== CPU Architecture ==="
        $ADB shell getprop ro.product.cpu.abi
        echo ""
        echo "=== Memory Info ==="
        $ADB shell cat /proc/meminfo | head -5
    } > "$out"
    echo "$out"
}

# ── Checks ──
check_device() {
    log_info "Checking ADB device..."
    if ! $ADB devices | grep -q "device$"; then
        log_error "No Android device connected"
        return 1
    fi
    local model brand android_ver
    model=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    brand=$($ADB shell getprop ro.product.brand 2>/dev/null | tr -d '\r')
    android_ver=$($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    log_ok "Device connected: $brand $model (Android $android_ver)"
}

check_is_emulator() {
    local model
    model=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    if [[ "$model" == sdk_gphone* || "$model" == emulator* ]]; then
        log_ok "Confirmed Pixel/Android Emulator ($model)"
        return 0
    else
        log_warn "Device may not be an emulator (model=$model). Tests may not be relevant."
        return 1
    fi
}

# ── Build & Install ──
build_apk() {
    log_info "Building debug APK..."
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null)}"
    cd "$PROJECT_ROOT"
    ./gradlew :app:assembleDebug --no-daemon --quiet
    log_ok "Build complete"
}

find_latest_apk() {
    local apk
    apk=$(ls -t "$APK_DIR"/BlackBox_*_universal-debug.apk 2>/dev/null | head -1)
    if [[ -z "$apk" ]]; then
        apk=$(ls -t "$APK_DIR"/*-debug.apk 2>/dev/null | head -1)
    fi
    echo "$apk"
}

install_apk() {
    local apk="${1:-$(find_latest_apk)}"
    if [[ ! -f "$apk" ]]; then
        log_error "APK not found"
        return 1
    fi
    log_info "Installing $(basename "$apk")..."
    $ADB install -r -d "$apk" | tail -1
    log_warn "APK installed. If cloned apps disappeared, please re-add them in BlackBox."
}

# ── App Lifecycle ──
start_app() {
    log_info "Starting BlackBox..."
    $ADB shell am start -n "$PACKAGE/top.niunaijun.blackboxa.view.main.MainActivity" \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>/dev/null || true
    sleep 2
}

stop_app() {
    log_info "Stopping BlackBox..."
    $ADB shell am force-stop "$PACKAGE"
}

# ── Screenshots ──
take_screenshot() {
    local name="${1:-screenshot}"
    mkdir -p "$SCREENSHOT_DIR"
    local file="$SCREENSHOT_DIR/${TIMESTAMP}_${name}.png"
    $ADB shell screencap -p /sdcard/e2e_tmp.png 2>/dev/null || $ADB shell screencap -p /data/local/tmp/e2e_tmp.png
    $ADB pull /sdcard/e2e_tmp.png "$file" 2>/dev/null || $ADB pull /data/local/tmp/e2e_tmp.png "$file"
    $ADB shell rm -f /sdcard/e2e_tmp.png /data/local/tmp/e2e_tmp.png 2>/dev/null || true
    echo "$file"
}

# ── UI Automation ──
tap() {
    local x="$1" y="$2"
    $ADB shell input tap "$x" "$y"
}

press_key() {
    $ADB shell input keyevent "$1"
}

# ── Log Capture ──
capture_logcat() {
    local name="${1:-logcat}"
    mkdir -p "$LOG_DIR"
    local file="$LOG_DIR/${TIMESTAMP}_${name}.log"
    $ADB logcat -d -t 1000 | grep -E "BlackBoxCore|BWebViewEnvironment|WebViewFactoryProxy|IWebViewUpdateServiceProxy|AndroidRuntime|FATAL|BActivityThread" > "$file" 2>/dev/null || true
    echo "$file"
}

clear_logcat() {
    $ADB logcat -c 2>/dev/null || true
}

assert_log_contains() {
    local pattern="$1"
    local timeout="${2:-5}"
    log_info "Waiting for log pattern: $pattern (timeout ${timeout}s)..."
    for ((i=0; i<timeout*2; i++)); do
        if $ADB logcat -d -t 200 | grep -q "$pattern"; then
            return 0
        fi
        sleep 0.5
    done
    log_error "Log pattern not found: $pattern"
    return 1
}

# ── UI Element Detection ──
get_ui_element_center() {
    local text_pattern="$1"
    local tmp_xml="/sdcard/e2e_ui_dump.xml"
    local tmp_local="/tmp/e2e_ui_dump_$$.xml"

    if ! $ADB shell uiautomator dump "$tmp_xml" >/dev/null 2>&1; then
        return 1
    fi

    if ! $ADB pull "$tmp_xml" "$tmp_local" >/dev/null 2>&1; then
        return 1
    fi

    local bounds
    bounds=$(grep -o "text=\"[^\"]*${text_pattern}[^\"]*\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" "$tmp_local" 2>/dev/null | head -1)
    rm -f "$tmp_local"

    if [[ -z "$bounds" ]]; then
        return 1
    fi

    local x1 y1 x2 y2 cx cy
    x1=$(echo "$bounds" | grep -o '\[[0-9]*,' | head -1 | tr -d '[' | tr -d ',')
    y1=$(echo "$bounds" | grep -o ',[0-9]*\]' | head -1 | tr -d ',' | tr -d ']')
    x2=$(echo "$bounds" | grep -o '\[[0-9]*,' | tail -1 | tr -d '[' | tr -d ',')
    y2=$(echo "$bounds" | grep -o ',[0-9]*\]' | tail -1 | tr -d ',' | tr -d ']')

    cx=$(( (x1 + x2) / 2 ))
    cy=$(( (y1 + y2) / 2 ))

    echo "$cx $cy"
}

find_element_by_text() {
    local text="$1"
    local coords
    if coords=$(get_ui_element_center "$text" 2>/dev/null); then
        echo "$coords"
        return 0
    fi
    return 1
}

# ── Screenshot Comparison ──
screenshot_hash() {
    local file="$1"
    if [[ -f "$file" ]]; then
        shasum -a 256 "$file" 2>/dev/null | awk '{print $1}'
    else
        echo ""
    fi
}

compare_screenshots() {
    local file1="$1"
    local file2="$2"
    local hash1
    local hash2
    hash1=$(screenshot_hash "$file1")
    hash2=$(screenshot_hash "$file2")
    if [[ "$hash1" == "$hash2" ]]; then
        return 0
    else
        return 1
    fi
}

# ── Timing ──
start_timer() {
    echo "$(date +%s)"
}

elapsed() {
    local start="$1"
    local now
    now=$(date +%s)
    echo "$((now - start))"
}
