# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import pprint

import time
from anki.hooks import runHook
from anki.utils import intTime, timestampID, joinFields
from anki.consts import *

# Cards
##########################################################################

# Type: 0=new, 1=learning, 2=due
# Queue: same as above, and:
#        -1=suspended, -2=user buried, -3=sched buried
# Due is used differently for different queues.
# - new queue: note id or random int
# - rev queue: integer day
# - lrn queue: integer timestamp

class Card(object):

    def __init__(self, col, id=None):
        self.col = col
        self.timerStarted = None
        self._qa = None
        self._note = None
        if id:
            self.id = id
            self.load()
        else:
            # to flush, set nid, ord, and due
            self.id = timestampID(col.db, "cards")
            self.did = 1
            self.crt = intTime()
            self.type = 0
            self.queue = 0
            self.ivl = 0
            self.factor = 0
            self.reps = 0
            self.lapses = 0
            self.left = 0
            self.odue = 0
            self.odid = 0
            self.flags = 0
            self.data = ""

    def load(self):
        (self.id,
         self.nid,
         self.did,
         self.ord,
         self.mod,
         self.usn,
         self.type,
         self.queue,
         self.due,
         self.ivl,
         self.factor,
         self.reps,
         self.lapses,
         self.left,
         self.odue,
         self.odid,
         self.flags,
         self.data) = self.col.db.first(
             "select * from cards where id = ?", self.id)
        self._qa = None
        self._note = None

    def flush(self):
        self.mod = intTime()
        self.usn = self.col.usn()
        # bug check
        if self.queue == 2 and self.odue and not self.col.decks.isDyn(self.did):
            runHook("odueInvalid")
        assert self.due < 4294967296
        self.col.db.execute(
            """
insert or replace into cards values
(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            self.id,
            self.nid,
            self.did,
            self.ord,
            self.mod,
            self.usn,
            self.type,
            self.queue,
            self.due,
            self.ivl,
            self.factor,
            self.reps,
            self.lapses,
            self.left,
            self.odue,
            self.odid,
            self.flags,
            self.data)
        self.col.log(self)

    def flushSched(self):
        self.mod = intTime()
        self.usn = self.col.usn()
        # bug checks
        if self.queue == 2 and self.odue and not self.col.decks.isDyn(self.did):
            runHook("odueInvalid")
        assert self.due < 4294967296
        self.col.db.execute(
            """update cards set
mod=?, usn=?, type=?, queue=?, due=?, ivl=?, factor=?, reps=?,
lapses=?, left=?, odue=?, odid=?, did=? where id = ?""",
            self.mod, self.usn, self.type, self.queue, self.due, self.ivl,
            self.factor, self.reps, self.lapses,
            self.left, self.odue, self.odid, self.did, self.id)
        self.col.log(self)

    def q(self, reload=False, browser=False):
        return self.css() + self._getQA(reload, browser)['q']

    def a(self):
        return self.css() + self._getQA()['a']

    def css(self):
        return "<style>%s</style>" % self.model()['css']

    def _getQA(self, reload=False, browser=False):
        if not self._qa or reload:
            f = self.note(reload); m = self.model(); t = self.template()
            data = [self.id, f.id, m['id'], self.odid or self.did, self.ord,
                    f.stringTags(), f.joinedFields()]
            if browser:
                args = (t.get('bqfmt'), t.get('bafmt'))
            else:
                args = tuple()
            self._qa = self.col._renderQA(data, *args)
        return self._qa

    def note(self, reload=False):
        if not self._note or reload:
            self._note = self.col.getNote(self.nid)
        return self._note

    def model(self):
        return self.col.models.get(self.note().mid)

    def template(self):
        m = self.model()
        if m['type'] == MODEL_STD:
            return self.model()['tmpls'][self.ord]
        else:
            return self.model()['tmpls'][0]

    def startTimer(self):
        self.timerStarted = time.time()

    def timeLimit(self):
        "Time limit for answering in milliseconds."
        conf = self.col.decks.confForDid(self.odid or self.did)
        return conf['maxTaken']*1000

    def shouldShowTimer(self):
        conf = self.col.decks.confForDid(self.odid or self.did)
        return conf['timer']

    def timeTaken(self):
        "Time taken to answer card, in integer MS."
        total = int((time.time() - self.timerStarted)*1000)
        return min(total, self.timeLimit())

    def isEmpty(self):
        ords = self.col.models.availOrds(
            self.model(), joinFields(self.note().fields))
        if self.ord not in ords:
            return True

    def __repr__(self):
        d = dict(self.__dict__)
        # remove non-useful elements
        del d['_note']
        del d['_qa']
        del d['col']
        del d['timerStarted']
        return pprint.pformat(d, width=300)
