# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
Cards
====================
"""
__docformat__ = 'restructuredtext'

import time, sys, math, random
from anki.db import *
from anki.models import CardModel, Model, FieldModel, formatQA
from anki.facts import Fact, factsTable, Field
from anki.utils import parseTags, findTag, stripHTML, genID, hexifyID

# Cards
##########################################################################

cardsTable = Table(
    'cards', metadata,
    Column('id', Integer, primary_key=True),
    Column('factId', Integer, ForeignKey("facts.id"), nullable=False),
    Column('cardModelId', Integer, ForeignKey("cardModels.id"), nullable=False),
    Column('created', Float, nullable=False, default=time.time),
    Column('modified', Float, nullable=False, default=time.time),
    Column('tags', UnicodeText, nullable=False, default=u""),
    Column('ordinal', Integer, nullable=False),
    # cached - changed on fact update
    Column('question', UnicodeText, nullable=False, default=u""),
    Column('answer', UnicodeText, nullable=False, default=u""),
    # default to 'normal' priority;
    # this is indexed in deck.py as we need to create a reverse index
    Column('priority', Integer, nullable=False, default=2),
    Column('interval', Float, nullable=False, default=0),
    Column('lastInterval', Float, nullable=False, default=0),
    Column('due', Float, nullable=False, default=time.time),
    Column('lastDue', Float, nullable=False, default=0),
    Column('factor', Float, nullable=False, default=2.5),
    Column('lastFactor', Float, nullable=False, default=2.5),
    Column('firstAnswered', Float, nullable=False, default=0),
    # stats
    Column('reps', Integer, nullable=False, default=0),
    Column('successive', Integer, nullable=False, default=0),
    Column('averageTime', Float, nullable=False, default=0),
    Column('reviewTime', Float, nullable=False, default=0),
    Column('youngEase0', Integer, nullable=False, default=0),
    Column('youngEase1', Integer, nullable=False, default=0),
    Column('youngEase2', Integer, nullable=False, default=0),
    Column('youngEase3', Integer, nullable=False, default=0),
    Column('youngEase4', Integer, nullable=False, default=0),
    Column('matureEase0', Integer, nullable=False, default=0),
    Column('matureEase1', Integer, nullable=False, default=0),
    Column('matureEase2', Integer, nullable=False, default=0),
    Column('matureEase3', Integer, nullable=False, default=0),
    Column('matureEase4', Integer, nullable=False, default=0),
    # this duplicates the above data, because there's no way to map imported
    # data to the above
    Column('yesCount', Integer, nullable=False, default=0),
    Column('noCount', Integer, nullable=False, default=0),
    # caching
    Column('spaceUntil', Float, nullable=False, default=0),
    Column('isDue', Boolean, nullable=False, default=0),
    Column('type', Integer, nullable=False, default=2),
    Column('relativeDelay', Float, nullable=False, default=0), # obsolete
    Column('combinedDue', Integer, nullable=False, default=0))

class Card(object):
    "A card."

    def __init__(self, fact=None, cardModel=None):
        self.tags = u""
        self.id = genID()
        # new cards start as new & due
        self.type = 2
        self.isDue = True
        self.timerStarted = False
        self.timerStopped = False
        self.modified = time.time()
        self.due = self.modified
        self.combinedDue = self.modified
        if fact:
            self.fact = fact
        if cardModel:
            self.cardModel = cardModel
            self.ordinal = cardModel.ordinal
            d = {}
            for f in self.fact.model.fieldModels:
                d[f.name] = (f.id, self.fact[f.name])
            qa = formatQA(None, fact.modelId, d, self.allTags(), cardModel)
            self.question = qa['question']
            self.answer = qa['answer']

    def setModified(self):
        self.modified = time.time()

    def startTimer(self):
        self.timerStarted = time.time()

    def stopTimer(self):
        self.timerStopped = time.time()

    def thinkingTime(self):
        return (self.timerStopped or time.time()) - self.timerStarted

    def totalTime(self):
        return time.time() - self.timerStarted

    def genFuzz(self):
        "Generate a random offset to spread intervals."
        self.fuzz = random.uniform(0.95, 1.05)

    def htmlQuestion(self, type="question"):
        div = '''<div id="cm%s%s">%s</div>''' % (
            type[0], hexifyID(self.cardModel.id), getattr(self, type))
        # add outer div & alignment (with tables due to qt's html handling)
        attr = type + 'Align'
        if getattr(self.cardModel, attr) == 0:
            align = "center"
        elif getattr(self.cardModel, attr) == 1:
            align = "left"
        else:
            align = "right"
        return (("<center><table width=95%%><tr><td align=%s>" % align) +
                div + "</td></tr></table></center>")

    def htmlAnswer(self):
        return self.htmlQuestion(type="answer")

    def updateStats(self, ease, state):
        self.reps += 1
        if ease > 1:
            self.successive += 1
        else:
            self.successive = 0
        delay = self.totalTime()
        # ignore any times over 60 seconds
        if delay < 60:
            self.reviewTime += delay
            if self.averageTime:
                self.averageTime = (self.averageTime + delay) / 2.0
            else:
                self.averageTime = delay
        # we don't track first answer for cards
        if state == "new":
            state = "young"
        # update ease and yes/no count
        attr = state + "Ease%d" % ease
        setattr(self, attr, getattr(self, attr) + 1)
        if ease < 2:
            self.noCount += 1
        else:
            self.yesCount += 1
        if not self.firstAnswered:
            self.firstAnswered = time.time()
        self.setModified()

    def allTags(self):
        "Non-canonified string of all tags."
        return (self.tags + "," +
                self.fact.tags + "," +
                self.cardModel.name + "," +
                self.fact.model.tags)

    def hasTag(self, tag):
        return findTag(tag, parseTags(self.allTags()))

    def fromDB(self, s, id):
        r = s.first("""select
id, factId, cardModelId, created, modified, tags, ordinal, question, answer,
priority, interval, lastInterval, due, lastDue, factor,
lastFactor, firstAnswered, reps, successive, averageTime, reviewTime,
youngEase0, youngEase1, youngEase2, youngEase3, youngEase4,
matureEase0, matureEase1, matureEase2, matureEase3, matureEase4,
yesCount, noCount, spaceUntil, isDue, type, combinedDue
from cards where id = :id""", id=id)
        if not r:
            return
        (self.id,
         self.factId,
         self.cardModelId,
         self.created,
         self.modified,
         self.tags,
         self.ordinal,
         self.question,
         self.answer,
         self.priority,
         self.interval,
         self.lastInterval,
         self.due,
         self.lastDue,
         self.factor,
         self.lastFactor,
         self.firstAnswered,
         self.reps,
         self.successive,
         self.averageTime,
         self.reviewTime,
         self.youngEase0,
         self.youngEase1,
         self.youngEase2,
         self.youngEase3,
         self.youngEase4,
         self.matureEase0,
         self.matureEase1,
         self.matureEase2,
         self.matureEase3,
         self.matureEase4,
         self.yesCount,
         self.noCount,
         self.spaceUntil,
         self.isDue,
         self.type,
         self.combinedDue) = r
        return True

    def toDB(self, s):
        "Write card to DB. Note that isDue assumes card is not spaced."
        if self.reps == 0:
            self.type = 2
        elif self.successive:
            self.type = 1
        else:
            self.type = 0
        s.execute("""update cards set
modified=:modified,
tags=:tags,
interval=:interval,
lastInterval=:lastInterval,
due=:due,
lastDue=:lastDue,
factor=:factor,
lastFactor=:lastFactor,
firstAnswered=:firstAnswered,
reps=:reps,
successive=:successive,
averageTime=:averageTime,
reviewTime=:reviewTime,
youngEase0=:youngEase0,
youngEase1=:youngEase1,
youngEase2=:youngEase2,
youngEase3=:youngEase3,
youngEase4=:youngEase4,
matureEase0=:matureEase0,
matureEase1=:matureEase1,
matureEase2=:matureEase2,
matureEase3=:matureEase3,
matureEase4=:matureEase4,
yesCount=:yesCount,
noCount=:noCount,
spaceUntil = :spaceUntil,
isDue = :isDue,
type = :type,
combinedDue = max(:spaceUntil, :due),
relativeDelay = 0
where id=:id""", self.__dict__)

mapper(Card, cardsTable, properties={
    'cardModel': relation(CardModel),
    'fact': relation(Fact, backref="cards", primaryjoin=
                     cardsTable.c.factId == factsTable.c.id),
    })

mapper(Fact, factsTable, properties={
    'model': relation(Model),
    'fields': relation(Field, backref="fact", order_by=Field.c.ordinal),
    })


# Card deletions
##########################################################################

cardsDeletedTable = Table(
    'cardsDeleted', metadata,
    Column('cardId', Integer, ForeignKey("cards.id"),
           nullable=False),
    Column('deletedTime', Float, nullable=False))
