# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Helper for running tasks on background threads.
"""

from __future__ import annotations

from concurrent.futures import Future
from concurrent.futures.thread import ThreadPoolExecutor
from threading import Lock
from typing import Any, Callable, Dict, List, Optional

from PyQt5.QtCore import QObject, pyqtSignal

import aqt
from aqt.qt import QWidget, qconnect

Closure = Callable[[], None]


class TaskManager(QObject):
    _closures_pending = pyqtSignal()

    def __init__(self, mw: aqt.AnkiQt) -> None:
        QObject.__init__(self)
        self.mw = mw.weakref()
        self._executor = ThreadPoolExecutor()
        self._closures: List[Closure] = []
        self._closures_lock = Lock()
        qconnect(self._closures_pending, self._on_closures_pending)

    def run_on_main(self, closure: Closure) -> None:
        "Run the provided closure on the main thread."
        with self._closures_lock:
            self._closures.append(closure)
        self._closures_pending.emit()  # type: ignore

    def run_in_background(
        self,
        task: Callable,
        on_done: Optional[Callable[[Future], None]] = None,
        args: Optional[Dict[str, Any]] = None,
    ) -> Future:
        """Run task on a background thread.

        If on_done is provided, it will be called on the main thread with
        the completed future.

        Args if provided will be passed on as keyword arguments to the task callable."""
        if args is None:
            args = {}

        fut = self._executor.submit(task, **args)

        if on_done is not None:
            fut.add_done_callback(
                lambda future: self.run_on_main(lambda: on_done(future))
            )

        return fut

    def with_progress(
        self,
        task: Callable,
        on_done: Optional[Callable[[Future], None]] = None,
        parent: Optional[QWidget] = None,
        label: Optional[str] = None,
        immediate: bool = False,
    ) -> None:
        self.mw.progress.start(parent=parent, label=label, immediate=immediate)

        def wrapped_done(fut: Future) -> None:
            self.mw.progress.finish()
            if on_done:
                on_done(fut)

        self.run_in_background(task, wrapped_done)

    def _on_closures_pending(self) -> None:
        """Run any pending closures. This runs in the main thread."""
        with self._closures_lock:
            closures = self._closures
            self._closures = []

        for closure in closures:
            closure()
