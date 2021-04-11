# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Sequence

from anki.collection import OpChanges, OpChangesWithCount
from anki.notes import NoteId
from aqt import QWidget
from aqt.operations import CollectionOp
from aqt.utils import showInfo, tooltip, tr


def add_tags_to_notes(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
    space_separated_tags: str,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.tags.bulk_add(note_ids, space_separated_tags)
    ).success(
        lambda out: tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
    )


def remove_tags_from_notes(
    *,
    parent: QWidget,
    note_ids: Sequence[NoteId],
    space_separated_tags: str,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.tags.bulk_remove(note_ids, space_separated_tags)
    ).success(
        lambda out: tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
    )


def clear_unused_tags(*, parent: QWidget) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(parent, lambda col: col.tags.clear_unused_tags()).success(
        lambda out: tooltip(
            tr.browsing_removed_unused_tags_count(count=out.count), parent=parent
        )
    )


def rename_tag(
    *,
    parent: QWidget,
    current_name: str,
    new_name: str,
) -> CollectionOp[OpChangesWithCount]:
    def success(out: OpChangesWithCount) -> None:
        if out.count:
            tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
        else:
            showInfo(tr.browsing_tag_rename_warning_empty(), parent=parent)

    return CollectionOp(
        parent,
        lambda col: col.tags.rename(old=current_name, new=new_name),
    ).success(success)


def remove_tags_from_all_notes(
    *, parent: QWidget, space_separated_tags: str
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.tags.remove(space_separated_tags=space_separated_tags)
    ).success(
        lambda out: tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
    )


def reparent_tags(
    *, parent: QWidget, tags: Sequence[str], new_parent: str
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent, lambda col: col.tags.reparent(tags=tags, new_parent=new_parent)
    ).success(
        lambda out: tooltip(tr.browsing_notes_updated(count=out.count), parent=parent)
    )


def set_tag_collapsed(
    *, parent: QWidget, tag: str, collapsed: bool
) -> CollectionOp[OpChanges]:
    return CollectionOp(
        parent, lambda col: col.tags.set_collapsed(tag=tag, collapsed=collapsed)
    )


def find_and_replace_tag(
    *,
    parent: QWidget,
    note_ids: Sequence[int],
    search: str,
    replacement: str,
    regex: bool,
    match_case: bool,
) -> CollectionOp[OpChangesWithCount]:
    return CollectionOp(
        parent,
        lambda col: col.tags.find_and_replace(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            match_case=match_case,
        ),
    ).success(
        lambda out: tooltip(
            tr.findreplace_notes_updated(changed=out.count, total=len(note_ids)),
            parent=parent,
        ),
    )
