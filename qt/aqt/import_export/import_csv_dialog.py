# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.deckconf
import aqt.main
import aqt.operations
from aqt.qt import *
from aqt.utils import addCloseShortcut, disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind


class ImportCsvDialog(QDialog):
    TITLE = "csv import"
    silentlyClose = True

    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        path: str,
    ) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self._setup_ui(path)
        self.show()

    def _setup_ui(self, path: str) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.IMPORT_CSV)
        self.web.setVisible(False)
        self.web.load_ts_page("import-csv")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        restoreGeom(self, self.TITLE, default_size=(800, 800))

        escaped_path = path.replace("'", r"\'")
        self.web.evalWithCallback(
            f"anki.setupImportCsvPage('{escaped_path}');", lambda _: self.web.setFocus()
        )
        self.setWindowTitle(tr.decks_import_file())

    def reject(self) -> None:
        if self.mw.col and self.windowModality() == Qt.WindowModality.ApplicationModal:
            self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)
