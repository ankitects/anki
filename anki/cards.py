# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, sys, math, random
from anki.db import *
from anki.models import CardModel, Model, FieldModel, formatQA
from anki.facts import Fact, factsTable, Field
from anki.utils import parseTags, findTag, stripHTML, genID, hexifyID, intTime
from anki.media import updateMediaCount, mediaFiles

MAX_TIMER = 60

# Cards
##########################################################################

# Type: 0=learning, 1=due, 2=new
# Queue: 0=learning, 1=due, 2=new
#        -1=suspended, -2=user buried, -3=sched buried
# Group: scheduling group
# Ordinal: card template # for fact
# Flags: unused; reserved for future use

# Due is used differently for different queues.
# - new queue: fact.pos
# - rev queue: integer day
# - lrn queue: integer timestamp

cardsTable = Table(
    'cards', metadata,
    Column('id', Integer, primary_key=True),
    Column('factId', Integer, ForeignKey("facts.id"), nullable=False),
    Column('groupId', Integer, nullable=False, default=1),
    Column('cardModelId', Integer, ForeignKey("cardModels.id"), nullable=False),
    Column('modified', Integer, nullable=False, default=intTime),
    # general
    Column('question', UnicodeText, nullable=False, default=u""),
    Column('answer', UnicodeText, nullable=False, default=u""),
    Column('ordinal', Integer, nullable=False),
    Column('flags', Integer, nullable=False, default=0),
    # shared scheduling
    Column('type', Integer, nullable=False, default=2),
    Column('queue', Integer, nullable=False, default=2),
    Column('due', Integer, nullable=False),
    # sm2
    Column('interval', Integer, nullable=False, default=0),
    Column('factor', Integer, nullable=False),
    Column('reps', Integer, nullable=False, default=0),
    Column('streak', Integer, nullable=False, default=0),
    Column('lapses', Integer, nullable=False, default=0),
    # learn
    Column('grade', Integer, nullable=False, default=0),
    Column('cycles', Integer, nullable=False, default=0)
)

class Card(object):

    # called one of three ways:
    # - with no args, followed by .fromDB()
    # - with all args, when adding cards to db
    def __init__(self, fact=None, cardModel=None, group=None):
        # timer
        self.timerStarted = None
        if fact:
            self.id = genID()
            self.modified = intTime()
            self.due = fact.pos
            self.fact = fact
            self.modelId = fact.modelId
            self.cardModel = cardModel
            self.groupId = group.id
            # placeholder; will get set properly when card graduates
            self.factor = 2500
            # for non-orm use
            self.cardModelId = cardModel.id
            self.ordinal = cardModel.ordinal

    def setModified(self):
        self.modified = intTime()

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
        return (self.fact._tags, self.fact.model.name, self.cardModel.name)

    # Non-ORM
    ##########################################################################

    def fromDB(self, s, id):
        r = s.first("""select * from cards where id = :id""", id=id)
        if not r:
            return
        (self.id,
         self.factId,
         self.groupId,
         self.cardModelId,
         self.modified,
         self.question,
         self.answer,
         self.ordinal,
         self.flags,
         self.type,
         self.queue,
         self.due,
         self.interval,
         self.factor,
         self.reps,
         self.streak,
         self.lapses,
         self.grade,
         self.cycles) = r
        return True

    def toDB(self, s):
        # this shouldn't be used for schema changes
        s.execute("""update cards set
modified=:modified,
question=:question,
answer=:answer,
flags=:flags,
type=:type,
queue=:queue,
due=:due,
interval=:interval,
factor=:factor,
reps=:reps,
streak=:streak,
lapses=:lapses,
grade=:grade,
cycles=:cycles
where id=:id""", self.__dict__)

mapper(Card, cardsTable, properties={
    'cardModel': relation(CardModel),
    'fact': relation(Fact, backref="cards", primaryjoin=
                     cardsTable.c.factId == factsTable.c.id),
    })
