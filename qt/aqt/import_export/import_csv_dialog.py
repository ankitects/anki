# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.deckconf
import aqt.main
import aqt.operations
from anki.collection import ImportCsvRequest
from aqt.qt import *
from aqt.utils import addCloseShortcut, disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView


class ImportCsvDialog(QDialog):

    TITLE = "csv import"
    silentlyClose = True

    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        path: str,
        on_accepted: Callable[[ImportCsvRequest], None],
    ) -> None:
        QDialog.__init__(self, mw)
        self.mw = mw
        self._on_accepted = on_accepted
        self._setup_ui(path)
        self.show()

    def _setup_ui(self, path: str) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(800, 800))
        addCloseShortcut(self)

        self.web = AnkiWebView(title=self.TITLE)
        self.web.setVisible(False)
        self.web.load_ts_page("import-csv")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.web.evalWithCallback(
            f"anki.setupImportCsvPage('{path}');", lambda _: self.web.setFocus()
        )
        self.setWindowTitle(tr.decks_import_file())

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)

    def do_import(self, data: bytes) -> None:
        request = ImportCsvRequest()
        request.ParseFromString(data)
        self._on_accepted(request)
        super().reject()
