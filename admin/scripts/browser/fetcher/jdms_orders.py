#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def main() -> int:
    script = Path(__file__).with_suffix(".js")
    env = os.environ.copy()
    base_dir = Path(env.get("ZR_BASE_DIR", "/home/ubuntu/data"))
    default_node_path = str(base_dir / "browser" / "node_modules")
    current_node_path = env.get("NODE_PATH", "")
    if current_node_path:
        if default_node_path not in current_node_path.split(os.pathsep):
            env["NODE_PATH"] = current_node_path + os.pathsep + default_node_path
    else:
        env["NODE_PATH"] = default_node_path

    return subprocess.run(["node", str(script), *sys.argv[1:]], env=env, check=False).returncode


if __name__ == "__main__":
    sys.exit(main())
