# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""RWKV-Instant time budgets.

A deck with a time budget (and its subdecks) is studied by predicted recall
instead of due date; see rslib/src/scheduler/time_budget. This module holds
the settings dialog and the helpers used to show the remaining time.
"""

from __future__ import annotations

import math
from collections.abc import Sequence
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

from anki.collection import Collection
from anki.decks import DeckId
from anki.scheduler.base import TimeBudgetDeck
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, showWarning, tr

if TYPE_CHECKING:
    import aqt.main

CONFIG_KEY = "rwkvTimeBudget"

DEFAULT_MINUTES = 30
DEFAULT_MAX_REVIEWS_PER_CARD = 3
DEFAULT_MIN_DR = 60
DEFAULT_MAX_DR = 95


@dataclass
class DeckBudget:
    enabled: bool = True
    minutes: int = DEFAULT_MINUTES


@dataclass
class TimeBudgetSettings:
    """The time per deck, and the limits shared by all enabled decks."""

    decks: dict[DeckId, DeckBudget] = field(default_factory=dict)
    max_reviews_per_card: int = DEFAULT_MAX_REVIEWS_PER_CARD
    min_dr: int = DEFAULT_MIN_DR
    max_dr: int = DEFAULT_MAX_DR

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> TimeBudgetSettings:
        default = cls()
        decks: dict[DeckId, DeckBudget] = {}
        for deck_id, deck in (data.get("decks") or {}).items():
            try:
                decks[DeckId(int(deck_id))] = DeckBudget(
                    enabled=bool(deck.get("enabled", True)),
                    minutes=int(deck.get("minutes", DEFAULT_MINUTES)),
                )
            except (AttributeError, TypeError, ValueError):
                continue
        return cls(
            decks=decks,
            max_reviews_per_card=int(
                data.get("maxReviewsPerCard", default.max_reviews_per_card)
            ),
            min_dr=int(data.get("minDr", default.min_dr)),
            max_dr=int(data.get("maxDr", default.max_dr)),
        )

    def to_json(self) -> dict[str, Any]:
        return {
            "decks": {
                str(did): {"enabled": deck.enabled, "minutes": deck.minutes}
                for did, deck in self.decks.items()
            },
            "maxReviewsPerCard": self.max_reviews_per_card,
            "minDr": self.min_dr,
            "maxDr": self.max_dr,
        }


def load_settings(col: Collection) -> TimeBudgetSettings:
    return TimeBudgetSettings.from_json(col.get_config(CONFIG_KEY, {}) or {})


def save_settings(col: Collection, settings: TimeBudgetSettings) -> None:
    col.set_config(CONFIG_KEY, settings.to_json())


def remaining_secs_by_deck(status: Sequence[TimeBudgetDeck]) -> dict[DeckId, int]:
    return {DeckId(deck.deck_id): deck.remaining_secs for deck in status}


def format_remaining(remaining_secs: int) -> str:
    minutes = max(0, math.ceil(remaining_secs / 60))
    return tr.qt_misc_time_budget_minutes_left(minutes=str(minutes))


def remaining_for_deck(col: Collection, deck_id: DeckId) -> int | None:
    """Remaining seconds of the budget covering deck_id, if any."""
    remaining = remaining_secs_by_deck(col.sched.time_budget_status())
    if not remaining:
        return None
    deck = col.decks.get(deck_id, default=False)
    if not deck or deck["dyn"]:
        return None
    for did in [
        deck_id,
        *(parent["id"] for parent in reversed(col.decks.parents(deck_id))),
    ]:
        if did in remaining:
            return remaining[did]
    return None


class TimeBudgetDialog(QDialog):
    """Time per top-level deck, with the shared limits behind a toggle."""

    COL_DECK = 0
    COL_ENABLED = 1
    COL_MINUTES = 2

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__(mw)
        self.mw = mw
        self.setWindowTitle(tr.qt_misc_time_budget_title())
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModality.ApplicationModal)

        col = mw.col
        assert col is not None
        self.settings = load_settings(col)
        self.decks = [
            (DeckId(deck.id), deck.name)
            for deck in col.decks.all_names_and_ids(
                skip_empty_default=True, include_filtered=False
            )
            if "::" not in deck.name
        ]

        layout = QVBoxLayout(self)
        intro = QLabel(tr.qt_misc_time_budget_intro())
        intro.setWordWrap(True)
        layout.addWidget(intro)

        self.table = QTableWidget(len(self.decks), 3, self)
        self.table.setHorizontalHeaderLabels(
            [
                tr.qt_misc_time_budget_deck(),
                tr.qt_misc_time_budget_enabled(),
                tr.qt_misc_time_budget_minutes(),
            ]
        )
        vertical = self.table.verticalHeader()
        if vertical is not None:
            vertical.setVisible(False)
        header = self.table.horizontalHeader()
        if header is not None:
            header.setSectionResizeMode(QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(self.COL_DECK, QHeaderView.ResizeMode.Stretch)
        for row, (did, name) in enumerate(self.decks):
            self._populate_row(row, name, self.settings.decks.get(did))
        layout.addWidget(self.table)

        self.advanced_toggle = QToolButton(self)
        self.advanced_toggle.setCheckable(True)
        self.advanced_toggle.setToolButtonStyle(
            Qt.ToolButtonStyle.ToolButtonTextBesideIcon
        )
        self.advanced_toggle.setAutoRaise(True)
        qconnect(self.advanced_toggle.toggled, self._set_advanced_visible)
        layout.addWidget(self.advanced_toggle)

        self.advanced = QWidget(self)
        form = QFormLayout(self.advanced)
        form.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.FieldsStayAtSizeHint)
        note = QLabel(tr.qt_misc_time_budget_advanced_intro())
        note.setWordWrap(True)
        form.addRow(note)
        self.max_reviews = self._spin(1, 99, self.settings.max_reviews_per_card)
        self.min_dr = self._spin(1, 99, self.settings.min_dr, "%")
        self.max_dr = self._spin(1, 100, self.settings.max_dr, "%")
        for label, spin, tip in (
            (
                tr.qt_misc_time_budget_max_reviews(),
                self.max_reviews,
                tr.qt_misc_time_budget_help_max_reviews(),
            ),
            (
                tr.qt_misc_time_budget_min_dr(),
                self.min_dr,
                tr.qt_misc_time_budget_help_min_dr(),
            ),
            (
                tr.qt_misc_time_budget_max_dr(),
                self.max_dr,
                tr.qt_misc_time_budget_help_max_dr(),
            ),
        ):
            spin.setToolTip(tip)
            form.addRow(label, spin)
        layout.addWidget(self.advanced)
        self._set_advanced_visible(False)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        qconnect(buttons.accepted, self.accept)
        qconnect(buttons.rejected, self.reject)
        layout.addWidget(buttons)

        self.resize(600, 400)
        restoreGeom(self, "timeBudgets")
        self.show()

    def _set_advanced_visible(self, visible: bool) -> None:
        self.advanced.setVisible(visible)
        self.advanced_toggle.setArrowType(
            Qt.ArrowType.DownArrow if visible else Qt.ArrowType.RightArrow
        )
        self.advanced_toggle.setText(
            tr.qt_misc_time_budget_hide_advanced()
            if visible
            else tr.qt_misc_time_budget_show_advanced()
        )

    def _spin(
        self, minimum: int, maximum: int, value: int, suffix: str = ""
    ) -> QSpinBox:
        spin = QSpinBox(self)
        spin.setRange(minimum, maximum)
        spin.setValue(max(minimum, min(maximum, value)))
        if suffix:
            spin.setSuffix(suffix)
        return spin

    def _populate_row(self, row: int, name: str, deck: DeckBudget | None) -> None:
        item = QTableWidgetItem(name)
        item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
        self.table.setItem(row, self.COL_DECK, item)

        enabled = QCheckBox(self.table)
        enabled.setChecked(deck is not None and deck.enabled)
        self.table.setCellWidget(row, self.COL_ENABLED, enabled)

        minutes = self._spin(1, 24 * 60, (deck or DeckBudget()).minutes)
        minutes.setParent(self.table)
        self.table.setCellWidget(row, self.COL_MINUTES, minutes)

    def settings_from_dialog(self) -> TimeBudgetSettings | None:
        """Updated settings, or None (after warning) if invalid. Settings of
        decks that are not shown are kept."""
        if self.min_dr.value() > self.max_dr.value():
            self.advanced_toggle.setChecked(True)
            showWarning(tr.qt_misc_time_budget_min_above_max(), parent=self)
            return None
        decks = dict(self.settings.decks)
        for row, (did, _name) in enumerate(self.decks):
            enabled = self.table.cellWidget(row, self.COL_ENABLED)
            minutes = self.table.cellWidget(row, self.COL_MINUTES)
            assert isinstance(enabled, QCheckBox) and isinstance(minutes, QSpinBox)
            if enabled.isChecked() or did in decks:
                decks[did] = DeckBudget(
                    enabled=enabled.isChecked(), minutes=minutes.value()
                )
        return TimeBudgetSettings(
            decks=decks,
            max_reviews_per_card=self.max_reviews.value(),
            min_dr=self.min_dr.value(),
            max_dr=self.max_dr.value(),
        )

    def accept(self) -> None:
        settings = self.settings_from_dialog()
        if settings is None:
            return
        assert self.mw.col is not None
        save_settings(self.mw.col, settings)
        saveGeom(self, "timeBudgets")
        super().accept()
        self.mw.reset()

    def reject(self) -> None:
        saveGeom(self, "timeBudgets")
        super().reject()


def open_time_budget_dialog(mw: aqt.main.AnkiQt) -> TimeBudgetDialog:
    return TimeBudgetDialog(mw)
