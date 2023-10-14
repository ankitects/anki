# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from anki.collection import LegacyCheckpoint, OpChanges, OpChangesAfterUndo, Preferences
from anki.errors import UndoEmpty
from anki.types import assert_exhaustive
from aqt import gui_hooks
from aqt.operations import CollectionOp
from aqt.qt import QWidget
from aqt.utils import showInfo, showWarning, tooltip, tr


def undo(*, parent: QWidget) -> None:
    "Undo the last operation, and refresh the UI."

    def on_success(out: OpChangesAfterUndo) -> None:
        gui_hooks.state_did_undo(out)
        tooltip(tr.undo_action_undone(action=out.operation), parent=parent)

    def on_failure(exc: Exception) -> None:
        if isinstance(exc, UndoEmpty):
            # backend has no undo, but there may be a checkpoint waiting
            _legacy_undo(parent=parent)
        else:
            showWarning(str(exc), parent=parent)

    CollectionOp(parent, lambda col: col.undo()).success(on_success).failure(
        on_failure
    ).run_in_background()


def redo(*, parent: QWidget) -> None:
    "Redo the last operation, and refresh the UI."

    def on_success(out: OpChangesAfterUndo) -> None:
        tooltip(tr.undo_action_redone(action=out.operation), parent=parent)

    CollectionOp(parent, lambda col: col.redo()).success(on_success).run_in_background()


def _legacy_undo(*, parent: QWidget) -> None:
    from aqt import mw

    assert mw
    assert mw.col

    result = mw.col.undo_legacy()

    if result is None:
        # should not happen
        showInfo(tr.actions_nothing_to_undo(), parent=parent)
        mw.update_undo_actions()
        return

    elif isinstance(result, LegacyCheckpoint):
        name = result.name

    else:
        assert_exhaustive(result)
        assert False

    # full queue+gui reset required
    mw.reset()

    tooltip(tr.undo_action_undone(action=name), parent=parent)
    gui_hooks.state_did_revert(name)
    mw.update_undo_actions()


def set_preferences(
    *, parent: QWidget, preferences: Preferences
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.set_preferences(preferences))
