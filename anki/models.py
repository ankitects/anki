# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import simplejson
from anki.utils import intTime, hexifyID
from anki.lang import _

# Models
##########################################################################

defaultConf = {
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
         self.conf) = self.deck.db.first("""
select mod, name, flds, conf from models where id = ?""", self.id)
        self.fields = simplejson.loads(self.fields)
        self.conf = simplejson.loads(self.conf)
        self.loadTemplates()

    def flush(self):
        self.mod = intTime()
        ret = self.deck.db.execute("""
insert or replace into models values (?, ?, ?, ?, ?, ?)""",
                self.id, self.mod, self.name,
                simplejson.dumps(self.fields),
                simplejson.dumps(self.conf),
                self.genCSS())
        self.id = ret.lastrowid
        [t._flush() for t in self.templates]

    def _getID(self):
        if not self.id:
            # flush so we can get our DB id
            self.flush()
        return self.id

    # Fields
    ##################################################

    def newField(self):
        return defaultFieldConf.copy()

    def addField(self, field):
        self.deck.modSchema()
        self.fields.append(field)

    def fieldMap(self):
        "Mapping of field name -> (ord, conf)."
        return dict([(f['name'], (c, f)) for c, f in enumerate(self.fields)])

    def sortField(self):
        return 0

    # Templates
    ##################################################

    def loadTemplates(self):
        sql = "select * from templates where mid = ? order by ord"
        self.templates = [Template(self.deck, data)
                          for data in self.deck.db.all(sql, self.id)]

    def addTemplate(self, template):
        self.deck.modSchema()
        template.mid = self._getID()
        template.ord = len(self.templates)
        self.templates.append(template)

    # Copying
    ##################################################

    def copy(self):
        "Copy, flush and return."
        new = Model(self.deck, self.id)
        new.id = None
        new.name += _(" copy")
        new.fields = [f.copy() for f in self.fields]
        # get new id
        t = new.templates; new.templates = []
        new.flush()
        # then put back
        new.templates = t
        for t in new.templates:
            t.id = None
            t.mid = new.id
            t._flush()
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
        for t in self.templates:
            if not t.id:
                # not flushed yet, ignore for now
                continue
            css += "#cm%s {text-align:%s;background:%s}\n" % (
                hexifyID(t.id),
                ("center", "left", "right")[t.conf['align']],
                t.conf['bg'])
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

# Field object
##########################################################################

defaultFieldConf = {
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

# Template object
##########################################################################

defaultTemplateConf = {
    'hideQ': False,
    'align': 0,
    'bg': "#000",
    'allowEmptyAns': None,
    'typeAnswer': None,
}

class Template(object):

    def __init__(self, deck, data=None):
        self.deck = deck
        if data:
            self.initFromData(data)
        else:
            self.id = None
            self.active = True
            self.conf = defaultTemplateConf.copy()

    def initFromData(self, data):
        (self.id,
         self.mid,
         self.ord,
         self.name,
         self.active,
         self.qfmt,
         self.afmt,
         self.conf) = data
        self.conf = simplejson.loads(self.conf)

    def _flush(self):
        ret = self.deck.db.execute("""
insert or replace into templates values (?, ?, ?, ?, ?, ?, ?, ?)""",
                             self.id, self.mid, self.ord, self.name,
                             self.active, self.qfmt, self.afmt,
                             simplejson.dumps(self.conf))
        self.id = ret.lastrowid
