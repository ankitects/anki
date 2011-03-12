# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson
from anki.utils import intTime, hexifyID, joinFields, splitFields
from anki.lang import _

# Models
##########################################################################
# GUI code should ensure no two fields have the same name.

defaultConf = {
    'sortf': 0,
}

defaultField = {
    'name': "",
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
    'actv': True,
    'qfmt': "",
    'afmt': "",
    'hideQ': False,
    'align': 0,
    'bg': "#000",
    'emptyAns': None,
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
         self.conf) = self.deck.db.first("""
select mod, name, flds, tmpls, conf from models where id = ?""", self.id)
        self.fields = simplejson.loads(self.fields)
        self.templates = simplejson.loads(self.templates)
        self.conf = simplejson.loads(self.conf)

    def flush(self):
        self.mod = intTime()
        ret = self.deck.db.execute("""
insert or replace into models values (?, ?, ?, ?, ?, ?, ?)""",
                self.id, self.mod, self.name,
                simplejson.dumps(self.fields),
                simplejson.dumps(self.templates),
                simplejson.dumps(self.conf),
                self.genCSS())
        self.id = ret.lastrowid

    def fids(self):
        return self.deck.db.list("select id from facts where mid = ?", self.id)

    # Templates
    ##################################################

    def newTemplate(self):
        return defaultTemplate.copy()

    def addTemplate(self, template):
        self.deck.modSchema()
        self.templates.append(template)

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
            ".fm%s.%s" % (hexifyID(self.id), hexifyID(c)),
            (f['font'], f['qsize'], f['qcol'], f['rtl'], f['pre']))
            for c, f in enumerate(self.fields)])
        # templates
        css += "".join(["#cm%s-%s {text-align:%s;background:%s}\n" % (
            hexifyID(self.id), hexifyID(c),
            ("center", "left", "right")[t['align']], t['bg'])
                for c, t in enumerate(self.templates)])
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

    # Field basics
    ##################################################

    def fieldMap(self):
        "Mapping of field name -> (ord, conf)."
        return dict([(f['name'], (c, f)) for c, f in enumerate(self.fields)])

    def sortIdx(self):
        return self.conf['sortf']

    def setSortIdx(self, idx):
        assert idx > 0 and idx < len(self.fields)
        self.conf['sortf'] = idx
        self.deck.updateFieldCache(self.fids(), csum=False)

    # Adding/deleting/moving fields
    ##################################################

    def newField(self):
        return defaultField.copy()

    def addField(self, field):
        self.fields.append(field)
        self.flush()
        def add(fields):
            fields.append("")
            return fields
        self._transformFields(add)

    def deleteField(self, field):
        idx = self.fields.index(field)
        self.fields.remove(field)
        self.flush()
        def delete(fields):
            del fields[idx]
            return fields
        self._transformFields(delete)
        if idx == self.sortIdx():
            # need to rebuild
            self.deck.updateFieldCache(self.fids(), csum=False)
        self.renameField(field, None)

    def moveField(self, field, idx):
        oldidx = self.fields.index(field)
        if oldidx == idx:
            return
        self.fields.remove(field)
        self.fields.insert(idx, field)
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

    def _transformFields(self, fn):
        self.deck.startProgress()
        self.deck.modSchema()
        r = []
        for (id, flds) in self.deck.db.execute(
            "select id, flds from facts where mid = ?", self.id):
            r.append((joinFields(fn(splitFields(flds))), id))
        self.deck.db.executemany("update facts set flds = ? where id = ?", r)
        self.deck.finishProgress()
