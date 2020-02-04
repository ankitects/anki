# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from concurrent.futures import Future
from copy import copy
from dataclasses import dataclass
from typing import List, Optional, Union, Callable

import anki
import aqt
from anki import hooks
from anki.lang import _
from anki.media import media_paths_from_col_path
from anki.rsbackend import (
    Interrupted,
    MediaSyncDownloadedChanges,
    MediaSyncDownloadedFiles,
    MediaSyncProgress,
    MediaSyncRemovedFiles,
    MediaSyncUploaded,
    Progress,
    ProgressKind,
)
from anki.types import assert_impossible
from anki.utils import intTime
from aqt import gui_hooks
from aqt.qt import QDialog, QDialogButtonBox, QPushButton, QWidget
from aqt.taskman import TaskManager


@dataclass
class MediaSyncState:
    downloaded_changes: int = 0
    downloaded_files: int = 0
    uploaded_files: int = 0
    uploaded_removals: int = 0
    removed_files: int = 0


# fixme: make sure we don't run twice
# fixme: handle auth errors
# fixme: handle network errors
# fixme: show progress in UI
# fixme: abort when closing collection/app
# fixme: handle no hkey
# fixme: shards
# fixme: dialog should be a singleton
# fixme: abort button should not be default


class SyncBegun:
    pass


class SyncEnded:
    pass


class SyncAborted:
    pass


LogEntry = Union[MediaSyncState, SyncBegun, SyncEnded, SyncAborted]


@dataclass
class LogEntryWithTime:
    time: int
    entry: LogEntry


class MediaSyncer:
    def __init__(self, taskman: TaskManager, on_start_stop: Callable[[], None]):
        self._taskman = taskman
        self._sync_state: Optional[MediaSyncState] = None
        self._log: List[LogEntryWithTime] = []
        self._want_stop = False
        self._on_start_stop = on_start_stop
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

    def start(
        self, col: anki.storage._Collection, hkey: str, shard: Optional[int]
    ) -> None:
        "Start media syncing in the background, if it's not already running."
        if self._sync_state is not None:
            return

        self._log_and_notify(SyncBegun())
        self._sync_state = MediaSyncState()
        self._want_stop = False
        self._on_start_stop()

        if shard is not None:
            shard_str = str(shard)
        else:
            shard_str = ""
        endpoint = f"https://sync{shard_str}ankiweb.net"

        (media_folder, media_db) = media_paths_from_col_path(col.path)

        def run() -> None:
            col.backend.sync_media(hkey, media_folder, media_db, endpoint)

        self._taskman.run_in_background(run, self._on_finished)

    def _log_and_notify(self, entry: LogEntry) -> None:
        entry_with_time = LogEntryWithTime(time=intTime(), entry=entry)
        self._log.append(entry_with_time)
        self._taskman.run_on_main(
            lambda: gui_hooks.media_sync_did_progress(entry_with_time)
        )

    def _on_finished(self, future: Future) -> None:
        self._sync_state = None
        self._on_start_stop()

        exc = future.exception()
        if exc is not None:
            if isinstance(exc, Interrupted):
                self._log_and_notify(SyncAborted())
            else:
                raise exc
        else:
            self._log_and_notify(SyncEnded())

    def entries(self) -> List[LogEntryWithTime]:
        return self._log

    def abort(self) -> None:
        self._want_stop = True

    def is_syncing(self) -> bool:
        return self._sync_state is not None


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
        self.form.plainTextEdit.appendPlainText(
            self._time_and_text(intTime(), _("Aborting..."))
        )
        self._syncer.abort()
        self.abort_button.setHidden(True)

    def _time_and_text(self, stamp: int, text: str) -> str:
        asctime = time.asctime(time.localtime(stamp))
        return f"{asctime}: {text}"

    def _entry_to_text(self, entry: LogEntryWithTime):
        if isinstance(entry.entry, SyncBegun):
            txt = _("Media sync starting...")
        elif isinstance(entry.entry, SyncEnded):
            txt = _("Media sync complete.")
        elif isinstance(entry.entry, SyncAborted):
            txt = _("Aborted.")
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
