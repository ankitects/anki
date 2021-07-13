# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from typing import Any, Dict, List, Optional, Sequence, Union, cast

from anki.cards import Card, CardId
from anki.collection import BrowserColumns as Columns
from anki.collection import Collection
from anki.consts import *
from anki.errors import NotFoundError
from anki.notes import Note, NoteId
from aqt import gui_hooks
from aqt.browser.table import Cell, CellRow, Column, ItemId, SearchContext
from aqt.browser.table.state import ItemState
from aqt.qt import *
from aqt.utils import tr


class DataModel(QAbstractTableModel):
    """Data manager for the browser table.

    _items -- The card or note ids currently hold and corresponding to the
              table's rows.
    _rows -- The cached data objects to render items to rows.
    columns -- The data objects of all available columns, used to define the display
               of active columns and list all toggleable columns to the user.
    _block_updates -- If True, serve stale content to avoid hitting the DB.
    _stale_cutoff -- A threshold to decide whether a cached row has gone stale.
    """

    def __init__(self, col: Collection, state: ItemState) -> None:
        QAbstractTableModel.__init__(self)
        self.col: Collection = col
        self.columns: Dict[str, Column] = dict(
            ((c.key, c) for c in self.col.all_browser_columns())
        )
        gui_hooks.browser_did_fetch_columns(self.columns)
        self._state: ItemState = state
        self._items: Sequence[ItemId] = []
        self._rows: Dict[int, CellRow] = {}
        self._block_updates = False
        self._stale_cutoff = 0.0

    # Row Object Interface
    ######################################################################

    # Get Rows

    def get_cell(self, index: QModelIndex) -> Cell:
        return self.get_row(index).cells[index.column()]

    def get_row(self, index: QModelIndex) -> CellRow:
        item = self.get_item(index)
        if row := self._rows.get(item):
            if not self._block_updates and row.is_stale(self._stale_cutoff):
                # need to refresh
                self._rows[item] = self._fetch_row_from_backend(item)
                return self._rows[item]
            # return row, even if it's stale
            return row
        if self._block_updates:
            # blank row until we unblock
            return CellRow.placeholder(self.len_columns())
        # missing row, need to build
        self._rows[item] = self._fetch_row_from_backend(item)
        return self._rows[item]

    def _fetch_row_from_backend(self, item: ItemId) -> CellRow:
        try:
            row = CellRow(*self.col.browser_row_for_id(item))
        except NotFoundError:
            return CellRow.deleted(self.len_columns())
        except Exception as e:
            return CellRow.generic(self.len_columns(), str(e))
        except BaseException as e:
            # fatal error like a panic in the backend - dump it to the
            # console so it gets picked up by the error handler
            import traceback

            traceback.print_exc()
            # and prevent Qt from firing a storm of follow-up errors
            self._block_updates = True
            return CellRow.generic(self.len_columns(), "error")

        gui_hooks.browser_did_fetch_row(
            item, self._state.is_notes_mode(), row, self._state.active_columns
        )
        return row

    # Reset

    def mark_cache_stale(self) -> None:
        self._stale_cutoff = time.time()

    def reset(self) -> None:
        self.begin_reset()
        self.end_reset()

    def begin_reset(self) -> None:
        self.beginResetModel()
        self.mark_cache_stale()

    def end_reset(self) -> None:
        self.endResetModel()

    # Block/Unblock

    def begin_blocking(self) -> None:
        self._block_updates = True

    def end_blocking(self) -> None:
        self._block_updates = False
        self.redraw_cells()

    def redraw_cells(self) -> None:
        "Update cell contents, without changing search count/columns/sorting."
        if self.is_empty():
            return
        top_left = self.index(0, 0)
        bottom_right = self.index(self.len_rows() - 1, self.len_columns() - 1)
        self.dataChanged.emit(top_left, bottom_right)  # type: ignore

    # Item Interface
    ######################################################################

    # Get metadata

    def is_empty(self) -> bool:
        return not self._items

    def len_rows(self) -> int:
        return len(self._items)

    def len_columns(self) -> int:
        return len(self._state.active_columns)

    # Get items (card or note ids depending on state)

    def get_item(self, index: QModelIndex) -> ItemId:
        return self._items[index.row()]

    def get_items(self, indices: List[QModelIndex]) -> Sequence[ItemId]:
        return [self.get_item(index) for index in indices]

    def get_card_ids(self, indices: List[QModelIndex]) -> Sequence[CardId]:
        return self._state.get_card_ids(self.get_items(indices))

    def get_note_ids(self, indices: List[QModelIndex]) -> Sequence[NoteId]:
        return self._state.get_note_ids(self.get_items(indices))

    # Get row numbers from items

    def get_item_row(self, item: ItemId) -> Optional[int]:
        for row, i in enumerate(self._items):
            if i == item:
                return row
        return None

    def get_item_rows(self, items: Sequence[ItemId]) -> List[int]:
        rows = []
        for row, i in enumerate(self._items):
            if i in items:
                rows.append(row)
        return rows

    def get_card_row(self, card_id: CardId) -> Optional[int]:
        return self.get_item_row(self._state.get_item_from_card_id(card_id))

    # Get objects (cards or notes)

    def get_card(self, index: QModelIndex) -> Optional[Card]:
        """Try to return the indicated, possibly deleted card."""
        try:
            return self._state.get_card(self.get_item(index))
        except NotFoundError:
            return None

    def get_note(self, index: QModelIndex) -> Optional[Note]:
        """Try to return the indicated, possibly deleted note."""
        try:
            return self._state.get_note(self.get_item(index))
        except NotFoundError:
            return None

    # Table Interface
    ######################################################################

    def toggle_state(self, context: SearchContext) -> ItemState:
        self.beginResetModel()
        self._state = self._state.toggle_state()
        self.search(context)
        return self._state

    # Rows

    def search(self, context: SearchContext) -> None:
        self.begin_reset()
        try:
            if context.order is True:
                try:
                    context.order = self.columns[self._state.sort_column]
                except KeyError:
                    # invalid sort column in config
                    context.order = self.columns["noteCrt"]
                context.reverse = self._state.sort_backwards
            gui_hooks.browser_will_search(context)
            if context.ids is None:
                context.ids = self._state.find_items(
                    context.search, context.order, context.reverse
                )
            gui_hooks.browser_did_search(context)
            self._items = context.ids
            self._rows = {}
        finally:
            self.end_reset()

    def reverse(self) -> None:
        self.beginResetModel()
        self._items = list(reversed(self._items))
        self.endResetModel()

    # Columns

    def column_at(self, index: QModelIndex) -> Column:
        return self.column_at_section(index.column())

    def column_at_section(self, section: int) -> Column:
        """Returns the column object corresponding to the active column at index or the default
        column object if no data is associated with the active column.
        """
        key = self._state.column_key_at(section)
        try:
            return self.columns[key]
        except KeyError:
            self.columns[key] = addon_column_fillin(key)
            return self.columns[key]

    def active_column_index(self, column: str) -> Optional[int]:
        return (
            self._state.active_columns.index(column)
            if column in self._state.active_columns
            else None
        )

    def toggle_column(self, column: str) -> None:
        self.begin_reset()
        self._state.toggle_active_column(column)
        self.end_reset()

    # Model interface
    ######################################################################

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:
        if parent and parent.isValid():
            return 0
        return self.len_rows()

    def columnCount(self, parent: QModelIndex = QModelIndex()) -> int:
        if parent and parent.isValid():
            return 0
        return self.len_columns()

    def data(self, index: QModelIndex = QModelIndex(), role: int = 0) -> Any:
        if not index.isValid():
            return QVariant()
        if role == Qt.FontRole:
            if not self.column_at(index).uses_cell_font:
                return QVariant()
            qfont = QFont()
            row = self.get_row(index)
            qfont.setFamily(row.font_name)
            qfont.setPixelSize(row.font_size)
            return qfont
        if role == Qt.TextAlignmentRole:
            align: Union[Qt.AlignmentFlag, int] = Qt.AlignVCenter
            if self.column_at(index).alignment == Columns.ALIGNMENT_CENTER:
                align |= Qt.AlignHCenter
            return align
        if role in (Qt.DisplayRole, Qt.ToolTipRole):
            return self.get_cell(index).text
        return QVariant()

    def headerData(
        self, section: int, orientation: Qt.Orientation, role: int = 0
    ) -> Optional[str]:
        if orientation == Qt.Horizontal and role == Qt.DisplayRole:
            return self._state.column_label(self.column_at_section(section))
        return None

    def flags(self, index: QModelIndex) -> Qt.ItemFlags:
        if self.get_row(index).is_deleted:
            return Qt.ItemFlags(Qt.NoItemFlags)
        return cast(Qt.ItemFlags, Qt.ItemIsEnabled | Qt.ItemIsSelectable)


def addon_column_fillin(key: str) -> Column:
    """Return a column with generic fields and a label indicating to the user that this column was
    added by an add-on.
    """
    return Column(
        key=key,
        cards_mode_label=f"{tr.browsing_addon()} ({key})",
        notes_mode_label=f"{tr.browsing_addon()} ({key})",
        sorting=Columns.SORTING_NONE,
        uses_cell_font=False,
        alignment=Columns.ALIGNMENT_CENTER,
    )
