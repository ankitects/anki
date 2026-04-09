# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Validate a PEP 440 version string and compare to the current version."""

import re
import sys

from packaging.version import InvalidVersion, Version

VERSION_RE = re.compile(r"^\d+\.\d{2}(\.\d+)?(a\d+|b\d+|rc\d+)?$")


def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: validate_version.py <version> <current_version>", file=sys.stderr)
        sys.exit(1)

    version, current_version = sys.argv[1], sys.argv[2]

    if not VERSION_RE.match(version):
        print(
            f"::error::version '{version}' must be year.month(.patch) with zero-padded month (e.g. 26.04, 26.04b1, 26.04.1rc2)",
            file=sys.stderr,
        )
        sys.exit(1)

    try:
        v = Version(version)
    except InvalidVersion:
        print(f"::error::version '{version}' is not valid PEP 440", file=sys.stderr)
        sys.exit(1)

    if v <= Version(current_version):
        print(
            f"::error::version {version} must be greater than current {current_version}",
            file=sys.stderr,
        )
        sys.exit(1)

    print("true" if v.is_prerelease else "false")


if __name__ == "__main__":
    main()
