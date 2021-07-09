# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Optional

import aqt.editor
from anki.collection import OpChanges
from anki.errors import NotFoundError
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, tr


class EditCurrent(QDialog):
    def __init__(self, mw: aqt.AnkiQt) -> None:
        QDialog.__init__(self, None, Qt.Window)
        mw.garbage_collect_on_dialog_finish(self)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr.editing_edit_current())
        disable_help_button(self)
        self.setMinimumHeight(400)
        self.setMinimumWidth(250)
        self.form.buttonBox.button(QDialogButtonBox.Close).setShortcut(
            QKeySequence("Ctrl+Return")
        )
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea, self)
        self.editor.card = self.mw.reviewer.card
        self.editor.set_note(self.mw.reviewer.card.note(), focusTo=0)
        restoreGeom(self, "editcurrent")
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        gui_hooks.card_will_show.append(self.refresh_editor_on_review)
        gui_hooks.reviewer_will_end.append(self.saveAndClose)
        self.show()

    def on_operation_did_execute(
        self, changes: OpChanges, handler: Optional[object]
    ) -> None:
        if changes.note_text and handler is not self.editor:
            # reload note
            note = self.editor.note
            try:
                note.load()
            except NotFoundError:
                # note's been deleted
                self.cleanup_and_close()
                return

            self.editor.set_note(note)

    def refresh_editor_on_review(self, qa: str, card, type: str) -> None:
        if type.startswith("review"):
            self.editor.setNote(card.note())
        return qa

    def cleanup_and_close(self) -> None:
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)
        gui_hooks.card_will_show.remove(self.refresh_editor_on_review)
        gui_hooks.reviewer_will_end.remove(self.saveAndClose)
        self.editor.cleanup()
        saveGeom(self, "editcurrent")
        aqt.dialogs.markClosed("EditCurrent")
        QDialog.reject(self)

    def reopen(self, mw: aqt.AnkiQt) -> None:
        self.editor.web.setFocus()

    def reject(self) -> None:
        self.saveAndClose()

    def saveAndClose(self) -> None:
        self.editor.call_after_note_saved(self._saveAndClose)

    def _saveAndClose(self) -> None:
        self.cleanup_and_close()

    def closeWithCallback(self, onsuccess: Callable[[], None]) -> None:
        def callback() -> None:
            self._saveAndClose()
            onsuccess()

        self.editor.call_after_note_saved(callback)

    onReset = on_operation_did_execute
