# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import html
from typing import Dict, List, Optional, Tuple, Union

from anki.collection import Collection
from anki.consts import NEW_CARDS_RANDOM, STARTING_FACTOR
from anki.importing.base import Importer
from anki.lang import TR
from anki.utils import (
    fieldChecksum,
    guid64,
    intTime,
    joinFields,
    splitFields,
    timestampID,
)

# Stores a list of fields, tags and deck
######################################################################


class ForeignNote:
    "An temporary object storing fields and attributes."

    def __init__(self) -> None:
        self.fields: List[str] = []
        self.tags: List[str] = []
        self.deck = None
        self.cards: Dict[int, ForeignCard] = {}  # map of ord -> card
        self.fieldsStr = ""


class ForeignCard:
    def __init__(self) -> None:
        self.due = 0
        self.ivl = 1
        self.factor = STARTING_FACTOR
        self.reps = 0
        self.lapses = 0


# Base class for CSV and similar text-based imports
######################################################################

# The mapping is list of input fields, like:
# ['Expression', 'Reading', '_tags', None]
# - None means that the input should be discarded
# - _tags maps to note tags
# If the first field of the model is not in the map, the map is invalid.

# The import mode is one of:
# UPDATE_MODE: update if first field matches existing note
# IGNORE_MODE: ignore if first field matches existing note
# ADD_MODE: import even if first field matches existing note
UPDATE_MODE = 0
IGNORE_MODE = 1
ADD_MODE = 2


class NoteImporter(Importer):

    needMapper = True
    needDelimiter = False
    allowHTML = False
    importMode = UPDATE_MODE
    mapping: Optional[List[str]]
    tagModified: Optional[str]

    def __init__(self, col: Collection, file: str) -> None:
        Importer.__init__(self, col, file)
        self.model = col.models.current()
        self.mapping = None
        self.tagModified = None
        self._tagsMapped = False

    def run(self) -> None:
        "Import."
        assert self.mapping
        c = self.foreignNotes()
        self.importNotes(c)

    def fields(self) -> int:
        "The number of fields."
        return 0

    def initMapping(self) -> None:
        flds = [f["name"] for f in self.model["flds"]]
        # truncate to provided count
        flds = flds[0 : self.fields()]
        # if there's room left, add tags
        if self.fields() > len(flds):
            flds.append("_tags")
        # and if there's still room left, pad
        flds = flds + [None] * (self.fields() - len(flds))
        self.mapping = flds

    def mappingOk(self) -> bool:
        return self.model["flds"][0]["name"] in self.mapping

    def foreignNotes(self) -> List:
        "Return a list of foreign notes for importing."
        return []

    def open(self) -> None:
        "Open file and ensure it's in the right format."
        return

    def close(self) -> None:
        "Closes the open file."
        return

    def importNotes(self, notes: List[ForeignNote]) -> None:
        "Convert each card into a note, apply attributes and add to col."
        assert self.mappingOk()
        # note whether tags are mapped
        self._tagsMapped = False
        for f in self.mapping:
            if f == "_tags":
                self._tagsMapped = True
        # gather checks for duplicate comparison
        csums: Dict[str, List[int]] = {}
        for csum, id in self.col.db.execute(
            "select csum, id from notes where mid = ?", self.model["id"]
        ):
            if csum in csums:
                csums[csum].append(id)
            else:
                csums[csum] = [id]
        firsts: Dict[str, bool] = {}
        fld0idx = self.mapping.index(self.model["flds"][0]["name"])
        self._fmap = self.col.models.fieldMap(self.model)
        self._nextID = timestampID(self.col.db, "notes")
        # loop through the notes
        updates = []
        updateLog = []
        new = []
        self._ids: List[int] = []
        self._cards: List[Tuple] = []
        dupeCount = 0
        dupes: List[str] = []
        for n in notes:
            for c in range(len(n.fields)):
                if not self.allowHTML:
                    n.fields[c] = html.escape(n.fields[c], quote=False)
                n.fields[c] = n.fields[c].strip()
                if not self.allowHTML:
                    n.fields[c] = n.fields[c].replace("\n", "<br>")
            fld0 = n.fields[fld0idx]
            csum = fieldChecksum(fld0)
            # first field must exist
            if not fld0:
                self.log.append(
                    self.col.tr(TR.IMPORTING_EMPTY_FIRST_FIELD, val=" ".join(n.fields))
                )
                continue
            # earlier in import?
            if fld0 in firsts and self.importMode != ADD_MODE:
                # duplicates in source file; log and ignore
                self.log.append(
                    self.col.tr(TR.IMPORTING_APPEARED_TWICE_IN_FILE, val=fld0)
                )
                continue
            firsts[fld0] = True
            # already exists?
            found = False
            if csum in csums:
                # csum is not a guarantee; have to check
                for id in csums[csum]:
                    flds = self.col.db.scalar("select flds from notes where id = ?", id)
                    sflds = splitFields(flds)
                    if fld0 == sflds[0]:
                        # duplicate
                        found = True
                        if self.importMode == UPDATE_MODE:
                            data = self.updateData(n, id, sflds)
                            if data:
                                updates.append(data)
                                updateLog.append(
                                    self.col.tr(
                                        TR.IMPORTING_FIRST_FIELD_MATCHED, val=fld0
                                    )
                                )
                                dupeCount += 1
                                found = True
                        elif self.importMode == IGNORE_MODE:
                            dupeCount += 1
                        elif self.importMode == ADD_MODE:
                            # allow duplicates in this case
                            if fld0 not in dupes:
                                # only show message once, no matter how many
                                # duplicates are in the collection already
                                updateLog.append(
                                    self.col.tr(
                                        TR.IMPORTING_ADDED_DUPLICATE_WITH_FIRST_FIELD,
                                        val=fld0,
                                    )
                                )
                                dupes.append(fld0)
                            found = False
            # newly add
            if not found:
                data = self.newData(n)
                if data:
                    new.append(data)
                    # note that we've seen this note once already
                    firsts[fld0] = True
        self.addNew(new)
        self.addUpdates(updates)
        # generate cards + update field cache
        self.col.after_note_updates(self._ids, mark_modified=False)
        # apply scheduling updates
        self.updateCards()
        # we randomize or order here, to ensure that siblings
        # have the same due#
        did = self.col.decks.selected()
        conf = self.col.decks.confForDid(did)
        # in order due?
        if conf["new"]["order"] == NEW_CARDS_RANDOM:
            self.col.sched.randomizeCards(did)

        part1 = self.col.tr(TR.IMPORTING_NOTE_ADDED, count=len(new))
        part2 = self.col.tr(TR.IMPORTING_NOTE_UPDATED, count=self.updateCount)
        if self.importMode == UPDATE_MODE:
            unchanged = dupeCount - self.updateCount
        elif self.importMode == IGNORE_MODE:
            unchanged = dupeCount
        else:
            unchanged = 0
        part3 = self.col.tr(TR.IMPORTING_NOTE_UNCHANGED, count=unchanged)
        self.log.append(f"{part1}, {part2}, {part3}.")
        self.log.extend(updateLog)
        self.total = len(self._ids)

    def newData(self, n: ForeignNote) -> Optional[list]:
        id = self._nextID
        self._nextID += 1
        self._ids.append(id)
        self.processFields(n)
        # note id for card updates later
        for ord, c in list(n.cards.items()):
            self._cards.append((id, ord, c))
        return [
            id,
            guid64(),
            self.model["id"],
            intTime(),
            self.col.usn(),
            self.col.tags.join(n.tags),
            n.fieldsStr,
            "",
            "",
            0,
            "",
        ]

    def addNew(self, rows: List[List[Union[int, str]]]) -> None:
        self.col.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)", rows
        )

    def updateData(self, n: ForeignNote, id: int, sflds: List[str]) -> Optional[list]:
        self._ids.append(id)
        self.processFields(n, sflds)
        if self._tagsMapped:
            tags = self.col.tags.join(n.tags)
            return [intTime(), self.col.usn(), n.fieldsStr, tags, id, n.fieldsStr, tags]
        elif self.tagModified:
            tags = self.col.db.scalar("select tags from notes where id = ?", id)
            tagList = self.col.tags.split(tags) + self.tagModified.split()
            tags = self.col.tags.join(tagList)
            return [intTime(), self.col.usn(), n.fieldsStr, tags, id, n.fieldsStr]
        else:
            return [intTime(), self.col.usn(), n.fieldsStr, id, n.fieldsStr]

    def addUpdates(self, rows: List[List[Union[int, str]]]) -> None:
        changes = self.col.db.scalar("select total_changes()")
        if self._tagsMapped:
            self.col.db.executemany(
                """
update notes set mod = ?, usn = ?, flds = ?, tags = ?
where id = ? and (flds != ? or tags != ?)""",
                rows,
            )
        elif self.tagModified:
            self.col.db.executemany(
                """
update notes set mod = ?, usn = ?, flds = ?, tags = ?
where id = ? and flds != ?""",
                rows,
            )
        else:
            self.col.db.executemany(
                """
update notes set mod = ?, usn = ?, flds = ?
where id = ? and flds != ?""",
                rows,
            )
        changes2 = self.col.db.scalar("select total_changes()")
        self.updateCount = changes2 - changes

    def processFields(
        self, note: ForeignNote, fields: Optional[List[str]] = None
    ) -> None:
        if not fields:
            fields = [""] * len(self.model["flds"])
        for c, f in enumerate(self.mapping):
            if not f:
                continue
            elif f == "_tags":
                note.tags.extend(self.col.tags.split(note.fields[c]))
            else:
                sidx = self._fmap[f][0]
                fields[sidx] = note.fields[c]
        note.fieldsStr = joinFields(fields)

    def updateCards(self) -> None:
        data = []
        for nid, ord, c in self._cards:
            data.append((c.ivl, c.due, c.factor, c.reps, c.lapses, nid, ord))
        # we assume any updated cards are reviews
        self.col.db.executemany(
            """
update cards set type = 2, queue = 2, ivl = ?, due = ?,
factor = ?, reps = ?, lapses = ? where nid = ? and ord = ?""",
            data,
        )
