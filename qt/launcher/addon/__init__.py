# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import contextlib
import os
import subprocess
import sys
from pathlib import Path

import aqt.sound
from anki.utils import pointVersion
from aqt import mw
from aqt.qt import QAction
from aqt.utils import askUser, is_mac, is_win, showInfo


def _anki_launcher_path() -> str | None:
    return os.getenv("ANKI_LAUNCHER")


def have_launcher() -> bool:
    return _anki_launcher_path() is not None


def update_and_restart() -> None:
    from aqt import mw

    launcher = _anki_launcher_path()
    assert launcher

    _trigger_launcher_run()

    with contextlib.suppress(ResourceWarning):
        env = os.environ.copy()
        creationflags = 0
        if sys.platform == "win32":
            creationflags = (
                subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
            )
        subprocess.Popen(
            [launcher],
            start_new_session=True,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            env=env,
            creationflags=creationflags,
        )

    mw.app.quit()


def _trigger_launcher_run() -> None:
    """Bump the mtime on pyproject.toml in the local data directory to trigger an update on next run."""
    try:
        # Get the local data directory equivalent to Rust's dirs::data_local_dir()
        if is_win:
            from aqt.winpaths import get_local_appdata

            data_dir = Path(get_local_appdata())
        elif is_mac:
            data_dir = Path.home() / "Library" / "Application Support"
        else:  # Linux
            data_dir = Path(
                os.environ.get("XDG_DATA_HOME", Path.home() / ".local" / "share")
            )

        pyproject_path = data_dir / "AnkiProgramFiles" / "pyproject.toml"

        if pyproject_path.exists():
            # Touch the file to update its mtime
            pyproject_path.touch()
    except Exception as e:
        print(e)


def confirm_then_upgrade():
    if not askUser("Change to a different Anki version?"):
        return
    update_and_restart()


# return modified command array that points to bundled command, and return
# required environment
def _packagedCmd(cmd: list[str]) -> tuple[Any, dict[str, str]]:
    cmd = cmd[:]
    env = os.environ.copy()
    # keep LD_LIBRARY_PATH when in snap environment
    if "LD_LIBRARY_PATH" in env and "SNAP" not in env:
        del env["LD_LIBRARY_PATH"]

    # Try to find binary in anki-audio package for Windows/Mac
    if is_win or is_mac:
        try:
            import anki_audio

            audio_pkg_path = Path(anki_audio.__file__).parent
            if is_win:
                packaged_path = audio_pkg_path / (cmd[0] + ".exe")
            else:  # is_mac
                packaged_path = audio_pkg_path / cmd[0]

            if packaged_path.exists():
                cmd[0] = str(packaged_path)
                return cmd, env
        except ImportError:
            # anki-audio not available, fall back to old behavior
            pass

    packaged_path = Path(sys.prefix) / cmd[0]
    if packaged_path.exists():
        cmd[0] = str(packaged_path)

    return cmd, env


def setup():
    if pointVersion() >= 250600:
        return
    if not have_launcher():
        return

    # Add action to tools menu
    action = QAction("Upgrade/Downgrade", mw)
    action.triggered.connect(confirm_then_upgrade)
    mw.form.menuTools.addAction(action)

    # Monkey-patch audio tools to use anki-audio
    if is_win or is_mac:
        aqt.sound._packagedCmd = _packagedCmd


setup()
