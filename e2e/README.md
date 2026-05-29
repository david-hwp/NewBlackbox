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

## Device Coordinate Reference (MIX 2S — 1080×2160)

| UI Element | Approx Coordinates |
|------------|-------------------|
| Bottom nav — Apps tab | (350, 2150) |
| Bottom nav — Settings tab | (730, 2150) |
| Settings toggle (single-instance) | (930, 600) |
| Center of screen | (540, 1080) |

> **Tip:** For other devices, use `adb shell wm size` to get resolution,
> then scale coordinates proportionally.
