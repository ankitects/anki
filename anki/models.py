# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Models load their templates and fields when they are loaded. If you update a
template or field, you should call model.flush(), rather than trying to save
the subobject directly.
"""

import time, re, simplejson, copy as copyMod
from anki.utils import genID, canonifyTags, intTime
from anki.fonts import toPlatformFont
from anki.utils import parseTags, hexifyID, checksum, stripHTML, intTime
from anki.lang import _
from anki.hooks import runFilter
from anki.template import render
from copy import copy

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
            self.fields = []
            self.templates = []

    def load(self):
        (self.mod,
         self.name,
         self.conf) = self.deck.db.first("""
select mod, name, conf from models where id = ?""", self.id)
        self.conf = simplejson.loads(self.conf)
        self.loadFields()
        self.loadTemplates()

    def flush(self):
        self.mod = intTime()
        ret = self.deck.db.execute("""
insert or replace into models values (?, ?, ?, ?)""",
                             self.id, self.mod, self.name,
                             simplejson.dumps(self.conf))
        self.id = ret.lastrowid
        [f._flush() for f in self.fields]
        [t._flush() for t in self.templates]

    def updateCache(self):
        self.deck.updateCache([self.id], "model")

    def _getID(self):
        if not self.id:
            # flush so we can get our DB id
            self.flush()
        return self.id

    # Fields
    ##################################################

    def loadFields(self):
        sql = "select * from fields where mid = ? order by ord"
        self.fields = [Field(self.deck, data)
                       for data in self.deck.db.all(sql, self.id)]

    def addField(self, field):
        self.deck.modSchema()
        field.mid = self._getID()
        field.ord = len(self.fields)
        self.fields.append(field)

    def fieldMap(self):
        "Mapping of field name -> (fmid, ord)."
        return dict([(f.name, (f.id, f.ord, f.conf)) for f in self.fields])

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
        new.id = genID()
        new.name += _(" copy")
        for f in new.fields:
            f.id = genID()
            f.mid = new.id
        for t in new.templates:
            t.id = genID()
            t.mid = new.id
        new.flush()
        return new

# Field model object
##########################################################################

defaultFieldConf = {
    'rtl': False, # features
    'required': False,
    'unique': False,
    'font': "Arial",
    'quizSize': 20,
    'editSize': 20,
    'quizColour': "#fff",
    'pre': True,
}

class Field(object):

    def __init__(self, deck, data=None):
        self.deck = deck
        if data:
            self.initFromData(data)
        else:
            self.id = None
            self.numeric = 0
            self.conf = defaultFieldConf.copy()

    def initFromData(self, data):
        (self.id,
         self.mid,
         self.ord,
         self.name,
         self.numeric,
         self.conf) = data
        self.conf = simplejson.loads(self.conf)

    def _flush(self):
        ret = self.deck.db.execute("""
insert or replace into fields values (?, ?, ?, ?, ?, ?)""",
                             self.id, self.mid, self.ord,
                             self.name, self.numeric,
                             simplejson.dumps(self.conf))
        self.id = ret.lastrowid

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
            self.id = genID()
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
