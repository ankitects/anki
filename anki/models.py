# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson
from anki.utils import intTime, hexifyID, joinFields, splitFields
from anki.lang import _

# Models
##########################################################################

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
        css = "".join([self._fieldCSS(
            ".fm%s-%s" % (hexifyID(self.id), hexifyID(f['ord'])),
            (f['font'], f['qsize'], f['qcol'], f['rtl'], f['pre']))
            for f in self.fields])
        # templates
        css += "".join([".cm%s-%s {text-align:%s;background:%s}\n" % (
            hexifyID(self.id), hexifyID(t['ord']),
            ("center", "left", "right")[t['align']], t['bg'])
                for t in self.templates])
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
        return dict([(f['name'], (f['ord'], f)) for f in self.fields])

    def sortIdx(self):
        return self.conf['sortf']

    def setSortIdx(self, idx):
        assert idx > 0 and idx < len(self.fields)
        self.conf['sortf'] = idx
        self.deck.updateFieldCache(self.fids(), csum=False)

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
        oldidxs = dict([(id(t), t['ord']) for t in self.templates])
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

    def changeModel(self, fids, newModel, fieldMap, cardMap):
        raise Exception()
        self.modSchema()
        sfids = ids2str(fids)
        # field remapping
        if fieldMap:
            seen = {}
            for (old, new) in fieldMap.items():
                seen[new] = 1
                if new:
                    # can rename
                    self.db.execute("""
update fdata set
fmid = :new,
ord = :ord
where fmid = :old
and fid in %s""" % sfids, new=new.id, ord=new.ord, old=old.id)
                else:
                    # no longer used
                    self.db.execute("""
delete from fdata where fid in %s
and fmid = :id""" % sfids, id=old.id)
            # new
            for field in newModel.fields:
                if field not in seen:
                    d = [{'fid': f,
                          'fmid': field.id,
                          'ord': field.ord}
                         for f in fids]
                    self.db.executemany('''
insert into fdata
(fid, fmid, ord, value)
values
(:fid, :fmid, :ord, "")''', d)
            # fact modtime
            self.db.execute("""
update facts set
mod = :t,
mid = :id
where id in %s""" % sfids, t=time.time(), id=newModel.id)
        # template remapping
        toChange = []
        for (old, new) in cardMap.items():
            if not new:
                # delete
                self.db.execute("""
delete from cards
where tid = :cid and
fid in %s""" % sfids, cid=old.id)
            elif old != new:
                # gather ids so we can rename x->y and y->x
                ids = self.db.list("""
select id from cards where
tid = :id and fid in %s""" % sfids, id=old.id)
                toChange.append((new, ids))
        for (new, ids) in toChange:
            self.db.execute("""
update cards set
tid = :new,
ord = :ord
where id in %s""" % ids2str(ids), new=new.id, ord=new.ord)
        cardIds = self.db.list(
            "select id from cards where fid in %s" %
            ids2str(fids))
