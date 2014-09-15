# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import  cgi
from anki.consts import NEW_CARDS_RANDOM
from anki.lang import _
from anki.utils import fieldChecksum, guid64, timestampID, \
    joinFields, intTime, splitFields
from anki.importing.base import Importer
from anki.lang import ngettext

# Stores a list of fields, tags and deck
######################################################################

class ForeignNote(object):
    "An temporary object storing fields and attributes."
    def __init__(self):
        self.fields = []
        self.tags = []
        self.deck = None
        self.cards = {} # map of ord -> card

class ForeignCard(object):
    def __init__(self):
        self.due = 0
        self.ivl = 1
        self.factor = 2500
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
# 0: update if first field matches existing note
# 1: ignore if first field matches existing note
# 2: import even if first field matches existing note

class NoteImporter(Importer):

    needMapper = True
    needDelimiter = False
    allowHTML = False
    importMode = 0

    def __init__(self, col, file):
        Importer.__init__(self, col, file)
        self.model = col.models.current()
        self.mapping = None
        self._deckMap = {}

    def run(self):
        "Import."
        assert self.mapping
        c = self.foreignNotes()
        self.importNotes(c)

    def fields(self):
        "The number of fields."
        return 0

    def initMapping(self):
        flds = [f['name'] for f in self.model['flds']]
        # truncate to provided count
        flds = flds[0:self.fields()]
        # if there's room left, add tags
        if self.fields() > len(flds):
            flds.append("_tags")
        # and if there's still room left, pad
        flds = flds + [None] * (self.fields() - len(flds))
        self.mapping = flds

    def mappingOk(self):
        return self.model['flds'][0]['name'] in self.mapping

    def foreignNotes(self):
        "Return a list of foreign notes for importing."
        assert 0

    def open(self):
        "Open file and ensure it's in the right format."
        return

    def importNotes(self, notes):
        "Convert each card into a note, apply attributes and add to col."
        assert self.mappingOk()
        # note whether tags are mapped
        self._tagsMapped = False
        for f in self.mapping:
            if f == "_tags":
                self._tagsMapped = True
        # gather checks for duplicate comparison
        csums = {}
        for csum, id in self.col.db.execute(
            "select csum, id from notes where mid = ?", self.model['id']):
            if csum in csums:
                csums[csum].append(id)
            else:
                csums[csum] = [id]
        firsts = {}
        fld0idx = self.mapping.index(self.model['flds'][0]['name'])
        self._fmap = self.col.models.fieldMap(self.model)
        self._nextID = timestampID(self.col.db, "notes")
        # loop through the notes
        updates = []
        updateLog = []
        updateLogTxt = _("First field matched: %s")
        dupeLogTxt = _("Added duplicate with first field: %s")
        new = []
        self._ids = []
        self._cards = []
        self._emptyNotes = False
        dupeCount = 0
        dupes = []
        for n in notes:
            for c in range(len(n.fields)):
                if not self.allowHTML:
                    n.fields[c] = cgi.escape(n.fields[c])
                n.fields[c] = n.fields[c].strip()
                if not self.allowHTML:
                    n.fields[c] = n.fields[c].replace("\n", "<br>")
            fld0 = n.fields[fld0idx]
            csum = fieldChecksum(fld0)
            # first field must exist
            if not fld0:
                self.log.append(_("Empty first field: %s") %
                                " ".join(n.fields))
                continue
            # earlier in import?
            if fld0 in firsts and self.importMode != 2:
                # duplicates in source file; log and ignore
                self.log.append(_("Appeared twice in file: %s") %
                                fld0)
                continue
            firsts[fld0] = True
            # already exists?
            found = False
            if csum in csums:
                # csum is not a guarantee; have to check
                for id in csums[csum]:
                    flds = self.col.db.scalar(
                        "select flds from notes where id = ?", id)
                    sflds = splitFields(flds)
                    if fld0 == sflds[0]:
                        # duplicate
                        found = True
                        if self.importMode == 0:
                            data = self.updateData(n, id, sflds)
                            if data:
                                updates.append(data)
                                updateLog.append(updateLogTxt % fld0)
                                dupeCount += 1
                                found = True
                        elif self.importMode == 1:
                            dupeCount += 1
                        elif self.importMode == 2:
                            # allow duplicates in this case
                            if fld0 not in dupes:
                                # only show message once, no matter how many
                                # duplicates are in the collection already
                                updateLog.append(dupeLogTxt % fld0)
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
        # make sure to update sflds, etc
        self.col.updateFieldCache(self._ids)
        # generate cards
        if self.col.genCards(self._ids):
            self.log.insert(0, _(
                "Empty cards found. Please run Tools>Empty Cards."))
        # apply scheduling updates
        self.updateCards()
        # we randomize or order here, to ensure that siblings
        # have the same due#
        did = self.col.decks.selected()
        conf = self.col.decks.confForDid(did)
        # in order due?
        if conf['new']['order'] == NEW_CARDS_RANDOM:
            self.col.sched.randomizeCards(did)
        else:
            self.col.sched.orderCards(did)

        part1 = ngettext("%d note added", "%d notes added", len(new)) % len(new)
        part2 = ngettext("%d note updated", "%d notes updated",
                         self.updateCount) % self.updateCount
        if self.importMode == 0:
            unchanged = dupeCount - self.updateCount
        elif self.importMode == 1:
            unchanged = dupeCount
        else:
            unchanged = 0
        part3 = ngettext("%d note unchanged", "%d notes unchanged",
                         unchanged) % unchanged
        self.log.append("%s, %s, %s." % (part1, part2, part3))
        self.log.extend(updateLog)
        if self._emptyNotes:
            self.log.append(_("""\
One or more notes were not imported, because they didn't generate any cards. \
This can happen when you have empty fields or when you have not mapped the \
content in the text file to the correct fields."""))
        self.total = len(self._ids)

    def newData(self, n):
        id = self._nextID
        self._nextID += 1
        self._ids.append(id)
        if not self.processFields(n):
            return
        # note id for card updates later
        for ord, c in n.cards.items():
            self._cards.append((id, ord, c))
        self.col.tags.register(n.tags)
        return [id, guid64(), self.model['id'],
                intTime(), self.col.usn(), self.col.tags.join(n.tags),
                n.fieldsStr, "", "", 0, ""]

    def addNew(self, rows):
        self.col.db.executemany(
            "insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)",
            rows)

    def updateData(self, n, id, sflds):
        self._ids.append(id)
        if not self.processFields(n, sflds):
            return
        if self._tagsMapped:
            self.col.tags.register(n.tags)
            tags = self.col.tags.join(n.tags)
            return [intTime(), self.col.usn(), n.fieldsStr, tags,
                    id, n.fieldsStr, tags]
        else:
            return [intTime(), self.col.usn(), n.fieldsStr,
                    id, n.fieldsStr]

    def addUpdates(self, rows):
        old = self.col.db.totalChanges()
        if self._tagsMapped:
            self.col.db.executemany("""
update notes set mod = ?, usn = ?, flds = ?, tags = ?
where id = ? and (flds != ? or tags != ?)""", rows)
        else:
            self.col.db.executemany("""
update notes set mod = ?, usn = ?, flds = ?
where id = ? and flds != ?""", rows)
        self.updateCount = self.col.db.totalChanges() - old

    def processFields(self, note, fields=None):
        if not fields:
            fields = [""]*len(self.model['flds'])
        for c, f in enumerate(self.mapping):
            if not f:
                continue
            elif f == "_tags":
                note.tags.extend(self.col.tags.split(note.fields[c]))
            else:
                sidx = self._fmap[f][0]
                fields[sidx] = note.fields[c]
        note.fieldsStr = joinFields(fields)
        ords = self.col.models.availOrds(self.model, note.fieldsStr)
        if not ords:
            self._emptyNotes = True
        return ords

    def updateCards(self):
        data = []
        for nid, ord, c in self._cards:
            data.append((c.ivl, c.due, c.factor, c.reps, c.lapses, nid, ord))
        # we assume any updated cards are reviews
        self.col.db.executemany("""
update cards set type = 2, queue = 2, ivl = ?, due = ?,
factor = ?, reps = ?, lapses = ? where nid = ? and ord = ?""", data)
