# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import TYPE_CHECKING, Optional, Set

from anki.hooks import *

if TYPE_CHECKING:
    from anki.collection import Collection


class Finder:
    def __init__(self, col: Optional[Collection]) -> None:
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
    nids: List[int],
    src: str,
    dst: str,
    regex: bool = False,
    field: Optional[str] = None,
    fold: bool = True,
) -> int:
    "Find and replace fields in a note. Returns changed note count."
    return col._backend.find_and_replace(
        nids=nids,
        search=src,
        replacement=dst,
        regex=regex,
        match_case=not fold,
        field_name=field,
    )


def fieldNamesForNotes(col: Collection, nids: List[int]) -> List[str]:
    return list(col._backend.field_names_for_notes(nids))


# Find duplicates
##########################################################################


def fieldNames(col: Collection, downcase: bool = True) -> List:
    fields: Set[str] = set()
    for m in col.models.all():
        for f in m["flds"]:
            name = f["name"].lower() if downcase else f["name"]
            if name not in fields:  # slower w/o
                fields.add(name)
    return list(fields)
