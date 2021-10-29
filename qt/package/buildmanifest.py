# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from pathlib import Path


def build_manifest(top: Path) -> None:
    manifest = []
    for root, dirnames, fnames in os.walk(top, topdown=True):
        relroot = root[len(str(top)) + 1 :]
        # if not top level, add folder
        if relroot:
            manifest.append(relroot)
        # then the files
        for fname in fnames:
            path = os.path.join(relroot, fname)
            manifest.append(path)

    with open(top / "anki.install-manifest", "w") as file:
        file.write("\n".join(manifest) + "\n")


if __name__ == "__main__":
    build_manifest(Path(sys.argv[1]))
