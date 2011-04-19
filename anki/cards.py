# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time
from anki.utils import intTime, hexifyID

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
        self._rd = None
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
         self.lapses,
         self.grade,
         self.cycles,
         self.edue,
         self.data) = self.deck.db.first(
             "select * from cards where id = ?", self.id)
        self._qa = None
        self._rd = None

    def flush(self):
        self.mod = intTime()
        self.deck.db.execute(
            """
insert or replace into cards values
(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
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
lapses=?, grade=?, cycles=?, edue=? where id = ?""",
            self.mod, self.type, self.queue, self.due, self.ivl,
            self.factor, self.reps, self.lapses,
            self.grade, self.cycles, self.edue, self.id)

    def q(self, classes="q", reload=False):
        return self._withClass(self._getQA(reload)['q'], classes)

    def a(self, classes="a"):
        return self._withClass(self._getQA()['a'], classes)

    def _getQA(self, reload=False):
        if not self._qa or reload:
            gname = self.deck.db.scalar(
                "select name from groups where id = ?", self.gid)
            f = self.fact(); m = self.model()
            data = [self.id, f.id, m.id, self.gid, self.ord, f.stringTags(),
                    f.joinedFields()]
            self._qa = self.deck._renderQA(self.model(), gname, data)
        return self._qa

    def _withClass(self, txt, extra):
        return '<div class="%s %s">%s</div>' % (self.cssClass(), extra, txt)

    def _reviewData(self, reload=False):
        "Fetch the model and fact."
        if not self._rd or reload:
            f = self.deck.getFact(self.fid)
            m = self.deck.getModel(f.mid)
            self._rd = [f, m]
        return self._rd

    def fact(self):
        return self._reviewData()[0]

    def model(self, reload=False):
        return self._reviewData()[1]

    def template(self):
        return self._reviewData()[1].templates[self.ord]

    def cssClass(self):
        return "cm%s-%s" % (hexifyID(self.model().id),
                            hexifyID(self.template()['ord']))

    def startTimer(self):
        self.timerStarted = time.time()

    def timeTaken(self):
        "Time taken to answer card, in integer MS."
        return int((time.time() - self.timerStarted)*1000)
