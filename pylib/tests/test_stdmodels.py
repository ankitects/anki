# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import pytest

from anki import stdmodels
from anki.stdmodels import (
    StockNotetypeKind,
    _get_stock_notetype,
    _legacy_add_basic_model,
    _legacy_add_basic_typing_model,
    _legacy_add_cloze_model,
    _legacy_add_forward_optional_reverse,
    _legacy_add_forward_reverse,
    get_stock_notetypes,
)
from tests.shared import getEmptyCol


@pytest.mark.parametrize(
    ("kind", "fields", "templates"),
    [
        (StockNotetypeKind.KIND_BASIC, ["Front", "Back"], ["Card 1"]),
        (
            StockNotetypeKind.KIND_BASIC_AND_REVERSED,
            ["Front", "Back"],
            ["Card 1", "Card 2"],
        ),
        (
            StockNotetypeKind.KIND_BASIC_OPTIONAL_REVERSED,
            ["Front", "Back", "Add Reverse"],
            ["Card 1", "Card 2"],
        ),
        (StockNotetypeKind.KIND_BASIC_TYPING, ["Front", "Back"], ["Card 1"]),
        (StockNotetypeKind.KIND_CLOZE, ["Text", "Back Extra"], ["Cloze"]),
    ],
)
def test_stock_notetype_has_expected_shape(
    kind: StockNotetypeKind.V, fields: list[str], templates: list[str]
) -> None:
    # guards the Python enum -> notetype mapping; the field/template
    # contents themselves are produced by the backend.
    col = getEmptyCol()
    nt = _get_stock_notetype(col, kind)
    assert [f["name"] for f in nt["flds"]] == fields
    assert [t["name"] for t in nt["tmpls"]] == templates


def test_get_stock_notetypes_lists_builtin_kinds() -> None:
    col = getEmptyCol()
    names = [name for name, _ in get_stock_notetypes(col)]
    # one entry per built-in kind (6 today, incl. image occlusion)
    assert "Basic" in names
    assert "Cloze" in names
    assert len(names) >= 6


def test_get_stock_notetypes_appends_addon_entry_with_string_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    col = getEmptyCol()
    build = lambda col: _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC)
    monkeypatch.setattr(stdmodels, "models", [("My Add-on Type", build)])

    result = get_stock_notetypes(col)

    name, getter = result[-1]
    assert name == "My Add-on Type"
    assert getter(col)["flds"][0]["name"] == "Front"


def test_get_stock_notetypes_resolves_callable_addon_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    col = getEmptyCol()
    build = lambda col: _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC)
    monkeypatch.setattr(stdmodels, "models", [(lambda: "Lazy Name", build)])

    names = [name for name, _ in get_stock_notetypes(col)]

    assert "Lazy Name" in names


@pytest.mark.parametrize(
    "adder",
    [
        _legacy_add_basic_model,
        _legacy_add_basic_typing_model,
        _legacy_add_forward_reverse,
        _legacy_add_forward_optional_reverse,
        _legacy_add_cloze_model,
    ],
)
def test_legacy_add_persists_notetype_to_collection(adder) -> None:
    # the legacy adders differ from get_stock_notetype by also adding the
    # notetype to the collection before returning it.
    col = getEmptyCol()
    nt = adder(col)
    assert col.models.by_name(nt["name"]) is not None


def test_deprecated_alias_adds_notetype() -> None:
    # exercises the module __getattr__ deprecated-name remapping.
    col = getEmptyCol()
    # resolved at runtime through the module __getattr__, not statically
    nt = stdmodels.addBasicModel(col)  # type: ignore[attr-defined]
    assert col.models.by_name(nt["name"]) is not None
