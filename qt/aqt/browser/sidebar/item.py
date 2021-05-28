# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from enum import Enum, auto
from typing import Callable, Iterable, List, Optional, Union

from anki.collection import SearchNode
from aqt.theme import ColoredIcon


class SidebarItemType(Enum):
    ROOT = auto()
    SAVED_SEARCH_ROOT = auto()
    SAVED_SEARCH = auto()
    TODAY_ROOT = auto()
    TODAY = auto()
    FLAG_ROOT = auto()
    FLAG = auto()
    CARD_STATE_ROOT = auto()
    CARD_STATE = auto()
    DECK_ROOT = auto()
    DECK_CURRENT = auto()
    DECK = auto()
    NOTETYPE_ROOT = auto()
    NOTETYPE = auto()
    NOTETYPE_TEMPLATE = auto()
    TAG_ROOT = auto()
    TAG_NONE = auto()
    TAG = auto()

    CUSTOM = auto()

    @staticmethod
    def section_roots() -> Iterable[SidebarItemType]:
        return (type for type in SidebarItemType if type.name.endswith("_ROOT"))

    def is_section_root(self) -> bool:
        return self in self.section_roots()

    def is_editable(self) -> bool:
        return self in (
            SidebarItemType.FLAG,
            SidebarItemType.SAVED_SEARCH,
            SidebarItemType.DECK,
            SidebarItemType.TAG,
        )

    def is_deletable(self) -> bool:
        return self in (
            SidebarItemType.SAVED_SEARCH,
            SidebarItemType.DECK,
            SidebarItemType.TAG,
        )


class SidebarItem:
    def __init__(
        self,
        name: str,
        icon: Union[str, ColoredIcon],
        search_node: Optional[SearchNode] = None,
        on_expanded: Callable[[bool], None] = None,
        expanded: bool = False,
        item_type: SidebarItemType = SidebarItemType.CUSTOM,
        id: int = 0,
        name_prefix: str = "",
    ) -> None:
        self.name = name
        self.name_prefix = name_prefix
        self.full_name = name_prefix + name
        self.icon = icon
        self.item_type = item_type
        self.id = id
        self.search_node = search_node
        self.on_expanded = on_expanded
        self.children: List["SidebarItem"] = []
        self.tooltip: Optional[str] = None
        self._parent_item: Optional["SidebarItem"] = None
        self._expanded = expanded
        self._row_in_parent: Optional[int] = None
        self._search_matches_self = False
        self._search_matches_child = False

    def add_child(self, cb: "SidebarItem") -> None:
        self.children.append(cb)
        cb._parent_item = self

    def add_simple(
        self,
        name: str,
        icon: Union[str, ColoredIcon],
        type: SidebarItemType,
        search_node: Optional[SearchNode],
    ) -> SidebarItem:
        "Add child sidebar item, and return it."
        item = SidebarItem(
            name=name,
            icon=icon,
            search_node=search_node,
            item_type=type,
        )
        self.add_child(item)
        return item

    @property
    def expanded(self) -> bool:
        return self._expanded

    @expanded.setter
    def expanded(self, expanded: bool) -> None:
        if self.expanded != expanded:
            self._expanded = expanded
            if self.on_expanded:
                self.on_expanded(expanded)

    def show_expanded(self, searching: bool) -> bool:
        if not searching:
            return self.expanded
        if self._search_matches_child:
            return True
        # if search matches top level, expand children one level
        return self._search_matches_self and self.item_type.is_section_root()

    def is_highlighted(self) -> bool:
        return self._search_matches_self

    def search(self, lowered_text: str) -> bool:
        "True if we or child matched."
        self._search_matches_self = lowered_text in self.name.lower()
        self._search_matches_child = any(
            [child.search(lowered_text) for child in self.children]
        )
        return self._search_matches_self or self._search_matches_child

    def has_same_id(self, other: SidebarItem) -> bool:
        "True if `other` is same type, with same id/name."
        if other.item_type == self.item_type:
            if self.item_type == SidebarItemType.TAG:
                return self.full_name == other.full_name
            elif self.item_type == SidebarItemType.SAVED_SEARCH:
                return self.name == other.name
            elif self.item_type == SidebarItemType.NOTETYPE_TEMPLATE:
                return (
                    other.id == self.id
                    and other._parent_item.id == self._parent_item.id
                )
            else:
                return other.id == self.id

        return False
