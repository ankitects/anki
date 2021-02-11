# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/copyleft/agpl.html
from typing import List, Optional

import aqt
from aqt.customstudy import CustomStudy
from aqt.main import AnkiQt
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom


class TagLimit(QDialog):
    def __init__(self, mw: AnkiQt, parent: CustomStudy) -> None:
        QDialog.__init__(self, parent, Qt.Window)
        self.tags: str = ""
        self.tags_list: List[str] = []
        self.mw = mw
        self.parent: Optional[QWidget] = parent
        self.deck = self.parent.deck
        self.dialog = aqt.forms.taglimit.Ui_Dialog()
        self.dialog.setupUi(self)
        disable_help_button(self)
        s = QShortcut(
            QKeySequence("ctrl+d"), self.dialog.activeList, context=Qt.WidgetShortcut
        )
        qconnect(s.activated, self.dialog.activeList.clearSelection)
        s = QShortcut(
            QKeySequence("ctrl+d"), self.dialog.inactiveList, context=Qt.WidgetShortcut
        )
        qconnect(s.activated, self.dialog.inactiveList.clearSelection)
        self.rebuildTagList()
        restoreGeom(self, "tagLimit")
        self.exec_()

    def rebuildTagList(self) -> None:
        usertags = self.mw.col.tags.byDeck(self.deck["id"], True)
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
                    mode = QItemSelectionModel.Select
                    self.dialog.activeCheck.setChecked(True)
                else:
                    mode = QItemSelectionModel.Deselect
                idx = self.dialog.activeList.indexFromItem(item)
                self.dialog.activeList.selectionModel().select(idx, mode)
                # inactive
                item = QListWidgetItem(t.replace("_", " "))
                self.dialog.inactiveList.addItem(item)
                if t in noHash:
                    mode = QItemSelectionModel.Select
                else:
                    mode = QItemSelectionModel.Deselect
                idx = self.dialog.inactiveList.indexFromItem(item)
                self.dialog.inactiveList.selectionModel().select(idx, mode)

    def reject(self) -> None:
        self.tags = ""
        QDialog.reject(self)

    def accept(self) -> None:
        self.hide()
        # gather yes/no tags
        yes = []
        no = []
        for c in range(self.dialog.activeList.count()):
            # active
            if self.dialog.activeCheck.isChecked():
                item = self.dialog.activeList.item(c)
                idx = self.dialog.activeList.indexFromItem(item)
                if self.dialog.activeList.selectionModel().isSelected(idx):
                    yes.append(self.tags_list[c])
            # inactive
            item = self.dialog.inactiveList.item(c)
            idx = self.dialog.inactiveList.indexFromItem(item)
            if self.dialog.inactiveList.selectionModel().isSelected(idx):
                no.append(self.tags_list[c])
        # save in the deck for future invocations
        self.deck["activeTags"] = yes
        self.deck["inactiveTags"] = no
        self.mw.col.decks.save(self.deck)
        # build query string
        self.tags = ""
        if yes:
            arr = []
            for req in yes:
                arr.append(f'tag:"{req}"')
            self.tags += f"({' or '.join(arr)})"
        if no:
            arr = []
            for req in no:
                arr.append(f'-tag:"{req}"')
            self.tags += f" {' '.join(arr)}"
        saveGeom(self, "tagLimit")
        QDialog.accept(self)
