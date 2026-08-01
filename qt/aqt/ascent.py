# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
"""Ascent's three separated scores.

A plain window around the `ascent-scores` SvelteKit page; everything the
screen shows comes from the AscentScores rpc in rslib/src/ascent/.
"""

from __future__ import annotations

import aqt
import aqt.main
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom
from aqt.webview import AnkiWebView


class AscentScoresDialog(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        mw.garbage_collect_on_dialog_finish(self)
        self.mw = mw
        self.name = "ascentScores"
        disable_help_button(self)

        self.web = AnkiWebView(self, title="ascent scores")
        layout = QVBoxLayout(self)
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setWindowTitle("Ascent Scores")
        restoreGeom(self, self.name, default_size=(900, 800))
        self.web.load_sveltekit_page("ascent-scores")
        self.show()

    def reject(self) -> None:
        saveGeom(self, self.name)
        self.web.cleanup()
        self.web = None  # type: ignore[assignment]
        QDialog.reject(self)
