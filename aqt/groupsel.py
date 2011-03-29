# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt

COLNAME = 0
COLCHECK = 1
COLCOUNT = 2
COLDUE = 3
COLNEW = 4

class GroupSel(QDialog):
    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.groupsel.Ui_Dialog()
        self.form.setupUi(self)
        self.loadTable()
        self.addButtons()
        self.exec_()

    def loadTable(self):
        # load the data into the tree
        self.mw.progress.start()
        grps = self.mw.deck.sched.groupCountTree()
        self.mw.progress.finish()
        self._groupMap = {}
        items = self._makeItems(grps)
        self.form.tree.addTopLevelItems(items)
        # default to check column
        self.form.tree.setCurrentItem(items[0], 1)
        self.items = items
        # config tree
        h = self.form.tree.header()
        h.setResizeMode(QHeaderView.ResizeToContents)
        h.setResizeMode(0, QHeaderView.Stretch)
        h.setMovable(False)
        self.form.tree.setIndentation(15)
        self.form.tree.expandAll()

    def addButtons(self):
        box = self.form.buttonBox
        def button(name, func):
            b = box.addButton(name, QDialogButtonBox.ActionRole)
            b.connect(b, SIGNAL("clicked()"), func)
        button(_("Select &All"), self.onSelectAll)
        button(_("Select &None"), self.onSelectNone)
        button(_("&Edit..."), self.onEdit)
        self.connect(box,
                     SIGNAL("helpRequested()"),
                     lambda: QDesktopServices.openUrl(QUrl(
                         aqt.appWiki + "GroupSelection")))

    def onSelectAll(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Checked)

    def onSelectNone(self):
        for i in self.items:
            i.setCheckState(COLCHECK, Qt.Unchecked)

    def onEdit(self):
        item = self.form.tree.currentItem()
        gid = self._groupMap[unicode(item.text(0))]
        print "edit", gid

    def _makeItems(self, grps):
        if not self.mw.deck.qconf['groups']:
            on = None
        else:
            for gid in self.mw.deck.qconf['groups']:
                on[gid] = True
        def makeItems(grp):
            branch = QTreeWidgetItem()
            branch.setFlags(
                Qt.ItemIsUserCheckable|Qt.ItemIsEnabled|Qt.ItemIsSelectable|
                Qt.ItemIsTristate)
            if not on or on[gid]:
                branch.setCheckState(1, Qt.Checked)
            else:
                branch.setCheckState(1, Qt.Unchecked)
            branch.setText(COLNAME, grp[0])
            branch.setText(COLCOUNT, str(grp[2]))
            branch.setText(COLDUE, str(grp[3]))
            branch.setText(COLNEW, str(grp[4]))
            self._groupMap[grp[0]] = grp[1]
            if grp[5]:
                for c in grp[5]:
                    branch.addChild(makeItems(c))
            return branch
        top = [makeItems(g) for g in grps]
        return top
