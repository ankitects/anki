# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import fieldChecksum, intTime, \
    joinFields, splitFields, stripHTMLMedia, timestampID, guid64

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
            self.mid = model['id']
            self.tags = []
            self.fields = [""] * len(self._model['flds'])
            self.flags = 0
            self.data = ""
            self._fmap = self.col.models.fieldMap(self._model)
            self.scm = self.col.scm

    def load(self):
        (self.guid,
         self.mid,
         self.mod,
         self.usn,
         self.tags,
         self.fields,
         self.flags,
         self.data) = self.col.db.first("""
select guid, mid, mod, usn, tags, flds, flags, data
from notes where id = ?""", self.id)
        self.fields = splitFields(self.fields)
        self.tags = self.col.tags.split(self.tags)
        self._model = self.col.models.get(self.mid)
        self._fmap = self.col.models.fieldMap(self._model)
        self.scm = self.col.scm

    def flush(self, mod=None):
        "If fields or tags have changed, write changes to disk."
        assert self.scm == self.col.scm
        self._preFlush()
        sfld = stripHTMLMedia(self.fields[self.col.models.sortIdx(self._model)])
        tags = self.stringTags()
        fields = self.joinedFields()
        if not mod and self.col.db.scalar(
            "select 1 from notes where id = ? and tags = ? and flds = ?",
            self.id, tags, fields):
            return
        csum = fieldChecksum(self.fields[0])
        self.mod = mod if mod else intTime()
        self.usn = self.col.usn()
        res = self.col.db.execute("""
insert or replace into notes values (?,?,?,?,?,?,?,?,?,?,?)""",
                            self.id, self.guid, self.mid,
                            self.mod, self.usn, tags,
                            fields, sfld, csum, self.flags,
                            self.data)
        self.col.tags.register(self.tags)
        self._postFlush()

    def joinedFields(self):
        return joinFields(self.fields)

    def cards(self):
        return [self.col.getCard(id) for id in self.col.db.list(
            "select id from cards where nid = ? order by ord", self.id)]

    def model(self):
        return self._model

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

    def __contains__(self, key):
        return key in self._fmap.keys()

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
            if stripHTMLMedia(
                splitFields(flds)[0]) == stripHTMLMedia(self.fields[0]):
                return 2
        return False

    # Flushing cloze notes
    ##################################################

    def _preFlush(self):
        # have we been added yet?
        self.newlyAdded = not self.col.db.scalar(
            "select 1 from cards where nid = ?", self.id)

    def _postFlush(self):
        # generate missing cards
        if not self.newlyAdded:
            rem = self.col.genCards([self.id])
            # popping up a dialog while editing is confusing; instead we can
            # document that the user should open the templates window to
            # garbage collect empty cards
            #self.col.remEmptyCards(ids)
