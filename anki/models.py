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

import time
from sqlalchemy.ext.orderinglist import ordering_list
from anki.db import *
from anki.utils import genID
from anki.fonts import toPlatformFont
from anki.utils import parseTags
from anki.lang import _

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
    Column('description', UnicodeText, nullable=False, default=u""),
    Column('features', UnicodeText, nullable=False, default=u""),
    Column('required', Boolean, nullable=False, default=True),
    Column('unique', Boolean, nullable=False, default=True),
    Column('numeric', Boolean, nullable=False, default=False),
    # display
    Column('quizFontFamily', UnicodeText),
    Column('quizFontSize', Integer),
    Column('quizFontColour', String(7)),
    Column('editFontFamily', UnicodeText),
    Column('editFontSize', Integer, default=20))

class FieldModel(object):
    "The definition of one field in a fact."

    def __init__(self, name=u"", description=u"", required=True, unique=True):
        self.name = name
        self.description = description
        self.required = required
        self.unique = unique
        self.id = genID()

    def css(self, type="quiz"):
        t = ".%s { " % self.name.replace(" ", "")
        if getattr(self, type+'FontFamily'):
            t += "font-family: \"%s\"; " % toPlatformFont(
                getattr(self, type+'FontFamily'))
        if getattr(self, type+'FontSize'):
            t += "font-size: %dpx; " % getattr(self, type+'FontSize')
        if type == "quiz" and getattr(self, type+'FontColour'):
            t += "color: %s; " % getattr(self, type+'FontColour')
        t += " }\n"
        return t

mapper(FieldModel, fieldModelsTable)

# Card models
##########################################################################

cardModelsTable = Table(
    'cardModels', metadata,
    Column('id', Integer, primary_key=True),
    Column('ordinal', Integer, nullable=False),
    Column('modelId', Integer, ForeignKey('models.id'), nullable=False),
    Column('name', UnicodeText, nullable=False),
    Column('description', UnicodeText, nullable=False, default=u""),
    Column('active', Boolean, nullable=False, default=True),
    # formats: question/answer/last(not used)
    Column('qformat', UnicodeText, nullable=False),
    Column('aformat', UnicodeText, nullable=False),
    Column('lformat', UnicodeText),
    # question/answer editor format (not used yet)
    Column('qedformat', UnicodeText),
    Column('aedformat', UnicodeText),
    Column('questionInAnswer', Boolean, nullable=False, default=False),
    # display
    Column('questionFontFamily', UnicodeText, default=u"Arial"),
    Column('questionFontSize', Integer, default=20),
    Column('questionFontColour', String(7), default=u"#000000"),
    Column('questionAlign', Integer, default=0),
    Column('answerFontFamily', UnicodeText, default=u"Arial"),
    Column('answerFontSize', Integer, default=20),
    Column('answerFontColour', String(7), default=u"#000000"),
    Column('answerAlign', Integer, default=0),
    Column('lastFontFamily', UnicodeText, default=u"Arial"),
    Column('lastFontSize', Integer, default=20),
    Column('lastFontColour', String(7), default=u"#000000"),
    Column('editQuestionFontFamily', UnicodeText, default=None),
    Column('editQuestionFontSize', Integer, default=None),
    Column('editAnswerFontFamily', UnicodeText, default=None),
    Column('editAnswerFontSize', Integer, default=None))

class CardModel(object):
    """Represents how to generate the front and back of a card."""
    def __init__(self, name=u"", description=u"",
                 qformat=u"q", aformat=u"a", active=True):
        self.name = name
        self.description = description
        self.qformat = qformat
        self.aformat = aformat
        self.active = active
        self.id = genID()

    def renderQA(self, card, fact, type, format="text"):
        "Render fact into card based on card model."
        if type == "question": field = self.qformat
        elif type == "answer": field = self.aformat
        htmlFields = {}
        htmlFields.update(fact)
        alltags = parseTags(card.tags + "," +
                            card.fact.tags + "," +
                            card.cardModel.name + "," +
                            card.fact.model.tags)
        htmlFields['tags'] = ", ".join(alltags)
        textFields = {}
        textFields.update(htmlFields)
        # add per-field formatting
        for (k, v) in htmlFields.items():
            # generate pure text entries
            htmlFields["text:"+k] = v
            textFields["text:"+k] = v
            if v:
                # convert newlines to html & add spans to fields
                v = v.replace("\n", "<br>")
                htmlFields[k] = '<span class="%s">%s</span>' % (k.replace(" ",""), v)
        try:
            html = field % htmlFields
            text = field % textFields
        except (KeyError, TypeError, ValueError):
            return _("[invalid format; see model properties]")
        if not html:
            html = _("[empty]")
            text = _("[empty]")
        if format == "text":
            return text
        # add outer div & alignment (with tables due to qt's html handling)
        html = '<div class="%s">%s</div>' % (type, html)
        attr = type + 'Align'
        if getattr(self, attr) == 0:
            align = "center"
        elif getattr(self, attr) == 1:
            align = "left"
        else:
            align = "right"
        html = (("<center><table width=95%%><tr><td align=%s>" % align) +
                   html + "</td></tr></table></center>")
        return html

    def renderQASQL(self, type, factId):
        "Render QA in pure SQL, with no HTML generation."
        fields = dict(object_session(self).all("""
select fieldModels.name, fields.value from fields, fieldModels
where
fields.factId = :factId and
fields.fieldModelId = fieldModels.id""", factId=factId))
        fields['tags'] = u""
        for (k, v) in fields.items():
            fields["text:"+k] = v
        if type == "q": format = self.qformat
        else: format = self.aformat
        try:
            return format % fields
        except (KeyError, TypeError, ValueError):
            return _("[empty]")

    def css(self):
        "Return the CSS markup for this card."
        t = ""
        for type in ("question", "answer"):
            t += ".%s { font-family: \"%s\"; color: %s; font-size: %dpx; }\n" % (
                type,
                toPlatformFont(getattr(self, type+"FontFamily")),
                getattr(self, type+"FontColour"),
                getattr(self, type+"FontSize"))
        return t

mapper(CardModel, cardModelsTable)

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
    Column('description', UnicodeText, nullable=False, default=u""),
    Column('features', UnicodeText, nullable=False, default=u""),
    Column('spacing', Float, nullable=False, default=0.1),
    Column('initialSpacing', Float, nullable=False, default=600))

class Model(object):
    "Defines the way a fact behaves, what fields it can contain, etc."
    def __init__(self, name=u"", description=u""):
        self.name = name
        self.description = description
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
