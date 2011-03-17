# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.errors import AnkiError
from anki.utils import stripHTMLMedia, fieldChecksum, intTime, \
    joinFields, splitFields, ids2str, parseTags, joinTags, hasTag

class Fact(object):

    def __init__(self, deck, model=None, id=None):
        assert not (model and id)
        self.deck = deck
        if id:
            self.id = id
            self.load()
        else:
            self.id = None
            self._model = model
            self.gid = 1
            self.mid = model.id
            self.crt = intTime()
            self.mod = self.crt
            self.tags = []
            self._fields = [""] * len(self._model.fields)
            self.data = ""
        self._fmap = self._model.fieldMap()

    def load(self):
        (self.mid,
         self.gid,
         self.crt,
         self.mod,
         self.tags,
         self._fields,
         self.data) = self.deck.db.first("""
select mid, gid, crt, mod, tags, flds, data from facts where id = ?""", self.id)
        self._fields = splitFields(self._fields)
        self.tags = parseTags(self.tags)
        self._model = self.deck.getModel(self.mid)

    def flush(self):
        self.mod = intTime()
        # facts table
        sfld = self._fields[self._model.sortIdx()]
        tags = joinTags(self.tags)
        res = self.deck.db.execute("""
insert or replace into facts values (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                            self.id, self.mid, self.gid, self.crt,
                            self.mod, tags, self.joinedFields(),
                            sfld, self.data)
        self.id = res.lastrowid
        self.updateFieldChecksums()
        self.deck.registerTags(self.tags)

    def joinedFields(self):
        return joinFields(self._fields)

    def updateFieldChecksums(self):
        self.deck.db.execute("delete from fsums where fid = ?", self.id)
        d = []
        for (ord, conf) in self._fmap.values():
            if not conf['uniq']:
                continue
            val = self._fields[ord]
            if not val:
                continue
            d.append((self.id, self.mid, fieldChecksum(val)))
        self.deck.db.executemany("insert into fsums values (?, ?, ?)", d)

    def cards(self):
        return [self.deck.getCard(id) for id in self.deck.db.list(
            "select id from cards where fid = ? order by id", self.id)]

    def model(self):
        return self._model

    # Dict interface
    ##################################################

    def keys(self):
        return self._fmap.keys()

    def values(self):
        return self._fields

    def items(self):
        return [(k, self._fields[v[0]])
                for (k, v) in self._fmap.items()]

    def _fieldOrd(self, key):
        try:
            return self._fmap[key][0]
        except:
            raise KeyError(key)

    def __getitem__(self, key):
        return self._fields[self._fieldOrd(key)]

    def __setitem__(self, key, value):
        self._fields[self._fieldOrd(key)] = value

    # Tags
    ##################################################

    def hasTag(self, tag):
        return hasTag(tag, self.tags)

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
            lim = "and fid != :fid"
        else:
            lim = ""
        fids = self.deck.db.list(
            "select fid from fsums where csum = ? and fid != ? and mid = ?",
            csum, self.id or 0, self.mid)
        if not fids:
            return True
        # grab facts with the same checksums, and see if they're actually
        # duplicates
        for flds in self.deck.db.list("select flds from facts where id in "+
                                      ids2str(fids)):
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
