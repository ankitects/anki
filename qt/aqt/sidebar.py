# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import re
from concurrent.futures import Future
from enum import Enum

import aqt
from anki.errors import DeckRenameError
from aqt.main import ResetReason
from aqt.qt import *
from aqt.utils import TR, askUser, getOnlyText, showInfo, showWarning, tr


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
            SidebarItemType.DECK: (
                (tr(TR.ACTIONS_RENAME), self.rename_deck),
                (tr(TR.ACTIONS_DELETE), self.delete_deck),
            ),
            SidebarItemType.TAG: ((tr(TR.ACTIONS_RENAME), self.rename_tag),),
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
        self.browser.maybeRefreshSidebar()
        self.mw.deckBrowser.refresh()

    def rename_tag(self, item: "aqt.browser.SidebarItem") -> None:
        self.browser.editor.saveNow(lambda: self._rename_tag(item))

    def _rename_tag(self, item: "aqt.browser.SidebarItem") -> None:
        old_name = item.name
        new_name = getOnlyText(tr(TR.ACTIONS_NEW_NAME), default=old_name)
        if new_name == old_name or not new_name:
            return

        def do_rename():
            return self.col.tags.rename_tag(old_name, new_name)

        def on_done(fut: Future):
            self.mw.requireReset(reason=ResetReason.BrowserAddTags, context=self)
            self.browser.model.endReset()

            count = fut.result()
            if not count:
                showInfo(tr(TR.BROWSING_TAG_RENAME_WARNING_EMPTY))
                return

            self.browser.clearUnusedTags()

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
                self.browser.search()
                self.browser.model.endReset()
                self.browser.maybeRefreshSidebar()
                res = fut.result()  # Required to check for errors

            self.mw.checkpoint(tr(TR.DECKS_DELETE_DECK))
            self.browser.model.beginReset()
            self.mw.taskman.run_in_background(do_delete, on_done)
