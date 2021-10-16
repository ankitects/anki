# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Callable

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

    def __init__(
        self,
        parent: QWidget | None,
        mw: aqt.AnkiQt,
        card: Card,
        on_close: Callable | None = None,
        geometry_key: str | None = None,
        window_title: str | None = None,
    ) -> None:
        super().__init__(parent)
        self.mw = mw
        self._on_close = on_close
        self.GEOMETRY_KEY = geometry_key or self.GEOMETRY_KEY
        if window_title:
            self.setWindowTitle(window_title)
        self._setup_ui(card.id)
        self.show()

    def _setup_ui(self, card_id: CardId) -> None:
        self.mw.garbage_collect_on_dialog_finish(self)
        disable_help_button(self)
        restoreGeom(self, self.GEOMETRY_KEY)
        addCloseShortcut(self)
        icon = QIcon()
        icon.addPixmap(QPixmap("icons:anki.png"), QIcon.Mode.Normal, QIcon.State.Off)
        self.setWindowIcon(icon)

        self.web = AnkiWebView(title=self.TITLE)
        self.web.setVisible(False)
        self.web.load_ts_page("card-info")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        buttons.setContentsMargins(10, 0, 10, 10)
        layout.addWidget(buttons)
        qconnect(buttons.rejected, self.reject)
        self.setLayout(layout)

        self.web.eval(
            f"let cardInfo = anki.cardInfo(document.getElementById('main'), {card_id}, true);"
        )

    def update_card(self, card_id: CardId) -> None:
        self.web.eval(f"anki.updateCardInfo(cardInfo, {card_id}, true);")

    def reject(self) -> None:
        if self._on_close:
            self._on_close()
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        return QDialog.reject(self)
