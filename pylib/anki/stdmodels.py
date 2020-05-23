# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Callable, List, Tuple

from anki.collection import Collection
from anki.models import NoteType
from anki.rsbackend import StockNoteType, from_json_bytes

# add-on authors can add ("note type name", function_like_addBasicModel)
# to this list to have it shown in the add/clone note type screen
models: List[Tuple] = []


def add_stock_notetype(col: Collection, kind: StockNoteType) -> NoteType:
    m = from_json_bytes(col.backend.get_stock_notetype_legacy(kind))
    col.models.add(m)
    return m


def addBasicModel(col: Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC)


def addBasicTypingModel(col: Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC_TYPING)


def addForwardReverse(col: Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_BASIC_AND_REVERSED)


def addForwardOptionalReverse(col: Collection) -> NoteType:
    return add_stock_notetype(
        col, StockNoteType.STOCK_NOTE_TYPE_BASIC_OPTIONAL_REVERSED
    )


def addClozeModel(col: Collection) -> NoteType:
    return add_stock_notetype(col, StockNoteType.STOCK_NOTE_TYPE_CLOZE)


def get_stock_notetypes(
    col: Collection,
) -> List[Tuple[str, Callable[[Collection], NoteType]]]:
    out: List[Tuple[str, Callable[[Collection], NoteType]]] = []
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
        m = from_json_bytes(col.backend.get_stock_notetype_legacy(kind))
        out.append((m["name"], func))
    # add extras from add-ons
    for (name_or_func, func) in models:
        if not isinstance(name_or_func, str):
            name = name_or_func()
        else:
            name = name_or_func
        out.append((name, func))
    return out
