# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from concurrent.futures import Future
from typing import Any, List

import aqt
from anki.cards import CardId
from anki.collection import EmptyCardsReport
from aqt import gui_hooks
from aqt.qt import QDialog, QDialogButtonBox, qconnect
from aqt.utils import disable_help_button, restoreGeom, saveGeom, tooltip, tr


def show_empty_cards(mw: aqt.main.AnkiQt) -> None:
    mw.progress.start()

    def on_done(fut: Future) -> None:
        mw.progress.finish()
        report: EmptyCardsReport = fut.result()
        if not report.notes:
            tooltip(tr.empty_cards_not_found())
            return
        diag = EmptyCardsDialog(mw, report)
        diag.show()

    mw.taskman.run_in_background(mw.col.get_empty_cards, on_done)


class EmptyCardsDialog(QDialog):
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, report: EmptyCardsReport) -> None:
        super().__init__(mw)
        self.mw = mw.weakref()
        self.report = report
        self.form = aqt.forms.emptycards.Ui_Dialog()
        self.form.setupUi(self)
        restoreGeom(self, "emptycards")
        self.setWindowTitle(tr.empty_cards_window_title())
        disable_help_button(self)
        self.form.keep_notes.setText(tr.empty_cards_preserve_notes_checkbox())
        self.form.webview.set_title("empty cards")
        self.form.webview.set_bridge_command(self._on_note_link_clicked, self)

        gui_hooks.empty_cards_will_show(self)

        # make the note ids clickable
        html = re.sub(
            r"\[anki:nid:(\d+)\]",
            "<a href=# onclick=\"pycmd('nid:\\1'); return false\">\\1</a>: ",
            report.report,
        )
        style = "<style>.allempty { color: red; }</style>"
        self.form.webview.stdHtml(style + html, context=self)

        def on_finished(code: Any) -> None:
            saveGeom(self, "emptycards")

        qconnect(self.finished, on_finished)

        self._delete_button = self.form.buttonBox.addButton(
            tr.empty_cards_delete_button(), QDialogButtonBox.ActionRole
        )
        self._delete_button.setAutoDefault(False)
        qconnect(self._delete_button.clicked, self._on_delete)

    def _on_note_link_clicked(self, link: str) -> None:
        aqt.dialogs.open("Browser", self.mw, search=(link,))

    def _on_delete(self) -> None:
        self.mw.progress.start()

        def delete() -> int:
            return self._delete_cards(self.form.keep_notes.isChecked())

        def on_done(fut: Future) -> None:
            self.mw.progress.finish()
            try:
                count = fut.result()
            finally:
                self.close()
            tooltip(tr.empty_cards_deleted_count(cards=count))
            self.mw.reset()

        self.mw.taskman.run_in_background(delete, on_done)

    def _delete_cards(self, keep_notes: bool) -> int:
        to_delete: List[CardId] = []
        note: EmptyCardsReport.NoteWithEmptyCards
        for note in self.report.notes:
            if keep_notes and note.will_delete_note:
                # leave first card
                to_delete.extend([CardId(id) for id in note.card_ids[1:]])
            else:
                to_delete.extend([CardId(id) for id in note.card_ids])

        self.mw.col.remove_cards_and_orphaned_notes(to_delete)
        return len(to_delete)
