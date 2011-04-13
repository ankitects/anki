# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from anki.utils import intTime, hexifyID, joinFields, splitFields, ids2str
from anki.lang import _

# Models
##########################################################################
# gid may point to non-existent group

defaultConf = {
    'sortf': 0,
    'gid': 1,
}

defaultField = {
    'name': "",
    'ord': None,
    'rtl': False,
    'req': False,
    'uniq': False,
    'font': "Arial",
    'qsize': 20,
    'esize': 20,
    'qcol': "#fff",
    'pre': True,
}

defaultTemplate = {
    'name': "",
    'ord': None,
    'actv': True,
    'qfmt': "",
    'afmt': "",
    'hideQ': False,
    'align': 0,
    'bg': "#000",
    'emptyAns': True,
    'typeAns': None,
    'gid': None
}

class Model(object):

    def __init__(self, deck, id=None):
        self.deck = deck
        if id:
            self.id = id
            self.load()
        else:
            self.id = None
            self.name = u""
            self.mod = intTime()
            self.conf = defaultConf.copy()
            self.css = ""
            self.fields = []
            self.templates = []

    def load(self):
        (self.mod,
         self.name,
         self.fields,
         self.templates,
         self.conf,
         self.css) = self.deck.db.first("""
select mod, name, flds, tmpls, conf, css from models where id = ?""", self.id)
        self.fields = simplejson.loads(self.fields)
        self.templates = simplejson.loads(self.templates)
        self.conf = simplejson.loads(self.conf)

    def flush(self):
        self.mod = intTime()
        self.css = self.genCSS()
        ret = self.deck.db.execute("""
insert or replace into models values (?, ?, ?, ?, ?, ?, ?)""",
                self.id, self.mod, self.name,
                simplejson.dumps(self.fields),
                simplejson.dumps(self.templates),
                simplejson.dumps(self.conf),
                self.css)
        self.id = ret.lastrowid

    def fids(self):
        return self.deck.db.list(
            "select id from facts where mid = ?", self.id)

    def useCount(self):
        return self.deck.db.scalar(
            "select count() from facts where mid = ?", self.id)

    # Copying
    ##################################################

    def copy(self):
        "Copy, flush and return."
        new = Model(self.deck, self.id)
        new.id = None
        new.name += _(" copy")
        new.fields = [f.copy() for f in self.fields]
        new.templates = [t.copy() for t in self.templates]
        new.flush()
        return new

    # CSS generation
    ##################################################

    def genCSS(self):
        if not self.id:
            return ""
        # fields
        css = "".join(self._fieldCSS(
            ".fm%s-%s" % (hexifyID(self.id), hexifyID(f['ord'])),
            (f['font'], f['qsize'], f['qcol'], f['rtl'], f['pre']))
            for f in self.fields)
        # templates
        css += "".join(".cm%s-%s {text-align:%s;background:%s}\n" % (
            hexifyID(self.id), hexifyID(t['ord']),
            ("center", "left", "right")[t['align']], t['bg'])
                for t in self.templates)
        return css

    def _rewriteFont(self, font):
        "Convert a platform font to a multiplatform list."
        font = font.lower()
        for family in self.deck.conf['fontFamilies']:
            for font2 in family:
                if font == font2.lower():
                    return ",".join(family)
        return font

    def _fieldCSS(self, prefix, row):
        (fam, siz, col, rtl, pre) = row
        t = 'font-family:"%s";' % self._rewriteFont(fam)
        t += 'font-size:%dpx;' % siz
        t += 'color:%s;' % col
        if rtl:
            t += "direction:rtl;unicode-bidi:embed;"
        if pre:
            t += "white-space:pre-wrap;"
        t = "%s {%s}\n" % (prefix, t)
        return t

    # Fields
    ##################################################

    def fieldMap(self):
        "Mapping of field name -> (ord, field)."
        return dict((f['name'], (f['ord'], f)) for f in self.fields)

    def sortIdx(self):
        return self.conf['sortf']

    def setSortIdx(self, idx):
        assert idx >= 0 and idx < len(self.fields)
        self.deck.modSchema()
        self.conf['sortf'] = idx
        self.deck.updateFieldCache(self.fids(), csum=False)
        self.flush()

    def newField(self):
        return defaultField.copy()

    def addField(self, field):
        self.fields.append(field)
        self._updateFieldOrds()
        self.flush()
        def add(fields):
            fields.append("")
            return fields
        self._transformFields(add)

    def delField(self, field):
        idx = self.fields.index(field)
        self.fields.remove(field)
        self._updateFieldOrds()
        def delete(fields):
            del fields[idx]
            return fields
        self._transformFields(delete)
        if idx == self.sortIdx():
            # need to rebuild
            self.deck.updateFieldCache(self.fids(), csum=False)
        # flushes
        self.renameField(field, None)

    def moveField(self, field, idx):
        oldidx = self.fields.index(field)
        if oldidx == idx:
            return
        self.fields.remove(field)
        self.fields.insert(idx, field)
        self._updateFieldOrds()
        self.flush()
        def move(fields, oldidx=oldidx):
            val = fields[oldidx]
            del fields[oldidx]
            fields.insert(idx, val)
            return fields
        self._transformFields(move)

    def renameField(self, field, newName):
        self.deck.modSchema()
        for t in self.templates:
            types = ("{{%s}}", "{{text:%s}}", "{{#%s}}",
                     "{{^%s}}", "{{/%s}}")
            for type in types:
                for fmt in ('qfmt', 'afmt'):
                    if newName:
                        repl = type%newName
                    else:
                        repl = ""
                    t[fmt] = t[fmt].replace(type%field['name'], repl)
        field['name'] = newName
        self.flush()

    def _updateFieldOrds(self):
        for c, f in enumerate(self.fields):
            f['ord'] = c

    def _transformFields(self, fn):
        self.deck.modSchema()
        r = []
        for (id, flds) in self.deck.db.execute(
            "select id, flds from facts where mid = ?", self.id):
            r.append((joinFields(fn(splitFields(flds))), id))
        self.deck.db.executemany("update facts set flds = ? where id = ?", r)

    # Templates
    ##################################################

    def newTemplate(self):
        return defaultTemplate.copy()

    def addTemplate(self, template):
        self.deck.modSchema()
        self.templates.append(template)
        self._updateTemplOrds()
        self.flush()

    def delTemplate(self, template):
        self.deck.modSchema()
        ord = self.templates.index(template)
        cids = self.deck.db.list("""
select c.id from cards c, facts f where c.fid=f.id and mid = ? and ord = ?""",
                                 self.id, ord)
        self.deck.delCards(cids)
        # shift ordinals
        self.deck.db.execute("""
update cards set ord = ord - 1 where fid in (select id from facts
where mid = ?) and ord > ?""", self.id, ord)
        self.templates.remove(template)
        self._updateTemplOrds()
        self.flush()

    def _updateTemplOrds(self):
        for c, t in enumerate(self.templates):
            t['ord'] = c

    def moveTemplate(self, template, idx):
        oldidx = self.templates.index(template)
        if oldidx == idx:
            return
        oldidxs = dict((id(t), t['ord']) for t in self.templates)
        self.templates.remove(template)
        self.templates.insert(idx, template)
        self._updateTemplOrds()
        # generate change map
        map = []
        for t in self.templates:
            map.append("when ord = %d then %d" % (oldidxs[id(t)], t['ord']))
        # apply
        self.flush()
        self.deck.db.execute("""
update cards set ord = (case %s end) where fid in (
select id from facts where mid = ?)""" % " ".join(map), self.id)

    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing

    def changeModel(self, fids, newModel, fmap, cmap):
        self.deck.modSchema()
        assert newModel.id == self.id or (fmap and cmap)
        if fmap:
            self._changeFacts(fids, newModel, fmap)
        if cmap:
            self._changeCards(fids, newModel, cmap)

    def _changeFacts(self, fids, newModel, map):
        d = []
        nfields = len(newModel.fields)
        for (fid, flds) in self.deck.db.execute(
            "select id, flds from facts where id in "+ids2str(fids)):
            newflds = {}
            flds = splitFields(flds)
            for old, new in map.items():
                newflds[new] = flds[old]
            flds = []
            for c in range(nfields):
                flds.append(newflds.get(c, ""))
            flds = joinFields(flds)
            d.append(dict(fid=fid, flds=flds, mid=newModel.id))
        self.deck.db.executemany(
            "update facts set flds=:flds, mid=:mid where id = :fid", d)
        self.deck.updateFieldCache(fids)

    def _changeCards(self, fids, newModel, map):
        d = []
        deleted = []
        for (cid, ord) in self.deck.db.execute(
            "select id, ord from cards where fid in "+ids2str(fids)):
            if map[ord] is not None:
                d.append(dict(cid=cid, new=map[ord]))
            else:
                deleted.append(cid)
        self.deck.db.executemany(
            "update cards set ord=:new where id=:cid", d)
        self.deck.delCards(deleted)
