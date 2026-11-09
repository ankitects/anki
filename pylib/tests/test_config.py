# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any

import pytest

from tests.shared import getEmptyCol


def test_get_returns_value_after_set() -> None:
    col = getEmptyCol()
    col.set_config("mykey", "myval")
    assert col.get_config("mykey") == "myval"


def test_remove_makes_key_absent() -> None:
    col = getEmptyCol()
    col.set_config("mykey", "myval")
    col.remove_config("mykey")
    assert col.get_config("mykey") is None
    assert "mykey" not in col.conf


def test_missing_key_returns_default() -> None:
    col = getEmptyCol()
    assert col.get_config("nonexistent") is None
    assert col.get_config("nonexistent", default="fallback") == "fallback"


@pytest.mark.parametrize(
    "value",
    [True, False, 0, "", [1, "two", 3.0], {"a": 1, "b": [2, 3]}],
)
def test_value_survives_round_trip(value: Any) -> None:
    # smoke test that config.py's read side returns the stored value
    # unchanged (including falsy values, which must not be treated as
    # missing). Serialization itself is owned by the backend.
    col = getEmptyCol()
    col.set_config("mykey", value)
    got = col.get_config("mykey")
    assert got == value
    assert type(got) is type(value)


def test_get_config_returns_detached_copy() -> None:
    # documented contract: mutating the value returned by get_config()
    # does not persist unless set_config() is called again.
    col = getEmptyCol()
    col.set_config("mylist", [1, 2, 3])
    got = col.get_config("mylist")
    got.append(4)
    assert col.get_config("mylist") == [1, 2, 3]


# Legacy dict interface (col.conf)
##################################


def test_legacy_dict_get_set_contains_delete() -> None:
    col = getEmptyCol()
    col.conf["mykey"] = "myval"
    assert col.conf["mykey"] == "myval"
    assert col.conf.get("mykey") == "myval"
    assert col.conf.get("missing", "default") == "default"
    assert "mykey" in col.conf
    assert "missing" not in col.conf
    del col.conf["mykey"]
    assert "mykey" not in col.conf


def test_legacy_missing_key_raises_key_error() -> None:
    col = getEmptyCol()
    with pytest.raises(KeyError):
        _ = col.conf["missing"]


def test_legacy_setdefault_only_sets_when_absent() -> None:
    col = getEmptyCol()
    assert col.conf.setdefault("mykey", "first") == "first"
    # already present: existing value is kept
    assert col.conf.setdefault("mykey", "second") == "first"
    assert col.get_config("mykey") == "first"


def test_wrapped_list_mutation_persists_on_drop() -> None:
    # WrappedList exists so that legacy in-place mutation of a list read
    # via col.conf is written back when the wrapper is dropped.
    col = getEmptyCol()
    col.set_config("mylist", [1, 2, 3])
    wrapped = col.conf["mylist"]
    wrapped.append(4)
    del wrapped
    assert col.get_config("mylist") == [1, 2, 3, 4]


def test_wrapped_dict_mutation_persists_on_drop() -> None:
    col = getEmptyCol()
    col.set_config("mydict", {"a": 1})
    wrapped = col.conf["mydict"]
    wrapped["b"] = 2
    del wrapped
    assert col.get_config("mydict") == {"a": 1, "b": 2}


def test_wrapped_list_does_not_write_back_when_unmutated(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    # the value is unchanged either way, so the write itself has to be
    # observed to tell a skipped write-back from a redundant one.
    col = getEmptyCol()
    col.set_config("mylist", [1, 2, 3])
    wrapped = col.conf["mylist"]
    assert list(wrapped) == [1, 2, 3]
    writes: list[str] = []
    # the wrapper writes through ConfigManager.set
    monkeypatch.setattr(
        type(col.conf), "set", lambda self, key, val: writes.append(key)
    )

    del wrapped

    assert writes == []
