# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from anki.errors import AnkiError
from anki.utils import fieldChecksum, intTime, \
    joinFields, splitFields, ids2str, parseTags, canonifyTags, hasTag, \
    stripHTML, timestampID

class Fact(object):

    def __init__(self, deck, model=None, id=None):
        assert not (model and id)
        self.deck = deck
        if id:
            self.id = id
            self.load()
        else:
            self.id = timestampID(deck.db, "facts")
            self._model = model
            self.gid = model.conf['gid']
            self.mid = model.id
            self.tags = []
            self.fields = [""] * len(self._model.fields)
            self.data = ""
            self._fmap = self._model.fieldMap()

    def load(self):
        (self.mid,
         self.gid,
         self.mod,
         self.tags,
         self.fields,
         self.data) = self.deck.db.first("""
select mid, gid, mod, tags, flds, data from facts where id = ?""", self.id)
        self.fields = splitFields(self.fields)
        self.tags = parseTags(self.tags)
        self._model = self.deck.getModel(self.mid)
        self._fmap = self._model.fieldMap()

    def flush(self):
        self.mod = intTime()
        sfld = stripHTML(self.fields[self._model.sortIdx()])
        tags = self.stringTags()
        res = self.deck.db.execute("""
insert or replace into facts values (?, ?, ?, ?, ?, ?, ?, ?)""",
                            self.id, self.mid, self.gid,
                            self.mod, tags, self.joinedFields(),
                            sfld, self.data)
        self.id = res.lastrowid
        self.updateFieldChecksums()
        self.deck.registerTags(self.tags)

    def joinedFields(self):
        return joinFields(self.fields)

    def updateFieldChecksums(self):
        self.deck.db.execute("delete from fsums where fid = ?", self.id)
        d = []
        for (ord, conf) in self._fmap.values():
            if not conf['uniq']:
                continue
            val = self.fields[ord]
            if not val:
                continue
            d.append((self.id, self.mid, fieldChecksum(val)))
        self.deck.db.executemany("insert into fsums values (?, ?, ?)", d)

    def cards(self):
        return [self.deck.getCard(id) for id in self.deck.db.list(
            "select id from cards where fid = ? order by ord", self.id)]

    def model(self):
        return self._model

    def updateCardGids(self):
        for c in self.cards():
            if c.gid != self.gid and not c.template()['gid']:
                c.gid = self.gid
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
        return hasTag(tag, self.tags)

    def stringTags(self):
        return canonifyTags(self.tags)

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
