# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable

from anki.decks import DeckId
from anki.notes import Note
from anki.utils import is_mac
from aqt import AnkiQt, gui_hooks
from aqt.addcards_legacy import *
from aqt.qt import *
from aqt.utils import (
    HelpPage,
    add_close_shortcut,
    ask_user_dialog,
    openHelp,
    restoreGeom,
    saveGeom,
    tr,
)


class NewAddCards(QMainWindow):
    def __init__(self, mw: AnkiQt) -> None:
        super().__init__(None, Qt.WindowType.Window)
        self._close_event_has_cleaned_up = False
        self._close_callback: Callable[[], None] = self._close
        self.mw = mw
        self.col = mw.col
        form = aqt.forms.addcards.Ui_Dialog()
        form.setupUi(self)
        self.form = form
        self.setWindowTitle(tr.actions_add())
        self.setMinimumHeight(300)
        self.setMinimumWidth(400)
        self.setupEditor()
        add_close_shortcut(self)
        self._load_new_note()
        restoreGeom(self, "add")
        gui_hooks.add_cards_did_init(self)
        if not is_mac:
            self.setMenuBar(None)
        self.show()

    def set_note(self, note: Note, deck_id: DeckId | None = None) -> None:
        """Set tags, field contents and notetype according to `note`. Deck is set
        to `deck_id` or the deck last used with the notetype.
        """
        self.editor.load_note(
            mid=note.mid,
            original_note_id=note.id,
            focus_to=0,
        )

    def setupEditor(self) -> None:
        self.editor = aqt.editor.NewEditor(
            self.mw,
            self.form.fieldsArea,
            self,
            editor_mode=aqt.editor.EditorMode.ADD_CARDS,
        )

    def reopen(self, mw: AnkiQt) -> None:
        self.editor.reload_note_if_empty()

    def helpRequested(self) -> None:
        openHelp(HelpPage.ADDING_CARD_AND_NOTE)

    def _load_new_note(self) -> None:
        self.editor.load_note(
            mid=self.mw.col.models.current()["id"],
            focus_to=0,
        )

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key.Key_Escape:
            self.close()
        else:
            super().keyPressEvent(evt)

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._close_event_has_cleaned_up:
            evt.accept()
            return
        self.ifCanClose(self._close)
        evt.ignore()

    def _close(self) -> None:
        self.editor.cleanup()
        self.mw.maybeReset()
        saveGeom(self, "add")
        aqt.dialogs.markClosed("NewAddCards")
        self._close_event_has_cleaned_up = True
        self.mw.deferred_delete_and_garbage_collect(self)
        self.close()

    def ifCanClose(self, onOk: Callable) -> None:
        self._close_callback = onOk
        self.editor.web.eval("closeAddCards()")

    def _close_if_user_wants_to_discard_changes(self, prompt: bool) -> None:
        if not prompt:
            self._close_callback()
            return

        def callback(choice: int) -> None:
            if choice == 0:
                self._close_callback()

        ask_user_dialog(
            tr.adding_discard_current_input(),
            callback=callback,
            buttons=[
                QMessageBox.StandardButton.Discard,
                (tr.adding_keep_editing(), QMessageBox.ButtonRole.RejectRole),
            ],
        )

    def closeWithCallback(self, cb: Callable[[], None]) -> None:
        def doClose() -> None:
            self._close()
            cb()

        self.ifCanClose(doClose)
