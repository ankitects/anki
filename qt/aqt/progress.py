# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from typing import Callable, Optional

import aqt.forms
from aqt.qt import *
from aqt.utils import disable_help_button, tr

# Progress info
##########################################################################


class ProgressManager:
    def __init__(self, mw: aqt.AnkiQt) -> None:
        self.mw = mw
        self.app = mw.app
        self.inDB = False
        self.blockUpdates = False
        self._show_timer: Optional[QTimer] = None
        self._busy_cursor_timer: Optional[QTimer] = None
        self._win: Optional[ProgressDialog] = None
        self._levels = 0

    # Safer timers
    ##########################################################################
    # A custom timer which avoids firing while a progress dialog is active
    # (likely due to some long-running DB operation)

    def timer(
        self, ms: int, func: Callable, repeat: bool, requiresCollection: bool = True
    ) -> QTimer:
        """Create and start a standard Anki timer.

        If the timer fires while a progress window is shown:
        - if it is a repeating timer, it will wait the same delay again
        - if it is non-repeating, it will try again in 100ms

        If requiresCollection is True, the timer will not fire if the
        collection has been unloaded. Setting it to False will allow the
        timer to fire even when there is no collection, but will still
        only fire when there is no current progress dialog."""

        def handler() -> None:
            if requiresCollection and not self.mw.col:
                # no current collection; timer is no longer valid
                print(f"Ignored progress func as collection unloaded: {repr(func)}")
                return

            if not self._levels:
                # no current progress; safe to fire
                func()
            else:
                if repeat:
                    # skip this time; we'll fire again
                    pass
                else:
                    # retry in 100ms
                    self.timer(100, func, False, requiresCollection)

        t = QTimer(self.mw)
        if not repeat:
            t.setSingleShot(True)
        qconnect(t.timeout, handler)
        t.start(ms)
        return t

    # Creating progress dialogs
    ##########################################################################

    def start(
        self,
        max: int = 0,
        min: int = 0,
        label: Optional[str] = None,
        parent: Optional[QWidget] = None,
        immediate: bool = False,
    ) -> Optional[ProgressDialog]:
        self._levels += 1
        if self._levels > 1:
            return None
        # setup window
        parent = parent or self.app.activeWindow()
        if not parent and self.mw.isVisible():
            parent = self.mw

        label = label or tr.qt_misc_processing()
        self._win = ProgressDialog(parent)
        self._win.form.progressBar.setMinimum(min)
        self._win.form.progressBar.setMaximum(max)
        self._win.form.progressBar.setTextVisible(False)
        self._win.form.label.setText(label)
        self._win.setWindowTitle("Anki")
        self._win.setWindowModality(Qt.ApplicationModal)
        self._win.setMinimumWidth(300)
        self._busy_cursor_timer = QTimer(self.mw)
        self._busy_cursor_timer.setSingleShot(True)
        self._busy_cursor_timer.start(300)
        qconnect(self._busy_cursor_timer.timeout, self._set_busy_cursor)
        self._shown: float = 0
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.time()
        self._show_timer = QTimer(self.mw)
        self._show_timer.setSingleShot(True)
        self._show_timer.start(immediate and 100 or 600)
        qconnect(self._show_timer.timeout, self._on_show_timer)
        return self._win

    def update(
        self,
        label: Optional[str] = None,
        value: Optional[int] = None,
        process: bool = True,
        maybeShow: bool = True,
        max: Optional[int] = None,
    ) -> None:
        # print self._min, self._counter, self._max, label, time.time() - self._lastTime
        if not self.mw.inMainThread():
            print("progress.update() called on wrong thread")
            return
        if maybeShow:
            self._maybeShow()
        if not self._shown:
            return
        if label:
            self._win.form.label.setText(label)

        self._max = max or 0
        self._win.form.progressBar.setMaximum(self._max)
        if self._max:
            self._counter = value or (self._counter + 1)
            self._win.form.progressBar.setValue(self._counter)

    def finish(self) -> None:
        self._levels -= 1
        self._levels = max(0, self._levels)
        if self._levels == 0:
            if self._win:
                self._closeWin()
            if self._busy_cursor_timer:
                self._busy_cursor_timer.stop()
                self._busy_cursor_timer = None
            self._restore_cursor()
            if self._show_timer:
                self._show_timer.stop()
                self._show_timer = None

    def clear(self) -> None:
        "Restore the interface after an error."
        if self._levels:
            self._levels = 1
            self.finish()

    def _maybeShow(self) -> None:
        if not self._levels:
            return
        if self._shown:
            return
        delta = time.time() - self._firstTime
        if delta > 0.5:
            self._showWin()

    def _showWin(self) -> None:
        self._shown = time.time()
        self._win.show()

    def _closeWin(self) -> None:
        if self._shown:
            while True:
                # give the window system a second to present
                # window before we close it again - fixes
                # progress window getting stuck, especially
                # on ubuntu 16.10+
                elap = time.time() - self._shown
                if elap >= 0.5:
                    break
                self.app.processEvents(QEventLoop.ExcludeUserInputEvents)  # type: ignore #possibly related to https://github.com/python/mypy/issues/6910
        # if the parent window has been deleted, the progress dialog may have
        # already been dropped; delete it if it hasn't been
        if not sip.isdeleted(self._win):
            self._win.cancel()
        self._win = None
        self._shown = 0

    def _set_busy_cursor(self) -> None:
        self.mw.app.setOverrideCursor(QCursor(Qt.WaitCursor))

    def _restore_cursor(self) -> None:
        self.app.restoreOverrideCursor()

    def busy(self) -> int:
        "True if processing."
        return self._levels

    def _on_show_timer(self) -> None:
        self._show_timer = None
        self._showWin()

    def want_cancel(self) -> bool:
        win = self._win
        if win:
            return win.wantCancel
        else:
            return False

    def set_title(self, title: str) -> None:
        win = self._win
        if win:
            win.setWindowTitle(title)


class ProgressDialog(QDialog):
    def __init__(self, parent: QWidget) -> None:
        QDialog.__init__(self, parent)
        disable_help_button(self)
        self.form = aqt.forms.progress.Ui_Dialog()
        self.form.setupUi(self)
        self._closingDown = False
        self.wantCancel = False
        # required for smooth progress bars
        self.form.progressBar.setStyleSheet("QProgressBar::chunk { width: 1px; }")

    def cancel(self) -> None:
        self._closingDown = True
        self.hide()

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._closingDown:
            evt.accept()
        else:
            self.wantCancel = True
            evt.ignore()

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key_Escape:
            evt.ignore()
            self.wantCancel = True
