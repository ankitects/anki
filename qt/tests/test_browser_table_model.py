# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Tests for browser table row lookup.

`DataModel.get_item_rows` maps a saved selection back to table rows after a
search. Membership has to stay a set lookup: testing each row with `in` on the
selection list needs about rows x selected comparisons, which freezes the UI
for a large selection.
"""

from __future__ import annotations

from collections.abc import Sequence

import pytest

from aqt.browser.table.model import DataModel

# CardId has to come after DataModel. Importing anki.cards first re-enters
# cards.py through hooks_gen before Card is defined.
from anki.cards import CardId  # isort: skip


class _CountingId(int):
    """Card id that counts equality checks, to measure lookup cost without timing."""

    eq_calls = 0

    def __eq__(self, other: object) -> bool:
        type(self).eq_calls += 1
        return int(self) == other

    __hash__ = int.__hash__


def _ids(values: Sequence[int]) -> list[CardId]:
    return [CardId(value) for value in values]


def _counting_ids(values: Sequence[int]) -> list[CardId]:
    return [CardId(_CountingId(value)) for value in values]


def _model(rows: list[CardId]) -> DataModel:
    # The lookup only reads `_items`, so skip Qt and collection setup.
    model = DataModel.__new__(DataModel)
    model._items = list(rows)
    return model


@pytest.mark.parametrize(
    ("rows", "wanted", "expected"),
    [
        pytest.param(
            [10, 20, 30, 40],
            [30, 10, 99],
            [0, 2],
            id="partial-keeps-table-order",
        ),
        pytest.param([10, 20, 30, 40], [], [], id="empty-selection"),
        pytest.param([], [10], [], id="empty-table"),
        pytest.param([10, 20, 30], [10, 20, 30], [0, 1, 2], id="full-selection"),
        pytest.param([10, 20], [10, 10], [0], id="duplicate-in-request"),
        pytest.param([10, 20, 10], [10], [0, 2], id="duplicate-in-table"),
    ],
)
def test_get_item_rows_returns_matching_rows_in_table_order(
    rows: list[int], wanted: list[int], expected: list[int]
) -> None:
    assert _model(_ids(rows)).get_item_rows(_ids(wanted)) == expected


def test_get_item_rows_uses_linear_comparisons_for_large_selection() -> None:
    size = 1_000
    model = _model(_counting_ids(range(size)))
    # Distinct objects, so membership can't short-circuit on identity.
    wanted = _counting_ids(range(size))
    _CountingId.eq_calls = 0

    assert model.get_item_rows(wanted) == list(range(size))
    # These ids barely collide, so a set calls __eq__ almost never. List
    # membership is quadratic (500,500 comparisons here) and fails this bound.
    assert _CountingId.eq_calls <= 2 * size
