# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time
from anki.utils import intTime, hexifyID

MAX_TIMER = 60

# Cards
##########################################################################

# Type: 0=learning, 1=due, 2=new
# Queue: 0=learning, 1=due, 2=new
#        -1=suspended, -2=user buried, -3=sched buried
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
            self.type = 2
            self.queue = 2
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
