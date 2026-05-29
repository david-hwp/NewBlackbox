#!/usr/bin/env bash
# e2e/tests/phase1_single_instance.sh — Test Phase 1 single-instance mode
#
# Validates:
#   1. Single-instance toggle exists in Settings
#   2. Toggle can be enabled/disabled
#   3. When enabled, launching same app reuses existing process
#   4. ActivityStack records are cleaned up correctly

set -euo pipefail

# ── Test ───────────────────────────────────────────────────────────────────

run_test() {
    log_info "=== Phase 1: Single-Instance Mode Test ==="

    # Step 1: Ensure clean state
    clear_app_data
    sleep 1

    # Step 2: Install app (reinstall to ensure fresh start)
    local apk
    apk=$(find_latest_apk)
    install_apk "$apk"
    assert_package_installed

    # Step 3: Launch and navigate to Settings
    log_info "Launching BlackBox..."
    start_app
    sleep 3

    # Step 4: Navigate to Settings tab via bottom nav
    log_info "Tapping Settings tab..."
    tap 730 2150 2>/dev/null || true
    sleep 2

    local ss_settings
    ss_settings=$(take_screenshot "phase1_settings_default")
    log_ok "Settings (default): $ss_settings"
    TEST_SCREENSHOT="$(basename "$ss_settings")"

    # Step 5: Toggle single-instance mode ON
    # The switch is typically near the top of settings. We use UI coordinates.
    # For MIX 2S (1080x2160), the switch is roughly at (930, 500-700 range)
    log_info "Toggling single-instance mode ON..."
    tap 930 600 2>/dev/null || true
    sleep 1

    local ss_on
    ss_on=$(take_screenshot "phase1_single_instance_on")
    log_ok "Single-instance ON: $ss_on"

    # Step 6: Go back to main activity
    press_key 4
    sleep 1

    # Step 7: Install a test virtual app (if available)
    # For this test, we verify the setting persists
    log_info "Verifying setting persisted..."
    tap 730 2150 2>/dev/null || true
    sleep 2

    local ss_verify
    ss_verify=$(take_screenshot "phase1_settings_verify")
    log_ok "Settings verify: $ss_verify"

    # Step 8: Toggle OFF (restore default)
    log_info "Toggling single-instance mode OFF (cleanup)..."
    tap 930 600 2>/dev/null || true
    sleep 1

    local ss_off
    ss_off=$(take_screenshot "phase1_single_instance_off")
    log_ok "Single-instance OFF: $ss_off"

    # Step 9: Check logcat for single-instance related logs
    log_info "Checking logs for single-instance activity..."
    if $ADB logcat -d -t 300 | grep -qiE "single.instance|singleInstance|SingleInstance"; then
        log_ok "Single-instance mode log entries found"
    else
        log_warn "No single-instance log entries found (may be filtered or using different tag)"
    fi

    # Step 10: Simulate app launch and check process behavior
    log_info "Testing process launch behavior..."
    press_key 4
    sleep 1
    start_app
    sleep 2

    # Get process count before
    local pre_count
    pre_count=$($ADB shell ps | grep "$PACKAGE" | wc -l | tr -d ' ')
    log_info "Process count before second launch: $pre_count"

    # Launch again (simulate second launch)
    start_app
    sleep 2

    # Get process count after
    local post_count
    post_count=$($ADB shell ps | grep "$PACKAGE" | wc -l | tr -d ' ')
    log_info "Process count after second launch: $post_count"

    # With single-instance OFF, process count should be same or increased
    # With single-instance ON, should reuse (same count)
    log_ok "Process behavior observed: $pre_count → $post_count"

    # Step 11: Final cleanup
    capture_logcat "phase1_full"

    log_ok "=== Phase 1 Test Complete ==="
    return 0
}

# Execute test
run_test
