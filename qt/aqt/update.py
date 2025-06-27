# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import contextlib
import os
import subprocess
from pathlib import Path

import aqt
from anki.buildinfo import buildhash
from anki.collection import CheckForUpdateResponse, Collection
from anki.utils import dev_mode, int_time, int_version, is_mac, is_win, plat_desc
from aqt.operations import QueryOp
from aqt.qt import *
from aqt.utils import openLink, show_warning, showText, tr


def check_for_update() -> None:
    from aqt import mw

    def do_check(_col: Collection) -> CheckForUpdateResponse:
        return mw.backend.check_for_update(
            version=int_version(),
            buildhash=buildhash,
            os=plat_desc(),
            install_id=mw.pm.meta["id"],
            last_message_id=max(0, mw.pm.meta["lastMsg"]),
        )

    def on_done(resp: CheckForUpdateResponse) -> None:
        # is clock off?
        if not dev_mode:
            diff = abs(resp.current_time - int_time())
            if diff > 300:
                diff_text = tr.qt_misc_second(count=diff)
                warn = (
                    tr.qt_misc_in_order_to_ensure_your_collection(val="%s") % diff_text
                )
                show_warning(
                    warn,
                    parent=mw,
                    textFormat=Qt.TextFormat.RichText,
                    callback=mw.app.closeAllWindows,
                )
                return
        # should we show a message?
        if msg := resp.message:
            showText(msg, parent=mw, type="html")
            mw.pm.meta["lastMsg"] = resp.last_message_id
        # has Anki been updated?
        if ver := resp.new_version:
            if mw.pm.meta.get("suppressUpdate", None) != ver:
                prompt_to_update(mw, ver)

    def on_fail(exc: Exception) -> None:
        print(f"update check failed: {exc}")

    QueryOp(parent=mw, op=do_check, success=on_done).failure(
        on_fail
    ).without_collection().run_in_background()


def prompt_to_update(mw: aqt.AnkiQt, ver: str) -> None:
    msg = (
        tr.qt_misc_anki_updatedanki_has_been_released(val=ver)
        + tr.qt_misc_would_you_like_to_download_it()
    )

    msgbox = QMessageBox(mw)
    msgbox.setStandardButtons(
        QMessageBox.StandardButton.Yes | QMessageBox.StandardButton.No
    )
    msgbox.setIcon(QMessageBox.Icon.Information)
    msgbox.setText(msg)

    button = QPushButton(tr.qt_misc_ignore_this_update())
    msgbox.addButton(button, QMessageBox.ButtonRole.RejectRole)
    msgbox.setDefaultButton(QMessageBox.StandardButton.Yes)
    ret = msgbox.exec()

    if msgbox.clickedButton() == button:
        # ignore this update
        mw.pm.meta["suppressUpdate"] = ver
    elif ret == QMessageBox.StandardButton.Yes:
        if have_launcher():
            update_and_restart()
        else:
            openLink(aqt.appWebsiteDownloadSection)


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
            from .winpaths import get_local_appdata

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
