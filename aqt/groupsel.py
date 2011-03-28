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
        t = time.time()
        grps = self.mw.deck.sched.groupCountTree()
        print "groups", time.time() - t
        items = self._makeItems(grps)
        self.form.tree.addTopLevelItems(items)

    def _makeItems(self, grps):
        def makeItems(grp):
            branch = QTreeWidgetItem()
            branch.setText(0, grp[0])
            branch.setText(1, str(grp[1]))
            branch.setText(2, str(grp[2]))
            branch.setText(3, str(grp[3]))
            if grp[4]:
                for c in grp[4]:
                    branch.addChild(makeItems(c))
            return branch
        top = [makeItems(g) for g in grps]
        return top
