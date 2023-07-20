# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import dataclasses
import json
from dataclasses import dataclass

import aqt
import aqt.deckconf
import aqt.main
import aqt.operations
from aqt.qt import *
from aqt.utils import addCloseShortcut, disable_help_button, restoreGeom, saveGeom, tr
from aqt.webview import AnkiWebView, AnkiWebViewKind


@dataclass
class _CommonArgs:
    type: str = dataclasses.field(init=False)
    path: str

    def to_json(self) -> str:
        return json.dumps(dataclasses.asdict(self))


@dataclass
class ApkgArgs(_CommonArgs):
    type = "apkg"


@dataclass
class JsonFileArgs(_CommonArgs):
    type = "json_file"


@dataclass
class JsonStringArgs(_CommonArgs):
    type = "json_string"
    json: str


class ImportLogDialog(QDialog):
    GEOMETRY_KEY = "importLog"
    silentlyClose = True

    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        args: ApkgArgs | JsonFileArgs | JsonStringArgs,
    ) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self._setup_ui(args)
        self.show()

    def _setup_ui(
        self,
        args: ApkgArgs | JsonFileArgs | JsonStringArgs,
    ) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        disable_help_button(self)
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.IMPORT_LOG)
        self.web.setVisible(False)
        self.web.load_ts_page("import-log")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        restoreGeom(self, self.GEOMETRY_KEY, default_size=(800, 800))

        self.web.evalWithCallback(
            "anki.setupImportLogPage(%s);" % (args.to_json()),
            lambda _: self.web.setFocus(),
        )

        title = tr.importing_import_log()
        title += f" - {os.path.basename(args.path)}"
        self.setWindowTitle(title)

    def reject(self) -> None:
        if self.mw.col and self.windowModality() == Qt.WindowModality.ApplicationModal:
            self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        QDialog.reject(self)
