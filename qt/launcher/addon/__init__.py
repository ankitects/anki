# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import contextlib
import os
import subprocess
import sys
from pathlib import Path
from typing import Any

from anki.utils import pointVersion
from aqt import mw
from aqt.qt import QAction
from aqt.utils import askUser, is_mac, is_win, showInfo


def launcher_executable() -> str | None:
    """Return the path to the Anki launcher executable."""
    return os.getenv("ANKI_LAUNCHER")


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


def trigger_launcher_run() -> None:
    """Bump the mtime on pyproject.toml in the local data directory to trigger an update on next run."""
    try:
        root = launcher_root()
        if not root:
            return

        pyproject_path = Path(root) / "pyproject.toml"

        if pyproject_path.exists():
            # Touch the file to update its mtime
            pyproject_path.touch()
    except Exception as e:
        print(e)


def update_and_restart() -> None:
    """Update and restart Anki using the launcher."""
    launcher = launcher_executable()
    assert launcher

    trigger_launcher_run()

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


def on_addon_config():
    showInfo(
        "This add-on is automatically added when installing older Anki versions, so that they work with the launcher. You can remove it if you wish."
    )


def setup():
    mw.addonManager.setConfigAction(__name__, on_addon_config)

    if pointVersion() >= 250600:
        return
    if not launcher_executable():
        return

    # Add action to tools menu
    action = QAction("Upgrade/Downgrade", mw)
    action.triggered.connect(confirm_then_upgrade)
    mw.form.menuTools.addAction(action)

    # Monkey-patch audio tools to use anki-audio
    if is_win or is_mac:
        import aqt
        import aqt.sound

        aqt.sound._packagedCmd = _packagedCmd

    # Inject launcher functions into launcher module
    import aqt.package

    aqt.package.launcher_executable = launcher_executable
    aqt.package.update_and_restart = update_and_restart
    aqt.package.trigger_launcher_run = trigger_launcher_run
    aqt.package.uv_binary = uv_binary
    aqt.package.launcher_root = launcher_root
    aqt.package.venv_binary = venv_binary
    aqt.package.add_python_requirements = add_python_requirements


setup()
