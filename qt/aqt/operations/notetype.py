# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Sequence

from anki.collection import OpChanges, OpChangesWithCount, OpChangesWithId
from anki.models import (
    ChangeNotetypeRequest,
    NotetypeDict,
    NotetypeId,
    NotetypeNameIdUseCount,
)
from anki.stdmodels import StockNotetypeKind
from aqt.operations import CollectionOp
from aqt.qt import QWidget


def selected_notetype_ids_to_remove(
    notetypes: Sequence[NotetypeNameIdUseCount],
    notetype_ids: Sequence[NotetypeId],
    protected_notetype_id: NotetypeId | None = None,
) -> list[NotetypeId]:
    """Return selected note type IDs to remove, keeping at least one note type."""
    removable_count = max(len(notetypes) - 1, 0)
    requested_ids = {NotetypeId(notetype_id) for notetype_id in notetype_ids}
    protected_id = (
        NotetypeId(protected_notetype_id) if protected_notetype_id is not None else None
    )
    selected_ids = [
        NotetypeId(notetype.id)
        for notetype in notetypes
        if NotetypeId(notetype.id) in requested_ids
        and NotetypeId(notetype.id) != protected_id
    ]
    return selected_ids[:removable_count]


def add_notetype_legacy(
    *,
    parent: QWidget,
    notetype: NotetypeDict,
) -> CollectionOp[OpChangesWithId]:
    return CollectionOp(parent, lambda col: col.models.add_dict(notetype))


def update_notetype_legacy(
    *,
    parent: QWidget,
    notetype: NotetypeDict,
    skip_checks: bool = False,
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent, lambda col: col.models.update_dict(notetype, skip_checks)
    )


def remove_notetype(
    *,
    parent: QWidget,
    notetype_id: NotetypeId,
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.models.remove(notetype_id))


def remove_notetypes(
    *,
    parent: QWidget,
    notetype_ids: Sequence[NotetypeId],
    protected_notetype_id: NotetypeId | None = None,
) -> CollectionOp[OpChangesWithCount]:
    def op(col) -> OpChangesWithCount:
        ids_to_remove = selected_notetype_ids_to_remove(
            col.models.all_use_counts(),
            notetype_ids,
            protected_notetype_id,
        )
        if not ids_to_remove:
            return OpChangesWithCount(changes=OpChanges(), count=0)

        changes = col.models.remove(ids_to_remove[0])
        target = col.undo_status().last_step

        for notetype_id in ids_to_remove[1:]:
            col.models.remove(notetype_id)
            changes = col.merge_undo_entries(target)

        return OpChangesWithCount(changes=changes, count=len(ids_to_remove))

    return CollectionOp(parent, op)


def change_notetype_of_notes(
    *, parent: QWidget, input: ChangeNotetypeRequest
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.models.change_notetype_of_notes(input))


def restore_notetype_to_stock(
    *, parent: QWidget, notetype_id: NotetypeId, force_kind: StockNotetypeKind.V | None
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent,
        lambda col: col.models.restore_notetype_to_stock(notetype_id, force_kind),
    )
