# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from typing import Optional

import aqt.forms
from aqt.qt import *
from aqt.utils import TR, tr

# Progress info
##########################################################################


class ProgressManager:
    def __init__(self, mw):
        self.mw = mw
        self.app = QApplication.instance()
        self.inDB = False
        self.blockUpdates = False
        self._show_timer: Optional[QTimer] = None
        self._win = None
        self._levels = 0

    # Safer timers
    ##########################################################################
    # A custom timer which avoids firing while a progress dialog is active
    # (likely due to some long-running DB operation)

    def timer(self, ms, func, repeat, requiresCollection=True):
        """Create and start a standard Anki timer.

        If the timer fires while a progress window is shown:
        - if it is a repeating timer, it will wait the same delay again
        - if it is non-repeating, it will try again in 100ms

        If requiresCollection is True, the timer will not fire if the
        collection has been unloaded. Setting it to False will allow the
        timer to fire even when there is no collection, but will still
        only fire when there is no current progress dialog."""

        def handler():
            if requiresCollection and not self.mw.col:
                # no current collection; timer is no longer valid
                print("Ignored progress func as collection unloaded: %s" % repr(func))
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
        self, max=0, min=0, label=None, parent=None, immediate=False
    ) -> Optional[ProgressDialog]:
        self._levels += 1
        if self._levels > 1:
            return None
        # setup window
        parent = parent or self.app.activeWindow()
        if not parent and self.mw.isVisible():
            parent = self.mw

        label = label or tr(TR.QT_MISC_PROCESSING)
        self._win = ProgressDialog(parent)
        self._win.form.progressBar.setMinimum(min)
        self._win.form.progressBar.setMaximum(max)
        self._win.form.progressBar.setTextVisible(False)
        self._win.form.label.setText(label)
        self._win.setWindowTitle("Anki")
        self._win.setWindowModality(Qt.ApplicationModal)
        self._win.setMinimumWidth(300)
        self._setBusy()
        self._shown: float = 0
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.time()
        self._lastUpdate = time.time()
        self._updating = False
        self._show_timer = QTimer(self.mw)
        self._show_timer.setSingleShot(True)
        self._show_timer.start(immediate and 100 or 600)
        qconnect(self._show_timer.timeout, self._on_show_timer)
        return self._win

    def update(
        self,
        label=None,
        value=None,
        process=True,
        maybeShow=True,
        max: Optional[int] = None,
    ) -> None:
        # print self._min, self._counter, self._max, label, time.time() - self._lastTime
        if not self.mw.inMainThread():
            print("progress.update() called on wrong thread")
            return
        if self._updating:
            return
        if maybeShow:
            self._maybeShow()
        if not self._shown:
            return
        elapsed = time.time() - self._lastUpdate
        if label:
            self._win.form.label.setText(label)

        self._max = max or 0
        self._win.form.progressBar.setMaximum(self._max)
        if self._max:
            self._counter = value or (self._counter + 1)
            self._win.form.progressBar.setValue(self._counter)

        if process and elapsed >= 0.2:
            self._updating = True
            self.app.processEvents()  # type: ignore #possibly related to https://github.com/python/mypy/issues/6910
            self._updating = False
            self._lastUpdate = time.time()

    def finish(self):
        self._levels -= 1
        self._levels = max(0, self._levels)
        if self._levels == 0:
            if self._win:
                self._closeWin()
            self._unsetBusy()
            if self._show_timer:
                self._show_timer.stop()
                self._show_timer = None

    def clear(self):
        "Restore the interface after an error."
        if self._levels:
            self._levels = 1
            self.finish()

    def _maybeShow(self):
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
        self._win.cancel()
        self._win = None
        self._shown = 0

    def _setBusy(self):
        self.mw.app.setOverrideCursor(QCursor(Qt.WaitCursor))

    def _unsetBusy(self):
        self.app.restoreOverrideCursor()

    def busy(self):
        "True if processing."
        return self._levels

    def _on_show_timer(self):
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
    def __init__(self, parent):
        QDialog.__init__(self, parent)
        self.form = aqt.forms.progress.Ui_Dialog()
        self.form.setupUi(self)
        self._closingDown = False
        self.wantCancel = False
        # required for smooth progress bars
        self.form.progressBar.setStyleSheet("QProgressBar::chunk { width: 1px; }")

    def cancel(self):
        self._closingDown = True
        self.hide()

    def closeEvent(self, evt):
        if self._closingDown:
            evt.accept()
        else:
            self.wantCancel = True
            evt.ignore()

    def keyPressEvent(self, evt):
        if evt.key() == Qt.Key_Escape:
            evt.ignore()
            self.wantCancel = True
