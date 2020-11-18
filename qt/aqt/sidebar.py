# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from enum import Enum

import aqt
from anki.errors import DeckRenameError
from aqt.qt import *
from aqt.utils import TR, getOnlyText, showWarning, tr


class SidebarItemType(Enum):
    ROOT = 0
    COLLECTION = 1
    CURRENT_DECK = 2
    FILTER = 3
    DECK = 4
    NOTETYPE = 5
    TAG = 6
    CUSTOM = 7


class SidebarTreeViewBase(QTreeView):
    def __init__(self):
        super().__init__()
        qconnect(self.expanded, self.onExpansion)
        qconnect(self.collapsed, self.onCollapse)

    def onClickCurrent(self) -> None:
        idx = self.currentIndex()
        if idx.isValid():
            item: "aqt.browser.SidebarItem" = idx.internalPointer()
            if item.onClick:
                item.onClick()

    def mouseReleaseEvent(self, event: QMouseEvent) -> None:
        super().mouseReleaseEvent(event)
        self.onClickCurrent()

    def keyPressEvent(self, event: QKeyEvent) -> None:
        if event.key() in (Qt.Key_Return, Qt.Key_Enter):
            self.onClickCurrent()
        else:
            super().keyPressEvent(event)

    def onExpansion(self, idx: QModelIndex) -> None:
        self._onExpansionChange(idx, True)

    def onCollapse(self, idx: QModelIndex) -> None:
        self._onExpansionChange(idx, False)

    def _onExpansionChange(self, idx: QModelIndex, expanded: bool) -> None:
        item: "aqt.browser.SidebarItem" = idx.internalPointer()
        if item.expanded != expanded:
            item.expanded = expanded
            if item.onExpanded:
                item.onExpanded(expanded)


class NewSidebarTreeView(SidebarTreeViewBase):
    def __init__(self, browser: aqt.browser.Browser) -> None:
        super().__init__()
        self.browser = browser
        self.mw = browser.mw
        self.col = self.mw.col

        self.setContextMenuPolicy(Qt.CustomContextMenu)
        self.customContextMenuRequested.connect(self.onContextMenu)  # type: ignore
        self.context_menus = {
            SidebarItemType.DECK: ((tr(TR.ACTIONS_RENAME), self.rename_deck),),
        }

    def onContextMenu(self, point: QPoint) -> None:
        idx: QModelIndex = self.indexAt(point)
        item: "aqt.browser.SidebarItem" = idx.internalPointer()
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
        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_DECK))
        deck = self.mw.col.decks.get(item.id)
        old_name = deck["name"]
        new_name = getOnlyText(tr(TR.DECKS_NEW_DECK_NAME), default=old_name)
        new_name = new_name.replace('"', "")
        if not new_name or new_name == old_name:
            return
        try:
            self.mw.col.decks.rename(deck, new_name)
            self.browser.maybeRefreshSidebar()
        except DeckRenameError as e:
            return showWarning(e.description)
        self.show()
