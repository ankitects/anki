# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from enum import Enum, auto
from typing import Dict, Iterable, List, Optional, Tuple, cast

import aqt
from anki.collection import Config, OpChanges, SearchJoiner, SearchNode
from anki.decks import DeckTreeNode
from anki.errors import DeckIsFilteredError, InvalidInput
from anki.notes import Note
from anki.tags import TagTreeNode
from anki.types import assert_exhaustive
from aqt import colors, gui_hooks
from aqt.clayout import CardLayout
from aqt.deck_ops import remove_decks
from aqt.main import ResetReason
from aqt.models import Models
from aqt.qt import *
from aqt.theme import ColoredIcon, theme_manager
from aqt.utils import (
    TR,
    KeyboardModifiersPressed,
    askUser,
    getOnlyText,
    show_invalid_search_error,
    showInfo,
    showWarning,
    tooltip,
    tr,
)


class SidebarTool(Enum):
    SELECT = auto()
    SEARCH = auto()


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
        name: Union[str, TR.V],
        icon: Union[str, ColoredIcon],
        type: SidebarItemType,
        search_node: Optional[SearchNode],
    ) -> SidebarItem:
        "Add child sidebar item, and return it."
        if not isinstance(name, str):
            name = tr(name)
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


class SidebarModel(QAbstractItemModel):
    def __init__(self, sidebar: SidebarTreeView, root: SidebarItem) -> None:
        super().__init__()
        self.sidebar = sidebar
        self.root = root
        self._cache_rows(root)

    def _cache_rows(self, node: SidebarItem) -> None:
        "Cache index of children in parent."
        for row, item in enumerate(node.children):
            item._row_in_parent = row
            self._cache_rows(item)

    def item_for_index(self, idx: QModelIndex) -> SidebarItem:
        return idx.internalPointer()

    def index_for_item(self, item: SidebarItem) -> QModelIndex:
        return self.createIndex(item._row_in_parent, 0, item)

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

        if role not in (Qt.DisplayRole, Qt.DecorationRole, Qt.ToolTipRole, Qt.EditRole):
            return QVariant()

        item: SidebarItem = index.internalPointer()

        if role in (Qt.DisplayRole, Qt.EditRole):
            return QVariant(item.name)
        if role == Qt.ToolTipRole:
            return QVariant(item.tooltip)
        return QVariant(theme_manager.icon_from_resources(item.icon))

    def setData(self, index: QModelIndex, text: str, _role: int = Qt.EditRole) -> bool:
        return self.sidebar._on_rename(index.internalPointer(), text)

    def supportedDropActions(self) -> Qt.DropActions:
        return cast(Qt.DropActions, Qt.MoveAction)

    def flags(self, index: QModelIndex) -> Qt.ItemFlags:
        if not index.isValid():
            return cast(Qt.ItemFlags, Qt.ItemIsEnabled)
        flags = Qt.ItemIsEnabled | Qt.ItemIsSelectable | Qt.ItemIsDragEnabled
        item: SidebarItem = index.internalPointer()
        if item.item_type in self.sidebar.valid_drop_types:
            flags |= Qt.ItemIsDropEnabled
        if item.item_type.is_editable():
            flags |= Qt.ItemIsEditable

        return cast(Qt.ItemFlags, flags)


class SidebarToolbar(QToolBar):
    _tools: Tuple[Tuple[SidebarTool, str, TR.V], ...] = (
        (SidebarTool.SEARCH, ":/icons/magnifying_glass.svg", TR.ACTIONS_SEARCH),
        (SidebarTool.SELECT, ":/icons/select.svg", TR.ACTIONS_SELECT),
    )

    def __init__(self, sidebar: SidebarTreeView) -> None:
        super().__init__()
        self.sidebar = sidebar
        self._action_group = QActionGroup(self)
        qconnect(self._action_group.triggered, self._on_action_group_triggered)
        self._setup_tools()
        self.setIconSize(QSize(16, 16))
        self.setStyle(QStyleFactory.create("fusion"))

    def _setup_tools(self) -> None:
        for row, tool in enumerate(self._tools):
            action = self.addAction(
                theme_manager.icon_from_resources(tool[1]), tr(tool[2])
            )
            action.setCheckable(True)
            action.setShortcut(f"Alt+{row + 1}")
            self._action_group.addAction(action)
        saved = self.sidebar.col.get_config("sidebarTool", 0)
        active = saved if saved < len(self._tools) else 0
        self._action_group.actions()[active].setChecked(True)
        self.sidebar.tool = self._tools[active][0]

    def _on_action_group_triggered(self, action: QAction) -> None:
        index = self._action_group.actions().index(action)
        self.sidebar.col.set_config("sidebarTool", index)
        self.sidebar.tool = self._tools[index][0]


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


# fixme: we should have a top-level Sidebar class inheriting from QWidget that
# handles the treeview, search bar and so on. Currently the treeview embeds the
# search bar which is wrong, and the layout code is handled in browser.py instead
# of here
class SidebarTreeView(QTreeView):
    def __init__(self, browser: aqt.browser.Browser) -> None:
        super().__init__()
        self.browser = browser
        self.mw = browser.mw
        self.col = self.mw.col
        self.current_search: Optional[str] = None
        self.valid_drop_types: Tuple[SidebarItemType, ...] = ()
        self._refresh_needed = False

        self.setContextMenuPolicy(Qt.CustomContextMenu)
        self.customContextMenuRequested.connect(self.onContextMenu)  # type: ignore
        self.setUniformRowHeights(True)
        self.setHeaderHidden(True)
        self.setIndentation(15)
        self.setAutoExpandDelay(600)
        self.setDragDropOverwriteMode(False)
        self.setEditTriggers(QAbstractItemView.EditKeyPressed)

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

        # these do not really belong here, they should be in a higher-level class
        self.toolbar = SidebarToolbar(self)
        self.searchBar = SidebarSearchBar(self)

    @property
    def tool(self) -> SidebarTool:
        return self._tool

    @tool.setter
    def tool(self, tool: SidebarTool) -> None:
        self._tool = tool
        if tool == SidebarTool.SEARCH:
            selection_mode = QAbstractItemView.SingleSelection
            drag_drop_mode = QAbstractItemView.NoDragDrop
            double_click_expands = False
        else:
            selection_mode = QAbstractItemView.ExtendedSelection
            drag_drop_mode = QAbstractItemView.InternalMove
            double_click_expands = True
        self.setSelectionMode(selection_mode)
        self.setDragDropMode(drag_drop_mode)
        self.setExpandsOnDoubleClick(double_click_expands)

    def model(self) -> SidebarModel:
        return cast(SidebarModel, super().model())

    # Refreshing
    ###########################

    def op_executed(self, op: OpChanges, focused: bool) -> None:
        if op.tag or op.notetype or op.deck:
            self._refresh_needed = True
        if focused:
            self.refresh_if_needed()

    def refresh_if_needed(self) -> None:
        if self._refresh_needed:
            self.refresh()
            self._refresh_needed = False

    def refresh(
        self, is_current: Optional[Callable[[SidebarItem], bool]] = None
    ) -> None:
        "Refresh list. No-op if sidebar is not visible."
        if not self.isVisible():
            return

        def on_done(fut: Future) -> None:
            self.setUpdatesEnabled(True)
            root = fut.result()
            model = SidebarModel(self, root)

            # from PyQt5.QtTest import QAbstractItemModelTester
            # tester = QAbstractItemModelTester(model)

            self.setModel(model)
            qconnect(self.selectionModel().selectionChanged, self._on_selection_changed)
            if self.current_search:
                self.search_for(self.current_search)
            else:
                self._expand_where_necessary(model)
            if is_current:
                self.restore_current(is_current)

        # block repainting during refreshing to avoid flickering
        self.setUpdatesEnabled(False)
        self.mw.taskman.run_in_background(self._root_tree, on_done)

    def restore_current(self, is_current: Callable[[SidebarItem], bool]) -> None:
        if current := self.find_item(is_current):
            index = self.model().index_for_item(current)
            self.selectionModel().setCurrentIndex(
                index, QItemSelectionModel.SelectCurrent
            )
            self.scrollTo(index)

    def find_item(
        self,
        is_target: Callable[[SidebarItem], bool],
        parent: Optional[SidebarItem] = None,
    ) -> Optional[SidebarItem]:
        def find_item_rec(parent: SidebarItem) -> Optional[SidebarItem]:
            if is_target(parent):
                return parent
            for child in parent.children:
                if item := find_item_rec(child):
                    return item
            return None

        return find_item_rec(parent or self.model().root)

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
                if item.show_expanded(searching):
                    self.setExpanded(idx, True)

    def update_search(
        self,
        *terms: Union[str, SearchNode],
        joiner: SearchJoiner = "AND",
    ) -> None:
        """Modify the current search string based on modifier keys, then refresh."""
        mods = KeyboardModifiersPressed()
        previous = SearchNode(parsable_text=self.browser.current_search())
        current = self.mw.col.group_searches(*terms, joiner=joiner)

        # if Alt pressed, invert
        if mods.alt:
            current = SearchNode(negated=current)

        try:
            if mods.control and mods.shift:
                # If Ctrl+Shift, replace searches nodes of the same type.
                search = self.col.replace_in_search_node(previous, current)
            elif mods.control:
                # If Ctrl, AND with previous
                search = self.col.join_searches(previous, current, "AND")
            elif mods.shift:
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
        target_item = model.item_for_index(self.indexAt(event.pos()))
        if self.handle_drag_drop(self._selected_items(), target_item):
            event.acceptProposedAction()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)
        if self.tool == SidebarTool.SEARCH and event.button() == Qt.LeftButton:
            if (index := self.currentIndex()) == self.indexAt(event.pos()):
                self._on_search(index)

    def keyPressEvent(self, event: QKeyEvent) -> None:
        index = self.currentIndex()
        if event.key() in (Qt.Key_Return, Qt.Key_Enter):
            if not self.isPersistentEditorOpen(index):
                self._on_search(index)
        elif event.key() == Qt.Key_Delete:
            self._on_delete_key(index)
        else:
            super().keyPressEvent(event)

    # Slots
    ###########

    def _on_selection_changed(self, _new: QItemSelection, _old: QItemSelection) -> None:
        selected_types = [item.item_type for item in self._selected_items()]
        if all(item_type == SidebarItemType.DECK for item_type in selected_types):
            self.valid_drop_types = (SidebarItemType.DECK, SidebarItemType.DECK_ROOT)
        elif all(item_type == SidebarItemType.TAG for item_type in selected_types):
            self.valid_drop_types = (SidebarItemType.TAG, SidebarItemType.TAG_ROOT)
        else:
            self.valid_drop_types = ()

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
            self.mw.update_undo_actions()

        def on_save() -> None:
            self.browser.model.beginReset()
            self.mw.taskman.with_progress(
                lambda: self.col.decks.drag_drop_decks(source_ids, target.id), on_done
            )

        self.browser.editor.call_after_note_saved(on_save)
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

        self.browser.editor.call_after_note_saved(on_save)
        return True

    def _on_search(self, index: QModelIndex) -> None:
        if item := self.model().item_for_index(index):
            if search_node := item.search_node:
                self.update_search(search_node)

    def _on_rename(self, item: SidebarItem, text: str) -> bool:
        new_name = text.replace('"', "")
        if new_name and new_name != item.name:
            if item.item_type == SidebarItemType.DECK:
                self.rename_deck(item, new_name)
            elif item.item_type == SidebarItemType.SAVED_SEARCH:
                self.rename_saved_search(item, new_name)
            elif item.item_type == SidebarItemType.TAG:
                self.rename_tag(item, new_name)
        # renaming may be asynchronous so always return False
        return False

    def _on_delete_key(self, index: QModelIndex) -> None:
        if item := self.model().item_for_index(index):
            if self._enable_delete(item):
                self._on_delete(item)

    def _enable_delete(self, item: SidebarItem) -> bool:
        return item.item_type.is_deletable() and all(
            s.item_type == item.item_type for s in self._selected_items()
        )

    def _on_delete(self, item: SidebarItem) -> None:
        if item.item_type == SidebarItemType.SAVED_SEARCH:
            self.remove_saved_searches(item)
        elif item.item_type == SidebarItemType.DECK:
            self.delete_decks(item)
        elif item.item_type == SidebarItemType.TAG:
            self.remove_tags(item)

    def _on_expansion(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        if item := self.model().item_for_index(idx):
            item.expanded = True

    def _on_collapse(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        if item := self.model().item_for_index(idx):
            item.expanded = False

    # Tree building
    ###########################

    def _root_tree(self) -> SidebarItem:
        root: Optional[SidebarItem] = None

        for stage in SidebarStage:
            if stage == SidebarStage.ROOT:
                root = SidebarItem("", "", item_type=SidebarItemType.ROOT)
            handled = gui_hooks.browser_will_build_tree(
                False, root, stage, self.browser
            )
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
                search_node=SearchNode(parsable_text=filt),
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

        root.add_simple(
            name=TR.BROWSING_SIDEBAR_DUE_TODAY,
            icon=icon,
            type=type,
            search_node=SearchNode(due_on_day=0),
        )
        root.add_simple(
            name=TR.BROWSING_ADDED_TODAY,
            icon=icon,
            type=type,
            search_node=SearchNode(added_in_days=1),
        )
        root.add_simple(
            name=TR.BROWSING_EDITED_TODAY,
            icon=icon,
            type=type,
            search_node=SearchNode(edited_in_days=1),
        )
        root.add_simple(
            name=TR.BROWSING_STUDIED_TODAY,
            icon=icon,
            type=type,
            search_node=SearchNode(rated=SearchNode.Rated(days=1)),
        )
        root.add_simple(
            name=TR.BROWSING_AGAIN_TODAY,
            icon=icon,
            type=type,
            search_node=SearchNode(
                rated=SearchNode.Rated(days=1, rating=SearchNode.RATING_AGAIN)
            ),
        )
        root.add_simple(
            name=TR.BROWSING_SIDEBAR_OVERDUE,
            icon=icon,
            type=type,
            search_node=self.col.group_searches(
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

        root.add_simple(
            TR.ACTIONS_NEW,
            icon=icon.with_color(colors.NEW_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_NEW),
        )

        root.add_simple(
            name=TR.SCHEDULING_LEARNING,
            icon=icon.with_color(colors.LEARN_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_LEARN),
        )
        root.add_simple(
            name=TR.SCHEDULING_REVIEW,
            icon=icon.with_color(colors.REVIEW_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_REVIEW),
        )
        root.add_simple(
            name=TR.BROWSING_SUSPENDED,
            icon=icon.with_color(colors.SUSPENDED_FG),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_SUSPENDED),
        )
        root.add_simple(
            name=TR.BROWSING_BURIED,
            icon=icon.with_color(colors.BURIED_FG),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_BURIED),
        )

    # Tree: Flags
    ###########################

    def _flags_tree(self, root: SidebarItem) -> None:
        icon = ColoredIcon(path=":/icons/flag.svg", color=colors.DISABLED)
        root = self._section_root(
            root=root,
            name=TR.BROWSING_SIDEBAR_FLAGS,
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_FLAGS,
            type=SidebarItemType.FLAG_ROOT,
        )
        root.search_node = SearchNode(flag=SearchNode.FLAG_ANY)

        type = SidebarItemType.FLAG
        root.add_simple(
            TR.ACTIONS_RED_FLAG,
            icon=icon.with_color(colors.FLAG1_FG),
            type=type,
            search_node=SearchNode(flag=SearchNode.FLAG_RED),
        )
        root.add_simple(
            TR.ACTIONS_ORANGE_FLAG,
            icon=icon.with_color(colors.FLAG2_FG),
            type=type,
            search_node=SearchNode(flag=SearchNode.FLAG_ORANGE),
        )
        root.add_simple(
            TR.ACTIONS_GREEN_FLAG,
            icon=icon.with_color(colors.FLAG3_FG),
            type=type,
            search_node=SearchNode(flag=SearchNode.FLAG_GREEN),
        )
        root.add_simple(
            TR.ACTIONS_BLUE_FLAG,
            icon=icon.with_color(colors.FLAG4_FG),
            type=type,
            search_node=SearchNode(flag=SearchNode.FLAG_BLUE),
        )
        root.add_simple(
            TR.BROWSING_NO_FLAG,
            icon=icon.with_color(colors.DISABLED),
            type=type,
            search_node=SearchNode(flag=SearchNode.FLAG_NONE),
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
                    name=node.name,
                    icon=icon,
                    search_node=SearchNode(tag=head + node.name),
                    on_expanded=toggle_expand(),
                    expanded=node.expanded,
                    item_type=SidebarItemType.TAG,
                    name_prefix=head,
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
        root.search_node = SearchNode(negated=SearchNode(tag="none"))
        root.add_simple(
            name=tr(TR.BROWSING_SIDEBAR_UNTAGGED),
            icon=icon,
            type=SidebarItemType.TAG_NONE,
            search_node=SearchNode(tag="none"),
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
                    name=node.name,
                    icon=icon,
                    search_node=SearchNode(deck=head + node.name),
                    on_expanded=toggle_expand(),
                    expanded=not node.collapsed,
                    item_type=SidebarItemType.DECK,
                    id=node.deck_id,
                    name_prefix=head,
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
        root.search_node = SearchNode(deck="*")
        current = root.add_simple(
            name=tr(TR.BROWSING_CURRENT_DECK),
            icon=icon,
            type=SidebarItemType.DECK_CURRENT,
            search_node=SearchNode(deck="current"),
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
                search_node=SearchNode(note=nt["name"]),
                item_type=SidebarItemType.NOTETYPE,
                id=nt["id"],
            )

            for c, tmpl in enumerate(nt["tmpls"]):
                child = SidebarItem(
                    tmpl["name"],
                    icon,
                    search_node=self.col.group_searches(
                        SearchNode(note=nt["name"]), SearchNode(template=c)
                    ),
                    item_type=SidebarItemType.NOTETYPE_TEMPLATE,
                    name_prefix=f"{nt['name']}::",
                    id=tmpl["ord"],
                )
                item.add_child(child)

            root.add_child(item)

    # Context menu
    ###########################

    def onContextMenu(self, point: QPoint) -> None:
        index: QModelIndex = self.indexAt(point)
        item = self.model().item_for_index(index)
        if item and self.selectionModel().isSelected(index):
            self.show_context_menu(item, index)

    def show_context_menu(self, item: SidebarItem, index: QModelIndex) -> None:
        menu = QMenu()
        self._maybe_add_type_specific_actions(menu, item)
        self._maybe_add_delete_action(menu, item, index)
        self._maybe_add_rename_action(menu, item, index)
        self._maybe_add_search_actions(menu)
        self._maybe_add_tree_actions(menu)
        if menu.children():
            menu.exec_(QCursor.pos())

    def _maybe_add_type_specific_actions(self, menu: QMenu, item: SidebarItem) -> None:
        if item.item_type in (SidebarItemType.NOTETYPE, SidebarItemType.NOTETYPE_ROOT):
            menu.addAction(
                tr(TR.BROWSING_MANAGE_NOTE_TYPES), lambda: self.manage_notetype(item)
            )
        elif item.item_type == SidebarItemType.NOTETYPE_TEMPLATE:
            menu.addAction(tr(TR.NOTETYPES_CARDS), lambda: self.manage_template(item))
        elif item.item_type == SidebarItemType.SAVED_SEARCH_ROOT:
            menu.addAction(
                tr(TR.BROWSING_SIDEBAR_SAVE_CURRENT_SEARCH), self.save_current_search
            )

    def _maybe_add_delete_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if self._enable_delete(item):
            menu.addAction(tr(TR.ACTIONS_DELETE), lambda: self._on_delete(item))

    def _maybe_add_rename_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if item.item_type.is_editable() and len(self._selected_items()) == 1:
            menu.addAction(tr(TR.ACTIONS_RENAME), lambda: self.edit(index))

    def _maybe_add_search_actions(self, menu: QMenu) -> None:
        nodes = [
            item.search_node for item in self._selected_items() if item.search_node
        ]
        if not nodes:
            return
        menu.addSeparator()
        if len(nodes) == 1:
            menu.addAction(tr(TR.ACTIONS_SEARCH), lambda: self.update_search(*nodes))
            return
        sub_menu = menu.addMenu(tr(TR.ACTIONS_SEARCH))
        sub_menu.addAction(
            tr(TR.ACTIONS_ALL_SELECTED), lambda: self.update_search(*nodes)
        )
        sub_menu.addAction(
            tr(TR.ACTIONS_ANY_SELECTED),
            lambda: self.update_search(*nodes, joiner="OR"),
        )

    def _maybe_add_tree_actions(self, menu: QMenu) -> None:
        def set_expanded(expanded: bool) -> None:
            for index in self.selectedIndexes():
                self.setExpanded(index, expanded)

        def set_children_expanded(expanded: bool) -> None:
            for index in self.selectedIndexes():
                self.setExpanded(index, True)
                for row in range(self.model().rowCount(index)):
                    self.setExpanded(self.model().index(row, 0, index), expanded)

        if self.current_search:
            return

        selected_items = self._selected_items()
        if not any(item.children for item in selected_items):
            return

        menu.addSeparator()
        if any(not item.expanded for item in selected_items if item.children):
            menu.addAction(tr(TR.BROWSING_SIDEBAR_EXPAND), lambda: set_expanded(True))
        if any(item.expanded for item in selected_items if item.children):
            menu.addAction(
                tr(TR.BROWSING_SIDEBAR_COLLAPSE), lambda: set_expanded(False)
            )
        if any(
            not c.expanded for i in selected_items for c in i.children if c.children
        ):
            menu.addAction(
                tr(TR.BROWSING_SIDEBAR_EXPAND_CHILDREN),
                lambda: set_children_expanded(True),
            )
        if any(c.expanded for i in selected_items for c in i.children if c.children):
            menu.addAction(
                tr(TR.BROWSING_SIDEBAR_COLLAPSE_CHILDREN),
                lambda: set_children_expanded(False),
            )

    # Decks
    ###########################

    def rename_deck(self, item: SidebarItem, new_name: str) -> None:
        deck = self.mw.col.decks.get(item.id)
        new_name = item.name_prefix + new_name
        try:
            self.mw.col.decks.rename(deck, new_name)
        except DeckIsFilteredError as err:
            showWarning(str(err))
            return
        self.refresh(
            lambda other: other.item_type == SidebarItemType.DECK
            and other.id == item.id
        )
        self.mw.deckBrowser.refresh()
        self.mw.update_undo_actions()

    def delete_decks(self, _item: SidebarItem) -> None:
        remove_decks(mw=self.mw, parent=self.browser, deck_ids=self._selected_decks())

    # Tags
    ###########################

    def remove_tags(self, _item: SidebarItem) -> None:
        tags = self._selected_tags()

        def do_remove() -> int:
            return self.col._backend.expunge_tags(" ".join(tags))

        def on_done(fut: Future) -> None:
            self.mw.requireReset(reason=ResetReason.BrowserRemoveTags, context=self)
            self.browser.model.endReset()
            tooltip(tr(TR.BROWSING_NOTES_UPDATED, count=fut.result()), parent=self)
            self.refresh()

        self.mw.checkpoint(tr(TR.ACTIONS_REMOVE_TAG))
        self.browser.model.beginReset()
        self.mw.taskman.with_progress(do_remove, on_done)

    def rename_tag(self, item: SidebarItem, new_name: str) -> None:
        new_name = new_name.replace(" ", "")
        if new_name and new_name != item.name:
            # block repainting until collection is updated
            self.setUpdatesEnabled(False)
            self.browser.editor.call_after_note_saved(
                lambda: self._rename_tag(item, new_name)
            )

    def _rename_tag(self, item: SidebarItem, new_name: str) -> None:
        old_name = item.full_name
        new_name = item.name_prefix + new_name

        def do_rename() -> int:
            self.mw.col.tags.remove(old_name)
            return self.col.tags.rename(old_name, new_name).count

        def on_done(fut: Future) -> None:
            self.setUpdatesEnabled(True)
            self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)
            self.browser.model.endReset()

            count = fut.result()
            if not count:
                showInfo(tr(TR.BROWSING_TAG_RENAME_WARNING_EMPTY))
            else:
                tooltip(tr(TR.BROWSING_NOTES_UPDATED, count=count), parent=self)
                self.refresh(
                    lambda item: item.item_type == SidebarItemType.TAG
                    and item.full_name == new_name
                )

        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_TAG))
        self.browser.model.beginReset()
        self.mw.taskman.with_progress(do_rename, on_done)

    # Saved searches
    ####################################

    _saved_searches_key = "savedFilters"

    def _get_saved_searches(self) -> Dict[str, str]:
        return self.col.get_config(self._saved_searches_key, {})

    def _set_saved_searches(self, searches: Dict[str, str]) -> None:
        self.col.set_config(self._saved_searches_key, searches)

    def remove_saved_searches(self, _item: SidebarItem) -> None:
        selected = self._selected_saved_searches()
        conf = self._get_saved_searches()
        for name in selected:
            del conf[name]
        self._set_saved_searches(conf)
        self.refresh()

    def rename_saved_search(self, item: SidebarItem, new_name: str) -> None:
        old_name = item.name
        conf = self._get_saved_searches()
        try:
            filt = conf[old_name]
        except KeyError:
            return
        if new_name in conf and not askUser(
            tr(TR.BROWSING_CONFIRM_SAVED_SEARCH_OVERWRITE, name=new_name)
        ):
            return
        conf[new_name] = filt
        del conf[old_name]
        self._set_saved_searches(conf)
        self.refresh(
            lambda item: item.item_type == SidebarItemType.SAVED_SEARCH
            and item.name == new_name
        )

    def save_current_search(self) -> None:
        try:
            filt = self.col.build_search_string(
                self.browser.form.searchEdit.lineEdit().text()
            )
        except InvalidInput as e:
            show_invalid_search_error(e)
            return
        name = getOnlyText(tr(TR.BROWSING_PLEASE_GIVE_YOUR_FILTER_A_NAME))
        if not name:
            return
        conf = self._get_saved_searches()
        if name in conf and not askUser(
            tr(TR.BROWSING_CONFIRM_SAVED_SEARCH_OVERWRITE, name=name)
        ):
            return
        conf[name] = filt
        self._set_saved_searches(conf)
        self.refresh(
            lambda item: item.item_type == SidebarItemType.SAVED_SEARCH
            and item.name == name
        )

    # Notetypes and templates
    ####################################

    def manage_notetype(self, item: SidebarItem) -> None:
        Models(
            self.mw, parent=self.browser, fromMain=True, selected_notetype_id=item.id
        )

    def manage_template(self, item: SidebarItem) -> None:
        note = Note(self.col, self.col.models.get(item._parent_item.id))
        CardLayout(self.mw, note, ord=item.id, parent=self, fill_empty=True)

    # Helpers
    ####################################

    def _selected_items(self) -> List[SidebarItem]:
        return [self.model().item_for_index(idx) for idx in self.selectedIndexes()]

    def _selected_decks(self) -> List[int]:
        return [
            item.id
            for item in self._selected_items()
            if item.item_type == SidebarItemType.DECK
        ]

    def _selected_saved_searches(self) -> List[str]:
        return [
            item.name
            for item in self._selected_items()
            if item.item_type == SidebarItemType.SAVED_SEARCH
        ]

    def _selected_tags(self) -> List[str]:
        return [
            item.full_name
            for item in self._selected_items()
            if item.item_type == SidebarItemType.TAG
        ]
