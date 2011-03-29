# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt
from aqt.utils import showInfo, getOnlyText

COLNAME = 0
COLCHECK = 1
COLCOUNT = 2
COLDUE = 3
COLNEW = 4
GREY = "#777"

class GroupManager(QDialog):
    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.groupman.Ui_Dialog()
        self.form.setupUi(self)
        self.loadTable()
        self.addButtons()
        self.exec_()

    def reload(self):
        self.mw.progress.start()
        grps = self.mw.deck.sched.groupCountTree()
        self.mw.progress.finish()
        self.groupMap = {}
        self.fullNames = {}
        items = self._makeItems(grps)
        self.form.tree.clear()
        self.form.tree.addTopLevelItems(items)
        self.items = items
        self.form.tree.expandAll()

    def loadTable(self):
        self.reload()
        # default to check column
        self.form.tree.setCurrentItem(self.items[0], 1)
        # config tree
        h = self.form.tree.header()
        h.setResizeMode(QHeaderView.ResizeToContents)
        h.setResizeMode(0, QHeaderView.Stretch)
        h.setMovable(False)
        self.form.tree.setIndentation(15)

    def addButtons(self):
        box = self.form.buttonBox
        def button(name, func, type=QDialogButtonBox.ActionRole):
            b = box.addButton(name, type)
            b.connect(b, SIGNAL("clicked()"), func)
            return b
        # exits
        button(_("&Study"), self.onStudy, QDialogButtonBox.AcceptRole)
        button(_("&Cram"), self.onCram, QDialogButtonBox.AcceptRole)
        # selection
        button(_("Select &All"), self.onSelectAll)
        button(_("Select &None"), self.onSelectNone)
        button(_("&Rename..."), self.onRename)
        button(_("&Config..."), self.onEdit)
        self.connect(box,
                     SIGNAL("helpRequested()"),
                     lambda: QDesktopServices.openUrl(QUrl(
                         aqt.appWiki + "GroupManager")))

    def onStudy(self):
        self.mw.deck.reset()
        self.mw.moveToState("review")

    def onCram(self):
        self.mw.deck.cramGroups(self.mw.deck.qconf['groups'])
        self.mw.moveToState("review")

    def onSelectAll(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Checked)

    def onSelectNone(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Unchecked)

    def onRename(self):
        item = self.form.tree.currentItem()
        old = unicode(item.text(0))
        oldfull = self.fullNames[old]
        gid = self.groupMap[old]
        # if not gid:
        #     showInfo(_("Selected item is not a group."))
        #     return
        txt = getOnlyText(_("Rename to:"), self, default=oldfull)
        if txt and not txt.startswith("::") and not txt.endswith("::"):
            self._rename(oldfull, txt, gid, item)

    def _rename(self, old, txt, gid, item):
        def updateChild(item):
            cold = unicode(item.text(0))
            gid = self.groupMap[cold]
            cnew = self.fullNames[cold].replace(old, txt)
            if gid:
                self.mw.deck.db.execute(
                    "update groups set name = ? where id = ?",
                    cnew, gid)
            for i in range(item.childCount()):
                updateChild(item.child(i))
        updateChild(item)
        self.reload()

    def onEdit(self):
        gids = []
        for item in self.form.tree.selectedItems():
            gid = self.groupMap[unicode(item.text(0))]
            if gid:
                gids.append(gid)
        if gids:
            from aqt.groupconfsel import GroupConfSelector
            GroupConfSelector(self.mw, gids)
        else:
            showInfo(_("None of the selected items are a group."))

    def reject(self):
        self.accept()

    def accept(self):
        gids = []
        def findEnabled(item):
            if item.checkState(1) == Qt.Checked:
                gid = self.groupMap[unicode(item.text(0))]
                if gid:
                    gids.append(gid)
            for i in range(item.childCount()):
                findEnabled(item.child(i))
        for item in self.items:
            findEnabled(item)
        if len(gids) == self.gidCount:
            # all enabled is same as empty
            gids = []
        self.mw.deck.qconf['groups'] = gids
        QDialog.accept(self)

    def _makeItems(self, grps):
        self.gidCount = 0
        on = {}
        if not self.mw.deck.qconf['groups']:
            on = None
        else:
            for gid in self.mw.deck.qconf['groups']:
                on[gid] = True
        grey = QBrush(QColor(GREY))
        def makeItems(grp, head=""):
            branch = QTreeWidgetItem()
            branch.setFlags(
                Qt.ItemIsUserCheckable|Qt.ItemIsEnabled|Qt.ItemIsSelectable|
                Qt.ItemIsTristate)
            gid = grp[1]
            if not gid:
                branch.setForeground(0, grey)
            if not on or gid in on:
                branch.setCheckState(1, Qt.Checked)
            else:
                branch.setCheckState(1, Qt.Unchecked)
            branch.setText(COLNAME, grp[0])
            branch.setText(COLCOUNT, str(grp[2]))
            branch.setText(COLDUE, str(grp[3]))
            branch.setText(COLNEW, str(grp[4]))
            self.groupMap[grp[0]] = grp[1]
            self.fullNames[grp[0]] = head+grp[0]
            if grp[1]:
                self.gidCount += 1
            if grp[5]:
                for c in grp[5]:
                    branch.addChild(makeItems(c, head+grp[0]+"::"))
            return branch
        top = [makeItems(g) for g in grps]
        return top
