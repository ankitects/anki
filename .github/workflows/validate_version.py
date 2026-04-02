# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Validate a PEP 440 version string and compare to the current version."""

import sys

from packaging.version import InvalidVersion, Version


def main() -> None:
    if len(sys.argv) != 3:
        print("Usage: validate_version.py <version> <current_version>", file=sys.stderr)
        sys.exit(1)

    version, current_version = sys.argv[1], sys.argv[2]

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
