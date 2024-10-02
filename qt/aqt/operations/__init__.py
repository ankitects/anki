# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable
from concurrent.futures._base import Future
from typing import Any, Generic, Protocol, TypeVar, Union

import aqt
import aqt.gui_hooks
import aqt.main
from anki.collection import (
    Collection,
    ImportLogWithChanges,
    OpChanges,
    OpChangesAfterUndo,
    OpChangesOnly,
    OpChangesWithCount,
    OpChangesWithId,
    Progress,
)
from aqt.errors import show_exception
from aqt.progress import ProgressUpdate
from aqt.qt import QWidget


class HasChangesProperty(Protocol):
    changes: OpChanges


# either an OpChanges object, or an object with .changes on it. This bound
# doesn't actually work for protobuf objects, so new protobuf objects will
# either need to be added here, or cast at call time
ResultWithChanges = TypeVar(
    "ResultWithChanges",
    bound=Union[
        OpChanges,
        OpChangesOnly,
        OpChangesWithCount,
        OpChangesWithId,
        OpChangesAfterUndo,
        ImportLogWithChanges,
        HasChangesProperty,
    ],
)


class CollectionOp(Generic[ResultWithChanges]):
    """Helper to perform a mutating DB operation on a background thread, and update UI.

    `op` should either return OpChanges, or an object with a 'changes'
    property. The changes will be passed to `operation_did_execute` so that
    the UI can decide whether it needs to update itself.

    - Shows progress popup for the duration of the op.
    - Ensures the browser doesn't try to redraw during the operation, which can lead
    to a frozen UI
    - Updates undo state at the end of the operation
    - Commits changes
    - Fires the `operation_(will|did)_reset` hooks
    - Fires the legacy `state_did_reset` hook

    Be careful not to call any UI routines in `op`, as that may crash Qt.
    This includes things select .selectedCards() in the browse screen.

    `success` will be called with the return value of op().

    If op() throws an exception, it will be shown in a popup, or
    passed to `failure` if it is provided.
    """

    _success: Callable[[ResultWithChanges], Any] | None = None
    _failure: Callable[[Exception], Any] | None = None
    _progress_update: Callable[[Progress, ProgressUpdate], None] | None = None

    def __init__(self, parent: QWidget, op: Callable[[Collection], ResultWithChanges]):
        self._parent = parent
        self._op = op

    def success(
        self, success: Callable[[ResultWithChanges], Any] | None
    ) -> CollectionOp[ResultWithChanges]:
        self._success = success
        return self

    def failure(
        self, failure: Callable[[Exception], Any] | None
    ) -> CollectionOp[ResultWithChanges]:
        self._failure = failure
        return self

    def with_backend_progress(
        self, progress_update: Callable[[Progress, ProgressUpdate], None] | None
    ) -> CollectionOp[ResultWithChanges]:
        self._progress_update = progress_update
        return self

    def run_in_background(self, *, initiator: object | None = None) -> None:
        from aqt import mw

        assert mw

        mw._increase_background_ops()

        def wrapped_op() -> ResultWithChanges:
            assert mw
            return self._op(mw.col)

        def wrapped_done(future: Future) -> None:
            assert mw
            mw._decrease_background_ops()
            # did something go wrong?
            if exception := future.exception():
                if isinstance(exception, Exception):
                    if self._failure:
                        self._failure(exception)
                    else:
                        show_exception(parent=self._parent, exception=exception)
                    return
                else:
                    # BaseException like SystemExit; rethrow it
                    future.result()

            result = future.result()
            try:
                if self._success:
                    self._success(result)
            finally:
                on_op_finished(mw, result, initiator)

        self._run(mw, wrapped_op, wrapped_done)

    def _run(
        self,
        mw: aqt.main.AnkiQt,
        op: Callable[[], ResultWithChanges],
        on_done: Callable[[Future], None],
    ) -> None:
        if self._progress_update:
            mw.taskman.with_backend_progress(
                op, self._progress_update, on_done=on_done, parent=self._parent
            )
        else:
            mw.taskman.with_progress(op, on_done, parent=self._parent)


def on_op_finished(
    mw: aqt.main.AnkiQt, result: ResultWithChanges, initiator: object | None
) -> None:
    mw.update_undo_actions()

    if isinstance(result, OpChanges):
        changes = result
    else:
        changes = result.changes  # type: ignore[union-attr]

    # fire new hook
    aqt.gui_hooks.operation_did_execute(changes, initiator)
    # fire legacy hook so old code notices changes
    if mw.col.op_made_changes(changes):
        aqt.gui_hooks.state_did_reset()


T = TypeVar("T")


class QueryOp(Generic[T]):
    """Helper to perform an operation on a background thread.

    QueryOp is primarily used for read-only requests (reading information
    from the database, fetching data from the network, etc), but can also
    be used for mutable requests outside of the collection undo system
    (eg adding/deleting files, calling a collection method that doesn't support
    undo, etc). For operations that support undo, use CollectionOp instead.

    - Optionally shows progress popup for the duration of the op.
    - Ensures the browser doesn't try to redraw during the operation, which can lead
    to a frozen UI

    Be careful not to call any UI routines in `op`, as that may crash Qt.
    This includes things like .selectedCards() in the browse screen.

    `success` will be called with the return value of op().

    If op() throws an exception, it will be shown in a popup, or
    passed to `failure` if it is provided.
    """

    _failure: Callable[[Exception], Any] | None = None
    _progress: bool | str = False
    _progress_update: Callable[[Progress, ProgressUpdate], None] | None = None

    def __init__(
        self,
        *,
        parent: QWidget,
        op: Callable[[Collection], T],
        success: Callable[[T], Any],
    ):
        self._parent = parent
        self._op = op
        self._success = success
        self._uses_collection = True

    def failure(self, failure: Callable[[Exception], Any] | None) -> QueryOp[T]:
        self._failure = failure
        return self

    def without_collection(self) -> QueryOp[T]:
        """Flag this QueryOp as not needing the collection.

        Operations that access the collection are serialized. If you're doing
        something like a series of network queries, and your operation does not
        access the collection, then you can call this to allow the requests to
        run in parallel."""
        self._uses_collection = False
        return self

    def with_progress(
        self,
        label: str | None = None,
    ) -> QueryOp[T]:
        "If label not provided, will default to 'Processing...'"
        self._progress = label or True
        return self

    def with_backend_progress(
        self, progress_update: Callable[[Progress, ProgressUpdate], None] | None
    ) -> QueryOp[T]:
        self._progress_update = progress_update
        return self

    def run_in_background(self) -> None:
        from aqt import mw

        assert mw

        mw._increase_background_ops()

        def wrapped_op() -> T:
            assert mw
            return self._op(mw.col)

        def wrapped_done(future: Future) -> None:
            assert mw

            mw._decrease_background_ops()
            # did something go wrong?
            if exception := future.exception():
                if isinstance(exception, Exception):
                    if self._failure:
                        self._failure(exception)
                    else:
                        show_exception(parent=self._parent, exception=exception)
                    return
                else:
                    # BaseException like SystemExit; rethrow it
                    future.result()

            self._success(future.result())

        self._run(mw, wrapped_op, wrapped_done)

    def _run(
        self,
        mw: aqt.main.AnkiQt,
        op: Callable[[], T],
        on_done: Callable[[Future], None],
    ) -> None:
        label = self._progress if isinstance(self._progress, str) else None
        if self._progress_update:
            mw.taskman.with_backend_progress(
                op,
                self._progress_update,
                on_done=on_done,
                start_label=label,
                parent=self._parent,
            )
        elif self._progress:
            mw.taskman.with_progress(op, on_done, label=label, parent=self._parent)
        else:
            mw.taskman.run_in_background(
                op, on_done, uses_collection=self._uses_collection
            )
