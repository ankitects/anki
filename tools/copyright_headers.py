# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import subprocess
import sys
from pathlib import Path

want_fix = sys.argv[1] == "fix"
checked_for_dirty = False

nonstandard_header = {
    "pylib/anki/_vendor/stringcase.py",
    "pylib/anki/importing/pauker.py",
    "pylib/anki/importing/supermemo_xml.py",
    "pylib/anki/statsbg.py",
    "pylib/tools/protoc-gen-mypy.py",
    "python/pyqt/install.py",
    "python/write_wheel.py",
    "qt/aqt/mpv.py",
    "qt/aqt/winpaths.py",
    "qt/bundle/build.rs",
    "qt/bundle/src/main.rs",
}

ignored_folders = [
    "out",
    "node_modules",
    "qt/forms",
    "tools/workspace-hack",
    "qt/bundle/PyOxidizer",
    "rslib/ascii_percent_encoding",
]


def fix(path: Path) -> None:
    with open(path, "r", encoding="utf8") as f:
        existing_text = f.read()
    path_str = str(path)
    if path_str.endswith(".py"):
        header = """\
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
    elif (
        path_str.endswith(".ts")
        or path_str.endswith(".rs")
        or path_str.endswith(".mjs")
    ):
        header = """\
// Copyright: Ankitects Pty Ltd and contributors
// License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
    elif path_str.endswith(".svelte"):
        header = """\
<!--
Copyright: Ankitects Pty Ltd and contributors
License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
-->

"""
    with open(path, "w", encoding="utf8") as f:
        f.write(header + existing_text)


found = False
if sys.platform == "win32":
    ignored_folders = [f.replace("/", "\\") for f in ignored_folders]
    nonstandard_header = {f.replace("/", "\\") for f in nonstandard_header}

for dirpath, dirnames, fnames in os.walk("."):
    dir = Path(dirpath)

    # avoid infinite recursion with old symlink
    if ".bazel" in dirnames:
        dirnames.remove(".bazel")

    ignore = False
    for folder in ignored_folders:
        if folder in dirpath:
            ignore = True
            break
    if ignore:
        continue

    for fname in fnames:
        for ext in ".py", ".ts", ".rs", ".svelte", ".mjs":
            if fname.endswith(ext):
                path = dir / fname
                with open(path, encoding="utf8") as f:
                    top = f.read(256)
                    if not top.strip():
                        continue
                    if str(path) in nonstandard_header:
                        continue
                    if fname.endswith(".d.ts"):
                        continue
                    missing = "Ankitects Pty Ltd and contributors" not in top
                if missing:
                    if want_fix:
                        if not checked_for_dirty:
                            if subprocess.getoutput("git diff"):
                                print("stage any changes first")
                                sys.exit(1)
                            checked_for_dirty = True
                        fix(path)
                    else:
                        print("missing standard copyright header:", path)
                        found = True

if found:
    sys.exit(1)
