# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.utils import intTime, hexifyID

MAX_TIMER = 60

# Cards
##########################################################################

# Type: 0=new, 1=learning, 2=due
# Queue: same as above, and:
#        -1=suspended, -2=user buried, -3=sched buried, -4=deleted
# Due is used differently for different queues.
# - new queue: fact id or random int
# - rev queue: integer day
# - lrn queue: integer timestamp

class Card(object):

    def __init__(self, deck, id=None):
        self.deck = deck
        self.timerStarted = None
        self._qa = None
        if id:
            self.id = id
            self.load()
        else:
            # to flush, set fid, ord, and due
            self.id = None
            self.gid = 1
            self.crt = intTime()
            self.type = 0
            self.queue = 0
            self.ivl = 0
            self.factor = 0
            self.reps = 0
            self.streak = 0
            self.lapses = 0
            self.grade = 0
            self.cycles = 0
            self.edue = 0
            self.data = ""

    def load(self):
        (self.id,
         self.fid,
         self.gid,
         self.ord,
         self.crt,
         self.mod,
         self.type,
         self.queue,
         self.due,
         self.ivl,
         self.factor,
         self.reps,
         self.streak,
         self.lapses,
         self.grade,
         self.cycles,
         self.edue,
         self.data) = self.deck.db.first(
             "select * from cards where id = ?", self.id)

    def flush(self):
        self.mod = intTime()
        self.deck.db.execute(
            """
insert or replace into cards values
(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            self.id,
            self.fid,
            self.gid,
            self.ord,
            self.crt,
            self.mod,
            self.type,
            self.queue,
            self.due,
            self.ivl,
            self.factor,
            self.reps,
            self.streak,
            self.lapses,
            self.grade,
            self.cycles,
            self.edue,
            self.data)

    def flushSched(self):
        self.mod = intTime()
        self.deck.db.execute(
            """update cards set
mod=?, type=?, queue=?, due=?, ivl=?, factor=?, reps=?,
streak=?, lapses=?, grade=?, cycles=?, edue=? where id = ?""",
            self.mod, self.type, self.queue, self.due, self.ivl,
            self.factor, self.reps, self.streak, self.lapses,
            self.grade, self.cycles, self.edue, self.id)

    def q(self):
        return self._getQA()['q']

    def a(self):
        return self._getQA()['a']

    def _getQA(self, reload=False):
        if not self._qa or reload:
            self._qa = self.deck.renderQA([self.id], "card")[0]
        return self._qa

    def fact(self):
        return self.deck.getFact(self.fid)

    def template(self):
        return self.deck.getTemplate(self.fact().mid, self.ord)

    def startTimer(self):
        self.timerStarted = time.time()

    def timeTaken(self):
        "Time taken to answer card, in integer MS."
        return int(min(time.time() - self.timerStarted, MAX_TIMER)*1000)
