# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""RWKV-Instant time budgets.

A deck with a time budget (and its subdecks) is studied by predicted recall
instead of due date; see rslib/src/scheduler/time_budget. This module holds
the settings dialog and the helpers used to show the remaining time.
"""

from __future__ import annotations

import math
from collections.abc import Callable, Sequence
from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Any

import aqt
from anki.cards import Card
from anki.collection import Collection
from anki.decks import DeckId
from anki.scheduler.base import TimeBudgetDeck
from aqt import gui_hooks
from aqt.operations import QueryOp
from aqt.qt import *
from aqt.utils import disable_help_button, restoreGeom, saveGeom, showWarning, tr

if TYPE_CHECKING:
    import aqt.main

CONFIG_KEY = "rwkvTimeBudget"

DEFAULT_MINUTES = 30
DEFAULT_MAX_REVIEWS_PER_CARD = 3
DEFAULT_MIN_DR = 60
DEFAULT_MAX_DR = 95

# Groups of cards a session draws from; see CardGroup in
# rslib/src/scheduler/time_budget.
FIRST_REVIEW = "firstReview"
NEW_CARDS = "new"
RE_REVIEW = "reReview"
DEFAULT_ORDER = (FIRST_REVIEW, NEW_CARDS, RE_REVIEW)


def complete_order(order: Sequence[str]) -> list[str]:
    """Each known group once: `order` first, then the rest in default order."""
    out: list[str] = []
    for group in [*order, *DEFAULT_ORDER]:
        if group in DEFAULT_ORDER and group not in out:
            out.append(group)
    return out


def group_label(group: str) -> str:
    return {
        FIRST_REVIEW: tr.qt_misc_time_budget_first_review,
        NEW_CARDS: tr.qt_misc_time_budget_new_cards,
        RE_REVIEW: tr.qt_misc_time_budget_re_review,
    }[group]()


WEEKDAYS = 7


@dataclass
class DeckOverrides:
    """What one deck uses in place of the shared settings.

    An unset (None) field follows the shared setting. Keep the format in
    step with DeckOverrides in rslib.
    """

    max_reviews_per_card: int | None = None
    min_dr: int | None = None
    max_dr: int | None = None
    limit_new_cards: bool | None = None
    # study time for each day of the week, Monday first, in place of the
    # deck's single daily time
    weekday_minutes: list[int] | None = None

    def any_set(self) -> bool:
        return any(
            value is not None
            for value in (
                self.max_reviews_per_card,
                self.min_dr,
                self.max_dr,
                self.limit_new_cards,
                self.weekday_minutes,
            )
        )

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> DeckOverrides:
        def number(key: str) -> int | None:
            try:
                return None if data.get(key) is None else int(data[key])
            except (TypeError, ValueError):
                return None

        minutes = data.get("weekdayMinutes")
        weekday_minutes = None
        if isinstance(minutes, list):
            try:
                weekday_minutes = [int(value) for value in minutes[:WEEKDAYS]]
            except (TypeError, ValueError):
                weekday_minutes = None
        limit = data.get("limitNewCards")
        return cls(
            max_reviews_per_card=number("maxReviewsPerCard"),
            min_dr=number("minDr"),
            max_dr=number("maxDr"),
            limit_new_cards=None if limit is None else bool(limit),
            weekday_minutes=weekday_minutes,
        )

    def to_json(self) -> dict[str, Any]:
        """Only the settings that are set, so an unset one keeps following
        the shared setting even in an older version."""
        out: dict[str, Any] = {}
        for key, value in (
            ("maxReviewsPerCard", self.max_reviews_per_card),
            ("minDr", self.min_dr),
            ("maxDr", self.max_dr),
            ("limitNewCards", self.limit_new_cards),
            ("weekdayMinutes", self.weekday_minutes),
        ):
            if value is not None:
                out[key] = value
        return out

    def minutes_range(self) -> tuple[int, int] | None:
        """The least and most time any day of the week is given."""
        if not self.weekday_minutes:
            return None
        return (min(self.weekday_minutes), max(self.weekday_minutes))


@dataclass
class DeckBudget:
    enabled: bool = True
    minutes: int = DEFAULT_MINUTES
    overrides: DeckOverrides = field(default_factory=DeckOverrides)


@dataclass
class TimeBudgetSettings:
    """The time per deck, and the limits shared by all enabled decks."""

    decks: dict[DeckId, DeckBudget] = field(default_factory=dict)
    max_reviews_per_card: int = DEFAULT_MAX_REVIEWS_PER_CARD
    min_dr: int = DEFAULT_MIN_DR
    max_dr: int = DEFAULT_MAX_DR
    order: list[str] = field(default_factory=lambda: list(DEFAULT_ORDER))
    # stop new cards at the daily new card limits of the deck and subdecks
    limit_new_cards: bool = True

    @classmethod
    def from_json(cls, data: dict[str, Any]) -> TimeBudgetSettings:
        default = cls()
        decks: dict[DeckId, DeckBudget] = {}
        for deck_id, deck in (data.get("decks") or {}).items():
            try:
                decks[DeckId(int(deck_id))] = DeckBudget(
                    enabled=bool(deck.get("enabled", True)),
                    minutes=int(deck.get("minutes", DEFAULT_MINUTES)),
                    overrides=DeckOverrides.from_json(deck.get("overrides") or {}),
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
            order=complete_order(data.get("order") or []),
            limit_new_cards=bool(data.get("limitNewCards", default.limit_new_cards)),
        )

    def to_json(self) -> dict[str, Any]:
        def deck_json(deck: DeckBudget) -> dict[str, Any]:
            out: dict[str, Any] = {"enabled": deck.enabled, "minutes": deck.minutes}
            # left out entirely when the deck follows every shared setting
            if deck.overrides.any_set():
                out["overrides"] = deck.overrides.to_json()
            return out

        return {
            "decks": {str(did): deck_json(deck) for did, deck in self.decks.items()},
            "maxReviewsPerCard": self.max_reviews_per_card,
            "minDr": self.min_dr,
            "maxDr": self.max_dr,
            "order": self.order,
            "limitNewCards": self.limit_new_cards,
        }


def load_settings(col: Collection) -> TimeBudgetSettings:
    return TimeBudgetSettings.from_json(col.get_config(CONFIG_KEY, {}) or {})


def save_settings(col: Collection, settings: TimeBudgetSettings) -> None:
    col.set_config(CONFIG_KEY, settings.to_json())


def remaining_secs_by_deck(status: Sequence[TimeBudgetDeck]) -> dict[DeckId, int]:
    return {DeckId(deck.deck_id): deck.remaining_secs for deck in status}


def status_by_deck(status: Sequence[TimeBudgetDeck]) -> dict[DeckId, TimeBudgetDeck]:
    return {DeckId(deck.deck_id): deck for deck in status}


def format_status(deck: TimeBudgetDeck) -> str:
    """The time left, or 0 min once nothing is left to study today."""
    return format_remaining(0 if deck.done else deck.remaining_secs)


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


@dataclass
class DeckRow:
    """The widgets of one row of the deck table."""

    deck_id: DeckId
    name: str
    enabled: QCheckBox
    enabled_cell: QWidget
    name_item: QTableWidgetItem
    minutes: QSpinBox
    # shown in the minutes column instead when a week of times is set
    minutes_range: QLabel
    cog: QToolButton


class TimeBudgetDialog(QDialog):
    """Time per top-level deck, with the shared limits behind a toggle and
    each deck's overrides behind its cog."""

    COL_ENABLED = 0
    COL_DECK = 1
    COL_MINUTES = 2
    COL_OVERRIDES = 3
    DISABLED_OPACITY = 0.5
    # the cog only stands out for a deck that overrides something
    COG_UNSET_OPACITY = 0.3

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

        # Each deck's overrides are edited on their own page, which takes
        # the place of the deck list until it is closed again.
        self.overrides: dict[DeckId, DeckOverrides] = {
            did: deck.overrides for did, deck in self.settings.decks.items()
        }
        for did, _name in self.decks:
            self.overrides.setdefault(did, DeckOverrides())

        self.stack = QStackedWidget(self)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(0, 0, 0, 0)
        outer.addWidget(self.stack)
        main_page = QWidget(self)
        layout = QVBoxLayout(main_page)
        intro = QLabel(tr.qt_misc_time_budget_intro())
        intro.setWordWrap(True)
        layout.addWidget(intro)

        self.table = QTableWidget(len(self.decks), 4, self)
        self.table.setHorizontalHeaderLabels(
            [
                tr.qt_misc_time_budget_enabled(),
                tr.qt_misc_time_budget_deck(),
                tr.qt_misc_time_budget_minutes(),
                "",
            ]
        )
        vertical = self.table.verticalHeader()
        if vertical is not None:
            vertical.setVisible(False)
        header = self.table.horizontalHeader()
        if header is not None:
            header.setSectionResizeMode(QHeaderView.ResizeMode.ResizeToContents)
            header.setSectionResizeMode(self.COL_DECK, QHeaderView.ResizeMode.Stretch)
        self.rows: list[DeckRow] = []
        for row, (did, name) in enumerate(self.decks):
            self._populate_row(row, did, name, self.settings.decks.get(did))
        for row in range(len(self.rows)):
            self._update_row(row)
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
        self.limit_new_cards = QCheckBox(tr.qt_misc_time_budget_limit_new_cards(), self)
        self.limit_new_cards.setChecked(self.settings.limit_new_cards)
        self.limit_new_cards.setToolTip(tr.qt_misc_time_budget_help_limit_new_cards())
        form.addRow(self.limit_new_cards)

        # card order: drag to reorder
        self.order = QListWidget(self)
        self.order.setDragDropMode(QAbstractItemView.DragDropMode.InternalMove)
        self.order.setDefaultDropAction(Qt.DropAction.MoveAction)
        for group in complete_order(self.settings.order):
            item = QListWidgetItem(group_label(group))
            item.setData(Qt.ItemDataRole.UserRole, group)
            self.order.addItem(item)
        self.order.setToolTip(tr.qt_misc_time_budget_order_help())
        row_height = self.order.sizeHintForRow(0)
        self.order.setFixedHeight(row_height * self.order.count() + 6)
        self.order.setFixedWidth(self.order.sizeHintForColumn(0) + 40)
        order_label = QLabel(tr.qt_misc_time_budget_order())
        order_label.setToolTip(tr.qt_misc_time_budget_order_help())
        form.addRow(order_label, self.order)
        hint = QLabel(tr.qt_misc_time_budget_order_drag())
        hint.setEnabled(False)
        form.addRow("", hint)
        layout.addWidget(self.advanced)
        self._set_advanced_visible(False)

        buttons = QDialogButtonBox(
            QDialogButtonBox.StandardButton.Ok | QDialogButtonBox.StandardButton.Cancel
        )
        qconnect(buttons.accepted, self.accept)
        qconnect(buttons.rejected, self.reject)
        layout.addWidget(buttons)
        self.stack.addWidget(main_page)

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

    def _populate_row(
        self, row: int, deck_id: DeckId, name: str, deck: DeckBudget | None
    ) -> None:
        # The table shows its cell widgets when laying out, so each widget
        # sits in a container whose contents can be hidden or faded.
        def container(*widgets: QWidget) -> QWidget:
            cell = QWidget(self.table)
            layout = QHBoxLayout(cell)
            layout.setContentsMargins(0, 0, 0, 0)
            for widget in widgets:
                widget.setParent(cell)
                layout.addWidget(widget)
            return cell

        enabled = QCheckBox()
        enabled.setChecked(deck is not None and deck.enabled)
        enabled_cell = container(enabled)
        enabled_cell.layout().setAlignment(Qt.AlignmentFlag.AlignCenter)  # type: ignore[union-attr]
        self.table.setCellWidget(row, self.COL_ENABLED, enabled_cell)

        item = QTableWidgetItem(name)
        item.setFlags(item.flags() & ~Qt.ItemFlag.ItemIsEditable)
        self.table.setItem(row, self.COL_DECK, item)

        minutes = self._spin(1, 24 * 60, (deck or DeckBudget()).minutes)
        minutes_range = QLabel()
        minutes_range.setEnabled(False)
        minutes_range.setToolTip(tr.qt_misc_time_budget_help_weekday_minutes())
        self.table.setCellWidget(
            row, self.COL_MINUTES, container(minutes, minutes_range)
        )

        cog = QToolButton()
        cog.setText("\N{GEAR}\N{VARIATION SELECTOR-16}")
        cog.setAutoRaise(True)
        cog.setToolTip(tr.qt_misc_time_budget_overrides_open(deck=name))
        qconnect(cog.clicked, lambda _checked=False, row=row: self._show_overrides(row))
        cog_cell = container(cog)
        cog_cell.layout().setAlignment(Qt.AlignmentFlag.AlignRight)  # type: ignore[union-attr]
        self.table.setCellWidget(row, self.COL_OVERRIDES, cog_cell)

        self.rows.append(
            DeckRow(
                deck_id=deck_id,
                name=name,
                enabled=enabled,
                enabled_cell=enabled_cell,
                name_item=item,
                minutes=minutes,
                minutes_range=minutes_range,
                cog=cog,
            )
        )
        qconnect(enabled.toggled, lambda _checked, row=row: self._update_row(row))

    def _update_row(self, index: int) -> None:
        """Fades a deck that is not enabled, and shows its time as a range
        when a week of times replaces the single daily one."""
        row = self.rows[index]
        checked = row.enabled.isChecked()
        opacity = 1.0 if checked else self.DISABLED_OPACITY
        effect = QGraphicsOpacityEffect(row.enabled_cell)
        effect.setOpacity(opacity)
        row.enabled_cell.setGraphicsEffect(effect)
        colour = self.table.palette().color(QPalette.ColorRole.Text)
        colour.setAlphaF(opacity)
        row.name_item.setForeground(colour)

        overrides = self.overrides[row.deck_id]
        span = overrides.minutes_range()
        row.minutes.setVisible(checked and span is None)
        row.minutes_range.setVisible(checked and span is not None)
        if span is not None:
            low, high = span
            row.minutes_range.setText(
                str(low)
                if low == high
                else tr.qt_misc_time_budget_minutes_range(min=str(low), max=str(high))
            )
        cog_effect = QGraphicsOpacityEffect(row.cog)
        cog_effect.setOpacity(1.0 if overrides.any_set() else self.COG_UNSET_OPACITY)
        row.cog.setGraphicsEffect(cog_effect)

    def shared_from_dialog(self) -> TimeBudgetSettings:
        """The shared settings as they stand, which the decks that do not
        override them follow."""
        return TimeBudgetSettings(
            max_reviews_per_card=self.max_reviews.value(),
            min_dr=self.min_dr.value(),
            max_dr=self.max_dr.value(),
            limit_new_cards=self.limit_new_cards.isChecked(),
        )

    def _show_overrides(self, index: int) -> None:
        row = self.rows[index]
        page = DeckOverridesPage(
            self,
            deck_name=row.name,
            overrides=self.overrides[row.deck_id],
            shared=self.shared_from_dialog(),
            minutes=row.minutes.value(),
            on_back=lambda: self._close_overrides(index),
        )
        self.stack.addWidget(page)
        self.stack.setCurrentWidget(page)
        self.setWindowTitle(tr.qt_misc_time_budget_overrides_for(deck=row.name))

    def _close_overrides(self, index: int) -> None:
        page = self.stack.currentWidget()
        if not isinstance(page, DeckOverridesPage):
            return
        overrides = page.overrides_from_page()
        if overrides is None:
            return
        self.overrides[self.rows[index].deck_id] = overrides
        self.stack.setCurrentIndex(0)
        self.stack.removeWidget(page)
        page.deleteLater()
        self.setWindowTitle(tr.qt_misc_time_budget_title())
        self._update_row(index)

    def selected_order(self) -> list[str]:
        return complete_order(
            [
                item.data(Qt.ItemDataRole.UserRole)
                for row in range(self.order.count())
                if (item := self.order.item(row)) is not None
            ]
        )

    def settings_from_dialog(self) -> TimeBudgetSettings | None:
        """Updated settings, or None (after warning) if invalid. Settings of
        decks that are not shown are kept."""
        if self.min_dr.value() > self.max_dr.value():
            self.advanced_toggle.setChecked(True)
            showWarning(tr.qt_misc_time_budget_min_above_max(), parent=self)
            return None
        shared = self.shared_from_dialog()
        for index, row in enumerate(self.rows):
            # a deck that overrides only one end of the band can fall foul
            # of a later change to the shared one
            overrides = self.overrides[row.deck_id]
            min_dr = overrides.min_dr if overrides.min_dr is not None else shared.min_dr
            max_dr = overrides.max_dr if overrides.max_dr is not None else shared.max_dr
            if min_dr > max_dr:
                self._show_overrides(index)
                showWarning(tr.qt_misc_time_budget_min_above_max(), parent=self)
                return None
        decks = dict(self.settings.decks)
        for row in self.rows:
            if row.enabled.isChecked() or row.deck_id in decks:
                decks[row.deck_id] = DeckBudget(
                    enabled=row.enabled.isChecked(),
                    minutes=row.minutes.value(),
                    overrides=self.overrides[row.deck_id],
                )
        return TimeBudgetSettings(
            decks=decks,
            max_reviews_per_card=self.max_reviews.value(),
            min_dr=self.min_dr.value(),
            max_dr=self.max_dr.value(),
            order=self.selected_order(),
            limit_new_cards=self.limit_new_cards.isChecked(),
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


class DeckOverridesPage(QWidget):
    """What one deck uses in place of the shared settings.

    Shown in place of the deck list, and read back into the dialog when it
    is closed; nothing is saved until the dialog itself is accepted.
    """

    def __init__(
        self,
        parent: TimeBudgetDialog,
        deck_name: str,
        overrides: DeckOverrides,
        shared: TimeBudgetSettings,
        minutes: int,
        on_back: Callable[[], None],
    ) -> None:
        super().__init__(parent)
        self.shared = shared

        layout = QVBoxLayout(self)
        heading = QLabel(tr.qt_misc_time_budget_overrides_for(deck=deck_name))
        font = heading.font()
        font.setBold(True)
        heading.setFont(font)
        heading.setWordWrap(True)
        layout.addWidget(heading)
        intro = QLabel(tr.qt_misc_time_budget_overrides_intro())
        intro.setWordWrap(True)
        layout.addWidget(intro)

        form = QFormLayout()
        form.setFieldGrowthPolicy(QFormLayout.FieldGrowthPolicy.FieldsStayAtSizeHint)
        self.override_max_reviews, self.max_reviews = self._number(
            form,
            tr.qt_misc_time_budget_max_reviews(),
            tr.qt_misc_time_budget_help_max_reviews(),
            overrides.max_reviews_per_card,
            shared.max_reviews_per_card,
            1,
            99,
        )
        self.override_min_dr, self.min_dr = self._number(
            form,
            tr.qt_misc_time_budget_min_dr(),
            tr.qt_misc_time_budget_help_min_dr(),
            overrides.min_dr,
            shared.min_dr,
            1,
            99,
            "%",
        )
        self.override_max_dr, self.max_dr = self._number(
            form,
            tr.qt_misc_time_budget_max_dr(),
            tr.qt_misc_time_budget_help_max_dr(),
            overrides.max_dr,
            shared.max_dr,
            1,
            100,
            "%",
        )

        self.override_limit_new_cards = QCheckBox(
            tr.qt_misc_time_budget_limit_new_cards(), self
        )
        self.override_limit_new_cards.setChecked(overrides.limit_new_cards is not None)
        self.limit_new_cards = QCheckBox(self)
        self.limit_new_cards.setChecked(
            shared.limit_new_cards
            if overrides.limit_new_cards is None
            else overrides.limit_new_cards
        )
        for widget in (self.override_limit_new_cards, self.limit_new_cards):
            widget.setToolTip(tr.qt_misc_time_budget_help_limit_new_cards())
        self.limit_new_cards.setEnabled(self.override_limit_new_cards.isChecked())
        qconnect(self.override_limit_new_cards.toggled, self.limit_new_cards.setEnabled)
        form.addRow(self.override_limit_new_cards, self.limit_new_cards)

        # a time of its own for each day of the week, in place of the
        # deck's single daily time
        self.override_weekday_minutes = QCheckBox(
            tr.qt_misc_time_budget_weekday_minutes(), self
        )
        self.override_weekday_minutes.setChecked(overrides.weekday_minutes is not None)
        self.override_weekday_minutes.setToolTip(
            tr.qt_misc_time_budget_help_weekday_minutes()
        )
        days = QWidget(self)
        grid = QGridLayout(days)
        grid.setContentsMargins(0, 0, 0, 0)
        self.weekday_minutes: list[QSpinBox] = []
        set_minutes = overrides.weekday_minutes or []
        locale = QLocale()
        for day in range(WEEKDAYS):
            spin = QSpinBox(days)
            spin.setRange(1, 24 * 60)
            spin.setValue(set_minutes[day] if day < len(set_minutes) else minutes)
            # QLocale counts Monday as 1 and Sunday as 7
            label = QLabel(
                locale.standaloneDayName(day + 1, QLocale.FormatType.ShortFormat), days
            )
            grid.addWidget(label, 0, day, Qt.AlignmentFlag.AlignHCenter)
            grid.addWidget(spin, 1, day)
            self.weekday_minutes.append(spin)
        days.setEnabled(self.override_weekday_minutes.isChecked())
        qconnect(self.override_weekday_minutes.toggled, days.setEnabled)
        form.addRow(self.override_weekday_minutes)
        form.addRow("", days)
        layout.addLayout(form)
        layout.addStretch(1)

        buttons = QDialogButtonBox()
        back = buttons.addButton(
            tr.qt_misc_time_budget_overrides_back(),
            QDialogButtonBox.ButtonRole.AcceptRole,
        )
        assert back is not None
        qconnect(back.clicked, on_back)
        layout.addWidget(buttons)

    def _number(
        self,
        form: QFormLayout,
        label: str,
        help_text: str,
        value: int | None,
        shared_value: int,
        minimum: int,
        maximum: int,
        suffix: str = "",
    ) -> tuple[QCheckBox, QSpinBox]:
        """A setting the deck may override, shown with the shared value
        until it does."""
        box = QCheckBox(label, self)
        box.setChecked(value is not None)
        spin = QSpinBox(self)
        spin.setRange(minimum, maximum)
        spin.setValue(
            max(minimum, min(maximum, shared_value if value is None else value))
        )
        if suffix:
            spin.setSuffix(suffix)
        spin.setEnabled(box.isChecked())
        qconnect(box.toggled, spin.setEnabled)
        for widget in (box, spin):
            widget.setToolTip(help_text)
        form.addRow(box, spin)
        return (box, spin)

    def overrides_from_page(self) -> DeckOverrides | None:
        """The chosen overrides, or None (after warning) if invalid."""
        min_dr = (
            self.min_dr.value()
            if self.override_min_dr.isChecked()
            else self.shared.min_dr
        )
        max_dr = (
            self.max_dr.value()
            if self.override_max_dr.isChecked()
            else self.shared.max_dr
        )
        if min_dr > max_dr:
            showWarning(tr.qt_misc_time_budget_min_above_max(), parent=self)
            return None
        return DeckOverrides(
            max_reviews_per_card=(
                self.max_reviews.value()
                if self.override_max_reviews.isChecked()
                else None
            ),
            min_dr=self.min_dr.value() if self.override_min_dr.isChecked() else None,
            max_dr=self.max_dr.value() if self.override_max_dr.isChecked() else None,
            limit_new_cards=(
                self.limit_new_cards.isChecked()
                if self.override_limit_new_cards.isChecked()
                else None
            ),
            weekday_minutes=(
                [spin.value() for spin in self.weekday_minutes]
                if self.override_weekday_minutes.isChecked()
                else None
            ),
        )


def open_time_budget_dialog(mw: aqt.main.AnkiQt) -> TimeBudgetDialog:
    return TimeBudgetDialog(mw)


def _prefetch_after_question(_card: Card) -> None:
    """Score the next batch of a time-budget session while the question is
    on screen, so the next card rarely waits for it. A no-op unless the
    current batch is running low (see rslib/src/scheduler/time_budget)."""
    mw = aqt.mw
    if mw is None or mw.col is None:
        return
    QueryOp(
        parent=mw,
        op=lambda col: col.sched.prefetch_time_budget_cards(),
        success=lambda _scored: None,
    ).failure(lambda _err: None).run_in_background()


gui_hooks.reviewer_did_show_question.append(_prefetch_after_question)
