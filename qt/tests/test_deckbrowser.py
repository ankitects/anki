# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from unittest.mock import MagicMock, patch

import anki.lang

# aqt.deckbrowser translates its button labels at import time
anki.lang.set_lang("en")

from aqt.deckbrowser import DeckBrowser


def make_deck_browser() -> tuple[DeckBrowser, MagicMock, MagicMock]:
    browser = DeckBrowser.__new__(DeckBrowser)
    web = MagicMock()
    render_page = MagicMock()
    browser.mw = MagicMock()
    browser.web = web
    browser.bottom = MagicMock()
    browser._refresh_needed = True
    setattr(browser, "_DeckBrowser__renderPage", render_page)
    return browser, web, render_page


def finish_loading_deck_tree(query_op: MagicMock) -> None:
    query_op.call_args.kwargs["success"](MagicMock())


@patch("aqt.deckbrowser.QueryOp")
def test_refresh_keeps_scroll_position(query_op: MagicMock) -> None:
    browser, web, render_page = make_deck_browser()

    browser.refresh()
    finish_loading_deck_tree(query_op)

    web.evalWithCallback.assert_called_once_with("window.pageYOffset", render_page)
    render_page.assert_not_called()


@patch("aqt.deckbrowser.av_player")
@patch("aqt.deckbrowser.QueryOp")
def test_show_starts_at_top_instead_of_previous_screen_scroll(
    query_op: MagicMock, _av_player: MagicMock
) -> None:
    browser, web, render_page = make_deck_browser()

    browser.show()
    finish_loading_deck_tree(query_op)

    web.evalWithCallback.assert_not_called()
    render_page.assert_called_once_with(None)
