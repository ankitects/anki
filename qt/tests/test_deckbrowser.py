# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from importlib import import_module, reload
from types import SimpleNamespace
from unittest.mock import patch


class DummyTr:
    def decks_get_shared(self) -> str:
        return "Shared"

    def decks_create_deck(self) -> str:
        return "Create Deck"

    def decks_import_file(self) -> str:
        return "Import File"


def load_deckbrowser_module():
    with patch("aqt.utils.tr", DummyTr()):
        module = import_module("aqt.deckbrowser")
        return reload(module)


def test_parent_deck_hides_all_due_counts_when_enabled():
    deckbrowser = load_deckbrowser_module()
    node = SimpleNamespace(
        new_count=10, learn_count=5, review_count=20, children=[object()]
    )

    assert deckbrowser._deck_due_counts_for_display(node, True) == (None, None, None)


def test_leaf_deck_counts_are_unchanged_when_enabled():
    deckbrowser = load_deckbrowser_module()
    node = SimpleNamespace(new_count=10, learn_count=5, review_count=20, children=[])

    assert deckbrowser._deck_due_counts_for_display(node, True) == (10, 5, 20)


def test_parent_deck_counts_are_unchanged_when_setting_disabled():
    deckbrowser = load_deckbrowser_module()
    node = SimpleNamespace(
        new_count=10, learn_count=5, review_count=20, children=[object()]
    )

    assert deckbrowser._deck_due_counts_for_display(node, False) == (10, 5, 20)
