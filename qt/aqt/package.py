# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for the packaged version of Anki."""

from __future__ import annotations

import contextlib
import os
import subprocess
import sys
from pathlib import Path

from anki.utils import is_mac, is_win


# ruff: noqa: F401
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


def uv_binary() -> str | None:
    """Return the path to the uv binary."""
    return os.environ.get("ANKI_LAUNCHER_UV")


def launcher_root() -> str | None:
    """Return the path to the launcher root directory (AnkiProgramFiles)."""
    return os.environ.get("UV_PROJECT")


def venv_binary(cmd: str) -> str | None:
    """Return the path to a binary in the launcher's venv."""
    root = launcher_root()
    if not root:
        return None

    root_path = Path(root)
    if is_win:
        binary_path = root_path / ".venv" / "Scripts" / cmd
    else:
        binary_path = root_path / ".venv" / "bin" / cmd

    return str(binary_path)


def add_python_requirements(reqs: list[str]) -> tuple[bool, str]:
    """Add Python requirements to the launcher venv using uv add.

    Returns (success, output)"""

    binary = uv_binary()
    if not binary:
        return (False, "Not in packaged build.")

    uv_cmd = [binary, "add"] + reqs
    result = subprocess.run(uv_cmd, capture_output=True, text=True, check=False)

    if result.returncode == 0:
        root = launcher_root()
        if root:
            sync_marker = Path(root) / ".sync_complete"
            sync_marker.touch()

        return (True, result.stdout)
    else:
        return (False, result.stderr)


def launcher_executable() -> str | None:
    """Return the path to the Anki launcher executable."""
    return os.getenv("ANKI_LAUNCHER")


def trigger_launcher_run() -> None:
    """Create a trigger file to request launcher UI on next run."""
    try:
        root = launcher_root()
        if not root:
            return

        trigger_path = Path(root) / ".want-launcher"
        trigger_path.touch()
    except Exception as e:
        print(e)


def update_and_restart() -> None:
    """Update and restart Anki using the launcher."""
    from aqt import mw

    launcher = launcher_executable()
    assert launcher

    trigger_launcher_run()

    with contextlib.suppress(ResourceWarning):
        env = os.environ.copy()
        # fixes a bug where launcher fails to appear if opening it
        # straight after updating
        if "GNOME_TERMINAL_SCREEN" in env:
            del env["GNOME_TERMINAL_SCREEN"]
        creationflags = 0
        if sys.platform == "win32":
            creationflags = (
                subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
            )
        # On Windows 10, changing the handles breaks ANSI display
        io = None if sys.platform == "win32" else subprocess.DEVNULL

        subprocess.Popen(
            [launcher],
            start_new_session=True,
            stdin=io,
            stdout=io,
            stderr=io,
            env=env,
            creationflags=creationflags,
        )

    mw.app.quit()
