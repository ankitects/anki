# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from enum import Enum, auto
from typing import Dict, Iterable, List, Optional, Tuple, cast

import aqt
from anki.collection import Config, OpChanges, SearchJoiner, SearchNode
from anki.decks import Deck, DeckCollapseScope, DeckId, DeckTreeNode
from anki.models import NotetypeId
from anki.notes import Note
from anki.tags import TagTreeNode
from anki.types import assert_exhaustive
from aqt import colors, gui_hooks
from aqt.browser.sidebar import _want_right_border
from aqt.browser.sidebar.item import SidebarItem, SidebarItemType
from aqt.browser.sidebar.model import SidebarModel
from aqt.browser.sidebar.searchbar import SidebarSearchBar
from aqt.browser.sidebar.toolbar import SidebarTool, SidebarToolbar
from aqt.clayout import CardLayout
from aqt.flags import load_flags
from aqt.models import Models
from aqt.operations import CollectionOp, QueryOp
from aqt.operations.deck import (
    remove_decks,
    rename_deck,
    reparent_decks,
    set_deck_collapsed,
)
from aqt.operations.tag import (
    remove_tags_from_all_notes,
    rename_tag,
    reparent_tags,
    set_tag_collapsed,
)
from aqt.qt import *
from aqt.theme import ColoredIcon, theme_manager
from aqt.utils import KeyboardModifiersPressed, askUser, getOnlyText, showWarning, tr


class SidebarStage(Enum):
    ROOT = auto()
    SAVED_SEARCHES = auto()
    TODAY = auto()
    FLAGS = auto()
    CARD_STATE = auto()
    DECKS = auto()
    NOTETYPES = auto()
    TAGS = auto()


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

    def op_executed(
        self, changes: OpChanges, handler: Optional[object], focused: bool
    ) -> None:
        if changes.browser_sidebar and not handler is self:
            self._refresh_needed = True
        if focused:
            self.refresh_if_needed()

    def refresh_if_needed(self) -> None:
        if self._refresh_needed:
            self.refresh()
            self._refresh_needed = False

    def refresh(self) -> None:
        "Refresh list. No-op if sidebar is not visible."
        if not self.isVisible():
            return

        if self.model() and (idx := self.currentIndex()):
            current_item = self.model().item_for_index(idx)
        else:
            current_item = None

        def on_done(root: SidebarItem) -> None:
            # user may have closed browser
            if sip.isdeleted(self):
                return

            # block repainting during refreshing to avoid flickering
            self.setUpdatesEnabled(False)

            model = SidebarModel(self, root)
            self.setModel(model)

            if self.current_search:
                self.search_for(self.current_search)
            else:
                self._expand_where_necessary(model)
            if current_item:
                self.restore_current(current_item)

            self.setUpdatesEnabled(True)

            # needs to be set after changing model
            qconnect(self.selectionModel().selectionChanged, self._on_selection_changed)

        QueryOp(
            parent=self.browser, op=lambda _: self._root_tree(), success=on_done
        ).run_in_background()

    def restore_current(self, current: SidebarItem) -> None:
        if current := self.find_item(current.has_same_id):
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
        except Exception as e:
            showWarning(str(e))
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
        deck_ids = [
            DeckId(source.id)
            for source in sources
            if source.item_type == SidebarItemType.DECK
        ]
        if not deck_ids:
            return False

        new_parent = DeckId(target.id)

        reparent_decks(
            parent=self.browser, deck_ids=deck_ids, new_parent=new_parent
        ).run_in_background()

        return True

    def _handle_drag_drop_tags(
        self, sources: List[SidebarItem], target: SidebarItem
    ) -> bool:
        tags = [
            source.full_name
            for source in sources
            if source.item_type == SidebarItemType.TAG
        ]
        if not tags:
            return False

        if target.item_type == SidebarItemType.TAG_ROOT:
            new_parent = ""
        else:
            new_parent = target.full_name

        reparent_tags(
            parent=self.browser, tags=tags, new_parent=new_parent
        ).run_in_background()

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
            elif item.item_type == SidebarItemType.FLAG:
                self.rename_flag(item, new_name)
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
        name: str,
        icon: Union[str, ColoredIcon],
        collapse_key: Config.Bool.Key.V,
        type: Optional[SidebarItemType] = None,
    ) -> SidebarItem:
        def update(expanded: bool) -> None:
            CollectionOp(
                self.browser,
                lambda col: col.set_config_bool(collapse_key, not expanded),
            ).run_in_background(initiator=self)

        top = SidebarItem(
            name,
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
            name=tr.browsing_sidebar_saved_searches(),
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
            name=tr.browsing_today(),
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_TODAY,
            type=SidebarItemType.TODAY_ROOT,
        )
        type = SidebarItemType.TODAY

        root.add_simple(
            name=tr.browsing_sidebar_due_today(),
            icon=icon,
            type=type,
            search_node=SearchNode(due_on_day=0),
        )
        root.add_simple(
            name=tr.browsing_added_today(),
            icon=icon,
            type=type,
            search_node=SearchNode(added_in_days=1),
        )
        root.add_simple(
            name=tr.browsing_edited_today(),
            icon=icon,
            type=type,
            search_node=SearchNode(edited_in_days=1),
        )
        root.add_simple(
            name=tr.browsing_studied_today(),
            icon=icon,
            type=type,
            search_node=SearchNode(rated=SearchNode.Rated(days=1)),
        )
        root.add_simple(
            name=tr.browsing_sidebar_first_review(),
            icon=icon,
            type=type,
            search_node=SearchNode(introduced_in_days=1),
        )
        root.add_simple(
            name=tr.browsing_again_today(),
            icon=icon,
            type=type,
            search_node=SearchNode(
                rated=SearchNode.Rated(days=1, rating=SearchNode.RATING_AGAIN)
            ),
        )
        root.add_simple(
            name=tr.browsing_sidebar_overdue(),
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
            name=tr.browsing_sidebar_card_state(),
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_CARD_STATE,
            type=SidebarItemType.CARD_STATE_ROOT,
        )
        type = SidebarItemType.CARD_STATE

        root.add_simple(
            tr.actions_new(),
            icon=icon.with_color(colors.NEW_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_NEW),
        )

        root.add_simple(
            name=tr.scheduling_learning(),
            icon=icon.with_color(colors.LEARN_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_LEARN),
        )
        root.add_simple(
            name=tr.scheduling_review(),
            icon=icon.with_color(colors.REVIEW_COUNT),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_REVIEW),
        )
        root.add_simple(
            name=tr.browsing_suspended(),
            icon=icon.with_color(colors.SUSPENDED_FG),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_SUSPENDED),
        )
        root.add_simple(
            name=tr.browsing_buried(),
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
            name=tr.browsing_sidebar_flags(),
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_FLAGS,
            type=SidebarItemType.FLAG_ROOT,
        )
        root.search_node = SearchNode(flag=SearchNode.FLAG_ANY)

        for flag in load_flags(self.col):
            root.add_child(
                SidebarItem(
                    name=flag.label,
                    icon=flag.icon,
                    search_node=flag.search_node,
                    item_type=SidebarItemType.FLAG,
                    id=flag.index,
                )
            )

        root.add_simple(
            tr.browsing_no_flag(),
            icon=icon,
            type=SidebarItemType.FLAG,
            search_node=SearchNode(flag=SearchNode.FLAG_NONE),
        )

    # Tree: Tags
    ###########################

    def _tag_tree(self, root: SidebarItem) -> None:
        icon = ":/icons/tag.svg"

        def render(
            root: SidebarItem, nodes: Iterable[TagTreeNode], head: str = ""
        ) -> None:
            def toggle_expand(node: TagTreeNode) -> Callable[[bool], None]:
                full_name = head + node.name
                return lambda expanded: set_tag_collapsed(
                    parent=self, tag=full_name, collapsed=not expanded
                ).run_in_background()

            for node in nodes:
                item = SidebarItem(
                    name=node.name,
                    icon=icon,
                    search_node=SearchNode(tag=head + node.name),
                    on_expanded=toggle_expand(node),
                    expanded=not node.collapsed,
                    item_type=SidebarItemType.TAG,
                    name_prefix=head,
                )
                root.add_child(item)
                newhead = f"{head + node.name}::"
                render(item, node.children, newhead)

        tree = self.col.tags.tree()
        root = self._section_root(
            root=root,
            name=tr.browsing_sidebar_tags(),
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_TAGS,
            type=SidebarItemType.TAG_ROOT,
        )
        root.search_node = SearchNode(negated=SearchNode(tag="none"))
        root.add_simple(
            name=tr.browsing_sidebar_untagged(),
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
            def toggle_expand(node: DeckTreeNode) -> Callable[[bool], None]:
                return lambda expanded: set_deck_collapsed(
                    parent=self,
                    deck_id=DeckId(node.deck_id),
                    collapsed=not expanded,
                    scope=DeckCollapseScope.BROWSER,
                ).run_in_background(
                    initiator=self,
                )

            for node in nodes:
                item = SidebarItem(
                    name=node.name,
                    icon=icon,
                    search_node=SearchNode(deck=head + node.name),
                    on_expanded=toggle_expand(node),
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
            name=tr.browsing_sidebar_decks(),
            icon=icon,
            collapse_key=Config.Bool.COLLAPSE_DECKS,
            type=SidebarItemType.DECK_ROOT,
        )
        root.search_node = SearchNode(deck="*")
        current = root.add_simple(
            name=tr.browsing_current_deck(),
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
            name=tr.browsing_sidebar_notetypes(),
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
                tr.browsing_manage_note_types(), lambda: self.manage_notetype(item)
            )
        elif item.item_type == SidebarItemType.NOTETYPE_TEMPLATE:
            menu.addAction(tr.notetypes_cards(), lambda: self.manage_template(item))
        elif item.item_type == SidebarItemType.SAVED_SEARCH_ROOT:
            menu.addAction(
                tr.browsing_sidebar_save_current_search(), self.save_current_search
            )
        elif item.item_type == SidebarItemType.SAVED_SEARCH:
            menu.addAction(
                tr.browsing_update_saved_search(),
                lambda: self.update_saved_search(item),
            )

    def _maybe_add_delete_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if self._enable_delete(item):
            menu.addAction(tr.actions_delete(), lambda: self._on_delete(item))

    def _maybe_add_rename_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if item.item_type.is_editable() and len(self._selected_items()) == 1:
            menu.addAction(tr.actions_rename(), lambda: self.edit(index))

    def _maybe_add_search_actions(self, menu: QMenu) -> None:
        nodes = [
            item.search_node for item in self._selected_items() if item.search_node
        ]
        if not nodes:
            return
        menu.addSeparator()
        if len(nodes) == 1:
            menu.addAction(tr.actions_search(), lambda: self.update_search(*nodes))
            return
        sub_menu = menu.addMenu(tr.actions_search())
        sub_menu.addAction(
            tr.actions_all_selected(), lambda: self.update_search(*nodes)
        )
        sub_menu.addAction(
            tr.actions_any_selected(),
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
            menu.addAction(tr.browsing_sidebar_expand(), lambda: set_expanded(True))
        if any(item.expanded for item in selected_items if item.children):
            menu.addAction(tr.browsing_sidebar_collapse(), lambda: set_expanded(False))
        if any(
            not c.expanded for i in selected_items for c in i.children if c.children
        ):
            menu.addAction(
                tr.browsing_sidebar_expand_children(),
                lambda: set_children_expanded(True),
            )
        if any(c.expanded for i in selected_items for c in i.children if c.children):
            menu.addAction(
                tr.browsing_sidebar_collapse_children(),
                lambda: set_children_expanded(False),
            )

    # Flags
    ###########################

    def rename_flag(self, item: SidebarItem, new_name: str) -> None:
        labels = self.col.get_config("flagLabels", {})
        labels[str(item.id)] = new_name
        self.col.set_config("flagLabels", labels)
        item.name = new_name
        self.browser._update_flag_labels()
        self.refresh()

    # Decks
    ###########################

    def rename_deck(self, item: SidebarItem, new_name: str) -> None:
        if not new_name:
            return

        # update UI immediately, to avoid redraw
        item.name = new_name

        full_name = item.name_prefix + new_name
        deck_id = DeckId(item.id)

        def after_fetch(deck: Deck) -> None:
            if full_name == deck.name:
                return

            rename_deck(
                parent=self,
                deck_id=deck_id,
                new_name=full_name,
            ).run_in_background()

        QueryOp(
            parent=self.browser,
            op=lambda col: col.get_deck(deck_id),
            success=after_fetch,
        ).run_in_background()

    def delete_decks(self, _item: SidebarItem) -> None:
        remove_decks(parent=self, deck_ids=self._selected_decks()).run_in_background()

    # Tags
    ###########################

    def remove_tags(self, item: SidebarItem) -> None:
        tags = self.mw.col.tags.join(self._selected_tags())
        item.name = "..."

        remove_tags_from_all_notes(
            parent=self.browser, space_separated_tags=tags
        ).run_in_background()

    def rename_tag(self, item: SidebarItem, new_name: str) -> None:
        if not new_name or new_name == item.name:
            return

        new_name_base = new_name

        old_name = item.full_name
        new_name = item.name_prefix + new_name

        item.name = new_name_base
        item.full_name = new_name

        rename_tag(
            parent=self.browser,
            current_name=old_name,
            new_name=new_name,
        ).run_in_background()

    # Saved searches
    ####################################

    _saved_searches_key = "savedFilters"

    def _get_saved_searches(self) -> Dict[str, str]:
        return self.col.get_config(self._saved_searches_key, {})

    def _set_saved_searches(self, searches: Dict[str, str]) -> None:
        self.col.set_config(self._saved_searches_key, searches)

    def _get_current_search(self) -> Optional[str]:
        try:
            return self.col.build_search_string(self.browser.current_search())
        except Exception as e:
            showWarning(str(e))
            return None

    def _save_search(self, name: str, search: str, update: bool = False) -> None:
        conf = self._get_saved_searches()
        if (
            not update
            and name in conf
            and not askUser(tr.browsing_confirm_saved_search_overwrite(name=name))
        ):
            return
        conf[name] = search
        self._set_saved_searches(conf)
        self.refresh()

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
            tr.browsing_confirm_saved_search_overwrite(name=new_name)
        ):
            return
        conf[new_name] = filt
        del conf[old_name]
        self._set_saved_searches(conf)
        item.name = new_name
        self.refresh()

    def save_current_search(self) -> None:
        if (search := self._get_current_search()) is None:
            return
        name = getOnlyText(tr.browsing_please_give_your_filter_a_name())
        if not name:
            return
        self._save_search(name, search)

    def update_saved_search(self, item: SidebarItem) -> None:
        if (search := self._get_current_search()) is None:
            return
        self._save_search(item.name, search, update=True)

    # Notetypes and templates
    ####################################

    def manage_notetype(self, item: SidebarItem) -> None:
        Models(
            self.mw,
            parent=self.browser,
            fromMain=True,
            selected_notetype_id=NotetypeId(item.id),
        )

    def manage_template(self, item: SidebarItem) -> None:
        note = Note(self.col, self.col.models.get(NotetypeId(item._parent_item.id)))
        CardLayout(self.mw, note, ord=item.id, parent=self, fill_empty=True)

    # Helpers
    ####################################

    def _selected_items(self) -> List[SidebarItem]:
        return [self.model().item_for_index(idx) for idx in self.selectedIndexes()]

    def _selected_decks(self) -> List[DeckId]:
        return [
            DeckId(item.id)
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
