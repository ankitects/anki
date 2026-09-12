# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from collections.abc import Callable

from aqt.editcurrent_legacy import *
from aqt.qt import *
from aqt.utils import restoreGeom, saveGeom, tr


class NewEditCurrent(QMainWindow):
    def __init__(self, mw: aqt.AnkiQt) -> None:
        super().__init__(None, Qt.WindowType.Window)
        self.mw = mw
        self.form = aqt.forms.editcurrent.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr.editing_edit_current())
        self.setMinimumHeight(400)
        self.setMinimumWidth(250)
        if not is_mac:
            self.setMenuBar(None)
        self.editor = aqt.editor.NewEditor(
            self.mw,
            self.form.fieldsArea,
            self,
            editor_mode=aqt.editor.EditorMode.EDIT_CURRENT,
        )
        assert self.mw.reviewer.card is not None
        self.editor.card = self.mw.reviewer.card
        self.editor.set_note(self.mw.reviewer.card.note(), focusTo=0)
        restoreGeom(self, "editcurrent")
        self.show()

    def cleanup(self) -> None:
        self.editor.cleanup()
        saveGeom(self, "editcurrent")
        aqt.dialogs.markClosed("NewEditCurrent")

    def reopen(self, mw: aqt.AnkiQt) -> None:
        if card := self.mw.reviewer.card:
            self.editor.card = card
            self.editor.set_note(card.note())

    def closeEvent(self, evt: QCloseEvent | None) -> None:
        self.editor.call_after_note_saved(self.cleanup)

    def _saveAndClose(self) -> None:
        self.cleanup()
        self.mw.deferred_delete_and_garbage_collect(self)
        self.close()

    def closeWithCallback(self, onsuccess: Callable[[], None]) -> None:
        def callback() -> None:
            self._saveAndClose()
            onsuccess()

        self.editor.call_after_note_saved(callback)
