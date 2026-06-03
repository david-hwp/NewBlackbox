#!/usr/bin/env bash
# e2e_pixel/run.sh — Pixel Emulator E2E test runner
#
# Usage:
#   ./e2e_pixel/run.sh               # Run all Pixel tests
#   ./e2e_pixel/run.sh jd_captcha    # Run only JD captcha test
#   ./e2e_pixel/run.sh --build       # Build APK before testing
#   ./e2e_pixel/run.sh --reinstall   # Reinstall APK before testing
#   ADB=/path/to/adb ./e2e_pixel/run.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source utilities
source "$SCRIPT_DIR/lib/utils.sh"

# ── Argument Parsing ──
RUN_BUILD=false
RUN_REINSTALL=false
FILTER=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --build)      RUN_BUILD=true; shift ;;
        --reinstall)  RUN_REINSTALL=true; shift ;;
        -h|--help)
            sed -n '3,12p' "$0"
            exit 0
            ;;
        *)            FILTER="$1"; shift ;;
    esac
done

# ── Pre-run ──
log_info "═══════════════════════════════════════════════"
log_info "  Pixel Emulator E2E Test Suite"
log_info "═══════════════════════════════════════════════"
echo ""

RUN_START=$(start_timer)

check_device || exit 1
check_is_emulator || true

REPORT_FILE=$(init_report)

# Build if requested
if [[ "$RUN_BUILD" == true ]]; then
    build_apk
fi

# Reinstall if requested
if [[ "$RUN_REINSTALL" == true ]]; then
    install_apk
fi

# ── Discover Tests ──
declare -a TEST_SCRIPTS=()

if [[ -n "$FILTER" ]]; then
    if [[ -f "$SCRIPT_DIR/tests/${FILTER}.sh" ]]; then
        TEST_SCRIPTS+=("$SCRIPT_DIR/tests/${FILTER}.sh")
    else
        log_error "Test not found: $SCRIPT_DIR/tests/${FILTER}.sh"
        exit 1
    fi
else
    for f in "$SCRIPT_DIR"/tests/*.sh; do
        [[ -f "$f" ]] && TEST_SCRIPTS+=("$f")
    done
fi

if [[ ${#TEST_SCRIPTS[@]} -eq 0 ]]; then
    log_warn "No test scripts found in $SCRIPT_DIR/tests/"
    exit 0
fi

log_info "Found ${#TEST_SCRIPTS[@]} test(s) to run"
echo ""

# ── Run Tests ──
PASS_COUNT=0
FAIL_COUNT=0
TEST_INDEX=0

for test_script in "${TEST_SCRIPTS[@]}"; do
    TEST_INDEX=$((TEST_INDEX + 1))
    test_name=$(basename "$test_script" .sh)

    log_info "───────────────────────────────────────────────"
    log_info "[$TEST_INDEX/${#TEST_SCRIPTS[@]}] Running: $test_name"
    log_info "───────────────────────────────────────────────"

    TEST_RESULT=0
    TEST_DURATION=0

    test_start=$(start_timer)
    # Use bash to run the test script in a subshell so 'set -e' works correctly
    bash "$test_script" || TEST_RESULT=$?
    TEST_DURATION=$(elapsed "$test_start")

    if [[ $TEST_RESULT -eq 0 ]]; then
        log_ok "Test PASSED: $test_name (${TEST_DURATION}s)"
        PASS_COUNT=$((PASS_COUNT + 1))
        report_pass "$TEST_INDEX" "$test_name" "$TEST_DURATION"
    else
        log_error "Test FAILED: $test_name (${TEST_DURATION}s)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        report_fail "$TEST_INDEX" "$test_name" "$TEST_DURATION"
    fi
    echo ""
done

# ── Summary ──
TOTAL=$((PASS_COUNT + FAIL_COUNT))

cat >> "$REPORT_FILE" <<EOF

## Summary

- **Total**: $TOTAL
- **Passed**: $PASS_COUNT ✅
- **Failed**: $FAIL_COUNT ❌
- **Duration**: $(elapsed "$RUN_START")s

EOF

log_info "═══════════════════════════════════════════════"
log_info "  Test Run Complete"
log_info "═══════════════════════════════════════════════"
log_info "  Total:  $TOTAL"
log_info "  Passed: $PASS_COUNT"
log_info "  Failed: $FAIL_COUNT"
log_info "  Report: $REPORT_FILE"
log_info "  Screenshots: $SCREENSHOT_DIR"
log_info "  Logs: $LOG_DIR"
log_info "═══════════════════════════════════════════════"

[[ $FAIL_COUNT -eq 0 ]]
