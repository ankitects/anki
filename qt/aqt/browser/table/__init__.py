# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import copy
import time
from collections.abc import Generator, Sequence
from dataclasses import dataclass
from typing import TYPE_CHECKING, Union

import aqt
import aqt.browser
from anki.cards import CardId
from anki.collection import BrowserColumns as Columns
from anki.collection import BrowserRow
from anki.notes import NoteId
from aqt import colors
from aqt.qt import QColor
from aqt.utils import tr

Column = Columns.Column
ItemId = Union[CardId, NoteId]
ItemList = Union[Sequence[CardId], Sequence[NoteId]]


@dataclass
class SearchContext:
    search: str
    browser: aqt.browser.Browser
    order: bool | str | Column = True
    reverse: bool = False
    # if set, provided ids will be used instead of the regular search
    ids: Sequence[ItemId] | None = None


class Cell:
    def __init__(
        self, text: str, is_rtl: bool, elide_mode: BrowserRow.Cell.TextElideMode.V
    ) -> None:
        self.text: str = text
        self.is_rtl: bool = is_rtl
        self.elide_mode: aqt.Qt.TextElideMode = backend_elide_mode_to_aqt_elide_mode(
            elide_mode
        )


class CellRow:
    is_disabled: bool = False

    def __init__(
        self,
        cells: Generator[tuple[str, bool, BrowserRow.Cell.TextElideMode.V], None, None],
        color: BrowserRow.Color.V,
        font_name: str,
        font_size: int,
    ) -> None:
        self.refreshed_at: float = time.time()
        self.cells: tuple[Cell, ...] = tuple(Cell(*cell) for cell in cells)
        self.color: dict[str, str] | None = backend_color_to_aqt_color(color)
        self.font_name: str = font_name or "arial"
        self.font_size: int = font_size if font_size > 0 else 12

    def is_stale(self, threshold: float) -> bool:
        return self.refreshed_at < threshold

    @staticmethod
    def generic(length: int, cell_text: str) -> CellRow:
        return CellRow(
            ((cell_text, False, BrowserRow.Cell.ElideRight) for cell in range(length)),
            BrowserRow.COLOR_DEFAULT,
            "arial",
            12,
        )

    @staticmethod
    def placeholder(length: int) -> CellRow:
        return CellRow.generic(length, "...")

    @staticmethod
    def disabled(length: int, cell_text: str) -> CellRow:
        row = CellRow.generic(length, cell_text)
        row.is_disabled = True
        return row


def backend_elide_mode_to_aqt_elide_mode(
    elide_mode: BrowserRow.Cell.TextElideMode.V,
) -> aqt.Qt.TextElideMode:
    if elide_mode == BrowserRow.Cell.ElideLeft:
        return aqt.Qt.TextElideMode.ElideLeft
    if elide_mode == BrowserRow.Cell.ElideMiddle:
        return aqt.Qt.TextElideMode.ElideMiddle
    if elide_mode == BrowserRow.Cell.ElideNone:
        return aqt.Qt.TextElideMode.ElideNone
    return aqt.Qt.TextElideMode.ElideRight


def backend_color_to_aqt_color(color: BrowserRow.Color.V) -> dict[str, str] | None:
    temp_color = None

    if color == BrowserRow.COLOR_MARKED:
        temp_color = colors.STATE_MARKED
    if color == BrowserRow.COLOR_SUSPENDED:
        temp_color = colors.STATE_SUSPENDED
    if color == BrowserRow.COLOR_BURIED:
        temp_color = colors.STATE_BURIED
    if color == BrowserRow.COLOR_FLAG_RED:
        temp_color = colors.FLAG_1
    if color == BrowserRow.COLOR_FLAG_ORANGE:
        temp_color = colors.FLAG_2
    if color == BrowserRow.COLOR_FLAG_GREEN:
        temp_color = colors.FLAG_3
    if color == BrowserRow.COLOR_FLAG_BLUE:
        temp_color = colors.FLAG_4
    if color == BrowserRow.COLOR_FLAG_PINK:
        temp_color = colors.FLAG_5
    if color == BrowserRow.COLOR_FLAG_TURQUOISE:
        temp_color = colors.FLAG_6
    if color == BrowserRow.COLOR_FLAG_PURPLE:
        temp_color = colors.FLAG_7

    return adjusted_bg_color(temp_color)


def adjusted_bg_color(color: dict[str, str] | None) -> dict[str, str] | None:
    if color:
        adjusted_color = copy.copy(color)
        light = QColor(color["light"]).lighter(150)
        adjusted_color["light"] = light.name()
        dark = QColor(color["dark"]).darker(150)
        adjusted_color["dark"] = dark.name()
        return adjusted_color
    else:
        return None


from .model import DataModel
from .state import CardState, ItemState, NoteState
from .table import StatusDelegate, Table
