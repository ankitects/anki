# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from dataclasses import dataclass
from typing import TYPE_CHECKING, Generator, Optional, Sequence, Tuple, Union

import aqt
from anki.cards import CardId
from anki.collection import BrowserColumns as Columns
from anki.collection import BrowserRow
from anki.notes import NoteId
from aqt import colors
from aqt.utils import tr

Column = Columns.Column
ItemId = Union[CardId, NoteId]
ItemList = Union[Sequence[CardId], Sequence[NoteId]]


@dataclass
class SearchContext:
    search: str
    browser: aqt.browser.Browser
    order: Union[bool, str, Column] = True
    reverse: bool = False
    # if set, provided ids will be used instead of the regular search
    ids: Optional[Sequence[ItemId]] = None


@dataclass
class Cell:
    text: str
    is_rtl: bool


class CellRow:
    is_deleted: bool = False

    def __init__(
        self,
        cells: Generator[Tuple[str, bool], None, None],
        color: BrowserRow.Color.V,
        font_name: str,
        font_size: int,
    ) -> None:
        self.refreshed_at: float = time.time()
        self.cells: Tuple[Cell, ...] = tuple(Cell(*cell) for cell in cells)
        self.color: Optional[Tuple[str, str]] = backend_color_to_aqt_color(color)
        self.font_name: str = font_name or "arial"
        self.font_size: int = font_size if font_size > 0 else 12

    def is_stale(self, threshold: float) -> bool:
        return self.refreshed_at < threshold

    @staticmethod
    def generic(length: int, cell_text: str) -> CellRow:
        return CellRow(
            ((cell_text, False) for cell in range(length)),
            BrowserRow.COLOR_DEFAULT,
            "arial",
            12,
        )

    @staticmethod
    def placeholder(length: int) -> CellRow:
        return CellRow.generic(length, "...")

    @staticmethod
    def deleted(length: int) -> CellRow:
        row = CellRow.generic(length, tr.browsing_row_deleted())
        row.is_deleted = True
        return row


def backend_color_to_aqt_color(color: BrowserRow.Color.V) -> Optional[Tuple[str, str]]:
    if color == BrowserRow.COLOR_MARKED:
        return colors.MARKED_BG
    if color == BrowserRow.COLOR_SUSPENDED:
        return colors.SUSPENDED_BG
    if color == BrowserRow.COLOR_FLAG_RED:
        return colors.FLAG1_BG
    if color == BrowserRow.COLOR_FLAG_ORANGE:
        return colors.FLAG2_BG
    if color == BrowserRow.COLOR_FLAG_GREEN:
        return colors.FLAG3_BG
    if color == BrowserRow.COLOR_FLAG_BLUE:
        return colors.FLAG4_BG
    if color == BrowserRow.COLOR_FLAG_PINK:
        return colors.FLAG5_BG
    if color == BrowserRow.COLOR_FLAG_TURQUOISE:
        return colors.FLAG6_BG
    if color == BrowserRow.COLOR_FLAG_PURPLE:
        return colors.FLAG7_BG
    return None


from .model import DataModel
from .state import CardState, ItemState, NoteState
from .table import StatusDelegate, Table
