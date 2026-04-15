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


def render_node_html(deckbrowser, node):
    browser = deckbrowser.DeckBrowser.__new__(deckbrowser.DeckBrowser)
    return deckbrowser.DeckBrowser._render_deck_node(
        browser,
        node,
        deckbrowser.RenderDeckNodeContext(current_deck_id=0),
    )


def test_expanded_parent_deck_counts_are_deemphasized():
    deckbrowser = load_deckbrowser_module()
    child = SimpleNamespace(
        deck_id=2,
        level=2,
        filtered=False,
        name="Child",
        new_count=1,
        learn_count=2,
        review_count=3,
        children=[],
        collapsed=False,
    )
    node = SimpleNamespace(
        deck_id=1,
        level=1,
        filtered=False,
        name="Parent",
        new_count=10,
        learn_count=5,
        review_count=20,
        children=[child],
        collapsed=False,
    )

    assert deckbrowser._deck_due_counts_are_deemphasized(node) is True
    assert 'class="new-count parent-count"' in render_node_html(deckbrowser, node)
    assert 'class="learn-count parent-count"' in render_node_html(deckbrowser, node)
    assert 'class="review-count parent-count"' in render_node_html(deckbrowser, node)


def test_collapsed_parent_deck_counts_are_not_deemphasized():
    deckbrowser = load_deckbrowser_module()
    node = SimpleNamespace(
        deck_id=1,
        level=1,
        filtered=False,
        name="Parent",
        new_count=10,
        learn_count=5,
        review_count=20,
        children=[object()],
        collapsed=True,
    )

    assert deckbrowser._deck_due_counts_are_deemphasized(node) is False
    assert "parent-count" not in render_node_html(deckbrowser, node)


def test_leaf_deck_counts_are_not_deemphasized():
    deckbrowser = load_deckbrowser_module()
    node = SimpleNamespace(
        deck_id=1,
        level=1,
        filtered=False,
        name="Leaf",
        new_count=10,
        learn_count=5,
        review_count=20,
        children=[],
        collapsed=False,
    )

    assert deckbrowser._deck_due_counts_are_deemphasized(node) is False
    assert "parent-count" not in render_node_html(deckbrowser, node)
