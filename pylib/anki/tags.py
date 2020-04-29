# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for notes.
"""

from __future__ import annotations

import re
from typing import Callable, Collection, List, Optional, Tuple

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.utils import ids2str, intTime


class TagManager:
    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col.weakref()

    # all tags
    def all(self) -> List[str]:
        return [t.tag for t in self.col.backend.all_tags()]

    # # List of (tag, usn)
    def allItems(self) -> List[Tuple[str, int]]:
        return [(t.tag, t.usn) for t in self.col.backend.all_tags()]

    # Registering and fetching tags
    #############################################################

    def register(
        self, tags: Collection[str], usn: Optional[int] = None, clear=False
    ) -> None:
        "Given a list of tags, add any missing ones to tag registry."
        changed = self.col.backend.register_tags(" ".join(tags), usn, clear)
        if changed:
            hooks.tag_list_did_update()

    def registerNotes(self, nids: Optional[List[int]] = None) -> None:
        "Add any missing tags from notes to the tags list."
        # when called without an argument, the old list is cleared first.
        if nids:
            lim = " where id in " + ids2str(nids)
            clear = False
        else:
            lim = ""
            clear = True
        self.register(
            set(
                self.split(
                    " ".join(self.col.db.list("select distinct tags from notes" + lim))
                )
            ),
            clear=clear,
        )

    def byDeck(self, did, children=False) -> List[str]:
        basequery = "select n.tags from cards c, notes n WHERE c.nid = n.id"
        if not children:
            query = basequery + " AND c.did=?"
            res = self.col.db.list(query, did)
            return list(set(self.split(" ".join(res))))
        dids = [did]
        for name, id in self.col.decks.children(did):
            dids.append(id)
        query = basequery + " AND c.did IN " + ids2str(dids)
        res = self.col.db.list(query)
        return list(set(self.split(" ".join(res))))

    # Bulk addition/removal from notes
    #############################################################

    def bulkAdd(self, ids: List[int], tags: str, add: bool = True) -> None:
        "Add tags in bulk. TAGS is space-separated."
        newTags = self.split(tags)
        if not newTags:
            return
        # cache tag names
        if add:
            self.register(newTags)
        # find notes missing the tags
        fn: Callable[[str, str], str]
        if add:
            l = "tags not "
            fn = self.addToStr
        else:
            l = "tags "
            fn = self.remFromStr
        lim = " or ".join(l + "like ?" for x in newTags)
        res = self.col.db.all(
            "select id, tags from notes where id in %s and (%s)" % (ids2str(ids), lim),
            *["%% %s %%" % y.replace("*", "%") for x, y in enumerate(newTags)],
        )
        # update tags
        def fix(row):
            return [
                fn(tags, row[1]),
                intTime(),
                self.col.usn(),
                row[0],
            ]

        self.col.db.executemany(
            "update notes set tags=?,mod=?,usn=? where id = ?",
            [fix(row) for row in res],
        )

    def bulkRem(self, ids: List[int], tags: str) -> None:
        self.bulkAdd(ids, tags, False)

    # String-based utilities
    ##########################################################################

    def split(self, tags: str) -> List[str]:
        "Parse a string and return a list of tags."
        return [t for t in tags.replace("\u3000", " ").split(" ") if t]

    def join(self, tags: List[str]) -> str:
        "Join tags into a single string, with leading and trailing spaces."
        if not tags:
            return ""
        return " %s " % " ".join(tags)

    def addToStr(self, addtags: str, tags: str) -> str:
        "Add tags if they don't exist, and canonify."
        currentTags = self.split(tags)
        for tag in self.split(addtags):
            if not self.inList(tag, currentTags):
                currentTags.append(tag)
        return self.join(self.canonify(currentTags))

    def remFromStr(self, deltags: str, tags: str) -> str:
        "Delete tags if they exist."

        def wildcard(pat, str):
            pat = re.escape(pat).replace("\\*", ".*")
            return re.match("^" + pat + "$", str, re.IGNORECASE)

        currentTags = self.split(tags)
        for tag in self.split(deltags):
            # find tags, ignoring case
            remove = []
            for tx in currentTags:
                if (tag.lower() == tx.lower()) or wildcard(tag, tx):
                    remove.append(tx)
            # remove them
            for r in remove:
                currentTags.remove(r)
        return self.join(currentTags)

    # List-based utilities
    ##########################################################################

    def canonify(self, tagList: List[str]) -> List[str]:
        "Strip duplicates, adjust case to match existing tags, and sort."
        tag_str, changed = self.col.backend.canonify_tags(" ".join(tagList))
        if changed:
            hooks.tag_list_did_update()

        return tag_str.split(" ")

    def inList(self, tag: str, tags: List[str]) -> bool:
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]
