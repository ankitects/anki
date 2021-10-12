# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
from anki.cards import Card, CardId
from aqt.qt import *
from aqt.utils import (
    addCloseShortcut,
    disable_help_button,
    qconnect,
    restoreGeom,
    saveGeom,
)
from aqt.webview import AnkiWebView


class CardInfoDialog(QDialog):
    TITLE = "browser card info"
    GEOMETRY_KEY = "revlog"
    silentlyClose = True

    def __init__(self, parent: QWidget, mw: aqt.AnkiQt, card: Card) -> None:
        super().__init__(parent)
        self.mw = mw
        self._setup_ui(card.id)
        self.show()

    def _setup_ui(self, card_id: CardId) -> None:
        self.setWindowModality(Qt.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        disable_help_button(self)
        restoreGeom(self, self.GEOMETRY_KEY)
        addCloseShortcut(self)

        self.web = AnkiWebView(title=self.TITLE)
        self.web.setVisible(False)
        self.web.load_ts_page("card-info")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        buttons = QDialogButtonBox(QDialogButtonBox.Close)
        buttons.setContentsMargins(10, 0, 10, 10)
        layout.addWidget(buttons)
        qconnect(buttons.rejected, self.reject)
        self.setLayout(layout)

        self.web.eval(
            f"anki.cardInfo(document.getElementById('main'), {card_id}, true);"
        )

    def reject(self) -> None:
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        return QDialog.reject(self)
