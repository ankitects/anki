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
from dataclasses import dataclass
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
class DeckTimeBudget:
    enabled: bool = True
    minutes: int = DEFAULT_MINUTES
    max_reviews_per_card: int = DEFAULT_MAX_REVIEWS_PER_CARD
    min_dr: int = DEFAULT_MIN_DR
    max_dr: int = DEFAULT_MAX_DR

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> DeckTimeBudget:
        default = cls()
        return cls(
            enabled=bool(data.get("enabled", default.enabled)),
            minutes=int(data.get("minutes", default.minutes)),
            max_reviews_per_card=int(
                data.get("maxReviewsPerCard", default.max_reviews_per_card)
            ),
            min_dr=int(data.get("minDr", default.min_dr)),
            max_dr=int(data.get("maxDr", default.max_dr)),
        )

    def to_json(self) -> dict[str, Any]:
        return {
            "enabled": self.enabled,
            "minutes": self.minutes,
            "maxReviewsPerCard": self.max_reviews_per_card,
            "minDr": self.min_dr,
            "maxDr": self.max_dr,
        }


def load_budgets(col: Collection) -> dict[DeckId, DeckTimeBudget]:
    config = col.get_config(CONFIG_KEY, {}) or {}
    budgets: dict[DeckId, DeckTimeBudget] = {}
    for deck_id, data in (config.get("decks") or {}).items():
        try:
            budgets[DeckId(int(deck_id))] = DeckTimeBudget.from_json(data)
        except (TypeError, ValueError):
            continue
    return budgets


def save_budgets(col: Collection, budgets: dict[DeckId, DeckTimeBudget]) -> None:
    col.set_config(
        CONFIG_KEY,
        {"decks": {str(did): budget.to_json() for did, budget in budgets.items()}},
    )


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
    """Per-deck settings for top-level decks."""

    COL_DECK = 0
    COL_ENABLED = 1
    COL_MINUTES = 2
    COL_MAX_REVIEWS = 3
    COL_MIN_DR = 4
    COL_MAX_DR = 5

    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        super().__init__(mw)
        self.mw = mw
        self.setWindowTitle(tr.qt_misc_time_budget_title())
        disable_help_button(self)
        self.setWindowModality(Qt.WindowModality.ApplicationModal)

        col = mw.col
        assert col is not None
        self.budgets = load_budgets(col)
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

        self.table = QTableWidget(len(self.decks), 6, self)
        self.table.setHorizontalHeaderLabels(
            [
                tr.qt_misc_time_budget_deck(),
                tr.qt_misc_time_budget_enabled(),
                tr.qt_misc_time_budget_minutes(),
                tr.qt_misc_time_budget_max_reviews(),
                tr.qt_misc_time_budget_min_dr(),
                tr.qt_misc_time_budget_max_dr(),
            ]
        )
        for column, tip in (
            (self.COL_MAX_REVIEWS, tr.qt_misc_time_budget_help_max_reviews()),
            (self.COL_MIN_DR, tr.qt_misc_time_budget_help_min_dr()),
            (self.COL_MAX_DR, tr.qt_misc_time_budget_help_max_dr()),
        ):
            item = self.table.horizontalHeaderItem(column)
            if item is not None:
                item.setToolTip(tip)
        vertical = self.table.verticalHeader()
        if vertical is not None:
            vertical.setVisible(False)
        header = self.table.horizontalHeader()
        if header is not None:
            header.setSectionResizeMode(QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(self.COL_DECK, QHeaderView.ResizeMode.Stretch)
        for row, (did, name) in enumerate(self.decks):
            self._populate_row(row, name, self.budgets.get(did))
        layout.addWidget(self.table)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        qconnect(buttons.accepted, self.accept)
        qconnect(buttons.rejected, self.reject)
        layout.addWidget(buttons)

        self.resize(1000, 400)
        restoreGeom(self, "timeBudgets")
        self.show()

    def _spin(
        self, minimum: int, maximum: int, value: int, suffix: str = ""
    ) -> QSpinBox:
        spin = QSpinBox(self.table)
        spin.setRange(minimum, maximum)
        spin.setValue(max(minimum, min(maximum, value)))
        if suffix:
            spin.setSuffix(suffix)
        return spin

    def _populate_row(self, row: int, name: str, budget: DeckTimeBudget | None) -> None:
        item = QTableWidgetItem(name)
        item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
        self.table.setItem(row, self.COL_DECK, item)

        enabled = QCheckBox(self.table)
        enabled.setChecked(budget is not None and budget.enabled)
        self.table.setCellWidget(row, self.COL_ENABLED, enabled)

        budget = budget or DeckTimeBudget()
        self.table.setCellWidget(
            row, self.COL_MINUTES, self._spin(1, 24 * 60, budget.minutes)
        )
        self.table.setCellWidget(
            row,
            self.COL_MAX_REVIEWS,
            self._spin(1, 99, budget.max_reviews_per_card),
        )
        self.table.setCellWidget(
            row, self.COL_MIN_DR, self._spin(1, 99, budget.min_dr, "%")
        )
        self.table.setCellWidget(
            row, self.COL_MAX_DR, self._spin(1, 100, budget.max_dr, "%")
        )

    def _value(self, row: int, column: int) -> int:
        widget = self.table.cellWidget(row, column)
        assert isinstance(widget, QSpinBox)
        return widget.value()

    def _enabled(self, row: int) -> bool:
        widget = self.table.cellWidget(row, self.COL_ENABLED)
        assert isinstance(widget, QCheckBox)
        return widget.isChecked()

    def budgets_from_table(self) -> dict[DeckId, DeckTimeBudget] | None:
        """Updated settings, or None (after warning) if invalid. Settings of
        decks that are not shown are kept."""
        budgets = dict(self.budgets)
        for row, (did, name) in enumerate(self.decks):
            budget = DeckTimeBudget(
                enabled=self._enabled(row),
                minutes=self._value(row, self.COL_MINUTES),
                max_reviews_per_card=self._value(row, self.COL_MAX_REVIEWS),
                min_dr=self._value(row, self.COL_MIN_DR),
                max_dr=self._value(row, self.COL_MAX_DR),
            )
            if budget.enabled and budget.min_dr > budget.max_dr:
                showWarning(
                    tr.qt_misc_time_budget_min_above_max(deck=name), parent=self
                )
                return None
            if budget.enabled or did in budgets:
                budgets[did] = budget
        return budgets

    def accept(self) -> None:
        budgets = self.budgets_from_table()
        if budgets is None:
            return
        assert self.mw.col is not None
        save_budgets(self.mw.col, budgets)
        saveGeom(self, "timeBudgets")
        super().accept()
        self.mw.reset()

    def reject(self) -> None:
        saveGeom(self, "timeBudgets")
        super().reject()


def open_time_budget_dialog(mw: aqt.main.AnkiQt) -> TimeBudgetDialog:
    return TimeBudgetDialog(mw)
