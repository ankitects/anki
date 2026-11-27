# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki.collection import Collection
from anki.decks import DeckId
from aqt.time_budget import (
    DeckBudget,
    TimeBudgetSettings,
    format_remaining,
    load_settings,
    remaining_for_deck,
    save_settings,
)


def _collection() -> Collection:
    (fd, path) = tempfile.mkstemp(suffix=".anki2")
    os.close(fd)
    os.unlink(path)
    return Collection(path)


def test_settings_round_trip_with_shared_limits() -> None:
    col = _collection()
    try:
        assert load_settings(col) == TimeBudgetSettings()
        parent = DeckId(col.decks.id("Languages"))
        save_settings(
            col, TimeBudgetSettings(decks={parent: DeckBudget(minutes=65)}, min_dr=70)
        )
        assert load_settings(col) == TimeBudgetSettings(
            decks={parent: DeckBudget(enabled=True, minutes=65)},
            max_reviews_per_card=3,
            min_dr=70,
            max_dr=95,
        )
        assert col.get_config("rwkvTimeBudget") == {
            "decks": {str(parent): {"enabled": True, "minutes": 65}},
            "maxReviewsPerCard": 3,
            "minDr": 70,
            "maxDr": 95,
            "order": ["firstReview", "new", "reReview"],
            "limitNewCards": True,
        }
        save_settings(col, TimeBudgetSettings(limit_new_cards=False))
        assert not load_settings(col).limit_new_cards
    finally:
        col.close()


def test_remaining_time_covers_subdecks() -> None:
    col = _collection()
    try:
        parent = DeckId(col.decks.id("Languages"))
        child = DeckId(col.decks.id("Languages::Spanish"))
        other = DeckId(col.decks.id("Other"))
        save_settings(col, TimeBudgetSettings(decks={parent: DeckBudget(minutes=65)}))

        status = col.sched.time_budget_status()
        assert [(d.deck_id, d.budget_secs, d.remaining_secs) for d in status] == [
            (parent, 65 * 60, 65 * 60)
        ]
        assert remaining_for_deck(col, parent) == 65 * 60
        assert remaining_for_deck(col, child) == 65 * 60
        assert remaining_for_deck(col, other) is None

        # a disabled budget no longer applies
        save_settings(
            col, TimeBudgetSettings(decks={parent: DeckBudget(enabled=False)})
        )
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


def test_done_decks_show_no_time_left() -> None:
    from anki.scheduler.base import TimeBudgetDeck
    from aqt.time_budget import format_status

    assert (
        _plain(format_status(TimeBudgetDeck(remaining_secs=6 * 60, done=True)))
        == "0 min"
    )
    assert (
        _plain(format_status(TimeBudgetDeck(remaining_secs=6 * 60, done=False)))
        == "6 min"
    )


def test_card_order_is_completed_and_kept() -> None:
    from aqt.time_budget import complete_order

    assert complete_order([]) == ["firstReview", "new", "reReview"]
    assert complete_order(["reReview", "bogus", "reReview"]) == [
        "reReview",
        "firstReview",
        "new",
    ]
    col = _collection()
    try:
        save_settings(col, TimeBudgetSettings(order=["new", "firstReview", "reReview"]))
        assert load_settings(col).order == ["new", "firstReview", "reReview"]
    finally:
        col.close()


def test_done_is_only_worked_out_when_asked() -> None:
    col = _collection()
    try:
        deck = DeckId(col.decks.id("Empty"))
        save_settings(col, TimeBudgetSettings(decks={deck: DeckBudget(minutes=10)}))
        # the quick status leaves `done` alone; the full one sees no cards left
        assert not col.sched.time_budget_status()[0].done
        assert col.sched.time_budget_status(check_done=True)[0].done
    finally:
        col.close()
