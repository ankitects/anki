# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for notes.
"""

from __future__ import annotations

import json
import re
from typing import Callable, Collection, Dict, List, Optional, Tuple

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.decks import DeckId
from anki.notes import NoteId
from anki.utils import ids2str, intTime

# Type of a single tag
Tag = str
# Type of a space separated list of tags."""
Tags = str


class TagManager:

    tags: Dict[Tag, anki.collection.Usn]

    # Registry save/load
    #############################################################

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col
        self.tags = {}

    def load(self, json_) -> None:
        self.tags = json.loads(json_)
        self.changed = False

    def flush(self) -> None:
        if self.changed:
            self.col.db.execute("update col set tags=?", json.dumps(self.tags))
            self.changed = False

    # Registering and fetching tags
    #############################################################

    def register(
        self, tags: Collection[Tag], usn: Optional[anki.collection.Usn] = None
    ) -> None:
        "Given a list of tags, add any missing ones to tag registry."
        found = False
        for t in tags:
            if t not in self.tags:
                found = True
                self.tags[t] = self.col.usn() if usn is None else usn
                self.changed = True
        if found:
            hooks.tag_added(t)  # pylint: disable=undefined-loop-variable

    def all(self) -> List[Tag]:
        return list(self.tags.keys())

    def registerNotes(self, nids: Optional[List[NoteId]] = None) -> None:
        "Add any missing tags from notes to the tags list."
        # when called without an argument, the old list is cleared first.
        if nids:
            lim = " where id in " + ids2str(nids)
        else:
            lim = ""
            self.tags = {}
            self.changed = True
        self.register(
            set(
                self.split(
                    " ".join(self.col.db.list("select distinct tags from notes" + lim))
                )
            )
        )

    def allItems(self) -> List[Tuple[Tag, int]]:
        return list(self.tags.items())

    def save(self) -> None:
        self.changed = True

    def byDeck(self, did: DeckId, children=False) -> List[Tag]:
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

    def bulkAdd(self, ids: List[NoteId], tags: Tags, add=True) -> None:
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
        lim = " or ".join([l + "like :_%d" % c for c, t in enumerate(newTags)])
        res = self.col.db.all(
            "select id, tags from notes where id in %s and (%s)" % (ids2str(ids), lim),
            **dict(
                [
                    ("_%d" % x, "%% %s %%" % y.replace("*", "%"))
                    for x, y in enumerate(newTags)
                ]
            ),
        )
        # update tags
        nids = []

        def fix(row):
            nids.append(row[0])
            return {
                "id": row[0],
                "t": fn(tags, row[1]),
                "n": intTime(),
                "u": self.col.usn(),
            }

        self.col.db.executemany(
            "update notes set tags=:t,mod=:n,usn=:u where id = :id",
            [fix(row) for row in res],
        )

    def bulkRem(self, ids: List[NoteId], tags: Tags) -> None:
        self.bulkAdd(ids, tags, False)

    # String-based utilities
    ##########################################################################

    def split(self, tags: Tags) -> List[Tag]:
        "Parse a string and return a list of tags."
        return [t for t in tags.replace("\u3000", " ").split(" ") if t]

    def join(self, tags: List[Tag]) -> Tags:
        "Join tags into a single string, with leading and trailing spaces."
        if not tags:
            return ""
        return " %s " % " ".join(tags)

    def addToStr(self, addtags: Tags, tags: Tags) -> Tags:
        "Add tags if they don't exist, and canonify."
        currentTags = self.split(tags)
        for tag in self.split(addtags):
            if not self.inList(tag, currentTags):
                currentTags.append(tag)
        return self.join(self.canonify(currentTags))

    def remFromStr(self, deltags: Tags, tags: Tags) -> Tags:
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

    def canonify(self, tagList: List[Tag]) -> List[Tag]:
        "Strip duplicates, adjust case to match existing tags, and sort."
        strippedTags = []
        for t in tagList:
            s = re.sub("[\"']", "", t)
            for existingTag in self.tags:
                if s.lower() == existingTag.lower():
                    s = existingTag
            strippedTags.append(s)
        return sorted(set(strippedTags))

    def inList(self, tag: Tag, tags: List[Tag]) -> bool:
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]

    # Sync handling
    ##########################################################################

    def beforeUpload(self) -> None:
        for k in list(self.tags.keys()):
            self.tags[k] = 0
        self.save()
