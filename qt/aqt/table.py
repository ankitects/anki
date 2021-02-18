# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from abc import ABC, abstractmethod
from operator import itemgetter
from typing import (
    TYPE_CHECKING,
    Any,
    Callable,
    Dict,
    List,
    Optional,
    Sequence,
    Tuple,
    Union,
    cast,
)

import aqt
import aqt.forms
from anki.cards import Card
from anki.collection import Collection, ConfigBoolKey
from anki.consts import *
from anki.notes import Note
from anki.utils import htmlToTextLine, ids2str, isWin
from aqt import gui_hooks
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import TR, qtMenuShortcutWorkaround, restoreHeader, showInfo, tr

if TYPE_CHECKING:
    from anki.lang import TRValue


class Table:
    """"""

    SELECTION_LIMIT: int = 500

    def __init__(self, browser: aqt.browser.Browser) -> None:
        self.browser = browser
        self.col: Collection = browser.col
        self.state: ItemState = (
            CardState(self.col)
            if self.col.get_config("cardState", True)
            else NoteState(self.col)
        )
        self.model = DataModel(self.col, self.state)
        self._view: Optional[QTableView] = None
        self._current_item: Optional[int] = None
        self._selected_items: Sequence[int] = []

    def set_view(self, view: QTableView) -> None:
        self._view = view
        self._setup_view()
        self._setup_headers()

    # Public Methods
    ######################################################################

    # Get metadata

    def len(self) -> int:
        return self.model.len_rows()

    def len_selection(self) -> int:
        return len(self._view.selectionModel().selectedRows())

    def has_current(self) -> bool:
        return self._view.selectionModel().currentIndex().isValid()

    def has_previous(self) -> bool:
        return self.has_current() and self._get_current_row() > 0

    def has_next(self) -> bool:
        return self.has_current() and self._get_current_row() < self.len() - 1

    def single_selected_row(self) -> bool:
        return self.len_selection() == 1

    def is_card_state(self) -> bool:
        return self.state.is_card_state()

    # Get objects

    def get_current_card(self) -> Card:
        return self.model.get_card(self._get_current_row())

    def get_current_note(self, reload: bool = False) -> Note:
        return self.model.get_note(self._get_current_row(), reload)

    def get_single_selected_card(self) -> Optional[Card]:
        if self.len_selection() != 1:
            return None
        return self.model.get_card(self._get_selected_rows()[0])

    # Get ids

    def get_selected_card_ids(self) -> List[int]:
        return self.model.get_card_ids(self._get_selected_rows())

    def get_selected_note_ids(self) -> List[int]:
        return self.model.get_note_ids(self._get_selected_rows())

    def get_card_ids_from_selected_note_ids(self) -> List[int]:
        return self.state.card_ids_from_note_ids(self.get_selected_note_ids())

    def set_current_to_unselected_note(self) -> None:
        """Set the current row to the first row below or above the current one where the
        corresponding note is not selected.
        """
        current = self._get_current_row()
        selected = self.get_selected_note_ids()
        for i in range(current - 1, 0, -1):
            if self.model.get_note_ids([i])[0] not in selected:
                self._set_current(i)
                return
        for i in range(current + 1, self.len()):
            if self.model.get_note_ids([i])[0] not in selected:
                self._set_current(i)
                return

    # Select rows

    def select_item(self, item: int) -> None:
        row = self.model.get_row(item)
        if row is not None:
            self._view.selectRow(row)

    def select_single_card(self, card: int) -> None:
        """Try to set the selection to the item corresponding to the given card."""
        self._view.selectionModel().clear()
        try:
            self._view.selectRow(self.model.get_card_row(card))
        except ValueError:
            pass

    def _set_current(self, row: int, column: int = 0) -> None:
        index = self.model.index(row, self._view.horizontalHeader().logicalIndex(column))
        self._view.selectionModel().setCurrentIndex(index, QItemSelectionModel.NoUpdate)

    # Update table

    def reset(self) -> None:
        """Reload table data from collection and redraw."""
        self._save_selection()
        self.model.reset()
        self._restore_selection(self._intersected_selection)

    def refresh_note(self, note: Note) -> None:
        return self.model.refresh_note(note)

    def search(self, txt: str) -> None:
        self._save_selection()
        self.model.search(aqt.browser.SearchContext(search=txt, browser=self.browser))
        self._restore_selection(self._intersected_selection)

    # Alter table

    def toggle_state(self, is_card_state: bool) -> None:
        if is_card_state == self.is_card_state():
            return
        self._save_selection()
        self.state = self.model.toggle_state()
        self._restore_selection(self._toggled_selection)

    def toggle_column(self, checked: bool, column: str) -> None:
        def callback() -> None:
            # sorted field may have been hidden
            self._set_sort_indicator()
            self._set_column_sizes()

        if checked:
            new_column = self.model.add_column(column, callback)
            self._scroll_to_column(new_column)
        else:
            if self.model.len_columns() < 2:
                showInfo(tr(TR.BROWSING_YOU_MUST_HAVE_AT_LEAST_ONE))
                return
            self.model.remove_column(column, callback)

    def change_sort_column(self, index: int, order: bool) -> None:
        sort_column = self.model.get_active_column(index)
        if sort_column in ("question", "answer"):
            showInfo(tr(TR.BROWSING_SORTING_ON_THIS_COLUMN_IS_NOT))
            sort_column = self.col.get_config("sortType")
        if self.col.get_config("sortType") != sort_column:
            self.col.set_config("sortType", sort_column)
            # default to descending for non-text fields
            if sort_column == "noteFld":
                order = not order
            self.col.set_config_bool(ConfigBoolKey.BROWSER_SORT_BACKWARDS, order)
            self.browser.search()
        else:
            if self.col.get_config_bool(ConfigBoolKey.BROWSER_SORT_BACKWARDS) != order:
                self.col.set_config_bool(ConfigBoolKey.BROWSER_SORT_BACKWARDS, order)
                self.model.reverse()
        self._set_sort_indicator()

    # Move cursor

    def to_previous_row(self) -> None:
        self._move_current(QAbstractItemView.MoveUp)

    def to_next_row(self) -> None:
        self._move_current(QAbstractItemView.MoveDown)

    def to_first_row(self) -> None:
        self._move_current_to_row(0)

    def to_last_row(self) -> None:
        self._move_current_to_row(self.model.len_rows() - 1)

    # Private methods
    ######################################################################

    # Helpers

    def _get_current_row(self) -> int:
        return self._view.selectionModel().currentIndex().row()

    def _get_selected_rows(self) -> List[int]:
        return [index.row() for index in self._view.selectionModel().selectedRows()]

    def _select_rows(self, rows: List[int]) -> None:
        selection = QItemSelection()
        for row in rows:
            selection.select(
                self.model.index(row, 0),
                self.model.index(row, self.model.len_columns() - 1),
            )
        self._view.selectionModel().select(selection, QItemSelectionModel.SelectCurrent)

    # Setup

    def _setup_view(self) -> None:
        self._view.setSortingEnabled(True)
        self._view.setModel(self.model)
        self._view.selectionModel()
        self._view.setItemDelegate(StatusDelegate(self.model))
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
            grid = theme_manager.str_color("frame-bg")
            self._view.setStyleSheet(f"QTableView {{ gridline-color: {grid} }}")
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
        qconnect(hh.sortIndicatorChanged, self.browser.onSortChanged)
        qconnect(hh.sectionMoved, self._on_column_moved)

    # Slots

    def _on_context_menu(self, _point: QPoint) -> None:
        menu = QMenu()
        if self.is_card_state():
            main = self.browser.form.menu_Cards
            other = self.browser.form.menu_Notes
            other_name = TR.QT_ACCEL_NOTES
        else:
            main = self.browser.form.menu_Notes
            other = self.browser.form.menu_Cards
            other_name = TR.QT_ACCEL_CARDS
        for action in main.actions():
            menu.addAction(action)
        menu.addSeparator()
        sub_menu = menu.addMenu(tr(other_name))
        for action in other.actions():
            sub_menu.addAction(action)
        gui_hooks.browser_will_show_context_menu(self.browser, menu)
        qtMenuShortcutWorkaround(menu)
        menu.exec_(QCursor.pos())

    def _on_header_context(self, pos: QPoint) -> None:
        gpos = self._view.mapToGlobal(pos)
        m = QMenu()
        for column, name in self.state.columns:
            a = m.addAction(name)
            a.setCheckable(True)
            a.setChecked(self.model.get_active_column_index(column) is not None)
            qconnect(
                a.toggled,
                lambda checked, column=column: self.browser.on_column_toggled(
                    checked, column
                ),
            )
        gui_hooks.browser_header_will_show_context_menu(self.browser, m)
        m.exec_(gpos)

    def _on_column_moved(self, *_args: Any) -> None:
        self._set_column_sizes()

    # Restore selection

    def _save_selection(self) -> None:
        """Save the current item and selected items."""
        if self.has_current():
            self._current_item = self.model.get_item(self._get_current_row())
        self._selected_items = self.model.get_items(self._get_selected_rows())

    def _restore_selection(self, new_selected_and_current: Callable) -> None:
        """Restore the saved selection and current element as far as possible and scroll to the
        new current element. Clear the saved selection.
        """
        self._view.selectionModel().clear()
        if not self.model.is_empty():
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

    def _qualify_selected_rows(self, rows: List[int], current: Optional[int]) -> List[int]:
        """Return between 1 and SELECTION_LIMIT rows, as far as possible from rows or current."""
        if rows:
            if len(rows) < self.SELECTION_LIMIT:
                return rows
            if current in rows:
                return [current]
            return rows[0:1]
        return [current if current else 0]

    def _intersected_selection(self) -> Tuple[List[int], Optional[int]]:
        """Return all rows of items that were in the saved selection and the row of the saved
        current element if present.
        """
        selected_rows = self.model.get_rows(self._selected_items)
        current_row = self._current_item and self.model.get_row(self._current_item)
        return selected_rows, current_row

    def _toggled_selection(self) -> Tuple[List[int], Optional[int]]:
        """Convert the items of the saved selection and current element to the new state and
        return their rows.
        """
        selected_rows = self.model.get_rows(
            self.state.get_new_items(self._selected_items)
        )
        current_row = self._current_item and self.model.get_row(
            self.state.get_new_item(self._current_item)
        )
        return selected_rows, current_row

    # Move

    def _scroll_to_row(self, row: int) -> None:
        """Scroll vertically to row."""
        position = self._view.rowViewportPosition(row)
        visible = 0 <= position < self._view.viewport().height()
        if not visible:
            horizontal = self._view.horizontalScrollBar().value()
            self._view.scrollTo(self.model.index(row, 0), self._view.PositionAtCenter)
            self._view.horizontalScrollBar().setValue(horizontal)

    def _scroll_to_column(self, column: int) -> None:
        """Scroll horizontally to column."""
        position = self._view.columnViewportPosition(column)
        visible = 0 <= position < self._view.viewport().width()
        if not visible:
            vertical = self._view.verticalScrollBar().value()
            self._view.scrollTo(
                self.model.index(0, column), self._view.PositionAtCenter
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
        self._move_current(None, self.model.index(row, 0))
        if not self.browser.mw.app.keyboardModifiers() & Qt.ShiftModifier:
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

    # Headers

    def _set_sort_indicator(self) -> None:
        hh = self._view.horizontalHeader()
        sort_column = self.col.get_config("sortType")
        index = self.model.get_active_column_index(sort_column)
        if index is None:
            hh.setSortIndicatorShown(False)
            return
        if self.col.get_config_bool(ConfigBoolKey.BROWSER_SORT_BACKWARDS):
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
            hh.logicalIndex(self.model.len_columns() - 1), QHeaderView.Stretch
        )
        # this must be set post-resize or it doesn't work
        hh.setCascadingSectionResizes(False)


class ItemState(ABC):
    """Abstract base class for state-dependent managing, conversion and caching of items."""

    def __init__(self, col: Collection) -> None:
        self.col = col

    def is_card_state(self) -> bool:
        """Return True if the state is a CardState."""
        return isinstance(self, CardState)

    # stateless helpers

    def note_ids_from_card_ids(self, items: Sequence[int]) -> List[int]:
        return self.col.db.list(
            f"select distinct nid from cards where id in {ids2str(items)}"
        )

    def card_ids_from_note_ids(self, items: Sequence[int]) -> List[int]:
        return self.col.db.list(f"select id from cards where nid in {ids2str(items)}")

    # Get objects

    @abstractmethod
    def get_card(self, item: int) -> Card:
        """Return the item if it's a card or its first card if it's a note."""

    @abstractmethod
    def get_note(self, item: int, reload: bool = False) -> Note:
        """Return the item if it's a note or its note if it's a card."""

    # Get ids

    @abstractmethod
    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[int]:
        """Return the item ids fitting the given search and order."""

    @abstractmethod
    def get_item_from_card_id(self, card: int) -> int:
        """Return the appropriate item id for a the card id."""

    @abstractmethod
    def get_card_ids(self, items: List[int]) -> List[int]:
        """Return the card ids for the given item ids."""

    @abstractmethod
    def get_note_ids(self, items: List[int]) -> List[int]:
        """Return the note ids for the given item ids."""

    # Toggle items

    @abstractmethod
    def get_new_item(self, old_item: int) -> int:
        """Given a list of ids from the last state,
        return the corresponding ids for the current state."""

    @abstractmethod
    def get_new_items(self, old_items: Sequence[int]) -> List[int]:
        """Given a list of ids from the last state,
        return the corresponding ids for the current state."""

    # Update

    @abstractmethod
    def reset(self) -> None:
        """Empty object cache."""

    @abstractmethod
    def toggle_state(self) -> ItemState:
        """Return an instance of the other state."""

    @abstractmethod
    def refresh_note(self, note: Note) -> bool:
        """Delete cached objects associated with the note and return True if there were any."""

    # Columns

    @property
    @abstractmethod
    def columns(self) -> List[Tuple[str, str]]:
        """Return all for the state available columns."""

    @abstractmethod
    def get_active_columns(self) -> List[str]:
        """Return the saved or default columns for the state."""

    @abstractmethod
    def save_columns(self, columns: List[str]) -> None:
        """Save the columns in the config for the state."""

    @abstractmethod
    def get_item_color(self, item: int) -> Optional[str]:
        """Return the item's row's color for the view."""


# ItemStates
######################################################################


class CardState(ItemState):
    _columns: Optional[List[Tuple[str, str]]] = None

    def __init__(self, col: Collection) -> None:
        self._cards: Dict[int, Card] = {}
        super().__init__(col)

    def get_card(self, item: int) -> Card:
        if not item in self._cards:
            self._cards[item] = self.col.getCard(item)
        return self._cards[item]

    def get_note(self, item: int, reload: bool = False) -> Note:
        return self.get_card(item).note(reload=reload)

    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[int]:
        return self.col.find_cards(search, order)

    def get_item_from_card_id(self, card: int) -> int:
        return card

    def get_card_ids(self, items: List[int]) -> List[int]:
        return items

    def get_note_ids(self, items: List[int]) -> List[int]:
        return super().note_ids_from_card_ids(items)

    def get_new_item(self, old_item: int) -> int:
        return super().card_ids_from_note_ids([old_item])[0]

    def get_new_items(self, old_items: Sequence[int]) -> List[int]:
        return super().card_ids_from_note_ids(old_items)

    def reset(self) -> None:
        self._cards = {}

    def toggle_state(self) -> NoteState:
        return NoteState(self.col)

    def refresh_note(self, note: Note) -> bool:
        refresh = False
        for card in note.cards():
            if card.id in self._cards:
                del self._cards[card.id]
                refresh = True
        return refresh

    @property
    def columns(self) -> List[Tuple[str, str]]:
        if self._columns is None:
            self._columns = [
                ("noteFld", tr(TR.BROWSING_SORT_FIELD)),
                ("noteCrt", tr(TR.BROWSING_CREATED)),
                ("noteMod", tr(TR.SEARCH_NOTE_MODIFIED)),
                ("noteTags", tr(TR.EDITING_TAGS)),
                ("note", tr(TR.BROWSING_NOTE)),
                ("question", tr(TR.BROWSING_QUESTION)),
                ("answer", tr(TR.BROWSING_ANSWER)),
                ("template", tr(TR.BROWSING_CARD)),
                ("deck", tr(TR.DECKS_DECK)),
                ("cardMod", tr(TR.SEARCH_CARD_MODIFIED)),
                ("cardDue", tr(TR.STATISTICS_DUE_DATE)),
                ("cardIvl", tr(TR.BROWSING_INTERVAL)),
                ("cardEase", tr(TR.BROWSING_EASE)),
                ("cardReps", tr(TR.SCHEDULING_REVIEWS)),
                ("cardLapses", tr(TR.SCHEDULING_LAPSES)),
            ]
            self._columns.sort(key=itemgetter(1))
        return self._columns

    def get_active_columns(self) -> List[str]:
        return self.col.get_config(
            "activeCols", ["noteFld", "template", "cardDue", "deck"]
        )

    def save_columns(self, columns: List[str]) -> None:
        self.col.set_config("activeCols", columns)

    def get_item_color(self, item: int) -> Optional[str]:
        card = self.get_card(item)
        if card.userFlag() > 0:
            return theme_manager.qcolor(f"flag{card.userFlag()}-bg")
        if card.queue == QUEUE_TYPE_SUSPENDED:
            return theme_manager.qcolor("suspended-bg")
        if self.get_note(item).hasTag("Marked"):
            return theme_manager.qcolor("marked-bg")
        return None


class NoteState(ItemState):
    _columns: Optional[List[Tuple[str, str]]] = None

    def __init__(self, col: Collection) -> None:
        self._notes: Dict[int, Note] = {}
        super().__init__(col)

    def get_card(self, item: int) -> Card:
        return self.get_note(item).card(0)

    def get_note(self, item: int, reload: bool = False) -> Note:
        if reload or not item in self._notes:
            self._notes[item] = self.col.getNote(item)
        return self._notes[item]

    def find_items(self, search: str, order: Union[bool, str]) -> Sequence[int]:
        return self.col.find_notes(search)

    def get_item_from_card_id(self, card: int) -> int:
        return self.get_card(card).note().id

    def get_card_ids(self, items: List[int]) -> List[int]:
        return super().card_ids_from_note_ids(items)

    def get_note_ids(self, items: List[int]) -> List[int]:
        return items

    def get_new_item(self, old_item: int) -> int:
        return super().note_ids_from_card_ids([old_item])[0]

    def get_new_items(self, old_items: Sequence[int]) -> List[int]:
        return super().note_ids_from_card_ids(old_items)

    def reset(self) -> None:
        self._notes = {}

    def toggle_state(self) -> CardState:
        return CardState(self.col)

    def refresh_note(self, note: Note) -> bool:
        if note.id in self._notes:
            del self._notes[note.id]
            return True
        return False

    @property
    def columns(self) -> List[Tuple[str, str]]:
        if self._columns is None:
            self._columns = [
                ("noteFld", tr(TR.BROWSING_SORT_FIELD)),
                ("noteCrt", tr(TR.BROWSING_CREATED)),
                ("noteMod", tr(TR.SEARCH_NOTE_MODIFIED)),
                ("noteTags", tr(TR.EDITING_TAGS)),
                ("note", tr(TR.BROWSING_NOTE)),
            ]
            self._columns.sort(key=itemgetter(1))
        return self._columns

    def get_active_columns(self) -> List[str]:
        return self.col.get_config(
            "activeNoteCols", ["noteFld", "note", "noteCrt", "noteMod"]
        )

    def save_columns(self, columns: List[str]) -> None:
        self.col.set_config("activeNoteCols", columns)

    def get_item_color(self, item: int) -> Optional[str]:
        if self.get_note(item).hasTag("Marked"):
            return theme_manager.qcolor("marked-bg")
        card = self.get_card(item)
        if card.userFlag() > 0:
            return theme_manager.qcolor(f"flag{card.userFlag()}-bg")
        if card.queue == QUEUE_TYPE_SUSPENDED:
            return theme_manager.qcolor("suspended-bg")
        return None


# Data model
##########################################################################


class DataModel(QAbstractTableModel):
    def __init__(self, col: Collection, state: ItemState) -> None:
        QAbstractTableModel.__init__(self)
        self.col: Collection = col
        self.state: ItemState = state
        self._items: Sequence[int] = []
        self._active_columns: List[str] = self.state.get_active_columns()

    # Public methods
    ######################################################################

    # Get metadata

    def is_empty(self) -> bool:
        return not bool(self._items)

    def len_rows(self) -> int:
        return len(self._items)

    def len_columns(self) -> int:
        return len(self._active_columns)

    def is_card_state(self) -> bool:
        return self.state.is_card_state()

    # Get items (card or note ids depending on state) from rows

    def get_item(self, row: int) -> int:
        return self._items[row]

    def get_items(self, rows: List[int]) -> List[int]:
        return [self._items[row] for row in rows]

    def get_card_ids(self, rows: List[int]) -> List[int]:
        return self.state.get_card_ids([self._items[row] for row in rows])

    def get_note_ids(self, rows: List[int]) -> List[int]:
        return self.state.get_note_ids([self._items[row] for row in rows])

    # Get rows (list indices) from items

    def get_row(self, item: int) -> Optional[int]:
        for row, i in enumerate(self._items):
            if i == item:
                return row
        return None

    def get_rows(self, items: Sequence[int]) -> List[int]:
        rows = []
        for row, i in enumerate(self._items):
            if i in items:
                rows.append(row)
        return rows

    def get_card_row(self, card: int) -> Optional[int]:
        return self.get_row(self.state.get_item_from_card_id(card))

    # Get objects (cards or notes) from rows

    def get_card(self, row: int) -> Card:
        return self.state.get_card(self._items[row])

    def get_note(self, row: int, reload: bool = False) -> Note:
        return self.state.get_note(self._items[row], reload)

    # Update model

    def begin_reset(self) -> None:
        self.beginResetModel()
        self.state.reset()

    def end_reset(self) -> None:
        self.endResetModel()

    def reset(self) -> None:
        self.begin_reset()
        self.end_reset()

    def toggle_state(self) -> ItemState:
        self.begin_reset()
        self.state = self.state.toggle_state()
        self._items = self.state.get_new_items(self._items)
        self._active_columns = self.state.get_active_columns()
        self.end_reset()
        return self.state

    def refresh_note(self, note: Note) -> None:
        if self.state.refresh_note(note):
            self.layoutChanged.emit()  # type: ignore

    def search(self, context: aqt.browser.SearchContext) -> None:
        self.begin_reset()
        try:
            gui_hooks.browser_will_search(context)
            if context.item_ids is None:
                context.item_ids = self.state.find_items(context.search, context.order)
            gui_hooks.browser_did_search(context)
            self._items = context.item_ids
        finally:
            self.end_reset()

    def reverse(self) -> None:
        self.beginResetModel()
        self._items = list(reversed(self._items))
        self.endResetModel()

    # Columns

    def get_active_column(self, index: int) -> str:
        return self._active_columns[index]

    def get_active_column_index(self, column: str) -> Optional[int]:
        return (
            self._active_columns.index(column)
            if column in self._active_columns
            else None
        )

    def add_column(self, column: str, callback: Callable) -> int:
        """Add a new column and return its index, calling callback before ending reset."""
        self.beginResetModel()
        self._active_columns.append(column)
        self.state.save_columns(self._active_columns)
        callback()
        self.endResetModel()
        return self.len_columns() - 1

    def remove_column(self, column: str, callback: Callable) -> None:
        """Remove a column, calling callback before ending reset."""
        self.beginResetModel()
        self._active_columns.remove(column)
        self.state.save_columns(self._active_columns)
        callback()
        self.endResetModel()

    # Model interface
    ######################################################################

    def rowCount(self, browser: QModelIndex = QModelIndex()) -> int:
        if browser and browser.isValid():
            return 0
        return len(self._items)

    def columnCount(self, browser: QModelIndex = QModelIndex()) -> int:
        if browser and browser.isValid():
            return 0
        return len(self._active_columns)

    def data(self, index: QModelIndex = QModelIndex(), role: int = 0) -> Any:
        if not index.isValid():
            return
        if role == Qt.FontRole:
            if self._active_columns[index.column()] not in (
                "question",
                "answer",
                "noteFld",
            ):
                return
            t = self.get_card(index.row()).template()
            if not t.get("bfont"):
                return
            f = QFont()
            f.setFamily(cast(str, t.get("bfont", "arial")))
            f.setPixelSize(cast(int, t.get("bsize", 12)))
            return f

        elif role == Qt.TextAlignmentRole:
            align: Union[Qt.AlignmentFlag, int] = Qt.AlignVCenter
            if self._active_columns[index.column()] not in (
                "question",
                "answer",
                "template",
                "deck",
                "noteFld",
                "note",
                "noteTags",
            ):
                align |= Qt.AlignHCenter
            return align
        elif role in (Qt.DisplayRole, Qt.EditRole):
            return self.columnData(index)
        else:
            return

    def headerData(
        self, section: int, orientation: Qt.Orientation, role: int = 0
    ) -> Optional[str]:
        if orientation == Qt.Vertical:
            return None
        elif role == Qt.DisplayRole and section < self.len_columns():
            column = self.columnType(section)
            txt = None
            for stype, name in self.state.columns:
                if column == stype:
                    txt = name
                    break
            # give the user a hint an invalid column was added by an add-on
            if not txt:
                txt = tr(TR.BROWSING_ADDON)
            return txt
        else:
            return None

    def flags(self, index: QModelIndex) -> Qt.ItemFlags:
        return cast(Qt.ItemFlags, Qt.ItemIsEnabled | Qt.ItemIsSelectable)

    # Column data
    ######################################################################

    def columnType(self, column: int) -> str:
        return self._active_columns[column]

    def time_format(self) -> str:
        return "%Y-%m-%d"

    def columnData(self, index: QModelIndex) -> str:
        column = self.get_active_column(index.column())

        note = self.get_note(index.row())
        if column == "noteFld":
            return htmlToTextLine(note.fields[self.col.models.sortIdx(note.model())])
        if column == "noteCrt":
            return time.strftime(self.time_format(), time.localtime(note.id / 1000))
        if column == "noteMod":
            return time.strftime(self.time_format(), time.localtime(note.mod))
        if column == "noteTags":
            return " ".join(note.tags)
        if column == "note":
            return note.model()["name"]

        card = self.get_card(index.row())
        if column == "question":
            return self.question(card)
        if column == "answer":
            return self.answer(card)
        if column == "template":
            t = card.template()["name"]
            if card.model()["type"] == MODEL_CLOZE:
                t = f"{t} {card.ord + 1}"
            return cast(str, t)
        if column == "cardDue":
            # catch invalid dates
            try:
                t = self.nextDue(card)
            except:
                t = ""
            if card.queue < 0:
                t = f"({t})"
            return t
        if column == "cardMod":
            return time.strftime(self.time_format(), time.localtime(card.mod))
        if column == "cardReps":
            return str(card.reps)
        if column == "cardLapses":
            return str(card.lapses)
        if column == "cardIvl":
            if card.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            elif card.type == CARD_TYPE_LRN:
                return tr(TR.BROWSING_LEARNING)
            return self.col.format_timespan(card.ivl * 86400)
        if column == "cardEase":
            if card.type == CARD_TYPE_NEW:
                return tr(TR.BROWSING_NEW)
            return "%d%%" % (card.factor / 10)
        if column == "deck":
            if card.odid:
                # in a cram deck
                return "%s (%s)" % (
                    self.col.decks.name(card.did),
                    self.col.decks.name(card.odid),
                )
            # normal deck
            return self.col.decks.name(card.did)

        return ""

    def question(self, c: Card) -> str:
        return htmlToTextLine(c.q(browser=True))

    def answer(self, c: Card) -> str:
        if c.template().get("bafmt"):
            # they have provided a template, use it verbatim
            c.q(browser=True)
            return htmlToTextLine(c.a())
        # need to strip question from answer
        q = self.question(c)
        a = htmlToTextLine(c.a())
        if a.startswith(q):
            return a[len(q) :].strip()
        return a

    def nextDue(self, c: Card) -> str:
        date: float
        if c.odid:
            return tr(TR.BROWSING_FILTERED)
        elif c.queue == QUEUE_TYPE_LRN:
            date = c.due
        elif c.queue == QUEUE_TYPE_NEW or c.type == CARD_TYPE_NEW:
            return tr(TR.STATISTICS_DUE_FOR_NEW_CARD, number=c.due)
        elif c.queue in (QUEUE_TYPE_REV, QUEUE_TYPE_DAY_LEARN_RELEARN) or (
            c.type == CARD_TYPE_REV and c.queue < 0
        ):
            date = time.time() + ((c.due - self.col.sched.today) * 86400)
        else:
            return ""
        return time.strftime(self.time_format(), time.localtime(date))

    def isRTL(self, index: QModelIndex) -> bool:
        column = self.get_active_column(index.column())
        if column != "noteFld":
            return False
        model = self.get_note(index.row()).model()
        return model["flds"][self.col.models.sortIdx(model)]["rtl"]


# Line painter
######################################################################


class StatusDelegate(QItemDelegate):
    def __init__(self, model: DataModel) -> None:
        QItemDelegate.__init__(self, model)
        self.model = model

    def paint(
        self, painter: QPainter, option: QStyleOptionViewItem, index: QModelIndex
    ) -> None:
        try:
            item = self.model.get_item(index.row())
        except:
            # in the the middle of a reset; return nothing so this row is not
            # rendered until we have a chance to reset the model
            return

        if self.model.isRTL(index):
            option.direction = Qt.RightToLeft

        color = self.model.state.get_item_color(item)
        if color:
            brush = QBrush(color)
            painter.save()
            painter.fillRect(option.rect, brush)
            painter.restore()

        return QItemDelegate.paint(self, painter, option, index)
