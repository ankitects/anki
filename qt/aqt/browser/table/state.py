# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from abc import ABC, abstractmethod, abstractproperty
from typing import List, Sequence, Union, cast

from anki.browser import BrowserConfig
from anki.cards import Card, CardId
from anki.collection import Collection
from anki.notes import Note, NoteId
from anki.utils import ids2str
from aqt.browser.table import Column, ItemId, ItemList


class ItemState(ABC):
    GEOMETRY_KEY_PREFIX: str
    SORT_COLUMN_KEY: str
    SORT_BACKWARDS_KEY: str
    _active_columns: List[str]

    def __init__(self, col: Collection) -> None:
        self.col = col
        self._sort_column = self.col.get_config(self.SORT_COLUMN_KEY)
        self._sort_backwards = self.col.get_config(self.SORT_BACKWARDS_KEY, False)

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

    @property
    def sort_column(self) -> str:
        return self._sort_column

    @sort_column.setter
    def sort_column(self, column: str) -> None:
        self.col.set_config(self.SORT_COLUMN_KEY, column)
        self._sort_column = column

    @property
    def sort_backwards(self) -> bool:
        return self._sort_backwards

    @sort_backwards.setter
    def sort_backwards(self, order: bool) -> None:
        self.col.set_config(self.SORT_BACKWARDS_KEY, order)
        self._sort_backwards = order

    # Get objects

    @abstractmethod
    def get_card(self, item: ItemId) -> Card:
        """Return the item if it's a card or its first card if it's a note."""

    @abstractmethod
    def get_note(self, item: ItemId) -> Note:
        """Return the item if it's a note or its note if it's a card."""

    # Get ids

    @abstractmethod
    def find_items(
        self, search: str, order: Union[bool, str, Column], reverse: bool
    ) -> Sequence[ItemId]:
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
    GEOMETRY_KEY_PREFIX = "editor"
    SORT_COLUMN_KEY = BrowserConfig.CARDS_SORT_COLUMN_KEY
    SORT_BACKWARDS_KEY = BrowserConfig.CARDS_SORT_BACKWARDS_KEY

    def __init__(self, col: Collection) -> None:
        super().__init__(col)
        self._active_columns = self.col.load_browser_card_columns()

    @property
    def active_columns(self) -> List[str]:
        return self._active_columns

    def toggle_active_column(self, column: str) -> None:
        if column in self._active_columns:
            self._active_columns.remove(column)
        else:
            self._active_columns.append(column)
        self.col.set_browser_card_columns(self._active_columns)

    def get_card(self, item: ItemId) -> Card:
        return self.col.get_card(CardId(item))

    def get_note(self, item: ItemId) -> Note:
        return self.get_card(item).note()

    def find_items(
        self, search: str, order: Union[bool, str, Column], reverse: bool
    ) -> Sequence[ItemId]:
        return self.col.find_cards(search, order, reverse)

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
    GEOMETRY_KEY_PREFIX = "editorNotesMode"
    SORT_COLUMN_KEY = BrowserConfig.NOTES_SORT_COLUMN_KEY
    SORT_BACKWARDS_KEY = BrowserConfig.NOTES_SORT_BACKWARDS_KEY

    def __init__(self, col: Collection) -> None:
        super().__init__(col)
        self._active_columns = self.col.load_browser_note_columns()

    @property
    def active_columns(self) -> List[str]:
        return self._active_columns

    def toggle_active_column(self, column: str) -> None:
        if column in self._active_columns:
            self._active_columns.remove(column)
        else:
            self._active_columns.append(column)
        self.col.set_browser_note_columns(self._active_columns)

    def get_card(self, item: ItemId) -> Card:
        return self.get_note(item).cards()[0]

    def get_note(self, item: ItemId) -> Note:
        return self.col.get_note(NoteId(item))

    def find_items(
        self, search: str, order: Union[bool, str, Column], reverse: bool
    ) -> Sequence[ItemId]:
        return self.col.find_notes(search, order, reverse)

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
