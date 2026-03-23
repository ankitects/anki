# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from types import SimpleNamespace

from aqt.deckbrowser import _deck_due_counts_for_display


def test_parent_deck_hides_all_due_counts_when_enabled():
    node = SimpleNamespace(
        new_count=10, learn_count=5, review_count=20, children=[object()]
    )

    assert _deck_due_counts_for_display(node, True) == (None, None, None)


def test_leaf_deck_counts_are_unchanged_when_enabled():
    node = SimpleNamespace(new_count=10, learn_count=5, review_count=20, children=[])

    assert _deck_due_counts_for_display(node, True) == (10, 5, 20)


def test_parent_deck_counts_are_unchanged_when_setting_disabled():
    node = SimpleNamespace(
        new_count=10, learn_count=5, review_count=20, children=[object()]
    )

    assert _deck_due_counts_for_display(node, False) == (10, 5, 20)
