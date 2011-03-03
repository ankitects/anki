# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.db import *

# Flags: 0=standard review, 1=reschedule due to cram, drill, etc
# Rep: Repetition number. The same number may appear twice if a card has been
# manually rescheduled or answered on multiple sites before a sync.
#
# We store the times in integer milliseconds to avoid an extra index on the
# primary key.

revlogTable = Table(
    'revlog', metadata,
    Column('time', Integer, nullable=False, primary_key=True),
    Column('cardId', Integer, nullable=False),
    Column('ease', Integer, nullable=False),
    Column('rep', Integer, nullable=False),
    Column('lastInterval', Integer, nullable=False),
    Column('interval', Integer, nullable=False),
    Column('factor', Integer, nullable=False),
    Column('userTime', Integer, nullable=False),
    Column('flags', Integer, nullable=False, default=0))

def logReview(db, card, ease, flags=0):
    db.statement("""
insert into revlog values (
:created, :cardId, :ease, :rep, :lastInterval, :interval, :factor,
:userTime, :flags)""",
                 created=int(time.time()*1000), cardId=card.id, ease=ease, rep=card.reps,
                 lastInterval=card.lastInterval, interval=card.interval,
                 factor=card.factor, userTime=int(card.userTime()*1000),
                 flags=flags)
