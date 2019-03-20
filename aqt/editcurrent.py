# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import tooltip
import aqt.editor
from aqt.utils import saveGeom, restoreGeom
from anki.hooks import addHook, remHook
from anki.utils import isMac
from anki.lang import _

class EditCurrent(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, None, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Edit Current"))
        self.setMinimumHeight(400)
        self.setMinimumWidth(500)
        self.form.buttonBox.button(QDialogButtonBox.Close).setShortcut(
                QKeySequence("Ctrl+Return"))
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self)
        self.editor.card = self.mw.reviewer.card
        self.editor.setNote(self.mw.reviewer.card.note(), focusTo=0)
        restoreGeom(self, "editcurrent")
        addHook("reset", self.onReset)
        self.mw.requireReset()
        self.show()
        # reset focus after open, taking care not to retain webview
        # pylint: disable=unnecessary-lambda
        self.mw.progress.timer(100, lambda: self.editor.web.setFocus(), False)

    def onReset(self):
        # lazy approach for now: throw away edits
        try:
            n = self.editor.note
            n.load()#reload in case the model changed
        except:
            # card's been deleted
            remHook("reset", self.onReset)
            self.editor.setNote(None)
            self.mw.reset()
            aqt.dialogs.markClosed("EditCurrent")
            self.close()
            return
        self.editor.setNote(n)

    def reopen(self,mw):
        tooltip("Please finish editing the existing card first.")
        self.onReset()
        
    def reject(self):
        self.saveAndClose()

    def saveAndClose(self):
        self.editor.saveNow(self._saveAndClose)

    def _saveAndClose(self):
        remHook("reset", self.onReset)
        r = self.mw.reviewer
        try:
            r.card.load()
        except:
            # card was removed by clayout
            pass
        else:
            self.mw.reviewer.cardQueue.append(self.mw.reviewer.card)
        self.editor.cleanup()
        self.mw.moveToState("review")
        saveGeom(self, "editcurrent")
        aqt.dialogs.markClosed("EditCurrent")
        QDialog.reject(self)

    def closeWithCallback(self, onsuccess):
        def callback():
            self._saveAndClose()
            onsuccess()
        self.editor.saveNow(callback)
