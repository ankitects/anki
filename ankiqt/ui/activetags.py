# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt
from anki.utils import parseTags, joinTags, canonifyTags
from ankiqt.ui.utils import saveGeom, restoreGeom

class ActiveTagsChooser(QDialog):

    def __init__(self, parent, active, inactive):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = self.parent.deck
        self.active = active
        self.inactive = inactive
        self.dialog = ankiqt.forms.activetags.Ui_Dialog()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.rebuildTagList()
        restoreGeom(self, "activeTags")

    def rebuildTagList(self):
        usertags = self.deck.allTags()
        self.items = []
        self.suspended = {}
        yes = parseTags(self.deck.getVar(self.active))
        no = parseTags(self.deck.getVar(self.inactive))
        yesHash = {}
        noHash = {}
        for y in yes:
            yesHash[y] = True
        for n in no:
            noHash[n] = True
        groupedTags = []
        usertags.sort()
        # render models and templates
        for (type, sql, icon) in (
            ("models", "select tags from models", "contents.png"),
            ("cms", "select name from cardModels", "Anki_Card.png")):
            d = {}
            tagss = self.deck.s.column0(sql)
            for tags in tagss:
                for tag in parseTags(tags):
                    d[tag] = 1
            sortedtags = sorted(d.keys())
            icon = QIcon(":/icons/" + icon)
            groupedTags.append([icon, sortedtags])
        # remove from user tags
        for tag in groupedTags[0][1] + groupedTags[1][1]:
            try:
                usertags.remove(tag)
            except:
                pass
        # user tags
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
                    self.dialog.inactiveCheck.setChecked(True)
                else:
                    mode = QItemSelectionModel.Deselect
                idx = self.dialog.inactiveList.indexFromItem(item)
                self.dialog.inactiveList.selectionModel().select(idx, mode)

    def accept(self):
        self.hide()
        n = 0
        yes = []
        no = []
        for c in range(self.dialog.activeList.count()):
            # active
            item = self.dialog.activeList.item(c)
            idx = self.dialog.activeList.indexFromItem(item)
            if self.dialog.activeList.selectionModel().isSelected(idx):
                yes.append(self.tags[c])
            # inactive
            item = self.dialog.inactiveList.item(c)
            idx = self.dialog.inactiveList.indexFromItem(item)
            if self.dialog.inactiveList.selectionModel().isSelected(idx):
                no.append(self.tags[c])

        if self.dialog.activeCheck.isChecked():
            self.deck.setVar(self.active, joinTags(yes))
        else:
            self.deck.setVar(self.active, "")
        if self.dialog.inactiveCheck.isChecked():
            self.deck.setVar(self.inactive, joinTags(no))
        else:
            self.deck.setVar(self.inactive, "")
        self.parent.reset()
        saveGeom(self, "activeTags")
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "SelectiveStudy"))

def show(parent, active, inactive):
    at = ActiveTagsChooser(parent, active, inactive)
    at.exec_()
