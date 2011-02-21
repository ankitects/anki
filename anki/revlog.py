# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.db import *

# Flags: 0=standard review, 1=reschedule due to cram, drill, etc
# Rep: Repetition number. The same number may appear twice if a card has been
# manually rescheduled or answered on multiple sites before a sync.

revlogTable = Table(
    'revlog', metadata,
    Column('time', Float, nullable=False, primary_key=True, default=time.time),
    Column('cardId', Integer, nullable=False),
    Column('ease', Integer, nullable=False),
    Column('rep', Integer, nullable=False),
    Column('lastInterval', Float, nullable=False),
    Column('interval', Float, nullable=False),
    Column('factor', Float, nullable=False),
    Column('userTime', Float, nullable=False),
    Column('flags', Integer, nullable=False, default=0))

def logReview(db, card, ease, flags=0):
    db.statement("""
insert into revlog values (
:created, :cardId, :ease, :rep, :lastInterval, :interval, :factor,
:userTime, :flags)""",
                 created=time.time(), cardId=card.id, ease=ease, rep=card.reps,
                 lastInterval=card.lastInterval, interval=card.interval,
                 factor=card.factor, userTime=card.userTime(),
                 flags=flags)
