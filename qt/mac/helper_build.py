# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import platform
import subprocess
import sys
from pathlib import Path

out_dylib, *src_files = sys.argv[1:]
out_dir = Path(out_dylib).parent.resolve()
src_dir = Path(src_files[0]).parent.resolve()

if platform.machine() == "arm64" and not os.environ.get("MAC_X86"):
    target = "arm64-apple-macos11"
else:
    target = "x86_64-apple-macos10.14"

args = [
    "swiftc",
    "-target",
    target,
    "-emit-library",
    "-module-name",
    "ankihelper",
    "-O",
]
args.extend(src_dir / Path(file).name for file in src_files)
subprocess.run(args, check=True, cwd=out_dir)
