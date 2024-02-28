# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from dataclasses import dataclass

from anki.collection import UndoStatus


@dataclass
class UndoActionsInfo:
    can_undo: bool
    can_redo: bool

    undo_text: str
    redo_text: str

    # menu item is hidden when legacy undo is active, since it can't be undone
    show_redo: bool

    @staticmethod
    def from_undo_status(status: UndoStatus) -> UndoActionsInfo:
        from aqt import tr

        return UndoActionsInfo(
            can_undo=bool(status.undo),
            can_redo=bool(status.redo),
            undo_text=(
                tr.undo_undo_action(val=status.undo) if status.undo else tr.undo_undo()
            ),
            redo_text=(
                tr.undo_redo_action(action=status.redo)
                if status.redo
                else tr.undo_redo()
            ),
            show_redo=status.last_step > 0,
        )
