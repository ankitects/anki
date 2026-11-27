# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""RWKV settings that apply outside of time budgets.

With "Enable RWKV on all decks", a deck that no time budget covers is also
studied weakest-first: its due cards are shown lowest predicted recall
first, within the deck's usual daily limits. The scheduling itself is in
rslib/src/scheduler/rwkv_options.rs; this module only holds the settings
dialog.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import TYPE_CHECKING, Any

import aqt
from anki.collection import Collection
from aqt.qt import *
from aqt.utils import disable_help_button, tr

if TYPE_CHECKING:
    import aqt.main

CONFIG_KEY = "rwkvOptions"


@dataclass
class RwkvOptions:
    """Keep the format in step with RwkvOptions in rslib."""

    # order every deck's queue by predicted recall, not only budget decks
    all_decks: bool = False

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> RwkvOptions:
        default = cls()
        return cls(all_decks=bool(data.get("allDecks", default.all_decks)))

    def to_json(self) -> dict[str, Any]:
        return {"allDecks": self.all_decks}


def load_options(col: Collection) -> RwkvOptions:
    return RwkvOptions.from_json(col.get_config(CONFIG_KEY, {}) or {})


def save_options(col: Collection, options: RwkvOptions) -> None:
    col.set_config(CONFIG_KEY, options.to_json())


class RwkvOptionsDialog(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__(mw)
        self.mw = mw
        self.setWindowTitle(tr.qt_misc_rwkv_options_title())
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModality.ApplicationModal)

        col = mw.col
        assert col is not None
        self.options = load_options(col)

        layout = QVBoxLayout(self)
        self.all_decks = QCheckBox(tr.qt_misc_rwkv_all_decks(), self)
        self.all_decks.setChecked(self.options.all_decks)
        self.all_decks.setToolTip(tr.qt_misc_rwkv_all_decks_help())
        layout.addWidget(self.all_decks)
        help_label = QLabel(tr.qt_misc_rwkv_all_decks_help())
        help_label.setWordWrap(True)
        help_label.setEnabled(False)
        layout.addWidget(help_label)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        qconnect(buttons.accepted, self.accept)
        qconnect(buttons.rejected, self.reject)
        layout.addWidget(buttons)

        self.setMinimumWidth(420)
        self.show()

    def options_from_dialog(self) -> RwkvOptions:
        return RwkvOptions(all_decks=self.all_decks.isChecked())

    def accept(self) -> None:
        assert self.mw.col is not None
        save_options(self.mw.col, self.options_from_dialog())
        super().accept()
        self.mw.reset()


def open_rwkv_options_dialog(mw: aqt.main.AnkiQt) -> RwkvOptionsDialog:
    return RwkvOptionsDialog(mw)
