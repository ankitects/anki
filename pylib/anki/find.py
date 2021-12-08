# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name

from __future__ import annotations

from typing import TYPE_CHECKING, Any

from anki.notes import NoteId

if TYPE_CHECKING:
    from anki.collection import Collection


class Finder:
    def __init__(self, col: Collection | None) -> None:
        self.col = col.weakref()
        print("Finder() is deprecated, please use col.find_cards() or .find_notes()")

    def findCards(self, query: Any, order: Any) -> Any:
        return self.col.find_cards(query, order)

    def findNotes(self, query: Any) -> Any:
        return self.col.find_notes(query)


# Find and replace
##########################################################################


def findReplace(
    col: Collection,
    nids: list[NoteId],
    src: str,
    dst: str,
    regex: bool = False,
    field: str | None = None,
    fold: bool = True,
) -> int:
    "Find and replace fields in a note. Returns changed note count."
    print("use col.find_and_replace() instead of findReplace()")
    return col.find_and_replace(
        note_ids=nids,
        search=src,
        replacement=dst,
        regex=regex,
        match_case=not fold,
        field_name=field,
    ).count


def fieldNamesForNotes(col: Collection, nids: list[NoteId]) -> list[str]:
    return list(col.field_names_for_note_ids(nids))


# Find duplicates
##########################################################################


def fieldNames(col: Collection, downcase: bool = True) -> list[str]:
    fields: set[str] = set()
    for m in col.models.all():
        for f in m["flds"]:
            name = f["name"].lower() if downcase else f["name"]
            if name not in fields:  # slower w/o
                fields.add(name)
    return list(fields)
