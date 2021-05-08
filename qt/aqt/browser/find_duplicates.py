# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
from typing import Any, List, Optional

import anki
import anki.find
import aqt
from anki.collection import SearchNode
from aqt.qt import *

from ..main import ResetReason
from ..utils import (
    disable_help_button,
    restore_combo_history,
    restore_combo_index_for_session,
    restoreGeom,
    save_combo_history,
    save_combo_index_for_session,
    saveGeom,
    showWarning,
    tooltip,
    tr,
)
from ..webview import AnkiWebView
from . import Browser


class FindDuplicatesDialog(QDialog):
    def __init__(self, browser: Browser, mw: aqt.AnkiQt):
        super().__init__(parent=browser)
        self.browser = browser
        self.mw = mw
        self.mw.garbage_collect_on_dialog_finish(self)
        form = aqt.forms.finddupes.Ui_Dialog()
        form.setupUi(self)
        restoreGeom(self, "findDupes")
        disable_help_button(self)
        searchHistory = restore_combo_history(form.search, "findDupesFind")

        fields = sorted(
            anki.find.fieldNames(self.mw.col, downcase=False), key=lambda x: x.lower()
        )
        form.fields.addItems(fields)
        restore_combo_index_for_session(form.fields, fields, "findDupesFields")
        self._dupesButton: Optional[QPushButton] = None

        # links
        form.webView.set_title("find duplicates")
        form.webView.set_bridge_command(self.dupeLinkClicked, context=self)
        form.webView.stdHtml("", context=self)

        def onFin(code: Any) -> None:
            saveGeom(self, "findDupes")

        qconnect(self.finished, onFin)

        def onClick() -> None:
            search_text = save_combo_history(
                form.search, searchHistory, "findDupesFind"
            )
            save_combo_index_for_session(form.fields, "findDupesFields")
            field = fields[form.fields.currentIndex()]
            self.duplicatesReport(form.webView, field, search_text, form)

        search = form.buttonBox.addButton(
            tr.actions_search(), QDialogButtonBox.ActionRole
        )
        qconnect(search.clicked, onClick)
        self.show()

    def duplicatesReport(
        self,
        web: AnkiWebView,
        fname: str,
        search: str,
        frm: aqt.forms.finddupes.Ui_Dialog,
    ) -> None:
        self.mw.progress.start()
        try:
            res = self.mw.col.findDupes(fname, search)
        except Exception as e:
            self.mw.progress.finish()
            showWarning(str(e))
            return
        if not self._dupesButton:
            self._dupesButton = b = frm.buttonBox.addButton(
                tr.browsing_tag_duplicates(), QDialogButtonBox.ActionRole
            )
            qconnect(b.clicked, lambda: self._onTagDupes(res))
        t = ""
        groups = len(res)
        notes = sum(len(r[1]) for r in res)
        part1 = tr.browsing_group(count=groups)
        part2 = tr.browsing_note_count(count=notes)
        t += tr.browsing_found_as_across_bs(part=part1, whole=part2)
        t += "<p><ol>"
        for val, nids in res:
            t += (
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
        t += "</ol>"
        web.stdHtml(t, context=self)
        self.mw.progress.finish()

    def _onTagDupes(self, res: List[Any]) -> None:
        if not res:
            return
        self.browser.begin_reset()
        self.mw.checkpoint(tr.browsing_tag_duplicates())
        nids = set()
        for _, nidlist in res:
            nids.update(nidlist)
        self.mw.col.tags.bulk_add(list(nids), tr.browsing_duplicate())
        self.mw.progress.finish()
        self.browser.end_reset()
        self.mw.requireReset(reason=ResetReason.BrowserTagDupes, context=self)
        tooltip(tr.browsing_notes_tagged())

    def dupeLinkClicked(self, link: str) -> None:
        self.browser.search_for(link)
        self.browser.onNote()
