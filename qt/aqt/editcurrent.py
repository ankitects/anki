# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import aqt.editor
from aqt import gui_hooks
from aqt.main import ResetReason
from aqt.qt import *
from aqt.utils import TR, disable_help_button, restoreGeom, saveGeom, tooltip, tr


class EditCurrent(QDialog):
    def __init__(self, mw: aqt.AnkiQt) -> None:
        QDialog.__init__(self, None, Qt.Window)
        mw.setupDialogGC(self)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr(TR.EDITING_EDIT_CURRENT))
        disable_help_button(self)
        self.setMinimumHeight(400)
        self.setMinimumWidth(250)
        self.form.buttonBox.button(QDialogButtonBox.Close).setShortcut(
            QKeySequence("Ctrl+Return")
        )
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self)
        self.editor.card = self.mw.reviewer.card
        self.editor.setNote(self.mw.reviewer.card.note(), focusTo=0)
        restoreGeom(self, "editcurrent")
        gui_hooks.state_did_reset.append(self.onReset)
        self.mw.requireReset(reason=ResetReason.EditCurrentInit, context=self)
        self.show()
        # reset focus after open, taking care not to retain webview
        # pylint: disable=unnecessary-lambda
        self.mw.progress.timer(100, lambda: self.editor.web.setFocus(), False)

    def onReset(self) -> None:
        # lazy approach for now: throw away edits
        try:
            n = self.editor.note
            n.load()  # reload in case the model changed
        except:
            # card's been deleted
            gui_hooks.state_did_reset.remove(self.onReset)
            self.editor.setNote(None)
            self.mw.reset()
            aqt.dialogs.markClosed("EditCurrent")
            self.close()
            return
        self.editor.setNote(n)

    def reopen(self, mw: aqt.AnkiQt) -> None:
        tooltip("Please finish editing the existing card first.")
        self.onReset()

    def reject(self) -> None:
        self.saveAndClose()

    def saveAndClose(self) -> None:
        self.editor.saveNow(self._saveAndClose)

    def _saveAndClose(self) -> None:
        gui_hooks.state_did_reset.remove(self.onReset)
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

    def closeWithCallback(self, onsuccess: Callable[[], None]) -> None:
        def callback() -> None:
            self._saveAndClose()
            onsuccess()

        self.editor.saveNow(callback)
