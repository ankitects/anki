# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt.forms
import aqt.main
from anki.decks import DeckDict
from aqt.operations.deck import update_deck_dict
from aqt.qt import *
from aqt.utils import disable_help_button


class DeckLimitsDialog(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt, deck: DeckDict):
        super().__init__(parent=mw)
        self.mw = mw
        self.col = mw.col
        self.deck = deck
        self.frm = aqt.forms.deck_limits.Ui_Dialog()
        self.frm.setupUi(self)
        disable_help_button(self)
        self._setup()
        self.open()

    def _setup(self) -> None:
        review_limit = self.deck["reviewLimit"]
        new_limit = self.deck["newLimit"]
        self.frm.review_limit.setValue(review_limit or 0)
        self.frm.new_limit.setValue(new_limit or 0)

        enabled = None not in (review_limit, new_limit)
        self.frm.enable_override.setChecked(enabled)
        self.frm.review_limit.setEnabled(enabled)
        self.frm.new_limit.setEnabled(enabled)

        qconnect(self.frm.enable_override.toggled, self._on_override_toggled)

    def _on_override_toggled(self) -> None:
        enabled = self.frm.enable_override.isChecked()
        self.frm.review_limit.setEnabled(enabled)
        self.frm.new_limit.setEnabled(enabled)

    def accept(self) -> None:
        if self.frm.enable_override.isChecked():
            self.deck["reviewLimit"] = self.frm.review_limit.value()
            self.deck["newLimit"] = self.frm.new_limit.value()
        else:
            self.deck["reviewLimit"] = self.deck["newLimit"] = None
        update_deck_dict(parent=self.mw, deck=self.deck).run_in_background()
        super().accept()
