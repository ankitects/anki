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


class ImportLogDialog(QDialog):
    GEOMETRY_KEY = "importLog"
    silentlyClose = True

    def __init__(
        self,
        mw: aqt.main.AnkiQt,
    ) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.IMPORT_LOG)
        self.web.setVisible(False)
        self.web.load_ts_page("import-log")
        self.web.set_bridge_command(self._on_bridge_cmd, self)
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        restoreGeom(self, self.GEOMETRY_KEY, default_size=(800, 800))

        self.web.eval("anki.setupImportLogPage()")

        self.setWindowTitle(tr.importing_import_log())

    def reject(self) -> None:
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        QDialog.reject(self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd.startswith("browse:"):
            nids = cmd[len("browse:") :].split(",")
            nodes = [SearchNode(nid=int(nid)) for nid in nids]
            search = self.mw.col.build_search_string(*nodes, joiner="OR")
            print(nids, search)
            aqt.dialogs.open("Browser", self.mw, search=(search,))
