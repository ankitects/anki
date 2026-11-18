# Copyright: Anki Software, LLC and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import cast
from unittest.mock import MagicMock, patch

import pytest

import aqt
from aqt.qt import QUrl, QWebEngineView
from aqt.webview import AnkiWebView


@pytest.mark.parametrize(
    ("ok", "uses_dynamic_styling", "expect_eval", "expect_dynamic_styling"),
    [
        (False, True, False, False),
        (True, True, True, True),
        (True, False, True, False),
    ],
)
def test_on_load_finished(
    ok: bool,
    uses_dynamic_styling: bool,
    expect_eval: bool,
    expect_dynamic_styling: bool,
) -> None:
    web = cast(AnkiWebView, MagicMock(spec=AnkiWebView))
    web._uses_dynamic_styling = uses_dynamic_styling

    AnkiWebView._on_load_finished(web, ok)

    assert web.eval.called is expect_eval
    assert web.add_dynamic_styling_and_props_then_show.called is expect_dynamic_styling


@pytest.mark.parametrize(
    ("method_name", "arg"),
    [
        ("load_ts_page", "deckconfig"),
        ("load_sveltekit_page", "congrats"),
    ],
)
@patch.object(QWebEngineView, "load")
def test_load_url_resets_dynamic_styling(
    super_load: MagicMock,
    monkeypatch: pytest.MonkeyPatch,
    method_name: str,
    arg: str,
) -> None:
    mw = MagicMock()
    mw.serverURL.return_value = "http://127.0.0.1:12345/"
    monkeypatch.setattr(aqt, "mw", mw, raising=False)

    web = cast(AnkiWebView, MagicMock(spec=AnkiWebView))
    web._uses_dynamic_styling = False
    web.load_url.side_effect = lambda url: AnkiWebView.load_url(web, url)

    getattr(AnkiWebView, method_name)(web, arg)
    assert web._uses_dynamic_styling is True

    static_url = QUrl("http://127.0.0.1:12345/_anki/legacyPageData?id=1")
    AnkiWebView.load_url(web, static_url)
    assert web._uses_dynamic_styling is False
    assert super_load.call_args.args == (static_url,)
