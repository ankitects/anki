# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import aqt.editor
from aqt.utils import saveGeom, restoreGeom

class EditCurrent(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Edit Current"))
        self.setMinimumHeight(400)
        self.setMinimumWidth(500)
        self.connect(self.form.buttonBox.button(QDialogButtonBox.Save),
                     SIGNAL("clicked()"),
                     self.onSave)
        self.connect(self,
                     SIGNAL("rejected()"),
                     self.onSave)
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea)
        self.editor.setFact(self.mw.reviewer.card.fact())
        restoreGeom(self, "editcurrent")
        self.mw.requireReset(modal=True)
        self.open()
        # reset focus after open
        self.editor.web.setFocus()

    def onSave(self):
        self.editor.saveNow()
        self.editor.setFact(None)
        r = self.mw.reviewer
        r.card.load()
        r.keep = True
        self.mw.moveToState("review")
        saveGeom(self, "editcurrent")
        self.close()
