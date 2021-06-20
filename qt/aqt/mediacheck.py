# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import itertools
import time
from concurrent.futures import Future
from typing import Iterable, List, Optional, Sequence, TypeVar

import aqt
from anki.collection import SearchNode
from anki.errors import Interrupted
from anki.media import CheckMediaResponse
from aqt.qt import *
from aqt.utils import (
    askUser,
    disable_help_button,
    restoreGeom,
    saveGeom,
    showText,
    tooltip,
    tr,
)

T = TypeVar("T")


def chunked_list(l: Iterable[T], n: int) -> Iterable[List[T]]:
    l = iter(l)
    while True:
        res = list(itertools.islice(l, n))
        if not res:
            return
        yield res


def check_media_db(mw: aqt.AnkiQt) -> None:
    c = MediaChecker(mw)
    c.check()


class MediaChecker:
    progress_dialog: Optional[aqt.progress.ProgressDialog]

    def __init__(self, mw: aqt.AnkiQt) -> None:
        self.mw = mw
        self._progress_timer: Optional[QTimer] = None

    def check(self) -> None:
        self.progress_dialog = self.mw.progress.start()
        self._set_progress_enabled(True)
        self.mw.taskman.run_in_background(self._check, self._on_finished)

    def _set_progress_enabled(self, enabled: bool) -> None:
        if self._progress_timer:
            self._progress_timer.stop()
            self._progress_timer = None
        if enabled:
            self._progress_timer = timer = QTimer()
            timer.setSingleShot(False)
            timer.setInterval(100)
            qconnect(timer.timeout, self._on_progress)
            timer.start()

    def _on_progress(self) -> None:
        if not self.mw.col:
            return
        progress = self.mw.col.latest_progress()
        if not progress.HasField("media_check"):
            return
        label = progress.media_check

        try:
            if self.progress_dialog.wantCancel:
                self.mw.col.set_wants_abort()
        except AttributeError:
            # dialog may not be active
            pass

        self.mw.taskman.run_on_main(lambda: self.mw.progress.update(label=label))

    def _check(self) -> CheckMediaResponse:
        "Run the check on a background thread."
        return self.mw.col.media.check()

    def _on_finished(self, future: Future) -> None:
        self._set_progress_enabled(False)
        self.mw.progress.finish()
        self.progress_dialog = None

        exc = future.exception()
        if isinstance(exc, Interrupted):
            return

        output: CheckMediaResponse = future.result()
        report = output.report

        # show report and offer to delete
        diag = QDialog(self.mw)
        diag.setWindowTitle(tr.media_check_window_title())
        disable_help_button(diag)
        layout = QVBoxLayout(diag)
        diag.setLayout(layout)
        text = QPlainTextEdit()
        text.setReadOnly(True)
        text.setPlainText(report)
        text.setWordWrapMode(QTextOption.NoWrap)
        layout.addWidget(text)
        box = QDialogButtonBox(QDialogButtonBox.Close)
        layout.addWidget(box)

        if output.unused:
            b = QPushButton(tr.media_check_delete_unused())
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_trash_files(output.unused))

        if output.missing:
            if any(map(lambda x: x.startswith("latex-"), output.missing)):
                b = QPushButton(tr.media_check_render_latex())
                b.setAutoDefault(False)
                box.addButton(b, QDialogButtonBox.RejectRole)
                qconnect(b.clicked, self._on_render_latex)

        if output.have_trash:
            b = QPushButton(tr.media_check_empty_trash())
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_empty_trash())

            b = QPushButton(tr.media_check_restore_trash())
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_restore_trash())

        qconnect(box.rejected, diag.reject)
        diag.setMinimumHeight(400)
        diag.setMinimumWidth(500)
        restoreGeom(diag, "checkmediadb")
        diag.exec_()
        saveGeom(diag, "checkmediadb")

    def _on_render_latex(self) -> None:
        self.progress_dialog = self.mw.progress.start()
        try:
            out = self.mw.col.media.render_all_latex(self._on_render_latex_progress)
            if self.progress_dialog.wantCancel:
                return
        finally:
            self.mw.progress.finish()
            self.progress_dialog = None

        if out is not None:
            nid, err = out
            aqt.dialogs.open("Browser", self.mw, search=(SearchNode(nid=nid),))
            showText(err, type="html")
        else:
            tooltip(tr.media_check_all_latex_rendered())

    def _on_render_latex_progress(self, count: int) -> bool:
        if self.progress_dialog.wantCancel:
            return False

        self.mw.progress.update(tr.media_check_checked(count=count))
        return True

    def _on_trash_files(self, fnames: Sequence[str]) -> None:
        if not askUser(tr.media_check_delete_unused_confirm()):
            return

        self.progress_dialog = self.mw.progress.start()

        last_progress = time.time()
        remaining = len(fnames)
        total = len(fnames)
        try:
            for chunk in chunked_list(fnames, 25):
                self.mw.col.media.trash_files(chunk)
                remaining -= len(chunk)
                if time.time() - last_progress >= 0.3:
                    self.mw.progress.update(
                        tr.media_check_files_remaining(count=remaining)
                    )
        finally:
            self.mw.progress.finish()
            self.progress_dialog = None

        tooltip(tr.media_check_delete_unused_complete(count=total))

    def _on_empty_trash(self) -> None:
        self.progress_dialog = self.mw.progress.start()
        self._set_progress_enabled(True)

        def empty_trash() -> None:
            self.mw.col.media.empty_trash()

        def on_done(fut: Future) -> None:
            self.mw.progress.finish()
            self._set_progress_enabled(False)
            # check for errors
            fut.result()

            tooltip(tr.media_check_trash_emptied())

        self.mw.taskman.run_in_background(empty_trash, on_done)

    def _on_restore_trash(self) -> None:
        self.progress_dialog = self.mw.progress.start()
        self._set_progress_enabled(True)

        def restore_trash() -> None:
            self.mw.col.media.restore_trash()

        def on_done(fut: Future) -> None:
            self.mw.progress.finish()
            self._set_progress_enabled(False)
            # check for errors
            fut.result()

            tooltip(tr.media_check_trash_restored())

        self.mw.taskman.run_in_background(restore_trash, on_done)
