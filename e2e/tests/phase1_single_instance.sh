#!/usr/bin/env bash
# e2e/tests/phase1_single_instance.sh — Phase 1 Single-Instance Mode Smoke Test
#
# Manual test flow:
#   1. Enable single-instance mode in Settings, restart BlackBox
#   2. Launch first clone app (e.g. 美团外卖商家版), wait for full load
#   3. Return to BlackBox, launch second clone app (e.g. 淘宝闪购)
#   4. Open Recent Tasks (swipe up + hold)
#   5. EXPECTED: Only BlackBox + second app visible; first app should be gone
#   6. Tap "X" in BlackBox to kill all running apps (clean state)
#
# This script automates log collection and analysis.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/utils.sh" 2>/dev/null || {
    # Fallback if utils.sh not available
    ADB="${ADB:-adb}"
    log_info() { echo "[INFO] $*"; }
    log_ok()   { echo "[OK]   $*"; }
    log_warn() { echo "[WARN] $*"; }
    log_err()  { echo "[ERR]  $*"; }
}

PACKAGE="top.niunaijun.blackbox"
LOG_DIR="${LOG_DIR:-e2e/logs}"
mkdir -p "$LOG_DIR"

TIMESTAMP=$(date +%Y%m%d_%H%M%S)
LOG_FILE="$LOG_DIR/${TIMESTAMP}_phase1_single_instance.log"

# ── Main Test ──────────────────────────────────────────────────────────────

run_test() {
    log_info "=== Phase 1: Single-Instance Mode Smoke Test ==="
    log_info "Log file: $LOG_FILE"
    echo ""

    # Check device
    if ! $ADB devices | grep -q "device$"; then
        log_err "No Android device connected. Please connect your phone."
        exit 1
    fi
    log_ok "Device connected"

    # Clear logcat
    log_info "Clearing logcat buffer..."
    $ADB logcat -c 2>/dev/null || true

    # Start collecting logs
    log_info "Starting logcat collection..."
    $ADB logcat -s "BlackBoxCore" "BProcessManager" "ActivityStack" "BActivityManager" > "$LOG_FILE" &
    LOGCAT_PID=$!
    sleep 1

    # Launch BlackBox
    log_info "Launching BlackBox..."
    $ADB shell am start -n "$PACKAGE/top.niunaijun.blackboxa.view.main.MainActivity" 2>/dev/null || true
    sleep 2

    # ── Manual steps prompt ──────────────────────────────────────────────
    cat << 'EOF'

========================================
MANUAL TEST STEPS
========================================

Preparation:
  1. Go to BlackBox Settings
  2. Enable "Single Instance Mode" switch
  3. Restart BlackBox when prompted

Test:
  4. Tap the FIRST clone app (e.g. 美团外卖商家版)
     → Wait until it FULLY loads (see order page)

  5. Return to BlackBox (BACK gesture)

  6. Tap the SECOND clone app (e.g. 淘宝闪购)
     → Wait until it appears (login page is fine)

  7. Open Recent Tasks (swipe up from bottom, hold 1s)
     → EXPECTED: Only BlackBox + second app visible
     → The FIRST app should NOT appear in Recent Tasks

  8. In BlackBox, tap the "X" button to kill all running apps
     (creates clean state for next test)

========================================
EOF

    read -r -p "Press ENTER when all steps are complete..."

    # Stop logcat
    kill $LOGCAT_PID 2>/dev/null || true
    sleep 1

    # ── Analysis ─────────────────────────────────────────────────────────
    echo ""
    log_info "Analyzing logs..."
    echo ""

    if [ ! -f "$LOG_FILE" ]; then
        log_err "Log file not found"
        exit 1
    fi

    # 1. Check trigger
    TRIGGER=$(grep -c "Single instance mode: killing other running apps" "$LOG_FILE" || echo 0)
    if [ "$TRIGGER" -gt 0 ]; then
        log_ok "Single-instance TRIGGERED ($TRIGGER time(s))"
        grep "Single instance mode: killing other running apps" "$LOG_FILE" | head -3
    else
        log_err "Single-instance NOT triggered — verify setting is ON and app was restarted"
    fi
    echo ""

    # 2. Check kill results
    KILL=$(grep -c "Single instance mode: killed" "$LOG_FILE" || echo 0)
    if [ "$KILL" -gt 0 ]; then
        log_ok "Process kill executed ($KILL time(s))"
        grep "Single instance mode: killed" "$LOG_FILE" | head -3
    else
        log_err "No process kill recorded"
    fi
    echo ""

    # 3. Check activity finish
    FINISH=$(grep -c "Single instance mode: finished activities" "$LOG_FILE" || echo 0)
    if [ "$FINISH" -gt 0 ]; then
        log_ok "Activity finish executed ($FINISH time(s))"
        grep "Single instance mode: finished activities" "$LOG_FILE" | head -3
    else
        log_warn "No activity finish recorded"
    fi
    echo ""

    # 4. Check for cross-process call
    CROSS=$(grep -c "killAllOtherProcesses" "$LOG_FILE" || echo 0)
    if [ "$CROSS" -gt 0 ]; then
        log_ok "Cross-process AIDL call observed ($CROSS time(s))"
    else
        log_warn "No AIDL call observed in logs"
    fi
    echo ""

    # 5. Check errors
    ERRORS=$(grep -cE "Failed to kill|Failed to finish|RemoteException" "$LOG_FILE" || echo 0)
    if [ "$ERRORS" -gt 0 ]; then
        log_err "Errors detected ($ERRORS):"
        grep -E "Failed to kill|Failed to finish|RemoteException" "$LOG_FILE" | head -5
    else
        log_ok "No errors detected"
    fi
    echo ""

    # 6. Show all relevant lines
    echo "--- All relevant log lines ---"
    grep -E "Single instance|killAllOtherProcesses|finishAllActivitiesExcept" "$LOG_FILE" | tail -30 || echo "(none)"
    echo ""

    # ── Summary ──────────────────────────────────────────────────────────
    echo "========================================"
    echo "TEST SUMMARY"
    echo "========================================"
    printf "%-20s %s\n" "Trigger:" "$([ "$TRIGGER" -gt 0 ] && echo "PASS ✓" || echo "FAIL ✗")"
    printf "%-20s %s\n" "Process kill:" "$([ "$KILL" -gt 0 ] && echo "PASS ✓" || echo "FAIL ✗")"
    printf "%-20s %s\n" "Activity finish:" "$([ "$FINISH" -gt 0 ] && echo "PASS ✓" || echo "WARN ⚠")"
    printf "%-20s %s\n" "AIDL call:" "$([ "$CROSS" -gt 0 ] && echo "PASS ✓" || echo "WARN ⚠")"
    printf "%-20s %s\n" "Errors:" "$([ "$ERRORS" -eq 0 ] && echo "PASS ✓" || echo "FAIL ✗")"
    echo ""
    echo "Full log: $LOG_FILE"

    # Return non-zero if any critical check failed
    if [ "$TRIGGER" -eq 0 ] || [ "$KILL" -eq 0 ] || [ "$ERRORS" -gt 0 ]; then
        return 1
    fi
    return 0
}

run_test
