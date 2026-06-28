#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import sys
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

from crawl_authorized_shop_orders import cleanup_old_shop_files, is_supported_target, run_platform_fetcher


def test_is_supported_target_accepts_authorized_meituan_shape() -> None:
    assert is_supported_target(
        {
            "systemShopId": 194,
            "userPhone": "15200837196",
            "controlShopId": "system-194",
            "platform": "mtwm",
        }
    )


def test_is_supported_target_rejects_missing_or_unsupported_shape() -> None:
    assert not is_supported_target({"systemShopId": 194, "controlShopId": "system-194", "platform": "mtwm"})

    assert is_supported_target(
        {
            "systemShopId": 76,
            "userPhone": "15200837196",
            "controlShopId": "16081572",
            "platform": "jdms",
        }
    )
    assert is_supported_target(
        {
            "systemShopId": 120,
            "userPhone": "15200837196",
            "controlShopId": "1184657317",
            "platform": "tbwm",
        }
    )
    assert not is_supported_target(
        {
            "systemShopId": 91,
            "userPhone": "15200837196",
            "controlShopId": "123",
            "platform": "unsupported",
        }
    )


def test_cleanup_old_shop_files_only_removes_expired_regular_files() -> None:
    now = datetime(2026, 6, 27, 12, 0, 0)
    with tempfile.TemporaryDirectory() as tmp:
        shop_output = Path(tmp)
        old_file = shop_output / "meituan_orders_194_20260601000000.json"
        recent_file = shop_output / "meituan_orders_194_20260627110000.json"
        nested_dir = shop_output / "archive"
        symlink_path = shop_output / "latest-link.json"
        target_file = shop_output / "link-target.json"

        old_file.write_text("old", encoding="utf-8")
        recent_file.write_text("recent", encoding="utf-8")
        target_file.write_text("target", encoding="utf-8")
        nested_dir.mkdir()
        os.symlink(target_file, symlink_path)

        old_ts = (now - timedelta(days=8)).timestamp()
        recent_ts = (now - timedelta(days=2)).timestamp()
        os.utime(old_file, (old_ts, old_ts))
        os.utime(recent_file, (recent_ts, recent_ts))
        os.utime(target_file, (old_ts, old_ts))

        removed = cleanup_old_shop_files(shop_output, 7, now=now)

        assert removed == 2
        assert not old_file.exists()
        assert recent_file.exists()
        assert nested_dir.exists()
        assert symlink_path.is_symlink()
        assert not target_file.exists()


def test_run_platform_fetcher_dispatches_by_platform() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        base_dir = Path(tmp)
        fetcher_dir = base_dir / "fetcher"
        python_bin = base_dir / "python3-bin"
        fetcher = fetcher_dir / "jdms_orders.py"
        fetcher_dir.mkdir()
        python_bin.write_text(
            f"#!{sys.executable}\n"
            "import json, os, sys\n"
            "payload = os.path.join(os.environ['OUTPUT_DIR'], 'payload.json')\n"
            "open(payload, 'w', encoding='utf-8').write('{}')\n"
            "print(json.dumps({'payload': payload, 'orders': 2, 'labels': 3, 'platform': os.environ.get('ZR_PLATFORM')}))\n",
            encoding="utf-8",
        )
        fetcher.write_text("# placeholder\n", encoding="utf-8")
        python_bin.chmod(python_bin.stat().st_mode | stat.S_IXUSR)
        args = type(
            "Args",
            (),
            {
                "base_dir": base_dir,
                "fetcher_dir": fetcher_dir,
                "output_dir": base_dir / "out",
                "output_retention_days": 7,
                "control_url": "http://127.0.0.1:14501",
                "collect_timeout_seconds": 10,
            },
        )()
        old_path = os.environ.get("PATH", "")
        os.environ["PATH"] = str(base_dir) + os.pathsep + old_path
        try:
            fake_python = base_dir / "python3"
            fake_python.symlink_to(python_bin)
            result = run_platform_fetcher(
                args,
                {
                    "systemShopId": 76,
                    "userPhone": "15200837196",
                    "controlShopId": "16081572",
                    "shopName": "罗家臭豆腐",
                    "platform": "jdms",
                },
            )
        finally:
            os.environ["PATH"] = old_path

        assert result["orders"] == 2
        assert result["platform"] == "jdms"
        assert Path(result["payload"]).exists()


def test_run_platform_fetcher_dispatches_tbwm() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        base_dir = Path(tmp)
        fetcher_dir = base_dir / "fetcher"
        python_bin = base_dir / "python3-bin"
        fetcher = fetcher_dir / "tbwm_orders.py"
        fetcher_dir.mkdir()
        python_bin.write_text(
            f"#!{sys.executable}\n"
            "import json, os\n"
            "payload = os.path.join(os.environ['OUTPUT_DIR'], 'payload.json')\n"
            "open(payload, 'w', encoding='utf-8').write('{}')\n"
            "print(json.dumps({'payload': payload, 'orders': 1, 'labels': 1, 'platform': os.environ.get('ZR_PLATFORM')}))\n",
            encoding="utf-8",
        )
        fetcher.write_text("# placeholder\n", encoding="utf-8")
        python_bin.chmod(python_bin.stat().st_mode | stat.S_IXUSR)
        args = type(
            "Args",
            (),
            {
                "base_dir": base_dir,
                "fetcher_dir": fetcher_dir,
                "output_dir": base_dir / "out",
                "output_retention_days": 7,
                "control_url": "http://127.0.0.1:14501",
                "collect_timeout_seconds": 10,
            },
        )()
        old_path = os.environ.get("PATH", "")
        os.environ["PATH"] = str(base_dir) + os.pathsep + old_path
        try:
            fake_python = base_dir / "python3"
            fake_python.symlink_to(python_bin)
            result = run_platform_fetcher(
                args,
                {
                    "systemShopId": 120,
                    "userPhone": "15200837196",
                    "controlShopId": "1184657317",
                    "shopName": "罗家臭豆腐",
                    "platform": "tbwm",
                },
            )
        finally:
            os.environ["PATH"] = old_path

        assert result["orders"] == 1
        assert result["platform"] == "tbwm"
        assert Path(result["payload"]).exists()


def main() -> None:
    test_is_supported_target_accepts_authorized_meituan_shape()
    test_is_supported_target_rejects_missing_or_unsupported_shape()
    test_cleanup_old_shop_files_only_removes_expired_regular_files()
    test_run_platform_fetcher_dispatches_by_platform()
    test_run_platform_fetcher_dispatches_tbwm()
    print("crawl_authorized_shop_orders tests passed")


if __name__ == "__main__":
    main()
