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
        res = self.col.db.execute("""
insert or replace into notes values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                            self.id, self.guid, self.mid, self.did,
                            self.mod, self.usn, tags,
                            self.joinedFields(), sfld, self.flags, self.data)
        self.id = res.lastrowid
        self.updateFieldChecksums()
        self.col.tags.register(self.tags)
        if self.model()['cloze']:
            self._clozePostFlush()

    def joinedFields(self):
        return joinFields(self.fields)

    def updateFieldChecksums(self):
        self.col.db.execute("delete from nsums where nid = ?", self.id)
        d = []
        for (ord, conf) in self._fmap.values():
            if not conf['uniq']:
                continue
            val = self.fields[ord]
            if not val:
                continue
            d.append((self.id, self.mid, fieldChecksum(val)))
        self.col.db.executemany("insert into nsums values (?, ?, ?)", d)

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

    # Unique/duplicate checks
    ##################################################

    def fieldUnique(self, name):
        (ord, conf) = self._fmap[name]
        if not conf['uniq']:
            return True
        val = self[name]
        if not val:
            return True
        csum = fieldChecksum(val)
        if self.id:
            lim = "and nid != :nid"
        else:
            lim = ""
        nids = self.col.db.list(
            "select nid from nsums where csum = ? and nid != ? and mid = ?",
            csum, self.id or 0, self.mid)
        if not nids:
            return True
        # grab notes with the same checksums, and see if they're actually
        # duplicates
        for flds in self.col.db.list("select flds from notes where id in "+
                                      ids2str(nids)):
            fields = splitFields(flds)
            if fields[ord] == val:
                return False
        return True

    def fieldComplete(self, name, text=None):
        (ord, conf) = self._fmap[name]
        if not conf['req']:
            return True
        return self[name]

    def problems(self):
        d = []
        for (k, (ord, conf)) in self._fmap.items():
            if not self.fieldUnique(k):
                d.append((ord, "unique"))
            elif not self.fieldComplete(k):
                d.append((ord, "required"))
            else:
                d.append((ord, None))
        return [x[1] for x in sorted(d)]

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
