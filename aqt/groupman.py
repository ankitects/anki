# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt
from aqt.utils import showInfo, getOnlyText

COLNAME = 0
COLOPTS = 1
COLCHECK = 2
COLCOUNT = 3
COLDUE = 4
COLNEW = 5
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
        # default to check column
        self.form.tree.setCurrentItem(self.items[0], COLCHECK)

    def loadTable(self):
        self.reload()
        # config tree
        h = self.form.tree.header()
        h.setResizeMode(COLNAME, QHeaderView.Stretch)
        h.setResizeMode(COLOPTS, QHeaderView.ResizeToContents)
        h.setResizeMode(COLCHECK, QHeaderView.ResizeToContents)
        h.resizeSection(COLCOUNT, 70)
        h.resizeSection(COLDUE, 70)
        h.resizeSection(COLNEW, 70)
        h.setMovable(False)
        self.form.tree.setIndentation(15)
        self.connect(self.form.tree,
                     SIGNAL("itemDoubleClicked(QTreeWidgetItem*, int)"),
                     self.onDoubleClick)

    def onDoubleClick(self, item, col):
        if not item:
            return
        if col == COLNAME:
            self.onRename()
        if col == COLOPTS:
            self.onEdit()

    def addButtons(self):
        box = self.form.buttonBox
        def button(w, func):
            w.connect(w, SIGNAL("clicked()"), func)
            return w
        f = self.form
        # selection
        button(f.selAll, self.onSelectAll).setShortcut("a")
        button(f.selNone, self.onSelectNone).setShortcut("n")
        button(f.opts, self.onEdit).setShortcut("o")
        button(f.rename, self.onRename).setShortcut("r")
        button(f.delete_2, self.onDelete)
        self.connect(self.form.buttonBox,
                     SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("GroupManager"))

    def onSelectAll(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Checked)

    def onSelectNone(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Unchecked)

    def onDelete(self):
        item = self.form.tree.currentItem()
        old = unicode(item.text(0))
        gid = self.groupMap[old]
        if not gid:
            showInfo(_("Selected item is not a group."))
            return
        elif gid == 1:
            showInfo(_("The default group can't be deleted."))
            return
        self.mw.checkpoint(_("Delete Group"))
        self.mw.deck.db.execute(
            "update cards set gid = 1 where gid = ?", gid)
        self.mw.deck.db.execute(
            "delete from groups where id = ?", gid)
        self.reload()

    def onRename(self):
        item = self.form.tree.currentItem()
        old = unicode(item.text(0))
        oldfull = self.fullNames[old]
        gid = self.groupMap[old]
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
            # this gets set on reload; do it in the background so it doesn't flicker
            self.form.tree.setCurrentItem(self.items[0], COLCHECK)
            from aqt.groupconf import GroupConfSelector
            GroupConfSelector(self.mw, gids, self)
            self.reload()
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
        if gids != self.mw.deck.qconf['groups']:
            self.mw.deck.qconf['groups'] = gids
            self.mw.deck.reset()
            self.mw.moveToState("review")
        QDialog.accept(self)

    def _makeItems(self, grps):
        self.gidCount = 0
        on = {}
        if not self.mw.deck.qconf['groups']:
            on = None
        else:
            for gid in self.mw.deck.qconf['groups']:
                on[gid] = True
        self.confMap = dict(self.mw.deck.db.all(
            "select g.id, gc.name from groups g, gconf gc where g.gcid=gc.id"))
        grey = QBrush(QColor(GREY))
        def makeItems(grp, head=""):
            branch = QTreeWidgetItem()
            branch.setFlags(
                Qt.ItemIsUserCheckable|Qt.ItemIsEnabled|Qt.ItemIsSelectable|
                Qt.ItemIsTristate)
            gid = grp[1]
            if not gid:
                branch.setForeground(COLNAME, grey)
            if not on or gid in on:
                branch.setCheckState(COLCHECK, Qt.Checked)
            else:
                branch.setCheckState(COLCHECK, Qt.Unchecked)
            branch.setText(COLNAME, grp[0])
            if gid:
                branch.setText(COLOPTS, self.confMap[gid])
            branch.setText(COLCOUNT, str(grp[2]))
            branch.setText(COLDUE, str(grp[3]))
            branch.setText(COLNEW, str(grp[4]))
            for i in (COLCOUNT, COLDUE, COLNEW):
                branch.setTextAlignment(i, Qt.AlignRight)
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
