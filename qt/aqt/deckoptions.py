# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.deckconf
import aqt.main
from anki.cards import Card
from anki.decks import DeckDict, DeckId
from anki.lang import without_unicode_isolation
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    KeyboardModifiersPressed,
    add_close_shortcut,
    disable_help_button,
    restoreGeom,
    saveGeom,
    tr,
)
from aqt.webview import AnkiWebView, AnkiWebViewKind


class DeckOptionsDialog(QDialog):
    "The new deck configuration screen."

    TITLE = "deckOptions"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, deck: DeckDict) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self._deck = deck
        self._close_event_has_cleaned_up = False
        self._ready = False
        self._setup_ui()

    def _setup_ui(self) -> None:
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumWidth(400)
        disable_help_button(self)
        restoreGeom(self, self.TITLE, default_size=(800, 800))
        add_close_shortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.DECK_OPTIONS)
        self.web.load_sveltekit_page(f"deck-options/{self._deck['id']}")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)
        self.show()
        self.web.hide_while_preserving_layout()
        self.setWindowTitle(
            without_unicode_isolation(tr.actions_options_for(val=self._deck["name"]))
        )

    def set_ready(self):
        self._ready = True
        gui_hooks.deck_options_did_load(self)

    def closeEvent(self, evt: QCloseEvent | None) -> None:
        if self._close_event_has_cleaned_up or not self._ready:
            return super().closeEvent(evt)
        assert evt is not None
        evt.ignore()
        self.web.eval("anki.deckOptionsPendingChanges();")

    def require_close(self):
        """Close. Ensure the closeEvent is not ignored."""
        self._close_event_has_cleaned_up = True
        self.close()

    def reject(self) -> None:
        self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None  # type: ignore
        saveGeom(self, self.TITLE)
        QDialog.reject(self)


def confirm_deck_then_display_options(active_card: Card | None = None) -> None:
    decks = [aqt.mw.col.decks.current()]
    if card := active_card:
        if card.odid and card.odid != decks[0]["id"]:
            deck = aqt.mw.col.decks.get(card.odid)
            assert deck is not None
            decks.append(deck)

        if not any(d["id"] == card.did for d in decks):
            deck = aqt.mw.col.decks.get(card.did)
            assert deck is not None
            decks.append(deck)

    if len(decks) == 1:
        display_options_for_deck(decks[0])
    else:
        decks.sort(key=lambda x: x["dyn"])
        _deck_prompt_dialog(decks)


def _deck_prompt_dialog(decks: list[DeckDict]) -> None:
    diag = QDialog(aqt.mw.app.activeWindow())
    diag.setWindowTitle("Anki")
    box = QVBoxLayout()
    box.addWidget(QLabel(tr.deck_config_which_deck()))
    for deck in decks:
        button = QPushButton(deck["name"])
        qconnect(button.clicked, diag.close)
        qconnect(button.clicked, lambda _, deck=deck: display_options_for_deck(deck))
        box.addWidget(button)
    button = QPushButton(tr.actions_cancel())
    qconnect(button.clicked, diag.close)
    box.addWidget(button)
    diag.setLayout(box)
    diag.open()


def display_options_for_deck_id(deck_id: DeckId) -> None:
    deck = aqt.mw.col.decks.get(deck_id)
    assert deck is not None
    display_options_for_deck(deck)


def display_options_for_deck(deck: DeckDict) -> None:
    if not deck["dyn"]:
        if KeyboardModifiersPressed().shift or not aqt.mw.col.v3_scheduler():
            deck_legacy = aqt.mw.col.decks.get(DeckId(deck["id"]))
            assert deck_legacy is not None
            aqt.deckconf.DeckConf(aqt.mw, deck_legacy)
        else:
            DeckOptionsDialog(aqt.mw, deck)
    else:
        aqt.dialogs.open("FilteredDeckConfigDialog", aqt.mw, deck_id=deck["id"])
