# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtCore import *
from PyQt4.QtGui import *
import aqt
from aqt.utils import showInfo

class GroupConfSelector(QDialog):
    def __init__(self, mw, gids):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.gids = gids
        self.form = aqt.forms.groupconfsel.Ui_Dialog()
        self.form.setupUi(self)
        self.load()
        self.addButtons()
        self.exec_()

    def load(self):
        self.confs = self.mw.deck.groupConfs()
        for c in self.confs:
            item = QListWidgetItem(c[0])
            item.setFlags(item.flags() | Qt.ItemIsEditable)
            self.form.list.addItem(item)
        self.connect(self.form.list, SIGNAL("itemChanged(QListWidgetItem*)"),
                     self.onRename)

    def addButtons(self):
        box = self.form.buttonBox
        def button(name, func, type=QDialogButtonBox.ActionRole):
            b = box.addButton(name, type)
            b.connect(b, SIGNAL("clicked()"), func)
            return b
        button(_("Edit"), self.onEdit)
        button(_("Copy"), self.onCopy)
        button(_("Delete"), self.onDelete)

    def idx(self):
        return self.form.list.currentRow()

    def onRename(self, item):
        idx = self.idx()
        id = self.confs[idx][1]
        self.mw.deck.db.execute("update gconf set name = ? where id = ?",
                                unicode(item.text()), id)

    def onEdit(self):
        pass

    def onCopy(self):
        pass

    def onDelete(self):
        idx = self.form.list.currentRow()
        if self.confs[idx][1] == 1:
            showInfo(_("The default configuration can't be removed."))
            return

