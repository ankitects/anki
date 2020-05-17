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
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC)


def addBasicTypingModel(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC_TYPING)


def addForwardReverse(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC_AND_REVERSED)


def addForwardOptionalReverse(col: _Collection) -> NoteType:
    return add_stock_notetype(
        col, StockNoteType.STOCK_NOTE_TYPE_BASIC_OPTIONAL_REVERSED
    )


def addClozeModel(col: _Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_CLOZE)


def get_stock_notetypes(
    col: _Collection,
) -> List[Tuple[str, Callable[[_Collection], NoteType]]]:
    out: List[Tuple[str, Callable[[_Collection], NoteType]]] = []
    # add standard
    for (kind, func) in [
        (StockNoteType.STOCK_NOTE_TYPE_BASIC, addBasicModel),
        (StockNoteType.STOCK_NOTE_TYPE_BASIC_TYPING, addBasicTypingModel),
        (StockNoteType.STOCK_NOTE_TYPE_BASIC_AND_REVERSED, addForwardReverse),
        (
            StockNoteType.STOCK_NOTE_TYPE_BASIC_OPTIONAL_REVERSED,
            addForwardOptionalReverse,
        ),
        (StockNoteType.STOCK_NOTE_TYPE_CLOZE, addClozeModel),
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
