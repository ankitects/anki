# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import cast
from unittest.mock import MagicMock, patch

from aqt.toolbar import TopWebView
from aqt.webview import AnkiWebView


def make_top_web_view(state: str) -> TopWebView:
    web = cast(TopWebView, MagicMock(spec=TopWebView))
    web.mw = MagicMock()
    web.mw.state = state
    return web


@patch.object(AnkiWebView, "on_theme_did_change")
def test_theme_change_refreshes_review_background(
    super_on_theme_did_change: MagicMock,
) -> None:
    web = make_top_web_view("review")

    TopWebView.on_theme_did_change(web)

    super_on_theme_did_change.assert_called_once_with()
    web.eval.assert_called_once()
    script = web.eval.call_args.args[0]
    assert 'document.body.style.removeProperty("background")' in script
    delay, callback = web.mw.progress.single_shot.call_args.args
    assert delay == 0
    callback()
    web.update_background_image.assert_called_once_with()


@patch.object(AnkiWebView, "on_theme_did_change")
def test_theme_change_does_not_copy_background_outside_review(
    super_on_theme_did_change: MagicMock,
) -> None:
    web = make_top_web_view("deckBrowser")

    TopWebView.on_theme_did_change(web)

    super_on_theme_did_change.assert_called_once_with()
    web.eval.assert_not_called()
    web.mw.progress.single_shot.assert_not_called()
