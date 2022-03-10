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
from typing import Collection, Match, Sequence

import anki  # pylint: disable=unused-import
import anki.collection
from anki import tags_pb2
from anki._legacy import DeprecatedNamesMixin, deprecated
from anki.collection import OpChanges, OpChangesWithCount
from anki.decks import DeckId
from anki.notes import NoteId
from anki.utils import ids2str

# public exports
TagTreeNode = tags_pb2.TagTreeNode
CompleteTagRequest = tags_pb2.CompleteTagRequest
MARKED_TAG = "marked"


class TagManager(DeprecatedNamesMixin):
    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()

    # legacy add-on code expects a List return type
    def all(self) -> list[str]:
        return list(self.col._backend.all_tags())

    def __repr__(self) -> str:
        dict_ = dict(self.__dict__)
        del dict_["col"]
        return f"{super().__repr__()} {pprint.pformat(dict_, width=300)}"

    def tree(self) -> TagTreeNode:
        return self.col._backend.tag_tree()

    # Registering and fetching tags
    #############################################################

    def clear_unused_tags(self) -> OpChangesWithCount:
        return self.col._backend.clear_unused_tags()

    def set_collapsed(self, tag: str, collapsed: bool) -> OpChanges:
        "Set browser expansion state for tag, registering the tag if missing."
        return self.col._backend.set_tag_collapsed(name=tag, collapsed=collapsed)

    # Bulk addition/removal from specific notes
    #############################################################

    def bulk_add(self, note_ids: Sequence[NoteId], tags: str) -> OpChangesWithCount:
        """Add space-separate tags to provided notes, returning changed count."""
        return self.col._backend.add_note_tags(note_ids=note_ids, tags=tags)

    def bulk_remove(self, note_ids: Sequence[NoteId], tags: str) -> OpChangesWithCount:
        return self.col._backend.remove_note_tags(note_ids=note_ids, tags=tags)

    # Find&replace
    #############################################################

    def find_and_replace(
        self,
        note_ids: Sequence[int],
        search: str,
        replacement: str,
        regex: bool,
        match_case: bool,
    ) -> OpChangesWithCount:
        """Replace instances of 'search' with 'replacement' in tags.
        Each tag is matched separately. If the replacement results in an empty string,
        the tag will be removed."""
        return self.col._backend.find_and_replace_tag(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            match_case=match_case,
        )

    # Bulk addition/removal based on tag
    #############################################################

    def rename(self, old: str, new: str) -> OpChangesWithCount:
        "Rename provided tag and its children, returning number of changed notes."
        return self.col._backend.rename_tags(current_prefix=old, new_prefix=new)

    def remove(self, space_separated_tags: str) -> OpChangesWithCount:
        "Remove the provided tag(s) and their children from notes and the tag list."
        return self.col._backend.remove_tags(val=space_separated_tags)

    def reparent(self, tags: Sequence[str], new_parent: str) -> OpChangesWithCount:
        """Change the parent of the provided tags.
        If new_parent is empty, tags will be reparented to the top-level."""
        return self.col._backend.reparent_tags(tags=tags, new_parent=new_parent)

    # String-based utilities
    ##########################################################################

    def split(self, tags: str) -> list[str]:
        "Parse a string and return a list of tags."
        return [t for t in tags.replace("\u3000", " ").split(" ") if t]

    def join(self, tags: list[str]) -> str:
        "Join tags into a single string, with leading and trailing spaces."
        if not tags:
            return ""
        return f" {' '.join(tags)} "

    def rem_from_str(self, deltags: str, tags: str) -> str:
        "Delete tags if they exist."

        def wildcard(pat: str, repl: str) -> Match:
            pat = re.escape(pat).replace("\\*", ".*")
            return re.match(f"^{pat}$", repl, re.IGNORECASE)

        current_tags = self.split(tags)
        for del_tag in self.split(deltags):
            # find tags, ignoring case
            remove = []
            for cur_tag in current_tags:
                if (del_tag.lower() == cur_tag.lower()) or wildcard(del_tag, cur_tag):
                    remove.append(cur_tag)
            # remove them
            for rem in remove:
                current_tags.remove(rem)
        return self.join(current_tags)

    # List-based utilities
    ##########################################################################

    @deprecated(info="no-op - tags are now canonified when note is saved")
    def canonify(self, tag_list: list[str]) -> list[str]:
        return tag_list

    def in_list(self, tag: str, tags: list[str]) -> bool:
        "True if TAG is in TAGS. Ignore case."
        return tag.lower() in [t.lower() for t in tags]

    # legacy
    ##########################################################################

    def _legacy_register_notes(self, nids: list[int] | None = None) -> None:
        self.clear_unused_tags()

    def register(
        self, tags: Collection[str], usn: int | None = None, clear: bool = False
    ) -> None:
        print("tags.register() is deprecated and no longer works")

    def _legacy_bulk_add(self, ids: list[NoteId], tags: str, add: bool = True) -> None:
        "Add tags in bulk. TAGS is space-separated."
        if add:
            self.bulk_add(ids, tags)
        else:
            self.bulk_remove(ids, tags)

    def _legacy_bulk_rem(self, ids: list[NoteId], tags: str) -> None:
        self._legacy_bulk_add(ids, tags, False)

    @deprecated(info="no longer used by Anki, and will be removed in the future")
    def by_deck(self, did: DeckId, children: bool = False) -> list[str]:
        basequery = "select n.tags from cards c, notes n WHERE c.nid = n.id"
        if not children:
            query = f"{basequery} AND c.did=?"
            res = self.col.db.list(query, did)
            return list(set(self.split(" ".join(res))))
        dids = [did]
        for name, id in self.col.decks.children(did):
            dids.append(id)
        query = f"{basequery} AND c.did IN {ids2str(dids)}"
        res = self.col.db.list(query)
        return list(set(self.split(" ".join(res))))


TagManager.register_deprecated_attributes(
    registerNotes=(TagManager._legacy_register_notes, TagManager.clear_unused_tags),
    bulkAdd=(TagManager._legacy_bulk_add, TagManager.bulk_add),
    bulkRem=(TagManager._legacy_bulk_rem, TagManager.bulk_remove),
)
