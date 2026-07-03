# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from anki.collection import OpChanges, OpChangesAfterUndo, Preferences
from anki.errors import UndoEmpty
from aqt import gui_hooks
from aqt.operations import CollectionOp
from aqt.qt import QWidget
from aqt.utils import showWarning, tooltip, tr


def undo(*, parent: QWidget) -> None:
    "Undo the last operation, and refresh the UI."

    def on_success(out: OpChangesAfterUndo) -> None:
        gui_hooks.state_did_undo(out)
        tooltip(tr.undo_action_undone(action=out.operation), parent=parent)

    def on_failure(exc: Exception) -> None:
        if not isinstance(exc, UndoEmpty):
            showWarning(str(exc), parent=parent)

    CollectionOp(parent, lambda col: col.undo()).success(on_success).failure(
        on_failure
    ).run_in_background()


def redo(*, parent: QWidget) -> None:
    "Redo the last operation, and refresh the UI."

    def on_success(out: OpChangesAfterUndo) -> None:
        tooltip(tr.undo_action_redone(action=out.operation), parent=parent)

    CollectionOp(parent, lambda col: col.redo()).success(on_success).run_in_background()


def set_preferences(
    *, parent: QWidget, preferences: Preferences
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.set_preferences(preferences))
