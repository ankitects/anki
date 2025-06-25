# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for the packaged version of Anki."""

from __future__ import annotations

import subprocess
from pathlib import Path

from anki.utils import is_mac


# pylint: disable=unused-import,import-error
def first_run_setup() -> None:
    """Code run the first time after install/upgrade.

    Currently, we just import our main libraries and invoke
    mpv/lame on macOS, which is slow on the first run, and doing
    it this way shows progress being made.
    """

    if not is_mac:
        return

    # Import anki_audio first and spawn commands
    import anki_audio

    audio_pkg_path = Path(anki_audio.__file__).parent

    # Start mpv and lame commands concurrently
    processes = []
    for cmd_name in ["mpv", "lame"]:
        cmd_path = audio_pkg_path / cmd_name
        proc = subprocess.Popen(
            [str(cmd_path), "--version"],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        processes.append(proc)

    # Continue with other imports while commands run
    import concurrent.futures

    import bs4
    import flask
    import flask_cors
    import markdown
    import PyQt6.QtCore
    import PyQt6.QtGui
    import PyQt6.QtNetwork
    import PyQt6.QtQuick
    import PyQt6.QtWebChannel
    import PyQt6.QtWebEngineCore
    import PyQt6.QtWebEngineWidgets
    import PyQt6.QtWidgets
    import PyQt6.sip
    import requests
    import waitress

    import anki.collection

    from . import _macos_helper

    # Wait for both commands to complete
    for proc in processes:
        proc.wait()
