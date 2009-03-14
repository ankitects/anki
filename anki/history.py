# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

"""\
History - keeping a record of all reviews
==========================================

If users run 'check db', duplicate records will be inserted into the DB - I
really should have used the time stamp as the key. You can remove them by
keeping the lowest id for any given timestamp.
"""

__docformat__ = 'restructuredtext'

import time
from anki.db import *

reviewHistoryTable = Table(
    'reviewHistory', metadata,
    Column('cardId', Integer, nullable=False),
    Column('time', Float, nullable=False, default=time.time),
    Column('lastInterval', Float, nullable=False),
    Column('nextInterval', Float, nullable=False),
    Column('ease', Integer, nullable=False),
    Column('delay', Float, nullable=False),
    Column('lastFactor', Float, nullable=False),
    Column('nextFactor', Float, nullable=False),
    Column('reps', Float, nullable=False),
    Column('thinkingTime', Float, nullable=False),
    Column('yesCount', Float, nullable=False),
    Column('noCount', Float, nullable=False),
    PrimaryKeyConstraint("cardId", "time"))

class CardHistoryEntry(object):
    "Create after rescheduling card."

    def __init__(self, card=None, ease=None, delay=None):
        if not card:
            return
        self.cardId = card.id
        self.lastInterval = card.lastInterval
        self.nextInterval = card.interval
        self.lastFactor = card.lastFactor
        self.nextFactor = card.factor
        self.reps = card.reps
        self.yesCount = card.yesCount
        self.noCount = card.noCount
        self.ease = ease
        self.delay = delay
        self.thinkingTime = card.thinkingTime()

    def writeSQL(self, s):
        s.statement("""
insert into reviewHistory
(cardId, lastInterval, nextInterval, ease, delay, lastFactor,
nextFactor, reps, thinkingTime, yesCount, noCount, time)
values (
:cardId, :lastInterval, :nextInterval, :ease, :delay,
:lastFactor, :nextFactor, :reps, :thinkingTime, :yesCount, :noCount,
:time)""",
                    cardId=self.cardId,
                    lastInterval=self.lastInterval,
                    nextInterval=self.nextInterval,
                    ease=self.ease,
                    delay=self.delay,
                    lastFactor=self.lastFactor,
                    nextFactor=self.nextFactor,
                    reps=self.reps,
                    thinkingTime=self.thinkingTime,
                    yesCount=self.yesCount,
                    noCount=self.noCount,
                    time=time.time())

mapper(CardHistoryEntry, reviewHistoryTable)
