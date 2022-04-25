# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from abc import ABC
from concurrent.futures._base import Future
from typing import Any, Callable, Generic, Literal, Protocol, TypeVar, Union

import aqt
import aqt.gui_hooks
import aqt.main
from anki.collection import (
    Collection,
    ImportLogWithChanges,
    OpChanges,
    OpChangesAfterUndo,
    OpChangesWithCount,
    OpChangesWithId,
    Progress,
)
from aqt.errors import show_exception
from aqt.qt import QTimer, QWidget, qconnect
from aqt.utils import showWarning, tr


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
                self._finish_op(mw, result, initiator)

        self._run(mw, wrapped_op, wrapped_done)

    def _run(
        self,
        mw: aqt.main.AnkiQt,
        op: Callable[[], ResultWithChanges],
        on_done: Callable[[Future], None],
    ) -> None:
        mw.taskman.with_progress(op, on_done)

    def _finish_op(
        self, mw: aqt.main.AnkiQt, result: ResultWithChanges, initiator: object | None
    ) -> None:
        mw.update_undo_actions()
        mw.autosave()
        self._fire_change_hooks_after_op_performed(result, initiator)

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

            if self._progress:
                mw.progress.finish()

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
        mw.taskman.run_in_background(op, on_done)


class OpWithBackendProgress(ABC):
    """Periodically queries the backend for progress updates, and enables abortion.

    Requires a key for a value on the `Progress` proto message."""

    def __init__(
        self,
        *args: Any,
        key: Literal["importing", "exporting"],
        **kwargs: Any,
    ):
        self._key = key
        self._timer = QTimer()
        self._timer.setSingleShot(False)
        self._timer.setInterval(100)
        super().__init__(*args, **kwargs)

    def _run(
        self,
        mw: aqt.main.AnkiQt,
        op: Callable,
        on_done: Callable[[Future], None],
    ) -> None:
        if not (dialog := mw.progress.start(immediate=True, parent=mw)):
            print("Progress dialog already running; aborting will not work")

        def on_progress() -> None:
            assert mw

            progress = mw.backend.latest_progress()
            if not (label := label_from_progress(progress, self._key)):
                return
            if dialog and dialog.wantCancel:
                mw.backend.set_wants_abort()
            mw.taskman.run_on_main(lambda: mw.progress.update(label=label))

        def wrapped_on_done(future: Future) -> None:
            self._timer.deleteLater()
            assert mw
            mw.progress.finish()
            on_done(future)

        qconnect(self._timer.timeout, on_progress)
        self._timer.start()
        mw.taskman.run_in_background(task=op, on_done=wrapped_on_done)


def label_from_progress(
    progress: Progress,
    key: Literal["importing", "exporting"],
) -> str | None:
    if not progress.HasField(key):
        return None
    if key == "importing":
        return progress.importing
    if key == "exporting":
        return tr.exporting_exported_media_file(count=progress.exporting)


class CollectionOpWithBackendProgress(OpWithBackendProgress, CollectionOp):
    pass


class QueryOpWithBackendProgress(OpWithBackendProgress, QueryOp):
    def with_progress(self, *_args: Any) -> Any:
        raise NotImplementedError


class ClosedCollectionOp(CollectionOp):
    """For CollectionOps that need to be run on a closed collection.

    If a collection is open, backs it up and unloads it, before running the op.
    Reloads it, if that has not been done by a callback, yet.
    """

    def __init__(
        self,
        parent: QWidget,
        op: Callable[[], ResultWithChanges],
        *args: Any,
        **kwargs: Any,
    ):
        super().__init__(parent, lambda _: op(), *args, **kwargs)

    def _run(
        self,
        mw: aqt.main.AnkiQt,
        op: Callable[[], ResultWithChanges],
        on_done: Callable[[Future], None],
    ) -> None:
        if mw.col:
            QueryOp(
                parent=mw,
                op=lambda _: mw.create_backup_now(),
                success=lambda _: mw.unloadCollection(
                    lambda: super(ClosedCollectionOp, self)._run(mw, op, on_done)
                ),
            ).with_progress().run_in_background()
        else:
            super()._run(mw, op, on_done)

    def _finish_op(
        self,
        _mw: aqt.main.AnkiQt,
        _result: ResultWithChanges,
        _initiator: object | None,
    ) -> None:
        pass


class ClosedCollectionOpWithBackendProgress(
    ClosedCollectionOp, CollectionOpWithBackendProgress
):
    """See ClosedCollectionOp and CollectionOpWithBackendProgress."""
