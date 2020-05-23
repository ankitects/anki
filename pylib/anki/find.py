# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import TYPE_CHECKING, Optional, Set

from anki.hooks import *
from anki.utils import ids2str, splitFields, stripHTMLMedia

if TYPE_CHECKING:
    from anki.collection import Collection


class Finder:
    def __init__(self, col: Optional[Collection]) -> None:
        self.col = col.weakref()
        print("Finder() is deprecated, please use col.find_cards() or .find_notes()")

    def findCards(self, query, order):
        return self.col.find_cards(query, order)

    def findNotes(self, query):
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
    return col.backend.find_and_replace(
        nids=nids,
        search=src,
        replacement=dst,
        regex=regex,
        match_case=not fold,
        field_name=field,
    )


def fieldNamesForNotes(col: Collection, nids: List[int]) -> List[str]:
    return list(col.backend.field_names_for_notes(nids))


# Find duplicates
##########################################################################


def fieldNames(col, downcase=True) -> List:
    fields: Set[str] = set()
    for m in col.models.all():
        for f in m["flds"]:
            name = f["name"].lower() if downcase else f["name"]
            if name not in fields:  # slower w/o
                fields.add(name)
    return list(fields)


# returns array of ("dupestr", [nids])
def findDupes(
    col: Collection, fieldName: str, search: str = ""
) -> List[Tuple[Any, List]]:
    # limit search to notes with applicable field name
    if search:
        search = "(" + search + ") "
    search += '"%s:*"' % fieldName.replace('"', '"')
    # go through notes
    vals: Dict[str, List[int]] = {}
    dupes = []
    fields: Dict[int, int] = {}

    def ordForMid(mid):
        if mid not in fields:
            model = col.models.get(mid)
            for c, f in enumerate(model["flds"]):
                if f["name"].lower() == fieldName.lower():
                    fields[mid] = c
                    break
        return fields[mid]

    for nid, mid, flds in col.db.all(
        "select id, mid, flds from notes where id in " + ids2str(col.findNotes(search))
    ):
        flds = splitFields(flds)
        ord = ordForMid(mid)
        if ord is None:
            continue
        val = flds[ord]
        val = stripHTMLMedia(val)
        # empty does not count as duplicate
        if not val:
            continue
        vals.setdefault(val, []).append(nid)
        if len(vals[val]) == 2:
            dupes.append((val, vals[val]))
    return dupes
