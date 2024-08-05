# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import time
from collections.abc import Callable
from concurrent.futures import Future
from datetime import datetime
from typing import Any

import aqt
import aqt.forms
import aqt.main
from anki.collection import Collection
from anki.errors import Interrupted
from anki.utils import int_time
from aqt import gui_hooks
from aqt.operations import QueryOp
from aqt.qt import QDialog, QDialogButtonBox, QPushButton, Qt, QTimer, qconnect
from aqt.utils import disable_help_button, show_info, tr


class MediaSyncer:
    def __init__(self, mw: aqt.main.AnkiQt) -> None:
        self.mw = mw
        self._syncing: bool = False
        self.last_progress = ""
        self._last_progress_at = 0
        gui_hooks.media_sync_did_start_or_stop.append(self._on_start_stop)

    def start(self, is_periodic_sync: bool = False) -> None:
        "Start media syncing in the background, if it's not already running."
        if not self.mw.pm.media_syncing_enabled() or not (
            auth := self.mw.pm.sync_auth()
        ):
            return

        def run(col: Collection) -> None:
            col.sync_media(auth)

        # this will exit after the thread is spawned, but may block if there's an existing
        # backend lock
        QueryOp(parent=aqt.mw, op=run, success=lambda _: 1).failure(
            lambda e: self._handle_sync_error(e, is_periodic_sync)
        ).run_in_background()

        self.start_monitoring(is_periodic_sync)

    def start_monitoring(self, is_periodic_sync: bool = False) -> None:
        if self._syncing:
            return
        self._syncing = True
        gui_hooks.media_sync_did_start_or_stop(True)
        self._update_progress(tr.sync_media_starting())

        def monitor() -> None:
            while True:
                resp = self.mw.col.media_sync_status()
                if not resp.active:
                    return
                if p := resp.progress:
                    self._update_progress(f"{p.added}, {p.removed}, {p.checked}")

                time.sleep(0.25)

        self.mw.taskman.run_in_background(
            monitor,
            lambda fut: self._on_finished(fut, is_periodic_sync),
            uses_collection=False,
        )

    def _update_progress(self, progress: str) -> None:
        self.last_progress = progress
        self.mw.taskman.run_on_main(lambda: gui_hooks.media_sync_did_progress(progress))

    def _on_finished(self, future: Future, is_periodic_sync: bool = False) -> None:
        self._syncing = False
        self._last_progress_at = int_time()
        gui_hooks.media_sync_did_start_or_stop(False)

        exc = future.exception()
        if exc is not None:
            self._handle_sync_error(exc, is_periodic_sync)
        else:
            self._update_progress(tr.sync_media_complete())

    def _handle_sync_error(
        self, exc: BaseException, is_periodic_sync: bool = False
    ) -> None:
        if isinstance(exc, Interrupted):
            self._update_progress(tr.sync_media_aborted())
        elif is_periodic_sync:
            print(str(exc))
        else:
            show_info(str(exc), modality=Qt.WindowModality.NonModal)

    def abort(self) -> None:
        if not self.is_syncing():
            return
        self.mw.col.set_wants_abort()
        self.mw.col.abort_media_sync()
        self._update_progress(tr.sync_media_aborting())

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

        timer: QTimer | None = None

        def check_finished() -> None:
            if not self.is_syncing():
                timer.deleteLater()
                on_finished()

        timer = self.mw.progress.timer(150, check_finished, True, False, parent=self.mw)

    def seconds_since_last_sync(self) -> int:
        if self.is_syncing():
            return 0

        return int_time() - self._last_progress_at


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
        self.setWindowTitle(tr.sync_media_log_title())
        disable_help_button(self)
        self.abort_button = QPushButton(tr.sync_abort_button())
        qconnect(self.abort_button.clicked, self._on_abort)
        self.abort_button.setAutoDefault(False)
        self.form.buttonBox.addButton(
            self.abort_button, QDialogButtonBox.ButtonRole.ActionRole
        )
        self.abort_button.setHidden(not self._syncer.is_syncing())

        gui_hooks.media_sync_did_progress.append(self._on_log_entry)
        gui_hooks.media_sync_did_start_or_stop.append(self._on_start_stop)

        self._on_log_entry(syncer.last_progress)
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

    def _on_log_entry(self, entry: str) -> None:
        dt = datetime.fromtimestamp(int_time())
        time = dt.strftime("%H:%M:%S")
        text = f"{time}: {entry}"
        self.form.log_label.setText(text)
        if not self._syncer.is_syncing():
            self.abort_button.setHidden(True)

    def _on_start_stop(self, running: bool) -> None:
        if not running and self._close_when_done:
            aqt.dialogs.markClosed("sync_log")
            self._close_when_done = False
            self.close()
