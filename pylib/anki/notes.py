# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.models import Field, NoteType
from anki.utils import (
    fieldChecksum,
    guid64,
    intTime,
    joinFields,
    splitFields,
    stripHTMLMedia,
    timestampID,
)


class Note:
    col: anki.storage._Collection
    newlyAdded: bool
    id: int
    guid: str
    _model: NoteType
    mid: int
    tags: List[str]
    fields: List[str]
    flags: int
    data: str
    _fmap: Dict[str, Tuple[int, Field]]
    scm: int

    def __init__(
        self,
        col: anki.storage._Collection,
        model: Optional[NoteType] = None,
        id: Optional[int] = None,
    ) -> None:
        assert not (model and id)
        self.col = col.weakref()
        self.newlyAdded = False
        if id:
            self.id = id
            self.load()
        else:
            self.id = timestampID(col.db, "notes")
            self.guid = guid64()
            self._model = model
            self.mid = model["id"]
            self.tags = []
            self.fields = [""] * len(self._model["flds"])
            self.flags = 0
            self.data = ""
            self._fmap = self.col.models.fieldMap(self._model)
            self.scm = self.col.scm

    def load(self) -> None:
        (
            self.guid,
            self.mid,
            self.mod,
            self.usn,
            tags,
            fields,
            self.flags,
            self.data,
        ) = self.col.db.first(
            """
select guid, mid, mod, usn, tags, flds, flags, data
from notes where id = ?""",
            self.id,
        )
        self.fields = splitFields(fields)
        self.tags = self.col.tags.split(tags)
        self._model = self.col.models.get(self.mid)
        self._fmap = self.col.models.fieldMap(self._model)
        self.scm = self.col.scm

    def flush(self, mod: Optional[int] = None) -> None:
        "If fields or tags have changed, write changes to disk."
        assert self.scm == self.col.scm
        self._preFlush()
        sfld = stripHTMLMedia(self.fields[self.col.models.sortIdx(self._model)])
        tags = self.stringTags()
        fields = self.joinedFields()
        if not mod and self.col.db.scalar(
            "select 1 from notes where id = ? and tags = ? and flds = ?",
            self.id,
            tags,
            fields,
        ):
            return
        csum = fieldChecksum(self.fields[0])
        self.mod = mod if mod else intTime()
        self.usn = self.col.usn()
        res = self.col.db.execute(
            """
insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)""",
            self.id,
            self.guid,
            self.mid,
            self.mod,
            self.usn,
            tags,
            fields,
            sfld,
            csum,
            self.flags,
            self.data,
        )
        self.col.tags.register(self.tags)
        self._postFlush()

    def joinedFields(self) -> str:
        return joinFields(self.fields)

    def cards(self) -> List[anki.cards.Card]:
        return [
            self.col.getCard(id)
            for id in self.col.db.list(
                "select id from cards where nid = ? order by ord", self.id
            )
        ]

    def model(self) -> Optional[NoteType]:
        return self._model

    # Dict interface
    ##################################################

    def keys(self) -> List[str]:
        return list(self._fmap.keys())

    def values(self) -> List[str]:
        return self.fields

    def items(self) -> List[Tuple[Any, Any]]:
        return [(f["name"], self.fields[ord]) for ord, f in sorted(self._fmap.values())]

    def _fieldOrd(self, key: str) -> Any:
        try:
            return self._fmap[key][0]
        except:
            raise KeyError(key)

    def __getitem__(self, key: str) -> str:
        return self.fields[self._fieldOrd(key)]

    def __setitem__(self, key: str, value: str) -> None:
        self.fields[self._fieldOrd(key)] = value

    def __contains__(self, key) -> bool:
        return key in self._fmap

    # Tags
    ##################################################

    def hasTag(self, tag: str) -> Any:
        return self.col.tags.inList(tag, self.tags)

    def stringTags(self) -> Any:
        return self.col.tags.join(self.col.tags.canonify(self.tags))

    def setTagsFromStr(self, tags: str) -> None:
        self.tags = self.col.tags.split(tags)

    def delTag(self, tag: str) -> None:
        rem = []
        for t in self.tags:
            if t.lower() == tag.lower():
                rem.append(t)
        for r in rem:
            self.tags.remove(r)

    def addTag(self, tag: str) -> None:
        # duplicates will be stripped on save
        self.tags.append(tag)

    # Unique/duplicate check
    ##################################################

    def dupeOrEmpty(self) -> int:
        "1 if first is empty; 2 if first is a duplicate, False otherwise."
        val = self.fields[0]
        if not val.strip():
            return 1
        csum = fieldChecksum(val)
        # find any matching csums and compare
        for flds in self.col.db.list(
            "select flds from notes where csum = ? and id != ? and mid = ?",
            csum,
            self.id or 0,
            self.mid,
        ):
            if stripHTMLMedia(splitFields(flds)[0]) == stripHTMLMedia(self.fields[0]):
                return 2
        return False

    # Flushing cloze notes
    ##################################################

    def _preFlush(self) -> None:
        hooks.note_will_flush(self)
        # have we been added yet?
        self.newlyAdded = not self.col.db.scalar(
            "select 1 from cards where nid = ?", self.id
        )

    def _postFlush(self) -> None:
        # generate missing cards
        if not self.newlyAdded:
            rem = self.col.genCards([self.id])
            # popping up a dialog while editing is confusing; instead we can
            # document that the user should open the templates window to
            # garbage collect empty cards
            # self.col.remEmptyCards(ids)
