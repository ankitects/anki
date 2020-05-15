# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Callable, List, Tuple

from anki.collection import _Collection
from anki.models import NoteType
from anki.rsbackend import StockNoteType

# add-on authors can add ("note type name", function_like_addBasicModel)
# to this list to have it shown in the add/clone note type screen
models: List[Tuple] = []


def add_stock_notetype(col: _Collection, kind: StockNoteType) -> NoteType:
    m = col.backend.get_stock_notetype_legacy(kind)
    col.models.add(m)
    return m


def addBasicModel(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.StockNoteTypeBasic)


def addBasicTypingModel(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.StockNoteTypeBasicTyping)


def addForwardReverse(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.StockNoteTypeBasicAndReversed)


def addForwardOptionalReverse(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.StockNoteTypeBasicOptionalReversed)


def addClozeModel(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.StockNoteTypeCloze)


def get_stock_notetypes(
    col: _Collection,
) -> List[Tuple[str, Callable[[_Collection], NoteType]]]:
    out: List[Tuple[str, Callable[[_Collection], NoteType]]] = []
    # add standard
    for (kind, func) in [
        (StockNoteType.StockNoteTypeBasic, addBasicModel),
        (StockNoteType.StockNoteTypeBasicTyping, addBasicTypingModel),
        (StockNoteType.StockNoteTypeBasicAndReversed, addForwardReverse),
        (StockNoteType.StockNoteTypeBasicOptionalReversed, addForwardOptionalReverse),
        (StockNoteType.StockNoteTypeCloze, addClozeModel),
    ]:
        m = col.backend.get_stock_notetype_legacy(kind)
        out.append((m["name"], func))
    # add extras from add-ons
    for (name_or_func, func) in models:
        if not isinstance(name_or_func, str):
            name = name_or_func()
        else:
            name = name_or_func
        out.append((name, func))
    return out
