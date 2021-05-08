# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
from anki.cards import Card
from anki.stats import CardStats
from aqt.qt import *
from aqt.utils import disable_help_button, qconnect, restoreGeom, saveGeom
from aqt.webview import AnkiWebView


class CardInfoDialog(QDialog):
    silentlyClose = True

    def __init__(self, parent: QWidget, mw: aqt.AnkiQt, card: Card) -> None:
        super().__init__(parent)
        disable_help_button(self)
        cs = CardStats(mw.col, card)
        info = cs.report(include_revlog=True)

        l = QVBoxLayout()
        l.setContentsMargins(0, 0, 0, 0)
        w = AnkiWebView(title="browser card info")
        l.addWidget(w)
        w.stdHtml(info + "<p>", context=self)
        bb = QDialogButtonBox(QDialogButtonBox.Close)
        l.addWidget(bb)
        qconnect(bb.rejected, self.reject)
        self.setLayout(l)
        self.setWindowModality(Qt.WindowModal)
        self.resize(500, 400)
        restoreGeom(self, "revlog")
        self.show()

    def reject(self) -> None:
        saveGeom(self, "revlog")
        return QDialog.reject(self)
