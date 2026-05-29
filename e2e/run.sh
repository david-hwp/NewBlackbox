#!/usr/bin/env bash
# e2e/run.sh — Main entry point for BlackBox E2E test suite
#
# Usage:
#   ./e2e/run.sh                     # Run all tests
#   ./e2e/run.sh smoke               # Run only smoke tests
#   ./e2e/run.sh phase1              # Run Phase 1 single-instance tests
#   ./e2e/run.sh --build             # Build APK before running tests
#   ./e2e/run.sh --reinstall         # Reinstall app before running tests
#   ./e2e/run.sh --clean             # Clear app data before running tests
#   ./e2e/run.sh --no-build          # Skip build step (use existing APK)
#
# Examples:
#   ./e2e/run.sh --build --reinstall smoke
#   ./e2e/run.sh --clean phase1
#   ADB=/custom/adb ./e2e/run.sh

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source utilities
source "$SCRIPT_DIR/lib/utils.sh"

# ── Argument Parsing ───────────────────────────────────────────────────────
RUN_BUILD=false
RUN_REINSTALL=false
RUN_CLEAN=false
SKIP_BUILD=false
FILTER=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --build)     RUN_BUILD=true; shift ;;
        --reinstall) RUN_REINSTALL=true; shift ;;
        --clean)     RUN_CLEAN=true; shift ;;
        --no-build)  SKIP_BUILD=true; shift ;;
        -h|--help)
            sed -n '3,20p' "$0"
            exit 0
            ;;
        *)           FILTER="$1"; shift ;;
    esac
done

# ── Pre-run Setup ──────────────────────────────────────────────────────────
log_info "═══════════════════════════════════════════════"
log_info "  BlackBox E2E Test Suite"
log_info "═══════════════════════════════════════════════"
echo ""

RUN_START=$(start_timer)

check_device || exit 1
ensure_unlocked

# Initialize report
REPORT_FILE=$(init_report)

# Build if requested (or by default if no APK exists)
if [[ "$RUN_BUILD" == true ]]; then
    build_apk
elif [[ "$SKIP_BUILD" != true ]]; then
    local_apk=$(find_latest_apk)
    if [[ ! -f "$local_apk" ]]; then
        log_warn "No debug APK found, building..."
        build_apk
    fi
fi

# Reinstall if requested
if [[ "$RUN_REINSTALL" == true ]]; then
    install_apk
fi

# Clean data if requested
if [[ "$RUN_CLEAN" == true ]]; then
    clear_app_data
fi

# ── Discover Tests ─────────────────────────────────────────────────────────
declare -a TEST_SCRIPTS=()

if [[ -n "$FILTER" ]]; then
    # Run specific test
    if [[ -f "$SCRIPT_DIR/tests/${FILTER}.sh" ]]; then
        TEST_SCRIPTS+=("$SCRIPT_DIR/tests/${FILTER}.sh")
    else
        log_error "Test not found: $SCRIPT_DIR/tests/${FILTER}.sh"
        exit 1
    fi
else
    # Auto-discover all test scripts
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

# ── Run Tests ──────────────────────────────────────────────────────────────
PASS_COUNT=0
FAIL_COUNT=0
TEST_INDEX=0

for test_script in "${TEST_SCRIPTS[@]}"; do
    TEST_INDEX=$((TEST_INDEX + 1))
    test_name=$(basename "$test_script" .sh)

    log_info "───────────────────────────────────────────────"
    log_info "[$TEST_INDEX/${#TEST_SCRIPTS[@]}] Running: $test_name"
    log_info "───────────────────────────────────────────────"

    # Source the test (it should call run_test or similar)
    TEST_RESULT=0
    TEST_DURATION=0
    TEST_SCREENSHOT=""

    test_start=$(start_timer)

    # Each test script is sourced with a helper function available
    source "$test_script" || TEST_RESULT=$?

    TEST_DURATION=$(elapsed "$test_start")

    if [[ $TEST_RESULT -eq 0 ]]; then
        log_ok "Test PASSED: $test_name (${TEST_DURATION}s)"
        PASS_COUNT=$((PASS_COUNT + 1))
        report_pass "$TEST_INDEX" "$test_name" "$TEST_DURATION" "${TEST_SCREENSHOT:-"-"}"
    else
        log_error "Test FAILED: $test_name (${TEST_DURATION}s)"
        FAIL_COUNT=$((FAIL_COUNT + 1))
        report_fail "$TEST_INDEX" "$test_name" "$TEST_DURATION" "${TEST_SCREENSHOT:-"-"}"
    fi
    echo ""
done

# ── Summary ────────────────────────────────────────────────────────────────
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

# Exit with failure if any test failed
[[ $FAIL_COUNT -eq 0 ]]
