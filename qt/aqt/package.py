# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Helpers for the packaged version of Anki."""

from __future__ import annotations

import contextlib
import subprocess
import sys

from anki.collection import GithubRelease, Progress
from aqt.progress import ProgressUpdate
from aqt.utils import openLink, tr


def download_github_update_and_install(release: GithubRelease) -> None:
    from aqt import mw

    if release.filename.endswith(".msi"):
        args = ["msiexec", "/i"]
    elif release.filename.endswith(".dmg"):
        args = ["open"]
    else:
        openLink(release.url)
        return

    def on_success(output_path: str) -> None:
        with contextlib.suppress(ResourceWarning):
            creationflags = 0
            if sys.platform == "win32":
                creationflags = (
                    subprocess.CREATE_NEW_PROCESS_GROUP | subprocess.DETACHED_PROCESS
                )
            args.append(output_path)
            subprocess.Popen(
                args,
                start_new_session=True,
                creationflags=creationflags,
            )

        mw.app.quit()

    def update_progress(progress: Progress, update: ProgressUpdate) -> None:
        if not progress.HasField("download_update"):
            return
        download_update = progress.download_update
        if download_update.total_bytes:
            update.label = tr.qt_misc_downloading_update(
                count=download_update.downloaded_bytes // (1024 * 1024),
                total=download_update.total_bytes // (1024 * 1024),
            )
            update.value = download_update.downloaded_bytes
            update.max = download_update.total_bytes
        if update.user_wants_abort:
            update.abort = True

    from aqt.operations import QueryOp

    QueryOp(
        parent=mw,
        op=lambda col: col._backend.download_release(release),
        success=on_success,
    ).with_backend_progress(update_progress).run_in_background()
