# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.main
import aqt.operations
from anki.decks import DeckDict
from aqt.operations import QueryOp
from aqt.operations.deck import update_deck_dict
from aqt.qt import *
from aqt.utils import addCloseShortcut, disable_help_button, restoreGeom, saveGeom, tr


class DeckDescriptionDialog(QDialog):
    TITLE = "deckDescription"
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw

        # set on success
        self.deck: DeckDict

        QueryOp(
            parent=self.mw,
            op=lambda col: col.decks.current(),
            success=self._setup_and_show,
        ).run_in_background()

    def _setup_and_show(self, deck: DeckDict) -> None:
        if deck["dyn"]:
            return

        self.deck = deck
        self._setup_ui()
        self.show()

    def _setup_ui(self) -> None:
        self.setWindowTitle(tr.scheduling_description())
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        self.mw.garbage_collect_on_dialog_finish(self)
        self.setMinimumWidth(400)
        disable_help_button(self)
        restoreGeom(self, self.TITLE)
        addCloseShortcut(self)

        box = QVBoxLayout()

        self.enable_markdown = QCheckBox(tr.deck_config_description_new_handling())
        self.enable_markdown.setToolTip(tr.deck_config_description_new_handling_hint())
        self.enable_markdown.setChecked(self.deck.get("md", False))
        box.addWidget(self.enable_markdown)

        self.description = QPlainTextEdit()
        self.description.setPlainText(self.deck.get("desc", ""))
        box.addWidget(self.description)

        button_box = QDialogButtonBox()
        ok = button_box.addButton(QDialogButtonBox.StandardButton.Ok)
        qconnect(ok.clicked, self.save_and_accept)
        box.addWidget(button_box)

        self.setLayout(box)
        self.show()

    def save_and_accept(self) -> None:
        self.deck["desc"] = self.description.toPlainText()
        self.deck["md"] = self.enable_markdown.isChecked()

        update_deck_dict(parent=self, deck=self.deck).success(
            lambda _: self.accept()
        ).run_in_background()

    def accept(self) -> None:
        saveGeom(self, self.TITLE)
        QDialog.accept(self)
