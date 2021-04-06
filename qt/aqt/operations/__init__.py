# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures._base import Future
from typing import Any, Callable, Generic, Optional, Protocol, TypeVar, Union

import aqt
from anki.collection import (
    Collection,
    OpChanges,
    OpChangesAfterUndo,
    OpChangesWithCount,
    OpChangesWithId,
)
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

T = TypeVar("T")

CollectionOpSuccessCallback = Callable[[ResultWithChanges], Any]
CollectionOpFailureCallback = Optional[Callable[[Exception], Any]]


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

    _success: Optional[CollectionOpSuccessCallback] = None
    _failure: Optional[CollectionOpFailureCallback] = None

    def __init__(self, parent: QWidget, op: Callable[[Collection], ResultWithChanges]):
        self._parent = parent
        self._op = op

    def success(self, success: Optional[CollectionOpSuccessCallback]) -> CollectionOp:
        self._success = success
        return self

    def failure(self, failure: Optional[CollectionOpFailureCallback]) -> CollectionOp:
        self._failure = failure
        return self

    def run(self, *, handler: Optional[object] = None) -> None:
        aqt.mw._increase_background_ops()

        def wrapped_op() -> ResultWithChanges:
            return self._op(aqt.mw.col)

        def wrapped_done(future: Future) -> None:
            aqt.mw._decrease_background_ops()
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
                if self._success:
                    self._success(result)
            finally:
                # update undo status
                status = aqt.mw.col.undo_status()
                aqt.mw._update_undo_actions_for_status_and_save(status)
                # fire change hooks
                self._fire_change_hooks_after_op_performed(result, handler)

        aqt.mw.taskman.with_progress(wrapped_op, wrapped_done)

    def _fire_change_hooks_after_op_performed(
        self,
        result: ResultWithChanges,
        handler: Optional[object],
    ) -> None:
        if isinstance(result, OpChanges):
            changes = result
        else:
            changes = result.changes

        # fire new hook
        print("op changes:")
        print(changes)
        aqt.gui_hooks.operation_did_execute(changes, handler)
        # fire legacy hook so old code notices changes
        if aqt.mw.col.op_made_changes(changes):
            aqt.gui_hooks.state_did_reset()
