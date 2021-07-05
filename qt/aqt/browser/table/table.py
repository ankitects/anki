# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from typing import Any, Callable, List, Optional, Sequence, Tuple, cast

import aqt
import aqt.forms
from anki.cards import Card, CardId
from anki.collection import Collection, Config, OpChanges
from anki.consts import *
from anki.notes import Note, NoteId
from anki.utils import isWin
from aqt import colors, gui_hooks
from aqt.browser.table import Columns, ItemId, SearchContext
from aqt.browser.table.model import DataModel
from aqt.browser.table.state import CardState, ItemState, NoteState
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import (
    KeyboardModifiersPressed,
    qtMenuShortcutWorkaround,
    restoreHeader,
    saveHeader,
    showInfo,
    tr,
)


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

    def cleanup(self) -> None:
        self._save_header()

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
            self._scroll_to_row(row, scroll_even_if_visible=True)

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

    def op_executed(
        self, changes: OpChanges, handler: Optional[object], focused: bool
    ) -> None:
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
        self._save_header()
        self._save_selection()
        self._state = self._model.toggle_state(
            SearchContext(search=last_search, browser=self.browser)
        )
        self.col.set_config_bool(
            Config.Bool.BROWSER_TABLE_SHOW_NOTES_MODE,
            self.is_notes_mode(),
        )
        self._restore_header()
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

    def _save_header(self) -> None:
        saveHeader(self._view.horizontalHeader(), self._state.GEOMETRY_KEY_PREFIX)

    def _restore_header(self) -> None:
        self._view.horizontalHeader().blockSignals(True)
        restoreHeader(self._view.horizontalHeader(), self._state.GEOMETRY_KEY_PREFIX)
        self._set_column_sizes()
        self._set_sort_indicator()
        self._view.horizontalHeader().blockSignals(False)

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
        self._view.setHorizontalScrollMode(QAbstractItemView.ScrollPerPixel)
        self._view.horizontalScrollBar().setSingleStep(10)
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
        hh.setHighlightSections(False)
        hh.setMinimumSectionSize(50)
        hh.setSectionsMovable(True)
        hh.setContextMenuPolicy(Qt.CustomContextMenu)
        self._restore_header()
        qconnect(hh.customContextMenuRequested, self._on_header_context)
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
        column = self._model.column_at_section(section)
        if column.sorting == Columns.SORTING_NONE:
            showInfo(tr.browsing_sorting_on_this_column_is_not())
            self._set_sort_indicator()
            return
        order = bool(order)
        if self._state.sort_column != column.key:
            self._state.sort_column = column.key
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
            self._scroll_to_row(current)
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

    def _scroll_to_row(self, row: int, scroll_even_if_visible: bool = False) -> None:
        """Scroll vertically to row."""
        top_border = self._view.rowViewportPosition(row)
        bottom_border = top_border + self._view.rowHeight(0)
        visible = top_border >= 0 and bottom_border < self._view.viewport().height()
        if not visible or scroll_even_if_visible:
            horizontal = self._view.horizontalScrollBar().value()
            self._view.scrollTo(self._model.index(row, 0), self._view.PositionAtTop)
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
