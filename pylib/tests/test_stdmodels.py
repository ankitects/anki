# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from anki.stdmodels import StockNotetypeKind, _get_stock_notetype
from tests.shared import getEmptyCol


def test_basic() -> None:
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC)
    field_names = [f["name"] for f in nt["flds"]]
    template_names = [t["name"] for t in nt["tmpls"]]
    assert field_names == ["Front", "Back"]
    assert template_names == ["Card 1"]


def test_basic_and_reversed() -> None:
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC_AND_REVERSED)
    field_names = [f["name"] for f in nt["flds"]]
    template_names = [t["name"] for t in nt["tmpls"]]
    assert field_names == ["Front", "Back"]
    assert template_names == ["Card 1", "Card 2"]


def test_basic_optional_reversed() -> None:
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC_OPTIONAL_REVERSED)
    field_names = [f["name"] for f in nt["flds"]]
    template_names = [t["name"] for t in nt["tmpls"]]
    # reverse card is conditional on the "Add Reverse" field being non-empty
    assert field_names == ["Front", "Back", "Add Reverse"]
    assert template_names == ["Card 1", "Card 2"]


def test_basic_typing() -> None:
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_BASIC_TYPING)
    field_names = [f["name"] for f in nt["flds"]]
    template_names = [t["name"] for t in nt["tmpls"]]
    # same shape as Basic; the type-in behaviour is in the template format
    assert field_names == ["Front", "Back"]
    assert template_names == ["Card 1"]


def test_cloze() -> None:
    col = getEmptyCol()
    nt = _get_stock_notetype(col, StockNotetypeKind.KIND_CLOZE)
    field_names = [f["name"] for f in nt["flds"]]
    template_names = [t["name"] for t in nt["tmpls"]]
    assert field_names == ["Text", "Back Extra"]
    assert template_names == ["Cloze"]


def test_legacy_add_basic_model() -> None:
    col = getEmptyCol()
    from anki.stdmodels import _legacy_add_basic_model
    nt = _legacy_add_basic_model(col)
    # notetype is added to the collection and returned
    assert col.models.by_name(nt["name"]) is not None
    assert [f["name"] for f in nt["flds"]] == ["Front", "Back"]


def test_legacy_add_cloze_model() -> None:
    col = getEmptyCol()
    from anki.stdmodels import _legacy_add_cloze_model
    nt = _legacy_add_cloze_model(col)
    assert col.models.by_name(nt["name"]) is not None
    assert [f["name"] for f in nt["flds"]] == ["Text", "Back Extra"]


def test_legacy_add_forward_reverse() -> None:
    col = getEmptyCol()
    from anki.stdmodels import _legacy_add_forward_reverse
    nt = _legacy_add_forward_reverse(col)
    assert col.models.by_name(nt["name"]) is not None
    assert [t["name"] for t in nt["tmpls"]] == ["Card 1", "Card 2"]
