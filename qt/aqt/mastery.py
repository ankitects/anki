# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Topic-mastery dashboard (MCAT fork).

A thin Qt window hosting the SvelteKit `mastery` page, which reads the
read-only `tag_mastery` backend query. See specs/PRD1.md §5.5.
"""

from __future__ import annotations

from collections.abc import Callable

import aqt
import aqt.main
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind


class MasteryStats(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.name = "masteryStats"
        mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(600, 400)
        disable_help_button(self)
        restoreGeom(self, self.name, default_size=(800, 800))

        self.web = AnkiWebView(kind=AnkiWebViewKind.MASTERY_STATS)
        self.web.load_sveltekit_page("mastery")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.setWindowTitle(tr.statistics_mastery_title())
        self.show()

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None  # type: ignore
        saveGeom(self, self.name)
        aqt.dialogs.markClosed("MasteryStats")
        QDialog.reject(self)

    def closeWithCallback(self, callback: Callable[[], None]) -> None:
        self.reject()
        callback()
