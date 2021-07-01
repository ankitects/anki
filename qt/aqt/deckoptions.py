# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import List, Optional

import aqt
import aqt.deckconf
from anki.cards import Card
from anki.decks import DeckDict, DeckId
from anki.lang import without_unicode_isolation
from aqt import gui_hooks
from aqt.qt import *
from aqt.utils import (
    KeyboardModifiersPressed,
    addCloseShortcut,
    disable_help_button,
    restoreGeom,
    saveGeom,
    tr,
)
from aqt.webview import AnkiWebView


class DeckOptionsDialog(QDialog):
    "The new deck configuration screen."

    TITLE = "deckOptions"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, deck: DeckDict) -> None:
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self._deck = deck
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.setWindowModality(Qt.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumWidth(400)
        disable_help_button(self)
        restoreGeom(self, self.TITLE)
        addCloseShortcut(self)

        self.web = AnkiWebView(title=self.TITLE)
        self.web.setVisible(False)
        self.web.load_ts_page("deck-options")
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 0, 0)
        layout.addWidget(self.web)
        self.setLayout(layout)

        self.web.eval(
            f"""const $deckOptions = anki.deckOptions(
            document.getElementById('main'), {self._deck["id"]});"""
        )
        self.setWindowTitle(
            without_unicode_isolation(tr.actions_options_for(val=self._deck["name"]))
        )
        gui_hooks.deck_options_did_load(self)

    def reject(self) -> None:
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)


def confirm_deck_then_display_options(active_card: Optional[Card] = None) -> None:
    decks = [aqt.mw.col.decks.current()]
    if card := active_card:
        if card.odid and card.odid != decks[0]["id"]:
            decks.append(aqt.mw.col.decks.get(card.odid))

        if not any(d["id"] == card.did for d in decks):
            decks.append(aqt.mw.col.decks.get(card.odid))

    if len(decks) == 1:
        display_options_for_deck(decks[0])
    else:
        decks.sort(key=lambda x: x["dyn"])
        _deck_prompt_dialog(decks)


def _deck_prompt_dialog(decks: List[DeckDict]) -> None:
    diag = QDialog(aqt.mw.app.activeWindow())
    diag.setWindowTitle("Anki")
    box = QVBoxLayout()
    box.addWidget(QLabel(tr.deck_config_which_deck()))
    for deck in decks:
        button = QPushButton(deck["name"])
        qconnect(button.clicked, lambda _, deck=deck: display_options_for_deck(deck))
        qconnect(button.clicked, diag.close)
        box.addWidget(button)
    button = QPushButton(tr.actions_cancel())
    qconnect(button.clicked, diag.close)
    box.addWidget(button)
    diag.setLayout(box)
    diag.exec_()


def display_options_for_deck_id(deck_id: DeckId) -> None:
    display_options_for_deck(aqt.mw.col.decks.get(deck_id))


def display_options_for_deck(deck: DeckDict) -> None:
    if not deck["dyn"]:
        if KeyboardModifiersPressed().shift or aqt.mw.col.sched_ver() == 1:
            deck_legacy = aqt.mw.col.decks.get(DeckId(deck["id"]))
            aqt.deckconf.DeckConf(aqt.mw, deck_legacy)
        else:
            DeckOptionsDialog(aqt.mw, deck)
    else:
        aqt.dialogs.open("FilteredDeckConfigDialog", aqt.mw, deck_id=deck["id"])
