# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.main
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind


class CfaDashboardDialog(QDialog):
    """CFA readiness dashboard: per-subject progress plus the Memory / Performance
    / Readiness gauges over the whole collection (the exam). Hosted in an
    API-enabled webview so the page can call the backend."""

    TITLE = "cfaDashboard"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        QDialog.__init__(self, mw)
        self.mw = mw
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.mw.garbage_collect_on_dialog_finish(self)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(900, 800))

        self.web = AnkiWebView(kind=AnkiWebViewKind.CFA_DASHBOARD)
        self.web.load_sveltekit_page("dashboard")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.setWindowTitle(tr.statistics_cfa_dashboard())

    def reject(self) -> None:
        global _dialog
        self.web.cleanup()
        self.web = None  # type: ignore
        saveGeom(self, self.TITLE)
        if _dialog is self:
            _dialog = None
        QDialog.reject(self)


# A single reusable dashboard window, so re-opening refreshes/raises the existing
# one instead of stacking a new window.
_dialog: CfaDashboardDialog | None = None


def show_dashboard(mw: aqt.main.AnkiQt) -> None:
    global _dialog
    if _dialog is not None:
        try:
            _dialog.activateWindow()
            _dialog.raise_()
            return
        except RuntimeError:
            # the underlying window was already destroyed
            _dialog = None
    _dialog = CfaDashboardDialog(mw)
