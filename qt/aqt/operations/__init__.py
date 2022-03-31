# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures._base import Future
from typing import Any, Callable, Generic, Protocol, TypeVar, Union

import aqt
import aqt.gui_hooks
from anki.collection import (
    Collection,
    OpChanges,
    OpChangesAfterUndo,
    OpChangesWithCount,
    OpChangesWithId,
)
from aqt.errors import show_exception
from aqt.qt import QWidget
from aqt.utils import showWarning


class HasChangesProperty(Protocol):
    changes: OpChanges


# either an OpChanges object, or an object with .changes on it. This bound
# doesn't actually work for protobuf objects, so new protobuf objects will
# either need to be added here, or cast at call time
ResultWithChanges = TypeVar(
    "ResultWithChanges",
    bound=Union[
        OpChanges,
        OpChangesWithCount,
        OpChangesWithId,
        OpChangesAfterUndo,
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
                mw.update_undo_actions()
                mw.autosave()
                # fire change hooks
                self._fire_change_hooks_after_op_performed(result, initiator)

        mw.taskman.with_progress(wrapped_op, wrapped_done)

    def _fire_change_hooks_after_op_performed(
        self,
        result: ResultWithChanges,
        handler: object | None,
    ) -> None:
        from aqt import mw

        assert mw

        if isinstance(result, OpChanges):
            changes = result
        else:
            changes = result.changes  # type: ignore[union-attr]

        # fire new hook
        aqt.gui_hooks.operation_did_execute(changes, handler)
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

    def failure(self, failure: Callable[[Exception], Any] | None) -> QueryOp[T]:
        self._failure = failure
        return self

    def with_progress(
        self,
        label: str | None = None,
    ) -> QueryOp[T]:
        "If label not provided, will default to 'Processing...'"
        self._progress = label or True
        return self

    def run_in_background(self) -> None:
        from aqt import mw

        assert mw

        mw._increase_background_ops()

        def wrapped_op() -> T:
            assert mw
            if self._progress:
                label: str | None
                if isinstance(self._progress, str):
                    label = self._progress
                else:
                    label = None

                def start_progress() -> None:
                    assert mw
                    mw.progress.start(label=label)

                mw.taskman.run_on_main(start_progress)
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
                        showWarning(str(exception), self._parent)
                    return
                else:
                    # BaseException like SystemExit; rethrow it
                    future.result()

            result = future.result()
            try:
                self._success(result)
            finally:
                if self._progress:
                    mw.progress.finish()

        mw.taskman.run_in_background(wrapped_op, wrapped_done)
