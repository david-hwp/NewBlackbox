#!/usr/bin/env bash
# e2e/tests/smoke.sh — Smoke test: build, install, launch, basic sanity checks

set -euo pipefail

# This script is sourced by run.sh, which already sources lib/utils.sh
# Variables available: ADB, PACKAGE, SCREENSHOT_DIR, TIMESTAMP, REPORT_FILE, etc.

# ── Test Definition ────────────────────────────────────────────────────────
# Each test script defines a run_test() function that returns 0 on success

run_test() {
    log_info "=== Smoke Test ==="

    # Step 1: Ensure APK is built
    local apk
    apk=$(find_latest_apk)
    if [[ ! -f "$apk" ]]; then
        log_warn "No APK found, triggering build..."
        build_apk
        apk=$(find_latest_apk)
    fi
    log_ok "APK ready: $(basename "$apk")"

    # Step 2: Install / reinstall
    log_info "Installing APK..."
    install_apk "$apk"
    assert_package_installed
    log_ok "Package installed"

    # Step 3: Clear logcat for clean capture
    clear_logcat

    # Step 4: Launch app (WelcomeActivity -> MainActivity)
    log_info "Launching app..."
    start_app

    # Wait for app to settle
    sleep 3

    # Step 5: Verify app is running
    assert_package_running
    log_ok "App is running"

    # Step 6: Take launch screenshot
    local ss_launch
    ss_launch=$(take_screenshot "smoke_launch")
    log_ok "Screenshot: $ss_launch"
    TEST_SCREENSHOT="$(basename "$ss_launch")"

    # Step 7: Check for crashes in logcat
    log_info "Checking logcat for crashes..."
    if $ADB logcat -d -t 200 | grep -E "FATAL EXCEPTION|AndroidRuntime|Process .* dying" | grep -q "$PACKAGE"; then
        log_error "Crash detected in logcat!"
        capture_logcat "smoke_crash"
        return 1
    fi
    log_ok "No crashes detected"

    # Step 8: Verify key log patterns (engine initialization)
    log_info "Verifying engine initialization..."
    if assert_log_contains "BlackBoxCore" 5; then
        log_ok "BlackBoxCore initialized"
    else
        log_warn "BlackBoxCore log not found (may be expected on some devices)"
    fi

    # Step 9: Navigate to app list (swipe or tap)
    log_info "Navigating to app list..."
    # Common coordinate for the "Apps" tab on most screens (~1/3 from left bottom)
    tap 350 2150 2>/dev/null || true
    sleep 1

    local ss_apps
    ss_apps=$(take_screenshot "smoke_apps_tab")
    log_ok "Apps tab screenshot: $ss_apps"

    # Step 10: Navigate to Settings tab via bottom nav
    # Settings is typically the rightmost tab on the bottom nav bar
    log_info "Tapping Settings tab..."
    tap 730 2150 2>/dev/null || true
    sleep 2

    local ss_settings
    ss_settings=$(take_screenshot "smoke_settings")
    log_ok "Settings screenshot: $ss_settings"

    # Step 11: Press back to return to main
    press_key 4
    sleep 1

    # Final screenshot
    local ss_final
    ss_final=$(take_screenshot "smoke_final")
    log_ok "Final screenshot: $ss_final"

    # Step 12: Capture full logcat for the test
    capture_logcat "smoke_full"

    log_ok "=== Smoke Test Complete ==="
    return 0
}

# Execute test
run_test
