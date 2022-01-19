# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/agpl.html

from __future__ import annotations

from typing import List, Optional, Tuple

import aqt
from anki.lang import with_collapsed_whitespace
from aqt.main import AnkiQt
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, showWarning, tr


class TagLimit(QDialog):
    @staticmethod
    def get_tags(
        mw: AnkiQt, parent: aqt.customstudy.CustomStudy
    ) -> Tuple[List[str], List[str]]:
        """Get two lists of tags to include/exclude."""
        return TagLimit(mw, parent).tags

    def __init__(self, mw: AnkiQt, parent: aqt.customstudy.CustomStudy) -> None:
        QDialog.__init__(self, parent, Qt.WindowType.Window)
        self.tags: Tuple[List[str], List[str]] = ([], [])
        self.tags_list: list[str] = []
        self.mw = mw
        self.parent_: Optional[aqt.customstudy.CustomStudy] = parent
        self.deck = self.parent_.deck
        self.dialog = aqt.forms.taglimit.Ui_Dialog()
        self.dialog.setupUi(self)
        disable_help_button(self)
        s = QShortcut(
            QKeySequence("ctrl+d"),
            self.dialog.activeList,
            context=Qt.ShortcutContext.WidgetShortcut,
        )
        qconnect(s.activated, self.dialog.activeList.clearSelection)
        s = QShortcut(
            QKeySequence("ctrl+d"),
            self.dialog.inactiveList,
            context=Qt.ShortcutContext.WidgetShortcut,
        )
        qconnect(s.activated, self.dialog.inactiveList.clearSelection)
        self.rebuildTagList()
        restoreGeom(self, "tagLimit")
        self.exec()

    def rebuildTagList(self) -> None:
        usertags = self.mw.col.tags.by_deck(self.deck["id"], True)
        yes = self.deck.get("activeTags", [])
        no = self.deck.get("inactiveTags", [])
        yesHash = {}
        noHash = {}
        for y in yes:
            yesHash[y] = True
        for n in no:
            noHash[n] = True
        groupedTags = []
        usertags.sort()
        groupedTags.append(usertags)
        self.tags_list = []
        for tags in groupedTags:
            for t in tags:
                self.tags_list.append(t)
                item = QListWidgetItem(t.replace("_", " "))
                self.dialog.activeList.addItem(item)
                if t in yesHash:
                    mode = QItemSelectionModel.SelectionFlag.Select
                    self.dialog.activeCheck.setChecked(True)
                else:
                    mode = QItemSelectionModel.SelectionFlag.Deselect
                idx = self.dialog.activeList.indexFromItem(item)
                self.dialog.activeList.selectionModel().select(idx, mode)
                # inactive
                item = QListWidgetItem(t.replace("_", " "))
                self.dialog.inactiveList.addItem(item)
                if t in noHash:
                    mode = QItemSelectionModel.SelectionFlag.Select
                else:
                    mode = QItemSelectionModel.SelectionFlag.Deselect
                idx = self.dialog.inactiveList.indexFromItem(item)
                self.dialog.inactiveList.selectionModel().select(idx, mode)

    def reject(self) -> None:
        QDialog.reject(self)

    def accept(self) -> None:
        include_tags = exclude_tags = []
        # gather yes/no tags
        for c in range(self.dialog.activeList.count()):
            # active
            if self.dialog.activeCheck.isChecked():
                item = self.dialog.activeList.item(c)
                idx = self.dialog.activeList.indexFromItem(item)
                if self.dialog.activeList.selectionModel().isSelected(idx):
                    include_tags.append(self.tags_list[c])
            # inactive
            item = self.dialog.inactiveList.item(c)
            idx = self.dialog.inactiveList.indexFromItem(item)
            if self.dialog.inactiveList.selectionModel().isSelected(idx):
                exclude_tags.append(self.tags_list[c])

        if (len(include_tags) + len(exclude_tags)) > 100:
            showWarning(with_collapsed_whitespace(tr.errors_100_tags_max()))
            return

        self.hide()
        self.tags = (include_tags, exclude_tags)

        # save in the deck for future invocations
        self.deck["activeTags"] = include_tags
        self.deck["inactiveTags"] = exclude_tags
        self.mw.col.decks.save(self.deck)

        saveGeom(self, "tagLimit")
        QDialog.accept(self)
