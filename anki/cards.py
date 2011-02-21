# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, sys, math, random
from anki.db import *
from anki.models import CardModel, Model, FieldModel, formatQA
from anki.facts import Fact, factsTable, Field
from anki.utils import parseTags, findTag, stripHTML, genID, hexifyID
from anki.media import updateMediaCount, mediaFiles

MAX_TIMER = 60

# Cards
##########################################################################

# Type: 0=lapsed, 1=due, 2=new, 3=drilled
# Queue: under normal circumstances, same as type.
#        -1=suspended, -2=user buried, -3=sched buried (rev early, etc)
# Ordinal: card template # for fact
# Position: sorting position, only for new cards
# Flags: unused; reserved for future use

cardsTable = Table(
    'cards', metadata,
    Column('id', Integer, primary_key=True),
    Column('factId', Integer, ForeignKey("facts.id"), nullable=False),
    Column('cardModelId', Integer, ForeignKey("cardModels.id"), nullable=False),
    # general
    Column('created', Float, nullable=False, default=time.time),
    Column('modified', Float, nullable=False, default=time.time),
    Column('question', UnicodeText, nullable=False, default=u""),
    Column('answer', UnicodeText, nullable=False, default=u""),
    Column('flags', Integer, nullable=False, default=0),
    # ordering
    Column('ordinal', Integer, nullable=False),
    Column('position', Integer, nullable=False),
    # scheduling data
    Column('type', Integer, nullable=False, default=2),
    Column('queue', Integer, nullable=False, default=2),
    Column('lastInterval', Float, nullable=False, default=0),
    Column('interval', Float, nullable=False, default=0),
    Column('due', Float, nullable=False),
    Column('factor', Float, nullable=False, default=2.5),
    # counters
    Column('reps', Integer, nullable=False, default=0),
    Column('successive', Integer, nullable=False, default=0),
    Column('lapses', Integer, nullable=False, default=0))

class Card(object):

    def __init__(self, fact=None, cardModel=None, created=None):
        self.id = genID()
        self.modified = time.time()
        if created:
            self.created = created
            self.due = created
        else:
            self.due = self.modified
        self.position = self.due
        if fact:
            self.fact = fact
        if cardModel:
            self.cardModel = cardModel
            # for non-orm use
            self.cardModelId = cardModel.id
            self.ordinal = cardModel.ordinal
        # timer
        self.timerStarted = None

    def setModified(self):
        self.modified = time.time()

    def startTimer(self):
        self.timerStarted = time.time()

    def userTime(self):
        return min(time.time() - self.timerStarted, MAX_TIMER)

    # Questions and answers
    ##########################################################################

    def rebuildQA(self, deck, media=True):
        # format qa
        d = {}
        for f in self.fact.model.fieldModels:
            d[f.name] = (f.id, self.fact[f.name])
        qa = formatQA(None, self.fact.modelId, d, self._splitTags(),
                      self.cardModel, deck)
        # find old media references
        files = {}
        for type in ("question", "answer"):
            for f in mediaFiles(getattr(self, type) or ""):
                if f in files:
                    files[f] -= 1
                else:
                    files[f] = -1
        # update q/a
        self.question = qa['question']
        self.answer = qa['answer']
        # determine media delta
        for type in ("question", "answer"):
            for f in mediaFiles(getattr(self, type)):
                if f in files:
                    files[f] += 1
                else:
                    files[f] = 1
        # update media counts if we're attached to deck
        if media:
            for (f, cnt) in files.items():
                updateMediaCount(deck, f, cnt)
        self.setModified()

    def htmlQuestion(self, type="question", align=True):
        div = '''<div class="card%s" id="cm%s%s">%s</div>''' % (
            type[0], type[0], hexifyID(self.cardModelId),
            getattr(self, type))
        # add outer div & alignment (with tables due to qt's html handling)
        if not align:
            return div
        attr = type + 'Align'
        if getattr(self.cardModel, attr) == 0:
            align = "center"
        elif getattr(self.cardModel, attr) == 1:
            align = "left"
        else:
            align = "right"
        return (("<center><table width=95%%><tr><td align=%s>" % align) +
                div + "</td></tr></table></center>")

    def htmlAnswer(self, align=True):
        return self.htmlQuestion(type="answer", align=align)

    def _splitTags(self):
        return (self.fact.tags, self.fact.model.tags, self.cardModel.name)

    # Non-ORM
    ##########################################################################

    def fromDB(self, s, id):
        r = s.first("""select * from cards where id = :id""", id=id)
        if not r:
            return
        (self.id,
         self.factId,
         self.cardModelId,
         self.created,
         self.modified,
         self.question,
         self.answer,
         self.flags,
         self.ordinal,
         self.position,
         self.type,
         self.queue,
         self.lastInterval,
         self.interval,
         self.due,
         self.factor,
         self.reps,
         self.successive,
         self.lapses) = r
        return True

    def toDB(self, s):
        s.execute("""update cards set
factId=:factId,
cardModelId=:cardModelId,
created=:created,
modified=:modified,
question=:question,
answer=:answer,
flags=:flags,
ordinal=:ordinal,
position=:position,
type=:type,
queue=:queue,
lastInterval=:lastInterval,
interval=:interval,
due=:due,
factor=:factor,
reps=:reps,
successive=:successive,
lapses=:lapses
where id=:id""", self.__dict__)

mapper(Card, cardsTable, properties={
    'cardModel': relation(CardModel),
    'fact': relation(Fact, backref="cards", primaryjoin=
                     cardsTable.c.factId == factsTable.c.id),
    })

mapper(Fact, factsTable, properties={
    'model': relation(Model),
    'fields': relation(Field, backref="fact", order_by=Field.ordinal),
    })

# Card deletions
##########################################################################

cardsDeletedTable = Table(
    'cardsDeleted', metadata,
    Column('cardId', Integer, ForeignKey("cards.id"),
           nullable=False),
    Column('deletedTime', Float, nullable=False))
