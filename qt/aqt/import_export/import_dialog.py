# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

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
class ImportArgs:
    path: str
    title = "importLog"
    kind = AnkiWebViewKind.IMPORT_LOG
    ts_page = "import-page"
    setup_function_name = "setupImportPage"

    def args_json(self) -> str:
        return json.dumps(self.path)


class JsonFileArgs(ImportArgs):
    def args_json(self) -> str:
        return json.dumps(dict(type="json_file", path=self.path))


@dataclass
class JsonStringArgs(ImportArgs):
    json: str

    def args_json(self) -> str:
        return json.dumps(dict(type="json_string", path=self.path, json=self.json))


class CsvArgs(ImportArgs):
    title = "csv import"
    kind = AnkiWebViewKind.IMPORT_CSV
    ts_page = "import-csv"
    setup_function_name = "setupImportCsvPage"


class AnkiPackageArgs(ImportArgs):
    title = "anki package import"
    kind = AnkiWebViewKind.IMPORT_ANKI_PACKAGE
    ts_page = "import-anki-package"
    setup_function_name = "setupImportAnkiPackagePage"


class ImportDialog(QDialog):
    DEFAULT_SIZE = (800, 800)
    MIN_SIZE = (400, 300)
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, args: ImportArgs) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.args = args
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(*self.MIN_SIZE)
        disable_help_button(self)
        restoreGeom(self, self.args.title, default_size=self.DEFAULT_SIZE)
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=self.args.kind)
        self.web.setVisible(False)
        self.web.load_ts_page(self.args.ts_page)
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        restoreGeom(self, self.args.title, default_size=(800, 800))

        self.web.evalWithCallback(
            f"anki.{self.args.setup_function_name}({self.args.args_json()});",
            lambda _: self.web.setFocus(),
        )
        self.setWindowTitle(tr.decks_import_file())

    def reject(self) -> None:
        if self.mw.col and self.windowModality() == Qt.WindowModality.ApplicationModal:
            self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.args.title)
        QDialog.reject(self)
