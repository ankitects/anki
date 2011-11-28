# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from anki.errors import AnkiError
from anki.utils import fieldChecksum, intTime, \
    joinFields, splitFields, ids2str, stripHTML, timestampID, guid64

class Note(object):

    def __init__(self, col, model=None, id=None):
        assert not (model and id)
        self.col = col
        if id:
            self.id = id
            self.load()
        else:
            self.id = timestampID(col.db, "notes")
            self.guid = guid64()
            self._model = model
            self.did = model['did']
            self.mid = model['id']
            self.tags = []
            self.fields = [""] * len(self._model['flds'])
            self.flags = 0
            self.data = ""
            self._fmap = self.col.models.fieldMap(self._model)

    def load(self):
        (self.guid,
         self.mid,
         self.did,
         self.mod,
         self.usn,
         self.tags,
         self.fields,
         self.flags,
         self.data) = self.col.db.first("""
select guid, mid, did, mod, usn, tags, flds, flags, data
from notes where id = ?""", self.id)
        self.fields = splitFields(self.fields)
        self.tags = self.col.tags.split(self.tags)
        self._model = self.col.models.get(self.mid)
        self._fmap = self.col.models.fieldMap(self._model)

    def flush(self, mod=None):
        if self.model()['cloze']:
            self._clozePreFlush()
        self.mod = mod if mod else intTime()
        self.usn = self.col.usn()
        sfld = stripHTML(self.fields[self.col.models.sortIdx(self._model)])
        tags = self.stringTags()
        csum = fieldChecksum(self.fields[0])
        res = self.col.db.execute("""
insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?,?)""",
                            self.id, self.guid, self.mid, self.did,
                            self.mod, self.usn, tags,
                            self.joinedFields(), sfld, csum, self.flags,
                            self.data)
        self.id = res.lastrowid
        self.col.tags.register(self.tags)
        if self.model()['cloze']:
            self._clozePostFlush()

    def joinedFields(self):
        return joinFields(self.fields)

    def cards(self):
        return [self.col.getCard(id) for id in self.col.db.list(
            "select id from cards where nid = ? order by ord", self.id)]

    def model(self):
        return self._model

    def updateCardDids(self):
        for c in self.cards():
            if c.did != self.did and not c.template()['did']:
                c.did = self.did
                c.flush()

    # Dict interface
    ##################################################

    def keys(self):
        return self._fmap.keys()

    def values(self):
        return self.fields

    def items(self):
        return [(f['name'], self.fields[ord])
                for ord, f in sorted(self._fmap.values())]

    def _fieldOrd(self, key):
        try:
            return self._fmap[key][0]
        except:
            raise KeyError(key)

    def __getitem__(self, key):
        return self.fields[self._fieldOrd(key)]

    def __setitem__(self, key, value):
        self.fields[self._fieldOrd(key)] = value

    # Tags
    ##################################################

    def hasTag(self, tag):
        return self.col.tags.inList(tag, self.tags)

    def stringTags(self):
        return self.col.tags.join(self.col.tags.canonify(self.tags))

    def setTagsFromStr(self, str):
        self.tags = self.col.tags.split(str)

    def delTag(self, tag):
        rem = []
        for t in self.tags:
            if t.lower() == tag.lower():
                rem.append(t)
        for r in rem:
            self.tags.remove(r)

    def addTag(self, tag):
        # duplicates will be stripped on save
        self.tags.append(tag)

    # Unique/duplicate check
    ##################################################

    def dupeOrEmpty(self):
        "1 if first is empty; 2 if first is a duplicate, False otherwise."
        val = self.fields[0]
        if not val.strip():
            return 1
        csum = fieldChecksum(val)
        # find any matching csums and compare
        for flds in self.col.db.list(
            "select flds from notes where csum = ? and id != ? and mid = ?",
            csum, self.id or 0, self.mid):
            if splitFields(flds)[0] == self.fields[0]:
                return 2
        return False

    # Flushing cloze notes
    ##################################################

    def _clozePreFlush(self):
        self.newlyAdded = not self.col.db.scalar(
            "select 1 from cards where nid = ?", self.id)
        tmpls = self.col.findTemplates(self)
        ok = []
        for t in tmpls:
            ok.append(t['ord'])
        # check if there are cards referencing a deleted cloze
        if self.col.db.scalar(
            "select 1 from cards where nid = ? and ord not in %s" %
            ids2str(ok), self.id):
            # there are; abort, as the UI should have handled this
            raise Exception("UI should have deleted cloze")

    def _clozePostFlush(self):
        # generate missing cards
        if not self.newlyAdded:
            self.col.genCards([self.id])
