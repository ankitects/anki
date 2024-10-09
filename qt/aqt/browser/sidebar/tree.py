# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from collections.abc import Callable, Iterable
from enum import Enum, auto
from typing import cast

import aqt
import aqt.browser
import aqt.operations
from anki.collection import (
    Config,
    OpChanges,
    OpChangesWithCount,
    SearchJoiner,
    SearchNode,
)
from anki.decks import DeckCollapseScope, DeckId, DeckTreeNode
from anki.models import NotetypeId
from anki.notes import Note
from anki.tags import TagTreeNode
from anki.types import assert_exhaustive
from aqt import colors, gui_hooks
from aqt.browser.find_and_replace import FindAndReplaceDialog
from aqt.browser.sidebar.item import SidebarItem, SidebarItemType
from aqt.browser.sidebar.model import SidebarModel
from aqt.browser.sidebar.searchbar import SidebarSearchBar
from aqt.browser.sidebar.toolbar import SidebarTool, SidebarToolbar
from aqt.clayout import CardLayout
from aqt.fields import FieldDialog
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
from aqt.qt import sip
from aqt.theme import ColoredIcon, theme_manager
from aqt.utils import (
    KeyboardModifiersPressed,
    askUser,
    getOnlyText,
    showInfo,
    showWarning,
    tooltip,
    tr,
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
        self.current_search: str | None = None
        self.valid_drop_types: tuple[SidebarItemType, ...] = ()
        self._refresh_needed = False

        self.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.customContextMenuRequested.connect(self.onContextMenu)  # type: ignore
        self.setUniformRowHeights(True)
        self.setHeaderHidden(True)
        self.setIndentation(15)
        self.setAutoExpandDelay(600)
        self.setDragDropOverwriteMode(False)
        self.setEditTriggers(QAbstractItemView.EditTrigger.EditKeyPressed)

        qconnect(self.expanded, self._on_expansion)
        qconnect(self.collapsed, self._on_collapse)

        self._setup_style()

        # these do not really belong here, they should be in a higher-level class
        self.toolbar = SidebarToolbar(self)
        self.searchBar = SidebarSearchBar(self)

        gui_hooks.flag_label_did_change.append(self.refresh)
        gui_hooks.theme_did_change.append(self._setup_style)

    def _setup_style(self) -> None:
        # match window background color and tweak style
        bgcolor = QPalette().window().color().name()
        border = theme_manager.var(colors.BORDER)
        styles = [
            "padding: 3px",
            "padding-right: 0px",
            "border: 0",
            f"background: {bgcolor}",
        ]

        self.setStyleSheet("QTreeView { %s }" % ";".join(styles))

    def cleanup(self) -> None:
        self.toolbar.cleanup()
        gui_hooks.flag_label_did_change.remove(self.refresh)
        gui_hooks.theme_did_change.remove(self._setup_style)

    @property
    def tool(self) -> SidebarTool:
        return self._tool

    @tool.setter
    def tool(self, tool: SidebarTool) -> None:
        self._tool = tool
        if tool == SidebarTool.SEARCH:
            selection_mode = QAbstractItemView.SelectionMode.SingleSelection
            drag_drop_mode = QAbstractItemView.DragDropMode.NoDragDrop
            double_click_expands = False
        else:
            selection_mode = QAbstractItemView.SelectionMode.ExtendedSelection
            drag_drop_mode = QAbstractItemView.DragDropMode.InternalMove
            double_click_expands = True
        self.setSelectionMode(selection_mode)
        self.setDragDropMode(drag_drop_mode)
        self.setExpandsOnDoubleClick(double_click_expands)

    def model(self) -> SidebarModel:
        return cast(SidebarModel, super().model())

    # Refreshing
    ###########################

    def op_executed(
        self, changes: OpChanges, handler: object | None, focused: bool
    ) -> None:
        if changes.browser_sidebar and handler is not self:
            self._refresh_needed = True
        if focused:
            self.refresh_if_needed()

    def refresh_if_needed(self) -> None:
        if self._refresh_needed:
            self.refresh()
            self._refresh_needed = False

    def refresh(self, new_current: SidebarItem | None = None) -> None:
        "Refresh list. No-op if sidebar is not visible."
        if not self.isVisible():
            return

        if not new_current and self.model() and (idx := self.currentIndex()):
            new_current = self.model().item_for_index(idx)

        def on_done(root: SidebarItem) -> None:
            # user may have closed browser
            if sip.isdeleted(self):
                return

            # block repainting during refreshing to avoid flickering
            self.setUpdatesEnabled(False)

            if old_model := self.model():
                old_model.deleteLater()
            model = SidebarModel(self, root)
            self.setModel(model)

            if self.current_search:
                self.search_for(self.current_search)
            else:
                self._expand_where_necessary(model)
            if new_current:
                self.restore_current(new_current)

            self.setUpdatesEnabled(True)

            # needs to be set after changing model
            qconnect(
                self._selection_model().selectionChanged, self._on_selection_changed
            )

        QueryOp(
            parent=self.browser, op=lambda _: self._root_tree(), success=on_done
        ).run_in_background()

    def restore_current(self, current: SidebarItem) -> None:
        if current_item := self.find_item(current.has_same_id):
            index = self.model().index_for_item(current_item)

            self._selection_model().setCurrentIndex(
                index, QItemSelectionModel.SelectionFlag.SelectCurrent
            )
            self.scrollTo(index, QAbstractItemView.ScrollHint.PositionAtCenter)

    def find_item(
        self,
        is_target: Callable[[SidebarItem], bool],
        parent: SidebarItem | None = None,
    ) -> SidebarItem | None:
        def find_item_rec(parent: SidebarItem) -> SidebarItem | None:
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
        parent: QModelIndex | None = None,
        searching: bool = False,
    ) -> None:
        scroll_to_first_match = searching

        def expand_node(parent: QModelIndex) -> None:
            nonlocal scroll_to_first_match

            for row in range(model.rowCount(parent)):
                idx = model.index(row, 0, parent)
                if not idx.isValid():
                    continue

                # descend into children first
                expand_node(idx)

                if item := model.item_for_index(idx):
                    if item.show_expanded(searching):
                        self.setExpanded(idx, True)
                    if item.is_highlighted() and scroll_to_first_match:
                        self._selection_model().setCurrentIndex(
                            idx,
                            QItemSelectionModel.SelectionFlag.SelectCurrent,
                        )
                        self.scrollTo(
                            idx, QAbstractItemView.ScrollHint.PositionAtCenter
                        )
                        scroll_to_first_match = False

        expand_node(parent or QModelIndex())

    def update_search(
        self,
        *terms: str | SearchNode,
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
        self, painter: QPainter | None, options: QStyleOptionViewItem, idx: QModelIndex
    ) -> None:
        if self.current_search and (item := self.model().item_for_index(idx)):
            if item.is_highlighted():
                assert painter is not None

                brush = QBrush(theme_manager.qcolor(colors.HIGHLIGHT_BG))
                painter.save()
                painter.fillRect(options.rect, brush)
                painter.restore()
        return super().drawRow(painter, options, idx)

    def dropEvent(self, event: QDropEvent | None) -> None:
        assert event is not None

        model = self.model()
        if qtmajor == 5:
            pos = event.pos()  # type: ignore
        else:
            pos = event.position().toPoint()
        target_item = model.item_for_index(self.indexAt(pos))
        if self.handle_drag_drop(self._selected_items(), target_item):
            event.acceptProposedAction()

    def mouseReleaseEvent(self, event: QMouseEvent | None) -> None:
        assert event is not None

        super().mouseReleaseEvent(event)
        if (
            self.tool == SidebarTool.SEARCH
            and event.button() == Qt.MouseButton.LeftButton
        ):
            if qtmajor == 5:
                pos = event.pos()  # type: ignore
            else:
                pos = event.position().toPoint()
            if (index := self.currentIndex()) == self.indexAt(pos):
                self._on_search(index)

    def keyPressEvent(self, event: QKeyEvent | None) -> None:
        assert event is not None

        index = self.currentIndex()
        if event.key() in (Qt.Key.Key_Return, Qt.Key.Key_Enter):
            if not self.isPersistentEditorOpen(index):
                self._on_search(index)
        elif event.key() == Qt.Key.Key_Delete:
            self._on_delete_key(index)
        else:
            super().keyPressEvent(event)

    # Slots
    ###########

    def _on_selection_changed(self, _new: QItemSelection, _old: QItemSelection) -> None:
        valid_drop_types = []
        selected_items = self._selected_items()
        selected_types = [item.item_type for item in selected_items]

        # check if renaming is allowed
        if all(item_type == SidebarItemType.DECK for item_type in selected_types):
            valid_drop_types += [SidebarItemType.DECK, SidebarItemType.DECK_ROOT]
        elif all(item_type == SidebarItemType.TAG for item_type in selected_types):
            valid_drop_types += [SidebarItemType.TAG, SidebarItemType.TAG_ROOT]

        # check if creating a saved search is allowed
        if len(selected_items) == 1:
            if (
                selected_types[0] != SidebarItemType.SAVED_SEARCH
                and selected_items[0].search_node is not None
            ):
                valid_drop_types += [
                    SidebarItemType.SAVED_SEARCH_ROOT,
                    SidebarItemType.SAVED_SEARCH,
                ]

        self.valid_drop_types = tuple(valid_drop_types)

    def handle_drag_drop(self, sources: list[SidebarItem], target: SidebarItem) -> bool:
        if target.item_type in (SidebarItemType.DECK, SidebarItemType.DECK_ROOT):
            return self._handle_drag_drop_decks(sources, target)
        if target.item_type in (SidebarItemType.TAG, SidebarItemType.TAG_ROOT):
            return self._handle_drag_drop_tags(sources, target)
        if target.item_type in (
            SidebarItemType.SAVED_SEARCH_ROOT,
            SidebarItemType.SAVED_SEARCH,
        ):
            return self._handle_drag_drop_saved_search(sources, target)
        return False

    def _handle_drag_drop_decks(
        self, sources: list[SidebarItem], target: SidebarItem
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
        self, sources: list[SidebarItem], target: SidebarItem
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

    def _handle_drag_drop_saved_search(
        self, sources: list[SidebarItem], _target: SidebarItem
    ) -> bool:
        if len(sources) != 1 or sources[0].search_node is None:
            return False
        self._save_search(
            sources[0].name, self.col.build_search_string(sources[0].search_node)
        )
        return True

    def _on_search(self, index: QModelIndex) -> None:
        if model := self.model():
            if item := model.item_for_index(index):
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

    def _on_add(self, item: SidebarItem):
        self.browser.add_card(DeckId(item.id))

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
        root = SidebarItem("", "", item_type=SidebarItemType.ROOT)

        for stage in SidebarStage:
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
        icon: str | ColoredIcon,
        collapse_key: Config.Bool.V,
        type: SidebarItemType | None = None,
    ) -> SidebarItem:
        assert type is not None

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
        icon = "icons:heart-outline.svg"
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
        icon = "icons:clock-outline.svg"
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
            name=tr.browsing_sidebar_rescheduled(),
            icon=icon,
            type=type,
            search_node=SearchNode(
                rated=SearchNode.Rated(days=1, rating=SearchNode.RATING_BY_RESCHEDULE)
            ),
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
        icon = "icons:circle.svg"
        icon_outline = "icons:circle-outline.svg"

        root = self._section_root(
            root=root,
            name=tr.browsing_sidebar_card_state(),
            icon=icon_outline,
            collapse_key=Config.Bool.COLLAPSE_CARD_STATE,
            type=SidebarItemType.CARD_STATE_ROOT,
        )
        type = SidebarItemType.CARD_STATE
        colored_icon = ColoredIcon(path=icon, color=colors.FG_DISABLED)

        root.add_simple(
            tr.actions_new(),
            icon=colored_icon.with_color(colors.STATE_NEW),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_NEW),
        )

        root.add_simple(
            name=tr.scheduling_learning(),
            icon=colored_icon.with_color(colors.STATE_LEARN),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_LEARN),
        )
        root.add_simple(
            name=tr.browsing_sidebar_card_state_review(),
            icon=colored_icon.with_color(colors.STATE_REVIEW),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_REVIEW),
        )
        root.add_simple(
            name=tr.browsing_suspended(),
            icon=colored_icon.with_color(colors.STATE_SUSPENDED),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_SUSPENDED),
        )
        root.add_simple(
            name=tr.browsing_buried(),
            icon=colored_icon.with_color(colors.STATE_BURIED),
            type=type,
            search_node=SearchNode(card_state=SearchNode.CARD_STATE_BURIED),
        )

    # Tree: Flags
    ###########################

    def _flags_tree(self, root: SidebarItem) -> None:
        icon_off = "icons:flag-variant-off-outline.svg"
        icon = "icons:flag-variant.svg"
        icon_outline = "icons:flag-variant-outline.svg"

        root = self._section_root(
            root=root,
            name=tr.browsing_sidebar_flags(),
            icon=icon_outline,
            collapse_key=Config.Bool.COLLAPSE_FLAGS,
            type=SidebarItemType.FLAG_ROOT,
        )
        root.search_node = SearchNode(flag=SearchNode.FLAG_ANY)

        root.add_simple(
            tr.browsing_no_flag(),
            icon=icon_off,
            type=SidebarItemType.FLAG_NONE,
            search_node=SearchNode(flag=SearchNode.FLAG_NONE),
        )

        for flag in self.mw.flags.all():
            root.add_child(
                SidebarItem(
                    name=flag.label,
                    icon=flag.icon,
                    search_node=flag.search_node,
                    item_type=SidebarItemType.FLAG,
                    id=flag.index,
                )
            )

    # Tree: Tags
    ###########################

    def _tag_tree(self, root: SidebarItem) -> None:
        icon = "icons:tag-outline.svg"
        icon_off = "icons:tag-off-outline.svg"

        def render(
            root: SidebarItem, nodes: Iterable[TagTreeNode], head: str = ""
        ) -> None:
            def toggle_expand(node: TagTreeNode) -> Callable[[bool], None]:
                full_name = head + node.name
                return lambda expanded: set_tag_collapsed(
                    parent=self, tag=full_name, collapsed=not expanded
                ).run_in_background(initiator=self)

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
        root.search_node = SearchNode(tag="_*")
        root.add_simple(
            name=tr.browsing_sidebar_untagged(),
            icon=icon_off,
            type=SidebarItemType.TAG_NONE,
            search_node=SearchNode(negated=SearchNode(tag="_*")),
        )

        render(root, tree.children)

    # Tree: Decks
    ###########################

    def _deck_tree(self, root: SidebarItem) -> None:
        icon = "icons:book-outline.svg"
        icon_current = "icons:book-clock-outline.svg"
        icon_filtered = "icons:book-cog-outline.svg"

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
                    icon=icon_filtered if node.filtered else icon,
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
        root.search_node = SearchNode(deck="_*")
        current = root.add_simple(
            name=tr.browsing_current_deck(),
            icon=icon_current,
            type=SidebarItemType.DECK_CURRENT,
            search_node=SearchNode(deck="current"),
        )
        current.id = self.mw.col.decks.selected()

        render(root, tree.children)

    # Tree: Notetypes
    ###########################

    def _notetype_tree(self, root: SidebarItem) -> None:
        notetype_icon = "icons:newspaper.svg"
        template_icon = "icons:application-braces-outline.svg"
        field_icon = "icons:form-textbox.svg"

        root = self._section_root(
            root=root,
            name=tr.browsing_sidebar_notetypes(),
            icon=notetype_icon,
            collapse_key=Config.Bool.COLLAPSE_NOTETYPES,
            type=SidebarItemType.NOTETYPE_ROOT,
        )
        root.search_node = SearchNode(note="_*")

        for nt in sorted(self.col.models.all(), key=lambda nt: nt["name"].lower()):
            item = SidebarItem(
                nt["name"],
                notetype_icon,
                search_node=SearchNode(note=nt["name"]),
                item_type=SidebarItemType.NOTETYPE,
                id=nt["id"],
            )

            for c, tmpl in enumerate(nt["tmpls"]):
                child = SidebarItem(
                    tmpl["name"],
                    template_icon,
                    search_node=self.col.group_searches(
                        SearchNode(note=nt["name"]), SearchNode(template=c)
                    ),
                    item_type=SidebarItemType.NOTETYPE_TEMPLATE,
                    id=tmpl["ord"],
                )
                item.add_child(child)

            for c, fld in enumerate(nt["flds"]):
                child = SidebarItem(
                    fld["name"],
                    field_icon,
                    search_node=self.col.group_searches(
                        SearchNode(note=nt["name"]), SearchNode(field_name=fld["name"])
                    ),
                    item_type=SidebarItemType.NOTETYPE_FIELD,
                    id=fld["ord"],
                )
                item.add_child(child)

            root.add_child(item)

    # Context menu
    ###########################

    def onContextMenu(self, point: QPoint) -> None:
        index: QModelIndex = self.indexAt(point)
        item = self.model().item_for_index(index)
        if item and self._selection_model().isSelected(index):
            self.show_context_menu(item, index)

    def show_context_menu(self, item: SidebarItem, index: QModelIndex) -> None:
        menu = QMenu()
        self._maybe_add_type_specific_actions(menu, item)
        menu.addSeparator()
        self._maybe_add_add_action(menu, item)
        self._maybe_add_delete_action(menu, item, index)
        self._maybe_add_rename_actions(menu, item, index)
        self._maybe_add_find_and_replace_action(menu, item, index)
        menu.addSeparator()
        self._maybe_add_search_actions(menu)
        menu.addSeparator()
        self._maybe_add_tree_actions(menu)
        gui_hooks.browser_sidebar_will_show_context_menu(self, menu, item, index)
        if menu.children():
            menu.exec(QCursor.pos())

    def _maybe_add_type_specific_actions(self, menu: QMenu, item: SidebarItem) -> None:
        if item.item_type in (SidebarItemType.NOTETYPE, SidebarItemType.NOTETYPE_ROOT):
            menu.addAction(
                tr.browsing_manage_note_types(), lambda: self.manage_notetype(item)
            )
        elif item.item_type == SidebarItemType.NOTETYPE_TEMPLATE:
            menu.addAction(tr.notetypes_cards(), lambda: self.manage_template(item))
        elif item.item_type == SidebarItemType.NOTETYPE_FIELD:
            menu.addAction(tr.notetypes_fields(), lambda: self.manage_fields(item))
        elif item.item_type == SidebarItemType.SAVED_SEARCH_ROOT:
            menu.addAction(
                tr.browsing_sidebar_save_current_search(), self.save_current_search
            )
        elif item.item_type == SidebarItemType.SAVED_SEARCH:
            menu.addAction(
                tr.browsing_update_saved_search(),
                lambda: self.update_saved_search(item),
            )
        elif item.item_type == SidebarItemType.TAG:
            if all(s.item_type == item.item_type for s in self._selected_items()):
                menu.addAction(
                    tr.browsing_add_to_selected_notes(), self.add_tags_to_selected_notes
                )
                menu.addAction(
                    tr.browsing_remove_from_selected_notes(),
                    self.remove_tags_from_selected_notes,
                )

    def _maybe_add_add_action(self, menu: QMenu, item: SidebarItem) -> None:
        if item.item_type.can_be_added_to():
            menu.addAction(tr.browsing_add_notes(), lambda: self._on_add(item))

    def _maybe_add_delete_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if self._enable_delete(item):
            menu.addAction(tr.actions_delete(), lambda: self._on_delete(item))

    def _maybe_add_rename_actions(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if item.item_type.is_editable() and len(self._selected_items()) == 1:
            menu.addAction(tr.actions_rename(), lambda: self.edit(index))
            if (
                item.item_type in (SidebarItemType.TAG, SidebarItemType.DECK)
                and item.name_prefix
            ):
                menu.addAction(
                    tr.actions_rename_with_parents(),
                    lambda: self._on_rename_with_parents(item),
                )

    def _maybe_add_find_and_replace_action(
        self, menu: QMenu, item: SidebarItem, index: QModelIndex
    ) -> None:
        if (
            len(self._selected_items()) == 1
            and item.item_type is SidebarItemType.NOTETYPE_FIELD
        ):
            menu.addAction(
                tr.browsing_find_and_replace(), lambda: self._on_find_and_replace(item)
            )

    def _maybe_add_search_actions(self, menu: QMenu) -> None:
        nodes = [
            item.search_node for item in self._selected_items() if item.search_node
        ]
        if not nodes:
            return
        if len(nodes) == 1:
            menu.addAction(tr.actions_search(), lambda: self.update_search(*nodes))
            return
        sub_menu = menu.addMenu(tr.actions_search())
        assert sub_menu is not None

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

    def _on_rename_with_parents(self, item: SidebarItem) -> None:
        title = "Anki"
        if item.item_type is SidebarItemType.TAG:
            title = tr.actions_rename_tag()
        elif item.item_type is SidebarItemType.DECK:
            title = tr.actions_rename_deck()

        new_name = getOnlyText(
            tr.actions_new_name(), title=title, default=item.full_name
        ).replace('"', "")
        if not new_name or new_name == item.full_name:
            return

        if item.item_type is SidebarItemType.TAG:

            def success(out: OpChangesWithCount) -> None:
                if out.count:
                    tooltip(tr.browsing_notes_updated(count=out.count), parent=self)
                else:
                    showInfo(tr.browsing_tag_rename_warning_empty(), parent=self)

            rename_tag(
                parent=self,
                current_name=item.full_name,
                new_name=new_name,
            ).success(success).run_in_background()

        elif item.item_type is SidebarItemType.DECK:
            rename_deck(
                parent=self,
                deck_id=DeckId(item.id),
                new_name=new_name,
            ).run_in_background()

    def _on_find_and_replace(self, item: SidebarItem) -> None:
        field = None
        if item.item_type is SidebarItemType.NOTETYPE_FIELD:
            field = item.name
        FindAndReplaceDialog(
            self,
            mw=self.mw,
            note_ids=self.browser.selected_notes(),
            field=field,
        )

    # Flags
    ###########################

    def rename_flag(self, item: SidebarItem, new_name: str) -> None:
        item.name = new_name
        self.mw.flags.rename_flag(item.id, new_name)

    # Decks
    ###########################

    def rename_deck(self, item: SidebarItem, new_name: str) -> None:
        if not new_name or new_name == item.name:
            return

        # update UI immediately, to avoid redraw
        item.name = new_name

        rename_deck(
            parent=self,
            deck_id=DeckId(item.id),
            new_name=item.name_prefix + new_name,
        ).run_in_background()

    def delete_decks(self, _item: SidebarItem) -> None:
        remove_decks(
            parent=self, deck_name=_item.name, deck_ids=self._selected_decks()
        ).run_in_background()

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

        old_name = item.name
        old_full_name = item.full_name
        new_full_name = item.name_prefix + new_name

        item.name = new_name
        item.full_name = new_full_name

        def success(out: OpChangesWithCount) -> None:
            if out.count:
                tooltip(tr.browsing_notes_updated(count=out.count), parent=self)
            else:
                # revert renaming of sidebar item
                item.full_name = old_full_name
                item.name = old_name
                showInfo(tr.browsing_tag_rename_warning_empty(), parent=self)

        rename_tag(
            parent=self.browser,
            current_name=old_full_name,
            new_name=new_full_name,
        ).success(success).run_in_background()

    def add_tags_to_selected_notes(self) -> None:
        tags = " ".join(item.full_name for item in self._selected_items())
        self.browser.add_tags_to_selected_notes(tags)

    def remove_tags_from_selected_notes(self) -> None:
        tags = " ".join(item.full_name for item in self._selected_items())
        self.browser.remove_tags_from_selected_notes(tags)

    # Saved searches
    ####################################

    _saved_searches_key = "savedFilters"

    def _get_saved_searches(self) -> dict[str, str]:
        return self.col.get_config(self._saved_searches_key, {})

    def _set_saved_searches(self, searches: dict[str, str]) -> None:
        self.col.set_config(self._saved_searches_key, searches)

    def _get_current_search(self) -> str | None:
        try:
            return self.col.build_search_string(self.browser.current_search())
        except Exception as e:
            showWarning(str(e))
            return None

    def _save_search(self, name: str, search: str, update: bool = False) -> None:
        conf = self._get_saved_searches()
        if not update and name in conf:
            if conf[name] == search:
                # nothing to do
                return
            if not askUser(tr.browsing_confirm_saved_search_overwrite(name=name)):
                # don't overwrite existing saved search
                return
        conf[name] = search
        self._set_saved_searches(conf)
        self.refresh(SidebarItem(name, "", item_type=SidebarItemType.SAVED_SEARCH))

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
        assert item._parent_item is not None

        note = Note(self.col, self.col.models.get(NotetypeId(item._parent_item.id)))
        CardLayout(self.mw, note, ord=item.id, parent=self, fill_empty=True)

    def manage_fields(self, item: SidebarItem) -> None:
        assert item._parent_item is not None

        notetype = self.mw.col.models.get(NotetypeId(item._parent_item.id))
        assert notetype is not None

        FieldDialog(self.mw, notetype, parent=self, open_at=item.id)

    # Helpers
    ####################################

    def _selected_items(self) -> list[SidebarItem]:
        return [self.model().item_for_index(idx) for idx in self.selectedIndexes()]

    def _selected_decks(self) -> list[DeckId]:
        return [
            DeckId(item.id)
            for item in self._selected_items()
            if item.item_type == SidebarItemType.DECK
        ]

    def _selected_saved_searches(self) -> list[str]:
        return [
            item.name
            for item in self._selected_items()
            if item.item_type == SidebarItemType.SAVED_SEARCH
        ]

    def _selected_tags(self) -> list[str]:
        return [
            item.full_name
            for item in self._selected_items()
            if item.item_type == SidebarItemType.TAG
        ]

    def _selection_model(self) -> QItemSelectionModel:
        selection_model = self.selectionModel()
        assert selection_model is not None
        return selection_model
