# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt

class GroupSel(QDialog):
    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.groupsel.Ui_Dialog()
        self.form.setupUi(self)
        self.load()
        # self.connect(self.form.optionsHelpButton,
        #              SIGNAL("clicked()"),
        #              lambda: QDesktopServices.openUrl(QUrl(
        #     aqt.appWiki + "StudyOptions")))
        self.exec_()

    def load(self):
        import time
        self.mw.progress.start()
        grps = self.mw.deck.sched.groupCountTree()
        self.mw.progress.finish()
        self._groupMap = {}
        items = self._makeItems(grps)
        self.form.tree.addTopLevelItems(items)
        for item in items:
            self._addButtons(item)
        h = self.form.tree.header()
        h.setResizeMode(QHeaderView.ResizeToContents)
        h.setResizeMode(0, QHeaderView.Stretch)
        h.setMovable(False)
        self.form.tree.setIndentation(15)
        self.form.tree.expandAll()

    def _addButtons(self, item):
        gid = self._groupMap[unicode(item.text(0))]
        if gid:
            b = QPushButton("Edit")
            b.setFixedHeight(20)
            b.connect(b, SIGNAL("clicked()"), lambda g=gid: self._edit(gid))
            self.form.tree.setItemWidget(item, 4, b)
        for i in range(item.childCount()):
            self._addButtons(item.child(i))

    def _edit(self, gid):
        print "edit", gid

    def _makeItems(self, grps):
        def makeItems(grp):
            branch = QTreeWidgetItem()
            branch.setText(0, grp[0])
            branch.setText(1, str(grp[2]))
            branch.setText(2, str(grp[3]))
            branch.setText(3, str(grp[4]))
            self._groupMap[grp[0]] = grp[1]
            if grp[5]:
                for c in grp[5]:
                    branch.addChild(makeItems(c))
            return branch
        top = [makeItems(g) for g in grps]
        return top
