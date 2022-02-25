# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# Because Bazel doesn't support building on a sandbox on Windows, our TypeScript
# outputs are not getting properly tracked. When a ts file is renamed or removed,
# the old build product is not automatically cleaned up, and that leads to a
# build failure until the stale outputs are manually removed. This script runs
# through the output folder, and removes all .ts/.js files to unbreak the build.

import os
from pathlib import Path
from typing import Iterable
import stat

root = Path(os.environ["BUILD_WORKSPACE_DIRECTORY"])
out_base = root / ".bazel" / "out"


def dts_and_js_files(path: Path) -> Iterable[Path]:
    for entry in path.iterdir():
        if "runfiles" in entry.name:
            continue
        elif entry.is_dir():
            yield from dts_and_js_files(entry.resolve())
        elif entry.suffix in (".ts", ".js") and not entry.name.startswith("_"):
            yield entry.resolve()


for output_folder in out_base.glob("*windows*/bin/ts"):
    for path in dts_and_js_files(output_folder):
        path.chmod(stat.S_IWRITE)
        path.unlink()
