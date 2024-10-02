# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Sequence

import aqt
import aqt.deckconf
import aqt.main
import aqt.operations
from anki.collection import OpChanges
from anki.models import ChangeNotetypeRequest, NotetypeId
from anki.notes import NoteId
from aqt.operations.notetype import change_notetype_of_notes
from aqt.qt import *
from aqt.utils import (
    addCloseShortcut,
    disable_help_button,
    restoreGeom,
    saveGeom,
    showWarning,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind


class ChangeNotetypeDialog(QDialog):
    TITLE = "changeNotetype"
    silentlyClose = True

    def __init__(
        self,
        parent: QWidget,
        mw: aqt.main.AnkiQt,
        note_ids: Sequence[NoteId],
        notetype_id: NotetypeId,
    ) -> None:
        QDialog.__init__(self, parent)
        self.mw = mw
        self._note_ids = note_ids
        self._setup_ui(notetype_id)
        self.show()

    def _setup_ui(self, notetype_id: NotetypeId) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(800, 800))
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.CHANGE_NOTETYPE)
        self.web.setVisible(False)
        self.web.load_sveltekit_page(f"change-notetype/{notetype_id}")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.setWindowTitle(tr.browsing_change_notetype())

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)

    def save(self, data: bytes) -> None:
        input = ChangeNotetypeRequest()
        input.ParseFromString(data)

        if not self.mw.confirm_schema_modification():
            return

        def on_done(op: OpChanges) -> None:
            tooltip(
                tr.browsing_notes_updated(count=len(input.note_ids)),
                parent=self.parentWidget(),
            )
            self.reject()

        input.note_ids.extend(self._note_ids)
        change_notetype_of_notes(parent=self, input=input).success(
            on_done
        ).run_in_background()


def change_notetype_dialog(parent: QWidget, note_ids: Sequence[NoteId]) -> None:
    try:
        notetype_id = aqt.mw.col.models.get_single_notetype_of_notes(note_ids)
    except Exception as e:
        showWarning(str(e), parent=parent)
        return

    ChangeNotetypeDialog(
        parent=parent, mw=aqt.mw, note_ids=note_ids, notetype_id=notetype_id
    )
