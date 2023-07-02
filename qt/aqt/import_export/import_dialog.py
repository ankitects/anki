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


class ImportDialog(QDialog):
    TITLE: str
    KIND: AnkiWebViewKind
    TS_PAGE: str
    SETUP_FUNCTION_NAME: str
    DEFAULT_SIZE = (800, 800)
    MIN_SIZE = (400, 300)
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
        self.setMinimumSize(*self.MIN_SIZE)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=self.DEFAULT_SIZE)
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=self.KIND)
        self.web.setVisible(False)
        self.web.load_ts_page(self.TS_PAGE)
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        restoreGeom(self, self.TITLE, default_size=(800, 800))

        escaped_path = path.replace("'", r"\'")
        self.web.evalWithCallback(
            f"anki.{self.SETUP_FUNCTION_NAME}('{escaped_path}');",
            lambda _: self.web.setFocus(),
        )
        self.setWindowTitle(tr.decks_import_file())

    def reject(self) -> None:
        if self.mw.col and self.windowModality() == Qt.WindowModality.ApplicationModal:
            self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)


class ImportCsvDialog(ImportDialog):
    TITLE = "csv import"
    KIND = AnkiWebViewKind.IMPORT_CSV
    TS_PAGE = "import-csv"
    SETUP_FUNCTION_NAME = "setupImportCsvPage"


class ImportAnkiPackageDialog(ImportDialog):
    TITLE = "anki package import"
    KIND = AnkiWebViewKind.IMPORT_ANKI_PACKAGE
    TS_PAGE = "import-anki-package"
    SETUP_FUNCTION_NAME = "setupImportAnkiPackagePage"
