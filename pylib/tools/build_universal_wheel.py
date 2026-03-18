# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import argparse
from pathlib import Path

from delocate.fuse import fuse_wheels


def main(wheels: list[str], out_path: Path) -> None:
    fuse_wheels(wheels[0], wheels[1], out_path)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build a universal macOS wheel.")
    parser.add_argument("--wheels", help="Paths to the two wheels to combine", nargs=2)
    parser.add_argument(
        "--out_wheel",
        type=Path,
        help="Output path for the universal wheel",
    )
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    main(args.wheels, args.out_wheel)
