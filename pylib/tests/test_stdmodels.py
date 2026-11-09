# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable

import pytest

# anki.models imports anki.stdmodels, so anki.collection must be imported
# first, or importing anki.stdmodels here fails with a circular import.
import anki.collection
import anki.models
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

NotetypeBuilder = Callable[[anki.collection.Collection], anki.models.NotetypeDict]


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


def test_basic_typing_asks_for_typed_answer() -> None:
    # the typing kind shares its field and template names with plain basic,
    # so the shape test above cannot tell the two apart.
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC_TYPING)
    assert "{{type:Back}}" in nt["tmpls"][0]["qfmt"]


def test_get_stock_notetypes_lists_builtin_kinds_in_proto_order() -> None:
    col = getEmptyCol()
    names = [name for name, _ in get_stock_notetypes(col)]
    assert names == [
        "Basic",
        "Basic (and reversed card)",
        "Basic (optional reversed card)",
        "Basic (type in the answer)",
        "Cloze",
        "Image Occlusion",
    ]


def test_get_stock_notetypes_appends_addon_entry_with_string_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    col = getEmptyCol()

    def build(col: anki.collection.Collection) -> anki.models.NotetypeDict:
        return _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC)

    monkeypatch.setattr(stdmodels, "models", [("My Add-on Type", build)])

    name, getter = get_stock_notetypes(col)[-1]

    assert name == "My Add-on Type"
    assert getter is build


def test_get_stock_notetypes_resolves_callable_addon_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    col = getEmptyCol()

    def build(col: anki.collection.Collection) -> anki.models.NotetypeDict:
        return _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC)

    monkeypatch.setattr(stdmodels, "models", [(lambda: "Lazy Name", build)])

    name, _ = get_stock_notetypes(col)[-1]

    assert name == "Lazy Name"


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
def test_legacy_add_persists_notetype_to_collection(adder: NotetypeBuilder) -> None:
    # the legacy adders differ from _get_stock_notetype by also adding the
    # notetype to the collection before returning it. A stock notetype of
    # the same name already exists, so identity must be checked by id.
    col = getEmptyCol()
    before = {entry.id for entry in col.models.all_names_and_ids()}

    nt = adder(col)

    after = {entry.id for entry in col.models.all_names_and_ids()}
    assert nt["id"] not in before
    assert after - before == {nt["id"]}


def test_deprecated_alias_adds_notetype() -> None:
    # exercises the module __getattr__ deprecated-name remapping.
    col = getEmptyCol()
    before = {entry.id for entry in col.models.all_names_and_ids()}

    # resolved at runtime through the module __getattr__, not statically
    nt = stdmodels.addBasicModel(col)  # type: ignore[attr-defined]

    after = {entry.id for entry in col.models.all_names_and_ids()}
    assert after - before == {nt["id"]}
