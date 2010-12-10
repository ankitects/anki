# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Model - define the way in which facts are added and shown
==========================================================

- Field models
- Card models
- Models

"""

import time, re
from sqlalchemy.ext.orderinglist import ordering_list
from anki.db import *
from anki.utils import genID, canonifyTags
from anki.fonts import toPlatformFont
from anki.utils import parseTags, hexifyID, checksum, stripHTML
from anki.lang import _
from anki.hooks import runFilter
from anki.template import render
from copy import copy

def alignmentLabels():
    return {
        0: _("Center"),
        1: _("Left"),
        2: _("Right"),
        }

# Field models
##########################################################################

fieldModelsTable = Table(
    'fieldModels', metadata,
    Column('id', Integer, primary_key=True),
    Column('ordinal', Integer, nullable=False),
    Column('modelId', Integer, ForeignKey('models.id'), nullable=False),
    Column('name', UnicodeText, nullable=False),
    Column('description', UnicodeText, nullable=False, default=u""), # obsolete
    # reused as RTL marker
    Column('features', UnicodeText, nullable=False, default=u""),
    Column('required', Boolean, nullable=False, default=True),
    Column('unique', Boolean, nullable=False, default=True), # sqlite keyword
    Column('numeric', Boolean, nullable=False, default=False),
    # display
    Column('quizFontFamily', UnicodeText, default=u"Arial"),
    Column('quizFontSize', Integer, default=20),
    Column('quizFontColour', String(7)),
    Column('editFontFamily', UnicodeText, default=u"1"), # reused as <pre> toggle
    Column('editFontSize', Integer, default=20))

class FieldModel(object):
    "The definition of one field in a fact."

    def __init__(self, name=u"", required=True, unique=True):
        self.name = name
        self.required = required
        self.unique = unique
        self.id = genID()

    def copy(self):
        new = FieldModel()
        for p in class_mapper(FieldModel).iterate_properties:
            setattr(new, p.key, getattr(self, p.key))
        new.id = genID()
        new.model = None
        return new

mapper(FieldModel, fieldModelsTable)

# Card models
##########################################################################

cardModelsTable = Table(
    'cardModels', metadata,
    Column('id', Integer, primary_key=True),
    Column('ordinal', Integer, nullable=False),
    Column('modelId', Integer, ForeignKey('models.id'), nullable=False),
    Column('name', UnicodeText, nullable=False),
    Column('description', UnicodeText, nullable=False, default=u""), # obsolete
    Column('active', Boolean, nullable=False, default=True),
    # formats: question/answer/last(not used)
    Column('qformat', UnicodeText, nullable=False),
    Column('aformat', UnicodeText, nullable=False),
    Column('lformat', UnicodeText),
    # question/answer editor format (not used yet)
    Column('qedformat', UnicodeText),
    Column('aedformat', UnicodeText),
    Column('questionInAnswer', Boolean, nullable=False, default=False),
    # unused
    Column('questionFontFamily', UnicodeText, default=u"Arial"),
    Column('questionFontSize', Integer, default=20),
    Column('questionFontColour', String(7), default=u"#000000"),
    # used for both question & answer
    Column('questionAlign', Integer, default=0),
    # ununsed
    Column('answerFontFamily', UnicodeText, default=u"Arial"),
    Column('answerFontSize', Integer, default=20),
    Column('answerFontColour', String(7), default=u"#000000"),
    Column('answerAlign', Integer, default=0),
    Column('lastFontFamily', UnicodeText, default=u"Arial"),
    Column('lastFontSize', Integer, default=20),
    # used as background colour
    Column('lastFontColour', String(7), default=u"#FFFFFF"),
    Column('editQuestionFontFamily', UnicodeText, default=None),
    Column('editQuestionFontSize', Integer, default=None),
    Column('editAnswerFontFamily', UnicodeText, default=None),
    Column('editAnswerFontSize', Integer, default=None),
    # empty answer
    Column('allowEmptyAnswer', Boolean, nullable=False, default=True),
    Column('typeAnswer', UnicodeText, nullable=False, default=u""))

class CardModel(object):
    """Represents how to generate the front and back of a card."""
    def __init__(self, name=u"", qformat=u"q", aformat=u"a", active=True):
        self.name = name
        self.qformat = qformat
        self.aformat = aformat
        self.active = active
        self.id = genID()

    def copy(self):
        new = CardModel()
        for p in class_mapper(CardModel).iterate_properties:
            setattr(new, p.key, getattr(self, p.key))
        new.id = genID()
        new.model = None
        return new

mapper(CardModel, cardModelsTable)

def formatQA(cid, mid, fact, tags, cm, deck):
    "Return a dict of {id, question, answer}"
    d = {'id': cid}
    fields = {}
    for (k, v) in fact.items():
        fields["text:"+k] = stripHTML(v[1])
        if v[1]:
            fields[k] = '<span class="fm%s">%s</span>' % (
                hexifyID(v[0]), v[1])
        else:
            fields[k] = u""
    fields['tags'] = tags[0]
    fields['Tags'] = tags[0]
    fields['modelTags'] = tags[1]
    fields['cardModel'] = tags[2]
    # render q & a
    ret = []
    for (type, format) in (("question", cm.qformat),
                           ("answer", cm.aformat)):
        # convert old style
        format = re.sub("%\((.+?)\)s", "{{\\1}}", format)
        # allow custom rendering functions & info
        fields = runFilter("prepareFields", fields, cid, mid, fact, tags, cm, deck)
        html = render(format, fields)
        d[type] = runFilter("formatQA", html, type, cid, mid, fact, tags, cm, deck)
    return d

# Model table
##########################################################################

modelsTable = Table(
    'models', metadata,
    Column('id', Integer, primary_key=True),
    Column('deckId', Integer, ForeignKey("decks.id", use_alter=True, name="deckIdfk")),
    Column('created', Float, nullable=False, default=time.time),
    Column('modified', Float, nullable=False, default=time.time),
    Column('tags', UnicodeText, nullable=False, default=u""),
    Column('name', UnicodeText, nullable=False),
    Column('description', UnicodeText, nullable=False, default=u""), # obsolete
    Column('features', UnicodeText, nullable=False, default=u""), # used as mediaURL
    Column('spacing', Float, nullable=False, default=0.1), # obsolete
    Column('initialSpacing', Float, nullable=False, default=60), # obsolete
    Column('source', Integer, nullable=False, default=0))

class Model(object):
    "Defines the way a fact behaves, what fields it can contain, etc."
    def __init__(self, name=u""):
        self.name = name
        self.id = genID()

    def setModified(self):
        self.modified = time.time()

    def addFieldModel(self, field):
        "Add a field model."
        self.fieldModels.append(field)
        s = object_session(self)
        if s:
            s.flush()

    def addCardModel(self, card):
        "Add a card model."
        self.cardModels.append(card)
        s = object_session(self)
        if s:
            s.flush()

mapper(Model, modelsTable, properties={
    'fieldModels': relation(FieldModel, backref='model',
                             collection_class=ordering_list('ordinal'),
                             order_by=[fieldModelsTable.c.ordinal],
                            cascade="all, delete-orphan"),
    'cardModels': relation(CardModel, backref='model',
                           collection_class=ordering_list('ordinal'),
                           order_by=[cardModelsTable.c.ordinal],
                           cascade="all, delete-orphan"),
       })

# Model deletions
##########################################################################

modelsDeletedTable = Table(
    'modelsDeleted', metadata,
    Column('modelId', Integer, ForeignKey("models.id"),
           nullable=False),
    Column('deletedTime', Float, nullable=False))
