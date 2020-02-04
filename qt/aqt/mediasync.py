# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from concurrent.futures import Future
from copy import copy
from dataclasses import dataclass
from typing import List, Optional, Union

import aqt
from anki import hooks
from anki.lang import _
from anki.media import media_paths_from_col_path
from anki.rsbackend import (
    DBError,
    Interrupted,
    MediaSyncDownloadedChanges,
    MediaSyncDownloadedFiles,
    MediaSyncProgress,
    MediaSyncRemovedFiles,
    MediaSyncUploaded,
    NetworkError,
    NetworkErrorKind,
    Progress,
    ProgressKind,
    SyncError,
    SyncErrorKind,
)
from anki.types import assert_impossible
from anki.utils import intTime
from aqt import gui_hooks
from aqt.qt import QDialog, QDialogButtonBox, QPushButton
from aqt.utils import showWarning


@dataclass
class MediaSyncState:
    downloaded_changes: int = 0
    downloaded_files: int = 0
    uploaded_files: int = 0
    uploaded_removals: int = 0
    removed_files: int = 0


# fixme: sync.rs fixmes
# fixme: maximum size when uploading
# fixme: abort when closing collection/app
# fixme: concurrent modifications during upload step
# fixme: mediaSanity
# fixme: autosync
#         elif evt == "mediaSanity":
#         showWarning(
#             _(
#                 """\
# A problem occurred while syncing media. Please use Tools>Check Media, then \
# sync again to correct the issue."""
#             )
#         )


LogEntry = Union[MediaSyncState, str]


@dataclass
class LogEntryWithTime:
    time: int
    entry: LogEntry


class MediaSyncer:
    def __init__(self, mw: aqt.main.AnkiQt):
        self.mw = mw
        self._sync_state: Optional[MediaSyncState] = None
        self._log: List[LogEntryWithTime] = []
        self._want_stop = False
        hooks.rust_progress_callback.append(self._on_rust_progress)

    def _on_rust_progress(self, proceed: bool, progress: Progress) -> bool:
        if progress.kind != ProgressKind.MediaSyncProgress:
            return proceed

        self._update_state(progress.val)
        self._log_and_notify(copy(self._sync_state))

        if self._want_stop:
            return False
        else:
            return proceed

    def _update_state(self, progress: MediaSyncProgress) -> None:
        if isinstance(progress, MediaSyncDownloadedChanges):
            self._sync_state.downloaded_changes += progress.changes
        elif isinstance(progress, MediaSyncDownloadedFiles):
            self._sync_state.downloaded_files += progress.files
        elif isinstance(progress, MediaSyncUploaded):
            self._sync_state.uploaded_files += progress.files
            self._sync_state.uploaded_removals += progress.deletions
        elif isinstance(progress, MediaSyncRemovedFiles):
            self._sync_state.removed_files += progress.files

    def start(self) -> None:
        "Start media syncing in the background, if it's not already running."
        if self._sync_state is not None:
            return

        hkey = self.mw.pm.sync_key()
        if hkey is None:
            return

        if not self.mw.pm.media_syncing_enabled():
            self._log_and_notify(_("Media syncing disabled."))
            return

        self._log_and_notify(_("Media sync starting..."))
        self._sync_state = MediaSyncState()
        self._want_stop = False
        self._on_start_stop()

        (media_folder, media_db) = media_paths_from_col_path(self.mw.col.path)

        def run() -> None:
            self.mw.col.backend.sync_media(
                hkey, media_folder, media_db, self._endpoint()
            )

        self.mw.taskman.run_in_background(run, self._on_finished)

    def _endpoint(self) -> str:
        shard = self.mw.pm.sync_shard()
        if shard is not None:
            shard_str = str(shard)
        else:
            shard_str = ""
        return f"https://sync{shard_str}.ankiweb.net/msync/"

    def _log_and_notify(self, entry: LogEntry) -> None:
        entry_with_time = LogEntryWithTime(time=intTime(), entry=entry)
        self._log.append(entry_with_time)
        self.mw.taskman.run_on_main(
            lambda: gui_hooks.media_sync_did_progress(entry_with_time)
        )

    def _on_finished(self, future: Future) -> None:
        self._sync_state = None
        self._on_start_stop()

        exc = future.exception()
        if exc is not None:
            self._handle_sync_error(exc)
        else:
            self._log_and_notify(_("Media sync complete."))

    def _handle_sync_error(self, exc: BaseException):
        if isinstance(exc, Interrupted):
            self._log_and_notify(_("Media sync aborted."))
            return

        self._log_and_notify(_("Media sync failed."))
        if isinstance(exc, SyncError):
            kind = exc.kind()
            if kind == SyncErrorKind.AUTH_FAILED:
                self.mw.pm.set_sync_key(None)
                showWarning(
                    _("AnkiWeb ID or password was incorrect; please try again.")
                )
            elif kind == SyncErrorKind.SERVER_ERROR:
                showWarning(
                    _(
                        "AnkiWeb encountered a problem. Please try again in a few minutes."
                    )
                )
            else:
                showWarning(_("Unexpected error: {}").format(str(exc)))
        elif isinstance(exc, NetworkError):
            nkind = exc.kind()
            if nkind in (NetworkErrorKind.OFFLINE, NetworkErrorKind.TIMEOUT):
                showWarning(
                    _("Syncing failed; please check your internet connection.")
                    + "\n\n"
                    + _("Detailed error: {}").format(str(exc))
                )
            else:
                showWarning(_("Unexpected error: {}").format(str(exc)))
        elif isinstance(exc, DBError):
            showWarning(_("Problem accessing the media database: {}").format(str(exc)))
        else:
            raise exc

    def entries(self) -> List[LogEntryWithTime]:
        return self._log

    def abort(self) -> None:
        if not self.is_syncing():
            return
        self._log_and_notify(_("Media sync aborting..."))
        self._want_stop = True

    def is_syncing(self) -> bool:
        return self._sync_state is not None

    def _on_start_stop(self):
        self.mw.toolbar.set_sync_active(self.is_syncing())

    def show_sync_log(self):
        aqt.dialogs.open("sync_log", self.mw, self)


class MediaSyncDialog(QDialog):
    silentlyClose = True

    def __init__(self, mw: aqt.main.AnkiQt, syncer: MediaSyncer) -> None:
        super().__init__(mw)
        self.mw = mw
        self._syncer = syncer
        self.form = aqt.forms.synclog.Ui_Dialog()
        self.form.setupUi(self)
        self.abort_button = QPushButton(_("Abort"))
        self.abort_button.clicked.connect(self._on_abort)  # type: ignore
        self.abort_button.setAutoDefault(False)
        self.form.buttonBox.addButton(self.abort_button, QDialogButtonBox.ActionRole)

        gui_hooks.media_sync_did_progress.append(self._on_log_entry)

        self.form.plainTextEdit.setPlainText(
            "\n".join(self._entry_to_text(x) for x in syncer.entries())
        )
        self.show()

    def reject(self):
        aqt.dialogs.markClosed("sync_log")
        QDialog.reject(self)

    def accept(self):
        aqt.dialogs.markClosed("sync_log")
        QDialog.accept(self)

    def reopen(self, *args):
        self.show()

    def _on_abort(self, *args) -> None:
        self._syncer.abort()
        self.abort_button.setHidden(True)

    def _time_and_text(self, stamp: int, text: str) -> str:
        asctime = time.asctime(time.localtime(stamp))
        return f"{asctime}: {text}"

    def _entry_to_text(self, entry: LogEntryWithTime):
        if isinstance(entry.entry, str):
            txt = entry.entry
        elif isinstance(entry.entry, MediaSyncState):
            txt = self._logentry_to_text(entry.entry)
        else:
            assert_impossible(entry.entry)
        return self._time_and_text(entry.time, txt)

    def _logentry_to_text(self, e: MediaSyncState) -> str:
        return _(
            "Added: %(a_up)s ↑, %(a_dwn)s ↓, Removed: %(r_up)s ↑, %(r_dwn)s ↓, Checked: %(chk)s"
        ) % dict(
            a_up=e.uploaded_files,
            a_dwn=e.downloaded_files,
            r_up=e.uploaded_removals,
            r_dwn=e.removed_files,
            chk=e.downloaded_changes,
        )

    def _on_log_entry(self, entry: LogEntryWithTime):
        self.form.plainTextEdit.appendPlainText(self._entry_to_text(entry))
        if not self._syncer.is_syncing():
            self.abort_button.setHidden(True)
