# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
from typing import Optional, Sequence

import aqt.forms
import aqt.main
from anki.decks import DeckId
from anki.notes import NoteId
from aqt import utils, webview
from aqt.qt import *
from aqt.utils import checkInvalidFilename, getSaveFile, showWarning, tr


class ExportDialog(QDialog):
    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        did: DeckId | None = None,
        nids: Sequence[NoteId] | None = None,
        parent: Optional[QWidget] = None,
    ):
        assert mw.col
        QDialog.__init__(self, parent or mw, Qt.WindowType.Window)
        self.mw = mw
        self.col = mw.col.weakref()
        self.nids = nids
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumSize(400, 300)
        self.resize(800, 600)
        utils.disable_help_button(self)
        utils.addCloseShortcut(self)
        self.web = webview.AnkiWebView(kind=webview.AnkiWebViewKind.EXPORT)
        self.web.setVisible(False)
        route = "notes" if self.nids else f"deck/{did}" if did else ""
        self.web.load_sveltekit_page(f"export-page/{route}")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        self.setWindowTitle(tr.actions_export())
        self.open()

    def reject(self) -> None:
        assert self.web
        self.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        QDialog.reject(self)


def get_out_path(exporter: str, extension: str, filename: str) -> str | None:
    assert aqt.mw
    parent = aqt.mw.app.activeWindow() or aqt.mw
    while True:
        path = getSaveFile(
            parent=parent,
            title=tr.actions_export(),
            dir_description="export",
            key=exporter,
            ext=f".{extension}",
            fname=filename,
        )
        if not path:
            return None
        if checkInvalidFilename(os.path.basename(path), dirsep=False):
            continue
        path = os.path.normpath(path)
        if os.path.commonprefix([aqt.mw.pm.base, path]) == aqt.mw.pm.base:
            showWarning("Please choose a different export location.")
            continue
        break
    return path
