# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
from anki.decks import Deck
from aqt.operations import QueryOp
from aqt.operations.deck import update_deck
from aqt.qt import *
from aqt.utils import addCloseShortcut, disable_help_button, restoreGeom, saveGeom, tr


class DeckDescriptionDialog(QDialog):

    TITLE = "deckDescription"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw

        # set on success
        self.deck: Deck

        QueryOp(
            parent=self.mw,
            op=lambda col: col.decks.get_current(),
            success=self._setup_and_show,
        ).run_in_background()

    def _setup_and_show(self, deck: Deck) -> None:
        if deck.WhichOneof("kind") != "normal":
            return

        self.deck = deck
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.setWindowTitle(tr.scheduling_description())
        self.setWindowModality(Qt.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumWidth(400)
        disable_help_button(self)
        restoreGeom(self, self.TITLE)
        addCloseShortcut(self)

        box = QVBoxLayout()

        label = QLabel(tr.scheduling_description_to_show_on_overview_screen())
        box.addWidget(label)

        self.enable_markdown = QCheckBox(tr.deck_config_description_markdown())
        self.enable_markdown.setToolTip(tr.deck_config_description_markdown_hint())
        self.enable_markdown.setChecked(self.deck.normal.markdown_description)
        box.addWidget(self.enable_markdown)

        self.description = QPlainTextEdit()
        self.description.setPlainText(self.deck.normal.description)
        box.addWidget(self.description)

        button_box = QDialogButtonBox()
        ok = button_box.addButton(QDialogButtonBox.Ok)
        qconnect(ok.clicked, self.save_and_accept)
        box.addWidget(button_box)

        self.setLayout(box)
        self.show()

    def save_and_accept(self) -> None:
        self.deck.normal.description = self.description.toPlainText()
        self.deck.normal.markdown_description = self.enable_markdown.isChecked()

        update_deck(parent=self, deck=self.deck).success(
            lambda _: self.accept()
        ).run_in_background()

    def accept(self) -> None:
        saveGeom(self, self.TITLE)
        QDialog.accept(self)
