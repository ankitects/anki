# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable, List, Tuple

import anki
import anki._backend.backend_pb2 as _pb
from anki.utils import from_json_bytes

# pylint: disable=no-member
StockNotetypeKind = _pb.StockNoteType.Kind

# add-on authors can add ("note type name", function_like_addBasicModel)
# to this list to have it shown in the add/clone note type screen
models: List[Tuple] = []


def _add_stock_notetype(
    col: anki.collection.Collection, kind: StockNotetypeKind.V
) -> anki.models.NoteType:
    m = from_json_bytes(col._backend.get_stock_notetype_legacy(kind))
    col.models.add(m)
    return m


def addBasicModel(col: anki.collection.Collection) -> anki.models.NoteType:
    return _add_stock_notetype(col, StockNotetypeKind.BASIC)


def addBasicTypingModel(col: anki.collection.Collection) -> anki.models.NoteType:
    return _add_stock_notetype(col, StockNotetypeKind.BASIC_TYPING)


def addForwardReverse(col: anki.collection.Collection) -> anki.models.NoteType:
    return _add_stock_notetype(col, StockNotetypeKind.BASIC_AND_REVERSED)


def addForwardOptionalReverse(col: anki.collection.Collection) -> anki.models.NoteType:
    return _add_stock_notetype(col, StockNotetypeKind.BASIC_OPTIONAL_REVERSED)


def addClozeModel(col: anki.collection.Collection) -> anki.models.NoteType:
    return _add_stock_notetype(col, StockNotetypeKind.CLOZE)


def get_stock_notetypes(
    col: anki.collection.Collection,
) -> List[Tuple[str, Callable[[anki.collection.Collection], anki.models.NoteType]]]:
    out: List[
        Tuple[str, Callable[[anki.collection.Collection], anki.models.NoteType]]
    ] = []
    # add standard
    for (kind, func) in [
        (StockNotetypeKind.BASIC, addBasicModel),
        (StockNotetypeKind.BASIC_TYPING, addBasicTypingModel),
        (StockNotetypeKind.BASIC_AND_REVERSED, addForwardReverse),
        (
            StockNotetypeKind.BASIC_OPTIONAL_REVERSED,
            addForwardOptionalReverse,
        ),
        (StockNotetypeKind.CLOZE, addClozeModel),
    ]:
        m = from_json_bytes(col._backend.get_stock_notetype_legacy(kind))
        out.append((m["name"], func))
    # add extras from add-ons
    for (name_or_func, func) in models:
        if not isinstance(name_or_func, str):
            name = name_or_func()
        else:
            name = name_or_func
        out.append((name, func))
    return out
