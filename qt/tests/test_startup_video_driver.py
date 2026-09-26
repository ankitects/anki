# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from pathlib import Path
from typing import Any

import pytest

import aqt
from aqt.profiles import ProfileManager, VideoDriver


@pytest.mark.parametrize(
    ("extra_args", "expected_driver"),
    [
        ([], VideoDriver.OpenGL),
        (["--safemode"], VideoDriver.Software),
    ],
)
def test_run_applies_video_driver_before_app_is_created(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    extra_args: list[str],
    expected_driver: VideoDriver,
) -> None:
    # Some of setupGL()'s settings, such as AA_UseSoftwareOpenGL on macOS and
    # QT_OPENGL on Windows, are ignored once the app exists
    monkeypatch.setattr(ProfileManager, "video_driver", lambda self: VideoDriver.OpenGL)
    events: list[tuple[str, VideoDriver | None]] = []

    def fake_setup_gl(pm: Any, driver: VideoDriver | None = None) -> None:
        events.append(("setupGL", driver))

    class FakeApp:
        def __init__(self, argv: list[str]) -> None:
            events.append(("app created", None))

        def secondInstance(self) -> bool:
            # makes _run() return right after creating the app
            return True

    monkeypatch.setattr(aqt, "setupGL", fake_setup_gl)
    monkeypatch.setattr(aqt, "AnkiApp", FakeApp)

    aqt._run(["anki", "-b", str(tmp_path), *extra_args], exec=False)

    assert events == [("setupGL", expected_driver), ("app created", None)]
