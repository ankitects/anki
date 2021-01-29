# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future
from enum import Enum
from typing import Iterable, List, Optional

import aqt
from anki.errors import DeckRenameError
from anki.rsbackend import DeckTreeNode, FilterToSearchIn, NamedFilter, TagTreeNode
from aqt import gui_hooks
from aqt.main import ResetReason
from aqt.models import Models
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import TR, getOnlyText, showInfo, showWarning, tr


class SidebarItemType(Enum):
    ROOT = 0
    COLLECTION = 1
    CURRENT_DECK = 2
    FILTER = 3
    DECK = 4
    NOTETYPE = 5
    TAG = 6
    CUSTOM = 7
    TEMPLATE = 8


#  used by an add-on hook
class SidebarStage(Enum):
    ROOT = 0
    STANDARD = 1
    FAVORITES = 2
    DECKS = 3
    MODELS = 4
    TAGS = 5


class SidebarItem:
    def __init__(
        self,
        name: str,
        icon: str,
        onClick: Callable[[], None] = None,
        onExpanded: Callable[[bool], None] = None,
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
        self.onClick = onClick
        self.onExpanded = onExpanded
        self.expanded = expanded
        self.children: List["SidebarItem"] = []
        self.parentItem: Optional["SidebarItem"] = None
        self.tooltip: Optional[str] = None
        self.row_in_parent: Optional[int] = None

    def addChild(self, cb: "SidebarItem") -> None:
        self.children.append(cb)
        cb.parentItem = self

    def rowForChild(self, child: "SidebarItem") -> Optional[int]:
        try:
            return self.children.index(child)
        except ValueError:
            return None


class SidebarModel(QAbstractItemModel):
    def __init__(self, root: SidebarItem) -> None:
        super().__init__()
        self.root = root
        self._cache_rows(root)

    def _cache_rows(self, node: SidebarItem):
        "Cache index of children in parent."
        for row, item in enumerate(node.children):
            item.row_in_parent = row
            self._cache_rows(item)

    def item_for_index(self, idx: QModelIndex) -> SidebarItem:
        return idx.internalPointer()

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
        parentItem = childItem.parentItem

        if parentItem is None or parentItem == self.root:
            return QModelIndex()

        row = parentItem.row_in_parent

        return self.createIndex(row, 0, parentItem)

    def data(self, index: QModelIndex, role: int = Qt.DisplayRole) -> QVariant:
        if not index.isValid():
            return QVariant()

        if role not in (Qt.DisplayRole, Qt.DecorationRole, Qt.ToolTipRole, Qt.EditRole):
            return QVariant()

        item: SidebarItem = index.internalPointer()

        if role == Qt.DisplayRole:
            return QVariant(item.name)
        elif role == Qt.ToolTipRole:
            return QVariant(item.tooltip)
        elif role == Qt.EditRole:
            return QVariant(item.full_name)
        else:
            return QVariant(theme_manager.icon_from_resources(item.icon))

    # Helpers
    ######################################################################

    def iconFromRef(self, iconRef: str) -> QIcon:
        print("iconFromRef() deprecated")
        return theme_manager.icon_from_resources(iconRef)


def expand_where_necessary(model: SidebarModel, tree: QTreeView, parent=None) -> None:
    parent = parent or QModelIndex()
    for row in range(model.rowCount(parent)):
        idx = model.index(row, 0, parent)
        if not idx.isValid():
            continue
        expand_where_necessary(model, tree, idx)
        item = model.item_for_index(idx)
        if item and item.expanded:
            tree.setExpanded(idx, True)


class FilterModel(QSortFilterProxyModel):
    def item_for_index(self, idx: QModelIndex) -> Optional[SidebarItem]:
        if not idx.isValid():
            return None
        return self.mapToSource(idx).internalPointer()


class SidebarSearchBar(QLineEdit):
    def __init__(self, sidebar: SidebarTreeView):
        QLineEdit.__init__(self, sidebar)
        self.sidebar = sidebar
        self.timer = QTimer(self)
        self.timer.setInterval(600)
        self.timer.setSingleShot(True)
        qconnect(self.timer.timeout, self.onSearch)
        qconnect(self.textChanged, self.onTextChanged)

    def onTextChanged(self, text: str):
        if not self.timer.isActive():
            self.timer.start()

    def onSearch(self):
        self.sidebar.search_for(self.text())

    def keyPressEvent(self, evt):
        if evt.key() in (Qt.Key_Up, Qt.Key_Down):
            self.sidebar.setFocus()
        elif evt.key() in (Qt.Key_Enter, Qt.Key_Return):
            self.onSearch()
        else:
            QLineEdit.keyPressEvent(self, evt)


class SidebarTreeView(QTreeView):
    def __init__(self, browser: aqt.browser.Browser) -> None:
        super().__init__()
        self.browser = browser
        self.mw = browser.mw
        self.col = self.mw.col
        self.current_search: Optional[str] = None

        self.setContextMenuPolicy(Qt.CustomContextMenu)
        self.customContextMenuRequested.connect(self.onContextMenu)  # type: ignore
        self.context_menus = {
            SidebarItemType.DECK: (
                (tr(TR.ACTIONS_RENAME), self.rename_deck),
                (tr(TR.ACTIONS_DELETE), self.delete_deck),
            ),
            SidebarItemType.TAG: (
                (tr(TR.ACTIONS_RENAME), self.rename_tag),
                (tr(TR.ACTIONS_DELETE), self.remove_tag),
            ),
            SidebarItemType.FILTER: (
                (tr(TR.ACTIONS_RENAME), self.rename_filter),
                (tr(TR.ACTIONS_DELETE), self.remove_filter),
            ),
            SidebarItemType.NOTETYPE: ((tr(TR.ACTIONS_MANAGE), self.manage_notetype),),
        }

        self.setUniformRowHeights(True)
        self.setHeaderHidden(True)
        self.setIndentation(15)

        qconnect(self.expanded, self.onExpansion)
        qconnect(self.collapsed, self.onCollapse)

        # match window background color
        bgcolor = QPalette().window().color().name()
        self.setStyleSheet("QTreeView { background: '%s'; }" % bgcolor)

    def model(self) -> Union[FilterModel, SidebarModel]:
        return super().model()

    def refresh(self) -> None:
        "Refresh list. No-op if sidebar is not visible."
        if not self.isVisible():
            return

        def on_done(fut: Future):
            root = fut.result()
            model = SidebarModel(root)

            # from PyQt5.QtTest import QAbstractItemModelTester
            # tester = QAbstractItemModelTester(model)

            self.setModel(model)
            if self.current_search:
                self.search_for(self.current_search)
            else:
                expand_where_necessary(model, self)

        self.mw.taskman.run_in_background(self._root_tree, on_done)

    def search_for(self, text: str):
        if not text.strip():
            self.current_search = None
            self.refresh()
            return
        if not isinstance(self.model(), FilterModel):
            filter_model = FilterModel(self)
            filter_model.setSourceModel(self.model())
            filter_model.setFilterCaseSensitivity(False)  # type: ignore
            filter_model.setRecursiveFilteringEnabled(True)
            filter_model.setFilterRole(Qt.EditRole)
            self.setModel(filter_model)
        else:
            filter_model = self.model()

        self.current_search = text
        # Without collapsing first, can be very slow. Surely there's
        # a better way than this?
        self.collapseAll()
        filter_model.setFilterFixedString(text)
        self.expandAll()

    def drawRow(
        self, painter: QPainter, options: QStyleOptionViewItem, idx: QModelIndex
    ):
        if self.current_search is None:
            return super().drawRow(painter, options, idx)
        if not (item := self.model().item_for_index(idx)):
            return super().drawRow(painter, options, idx)
        if self.current_search.lower() in item.name.lower():
            brush = QBrush(theme_manager.qcolor("suspended-bg"))
            painter.save()
            painter.fillRect(options.rect, brush)
            painter.restore()
        return super().drawRow(painter, options, idx)

    def onClickCurrent(self) -> None:
        idx = self.currentIndex()
        if item := self.model().item_for_index(idx):
            if item.onClick:
                item.onClick()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)
        if event.button() == Qt.LeftButton:
            self.onClickCurrent()

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() in (Qt.Key_Return, Qt.Key_Enter):
            self.onClickCurrent()
        else:
            super().keyPressEvent(event)

    def onExpansion(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        self._onExpansionChange(idx, True)

    def onCollapse(self, idx: QModelIndex) -> None:
        if self.current_search:
            return
        self._onExpansionChange(idx, False)

    def _onExpansionChange(self, idx: QModelIndex, expanded: bool) -> None:
        item = self.model().item_for_index(idx)
        if item and item.expanded != expanded:
            item.expanded = expanded
            if item.onExpanded:
                item.onExpanded(expanded)

    # Tree building
    ###########################

    def _root_tree(self) -> SidebarItem:
        root = SidebarItem("", "", item_type=SidebarItemType.ROOT)

        handled = gui_hooks.browser_will_build_tree(
            False, root, SidebarStage.ROOT, self
        )
        if handled:
            return root

        for stage, builder in zip(
            list(SidebarStage)[1:],
            (
                self._commonly_used_tree,
                self._favorites_tree,
                self._deck_tree,
                self._notetype_tree,
                self._tag_tree,
            ),
        ):
            handled = gui_hooks.browser_will_build_tree(False, root, stage, self)
            if not handled and builder:
                builder(root)

        return root

    def _commonly_used_tree(self, root: SidebarItem) -> None:
        item = SidebarItem(
            tr(TR.BROWSING_WHOLE_COLLECTION),
            ":/icons/collection.svg",
            self._named_filter(NamedFilter.WHOLE_COLLECTION),
            item_type=SidebarItemType.COLLECTION,
        )
        root.addChild(item)
        item = SidebarItem(
            tr(TR.BROWSING_CURRENT_DECK),
            ":/icons/deck.svg",
            self._named_filter(NamedFilter.CURRENT_DECK),
            item_type=SidebarItemType.CURRENT_DECK,
        )
        root.addChild(item)

    def _favorites_tree(self, root: SidebarItem) -> None:
        assert self.col
        saved = self.col.get_config("savedFilters", {})
        for name, filt in sorted(saved.items()):
            item = SidebarItem(
                name,
                ":/icons/heart.svg",
                self._saved_filter(filt),
                item_type=SidebarItemType.FILTER,
            )
            root.addChild(item)

    def _tag_tree(self, root: SidebarItem) -> None:
        tree = self.col.backend.tag_tree()

        def render(root: SidebarItem, nodes: Iterable[TagTreeNode], head="") -> None:
            for node in nodes:

                def toggle_expand():
                    full_name = head + node.name  # pylint: disable=cell-var-from-loop
                    return lambda expanded: self.mw.col.tags.set_collapsed(
                        full_name, not expanded
                    )

                item = SidebarItem(
                    node.name,
                    ":/icons/tag.svg",
                    self._tag_filter(head + node.name),
                    toggle_expand(),
                    not node.collapsed,
                    item_type=SidebarItemType.TAG,
                    full_name=head + node.name,
                )
                root.addChild(item)
                newhead = head + node.name + "::"
                render(item, node.children, newhead)

        render(root, tree.children)

    def _deck_tree(self, root: SidebarItem) -> None:
        tree = self.col.decks.deck_tree()

        def render(root, nodes: Iterable[DeckTreeNode], head="") -> None:
            for node in nodes:

                def toggle_expand():
                    did = node.deck_id  # pylint: disable=cell-var-from-loop
                    return lambda _: self.mw.col.decks.collapseBrowser(did)

                item = SidebarItem(
                    node.name,
                    ":/icons/deck.svg",
                    self._deck_filter(head + node.name),
                    toggle_expand(),
                    not node.collapsed,
                    item_type=SidebarItemType.DECK,
                    id=node.deck_id,
                    full_name=head + node.name,
                )
                root.addChild(item)
                newhead = head + node.name + "::"
                render(item, node.children, newhead)

        render(root, tree.children)

    def _notetype_tree(self, root: SidebarItem) -> None:
        assert self.col

        for nt in sorted(self.col.models.all(), key=lambda nt: nt["name"].lower()):
            item = SidebarItem(
                nt["name"],
                ":/icons/notetype.svg",
                self._note_filter(nt["name"]),
                item_type=SidebarItemType.NOTETYPE,
                id=nt["id"],
            )

            for c, tmpl in enumerate(nt["tmpls"]):
                child = SidebarItem(
                    tmpl["name"],
                    ":/icons/notetype.svg",
                    self._template_filter(nt["name"], c),
                    item_type=SidebarItemType.TEMPLATE,
                    full_name=nt["name"] + "::" + tmpl["name"],
                )
                item.addChild(child)

            root.addChild(item)

    def _named_filter(self, name: "FilterToSearchIn.NamedFilterValue") -> Callable:
        return lambda: self.browser.update_search(
            self.col.backend.filter_to_search(FilterToSearchIn(name=name))
        )

    def _tag_filter(self, tag: str) -> Callable:
        return lambda: self.browser.update_search(
            self.col.backend.filter_to_search(FilterToSearchIn(tag=tag))
        )

    def _deck_filter(self, deck: str) -> Callable:
        return lambda: self.browser.update_search(
            self.col.backend.filter_to_search(FilterToSearchIn(deck=deck))
        )

    def _note_filter(self, note: str) -> Callable:
        return lambda: self.browser.update_search(
            self.col.backend.filter_to_search(FilterToSearchIn(note=note))
        )

    def _template_filter(self, note: str, template: int) -> Callable:
        return lambda: self.browser.update_search(
            self.col.backend.filter_to_search(FilterToSearchIn(note=note)),
            self.col.backend.filter_to_search(FilterToSearchIn(template=template)),
        )

    def _saved_filter(self, saved: str) -> Callable:
        return lambda: self.browser.update_search(saved)

    # Context menu actions
    ###########################

    def onContextMenu(self, point: QPoint) -> None:
        idx: QModelIndex = self.indexAt(point)
        item = self.model().item_for_index(idx)
        if not item:
            return
        item_type: SidebarItemType = item.item_type
        if item_type not in self.context_menus:
            return

        m = QMenu()
        for action in self.context_menus[item_type]:
            act_name = action[0]
            act_func = action[1]
            a = m.addAction(act_name)
            a.triggered.connect(lambda _, func=act_func: func(item))  # type: ignore
        m.exec_(QCursor.pos())

    def rename_deck(self, item: "aqt.browser.SidebarItem") -> None:
        deck = self.mw.col.decks.get(item.id)
        old_name = deck["name"]
        new_name = getOnlyText(tr(TR.DECKS_NEW_DECK_NAME), default=old_name)
        new_name = new_name.replace('"', "")
        if not new_name or new_name == old_name:
            return
        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_DECK))
        try:
            self.mw.col.decks.rename(deck, new_name)
        except DeckRenameError as e:
            return showWarning(e.description)
        self.refresh()
        self.mw.deckBrowser.refresh()

    def remove_tag(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.editor.saveNow(lambda: self._remove_tag(item))

    def _remove_tag(self, item: "aqt.browser.SidebarItem") -> None:
        old_name = item.full_name

        def do_remove():
            self.mw.col.backend.clear_tag(old_name)
            self.col.tags.rename_tag(old_name, "")

        def on_done(fut: Future):
            self.mw.requireReset(reason=ResetReason.BrowserRemoveTags, context=self)
            self.browser.model.endReset()
            fut.result()
            self.refresh()

        self.mw.checkpoint(tr(TR.ACTIONS_REMOVE_TAG))
        self.browser.model.beginReset()
        self.mw.taskman.run_in_background(do_remove, on_done)

    def rename_tag(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.editor.saveNow(lambda: self._rename_tag(item))

    def _rename_tag(self, item: "aqt.browser.SidebarItem") -> None:
        old_name = item.full_name
        new_name = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=old_name)
        if new_name == old_name or not new_name:
            return

        def do_rename():
            self.mw.col.backend.clear_tag(old_name)
            return self.col.tags.rename_tag(old_name, new_name)

        def on_done(fut: Future):
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

    def delete_deck(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.editor.saveNow(lambda: self._delete_deck(item))

    def _delete_deck(self, item: "aqt.browser.SidebarItem") -> None:
        did = item.id
        if self.mw.deckBrowser.ask_delete_deck(did):

            def do_delete():
                return self.mw.col.decks.rem(did, True)

            def on_done(fut: Future):
                self.mw.requireReset(reason=ResetReason.BrowserDeleteDeck, context=self)
                self.browser.search()
                self.browser.model.endReset()
                self.refresh()
                res = fut.result()  # Required to check for errors

            self.mw.checkpoint(tr(TR.DECKS_DELETE_DECK))
            self.browser.model.beginReset()
            self.mw.taskman.run_in_background(do_delete, on_done)

    def remove_filter(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.removeFilter(item.name)

    def rename_filter(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.renameFilter(item.name)

    def manage_notetype(self, item: "aqt.browser.SidebarItem") -> None:
        Models(
            self.mw, parent=self.browser, fromMain=True, selected_notetype_id=item.id
        )
