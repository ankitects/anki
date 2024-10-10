# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable

import aqt
from anki.cards import Card, CardId
from anki.errors import NotFoundError
from anki.lang import without_unicode_isolation
from aqt.qt import *
from aqt.utils import (
    addCloseShortcut,
    disable_help_button,
    qconnect,
    restoreGeom,
    saveGeom,
    setWindowIcon,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind


class CardInfoDialog(QDialog):
    TITLE = "browser card info"
    GEOMETRY_KEY = "revlog"
    silentlyClose = True

    def __init__(
        self,
        parent: QWidget | None,
        mw: aqt.AnkiQt,
        card: Card | None,
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
        self._setup_ui(card.id if card else None)
        self.show()

    def _setup_ui(self, card_id: CardId | None) -> None:
        self.mw.garbage_collect_on_dialog_finish(self)
        disable_help_button(self)
        restoreGeom(self, self.GEOMETRY_KEY, default_size=(800, 800))
        addCloseShortcut(self)
        setWindowIcon(self)

        self.web: AnkiWebView | None = AnkiWebView(
            kind=AnkiWebViewKind.BROWSER_CARD_INFO
        )
        self.web.setVisible(False)
        self.web.load_sveltekit_page(f"card-info/{card_id}")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        buttons = QDialogButtonBox(QDialogButtonBox.StandardButton.Close)
        buttons.setContentsMargins(10, 0, 10, 10)
        layout.addWidget(buttons)
        qconnect(buttons.rejected, self.reject)
        self.setLayout(layout)

    def update_card(self, card_id: CardId | None) -> None:
        from aqt.theme import theme_manager

        try:
            self.mw.col.get_card(card_id)
        except NotFoundError:
            card_id = None

        if theme_manager.night_mode:
            extra = "#night"
        else:
            extra = ""
        assert self.web is not None
        self.web.eval(f"window.location.href = '/card-info/{card_id}{extra}';")

    def reject(self) -> None:
        if self._on_close:
            self._on_close()
        assert self.web is not None
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.GEOMETRY_KEY)
        return QDialog.reject(self)


class CardInfoManager:
    """Wrapper class to conveniently toggle, update and close a card info dialog."""

    def __init__(self, mw: aqt.AnkiQt, geometry_key: str, window_title: str):
        self.mw = mw
        self.geometry_key = geometry_key
        self.window_title = window_title
        self._card: Card | None = None
        self._dialog: CardInfoDialog | None = None

    def show(self) -> None:
        if self._dialog:
            self._dialog.activateWindow()
            self._dialog.raise_()
        else:
            self._dialog = CardInfoDialog(
                None,
                self.mw,
                self._card,
                self._on_close,
                self.geometry_key,
                self.window_title,
            )

    def set_card(self, card: Card | None) -> None:
        self._card = card
        if self._dialog:
            self._dialog.update_card(card.id if card else None)

    def close(self) -> None:
        if self._dialog:
            self._dialog.reject()

    def _on_close(self) -> None:
        self._dialog = None


class BrowserCardInfo(CardInfoManager):
    def __init__(self, mw: aqt.AnkiQt):
        super().__init__(
            mw,
            "revlog",
            without_unicode_isolation(
                tr.card_stats_current_card(context=tr.qt_misc_browse())
            ),
        )


class ReviewerCardInfo(CardInfoManager):
    def __init__(self, mw: aqt.AnkiQt):
        super().__init__(
            mw,
            "reviewerCardInfo",
            without_unicode_isolation(
                tr.card_stats_current_card(context=tr.decks_study())
            ),
        )


class PreviousReviewerCardInfo(CardInfoManager):
    def __init__(self, mw: aqt.AnkiQt):
        super().__init__(
            mw,
            "previousReviewerCardInfo",
            without_unicode_isolation(
                tr.card_stats_previous_card(context=tr.decks_study())
            ),
        )
