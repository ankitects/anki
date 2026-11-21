# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki.collection import Collection
from anki.decks import DeckId
from aqt.time_budget import (
    DeckTimeBudget,
    format_remaining,
    load_budgets,
    remaining_for_deck,
    save_budgets,
)


def _collection() -> Collection:
    (fd, path) = tempfile.mkstemp(suffix=".anki2")
    os.close(fd)
    os.unlink(path)
    return Collection(path)


def test_budgets_round_trip_with_defaults() -> None:
    col = _collection()
    try:
        assert load_budgets(col) == {}
        parent = DeckId(col.decks.id("Languages"))
        save_budgets(col, {parent: DeckTimeBudget(minutes=65)})
        assert load_budgets(col) == {
            parent: DeckTimeBudget(
                enabled=True, minutes=65, max_reviews_per_card=3, min_dr=60, max_dr=95
            )
        }
        assert col.get_config("rwkvTimeBudget")["decks"][str(parent)]["minDr"] == 60
    finally:
        col.close()


def test_remaining_time_covers_subdecks() -> None:
    col = _collection()
    try:
        parent = DeckId(col.decks.id("Languages"))
        child = DeckId(col.decks.id("Languages::Spanish"))
        other = DeckId(col.decks.id("Other"))
        save_budgets(col, {parent: DeckTimeBudget(minutes=65)})

        status = col.sched.time_budget_status()
        assert [(d.deck_id, d.budget_secs, d.remaining_secs) for d in status] == [
            (parent, 65 * 60, 65 * 60)
        ]
        assert remaining_for_deck(col, parent) == 65 * 60
        assert remaining_for_deck(col, child) == 65 * 60
        assert remaining_for_deck(col, other) is None

        # a disabled budget no longer applies
        save_budgets(col, {parent: DeckTimeBudget(enabled=False)})
        assert remaining_for_deck(col, child) is None
    finally:
        col.close()


def _plain(text: str) -> str:
    # strip Fluent's bidi isolation marks
    return text.replace("\u2068", "").replace("\u2069", "")


def test_format_remaining_rounds_up_and_clamps() -> None:
    assert _plain(format_remaining(65 * 60)) == "65 min"
    assert _plain(format_remaining(61)) == "2 min"
    assert _plain(format_remaining(-30)) == "0 min"
