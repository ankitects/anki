# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Helper for running tasks on background threads.

See QueryOp() and CollectionOp() for higher-level routines.
"""

from __future__ import annotations

import traceback
from collections.abc import Callable
from concurrent.futures import Future
from concurrent.futures.thread import ThreadPoolExecutor
from threading import Lock, current_thread, main_thread
from typing import Any

import aqt
from anki.collection import Progress
from aqt.progress import ProgressUpdate
from aqt.qt import *

Closure = Callable[[], None]


class TaskManager(QObject):
    _closures_pending = pyqtSignal()

    def __init__(self, mw: aqt.AnkiQt) -> None:
        QObject.__init__(self)
        self.mw = mw.weakref()
        self._no_collection_executor = ThreadPoolExecutor()
        self._collection_executor = ThreadPoolExecutor(max_workers=1)
        self._closures: list[Closure] = []
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
        on_done: Callable[[Future], None] | None = None,
        args: dict[str, Any] | None = None,
        uses_collection=True,
    ) -> Future:
        """Use QueryOp()/CollectionOp() in new code.

        Run task on a background thread.

        If on_done is provided, it will be called on the main thread with
        the completed future.

        Args if provided will be passed on as keyword arguments to the task callable.

        Tasks that access the collection are serialized. If you're doing things that
        don't require the collection (e.g. network requests), you can pass uses_collection
        =False to allow multiple tasks to run in parallel."""
        # Before we launch a background task, ensure any pending on_done closure are run on
        # main. Qt's signal/slot system will have posted a notification, but it may
        # not have been processed yet. The on_done() closures may make small queries
        # to the database that we want to run first - if we delay them until after the
        # background task starts, and it takes out a long-running lock on the database,
        # the UI thread will hang until the end of the op.
        if current_thread() is main_thread():
            self._on_closures_pending()
        else:
            print("bug: run_in_background not called from main thread")
            traceback.print_stack()

        if args is None:
            args = {}

        executor = (
            self._collection_executor
            if uses_collection
            else self._no_collection_executor
        )
        fut = executor.submit(task, **args)

        if on_done is not None:
            fut.add_done_callback(
                lambda future: self.run_on_main(lambda: on_done(future))
            )

        return fut

    def with_progress(
        self,
        task: Callable,
        on_done: Callable[[Future], None] | None = None,
        parent: QWidget | None = None,
        label: str | None = None,
        immediate: bool = False,
        uses_collection=True,
    ) -> None:
        "Use QueryOp()/CollectionOp() in new code."
        self.mw.progress.start(parent=parent, label=label, immediate=immediate)

        def wrapped_done(fut: Future) -> None:
            self.mw.progress.finish()
            if on_done:
                on_done(fut)

        self.run_in_background(task, wrapped_done, uses_collection=uses_collection)

    def with_backend_progress(
        self,
        task: Callable,
        progress_update: Callable[[Progress, ProgressUpdate], None],
        on_done: Callable[[Future], None] | None = None,
        parent: QWidget | None = None,
        start_label: str | None = None,
        uses_collection=True,
    ) -> None:
        self.mw.progress.start_with_backend_updates(
            progress_update,
            parent=parent,
            start_label=start_label,
        )

        def wrapped_done(fut: Future) -> None:
            self.mw.progress.finish()
            # allow the event loop to close the window before we proceed
            if on_done:
                self.mw.progress.single_shot(
                    100, lambda: on_done(fut), requires_collection=False
                )

        self.run_in_background(task, wrapped_done, uses_collection=uses_collection)

    def _on_closures_pending(self) -> None:
        """Run any pending closures. This runs in the main thread."""
        with self._closures_lock:
            closures = self._closures
            self._closures = []

        for closure in closures:
            closure()
