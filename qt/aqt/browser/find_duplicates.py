# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
from typing import Any

import anki
import anki.find
import aqt
import aqt.forms
from anki.collection import SearchNode
from anki.notes import NoteId
from aqt.qt import *
from aqt.qt import sip
from aqt.webview import AnkiWebViewKind

from ..operations import QueryOp
from ..operations.tag import add_tags_to_notes
from ..utils import (
    disable_help_button,
    restore_combo_history,
    restore_combo_index_for_session,
    restoreGeom,
    save_combo_history,
    save_combo_index_for_session,
    saveGeom,
    tr,
)
from . import Browser


class FindDuplicatesDialog(QDialog):
    def __init__(self, browser: Browser, mw: aqt.AnkiQt):
        super().__init__(parent=browser)
        self.browser = browser
        self.mw = mw
        self.mw.garbage_collect_on_dialog_finish(self)
        self.form = form = aqt.forms.finddupes.Ui_Dialog()
        form.setupUi(self)
        restoreGeom(self, "findDupes")
        disable_help_button(self)
        searchHistory = restore_combo_history(form.search, "findDupesFind")

        fields = sorted(
            anki.find.fieldNames(self.mw.col, downcase=False), key=lambda x: x.lower()
        )
        form.fields.addItems(fields)
        restore_combo_index_for_session(form.fields, fields, "findDupesFields")
        self._dupesButton: QPushButton | None = None
        self._dupes: list[tuple[str, list[NoteId]]] = []

        # links
        form.webView.set_kind(AnkiWebViewKind.FIND_DUPLICATES)
        form.webView.set_bridge_command(self._on_duplicate_clicked, context=self)
        form.webView.stdHtml("", context=self)

        def on_finished(code: Any) -> None:
            saveGeom(self, "findDupes")

        qconnect(self.finished, on_finished)

        def on_click() -> None:
            search_text = save_combo_history(
                form.search, searchHistory, "findDupesFind"
            )
            save_combo_index_for_session(form.fields, "findDupesFields")
            field = fields[form.fields.currentIndex()]
            QueryOp(
                parent=self.browser,
                op=lambda col: col.find_dupes(field, search_text),
                success=self.show_duplicates_report,
            ).run_in_background()

        search = form.buttonBox.addButton(
            tr.actions_search(), QDialogButtonBox.ButtonRole.ActionRole
        )

        assert search is not None

        qconnect(search.clicked, on_click)
        self.show()

    def show_duplicates_report(self, dupes: list[tuple[str, list[NoteId]]]) -> None:
        if sip.isdeleted(self):
            return
        self._dupes = dupes
        if not self._dupesButton:
            self._dupesButton = b = self.form.buttonBox.addButton(
                tr.browsing_tag_duplicates(), QDialogButtonBox.ButtonRole.ActionRole
            )

            assert b is not None

            qconnect(b.clicked, self._tag_duplicates)
        text = ""
        groups = len(dupes)
        notes = sum(len(r[1]) for r in dupes)
        part1 = tr.browsing_group(count=groups)
        part2 = tr.browsing_note_count(count=notes)
        text += tr.browsing_found_as_across_bs(part=part1, whole=part2)
        text += "<p><ol>"
        for val, nids in dupes:
            text += (
                """<li><a href=# onclick="pycmd('%s');return false;">%s</a>: %s</a>"""
                % (
                    html.escape(
                        self.mw.col.build_search_string(
                            SearchNode(nids=SearchNode.IdList(ids=nids))
                        )
                    ),
                    tr.browsing_note_count(count=len(nids)),
                    html.escape(val),
                )
            )
        text += "</ol>"
        self.form.webView.stdHtml(text, context=self)

    def _tag_duplicates(self) -> None:
        if not self._dupes:
            return

        note_ids = set()
        for _, nids in self._dupes:
            note_ids.update(nids)

        add_tags_to_notes(
            parent=self,
            note_ids=list(note_ids),
            space_separated_tags=tr.browsing_duplicate(),
        ).run_in_background()

    def _on_duplicate_clicked(self, link: str) -> None:
        self.browser.search_for(link)
        self.browser.onNote()
