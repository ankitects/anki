# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.errors import AnkiError
from anki.utils import stripHTMLMedia, fieldChecksum, intTime, \
    addTags, deleteTags, parseTags

class Fact(object):

    def __init__(self, deck, model=None, id=None):
        assert not (model and id)
        self.deck = deck
        if id:
            self.id = id
            self.load()
        else:
            self.id = None
            self.model = model
            self.mid = model.id
            self.crt = intTime()
            self.mod = self.crt
            self.tags = ""
            self.cache = ""
            self._fields = [""] * len(self.model.fields)
        self._fmap = self.model.fieldMap()

    def load(self):
        (self.mid,
         self.crt,
         self.mod) = self.deck.db.first("""
select mid, crt, mod from facts where id = ?""", self.id)
        self._fields = self.deck.db.list("""
select val from fdata where fid = ? and fmid order by ord""", self.id)
        self.tags = self.deck.db.scalar("""
select val from fdata where fid = ? and ord = -1""", self.id)
        self.model = self.deck.getModel(self.mid)

    def flush(self, cache=True):
        self.mod = intTime()
        # facts table
        self.cache = stripHTMLMedia(u" ".join(self._fields))
        res = self.deck.db.execute("""
insert or replace into facts values (?, ?, ?, ?, ?)""",
                             self.id, self.mid, self.crt,
                             self.mod, self.cache)
        self.id = res.lastrowid
        # fdata table
        self.deck.db.execute("delete from fdata where fid = ?", self.id)
        d = []
        for (fmid, ord, conf) in self._fmap.values():
            val = self._fields[ord]
            d.append(dict(fid=self.id, fmid=fmid, ord=ord,
                          val=val))
        d.append(dict(fid=self.id, fmid=0, ord=-1, val=self.tags))
        self.deck.db.executemany("""
insert into fdata values (:fid, :fmid, :ord, :val, '')""", d)
        # media and caches
        self.deck.updateCache([self.id], "fact")

    def cards(self):
        return [self.deck.getCard(id) for id in self.deck.db.list(
            "select id from cards where fid = ? order by ord", self.id)]

    # Dict interface
    ##################################################

    def keys(self):
        return self._fmap.keys()

    def values(self):
        return self._fields

    def items(self):
        return [(k, self._fields[v])
                for (k, v) in self._fmap.items()]

    def _fieldOrd(self, key):
        try:
            return self._fmap[key][1]
        except:
            raise KeyError(key)

    def __getitem__(self, key):
        return self._fields[self._fieldOrd(key)]

    def __setitem__(self, key, value):
        self._fields[self._fieldOrd(key)] = value

    def fieldsWithIds(self):
        return dict(
            [(k, (v[0], self[k])) for (k,v) in self._fmap.items()])

    # Tags
    ##################################################

    def addTags(self, tags):
        self.tags = addTags(tags, self.tags)

    def deleteTags(self, tags):
        self.tags = deleteTags(tags, self.tags)

    # Unique/duplicate checks
    ##################################################

    def fieldUnique(self, name):
        (fmid, ord, conf) = self._fmap[name]
        if not conf['unique']:
            return True
        val = self[name]
        csum = fieldChecksum(val)
        print "in check, ", self.id
        if self.id:
            lim = "and fid != :fid"
        else:
            lim = ""
        return not self.deck.db.scalar(
            "select 1 from fdata where csum = :c %s and val = :v" % lim,
            c=csum, v=val, fid=self.id)

    def fieldComplete(self, name, text=None):
        (fmid, ord, conf) = self._fmap[name]
        if not conf['required']:
            return True
        return self[name]

    def problems(self):
        d = []
        for k in self._fmap.keys():
            if not self.fieldUnique(k):
                d.append("unique")
            elif not self.fieldComplete(k):
                d.append("required")
            else:
                d.append(None)
        return d
