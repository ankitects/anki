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
    addCloseShortcut,
    ask_user_dialog,
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
        addCloseShortcut(self)

        self.web = AnkiWebView(kind=AnkiWebViewKind.DECK_OPTIONS)
        self.web.set_bridge_command(self._on_bridge_cmd, self)
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

    def _on_bridge_cmd(self, cmd: str) -> None:
        if cmd == "deckOptionsReady":
            self._ready = True
            gui_hooks.deck_options_did_load(self)
        elif cmd == "confirmDiscardChanges":
            self.confirm_discard_changes()
        elif cmd == "_close":
            self._close()

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._close_event_has_cleaned_up:
            return super().closeEvent(evt)
        evt.ignore()
        self.check_pending_changes()

    def _close(self):
        """Close. Ensure the closeEvent is not ignored."""
        self._close_event_has_cleaned_up = True
        self.close()

    def confirm_discard_changes(self) -> None:
        def callbackWithUserChoice(choice: int) -> None:
            if choice == 0:
                # The user accepted to discard current input.
                self._close()

        ask_user_dialog(
            tr.card_templates_discard_changes(),
            callback=callbackWithUserChoice,
            buttons=[
                QMessageBox.StandardButton.Discard,
                (tr.adding_keep_editing(), QMessageBox.ButtonRole.RejectRole),
            ],
            parent=self,
        )

    def reject(self) -> None:
        self.mw.col.set_wants_abort()
        self.web.cleanup()
        self.web = None
        saveGeom(self, self.TITLE)
        QDialog.reject(self)

    def check_pending_changes(self):
        if self._ready:
            self.web.eval("anki.deckOptionsPendingChanges();")
        else:
            self._close()


def confirm_deck_then_display_options(active_card: Card | None = None) -> None:
    decks = [aqt.mw.col.decks.current()]
    if card := active_card:
        if card.odid and card.odid != decks[0]["id"]:
            decks.append(aqt.mw.col.decks.get(card.odid))

        if not any(d["id"] == card.did for d in decks):
            decks.append(aqt.mw.col.decks.get(card.did))

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
    display_options_for_deck(aqt.mw.col.decks.get(deck_id))


def display_options_for_deck(deck: DeckDict) -> None:
    if not deck["dyn"]:
        if KeyboardModifiersPressed().shift or not aqt.mw.col.v3_scheduler():
            deck_legacy = aqt.mw.col.decks.get(DeckId(deck["id"]))
            aqt.deckconf.DeckConf(aqt.mw, deck_legacy)
        else:
            DeckOptionsDialog(aqt.mw, deck)
    else:
        aqt.dialogs.open("FilteredDeckConfigDialog", aqt.mw, deck_id=deck["id"])
