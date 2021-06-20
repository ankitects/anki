# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from anki.collection import OpChanges, OpChangesWithId
from anki.models import ChangeNotetypeRequest, NotetypeDict, NotetypeId
from aqt import QWidget
from aqt.operations import CollectionOp


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
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.models.update_dict(notetype))


def remove_notetype(
    *,
    parent: QWidget,
    notetype_id: NotetypeId,
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.models.remove(notetype_id))


def change_notetype_of_notes(
    *, parent: QWidget, input: ChangeNotetypeRequest
) -> CollectionOp[OpChanges]:
    return CollectionOp(parent, lambda col: col.models.change_notetype_of_notes(input))
