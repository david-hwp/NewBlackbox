# Account Manager JD Captcha Diagnostics

`e2e/tests/run.sh` is a lightweight adb/root wrapper for the JD 秒送 captcha issue. It prepares the device, opens Account Manager directly, switches to JD 秒送, opens an existing `新增店铺` card or creates one, enters the JD clone phone-login flow, clicks `获取验证码`, and immediately captures diagnostics.

## Quick Start

```bash
# Pixel 9 emulator
./e2e/tests/run.sh --serial emulator-5556 --profile pixel9 --skip-build

# Pixel 8 emulator
./e2e/tests/run.sh --serial emulator-5554 --profile pixel8 --skip-build

# Xiaomi physical device
./e2e/tests/run.sh --serial <xiaomi_serial> --profile xiaomi --skip-build

# Stop before clicking JD get-code
./e2e/tests/run.sh --serial emulator-5556 --profile pixel9 --skip-build --no-click
```

## Directory Structure

```text
e2e/
├── profile/
│   ├── pixel8.env
│   ├── pixel9.env
│   └── xiaomi.env
├── tests/
│   └── run.sh
├── artifacts/
│   └── (generated per run)
└── README.md
```

## Profiles

Device differences live in `e2e/profile/*.env`.

| Variable | Purpose |
|----------|---------|
| `PROFILE_NAME` | Run/profile name. |
| `DEVICE_KIND` | `emulator` or `physical`. |
| `AVD_NAME_CONTAINS` | Emulator AVD name selector when `--serial` is omitted. |
| `APP_PACKAGE` | Account Manager package. |
| `APP_LAUNCH_ACTIVITY` | Explicit Activity started through `am start -W -n`. |
| `ENGINE_PACKAGE` | Account Manager engine package. |
| `JD_PACKAGE` | Host JD package used by the clone. |
| `POLL_INTERVAL_MS` | adb UI polling interval. |
| `MEDIUM_TIMEOUT_MS` | Standard control wait timeout. |
| `LONG_TIMEOUT_MS` | Longer app-state wait timeout. |
| `CAPTCHA_CAPTURE_SECONDS` | Post-click root capture duration. |
| `DIALOG_BUTTON_TEXTS` | `|` separated system/app dialog button text list. |

## Artifacts

Each run writes local artifacts to:

```text
e2e/artifacts/<profile>_jd_captcha_<timestamp>/
```

The wrapper captures:

- screenshot PNG files
- UI XML from `uiautomator dump`
- foreground Activity/window snapshots
- `ps -A`
- `debuggerd -b <pid>` for the virtual JD process when found
- `/proc/<pid>/status`, `fd`, and `maps`
- `logcat -b all`
- `dumpsys webviewupdate`
- JD package dump
- `/data/anr` and `/data/tombstones` pulls when `adb root` is available
- `run-summary.json`
