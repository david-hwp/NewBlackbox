# BlackBox E2E Tests

End-to-end automation tests for NewBlackbox, driven via ADB.

## Quick Start

```bash
# Run all tests (uses existing APK if available)
./e2e/run.sh

# Build + reinstall + run all tests
./e2e/run.sh --build --reinstall

# Run only smoke test
./e2e/run.sh smoke

# Run Phase 1 single-instance test with clean data
./e2e/run.sh --clean phase1

# Use custom ADB path
ADB=/path/to/adb ./e2e/run.sh
```

## Directory Structure

```
e2e/
├── run.sh                        # Main test runner entry point
├── lib/
│   └── utils.sh                  # Shared utilities (device, ADB, screenshots)
├── tests/
│   ├── smoke.sh                  # Basic smoke test (install, launch, screenshot)
│   └── phase1_single_instance.sh # Phase 1 single-instance mode validation
├── screenshots/                  # Screenshots captured during tests
│   └── (auto-created)
├── logs/                         # Logcat dumps
│   └── (auto-created)
└── README.md                     # This file
```

## Test Scripts

| Script | Description | Key Checks |
|--------|-------------|------------|
| `smoke.sh` | Basic sanity check | Build, install, launch, no crashes, screenshot |
| `phase1_single_instance.sh` | Single-instance mode | Toggle ON/OFF, setting persistence, process behavior |

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ADB` | Auto-detected | Path to adb executable |
| `JAVA_HOME` | Auto-detected | JDK 21 path for Gradle build |

## Adding a New Test

1. Create `e2e/tests/my_feature.sh`
2. Define a `run_test()` function
3. Source `./e2e/lib/utils.sh` for helpers
4. Use `take_screenshot`, `tap`, `assert_*` helpers
5. Run: `./e2e/run.sh my_feature`

### Example Test Template

```bash
#!/usr/bin/env bash
set -euo pipefail

run_test() {
    log_info "=== My Feature Test ==="

    # Setup
    install_apk
    start_app
    sleep 2

    # Action
    tap 500 800
    sleep 1

    # Assert + Screenshot
    assert_package_running
    TEST_SCREENSHOT=$(basename "$(take_screenshot "my_feature")")

    log_ok "=== My Feature Test Complete ==="
    return 0
}

run_test
```

## Report Output

After each run, a Markdown report is generated:

```
e2e/report_20250530_143022.md
```

Example report:

| # | Test | Status | Duration | Screenshot |
|---|------|--------|----------|------------|
| 1 | smoke | ✅ PASS | 18s | smoke_launch.png |
| 2 | phase1 | ✅ PASS | 25s | phase1_settings_default.png |

## Screen-Off / Lock Screen Testing

### Can tests run with the screen off?

| Capability | Status | Notes |
|-----------|--------|-------|
| `adb shell input tap` while off | ✅ Works | Injected at system level, but may be intercepted by lock screen |
| `adb shell am start` while off | ✅ Works | App starts in background; visible after wake |
| Screenshot while off | ❌ Black | `screencap` outputs a black image — UI assertions fail |
| Logcat / process checks while off | ✅ Works | Fully unaffected by screen state |

**Bottom line**: Backend/logic tests can run screen-off; any test requiring screenshots or UI interaction needs the screen **on and unlocked**.

### Auto-wake on test start

`run.sh` automatically calls `ensure_unlocked()` before each run:

1. Presses **POWER** (`keyevent 26`) if screen is off
2. Performs an **upward swipe** to dismiss swipe-only lock
3. Warns if a PIN/password is still blocking the screen

If your device uses a **PIN/password/pattern**, add the unlock sequence to `lib/utils.sh`:

```bash
unlock_pin() {
    wake_screen
    # Tap PIN digits (example: 1-2-3-4)
    tap 180 1500  # digit 1
    tap 540 1500  # digit 2
    tap 900 1500  # digit 3
    tap 540 1800  # digit 4
    tap 540 2100  # OK/Enter
    sleep 1
}
```

Then update `ensure_unlocked()` to call `unlock_pin` instead of `unlock_swipe`.

### Testing with screen deliberately off

To keep the screen off during a test (e.g. background-process validation):

```bash
run_test() {
    # Ensure screen is on for setup
    ensure_unlocked

    # Install and launch
    install_apk
    start_app
    sleep 3

    # Turn screen off
    $ADB shell input keyevent 26
    sleep 2

    # ... run background checks (logs, pid, etc.) ...

    # Wake up for final screenshot
    wake_screen
    take_screenshot "after_background_test"
}
```

## Device Coordinate Reference (MIX 2S — 1080×2160)

| UI Element | Approx Coordinates |
|------------|-------------------|
| Bottom nav — Apps tab | (350, 2150) |
| Bottom nav — Settings tab | (730, 2150) |
| Settings toggle (single-instance) | (930, 600) |
| Center of screen | (540, 1080) |
| Swipe unlock (bottom → top) | (540, 1800) → (540, 600) |

> **Tip:** For other devices, use `adb shell wm size` to get resolution,
> then scale coordinates proportionally.
