# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# These steps are outsite bundle/build.py, so that multiple builds can be done
# in sequence without blocking on Apple's notarization, and so that the final
# dmg build can be done in bulk at the end.

import os
import subprocess
import sys
from pathlib import Path

output_root = Path(__file__).parent / "../../../.bazel/out"
dist_folder = output_root / "dist"
apps = output_root / "build" / "app"
variants = ["qt6_arm64", "qt6_amd64", "qt5_amd64"]


def staple_apps() -> None:
    for variant in variants:
        variant_base = apps / variant
        if variant_base.exists():
            if os.getenv("NOTARIZE_USER"):
                subprocess.run(
                    [
                        "python",
                        Path(__file__).with_name("notarize.py"),
                        "staple",
                        variant_base,
                    ],
                    check=True,
                )
        else:
            print("skip missing", variant_base)


def build_dmgs() -> None:
    for variant in variants:
        variant_base = apps / variant
        if variant_base.exists():
            dmg_name_path = variant_base / "dmg_name"
            dmg_name = open(dmg_name_path).read()
            dmg_name_path.unlink()
            subprocess.run(
                [
                    "bash",
                    Path(__file__).with_name("dmg") / "build.sh",
                    variant_base,
                    dist_folder / dmg_name,
                ],
                check=True,
            )
        else:
            print("skip missing", variant_base)


if sys.argv[1] == "staple":
    staple_apps()
elif sys.argv[1] == "dmg":
    build_dmgs()
