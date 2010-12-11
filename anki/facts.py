# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Facts
========
"""
__docformat__ = 'restructuredtext'

import time
from anki.db import *
from anki.errors import *
from anki.models import Model, FieldModel, fieldModelsTable
from anki.utils import genID, stripHTMLMedia
from anki.hooks import runHook

# Fields in a fact
##########################################################################

fieldsTable = Table(
    'fields', metadata,
    Column('id', Integer, primary_key=True),
    Column('factId', Integer, ForeignKey("facts.id"), nullable=False),
    Column('fieldModelId', Integer, ForeignKey("fieldModels.id"),
           nullable=False),
    Column('ordinal', Integer, nullable=False),
    Column('value', UnicodeText, nullable=False))

class Field(object):
    "A field in a fact."

    def __init__(self, fieldModel=None):
        if fieldModel:
            self.fieldModel = fieldModel
            self.ordinal = fieldModel.ordinal
        self.value = u""
        self.id = genID()

    def getName(self):
        return self.fieldModel.name
    name = property(getName)

mapper(Field, fieldsTable, properties={
    'fieldModel': relation(FieldModel)
    })

# Facts: a set of fields and a model
##########################################################################
# mapped in cards.py

factsTable = Table(
    'facts', metadata,
    Column('id', Integer, primary_key=True),
    Column('modelId', Integer, ForeignKey("models.id"), nullable=False),
    Column('created', Float, nullable=False, default=time.time),
    Column('modified', Float, nullable=False, default=time.time),
    Column('tags', UnicodeText, nullable=False, default=u""),
    # spaceUntil is reused as a html-stripped cache of the fields
    Column('spaceUntil', UnicodeText, nullable=False, default=u""),
    # obsolete
    Column('lastCardId', Integer, ForeignKey(
    "cards.id", use_alter=True, name="lastCardIdfk")))

class Fact(object):
    "A single fact. Fields exposed as dict interface."

    def __init__(self, model=None):
        self.model = model
        self.id = genID()
        if model:
            for fm in model.fieldModels:
                self.fields.append(Field(fm))
        self.new = True

    def isNew(self):
        return getattr(self, 'new', False)

    def keys(self):
        return [field.name for field in self.fields]

    def values(self):
        return [field.value for field in self.fields]

    def __getitem__(self, key):
        try:
            return [f.value for f in self.fields if f.name == key][0]
        except IndexError:
            raise KeyError(key)

    def __setitem__(self, key, value):
        try:
            [f for f in self.fields if f.name == key][0].value = value
        except IndexError:
            raise KeyError

    def get(self, key, default):
        try:
            return self[key]
        except (IndexError, KeyError):
            return default

    def assertValid(self):
        "Raise an error if required fields are empty."
        for field in self.fields:
            if not self.fieldValid(field):
                raise FactInvalidError(type="fieldEmpty",
                                       field=field.name)

    def fieldValid(self, field):
        return not (field.fieldModel.required and not field.value.strip())

    def assertUnique(self, s):
        "Raise an error if duplicate fields are found."
        for field in self.fields:
            if not self.fieldUnique(field, s):
                raise FactInvalidError(type="fieldNotUnique",
                                       field=field.name)

    def fieldUnique(self, field, s):
        if not field.fieldModel.unique:
            return True
        req = ("select value from fields "
               "where fieldModelId = :fmid and value = :val")
        if field.id:
            req += " and id != %s" % field.id
        return not s.scalar(req, val=field.value, fmid=field.fieldModel.id)

    def focusLost(self, field):
        runHook('fact.focusLost', self, field)

    def setModified(self, textChanged=False, deck=None, media=True):
        "Mark modified and update cards."
        self.modified = time.time()
        if textChanged:
            if not deck:
                # FIXME: compat code
                import ankiqt
                if not getattr(ankiqt, 'setModWarningShown', None):
                    import sys; sys.stderr.write(
                        "plugin needs to pass deck to fact.setModified()")
                    ankiqt.setModWarningShown = True
                deck = ankiqt.mw.deck
            assert deck
            self.spaceUntil = stripHTMLMedia(u" ".join(
                self.values()))
            for card in self.cards:
                card.rebuildQA(deck)

# Fact deletions
##########################################################################

factsDeletedTable = Table(
    'factsDeleted', metadata,
    Column('factId', Integer, ForeignKey("facts.id"),
           nullable=False),
    Column('deletedTime', Float, nullable=False))
