# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt
from anki.utils import parseTags, joinTags, canonifyTags
from ankiqt.ui.utils import saveGeom, restoreGeom

class ActiveTagsChooser(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = self.parent.deck
        self.dialog = ankiqt.forms.activetags.Ui_Dialog()
        self.dialog.setupUi(self)
        self.selectAll = QPushButton(_("Select All"))
        self.connect(self.selectAll, SIGNAL("clicked()"), self.onSelectAll)
        self.dialog.buttonBox.addButton(self.selectAll,
                                        QDialogButtonBox.ActionRole)
        self.selectNone = QPushButton(_("Select None"))
        self.connect(self.selectNone, SIGNAL("clicked()"), self.onSelectNone)
        self.dialog.buttonBox.addButton(self.selectNone,
                                        QDialogButtonBox.ActionRole)
        self.invert = QPushButton(_("Invert"))
        self.connect(self.invert, SIGNAL("clicked()"), self.onInvert)
        self.dialog.buttonBox.addButton(self.invert,
                                        QDialogButtonBox.ActionRole)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.rebuildTagList()
        restoreGeom(self, "activeTags")

    def onSelectAll(self):
        self.dialog.list.selectAll()

    def onSelectNone(self):
        self.dialog.list.clearSelection()

    def onInvert(self):
        sm = self.dialog.list.selectionModel()
        sel = sm.selection()
        self.dialog.list.selectAll()
        sm.select(sel, QItemSelectionModel.Deselect)

    def rebuildTagList(self):
        self.tags = self.deck.allTags()
        self.items = []
        self.suspended = {}
        alltags = []
        # get list of currently suspended
        for t in parseTags(self.deck.suspended):
            if t == "Suspended":
                continue
            self.suspended[t] = 1
            if t not in self.tags:
                self.tags.append(t)
        # sort and remove special 'Suspended' tag
        self.tags.sort()
        try:
            self.tags.remove("Suspended")
        except ValueError:
            pass
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
            alltags.extend(sortedtags)
            icon = QIcon(":/icons/" + icon)
            for t in sortedtags:
                item = QListWidgetItem(icon, t.replace("_", " "))
                self.dialog.list.addItem(item)
                self.items.append(item)
                idx = self.dialog.list.indexFromItem(item)
                if t in self.suspended:
                    mode = QItemSelectionModel.Select
                else:
                    mode = QItemSelectionModel.Deselect
                self.dialog.list.selectionModel().select(idx, mode)
        # remove from user tags
        for tag in alltags + ["Suspended"]:
            try:
                self.tags.remove(tag)
            except:
                pass
        # user tags
        icon = QIcon(":/icons/Anki_Fact.png")
        for t in self.tags:
            item = QListWidgetItem(icon, t.replace("_", " "))
            self.dialog.list.addItem(item)
            self.items.append(item)
            idx = self.dialog.list.indexFromItem(item)
            if t in self.suspended:
                mode = QItemSelectionModel.Select
            else:
                mode = QItemSelectionModel.Deselect
            self.dialog.list.selectionModel().select(idx, mode)
        self.tags = alltags + self.tags

    def accept(self):
        self.hide()
        self.deck.startProgress()
        n = 0
        suspended = []
        for item in self.items:
            idx = self.dialog.list.indexFromItem(item)
            if self.dialog.list.selectionModel().isSelected(idx):
                suspended.append(self.tags[n])
            n += 1
        self.deck.suspended = canonifyTags(joinTags(suspended + ["Suspended"]))
        self.deck.setModified()
        self.deck.updateAllPriorities(partial=True, dirty=False)
        self.parent.reset()
        saveGeom(self, "activeTags")
        self.deck.finishProgress()
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "ActiveTags"))

def show(parent):
    at = ActiveTagsChooser(parent)
    at.exec_()
