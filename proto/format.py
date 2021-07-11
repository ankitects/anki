# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import difflib
import os
import subprocess
import sys

clang_format = sys.argv[1]
workspace = os.environ.get("BUILD_WORKSPACE_DIRECTORY", "")
want_fix = bool(workspace)

found_bad = False
for path in sys.argv[2:]:
    with open(path) as file:
        orig = file.read()
    new = subprocess.check_output(
        #        [clang_format, "--style={'BasedOnStyle': 'google', 'IndentWidth': 4}", path]
        [clang_format, "--style=google", path]
    ).decode("utf-8")
    if orig != new:
        if want_fix:
            with open(os.path.join(workspace, path), "w", newline="\n") as file:
                file.write(new)
            print("fixed", path)
        else:
            print(f"Bad formatting in {path}")
            print(
                "\n".join(
                    difflib.unified_diff(
                        orig.splitlines(),
                        new.splitlines(),
                        fromfile="bad",
                        tofile="good",
                        lineterm="",
                    )
                )
            )
            found_bad = True

if found_bad:
    sys.exit(1)
