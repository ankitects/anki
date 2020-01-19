# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Helper for running tasks on background threads.
"""

from concurrent.futures import Future
from concurrent.futures.thread import ThreadPoolExecutor
from dataclasses import dataclass
from threading import Lock
from typing import Any, Callable, Dict, List, Optional

from PyQt5.QtCore import QObject, pyqtSignal


@dataclass
class PendingDoneCallback:
    callback: Callable[[Any], None]
    future: Future


class TaskManager(QObject):
    _results_available = pyqtSignal()

    def __init__(self):
        QObject.__init__(self)

        self._executor = ThreadPoolExecutor()

        self._pending_callbacks: List[PendingDoneCallback] = []

        self._results_lock = Lock()
        self._results_available.connect(self._drain_results)  # type: ignore

    def run(
        self,
        task: Callable,
        on_done: Optional[Callable],
        args: Optional[Dict[str, Any]] = None,
    ) -> Future:
        """Run task on a background thread, calling on_done on the main thread if provided.

        Args if provided will be passed on as keyword arguments to the task callable."""
        if args is None:
            args = {}

        fut = self._executor.submit(task, **args)

        if on_done is not None:

            def done_closure(completed_future: Future) -> None:
                self._handle_done_callback(completed_future, on_done)

            fut.add_done_callback(done_closure)

        return fut

    def _handle_done_callback(self, future: Future, callback: Callable) -> None:
        """When future completes, schedule its callback to run on the main thread."""
        # add result to the queue
        with self._results_lock:
            self._pending_callbacks.append(
                PendingDoneCallback(callback=callback, future=future)
            )

        # and tell the main thread to flush the queue
        self._results_available.emit()  # type: ignore

    def _drain_results(self):
        """Fires pending callbacks in the main thread."""
        with self._results_lock:
            results = self._pending_callbacks
            self._pending_callbacks = []

        for result in results:
            result.callback(result.future)
