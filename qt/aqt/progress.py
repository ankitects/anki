# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import time
from collections.abc import Callable
from concurrent.futures import Future
from dataclasses import dataclass

import aqt.forms
from anki._legacy import print_deprecation_warning
from anki.collection import Progress
from aqt.qt import *
from aqt.qt import sip
from aqt.utils import disable_help_button, tr

# Progress info
##########################################################################


class ProgressManager:
    def __init__(self, mw: aqt.AnkiQt) -> None:
        self.mw = mw
        self.app = mw.app
        self.inDB = False
        self.blockUpdates = False
        self._show_timer: QTimer | None = None
        self._busy_cursor_timer: QTimer | None = None
        self._win: ProgressDialog | None = None
        self._levels = 0
        self._backend_timer: QTimer | None = None

    # Safer timers
    ##########################################################################
    # Custom timers which avoid firing while a progress dialog is active
    # (likely due to some long-running DB operation)

    def timer(
        self,
        ms: int,
        func: Callable,
        repeat: bool,
        requiresCollection: bool = True,
        *,
        parent: QObject | None = None,
    ) -> QTimer:
        """Create and start a standard Anki timer. For an alternative see `single_shot()`.

        If the timer fires while a progress window is shown:
        - if it is a repeating timer, it will wait the same delay again
        - if it is non-repeating, it will try again in 100ms

        If requiresCollection is True, the timer will not fire if the
        collection has been unloaded. Setting it to False will allow the
        timer to fire even when there is no collection, but will still
        only fire when there is no current progress dialog.


        Issues and alternative
        ---
        The created timer will only be destroyed when `parent` is destroyed.
        This can cause memory leaks, because anything captured by `func` isn't freed either.
        If there is no QObject that will get destroyed reasonably soon, and you have to
        pass `mw`, you should call `deleteLater()` on the returned QTimer as soon as
        it's served its purpose, or use `single_shot()`.

        Also note that you may not be able to pass an adequate parent, if you want to
        make a callback after a widget closes. If you passed that widget, the timer
        would get destroyed before it could fire.
        """

        if parent is None:
            print_deprecation_warning(
                "to avoid memory leaks, pass an appropriate parent to progress.timer()"
                " or use progress.single_shot()"
            )
            parent = self.mw

        qtimer = QTimer(parent)
        if not repeat:
            qtimer.setSingleShot(True)
        qconnect(qtimer.timeout, self._get_handler(func, repeat, requiresCollection))
        qtimer.start(ms)
        return qtimer

    def single_shot(
        self,
        ms: int,
        func: Callable[[], None],
        requires_collection: bool = True,
    ) -> None:
        """Create and start a one-off Anki timer. For an alternative and more
        documentation, see `timer()`.


        Issues and alternative
        ---
        `single_shot()` cleans itself up, so a passed closure won't leak any memory.
        However, if `func` references a QObject other than `mw`, which gets deleted before the
        timer fires, an Exception is raised. To avoid this, either use `timer()` passing
        that object as the parent, or check in `func` with `sip.isdeleted(object)` if
        it still exists.

        On the other hand, if a widget is supposed to make an external callback after it closes,
        you likely want to use `single_shot()`, which will fire even if the calling
        widget is already destroyed.
        """
        QTimer.singleShot(ms, self._get_handler(func, False, requires_collection))

    def _get_handler(
        self, func: Callable[[], None], repeat: bool, requires_collection: bool
    ) -> Callable[[], None]:
        def handler() -> None:
            if requires_collection and not self.mw.col:
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
                    self.single_shot(100, func, requires_collection)

        return handler

    # Creating progress dialogs
    ##########################################################################

    def start(
        self,
        max: int = 0,
        min: int = 0,
        label: str | None = None,
        parent: QWidget | None = None,
        immediate: bool = False,
    ) -> ProgressDialog | None:
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
        self._win.setWindowModality(Qt.WindowModality.ApplicationModal)
        self._win.setMinimumWidth(300)
        self._busy_cursor_timer = QTimer(self.mw)
        self._busy_cursor_timer.setSingleShot(True)
        self._busy_cursor_timer.start(300)
        qconnect(self._busy_cursor_timer.timeout, self._set_busy_cursor)
        self._shown: float = 0
        self._counter = min
        self._min = min
        self._max = max
        self._firstTime = time.monotonic()
        self._show_timer = QTimer(self.mw)
        self._show_timer.setSingleShot(True)
        self._show_timer.start(immediate and 100 or 600)
        qconnect(self._show_timer.timeout, self._on_show_timer)
        return self._win

    def start_with_backend_updates(
        self,
        progress_update: Callable[[Progress, ProgressUpdate], None],
        start_label: str | None = None,
        parent: QWidget | None = None,
    ) -> None:
        self._backend_timer = QTimer()
        self._backend_timer.setSingleShot(False)
        self._backend_timer.setInterval(100)

        if not (dialog := self.start(immediate=True, label=start_label, parent=parent)):
            print("Progress dialog already running; aborting will not work")

        def on_progress() -> None:
            assert self.mw

            user_wants_abort = dialog and dialog.wantCancel or False
            update = ProgressUpdate(user_wants_abort=user_wants_abort)
            progress = self.mw.backend.latest_progress()
            progress_update(progress, update)
            if update.abort:
                self.mw.backend.set_wants_abort()
            if update.has_update():
                self.update(label=update.label, value=update.value, max=update.max)

        qconnect(self._backend_timer.timeout, on_progress)
        self._backend_timer.start()

    def update(
        self,
        label: str | None = None,
        value: int | None = None,
        process: bool = True,
        maybeShow: bool = True,
        max: int | None = None,
    ) -> None:
        # print("update", label, self._levels, self._min, self._counter, self._max, label, time.monotonic() - self._shown)
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
            self._counter = value if value is not None else (self._counter + 1)
            self._win.form.progressBar.setValue(self._counter)

    def finish(self) -> None:
        def do_window_cleanup(future: Future | None = None):
            # this method can be called in an async fashion from taskman where a future
            # is passed or in synchronous manner from the main thread
            if future is not None:
                future.result()

            next_levels = self._levels - 1
            next_levels = max(0, next_levels)
            try:
                if next_levels == 0:
                    if self._win:
                        self._closeWin()
                    if self._busy_cursor_timer:
                        self._busy_cursor_timer.stop()
                        self._busy_cursor_timer = None
                    self._restore_cursor()
                    if self._show_timer:
                        self._show_timer.stop()
                        self._show_timer = None
                if self._backend_timer:
                    self._backend_timer.stop()
                    self._backend_timer.deleteLater()
                    self._backend_timer = None
            except RuntimeError as exc:
                # during shutdown, the timers may have already been deleted by Qt
                print(f"do_window_cleanup error ignored: {exc}")
            self._levels = next_levels

        # if the window is not currently shown, we can do cleanup immediately, if it is
        # currently shown then we need to give the window system a half-second to
        # present the window before we close it again - fixes progress window getting
        # stuck, especially on ubuntu 16.10+
        elapsed_time = time.monotonic() - self._shown
        time_to_wait = 0.5 - elapsed_time
        # NOTE: we must not yield control if the window is not shown since we don't want
        # to expose ourselves to the possibility of something showing the window in the
        # meantime
        if (not self._shown) or (time_to_wait <= 0):
            do_window_cleanup()
        else:
            # NOTE: we can't use self.single_shot here since it won't call the callback
            # until _levels = 0, but if we are in this branch then _levels > 0
            self.mw.taskman.run_in_background(
                lambda time_to_wait=time_to_wait: time.sleep(time_to_wait),
                do_window_cleanup,
                uses_collection=False,
            )

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
        delta = time.monotonic() - self._firstTime
        if delta > 0.5:
            self._showWin()

    def _showWin(self) -> None:
        self._shown = time.monotonic()
        self._win.show()

    def _closeWin(self) -> None:
        # if the parent window has been deleted, the progress dialog may have
        # already been dropped; delete it if it hasn't been
        if not sip.isdeleted(self._win):
            self._win.cancel()
        self._win = None
        self._shown = 0

    def _set_busy_cursor(self) -> None:
        self.mw.app.setOverrideCursor(QCursor(Qt.CursorShape.WaitCursor))

    def _restore_cursor(self) -> None:
        self.app.restoreOverrideCursor()

    def busy(self) -> int:
        "True if processing."
        return self._levels

    def _on_show_timer(self) -> None:
        if self.mw.app.focusWindow() is None:
            # if no window is focused (eg app is minimized), defer display
            self._show_timer.start(10)
            return

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
        self.deleteLater()

    def closeEvent(self, evt: QCloseEvent) -> None:
        if self._closingDown:
            evt.accept()
        else:
            self.wantCancel = True
            evt.ignore()

    def keyPressEvent(self, evt: QKeyEvent) -> None:
        if evt.key() == Qt.Key.Key_Escape:
            evt.ignore()
            self.wantCancel = True


@dataclass
class ProgressUpdate:
    label: str | None = None
    value: int | None = None
    max: int | None = None
    user_wants_abort: bool = False
    abort: bool = False

    def has_update(self) -> bool:
        return self.label is not None or self.value is not None or self.max is not None
