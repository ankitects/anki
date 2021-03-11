# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from enum import Enum, auto
from typing import Any, Dict, Iterable, List, Optional, Sequence, Tuple, cast

import aqt
from anki.collection import Config, SearchNode
from anki.decks import DeckTreeNode
from anki.errors import DeckIsFilteredError, InvalidInput
from anki.tags import TagTreeNode
from anki.types import assert_exhaustive
from aqt import colors, gui_hooks
from aqt.main import ResetReason
from aqt.models import Models
from aqt.qt import *
from aqt.theme import ColoredIcon, theme_manager
from aqt.utils import (
    TR,
    askUser,
    getOnlyText,
    show_invalid_search_error,
    showInfo,
    showWarning,
    tr,
)


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


class SidebarStage(Enum):
    ROOT = auto()
    SAVED_SEARCHES = auto()
    TODAY = auto()
    FLAGS = auto()
    CARD_STATE = auto()
    DECKS = auto()
    NOTETYPES = auto()
    TAGS = auto()


class SidebarItem:
    def __init__(
        self,
        name: str,
        icon: Union[str, ColoredIcon],
        on_click: Callable[[], None] = None,
        on_expanded: Callable[[bool], None] = None,
        expanded: bool = False,
        item_type: SidebarItemType = SidebarItemType.CUSTOM,
        id: int = 0,
        full_name: str = None,
    ) -> None:
        self.name = name
        if not full_name:
            full_name = name
        self.full_name = full_name
        self.icon = icon
        self.item_type = item_type
        self.id = id
        self.on_click = on_click
        self.on_expanded = on_expanded
        self.children: List["SidebarItem"] = []
        self.tooltip: Optional[str] = None
        self._parent_item: Optional["SidebarItem"] = None
        self._is_expanded = expanded
        self._row_in_parent: Optional[int] = None
        self._search_matches_self = False
        self._search_matches_child = False

    def add_child(self, cb: "SidebarItem") -> None:
        self.children.append(cb)
        cb._parent_item = self

    def add_simple(
        self,
        name: Union[str, TR.V],
        icon: Union[str, ColoredIcon],
        type: SidebarItemType,
        on_click: Callable[[], None],
    ) -> SidebarItem:
        "Add child sidebar item, and return it."
        if not isinstance(name, str):
            name = tr(name)
        item = SidebarItem(
            name=name,
            icon=icon,
            on_click=on_click,
            item_type=type,
        )
        self.add_child(item)
        return item

    def is_expanded(self, searching: bool) -> bool:
        if not searching:
            return self._is_expanded
        else:
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


class SidebarModel(QAbstractItemModel):
    def __init__(self, root: SidebarItem) -> None:
        super().__init__()
        self.root = root
        self._cache_rows(root)

    def _cache_rows(self, node: SidebarItem) -> None:
        "Cache index of children in parent."
        for row, item in enumerate(node.children):
            item._row_in_parent = row
            self._cache_rows(item)

    def item_for_index(self, idx: QModelIndex) -> SidebarItem:
        return idx.internalPointer()

    def search(self, text: str) -> bool:
        return self.root.search(text.lower())

    # Qt API
    ######################################################################

    def rowCount(self, parent: QModelIndex = QModelIndex()) -> int:
        if not parent.isValid():
            return len(self.root.children)
        else:
            item: SidebarItem = parent.internalPointer()
            return len(item.children)

    def columnCount(self, parent: QModelIndex = QModelIndex()) -> int:
        return 1

    def index(
        self, row: int, column: int, parent: QModelIndex = QModelIndex()
    ) -> QModelIndex:
        if not self.hasIndex(row, column, parent):
            return QModelIndex()

        parentItem: SidebarItem
        if not parent.isValid():
            parentItem = self.root
        else:
            parentItem = parent.internalPointer()

        item = parentItem.children[row]
        return self.createIndex(row, column, item)

    def parent(self, child: QModelIndex) -> QModelIndex:  # type: ignore
        if not child.isValid():
            return QModelIndex()

        childItem: SidebarItem = child.internalPointer()
        parentItem = childItem._parent_item

        if parentItem is None or parentItem == self.root:
            return QModelIndex()

        row = parentItem._row_in_parent

        return self.createIndex(row, 0, parentItem)

    def data(self, index: QModelIndex, role: int = Qt.DisplayRole) -> QVariant:
        if not index.isValid():
            return QVariant()

        if role not in (Qt.DisplayRole, Qt.DecorationRole, Qt.ToolTipRole):
            return QVariant()

        item: SidebarItem = index.internalPointer()

        if role == Qt.DisplayRole:
            return QVariant(item.name)
        elif role == Qt.ToolTipRole:
            return QVariant(item.tooltip)
        else:
            return QVariant(theme_manager.icon_from_resources(item.icon))

    def supportedDropActions(self) -> Qt.DropActions:
        return cast(Qt.DropActions, Qt.MoveAction)

    def flags(self, index: QModelIndex) -> Qt.ItemFlags:
        if not index.isValid():
            return cast(Qt.ItemFlags, Qt.ItemIsEnabled)
        flags = Qt.ItemIsEnabled | Qt.ItemIsSelectable

        item: SidebarItem = index.internalPointer()
        if item.item_type in (
            SidebarItemType.DECK,
            SidebarItemType.DECK_ROOT,
            SidebarItemType.TAG,
            SidebarItemType.TAG_ROOT,
        ):
            flags |= Qt.ItemIsDragEnabled | Qt.ItemIsDropEnabled

        return cast(Qt.ItemFlags, flags)


class SidebarSearchBar(QLineEdit):
    def __init__(self, sidebar: SidebarTreeView) -> None:
        QLineEdit.__init__(self, sidebar)
        self.setPlaceholderText(sidebar.col.tr(TR.BROWSING_SIDEBAR_FILTER))
        self.sidebar = sidebar
        self.timer = QTimer(self)
        self.timer.setInterval(600)
        self.timer.setSingleShot(True)
        self.setFrame(False)
        border = theme_manager.color(colors.MEDIUM_BORDER)
        styles = [
            "padding: 1px",
            "padding-left: 3px",
            f"border-bottom: 1px solid {border}",
        ]
        if _want_right_border():
            styles.append(
                f"border-right: 1px solid {border}",
            )

        self.setStyleSheet("QLineEdit { %s }" % ";".join(styles))

        qconnect(self.timer.timeout, self.onSearch)
        qconnect(self.textChanged, self.onTextChanged)

    def onTextChanged(self, text: str) -> None:
        if not self.timer.isActive():
            self.timer.start()

    def onSearch(self) -> None:
        self.sidebar.search_for(self.text())

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() in (Qt.Key_Up, Qt.Key_Down):
            self.sidebar.setFocus()
        elif evt.key() in (Qt.Key_Enter, Qt.Key_Return):
            self.onSearch()
        else:
            QLineEdit.keyPressEvent(self, evt)


def _want_right_border() -> bool:
    return not isMac or theme_manager.night_mode


class SidebarTreeView(QTreeView):
    def __init__(self, browser: aqt.browser.Browser) -> None:
        super().__init__()
        self.browser = browser
        self.mw = browser.mw
        self.col = self.mw.col
        self.current_search: Optional[str] = None

        self.setContextMenuPolicy(Qt.CustomContextMenu)
        self.customContextMenuRequested.connect(self.onContextMenu)  # type: ignore
        self.context_menus: Dict[SidebarItemType, Sequence[Tuple[str, Callable]]] = {
            SidebarItemType.DECK: (
                (tr(TR.ACTIONS_RENAME), self.rename_deck),
                (tr(TR.ACTIONS_DELETE), self.delete_deck),
            ),
            SidebarItemType.TAG: (
                (tr(TR.ACTIONS_RENAME), self.rename_tag),
                (tr(TR.ACTIONS_DELETE), self.remove_tag),
            ),
            SidebarItemType.SAVED_SEARCH: (
                (tr(TR.ACTIONS_RENAME), self.rename_saved_search),
                (tr(TR.ACTIONS_DELETE), self.remove_saved_search),
            ),
            SidebarItemType.NOTETYPE: ((tr(TR.ACTIONS_MANAGE), self.manage_notetype),),
            SidebarItemType.SAVED_SEARCH_ROOT: (
                (tr(TR.BROWSING_SIDEBAR_SAVE_CURRENT_SEARCH), self.save_current_search),
            ),
        }

        self.setUniformRowHeights(True)
        self.setHeaderHidden(True)
        self.setIndentation(15)
        self.setAutoExpandDelay(600)
        # pylint: disable=no-member
        # mode = QAbstractItemView.SelectionMode.ExtendedSelection  # type: ignore
        # self.setSelectionMode(mode)
        self.setDragDropMode(QAbstractItemView.InternalMove)
        self.setDragDropOverwriteMode(False)

        qconnect(self.expanded, self._on_expansion)
        qconnect(self.collapsed, self._on_collapse)

        # match window background color and tweak style
        bgcolor = QPalette().window().color().name()
        border = theme_manager.color(colors.MEDIUM_BORDER)
        styles = [
            "padding: 3px",
            "padding-right: 0px",
            "border: 0",
            f"background: {bgcolor}",
        ]
        if _want_right_border():
            styles.append(f"border-right: 1px solid {border}")

        self.setStyleSheet("QTreeView { %s }" % ";".join(styles))

    def model(self) -> SidebarModel:
        return super().model()

    def refresh(self) -> None:
        "Refresh list. No-op if sidebar is not visible."
        if not self.isVisible():
            return

        def on_done(fut: Future) -> None:
            root = fut.result()
            model = SidebarModel(root)

            # from PyQt5.QtTest import QAbstractItemModelTester
            # tester = QAbstractItemModelTester(model)

            self.setModel(model)
            if self.current_search:
                self.search_for(self.current_search)
            else:
                self._expand_where_necessary(model)

        self.mw.taskman.run_in_background(self._root_tree, on_done)

    def search_for(self, text: str) -> None:
        self.showColumn(0)
        if not text.strip():
            self.current_search = None
            self.refresh()
            return

        self.current_search = text
        # start from a collapsed state, as it's faster
        self.collapseAll()
        self.setColumnHidden(0, not self.model().search(text))
        self._expand_where_necessary(self.model(), searching=True)

    def _expand_where_necessary(
        self,
        model: SidebarModel,
        parent: Optional[QModelIndex] = None,
        searching: bool = False,
    ) -> None:
        parent = parent or QModelIndex()
        for row in range(model.rowCount(parent)):
            idx = model.index(row, 0, parent)
            if not idx.isValid():
                continue
            self._expand_where_necessary(model, idx, searching)
            if item := model.item_for_index(idx):
                if item.is_expanded(searching):
                    self.setExpanded(idx, True)

    def update_search(self, *terms: Union[str, SearchNode]) -> None:
        """Modify the current search string based on modifier keys, then refresh."""
        mods = self.mw.app.keyboardModifiers()
        previous = SearchNode(parsable_text=self.browser.current_search())
        current = self.mw.col.group_searches(*terms)

        # if Alt pressed, invert
        if mods & Qt.AltModifier:
            current = SearchNode(negated=current)

        try:
            if mods & Qt.ControlModifier and mods & Qt.ShiftModifier:
                # If Ctrl+Shift, replace searches nodes of the same type.
                search = self.col.replace_in_search_node(previous, current)
            elif mods & Qt.ControlModifier:
                # If Ctrl, AND with previous
                search = self.col.join_searches(previous, current, "AND")
            elif mods & Qt.ShiftModifier:
                # If Shift, OR with previous
                search = self.col.join_searches(previous, current, "OR")
            else:
                search = self.col.build_search_string(current)
        except InvalidInput as e:
            show_invalid_search_error(e)
        else:
            self.browser.search_for(search)

    # Qt API
    ###########

    def drawRow(
        self, painter: QPainter, options: QStyleOptionViewItem, idx: QModelIndex
    ) -> None:
        if self.current_search and (item := self.model().item_for_index(idx)):
            if item.is_highlighted():
                brush = QBrush(theme_manager.qcolor(colors.SUSPENDED_BG))
                painter.save()
                painter.fillRect(options.rect, brush)
                painter.restore()
        return super().drawRow(painter, options, idx)

    def dropEvent(self, event: QDropEvent) -> None:
        model = self.model()
        source_items = [model.item_for_index(idx) for idx in self.selectedIndexes()]
        target_item = model.item_for_index(self.indexAt(event.pos()))
        if self.handle_drag_drop(source_items, target_item):
            event.acceptProposedAction()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)
        if event.button() == Qt.LeftButton:
            idx = self.indexAt(event.pos())
            if idx == self.currentIndex():
                self._on_click_index(idx)

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() in (Qt.Key_Return, Qt.Key_Enter):
            idx = self.currentIndex()
            self._on_click_index(idx)
        else:
            super().keyPressEvent(event)

    ###########

    def handle_drag_drop(self, sources: List[SidebarItem], target: SidebarItem) -> bool:
        if target.item_type in (SidebarItemType.DECK, SidebarItemType.DECK_ROOT):
            return self._handle_drag_drop_decks(sources, target)
        if target.item_type in (SidebarItemType.TAG, SidebarItemType.TAG_ROOT):
            return self._handle_drag_drop_tags(sources, target)
        return False

    def _handle_drag_drop_decks(
        self, sources: List[SidebarItem], target: SidebarItem
    ) -> bool:
        source_ids = [
            source.id for source in sources if source.item_type == SidebarItemType.DECK
        ]
        if not source_ids:
            return False

        def on_done(fut: Future) -> None:
            self.browser.model.endReset()
            try:
                fut.result()
            except Exception as e:
                showWarning(str(e))
                return
            self.refresh()
            self.mw.deckBrowser.refresh()

        def on_save() -> None:
            self.mw.checkpoint(tr(TR.ACTIONS_RENAME_DECK))
            self.browser.model.beginReset()
            self.mw.taskman.with_progress(
                lambda: self.col.decks.drag_drop_decks(source_ids, target.id), on_done
            )

        self.browser.editor.saveNow(on_save)
        return True

    def _handle_drag_drop_tags(
        self, sources: List[SidebarItem], target: SidebarItem
    ) -> bool:
        source_ids = [
            source.full_name
            for source in sources
            if source.item_type == SidebarItemType.TAG
        ]
        if not source_ids:
            return False

        def on_done(fut: Future) -> None:
            self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)
            self.browser.model.endReset()
            fut.result()
            self.refresh()

        if target.item_type == SidebarItemType.TAG_ROOT:
            target_name = ""
        else:
            target_name = target.full_name

        def on_save() -> None:
            self.mw.checkpoint(tr(TR.ACTIONS_RENAME_TAG))
            self.browser.model.beginReset()
            self.mw.taskman.with_progress(
                lambda: self.col.tags.drag_drop(source_ids, target_name), on_done
            )

        self.browser.editor.saveNow(on_save)
        return True

    def _on_click_index(self, idx: QModelIndex) -> None:
        if item := self.model().item_for_index(idx):
            if item.on_click:
                item.on_click()

    def _on_expansion(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        self._on_expand_or_collapse(idx, True)

    def _on_collapse(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        self._on_expand_or_collapse(idx, False)

    def _on_expand_or_collapse(self, idx: QModelIndex, expanded: bool) -> None:
        item = self.model().item_for_index(idx)
        if item and item._is_expanded != expanded:
            item._is_expanded = expanded
            if item.on_expanded:
                item.on_expanded(expanded)

    # Tree building
    ###########################

    def _root_tree(self) -> SidebarItem:
        root: Optional[SidebarItem] = None

        for stage in SidebarStage:
            if stage == SidebarStage.ROOT:
                root = SidebarItem("", "", item_type=SidebarItemType.ROOT)
            handled = gui_hooks.browser_will_build_tree(False, root, stage, self)
            if not handled:
                self._build_stage(root, stage)

        return root

    def _build_stage(self, root: SidebarItem, stage: SidebarStage) -> None:
        if stage is SidebarStage.SAVED_SEARCHES:
            self._saved_searches_tree(root)
        elif stage is SidebarStage.CARD_STATE:
            self._card_state_tree(root)
        elif stage is SidebarStage.TODAY:
            self._today_tree(root)
        elif stage is SidebarStage.FLAGS:
            self._flags_tree(root)
        elif stage is SidebarStage.DECKS:
            self._deck_tree(root)
        elif stage is SidebarStage.NOTETYPES:
            self._notetype_tree(root)
        elif stage is SidebarStage.TAGS:
            self._tag_tree(root)
        elif stage is SidebarStage.ROOT:
            pass
        else:
            assert_exhaustive(stage)

    def _section_root(
        self,
        *,
        root: SidebarItem,
        name: TR.V,
        icon: Union[str, ColoredIcon],
        collapse_key: Config.Bool.Key.V,
        type: Optional[SidebarItemType] = None,
    ) -> SidebarItem:
        def update(expanded: bool) -> None:
            self.col.set_config_bool(collapse_key, not expanded)

        top = SidebarItem(
            tr(name),
            icon,
            on_expanded=update,
            expanded=not self.col.get_config_bool(collapse_key),
            item_type=type,
        )
        root.add_child(top)

        return top

    def _filter_func(self, *terms: Union[str, SearchNode]) -> Callable:
        return lambda: self.update_search(*terms)

    # Tree: Saved Searches
    ###########################

    def _saved_searches_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/heart.svg"
        saved = self._get_saved_searches()

        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_SAVED_SEARCHES,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_SAVED_SEARCHES,
            type=SidebarItemType.SAVED_SEARCH_ROOT,
        )

        for name, filt in sorted(saved.items()):
            item = SidebarItem(
                name,
                icon,
                self._filter_func(filt),
                item_type=SidebarItemType.SAVED_SEARCH,
            )
            root.add_child(item)

    # Tree: Today
    ###########################

    def _today_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/clock.svg"
        root = self._section_root(
            root=root,
            name=TR.BROWSING_TODAY,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_TODAY,
            type=SidebarItemType.TODAY_ROOT,
        )
        type = SidebarItemType.TODAY
        search = self._filter_func

        root.add_simple(
            name=TR.BROWSING_SIDEBAR_DUE_TODAY,
            icon=icon,
            type=type,
            on_click=search(SearchNode(due_on_day=0)),
        )
        root.add_simple(
            name=TR.BROWSING_ADDED_TODAY,
            icon=icon,
            type=type,
            on_click=search(SearchNode(added_in_days=1)),
        )
        root.add_simple(
            name=TR.BROWSING_EDITED_TODAY,
            icon=icon,
            type=type,
            on_click=search(SearchNode(edited_in_days=1)),
        )
        root.add_simple(
            name=TR.BROWSING_STUDIED_TODAY,
            icon=icon,
            type=type,
            on_click=search(SearchNode(rated=SearchNode.Rated(days=1))),
        )
        root.add_simple(
            name=TR.BROWSING_AGAIN_TODAY,
            icon=icon,
            type=type,
            on_click=search(
                SearchNode(
                    rated=SearchNode.Rated(days=1, rating=SearchNode.RATING_AGAIN)
                )
            ),
        )
        root.add_simple(
            name=TR.BROWSING_SIDEBAR_OVERDUE,
            icon=icon,
            type=type,
            on_click=search(
                SearchNode(card_state=SearchNode.CARD_STATE_DUE),
                SearchNode(negated=SearchNode(due_on_day=0)),
            ),
        )

    # Tree: Card State
    ###########################

    def _card_state_tree(self, root: SidebarItem) -> None:
        icon = ColoredIcon(path=":/icons/card-state.svg", color=colors.DISABLED)
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_CARD_STATE,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_CARD_STATE,
            type=SidebarItemType.CARD_STATE_ROOT,
        )
        type = SidebarItemType.CARD_STATE
        search = self._filter_func

        root.add_simple(
            TR.ACTIONS_NEW,
            icon=icon.with_color(colors.NEW_COUNT),
            type=type,
            on_click=search(SearchNode(card_state=SearchNode.CARD_STATE_NEW)),
        )

        root.add_simple(
            name=TR.SCHEDULING_LEARNING,
            icon=icon.with_color(colors.LEARN_COUNT),
            type=type,
            on_click=search(SearchNode(card_state=SearchNode.CARD_STATE_LEARN)),
        )
        root.add_simple(
            name=TR.SCHEDULING_REVIEW,
            icon=icon.with_color(colors.REVIEW_COUNT),
            type=type,
            on_click=search(SearchNode(card_state=SearchNode.CARD_STATE_REVIEW)),
        )
        root.add_simple(
            name=TR.BROWSING_SUSPENDED,
            icon=icon.with_color(colors.SUSPENDED_FG),
            type=type,
            on_click=search(SearchNode(card_state=SearchNode.CARD_STATE_SUSPENDED)),
        )
        root.add_simple(
            name=TR.BROWSING_BURIED,
            icon=icon.with_color(colors.BURIED_FG),
            type=type,
            on_click=search(SearchNode(card_state=SearchNode.CARD_STATE_BURIED)),
        )

    # Tree: Flags
    ###########################

    def _flags_tree(self, root: SidebarItem) -> None:
        icon = ColoredIcon(path=":/icons/flag.svg", color=colors.DISABLED)
        search = self._filter_func
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_FLAGS,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_FLAGS,
            type=SidebarItemType.FLAG_ROOT,
        )
        root.on_click = search(SearchNode(flag=SearchNode.FLAG_ANY))

        type = SidebarItemType.FLAG
        root.add_simple(
            TR.ACTIONS_RED_FLAG,
            icon=icon.with_color(colors.FLAG1_FG),
            type=type,
            on_click=search(SearchNode(flag=SearchNode.FLAG_RED)),
        )
        root.add_simple(
            TR.ACTIONS_ORANGE_FLAG,
            icon=icon.with_color(colors.FLAG2_FG),
            type=type,
            on_click=search(SearchNode(flag=SearchNode.FLAG_ORANGE)),
        )
        root.add_simple(
            TR.ACTIONS_GREEN_FLAG,
            icon=icon.with_color(colors.FLAG3_FG),
            type=type,
            on_click=search(SearchNode(flag=SearchNode.FLAG_GREEN)),
        )
        root.add_simple(
            TR.ACTIONS_BLUE_FLAG,
            icon=icon.with_color(colors.FLAG4_FG),
            type=type,
            on_click=search(SearchNode(flag=SearchNode.FLAG_BLUE)),
        )
        root.add_simple(
            TR.BROWSING_NO_FLAG,
            icon=icon.with_color(colors.DISABLED),
            type=type,
            on_click=search(SearchNode(flag=SearchNode.FLAG_NONE)),
        )

    # Tree: Tags
    ###########################

    def _tag_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/tag.svg"

        def render(
            root: SidebarItem, nodes: Iterable[TagTreeNode], head: str = ""
        ) -> None:
            for node in nodes:

                def toggle_expand() -> Callable[[bool], None]:
                    full_name = head + node.name  # pylint: disable=cell-var-from-loop
                    return lambda expanded: self.mw.col.tags.set_expanded(
                        full_name, expanded
                    )

                item = SidebarItem(
                    node.name,
                    icon,
                    self._filter_func(SearchNode(tag=head + node.name)),
                    toggle_expand(),
                    node.expanded,
                    item_type=SidebarItemType.TAG,
                    full_name=head + node.name,
                )
                root.add_child(item)
                newhead = f"{head + node.name}::"
                render(item, node.children, newhead)

        tree = self.col.tags.tree()
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_TAGS,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_TAGS,
            type=SidebarItemType.TAG_ROOT,
        )
        root.on_click = self._filter_func(SearchNode(negated=SearchNode(tag="none")))
        root.add_simple(
            name=tr(TR.BROWSING_SIDEBAR_UNTAGGED),
            icon=icon,
            type=SidebarItemType.TAG_NONE,
            on_click=self._filter_func(SearchNode(tag="none")),
        )

        render(root, tree.children)

    # Tree: Decks
    ###########################

    def _deck_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/deck.svg"

        def render(
            root: SidebarItem, nodes: Iterable[DeckTreeNode], head: str = ""
        ) -> None:
            for node in nodes:

                def toggle_expand() -> Callable[[bool], None]:
                    did = node.deck_id  # pylint: disable=cell-var-from-loop
                    return lambda _: self.mw.col.decks.collapseBrowser(did)

                item = SidebarItem(
                    node.name,
                    icon,
                    self._filter_func(SearchNode(deck=head + node.name)),
                    toggle_expand(),
                    not node.collapsed,
                    item_type=SidebarItemType.DECK,
                    id=node.deck_id,
                    full_name=head + node.name,
                )
                root.add_child(item)
                newhead = f"{head + node.name}::"
                render(item, node.children, newhead)

        tree = self.col.decks.deck_tree()
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_DECKS,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_DECKS,
            type=SidebarItemType.DECK_ROOT,
        )
        root.on_click = self._filter_func(SearchNode(deck="*"))
        current = root.add_simple(
            name=tr(TR.BROWSING_CURRENT_DECK),
            icon=icon,
            type=SidebarItemType.DECK,
            on_click=self._filter_func(SearchNode(deck="current")),
        )
        current.id = self.mw.col.decks.selected()

        render(root, tree.children)

    # Tree: Notetypes
    ###########################

    def _notetype_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/notetype.svg"
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_NOTETYPES,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_NOTETYPES,
            type=SidebarItemType.NOTETYPE_ROOT,
        )

        for nt in sorted(self.col.models.all(), key=lambda nt: nt["name"].lower()):
            item = SidebarItem(
                nt["name"],
                icon,
                self._filter_func(SearchNode(note=nt["name"])),
                item_type=SidebarItemType.NOTETYPE,
                id=nt["id"],
            )

            for c, tmpl in enumerate(nt["tmpls"]):
                child = SidebarItem(
                    tmpl["name"],
                    icon,
                    self._filter_func(
                        SearchNode(note=nt["name"]), SearchNode(template=c)
                    ),
                    item_type=SidebarItemType.NOTETYPE_TEMPLATE,
                    full_name=f"{nt['name']}::{tmpl['name']}",
                )
                item.add_child(child)

            root.add_child(item)

    # Context menu actions
    ###########################

    def onContextMenu(self, point: QPoint) -> None:
        idx: QModelIndex = self.indexAt(point)
        item = self.model().item_for_index(idx)
        if not item:
            return
        self.show_context_menu(item, idx)

    # idx is only None when triggering the context menu from a left click on
    # saved searches - perhaps there is a better way to handle that?
    def show_context_menu(self, item: SidebarItem, idx: Optional[QModelIndex]) -> None:
        m = QMenu()

        if item.item_type in self.context_menus:
            for action in self.context_menus[item.item_type]:
                act_name = action[0]
                act_func = action[1]
                a = m.addAction(act_name)
                qconnect(a.triggered, lambda _, func=act_func: func(item))

        if idx:
            self.maybe_add_tree_actions(m, item, idx)

        if not m.children():
            return

        # until we support multiple selection, show user that only the current
        # item is being operated on by clearing the selection
        if idx:
            sm = self.selectionModel()
            sm.clear()
            sm.select(
                idx,
                cast(
                    QItemSelectionModel.SelectionFlag,
                    QItemSelectionModel.SelectCurrent | QItemSelectionModel.Rows,
                ),
            )

        m.exec_(QCursor.pos())

    def maybe_add_tree_actions(
        self, menu: QMenu, item: SidebarItem, parent: QModelIndex
    ) -> None:
        if self.current_search:
            return
        if not any(bool(c.children) for c in item.children):
            return

        def set_children_collapsed(collapsed: bool) -> None:
            m = self.model()
            self.setExpanded(parent, True)
            for row in range(m.rowCount(parent)):
                idx = m.index(row, 0, parent)
                self.setExpanded(idx, not collapsed)

        menu.addSeparator()
        menu.addAction(
            tr(TR.BROWSING_SIDEBAR_EXPAND_CHILDREN),
            lambda: set_children_collapsed(False),
        )
        menu.addAction(
            tr(TR.BROWSING_SIDEBAR_COLLAPSE_CHILDREN),
            lambda: set_children_collapsed(True),
        )

    def rename_deck(self, item: SidebarItem) -> None:
        deck = self.mw.col.decks.get(item.id)
        old_name = deck["name"]
        new_name = getOnlyText(tr(TR.DECKS_NEW_DECK_NAME), default=old_name)
        new_name = new_name.replace('"', "")
        if not new_name or new_name == old_name:
            return
        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_DECK))
        try:
            self.mw.col.decks.rename(deck, new_name)
        except DeckIsFilteredError as err:
            showWarning(str(err))
            return
        self.refresh()
        self.mw.deckBrowser.refresh()

    def remove_tag(self, item: SidebarItem) -> None:
        self.browser.editor.saveNow(lambda: self._remove_tag(item))

    def _remove_tag(self, item: SidebarItem) -> None:
        old_name = item.full_name

        def do_remove() -> None:
            self.mw.col.tags.remove(old_name)
            self.col.tags.rename(old_name, "")

        def on_done(fut: Future) -> None:
            self.mw.requireReset(reason=ResetReason.BrowserRemoveTags, context=self)
            self.browser.model.endReset()
            fut.result()
            self.refresh()

        self.mw.checkpoint(tr(TR.ACTIONS_REMOVE_TAG))
        self.browser.model.beginReset()
        self.mw.taskman.run_in_background(do_remove, on_done)

    def rename_tag(self, item: SidebarItem) -> None:
        self.browser.editor.saveNow(lambda: self._rename_tag(item))

    def _rename_tag(self, item: SidebarItem) -> None:
        old_name = item.full_name
        new_name = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=old_name)
        if new_name == old_name or not new_name:
            return

        def do_rename() -> int:
            self.mw.col.tags.remove(old_name)
            return self.col.tags.rename(old_name, new_name)

        def on_done(fut: Future) -> None:
            self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)
            self.browser.model.endReset()

            count = fut.result()
            if not count:
                showInfo(tr(TR.BROWSING_TAG_RENAME_WARNING_EMPTY))
                return

            self.refresh()

        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_TAG))
        self.browser.model.beginReset()
        self.mw.taskman.run_in_background(do_rename, on_done)

    def delete_deck(self, item: SidebarItem) -> None:
        self.browser.editor.saveNow(lambda: self._delete_deck(item))

    def _delete_deck(self, item: SidebarItem) -> None:
        did = item.id
        if self.mw.deckBrowser.ask_delete_deck(did):

            def do_delete() -> None:
                return self.mw.col.decks.rem(did, True)

            def on_done(fut: Future) -> None:
                self.mw.requireReset(reason=ResetReason.BrowserDeleteDeck, context=self)
                self.browser.search()
                self.browser.model.endReset()
                self.refresh()
                res = fut.result()  # Required to check for errors

            self.mw.checkpoint(tr(TR.DECKS_DELETE_DECK))
            self.browser.model.beginReset()
            self.mw.taskman.run_in_background(do_delete, on_done)

    # Saved searches
    ##################

    _saved_searches_key = "savedFilters"

    def _get_saved_searches(self) -> Dict[str, str]:
        return self.col.get_config(self._saved_searches_key, {})

    def _set_saved_searches(self, searches: Dict[str, str]) -> None:
        self.col.set_config(self._saved_searches_key, searches)

    def remove_saved_search(self, item: SidebarItem) -> None:
        name = item.name
        if not askUser(tr(TR.BROWSING_REMOVE_FROM_YOUR_SAVED_SEARCHES, val=name)):
            return
        conf = self._get_saved_searches()
        del conf[name]
        self._set_saved_searches(conf)
        self.refresh()

    def rename_saved_search(self, item: SidebarItem) -> None:
        old = item.name
        conf = self._get_saved_searches()
        try:
            filt = conf[old]
        except KeyError:
            return
        new = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=old)
        if new == old or not new:
            return
        conf[new] = filt
        del conf[old]
        self._set_saved_searches(conf)
        self.refresh()

    def save_current_search(self, _item: Any = None) -> None:
        try:
            filt = self.col.build_search_string(
                self.browser.form.searchEdit.lineEdit().text()
            )
        except InvalidInput as e:
            show_invalid_search_error(e)
        else:
            name = getOnlyText(tr(TR.BROWSING_PLEASE_GIVE_YOUR_FILTER_A_NAME))
            if not name:
                return
            conf = self._get_saved_searches()
            conf[name] = filt
            self._set_saved_searches(conf)
            self.refresh()

    def manage_notetype(self, item: SidebarItem) -> None:
        Models(
            self.mw, parent=self.browser, fromMain=True, selected_notetype_id=item.id
        )
