# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from concurrent.futures import Future
from dataclasses import dataclass
from typing import Any, Callable, List, Optional, Union

import aqt
from anki.collection import Progress
from anki.errors import Interrupted, NetworkError
from anki.lang import TR
from anki.types import assert_exhaustive
from anki.utils import intTime
from aqt import gui_hooks
from aqt.qt import QDialog, QDialogButtonBox, QPushButton, QTextCursor, QTimer, qconnect
from aqt.utils import disable_help_button, showWarning, tr

LogEntry = Union[Progress.MediaSync, str]


@dataclass
class LogEntryWithTime:
    time: int
    entry: LogEntry


class MediaSyncer:
    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        self.mw = mw
        self._syncing: bool = False
        self._log: List[LogEntryWithTime] = []
        self._progress_timer: Optional[QTimer] = None
        gui_hooks.media_sync_did_start_or_stop.append(self._on_start_stop)

    def _on_progress(self) -> None:
        progress = self.mw.col.latest_progress()
        if not progress.HasField("media_sync"):
            return
        sync_progress = progress.media_sync
        self._log_and_notify(sync_progress)

    def start(self) -> None:
        "Start media syncing in the background, if it's not already running."
        if self._syncing:
            return

        if not self.mw.pm.media_syncing_enabled():
            self._log_and_notify(tr(TR.SYNC_MEDIA_DISABLED))
            return

        auth = self.mw.pm.sync_auth()
        if auth is None:
            return

        self._log_and_notify(tr(TR.SYNC_MEDIA_STARTING))
        self._syncing = True
        self._progress_timer = self.mw.progress.timer(
            1000, self._on_progress, True, True
        )
        gui_hooks.media_sync_did_start_or_stop(True)

        def run() -> None:
            self.mw.col.sync_media(auth)

        self.mw.taskman.run_in_background(run, self._on_finished)

    def _log_and_notify(self, entry: LogEntry) -> None:
        entry_with_time = LogEntryWithTime(time=intTime(), entry=entry)
        self._log.append(entry_with_time)
        self.mw.taskman.run_on_main(
            lambda: gui_hooks.media_sync_did_progress(entry_with_time)
        )

    def _on_finished(self, future: Future) -> None:
        self._syncing = False
        if self._progress_timer:
            self._progress_timer.stop()
            self._progress_timer = None
        gui_hooks.media_sync_did_start_or_stop(False)

        exc = future.exception()
        if exc is not None:
            self._handle_sync_error(exc)
        else:
            self._log_and_notify(tr(TR.SYNC_MEDIA_COMPLETE))

    def _handle_sync_error(self, exc: BaseException) -> None:
        if isinstance(exc, Interrupted):
            self._log_and_notify(tr(TR.SYNC_MEDIA_ABORTED))
            return
        elif isinstance(exc, NetworkError):
            # avoid popups for network errors
            self._log_and_notify(str(exc))
            return

        self._log_and_notify(tr(TR.SYNC_MEDIA_FAILED))
        showWarning(str(exc))

    def entries(self) -> List[LogEntryWithTime]:
        return self._log

    def abort(self) -> None:
        if not self.is_syncing():
            return
        self._log_and_notify(tr(TR.SYNC_MEDIA_ABORTING))
        self.mw.col.set_wants_abort()
        self.mw.col.abort_media_sync()

    def is_syncing(self) -> bool:
        return self._syncing

    def _on_start_stop(self, running: bool) -> None:
        self.mw.toolbar.set_sync_active(running)

    def show_sync_log(self) -> None:
        aqt.dialogs.open("sync_log", self.mw, self)

    def show_diag_until_finished(self, on_finished: Callable[[], None]) -> None:
        # nothing to do if not syncing
        if not self.is_syncing():
            return on_finished()

        diag: MediaSyncDialog = aqt.dialogs.open("sync_log", self.mw, self, True)
        diag.show()

        timer: Optional[QTimer] = None

        def check_finished() -> None:
            if not self.is_syncing():
                timer.stop()
                on_finished()

        timer = self.mw.progress.timer(150, check_finished, True, False)

    def seconds_since_last_sync(self) -> int:
        if self.is_syncing():
            return 0

        if self._log:
            last = self._log[-1].time
        else:
            last = 0
        return intTime() - last


class MediaSyncDialog(QDialog):
    silentlyClose = True

    def __init__(
        self, mw: aqt.main.AnkiQt, syncer: MediaSyncer, close_when_done: bool = False
    ) -> None:
        super().__init__(mw)
        self.mw = mw
        self._syncer = syncer
        self._close_when_done = close_when_done
        self.form = aqt.forms.synclog.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(tr(TR.SYNC_MEDIA_LOG_TITLE))
        disable_help_button(self)
        self.abort_button = QPushButton(tr(TR.SYNC_ABORT_BUTTON))
        qconnect(self.abort_button.clicked, self._on_abort)
        self.abort_button.setAutoDefault(False)
        self.form.buttonBox.addButton(self.abort_button, QDialogButtonBox.ActionRole)
        self.abort_button.setHidden(not self._syncer.is_syncing())

        gui_hooks.media_sync_did_progress.append(self._on_log_entry)
        gui_hooks.media_sync_did_start_or_stop.append(self._on_start_stop)

        self.form.plainTextEdit.setPlainText(
            "\n".join(self._entry_to_text(x) for x in syncer.entries())
        )
        self.form.plainTextEdit.moveCursor(QTextCursor.End)
        self.show()

    def reject(self) -> None:
        if self._close_when_done and self._syncer.is_syncing():
            # closing while syncing on close starts an abort
            self._on_abort()
            return

        aqt.dialogs.markClosed("sync_log")
        QDialog.reject(self)

    def reopen(
        self, mw: aqt.AnkiQt, syncer: Any, close_when_done: bool = False
    ) -> None:
        self._close_when_done = close_when_done
        self.show()

    def _on_abort(self, *_args: Any) -> None:
        self._syncer.abort()
        self.abort_button.setHidden(True)

    def _time_and_text(self, stamp: int, text: str) -> str:
        asctime = time.asctime(time.localtime(stamp))
        return f"{asctime}: {text}"

    def _entry_to_text(self, entry: LogEntryWithTime) -> str:
        if isinstance(entry.entry, str):
            txt = entry.entry
        elif isinstance(entry.entry, Progress.MediaSync):
            txt = self._logentry_to_text(entry.entry)
        else:
            assert_exhaustive(entry.entry)
        return self._time_and_text(entry.time, txt)

    def _logentry_to_text(self, e: Progress.MediaSync) -> str:
        return f"{e.added}, {e.removed}, {e.checked}"

    def _on_log_entry(self, entry: LogEntryWithTime) -> None:
        self.form.plainTextEdit.appendPlainText(self._entry_to_text(entry))
        if not self._syncer.is_syncing():
            self.abort_button.setHidden(True)

    def _on_start_stop(self, running: bool) -> None:
        if not running and self._close_when_done:
            aqt.dialogs.markClosed("sync_log")
            self._close_when_done = False
            self.close()
