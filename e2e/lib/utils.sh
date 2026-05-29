#!/usr/bin/env bash
# e2e/lib/utils.sh — Shared utilities for BlackBox E2E tests
# shellcheck disable=SC2034

set -euo pipefail

# ── Configuration ──────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
SCREENSHOT_DIR="$SCRIPT_DIR/screenshots"
LOG_DIR="$SCRIPT_DIR/logs"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$SCRIPT_DIR/report_${TIMESTAMP}.md"

# ADB path resolution (fallback chain)
find_adb() {
    if command -v adb &>/dev/null; then
        echo "adb"
    elif [[ -x "/opt/homebrew/share/android-commandlinetools/platform-tools/adb" ]]; then
        echo "/opt/homebrew/share/android-commandlinetools/platform-tools/adb"
    elif [[ -x "/Applications/wechatwebdevtools.app/Contents/Resources/bin/adb-macos/adb" ]]; then
        echo "/Applications/wechatwebdevtools.app/Contents/Resources/bin/adb-macos/adb"
    elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
        echo "$HOME/Library/Android/sdk/platform-tools/adb"
    else
        echo "adb"
    fi
}

ADB="${ADB:-$(find_adb)}"
PACKAGE="top.niunaijun.blackbox"
APK_DIR="$PROJECT_ROOT/app/build/outputs/apk/debug"

# Colors for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# ── Logging ────────────────────────────────────────────────────────────────
log_info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
log_ok()    { echo -e "${GREEN}[PASS]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[FAIL]${NC}  $*"; }

# ── Report ─────────────────────────────────────────────────────────────────
init_report() {
    mkdir -p "$LOG_DIR" "$SCREENSHOT_DIR"
    cat > "$REPORT_FILE" <<EOF
# BlackBox E2E Test Report

- **Date**: $(date '+%Y-%m-%d %H:%M:%S')
- **Device**: $(${ADB} shell getprop ro.product.model 2>/dev/null || echo "unknown")
- **Android**: $(${ADB} shell getprop ro.build.version.release 2>/dev/null || echo "unknown") (API $(${ADB} shell getprop ro.build.version.sdk 2>/dev/null || echo "?"))
- **Branch**: $(cd "$PROJECT_ROOT" && git branch --show-current 2>/dev/null || echo "?")
- **Commit**: $(cd "$PROJECT_ROOT" && git rev-parse --short HEAD 2>/dev/null || echo "?")

## Results

| # | Test | Status | Duration | Screenshot |
|---|------|--------|----------|------------|
EOF
    echo "$REPORT_FILE"
}

report_pass() {
    local idx="$1" name="$2" dur="$3" ss="$4"
    printf "| %s | %s | ✅ PASS | %ss | %s |\n" "$idx" "$name" "$dur" "$ss" >> "$REPORT_FILE"
}

report_fail() {
    local idx="$1" name="$2" dur="$3" ss="$4"
    printf "| %s | %s | ❌ FAIL | %ss | %s |\n" "$idx" "$name" "$dur" "$ss" >> "$REPORT_FILE"
}

# ── Device Checks ──────────────────────────────────────────────────────────
check_device() {
    log_info "Checking ADB device..."
    if ! $ADB devices | grep -q "device$"; then
        log_error "No Android device connected via ADB"
        echo ""
        echo "Troubleshooting:"
        echo "  1. Connect device via USB"
        echo "  2. Enable Developer Options → USB Debugging"
        echo "  3. Accept the RSA fingerprint dialog on device"
        echo "  4. Run: $ADB devices"
        return 1
    fi
    local model
    model=$($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')
    local android_ver
    android_ver=$($ADB shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
    log_ok "Device connected: $model (Android $android_ver)"
}

# ── Build ──────────────────────────────────────────────────────────────────
build_apk() {
    log_info "Building debug APK..."
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null)}"
    cd "$PROJECT_ROOT"
    ./gradlew :app:assembleDebug --no-daemon --quiet
    log_ok "Build complete"
}

# ── APK Helpers ────────────────────────────────────────────────────────────
find_latest_apk() {
    local pattern="BlackBox_*_universal-debug.apk"
    local apk
    apk=$(ls -t "$APK_DIR"/$pattern 2>/dev/null | head -1)
    if [[ -z "$apk" ]]; then
        # fallback: any debug APK
        apk=$(ls -t "$APK_DIR"/*-debug.apk 2>/dev/null | head -1)
    fi
    echo "$apk"
}

install_apk() {
    local apk="${1:-$(find_latest_apk)}"
    if [[ ! -f "$apk" ]]; then
        log_error "APK not found: $apk"
        return 1
    fi
    log_info "Installing $(basename "$apk")..."
    $ADB install -r -d "$apk" | tail -1
}

uninstall_app() {
    log_info "Uninstalling $PACKAGE..."
    $ADB uninstall "$PACKAGE" 2>/dev/null || true
}

# ── App Lifecycle ──────────────────────────────────────────────────────────
start_app() {
    log_info "Starting BlackBox..."
    # Launcher activity is under the blackboxa subpackage
    $ADB shell am start -n "$PACKAGE/top.niunaijun.blackboxa.view.main.MainActivity" \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER 2>/dev/null || \
    $ADB shell am start -n "$PACKAGE/top.niunaijun.blackboxa.view.main.WelcomeActivity" \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER
    sleep 2
}

stop_app() {
    log_info "Stopping BlackBox..."
    $ADB shell am force-stop "$PACKAGE"
}

clear_app_data() {
    log_info "Clearing app data..."
    $ADB shell pm clear "$PACKAGE"
}

# ── Screenshots ────────────────────────────────────────────────────────────
take_screenshot() {
    local name="${1:-screenshot}"
    local file="$SCREENSHOT_DIR/${TIMESTAMP}_${name}.png"
    $ADB shell screencap -p /sdcard/e2e_tmp.png 2>/dev/null || $ADB shell screencap -p /data/local/tmp/e2e_tmp.png
    $ADB pull /sdcard/e2e_tmp.png "$file" 2>/dev/null || $ADB pull /data/local/tmp/e2e_tmp.png "$file"
    $ADB shell rm -f /sdcard/e2e_tmp.png /data/local/tmp/e2e_tmp.png 2>/dev/null || true
    echo "$file"
}

# ── UI Automation ──────────────────────────────────────────────────────────
tap() {
    local x="$1" y="$2"
    $ADB shell input tap "$x" "$y"
}

swipe() {
    local x1="$1" y1="$2" x2="$3" y2="$4"
    $ADB shell input swipe "$x1" "$y1" "$x2" "$y2"
}

input_text() {
    $ADB shell input text "$*"
}

press_key() {
    $ADB shell input keyevent "$1"
}

# ── Log Capture ────────────────────────────────────────────────────────────
capture_logcat() {
    local name="${1:-logcat}"
    local file="$LOG_DIR/${TIMESTAMP}_${name}.log"
    # Dump last 500 lines with BlackBox filters
    $ADB logcat -d -t 500 | grep -E "BlackBoxCore|BActivityThread|FakeCore|HookManager|BinderInvocationStub|AndroidRuntime|FATAL" > "$file" 2>/dev/null || true
    echo "$file"
}

clear_logcat() {
    $ADB logcat -c 2>/dev/null || true
}

# ── Assertions ─────────────────────────────────────────────────────────────
assert_package_installed() {
    if $ADB shell pm list packages | grep -q "^package:${PACKAGE}$"; then
        return 0
    else
        log_error "Package $PACKAGE is not installed"
        return 1
    fi
}

assert_package_running() {
    if $ADB shell pidof "$PACKAGE" >/dev/null 2>&1; then
        return 0
    else
        log_error "Package $PACKAGE is not running"
        return 1
    fi
}

assert_log_contains() {
    local pattern="$1"
    local timeout="${2:-5}"
    log_info "Waiting for log pattern: $pattern (timeout ${timeout}s)..."
    for ((i=0; i<timeout*2; i++)); do
        if $ADB logcat -d -t 100 | grep -q "$pattern"; then
            return 0
        fi
        sleep 0.5
    done
    log_error "Log pattern not found: $pattern"
    return 1
}

# ── Process Management ─────────────────────────────────────────────────────
kill_all_blackbox_processes() {
    log_info "Killing all BlackBox processes..."
    # Get all PIDs for the package
    local pids
    pids=$($ADB shell ps | grep "$PACKAGE" | awk '{print $2}' | tr '\n' ' ')
    if [[ -n "$pids" ]]; then
        for pid in $pids; do
            $ADB shell kill "$pid" 2>/dev/null || true
        done
    fi
}

# ── Timing ─────────────────────────────────────────────────────────────────
start_timer() {
    echo "$(date +%s)"
}

elapsed() {
    local start="$1"
    local now
    now=$(date +%s)
    echo "$((now - start))"
}

# ── Entry guard ────────────────────────────────────────────────────────────
# When sourced, export these helpers. When executed directly, print help.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "BlackBox E2E Test Utilities"
    echo "Usage: source $0"
    echo ""
    echo "Exported functions:"
    grep "^[a-z_]*() {" "$0" | sed 's/() {/()/' | sed 's/^/  /'
fi
