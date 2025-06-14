# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for the packaged version of Anki."""

from __future__ import annotations
from pathlib import Path
import subprocess
from anki.utils import is_mac

def first_run_setup() -> None:
    """Code run the first time after install/upgrade.
    
    Currently, we just import our main libraries and invoke
    mpv/lame on macOS, which is slow on the first run, and doing
    it this way shows progress being made.
    """

    if not is_mac:
        return
    
    def _dot():
        print(".", flush=True, end="")

    _dot()
    import anki.collection
    _dot()
    import PyQt6.sip
    _dot()
    import PyQt6.QtCore
    _dot()
    import PyQt6.QtGui
    _dot()
    import PyQt6.QtNetwork
    _dot()
    import PyQt6.QtQuick
    _dot()
    import PyQt6.QtWebChannel
    _dot()
    import PyQt6.QtWebEngineCore
    _dot()
    import PyQt6.QtWebEngineWidgets
    _dot()
    import PyQt6.QtWidgets

    import anki_audio
    audio_pkg_path = Path(anki_audio.__file__).parent

    # Invoke mpv and lame
    cmd = [Path(""), "--version"]
    for cmd_name in ["mpv", "lame"]:
        _dot()
        cmd[0] = audio_pkg_path / cmd_name
        subprocess.run(cmd, check=True, capture_output=True)
