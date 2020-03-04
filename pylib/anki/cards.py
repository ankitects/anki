# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import pprint
import time
from typing import List, Optional

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.consts import *
from anki.models import NoteType, Template
from anki.notes import Note
from anki.sound import AVTag
from anki.utils import intTime, joinFields, timestampID

# Cards
##########################################################################

# Type: 0=new, 1=learning, 2=due
# Queue: same as above, and:
#        -1=suspended, -2=user buried, -3=sched buried
# Due is used differently for different queues.
# - new queue: note id or random int
# - rev queue: integer day
# - lrn queue: integer timestamp


class Card:
    _note: Optional[Note]
    timerStarted: Optional[float]
    lastIvl: int
    ord: int

    def __init__(
        self, col: anki.collection._Collection, id: Optional[int] = None
    ) -> None:
        self.col = col.weakref()
        self.timerStarted = None
        self._render_output: Optional[anki.template.TemplateRenderOutput] = None
        self._note = None
        if id:
            self.id = id
            self.load()
        else:
            # to flush, set nid, ord, and due
            self.id = timestampID(col.db, "cards")
            self.did = 1
            self.crt = intTime()
            self.type = CARD_TYPE_NEW
            self.queue = QUEUE_TYPE_NEW
            self.ivl = 0
            self.factor = 0
            self.reps = 0
            self.lapses = 0
            self.left = 0
            self.odue = 0
            self.odid = 0
            self.flags = 0
            self.data = ""

    def load(self) -> None:
        (
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
            self.data,
        ) = self.col.db.first("select * from cards where id = ?", self.id)
        self._render_output = None
        self._note = None

    def _preFlush(self) -> None:
        hooks.card_will_flush(self)
        self.mod = intTime()
        self.usn = self.col.usn()
        # bug check
        if (
            self.queue == QUEUE_TYPE_REV
            and self.odue
            and not self.col.decks.isDyn(self.did)
        ):
            hooks.card_odue_was_invalid()
        assert self.due < 4294967296

    def flush(self) -> None:
        self._preFlush()
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
            self.data,
        )
        self.col.log(self)

    def flushSched(self) -> None:
        self._preFlush()
        # bug checks
        self.col.db.execute(
            """update cards set
mod=?, usn=?, type=?, queue=?, due=?, ivl=?, factor=?, reps=?,
lapses=?, left=?, odue=?, odid=?, did=? where id = ?""",
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
            self.did,
            self.id,
        )
        self.col.log(self)

    def question(self, reload: bool = False, browser: bool = False) -> str:
        return self.css() + self.render_output(reload, browser).question_text

    def answer(self) -> str:
        return self.css() + self.render_output().answer_text

    def question_av_tags(self) -> List[AVTag]:
        return self.render_output().question_av_tags

    def answer_av_tags(self) -> List[AVTag]:
        return self.render_output().answer_av_tags

    def css(self) -> str:
        return "<style>%s</style>" % self.model()["css"]

    def render_output(
        self, reload: bool = False, browser: bool = False
    ) -> anki.template.TemplateRenderOutput:
        if not self._render_output or reload:
            note = self.note(reload)
            self._render_output = anki.template.render_card(
                self.col, self, note, browser
            )
        return self._render_output

    def note(self, reload: bool = False) -> Note:
        if not self._note or reload:
            self._note = self.col.getNote(self.nid)
        return self._note

    def note_type(self) -> NoteType:
        return self.col.models.get(self.note().mid)

    q = question
    a = answer
    model = note_type

    def template(self) -> Template:
        m = self.model()
        if m["type"] == MODEL_STD:
            return self.model()["tmpls"][self.ord]
        else:
            return self.model()["tmpls"][0]

    def startTimer(self) -> None:
        self.timerStarted = time.time()

    def timeLimit(self) -> int:
        "Time limit for answering in milliseconds."
        conf = self.col.decks.confForDid(self.odid or self.did)
        return conf["maxTaken"] * 1000

    def shouldShowTimer(self) -> bool:
        conf = self.col.decks.confForDid(self.odid or self.did)
        return conf["timer"]

    def timeTaken(self) -> int:
        "Time taken to answer card, in integer MS."
        total = int((time.time() - self.timerStarted) * 1000)
        return min(total, self.timeLimit())

    def isEmpty(self) -> Optional[bool]:
        ords = self.col.models.availOrds(self.model(), joinFields(self.note().fields))
        if self.ord not in ords:
            return True
        return False

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        # remove non-useful elements
        del d["_note"]
        del d["_render_output"]
        del d["col"]
        del d["timerStarted"]
        return pprint.pformat(d, width=300)

    def userFlag(self) -> int:
        return self.flags & 0b111

    def setUserFlag(self, flag: int) -> None:
        assert 0 <= flag <= 7
        self.flags = (self.flags & ~0b111) | flag
