# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from typing import Optional

import aqt.editor
from anki.collection import OpChanges
from anki.errors import NotFoundError
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    disable_help_button,
    is_image_occlusion_notetype,
    restoreGeom,
    saveGeom,
    tr,
)


class EditCurrent(QDialog):
    def __init__(self, mw: aqt.AnkiQt) -> None:
        QDialog.__init__(self, None, Qt.WindowType.Window)
        mw.garbage_collect_on_dialog_finish(self)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr.editing_edit_current())
        disable_help_button(self)
        self.setMinimumHeight(400)
        self.setMinimumWidth(250)
        self.editor = aqt.editor.Editor(
            self.mw,
            self.form.fieldsArea,
            self,
            editor_mode=aqt.editor.EditorMode.EDIT_CURRENT,
        )
        self.editor.card = self.mw.reviewer.card
        self.editor.set_note(self.mw.reviewer.card.note(), focusTo=0)
        restoreGeom(self, "editcurrent")
        self.setupButtons()
        gui_hooks.operation_did_execute.append(self.on_operation_did_execute)
        self.show()

    def setupButtons(self) -> None:
        if is_image_occlusion_notetype(self.editor):
            bb = self.form.buttonBox
            ar = QDialogButtonBox.ButtonRole.ActionRole
            # add io hide all button
            self.addButtonHideAll = bb.addButton(tr.notetypes_hide_all_guess_one(), ar)
            qconnect(self.addButtonHideAll.clicked, self.update_io_hide_all_note)
            self.addButtonHideAll.setShortcut(QKeySequence("Ctrl+Return+A"))
            # add io hide one button
            self.addButtonHideOne = bb.addButton(tr.notetypes_hide_one_guess_one(), ar)
            qconnect(self.addButtonHideOne.clicked, self.update_io_hide_one_note)
            self.addButtonHideOne.setShortcut(QKeySequence("Ctrl+Return+O"))

        self.form.buttonBox.button(QDialogButtonBox.StandardButton.Close).setShortcut(
            QKeySequence("Ctrl+Return")
        )

    def update_io_hide_all_note(self) -> None:
        self.editor.web.eval("setOcclusionField(true)")

    def update_io_hide_one_note(self) -> None:
        self.editor.web.eval("setOcclusionField(false)")

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

    def cleanup_and_close(self) -> None:
        gui_hooks.operation_did_execute.remove(self.on_operation_did_execute)
        self.editor.cleanup()
        saveGeom(self, "editcurrent")
        aqt.dialogs.markClosed("EditCurrent")
        QDialog.reject(self)

    def reopen(self, mw: aqt.AnkiQt) -> None:
        if card := self.mw.reviewer.card:
            self.editor.set_note(card.note())

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
