# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import aqt.editor
from aqt.utils import saveGeom, restoreGeom
from anki.hooks import addHook, remHook
from anki.utils import isMac

class EditCurrent(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, None, Qt.Window)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Edit Current"))
        self.setMinimumHeight(400)
        self.setMinimumWidth(500)
        self.connect(self,
                     SIGNAL("rejected()"),
                     self.onSave)
        self.form.buttonBox.button(QDialogButtonBox.Close).setShortcut(
                QKeySequence("Ctrl+Return"))
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self)
        self.editor.setNote(self.mw.reviewer.card.note())
        restoreGeom(self, "editcurrent")
        addHook("reset", self.onReset)
        self.mw.requireReset()
        self.show()
        # reset focus after open
        self.editor.web.setFocus()

    def onReset(self):
        # lazy approach for now: throw away edits
        try:
            n = self.mw.reviewer.card.note()
            n.load()
        except:
            # card's been deleted
            remHook("reset", self.onReset)
            self.editor.setNote(None)
            self.mw.reset()
            aqt.dialogs.close("EditCurrent")
            self.close()
            return
        self.editor.setNote(n)

    def onSave(self):
        remHook("reset", self.onReset)
        self.editor.saveNow()
        r = self.mw.reviewer
        try:
            r.card.load()
        except:
            # card was removed by clayout
            pass
        else:
            self.mw.reviewer.cardQueue.append(self.mw.reviewer.card)
        self.mw.moveToState("review")
        saveGeom(self, "editcurrent")
        aqt.dialogs.close("EditCurrent")

    def canClose(self):
        return True
