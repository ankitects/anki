# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import pytest
from validate_version import validate_version


@pytest.mark.parametrize(
    "version, current, expected",
    [
        ("26.04", "26.03", False),
        ("26.04.1", "26.04", False),
        ("26.12", "26.11", False),
        ("26.04b1", "26.03", True),
        ("26.04a1", "26.03", True),
        ("26.04rc1", "26.03", True),
        ("26.04.1rc2", "26.04", True),
        ("26.04.1b1", "26.04", True),
    ],
)
def test_valid_versions(version: str, current: str, expected: bool) -> None:
    assert validate_version(version, current) is expected


@pytest.mark.parametrize(
    "version, current, match",
    [
        ("26.4", "26.03", "zero-padded month"),
        ("26.04", "26.04", "must be greater"),
        ("26.03", "26.04", "must be greater"),
        ("26.04b1", "26.04", "must be greater"),
        ("26", "25.01", "zero-padded month"),
        ("26.123", "26.03", "zero-padded month"),
        ("", "26.03", "zero-padded month"),
        ("not-a-version", "26.03", "zero-padded month"),
        ("26.04.dev1", "26.03", "zero-padded month"),
        ("26.04.post1", "26.03", "zero-padded month"),
        ("26.13", "26.12", "zero-padded month"),
    ],
)
def test_invalid_versions(version: str, current: str, match: str) -> None:
    with pytest.raises(ValueError, match=match):
        validate_version(version, current)
