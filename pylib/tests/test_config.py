# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from tests.shared import getEmptyCol


def test_get_set_remove() -> None:
    col = getEmptyCol()
    col.set_config("mykey", "myval")
    assert col.get_config("mykey") == "myval"
    col.remove_config("mykey")
    assert col.get_config("mykey") is None


def test_default() -> None:
    col = getEmptyCol()
    # missing key returns None by default, or the caller-supplied fallback
    assert col.get_config("nonexistent") is None
    assert col.get_config("nonexistent", default="fallback") == "fallback"


def test_bool_round_trip() -> None:
    col = getEmptyCol()
    col.set_config("mybool", True)
    assert col.get_config("mybool") is True
    col.set_config("mybool", False)
    assert col.get_config("mybool") is False


def test_list_round_trip() -> None:
    col = getEmptyCol()
    val = [1, "two", 3.0]
    col.set_config("mylist", val)
    assert col.get_config("mylist") == val


def test_dict_round_trip() -> None:
    col = getEmptyCol()
    val = {"a": 1, "b": [2, 3]}
    col.set_config("mydict", val)
    assert col.get_config("mydict") == val


def test_legacy_dict_interface() -> None:
    col = getEmptyCol()
    # col.conf is the legacy dict-style interface backed by the same store
    col.conf["mykey"] = "myval"
    assert col.conf["mykey"] == "myval"
    assert col.conf.get("mykey") == "myval"
    assert col.conf.get("missing", "default") == "default"
    assert "mykey" in col.conf
    assert "missing" not in col.conf
    del col.conf["mykey"]
    assert "mykey" not in col.conf


def test_legacy_wrapped_list() -> None:
    col = getEmptyCol()
    # accessing a list via col.conf returns a WrappedList
    col.set_config("mylist", [1, 2, 3])
    wrapped = col.conf["mylist"]
    assert list(wrapped) == [1, 2, 3]


def test_legacy_wrapped_dict() -> None:
    col = getEmptyCol()
    # accessing a dict via col.conf returns a WrappedDict
    col.set_config("mydict", {"a": 1})
    wrapped = col.conf["mydict"]
    assert dict(wrapped) == {"a": 1}
