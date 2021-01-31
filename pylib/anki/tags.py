# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Anki maintains a cache of used tags so it can quickly present a list of tags
for autocomplete and in the browser. For efficiency, deletions are not
tracked, so unused tags can only be removed from the list with a DB check.

This module manages the tag cache and tags for notes.
"""

from __future__ import annotations

import pprint
import re
from typing import Collection, List, Match, Optional, Sequence, Tuple

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
import anki.collection
from anki.utils import ids2str

# public exports
TagTreeNode = _pb.TagTreeNode


class TagManager:
    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()

    # all tags
    def all(self) -> List[str]:
        return [t.name for t in self.col._backend.all_tags()]

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    # # List of (tag, usn)
    def allItems(self) -> List[Tuple[str, int]]:
        return [(t.name, t.usn) for t in self.col._backend.all_tags()]

    def tree(self) -> TagTreeNode:
        return self.col._backend.tag_tree()

    # Registering and fetching tags
    #############################################################

    def register(
        self, tags: Collection[str], usn: Optional[int] = None, clear: bool = False
    ) -> None:
        print("tags.register() is deprecated and no longer works")

    def registerNotes(self, nids: Optional[List[int]] = None) -> None:
        "Clear unused tags and add any missing tags from notes to the tag list."
        self.clear_unused_tags()

    def clear_unused_tags(self) -> None:
        self.col._backend.clear_unused_tags()

    def byDeck(self, did: int, children: bool = False) -> List[str]:
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

    def set_collapsed(self, tag: str, collapsed: bool) -> None:
        "Set browser collapse state for tag, registering the tag if missing."
        self.col._backend.set_tag_collapsed(name=tag, collapsed=collapsed)

    # Bulk addition/removal from notes
    #############################################################

    def bulk_add(self, nids: List[int], tags: str) -> int:
        """Add space-separate tags to provided notes, returning changed count."""
        return self.col._backend.add_note_tags(nids=nids, tags=tags)

    def bulk_update(
        self, nids: Sequence[int], tags: str, replacement: str, regex: bool
    ) -> int:
        """Replace space-separated tags, returning changed count.
        Tags replaced with an empty string will be removed."""
        return self.col._backend.update_note_tags(
            nids=nids, tags=tags, replacement=replacement, regex=regex
        )

    def rename(self, old: str, new: str) -> int:
        "Rename provided tag, returning number of changed notes."
        nids = self.col.find_notes(anki.collection.SearchTerm(tag=old))
        if not nids:
            return 0
        escaped_name = re.sub(r"[*_\\]", r"\\\g<0>", old)
        return self.bulk_update(nids, escaped_name, new, False)

    def remove(self, tag: str) -> None:
        self.col._backend.clear_tag(tag)

    # legacy routines

    def bulkAdd(self, ids: List[int], tags: str, add: bool = True) -> None:
        "Add tags in bulk. TAGS is space-separated."
        if add:
            self.bulk_add(ids, tags)
        else:
            self.bulk_update(ids, tags, "", False)

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

        def wildcard(pat: str, repl: str) -> Match:
            pat = re.escape(pat).replace("\\*", ".*")
            return re.match("^" + pat + "$", repl, re.IGNORECASE)

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

    # this is now a no-op - the tags are canonified when the note is saved
    def canonify(self, tagList: List[str]) -> List[str]:
        return tagList

    def inList(self, tag: str, tags: List[str]) -> bool:
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]
