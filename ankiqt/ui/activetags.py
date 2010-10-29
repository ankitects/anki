# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt
from anki.utils import parseTags, joinTags, canonifyTags
from ankiqt.ui.utils import saveGeom, restoreGeom

class ActiveTagsChooser(QDialog):

    def __init__(self, parent, active):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = self.parent.deck
        self.active = active
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
        yesHash = {}
        for y in yes:
            yesHash[y] = True
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
        if self.dialog.activeCheck.isChecked():
            self.deck.setVar(self.active, joinTags(yes))
        else:
            self.deck.setVar(self.active, "")
        self.parent.reset()
        saveGeom(self, "activeTags")
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "ActiveTags"))

def show(parent, active):
    at = ActiveTagsChooser(parent, active)
    at.exec_()
