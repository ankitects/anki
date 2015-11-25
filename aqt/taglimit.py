# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import aqt
from aqt.qt import *
from aqt.utils import saveGeom, restoreGeom

class TagLimit(QDialog):

    def __init__(self, mw, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.mw = mw
        self.parent = parent
        self.deck = self.parent.deck
        self.dialog = aqt.forms.taglimit.Ui_Dialog()
        self.dialog.setupUi(self)
        self.rebuildTagList()
        restoreGeom(self, "tagLimit")
        self.exec_()

    def rebuildTagList(self):
        usertags = self.mw.col.tags.byDeck(self.deck['id'], True)
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
        icon = QIcon(":/icons/Anki_Fact.png")
        groupedTags.append([icon, usertags])
        self.tags = []
        for (icon, tags) in groupedTags:
            for t in tags:
                self.tags.append(t)
                item = QListWidgetItem(icon, t.replace("_", " "))
                self.dialog.activeList.addItem(item)
                if t in yesHash:
                    mode = QItemSelectionModel.Select
                    self.dialog.activeCheck.setChecked(True)
                else:
                    mode = QItemSelectionModel.Deselect
                idx = self.dialog.activeList.indexFromItem(item)
                self.dialog.activeList.selectionModel().select(idx, mode)
                # inactive
                item = QListWidgetItem(icon, t.replace("_", " "))
                self.dialog.inactiveList.addItem(item)
                if t in noHash:
                    mode = QItemSelectionModel.Select
                else:
                    mode = QItemSelectionModel.Deselect
                idx = self.dialog.inactiveList.indexFromItem(item)
                self.dialog.inactiveList.selectionModel().select(idx, mode)

    def reject(self):
        self.tags = ""
        QDialog.reject(self)

    def accept(self):
        self.hide()
        n = 0
        # gather yes/no tags
        yes = []
        no = []
        for c in range(self.dialog.activeList.count()):
            # active
            if self.dialog.activeCheck.isChecked():
                item = self.dialog.activeList.item(c)
                idx = self.dialog.activeList.indexFromItem(item)
                if self.dialog.activeList.selectionModel().isSelected(idx):
                    yes.append(self.tags[c])
            # inactive
            item = self.dialog.inactiveList.item(c)
            idx = self.dialog.inactiveList.indexFromItem(item)
            if self.dialog.inactiveList.selectionModel().isSelected(idx):
                no.append(self.tags[c])
        # save in the deck for future invocations
        self.deck['activeTags'] = yes
        self.deck['inactiveTags'] = no
        self.mw.col.decks.save(self.deck)
        # build query string
        self.tags = ""
        if yes:
            arr = []
            for req in yes:
                arr.append("tag:'%s'" % req)
            self.tags += "(" + " or ".join(arr) + ")"
        if no:
            arr = []
            for req in no:
                arr.append("-tag:'%s'" % req)
            self.tags += " " + " ".join(arr)
        saveGeom(self, "tagLimit")
        QDialog.accept(self)
