# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Pure interleaving policy for "Start flashcards" mode.

This module is intentionally free of I/O, Qt, and `anki.collection.Collection`
imports so it can be unit tested in isolation (no Rust backend, no event loop
required). The only side effect visible to callers is the returned tuple; the
`state` argument passed in is never mutated in place.

`window` is a `Sequence[str]` where `window[i]` is the category (depth-2 topic,
or the backend's raw untagged sentinel) of the card at index `i` in the
caller's `QueuedCards.cards` list. The window's ordering is expected to be the
scheduler's own order (i.e. `window[0]` is the next card the scheduler would
serve absent interleaving); "first card in scheduler order" below always means
lowest index into `window`.

Policy (see contracts/api.md section 4 and 03-spec.md Part 1):
- Stay in the current category while it still has a card present in the
  window AND the run hasn't reached its randomly chosen target length.
- Otherwise start a new run: uniform-randomly pick one of the distinct
  categories present in the window, excluding the current category when more
  than one distinct category is present (if only one distinct category is
  present -- or there is no current category -- that category is (re-)picked).
  A fresh run target of 1-3 is drawn.
- An empty window yields `None`; callers should treat this the same as an
  empty scheduler queue.
"""

from __future__ import annotations

import random
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any


@dataclass
class InterleaveState:
    current_category: str | None = None
    served_in_run: int = 0
    run_target: int = 0  # randint(1,3) chosen when a run starts


@dataclass
class CategorizedCard:
    window_index: int  # index into QueuedCards.cards (and into `window`)
    category: str  # depth-2 topic or sentinel


def pick_next(
    window: Sequence[str],
    state: InterleaveState,
    rng: Any = random,
) -> tuple[CategorizedCard, InterleaveState] | None:
    """Pick the next card to serve from `window`, given the current rotation `state`.

    Returns `(chosen, new_state)`, or `None` if `window` is empty. Does not
    mutate `state`; always returns a fresh `InterleaveState`.
    """
    if not window:
        return None

    def first_index_of(category: str) -> int | None:
        for i, cat in enumerate(window):
            if cat == category:
                return i
        return None

    # Stay in the current run if possible.
    if state.current_category is not None and state.served_in_run < state.run_target:
        idx = first_index_of(state.current_category)
        if idx is not None:
            chosen = CategorizedCard(window_index=idx, category=state.current_category)
            new_state = InterleaveState(
                current_category=state.current_category,
                served_in_run=state.served_in_run + 1,
                run_target=state.run_target,
            )
            return chosen, new_state

    # Start a new run: pick a random category, excluding the current one if
    # more than one distinct category is present.
    distinct_categories: list[str] = []
    for cat in window:
        if cat not in distinct_categories:
            distinct_categories.append(cat)

    candidates = [c for c in distinct_categories if c != state.current_category]
    if not candidates:
        # Either there is no current category, or it's the only one present.
        candidates = distinct_categories

    picked_category = rng.choice(candidates)
    idx = first_index_of(picked_category)
    assert idx is not None  # picked_category came from the window itself

    chosen = CategorizedCard(window_index=idx, category=picked_category)
    new_state = InterleaveState(
        current_category=picked_category,
        served_in_run=1,
        run_target=rng.randint(1, 3),
    )
    return chosen, new_state
