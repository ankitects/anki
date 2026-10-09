# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import hashlib
import os
import string
from unittest.mock import patch

import pytest

from anki.utils import (
    base62,
    base91,
    checksum,
    ids2str,
    int_version,
    int_version_to_str,
    invalid_filename,
    join_fields,
    namedtmp,
    split_fields,
    tmpfile,
)


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


@pytest.mark.parametrize(
    "ids,expected",
    [
        ([], "()"),
        ([1], "(1)"),
        ([1, 2, 3], "(1,2,3)"),
        # ids may already be strings
        (["a", "b"], "(a,b)"),
        # unsorted input is not reordered
        ([3, 1, 2], "(3,1,2)"),
    ],
)
def test_ids2str(ids, expected):
    assert ids2str(ids) == expected


# the alphabet base62() encodes with, in the order it uses them
BASE62_TABLE = string.ascii_letters + string.digits


def decode_base62(encoded: str, table: str) -> int:
    "Inverse of base62(), so a round trip can be asserted."
    num = 0
    for char in encoded:
        num = num * len(table) + table.index(char)
    return num


def test_base62_roundtrips():
    assert [decode_base62(base62(n), BASE62_TABLE) for n in range(1000)] == list(
        range(1000)
    )


def test_base62_is_injective():
    encoded = [base62(n) for n in range(1000)]
    assert len(set(encoded)) == len(encoded)


@pytest.mark.parametrize(
    "num,expected",
    [
        # zero is the empty string, as with base64
        (0, ""),
        # the table is ascii_letters + digits, so it is not the usual base62 order
        (1, "b"),
        (25, "z"),
        (26, "A"),
        (61, "9"),
        (62, "ba"),
    ],
)
def test_base62_edges(num, expected):
    assert base62(num) == expected


def test_base62_only_uses_its_alphabet():
    used = {char for num in range(1000) for char in base62(num)}
    assert used <= set(BASE62_TABLE)


def test_base91_only_uses_safe_chars():
    "The table is every printable char except quotes, backslash and separators."
    used = {char for num in range(1000) for char in base91(num)}
    assert not used & set('"\\ ')
    assert all(char.isprintable() for char in used)


def test_base91_uses_all_91_chars():
    used = {char for num in range(1000) for char in base91(num)}
    assert len(used) == 91


def test_base91_is_injective():
    encoded = [base91(n) for n in range(1000)]
    assert len(set(encoded)) == len(encoded)


def test_join_fields_uses_the_unit_separator():
    assert join_fields(["a", "b", "c"]) == "a\x1fb\x1fc"
    assert join_fields([]) == ""
    assert join_fields([""]) == ""


def test_split_fields():
    assert split_fields("a\x1fb\x1fc") == ["a", "b", "c"]
    # an empty string holds one empty field, not zero
    assert split_fields("") == [""]
    assert split_fields("\x1f") == ["", ""]


def test_join_fields_roundtrips():
    fields = ["", "a", "with space", "with\x1fsplit"]
    assert split_fields(join_fields(fields)) != fields
    # the separator cannot survive a round trip
    assert split_fields(join_fields(["a\x1fb"])) == ["a", "b"]


def test_checksum_accepts_str_and_bytes():
    assert checksum("abc") == checksum(b"abc")
    assert checksum("") == checksum(b"")


def test_checksum_is_utf8_sha1():
    assert checksum("abc") == hashlib.sha1(b"abc").hexdigest()
    # non-ascii is encoded as utf-8 rather than rejected
    assert checksum("é") == hashlib.sha1("é".encode()).hexdigest()


@pytest.mark.parametrize(
    "name,expected",
    [
        # the characters that are invalid on Windows
        ("a:b", ":"),
        ("a*b", "*"),
        ("a?b", "?"),
        ('a"b', '"'),
        ("a<b", "<"),
        ("a>b", ">"),
        ("a|b", "|"),
        # a leading dot is treated as hidden, even after whitespace
        (".hidden", "."),
        ("  .hidden", "."),
        # valid names
        ("ok", None),
        ("a b", None),
        ("a.b", None),
        ("", None),
    ],
)
def test_invalid_filename(name, expected):
    assert invalid_filename(name) == expected


@pytest.mark.parametrize("is_win", [True, False])
def test_invalid_filename_dirsep(is_win):
    "Either separator is rejected when dirsep is on, whatever the platform."
    with patch("anki.utils.is_win", is_win):
        assert invalid_filename("a/b", dirsep=True) == "/"
        assert invalid_filename("a\\b", dirsep=True) == "\\"


@pytest.mark.parametrize(
    "is_win,name,expected",
    [
        # with dirsep off, each platform allows its own separator
        (False, "a/b", None),
        (False, "a\\b", "\\"),
        (True, "a/b", "/"),
        (True, "a\\b", None),
    ],
)
def test_invalid_filename_dirsep_off(is_win, name, expected):
    with patch("anki.utils.is_win", is_win):
        assert invalid_filename(name, dirsep=False) == expected


def test_tmpfile_is_created_empty():
    name = tmpfile(prefix="anki-test-", suffix=".txt")
    assert os.path.basename(name).startswith("anki-test-")
    assert name.endswith(".txt")
    assert os.path.getsize(name) == 0


def test_namedtmp_is_stable_across_calls():
    first = namedtmp("anki-test-same")
    assert namedtmp("anki-test-same") == first


def test_namedtmp_removes_an_existing_file():
    path = namedtmp("anki-test-existing")
    with open(path, "w") as handle:
        handle.write("stale")
    assert namedtmp("anki-test-existing") == path
    assert not os.path.exists(path)


def test_namedtmp_can_keep_an_existing_file():
    path = namedtmp("anki-test-keep")
    with open(path, "w") as handle:
        handle.write("kept")
    assert namedtmp("anki-test-keep", remove=False) == path
    with open(path) as handle:
        assert handle.read() == "kept"
