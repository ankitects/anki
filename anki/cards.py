# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.utils import genID, intTime, hexifyID

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

class Card(object):

    def __init__(self, deck, id=None):
        self.deck = deck
        if id:
            self.id = id
            self.load()
        else:
            # to flush, set fid, tid, due and ord
            self.id = genID()
            self.gid = 1
            self.q = ""
            self.a = ""
            self.flags = 0
            self.type = 2
            self.queue = 2
            self.interval = 0
            self.factor = 0
            self.reps = 0
            self.streak = 0
            self.lapses = 0
            self.grade = 0
            self.cycles = 0
        self.timerStarted = None

    def load(self):
        (self.id,
         self.fid,
         self.tid,
         self.gid,
         self.mod,
         self.q,
         self.a,
         self.ord,
         self.type,
         self.queue,
         self.due,
         self.interval,
         self.factor,
         self.reps,
         self.streak,
         self.lapses,
         self.grade,
         self.cycles) = self.deck.db.first(
             "select * from cards where id = ?", self.id)

    def flush(self):
        self.mod = intTime()
        self.deck.db.execute(
            """
insert or replace into cards values
(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            self.id,
            self.fid,
            self.tid,
            self.gid,
            self.mod,
            self.q,
            self.a,
            self.ord,
            self.type,
            self.queue,
            self.due,
            self.interval,
            self.factor,
            self.reps,
            self.streak,
            self.lapses,
            self.grade,
            self.cycles)

    def flushSched(self):
        self.mod = intTime()
        self.deck.db.execute(
            """update cards set
mod=?, type=?, queue=?, due=?, interval=?, factor=?, reps=?,
streak=?, lapses=?, grade=?, cycles=? where id = ?""",
            self.mod, self.type, self.queue, self.due, self.interval,
            self.factor, self.reps, self.streak, self.lapses,
            self.grade, self.cycles, self.id)

    def fact(self):
        return self.deck.getFact(self.deck, self.fid)

    def startTimer(self):
        self.timerStarted = time.time()

    def userTime(self):
        return min(time.time() - self.timerStarted, MAX_TIMER)

    # Questions and answers
    ##########################################################################

    def htmlQuestion(self, type="question", align=True):
        div = '''<div class="card%s" id="cm%s%s">%s</div>''' % (
            type[0], type[0], hexifyID(self.tid),
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
