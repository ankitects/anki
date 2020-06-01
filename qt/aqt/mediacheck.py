# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import itertools
import time
from concurrent.futures import Future
from typing import Iterable, List, Optional, Sequence, TypeVar

import aqt
from anki.rsbackend import TR, Interrupted, ProgressKind, pb
from aqt.qt import *
from aqt.utils import askUser, restoreGeom, saveGeom, showText, tooltip, tr

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
            self._progress_timer = self.mw.progress.timer(100, self._on_progress, True)

    def _on_progress(self) -> None:
        progress = self.mw.col.latest_progress()
        if progress.kind != ProgressKind.MediaCheck:
            return

        assert isinstance(progress.val, str)

        try:
            if self.progress_dialog.wantCancel:
                self.mw.col.backend.set_wants_abort()
        except AttributeError:
            # dialog may not be active
            pass

        self.mw.taskman.run_on_main(lambda: self.mw.progress.update(progress.val))

    def _check(self) -> pb.CheckMediaOut:
        "Run the check on a background thread."
        return self.mw.col.media.check()

    def _on_finished(self, future: Future) -> None:
        self._set_progress_enabled(False)
        self.mw.progress.finish()
        self.progress_dialog = None

        exc = future.exception()
        if isinstance(exc, Interrupted):
            return

        output: pb.CheckMediaOut = future.result()
        report = output.report

        # show report and offer to delete
        diag = QDialog(self.mw)
        diag.setWindowTitle(tr(TR.MEDIA_CHECK_WINDOW_TITLE))
        layout = QVBoxLayout(diag)
        diag.setLayout(layout)
        text = QTextEdit()
        text.setReadOnly(True)
        text.setPlainText(report)
        layout.addWidget(text)
        box = QDialogButtonBox(QDialogButtonBox.Close)
        layout.addWidget(box)

        if output.unused:
            b = QPushButton(tr(TR.MEDIA_CHECK_DELETE_UNUSED))
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_trash_files(output.unused))

        if output.missing:
            if any(map(lambda x: x.startswith("latex-"), output.missing)):
                b = QPushButton(tr(TR.MEDIA_CHECK_RENDER_LATEX))
                b.setAutoDefault(False)
                box.addButton(b, QDialogButtonBox.RejectRole)
                qconnect(b.clicked, self._on_render_latex)

        if output.have_trash:
            b = QPushButton(tr(TR.MEDIA_CHECK_EMPTY_TRASH))
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_empty_trash())

            b = QPushButton(tr(TR.MEDIA_CHECK_RESTORE_TRASH))
            b.setAutoDefault(False)
            box.addButton(b, QDialogButtonBox.RejectRole)
            qconnect(b.clicked, lambda c: self._on_restore_trash())

        qconnect(box.rejected, diag.reject)
        diag.setMinimumHeight(400)
        diag.setMinimumWidth(500)
        restoreGeom(diag, "checkmediadb")
        diag.exec_()
        saveGeom(diag, "checkmediadb")

    def _on_render_latex(self):
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
            browser = aqt.dialogs.open("Browser", self.mw)
            browser.form.searchEdit.lineEdit().setText("nid:%d" % nid)
            browser.onSearchActivated()
            showText(err, type="html")
        else:
            tooltip(tr(TR.MEDIA_CHECK_ALL_LATEX_RENDERED))

    def _on_render_latex_progress(self, count: int) -> bool:
        if self.progress_dialog.wantCancel:
            return False

        self.mw.progress.update(tr(TR.MEDIA_CHECK_CHECKED, count=count))
        return True

    def _on_trash_files(self, fnames: Sequence[str]):
        if not askUser(tr(TR.MEDIA_CHECK_DELETE_UNUSED_CONFIRM)):
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
                        tr(TR.MEDIA_CHECK_FILES_REMAINING, count=remaining)
                    )
        finally:
            self.mw.progress.finish()
            self.progress_dialog = None

        tooltip(tr(TR.MEDIA_CHECK_DELETE_UNUSED_COMPLETE, count=total))

    def _on_empty_trash(self):
        self.progress_dialog = self.mw.progress.start()
        self._set_progress_enabled(True)

        def empty_trash():
            self.mw.col.backend.empty_trash()

        def on_done(fut: Future):
            self.mw.progress.finish()
            self._set_progress_enabled(False)
            # check for errors
            fut.result()

            tooltip(tr(TR.MEDIA_CHECK_TRASH_EMPTIED))

        self.mw.taskman.run_in_background(empty_trash, on_done)

    def _on_restore_trash(self):
        self.progress_dialog = self.mw.progress.start()
        self._set_progress_enabled(True)

        def restore_trash():
            self.mw.col.backend.restore_trash()

        def on_done(fut: Future):
            self.mw.progress.finish()
            self._set_progress_enabled(False)
            # check for errors
            fut.result()

            tooltip(tr(TR.MEDIA_CHECK_TRASH_RESTORED))

        self.mw.taskman.run_in_background(restore_trash, on_done)
