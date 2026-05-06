# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Validate a PEP 440 version string and compare to the current version."""

import re
import sys

from packaging.version import InvalidVersion, Version

VERSION_RE = re.compile(r"^\d+\.(\d{2})(\.\d+)?(a\d+|b\d+|rc\d+)?$")


def validate_version(version: str, current_version: str) -> bool:
    match = VERSION_RE.match(version)
    month = int(match.group(1)) if match else 0
    if not match or month > 12:
        raise ValueError(
            f"version '{version}' must be year.month(.patch) with zero-padded month"
            " (e.g. 26.04, 26.04b1, 26.04.1rc2)"
        )

    try:
        v = Version(version)
    except InvalidVersion as exc:
        raise ValueError(f"version '{version}' is not valid PEP 440") from exc

    if v <= Version(current_version):
        raise ValueError(
            f"version {version} must be greater than current {current_version}"
        )

    return v.is_prerelease


def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: validate_version.py <version> <current_version>", file=sys.stderr)
        sys.exit(1)

    try:
        is_prerelease = validate_version(sys.argv[1], sys.argv[2])
    except ValueError as exc:
        print(f"::error::{exc}", file=sys.stderr)
        sys.exit(1)

    print("true" if is_prerelease else "false")


if __name__ == "__main__":
    main()
