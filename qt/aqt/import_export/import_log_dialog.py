# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import json
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
        **params: Any,
    ) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self._setup_ui(**params)
        self.show()

    def _setup_ui(self, **params: Any) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
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

        self.web.evalWithCallback(
            "anki.setupImportLogPage(%s);" % json.dumps(params),
            lambda _: self.web.setFocus(),
        )

        title = tr.importing_import_log()
        if path := params.get("path", None):
            title += f" - {os.path.basename(path)}"
        self.setWindowTitle(title)

    def reject(self) -> None:
        if self.mw.col:
            self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        QDialog.reject(self)

    def _on_bridge_cmd(self, cmd: str) -> Any:
        if cmd == "import_done":
            self.hide()
            self.setWindowModality(Qt.WindowModality.NonModal)
            self.show()
        if cmd.startswith("browse:"):
            nids = [int(nid) for nid in cmd[len("browse:") :].split(",")]
            search = self.mw.col.build_search_string(
                SearchNode(nids=SearchNode.IdList(ids=nids))
            )
            aqt.dialogs.open("Browser", self.mw, search=(search,))
