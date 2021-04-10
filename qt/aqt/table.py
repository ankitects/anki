# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from abc import ABC, abstractmethod, abstractproperty
from dataclasses import dataclass
from typing import (
    Any,
    Callable,
    Dict,
    Generator,
    List,
    Optional,
    Sequence,
    Tuple,
    Union,
    cast,
)

import aqt
import aqt.forms
from anki.cards import Card, CardId
from anki.collection import BrowserColumns as Columns
from anki.collection import BrowserRow, Collection, Config, OpChanges
from anki.consts import *
from anki.errors import NotFoundError
from anki.notes import Note, NoteId
from anki.utils import ids2str, isWin
from aqt import colors, gui_hooks
from aqt.operations import OpMeta
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import (
    KeyboardModifiersPressed,
    qtMenuShortcutWorkaround,
    restoreHeader,
    showInfo,
    tr,
)

Column = Columns.Column
ItemId = Union[CardId, NoteId]
ItemList = Union[Sequence[CardId], Sequence[NoteId]]


@dataclass
class SearchContext:
    search: str
    browser: aqt.browser.Browser
    order: Union[bool, str] = True
    # if set, provided ids will be used instead of the regular search
    ids: Optional[Sequence[ItemId]] = None


class Table:
    SELECTION_LIMIT: int = 500

    def __init__(self, browser: aqt.browser.Browser) -> None:
        self.browser = browser
        self.col: Collection = browser.col
        self._state: ItemState = (
            NoteState(self.col)
            if self.col.get_config_bool(Config.Bool.BROWSER_TABLE_SHOW_NOTES_MODE)
            else CardState(self.col)
        )
        self._model = DataModel(self.col, self._state)
        self._view: Optional[QTableView] = None
        self._current_item: Optional[ItemId] = None
        self._selected_items: Sequence[ItemId] = []

    def set_view(self, view: QTableView) -> None:
        self._view = view
        self._setup_view()
        self._setup_headers()

    # Public Methods
    ######################################################################

    # Get metadata

    def len(self) -> int:
        return self._model.len_rows()

    def len_selection(self) -> int:
        return len(self._view.selectionModel().selectedRows())

    def has_current(self) -> bool:
        return self._view.selectionModel().currentIndex().isValid()

    def has_previous(self) -> bool:
        return self.has_current() and self._current().row() > 0

    def has_next(self) -> bool:
        return self.has_current() and self._current().row() < self.len() - 1

    def is_notes_mode(self) -> bool:
        return self._state.is_notes_mode()

    # Get objects

    def get_current_card(self) -> Optional[Card]:
        if not self.has_current():
            return None
        return self._model.get_card(self._current())

    def get_current_note(self) -> Optional[Note]:
        if not self.has_current():
            return None
        return self._model.get_note(self._current())

    def get_single_selected_card(self) -> Optional[Card]:
        """If there is only one row selected return its card, else None.
        This may be a different one than the current card."""
        if self.len_selection() != 1:
            return None
        return self._model.get_card(self._selected()[0])

    # Get ids

    def get_selected_card_ids(self) -> Sequence[CardId]:
        return self._model.get_card_ids(self._selected())

    def get_selected_note_ids(self) -> Sequence[NoteId]:
        return self._model.get_note_ids(self._selected())

    def get_card_ids_from_selected_note_ids(self) -> Sequence[CardId]:
        return self._state.card_ids_from_note_ids(self.get_selected_note_ids())

    # Selecting

    def select_all(self) -> None:
        self._view.selectAll()

    def clear_selection(self) -> None:
        self._view.selectionModel().clear()

    def invert_selection(self) -> None:
        selection = self._view.selectionModel().selection()
        self.select_all()
        self._view.selectionModel().select(
            selection,
            cast(
                QItemSelectionModel.SelectionFlags,
                QItemSelectionModel.Deselect | QItemSelectionModel.Rows,
            ),
        )

    def select_single_card(self, card_id: CardId) -> None:
        """Try to set the selection to the item corresponding to the given card."""
        self.clear_selection()
        if (row := self._model.get_card_row(card_id)) is not None:
            self._view.selectRow(row)

    # Reset

    def reset(self) -> None:
        """Reload table data from collection and redraw."""
        self.begin_reset()
        self.end_reset()

    def begin_reset(self) -> None:
        self._save_selection()
        self._model.begin_reset()

    def end_reset(self) -> None:
        self._model.end_reset()
        self._restore_selection(self._intersected_selection)

    def on_backend_will_block(self) -> None:
        # make sure the card list doesn't try to refresh itself during the operation,
        # as that will block the UI
        self._model.begin_blocking()

    def on_backend_did_block(self) -> None:
        self._model.end_blocking()

    def redraw_cells(self) -> None:
        self._model.redraw_cells()

    def op_executed(self, changes: OpChanges, meta: OpMeta, focused: bool) -> None:
        if changes.browser_table:
            self._model.mark_cache_stale()
        if focused:
            self.redraw_cells()

    # Modify table

    def search(self, txt: str) -> None:
        self._save_selection()
        self._model.search(SearchContext(search=txt, browser=self.browser))
        self._restore_selection(self._intersected_selection)

    def toggle_state(self, is_notes_mode: bool, last_search: str) -> None:
        if is_notes_mode == self.is_notes_mode():
            return
        self._save_selection()
        self._state = self._model.toggle_state(
            SearchContext(search=last_search, browser=self.browser)
        )
        self.col.set_config_bool(
            Config.Bool.BROWSER_TABLE_SHOW_NOTES_MODE, self.is_notes_mode()
        )
        self._set_sort_indicator()
        self._set_column_sizes()
        self._restore_selection(self._toggled_selection)

    # Move cursor

    def to_previous_row(self) -> None:
        self._move_current(QAbstractItemView.MoveUp)

    def to_next_row(self) -> None:
        self._move_current(QAbstractItemView.MoveDown)

    def to_first_row(self) -> None:
        self._move_current_to_row(0)

    def to_last_row(self) -> None:
        self._move_current_to_row(self._model.len_rows() - 1)

    # Private methods
    ######################################################################

    # Helpers

    def _current(self) -> QModelIndex:
        return self._view.selectionModel().currentIndex()

    def _selected(self) -> List[QModelIndex]:
        return self._view.selectionModel().selectedRows()

    def _set_current(self, row: int, column: int = 0) -> None:
        index = self._model.index(
            row, self._view.horizontalHeader().logicalIndex(column)
        )
        self._view.selectionModel().setCurrentIndex(index, QItemSelectionModel.NoUpdate)

    def _select_rows(self, rows: List[int]) -> None:
        selection = QItemSelection()
        for row in rows:
            selection.select(
                self._model.index(row, 0),
                self._model.index(row, self._model.len_columns() - 1),
            )
        self._view.selectionModel().select(selection, QItemSelectionModel.SelectCurrent)

    def _set_sort_indicator(self) -> None:
        hh = self._view.horizontalHeader()
        index = self._model.active_column_index(self._state.sort_column)
        if index is None:
            hh.setSortIndicatorShown(False)
            return
        if self._state.sort_backwards:
            order = Qt.DescendingOrder
        else:
            order = Qt.AscendingOrder
        hh.blockSignals(True)
        hh.setSortIndicator(index, order)
        hh.blockSignals(False)
        hh.setSortIndicatorShown(True)

    def _set_column_sizes(self) -> None:
        hh = self._view.horizontalHeader()
        hh.setSectionResizeMode(QHeaderView.Interactive)
        hh.setSectionResizeMode(
            hh.logicalIndex(self._model.len_columns() - 1), QHeaderView.Stretch
        )
        # this must be set post-resize or it doesn't work
        hh.setCascadingSectionResizes(False)

    # Setup

    def _setup_view(self) -> None:
        self._view.setSortingEnabled(True)
        self._view.setModel(self._model)
        self._view.selectionModel()
        self._view.setItemDelegate(StatusDelegate(self.browser, self._model))
        qconnect(
            self._view.selectionModel().selectionChanged, self.browser.onRowChanged
        )
        self._view.setWordWrap(False)
        self._update_font()
        if not theme_manager.night_mode:
            self._view.setStyleSheet(
                "QTableView{ selection-background-color: rgba(150, 150, 150, 50); "
                "selection-color: black; }"
            )
        elif theme_manager.macos_dark_mode():
            self._view.setStyleSheet(
                f"QTableView {{ gridline-color: {colors.FRAME_BG} }}"
            )
        self._view.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(self._view.customContextMenuRequested, self._on_context_menu)

    def _update_font(self) -> None:
        # we can't choose different line heights efficiently, so we need
        # to pick a line height big enough for any card template
        curmax = 16
        for m in self.col.models.all():
            for t in m["tmpls"]:
                bsize = t.get("bsize", 0)
                if bsize > curmax:
                    curmax = bsize
        self._view.verticalHeader().setDefaultSectionSize(curmax + 6)

    def _setup_headers(self) -> None:
        vh = self._view.verticalHeader()
        hh = self._view.horizontalHeader()
        if not isWin:
            vh.hide()
            hh.show()
        restoreHeader(hh, "editor")
        hh.setHighlightSections(False)
        hh.setMinimumSectionSize(50)
        hh.setSectionsMovable(True)
        self._set_column_sizes()
        hh.setContextMenuPolicy(Qt.CustomContextMenu)
        qconnect(hh.customContextMenuRequested, self._on_header_context)
        self._set_sort_indicator()
        qconnect(hh.sortIndicatorChanged, self._on_sort_column_changed)
        qconnect(hh.sectionMoved, self._on_column_moved)

    # Slots

    def _on_context_menu(self, _point: QPoint) -> None:
        menu = QMenu()
        if self.is_notes_mode():
            main = self.browser.form.menu_Notes
            other = self.browser.form.menu_Cards
            other_name = tr.qt_accel_cards()
        else:
            main = self.browser.form.menu_Cards
            other = self.browser.form.menu_Notes
            other_name = tr.qt_accel_notes()
        for action in main.actions():
            menu.addAction(action)
        menu.addSeparator()
        sub_menu = menu.addMenu(other_name)
        for action in other.actions():
            sub_menu.addAction(action)
        gui_hooks.browser_will_show_context_menu(self.browser, menu)
        qtMenuShortcutWorkaround(menu)
        menu.exec_(QCursor.pos())

    def _on_header_context(self, pos: QPoint) -> None:
        gpos = self._view.mapToGlobal(pos)
        m = QMenu()
        for key, column in self._model.columns.items():
            a = m.addAction(self._state.column_label(column))
            a.setCheckable(True)
            a.setChecked(self._model.active_column_index(key) is not None)
            qconnect(
                a.toggled,
                lambda checked, key=key: self._on_column_toggled(checked, key),
            )
        gui_hooks.browser_header_will_show_context_menu(self.browser, m)
        m.exec_(gpos)

    def _on_column_moved(self, *_args: Any) -> None:
        self._set_column_sizes()

    def _on_column_toggled(self, checked: bool, column: str) -> None:
        if not checked and self._model.len_columns() < 2:
            showInfo(tr.browsing_you_must_have_at_least_one())
            return
        self._model.toggle_column(column)
        self._set_column_sizes()
        # sorted field may have been hidden or revealed
        self._set_sort_indicator()
        if checked:
            self._scroll_to_column(self._model.len_columns() - 1)

    def _on_sort_column_changed(self, section: int, order: int) -> None:
        order = bool(order)
        column = self._model.column_at_section(section)
        if column.sorting == Columns.SORTING_NONE:
            showInfo(tr.browsing_sorting_on_this_column_is_not())
            sort_key = self._state.sort_column
        else:
            sort_key = column.key
        if self._state.sort_column != sort_key:
            self._state.sort_column = sort_key
            # default to descending for non-text fields
            if column.sorting == Columns.SORTING_REVERSED:
                order = not order
            self._state.sort_backwards = order
            self.browser.search()
        else:
            if self._state.sort_backwards != order:
                self._state.sort_backwards = order
                self._reverse()
        self._set_sort_indicator()

    def _reverse(self) -> None:
        self._save_selection()
        self._model.reverse()
        self._restore_selection(self._intersected_selection)

    # Restore selection

    def _save_selection(self) -> None:
        """Save the current item and selected items."""
        if self.has_current():
            self._current_item = self._model.get_item(self._current())
        self._selected_items = self._model.get_items(self._selected())

    def _restore_selection(self, new_selected_and_current: Callable) -> None:
        """Restore the saved selection and current element as far as possible and scroll to the
        new current element. Clear the saved selection.
        """
        self.clear_selection()
        if not self._model.is_empty():
            rows, current = new_selected_and_current()
            rows = self._qualify_selected_rows(rows, current)
            current = current or rows[0]
            self._select_rows(rows)
            self._set_current(current)
            # editor may pop up and hide the row later on
            QTimer.singleShot(100, lambda: self._scroll_to_row(current))
        if self.len_selection() == 0:
            # no row change will fire
            self.browser.onRowChanged(QItemSelection(), QItemSelection())
        self._selected_items = []
        self._current_item = None

    def _qualify_selected_rows(
        self, rows: List[int], current: Optional[int]
    ) -> List[int]:
        """Return between 1 and SELECTION_LIMIT rows, as far as possible from rows or current."""
        if rows:
            if len(rows) < self.SELECTION_LIMIT:
                return rows
            if current and current in rows:
                return [current]
            return rows[0:1]
        return [current if current else 0]

    def _intersected_selection(self) -> Tuple[List[int], Optional[int]]:
        """Return all rows of items that were in the saved selection and the row of the saved
        current element if present.
        """
        selected_rows = self._model.get_item_rows(self._selected_items)
        current_row = self._current_item and self._model.get_item_row(
            self._current_item
        )
        return selected_rows, current_row

    def _toggled_selection(self) -> Tuple[List[int], Optional[int]]:
        """Convert the items of the saved selection and current element to the new state and
        return their rows.
        """
        selected_rows = self._model.get_item_rows(
            self._state.get_new_items(self._selected_items)
        )
        current_row = None
        if self._current_item:
            if new_current := self._state.get_new_items([self._current_item]):
                current_row = self._model.get_item_row(new_current[0])
        return selected_rows, current_row

    # Move

    def _scroll_to_row(self, row: int) -> None:
        """Scroll vertically to row."""
        top_border = self._view.rowViewportPosition(row)
        bottom_border = top_border + self._view.rowHeight(0)
        visible = top_border >= 0 and bottom_border < self._view.viewport().height()
        if not visible:
            horizontal = self._view.horizontalScrollBar().value()
            self._view.scrollTo(self._model.index(row, 0), self._view.PositionAtCenter)
            self._view.horizontalScrollBar().setValue(horizontal)

    def _scroll_to_column(self, column: int) -> None:
        """Scroll horizontally to column."""
        position = self._view.columnViewportPosition(column)
        visible = 0 <= position < self._view.viewport().width()
        if not visible:
            vertical = self._view.verticalScrollBar().value()
            self._view.scrollTo(
                self._model.index(0, column), self._view.PositionAtCenter
            )
            self._view.verticalScrollBar().setValue(vertical)

    def _move_current(self, direction: int, index: QModelIndex = None) -> None:
        if not self.has_current():
            return
        if index is None:
            index = self._view.moveCursor(
                cast(QAbstractItemView.CursorAction, direction),
                self.browser.mw.app.keyboardModifiers(),
            )
        self._view.selectionModel().setCurrentIndex(
            index,
            cast(
                QItemSelectionModel.SelectionFlag,
                QItemSelectionModel.Clear
                | QItemSelectionModel.Select
                | QItemSelectionModel.Rows,
            ),
        )

    def _move_current_to_row(self, row: int) -> None:
        old = self._view.selectionModel().currentIndex()
        self._move_current(None, self._model.index(row, 0))
        if not KeyboardModifiersPressed().shift:
            return
        new = self._view.selectionModel().currentIndex()
        selection = QItemSelection(new, old)
        self._view.selectionModel().select(
            selection,
            cast(
                QItemSelectionModel.SelectionFlag,
                QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows,
            ),
        )


# ItemStates
######################################################################


class ItemState(ABC):
    _active_columns: List[str]
    _sort_column: str
    _sort_backwards: bool

    def __init__(self, col: Collection) -> None:
        self.col = col

    def is_notes_mode(self) -> bool:
        """Return True if the state is a NoteState."""
        return isinstance(self, NoteState)

    # Stateless Helpers

    def note_ids_from_card_ids(self, items: Sequence[ItemId]) -> Sequence[NoteId]:
        return self.col.db.list(
            f"select distinct nid from cards where id in {ids2str(items)}"
        )

    def card_ids_from_note_ids(self, items: Sequence[ItemId]) -> Sequence[CardId]:
        return self.col.db.list(f"select id from cards where nid in {ids2str(items)}")

    def column_key_at(self, index: int) -> str:
        return self._active_columns[index]

    def column_label(self, column: Column) -> str:
        return (
            column.notes_mode_label if self.is_notes_mode() else column.cards_mode_label
        )

    # Columns and sorting

    # abstractproperty is deprecated but used due to mypy limitations
    # (https://github.com/python/mypy/issues/1362)
    @abstractproperty
    def active_columns(self) -> List[str]:
        """Return the saved or default columns for the state."""

    @abstractmethod
    def toggle_active_column(self, column: str) -> None:
        """Add or remove an active column."""

    @abstractproperty
    def sort_column(self) -> str:
        """Return the sort column from the config."""

    @sort_column.setter
    def sort_column(self, column: str) -> None:
        """Save the sort column in the config."""

    @abstractproperty
    def sort_backwards(self) -> bool:
        """Return the sort order from the config."""

    @sort_backwards.setter
    def sort_backwards(self, order: bool) -> None:
        """Save the sort order in the config."""

    # Get objects

    @abstractmethod
    def get_card(self, item: ItemId) -> Card:
        """Return the item if it's a card or its first card if it's a note."""

    @abstractmethod
    def get_note(self, item: ItemId) -> Note:
        """Return the item if it's a note or its note if it's a card."""

    # Get ids

    @abstractmethod
    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[ItemId]:
        """Return the item ids fitting the given search and order."""

    @abstractmethod
    def get_item_from_card_id(self, card: CardId) -> ItemId:
        """Return the appropriate item id for a card id."""

    @abstractmethod
    def get_card_ids(self, items: Sequence[ItemId]) -> Sequence[CardId]:
        """Return the card ids for the given item ids."""

    @abstractmethod
    def get_note_ids(self, items: Sequence[ItemId]) -> Sequence[NoteId]:
        """Return the note ids for the given item ids."""

    # Toggle

    @abstractmethod
    def toggle_state(self) -> ItemState:
        """Return an instance of the other state."""

    @abstractmethod
    def get_new_items(self, old_items: Sequence[ItemId]) -> ItemList:
        """Given a list of ids from the other state, return the corresponding ids for this state."""


class CardState(ItemState):
    def __init__(self, col: Collection) -> None:
        super().__init__(col)
        self._active_columns = self.col.load_browser_card_columns()
        self._sort_column = self.col.get_config("sortType")
        self._sort_backwards = self.col.get_config_bool(
            Config.Bool.BROWSER_SORT_BACKWARDS
        )

    @property
    def active_columns(self) -> List[str]:
        return self._active_columns

    def toggle_active_column(self, column: str) -> None:
        if column in self._active_columns:
            self._active_columns.remove(column)
        else:
            self._active_columns.append(column)
        self.col.set_browser_card_columns(self._active_columns)

    @property
    def sort_column(self) -> str:
        return self._sort_column

    @sort_column.setter
    def sort_column(self, column: str) -> None:
        self.col.set_config("sortType", column)
        self._sort_column = column

    @property
    def sort_backwards(self) -> bool:
        return self._sort_backwards

    @sort_backwards.setter
    def sort_backwards(self, order: bool) -> None:
        self.col.set_config_bool(Config.Bool.BROWSER_SORT_BACKWARDS, order)
        self._sort_backwards = order

    def get_card(self, item: ItemId) -> Card:
        return self.col.get_card(CardId(item))

    def get_note(self, item: ItemId) -> Note:
        return self.get_card(item).note()

    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[ItemId]:
        return self.col.find_cards(search, order)

    def get_item_from_card_id(self, card: CardId) -> ItemId:
        return card

    def get_card_ids(self, items: Sequence[ItemId]) -> Sequence[CardId]:
        return cast(Sequence[CardId], items)

    def get_note_ids(self, items: Sequence[ItemId]) -> Sequence[NoteId]:
        return super().note_ids_from_card_ids(items)

    def toggle_state(self) -> NoteState:
        return NoteState(self.col)

    def get_new_items(self, old_items: Sequence[ItemId]) -> Sequence[CardId]:
        return super().card_ids_from_note_ids(old_items)


class NoteState(ItemState):
    def __init__(self, col: Collection) -> None:
        super().__init__(col)
        self._active_columns = self.col.load_browser_note_columns()
        self._sort_column = self.col.get_config("noteSortType")
        self._sort_backwards = self.col.get_config_bool(
            Config.Bool.BROWSER_NOTE_SORT_BACKWARDS
        )

    @property
    def active_columns(self) -> List[str]:
        return self._active_columns

    def toggle_active_column(self, column: str) -> None:
        if column in self._active_columns:
            self._active_columns.remove(column)
        else:
            self._active_columns.append(column)
        self.col.set_browser_note_columns(self._active_columns)

    @property
    def sort_column(self) -> str:
        return self._sort_column

    @sort_column.setter
    def sort_column(self, column: str) -> None:
        self.col.set_config("noteSortType", column)
        self._sort_column = column

    @property
    def sort_backwards(self) -> bool:
        return self._sort_backwards

    @sort_backwards.setter
    def sort_backwards(self, order: bool) -> None:
        self.col.set_config_bool(Config.Bool.BROWSER_NOTE_SORT_BACKWARDS, order)
        self._sort_backwards = order

    def get_card(self, item: ItemId) -> Card:
        return self.get_note(item).cards()[0]

    def get_note(self, item: ItemId) -> Note:
        return self.col.get_note(NoteId(item))

    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[ItemId]:
        return self.col.find_notes(search, order)

    def get_item_from_card_id(self, card: CardId) -> ItemId:
        return self.col.get_card(card).note().id

    def get_card_ids(self, items: Sequence[ItemId]) -> Sequence[CardId]:
        return super().card_ids_from_note_ids(items)

    def get_note_ids(self, items: Sequence[ItemId]) -> Sequence[NoteId]:
        return cast(Sequence[NoteId], items)

    def toggle_state(self) -> CardState:
        return CardState(self.col)

    def get_new_items(self, old_items: Sequence[ItemId]) -> Sequence[NoteId]:
        return super().note_ids_from_card_ids(old_items)


# Data model
##########################################################################


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
    return None


class DataModel(QAbstractTableModel):
    def __init__(self, col: Collection, state: ItemState) -> None:
        QAbstractTableModel.__init__(self)
        self.col: Collection = col
        self.columns: Dict[str, Column] = dict(
            ((c.key, c) for c in self.col.all_browser_columns())
        )
        self._state: ItemState = state
        self._items: Sequence[ItemId] = []
        self._rows: Dict[int, CellRow] = {}
        # serve stale content to avoid hitting the DB?
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
            gui_hooks.browser_will_search(context)
            if context.ids is None:
                context.ids = self._state.find_items(context.search, context.order)
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


# Line painter
######################################################################


class StatusDelegate(QItemDelegate):
    def __init__(self, browser: aqt.browser.Browser, model: DataModel) -> None:
        QItemDelegate.__init__(self, browser)
        self._model = model

    def paint(
        self, painter: QPainter, option: QStyleOptionViewItem, index: QModelIndex
    ) -> None:
        if self._model.get_cell(index).is_rtl:
            option.direction = Qt.RightToLeft
        if row_color := self._model.get_row(index).color:
            brush = QBrush(theme_manager.qcolor(row_color))
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()
        return QItemDelegate.paint(self, painter, option, index)


def addon_column_fillin(key: str) -> Column:
    """Return a column with generic fields and a label indicating to the user that this column was
    added by an add-on.
    """
    return Column(
        key=key,
        cards_mode_label=tr.browsing_addon(),
        notes_mode_label=tr.browsing_addon(),
        sorting=Columns.SORTING_NONE,
        uses_cell_font=False,
        alignment=Columns.ALIGNMENT_CENTER,
    )
