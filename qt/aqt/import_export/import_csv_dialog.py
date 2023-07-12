# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any

import aqt
import aqt.deckconf
import aqt.main
import aqt.operations
from anki.collection import SearchNode
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
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(800, 800))
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.IMPORT_CSV)
        self.web.setVisible(False)
        self.web.load_ts_page("import-csv")
        self.web.set_bridge_command(self._on_bridge_cmd, self)
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        escaped_path = path.replace("'", r"\'")
        self.web.evalWithCallback(
            f"anki.setupImportCsvPage('{escaped_path}');", lambda _: self.web.setFocus()
        )
        self.setWindowTitle(tr.decks_import_file())

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("browse:"):
            nids = [int(nid) for nid in cmd[len("browse:") :].split(",")]
            search = self.mw.col.build_search_string(
                SearchNode(nids=SearchNode.IdList(ids=nids))
            )
            aqt.dialogs.open("Browser", self.mw, search=(search,))
