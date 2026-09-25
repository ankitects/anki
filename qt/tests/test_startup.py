# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
from pathlib import Path
from unittest.mock import patch

import pytest

import aqt
from aqt.profiles import ProfileManager


@patch.dict(os.environ)
def test_ui_scale_is_applied_before_app_is_created(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    # Qt reads QT_SCALE_FACTOR when the app is constructed, so setting it
    # afterwards has no effect (#5676)
    monkeypatch.setattr(ProfileManager, "uiScale", lambda self: 2.0)
    seen: list[str | None] = []

    class FakeApp:
        def __init__(self, argv: list[str]) -> None:
            seen.append(os.environ.get("QT_SCALE_FACTOR"))

        def secondInstance(self) -> bool:
            # makes _run() return right after creating the app
            return True

    monkeypatch.setattr(aqt, "AnkiApp", FakeApp)

    aqt._run(["anki", "-b", str(tmp_path)], exec=False)

    assert seen == ["2.0"]
