# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from anki.collection import (
    LegacyCheckpoint,
    LegacyReviewUndo,
    OpChanges,
    OpChangesAfterUndo,
    Preferences,
)
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
            # backend has no undo, but there may be a checkpoint
            # or v1/v2 review waiting
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

    reviewing = mw.state == "review"
    just_refresh_reviewer = False

    result = mw.col.undo_legacy()

    if result is None:
        # should not happen
        showInfo("nothing to undo", parent=parent)
        mw.update_undo_actions()
        return

    elif isinstance(result, LegacyReviewUndo):
        name = tr.scheduling_review()

        if reviewing:
            # push the undone card to the top of the queue
            cid = result.card.id
            card = mw.col.getCard(cid)
            mw.reviewer.cardQueue.append(card)

            gui_hooks.review_did_undo(cid)

            just_refresh_reviewer = True

    elif isinstance(result, LegacyCheckpoint):
        name = result.name

    else:
        assert_exhaustive(result)
        assert False

    if just_refresh_reviewer:
        mw.reviewer.nextCard()
    else:
        # full queue+gui reset required
        mw.reset()

    tooltip(tr.undo_action_undone(action=name), parent=parent)
    gui_hooks.state_did_revert(name)
    mw.update_undo_actions()


def set_preferences(
    *, parent: QWidget, preferences: Preferences
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.set_preferences(preferences))
