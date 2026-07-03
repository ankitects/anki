# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from unittest.mock import patch

import pytest

from anki.utils import int_version, int_version_to_str


@pytest.mark.parametrize(
    "ver,expected",
    [
        # 2.1.x releases: ver is just the patch number
        (0, "2.1.0"),
        (23, "2.1.23"),
        (99, "2.1.99"),
        # YY.MM releases with no patch
        (230900, "23.09"),
        (250200, "25.02"),
        (260500, "26.05"),
        # YY.MM.PP releases with a patch
        (230901, "23.09.1"),
        (231012, "23.10.12"),
        (260501, "26.05.1"),
    ],
)
def test_int_version_to_str(ver, expected):
    assert int_version_to_str(ver) == expected


@pytest.mark.parametrize(
    "version,expected",
    [
        ("23.09", 230900),
        ("23.09.1", 230901),
        ("25.02", 250200),
        ("26.05", 260500),
        # beta/rc suffixes decode to the same int as the base release
        ("25.02b1", 250200),
        ("25.02rc3", 250200),
        ("23.09.1b2", 230901),
        ("23.09.1rc3", 230901),
        ("26.05b1", 260500),
    ],
)
def test_int_version(version, expected):
    with patch("anki.buildinfo.version", version):
        assert int_version() == expected


def test_int_version_rejects_garbage():
    with patch("anki.buildinfo.version", "not-a-version"):
        with pytest.raises(ValueError):
            int_version()
