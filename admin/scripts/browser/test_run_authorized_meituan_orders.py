#!/usr/bin/env python3
from __future__ import annotations

import os
import tempfile
from datetime import datetime, timedelta
from pathlib import Path

from run_authorized_meituan_orders import cleanup_old_shop_files, is_supported_target


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
    assert not is_supported_target(
        {
            "systemShopId": 76,
            "userPhone": "15200837196",
            "controlShopId": "16081572",
            "platform": "jdms",
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


def main() -> None:
    test_is_supported_target_accepts_authorized_meituan_shape()
    test_is_supported_target_rejects_missing_or_unsupported_shape()
    test_cleanup_old_shop_files_only_removes_expired_regular_files()
    print("run_authorized_meituan_orders tests passed")


if __name__ == "__main__":
    main()
