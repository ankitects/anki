# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Callable, List, Tuple

import anki.collection
import anki.models
from anki import notetypes_pb2
from anki.utils import from_json_bytes

# pylint: disable=no-member
StockNotetypeKind = notetypes_pb2.StockNotetype.Kind

# add-on authors can add ("note type name", function)
# to this list to have it shown in the add/clone note type screen
models: List[Tuple] = []


def _get_stock_notetype(
    col: anki.collection.Collection, kind: StockNotetypeKind.V
) -> anki.models.NotetypeDict:
    return from_json_bytes(col._backend.get_stock_notetype_legacy(kind))


def get_stock_notetypes(
    col: anki.collection.Collection,
) -> List[Tuple[str, Callable[[anki.collection.Collection], anki.models.NotetypeDict]]]:
    out: List[
        Tuple[str, Callable[[anki.collection.Collection], anki.models.NotetypeDict]]
    ] = []
    # add standard
    for kind in [
        StockNotetypeKind.BASIC,
        StockNotetypeKind.BASIC_TYPING,
        StockNotetypeKind.BASIC_AND_REVERSED,
        StockNotetypeKind.BASIC_OPTIONAL_REVERSED,
        StockNotetypeKind.CLOZE,
    ]:
        m = from_json_bytes(col._backend.get_stock_notetype_legacy(kind))

        def instance_getter(
            model: Any,
        ) -> Callable[[anki.collection.Collection], anki.models.NotetypeDict]:
            return lambda col: model

        out.append((m["name"], instance_getter(m)))
    # add extras from add-ons
    for (name_or_func, func) in models:
        if not isinstance(name_or_func, str):
            name = name_or_func()
        else:
            name = name_or_func
        out.append((name, func))
    return out


#
# Legacy functions that added the notetype before returning it
#


def addBasicModel(col: anki.collection.Collection) -> anki.models.NotetypeDict:
    nt = _get_stock_notetype(col, StockNotetypeKind.BASIC)
    col.models.add(nt)
    return nt


def addBasicTypingModel(col: anki.collection.Collection) -> anki.models.NotetypeDict:
    nt = _get_stock_notetype(col, StockNotetypeKind.BASIC_TYPING)
    col.models.add(nt)
    return nt


def addForwardReverse(col: anki.collection.Collection) -> anki.models.NotetypeDict:
    nt = _get_stock_notetype(col, StockNotetypeKind.BASIC_AND_REVERSED)
    col.models.add(nt)
    return nt


def addForwardOptionalReverse(
    col: anki.collection.Collection,
) -> anki.models.NotetypeDict:
    nt = _get_stock_notetype(col, StockNotetypeKind.BASIC_OPTIONAL_REVERSED)
    col.models.add(nt)
    return nt


def addClozeModel(col: anki.collection.Collection) -> anki.models.NotetypeDict:
    nt = _get_stock_notetype(col, StockNotetypeKind.CLOZE)
    col.models.add(nt)
    return nt
