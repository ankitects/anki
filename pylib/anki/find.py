# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from typing import TYPE_CHECKING, Optional, Set

from anki.hooks import *
from anki.utils import ids2str, intTime, joinFields, splitFields, stripHTMLMedia

if TYPE_CHECKING:
    from anki.collection import _Collection


class Finder:
    def __init__(self, col: Optional[_Collection]) -> None:
        self.col = col.weakref()
        print("Finder() is deprecated, please use col.find_cards() or .find_notes()")

    def findCards(self, query, order):
        return self.col.find_cards(query, order)

    def findNotes(self, query):
        return self.col.find_notes(query)


# Find and replace
##########################################################################


def findReplace(
    col: _Collection,
    nids: List[int],
    src: str,
    dst: str,
    regex: bool = False,
    field: Optional[str] = None,
    fold: bool = True,
) -> int:
    "Find and replace fields in a note."
    mmap: Dict[str, Any] = {}
    if field:
        for m in col.models.all():
            for f in m["flds"]:
                if f["name"].lower() == field.lower():
                    mmap[str(m["id"])] = f["ord"]
        if not mmap:
            return 0
    # find and gather replacements
    if not regex:
        src = re.escape(src)
        dst = dst.replace("\\", "\\\\")
    if fold:
        src = "(?i)" + src
    compiled_re = re.compile(src)

    def repl(s: str):
        return compiled_re.sub(dst, s)

    d = []
    snids = ids2str(nids)
    nids = []
    for nid, mid, flds in col.db.execute(
        "select id, mid, flds from notes where id in " + snids
    ):
        origFlds = flds
        # does it match?
        sflds = splitFields(flds)
        if field:
            try:
                ord = mmap[str(mid)]
                sflds[ord] = repl(sflds[ord])
            except KeyError:
                # note doesn't have that field
                continue
        else:
            for c in range(len(sflds)):
                sflds[c] = repl(sflds[c])
        flds = joinFields(sflds)
        if flds != origFlds:
            nids.append(nid)
            d.append((flds, intTime(), col.usn(), nid))
    if not d:
        return 0
    # replace
    col.db.executemany("update notes set flds=?,mod=?,usn=? where id=?", d)
    col.updateFieldCache(nids)
    col.genCards(nids)
    return len(d)


def fieldNames(col, downcase=True) -> List:
    fields: Set[str] = set()
    for m in col.models.all():
        for f in m["flds"]:
            name = f["name"].lower() if downcase else f["name"]
            if name not in fields:  # slower w/o
                fields.add(name)
    return list(fields)


def fieldNamesForNotes(col, nids) -> List:
    fields: Set[str] = set()
    mids = col.db.list("select distinct mid from notes where id in %s" % ids2str(nids))
    for mid in mids:
        model = col.models.get(mid)
        for name in col.models.fieldNames(model):
            if name not in fields:  # slower w/o
                fields.add(name)
    return sorted(fields, key=lambda x: x.lower())


# Find duplicates
##########################################################################
# returns array of ("dupestr", [nids])
def findDupes(
    col: _Collection, fieldName: str, search: str = ""
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
