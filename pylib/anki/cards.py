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
from anki.rsbackend import BackendCard
from anki.sound import AVTag
from anki.utils import joinFields

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

    def __init__(self, col: anki.storage._Collection, id: Optional[int] = None) -> None:
        self.col = col.weakref()
        self.timerStarted = None
        self._render_output: Optional[anki.template.TemplateRenderOutput] = None
        if id:
            # existing card
            self.id = id
            self.load()
        else:
            # new card with defaults
            self._load_from_backend_card(BackendCard())

    def load(self) -> None:
        c = self.col.backend.get_card(self.id)
        assert c
        self._load_from_backend_card(c)

    def _load_from_backend_card(self, c: BackendCard) -> None:
        self._render_output = None
        self._note = None
        self.id = c.id
        self.nid = c.nid
        self.did = c.did
        self.ord = c.ord
        self.mod = c.mtime
        self.usn = c.usn
        self.type = c.ctype
        self.queue = c.queue
        self.due = c.due
        self.ivl = c.ivl
        self.factor = c.factor
        self.reps = c.reps
        self.lapses = c.lapses
        self.left = c.left
        self.odue = c.odue
        self.odid = c.odid
        self.flags = c.flags
        self.data = c.data

    def _bugcheck(self) -> None:
        if (
            self.queue == QUEUE_TYPE_REV
            and self.odue
            and not self.col.decks.isDyn(self.did)
        ):
            hooks.card_odue_was_invalid()

    def flush(self) -> None:
        self._bugcheck()
        hooks.card_will_flush(self)
        # mtime & usn are set by backend
        card = BackendCard(
            id=self.id,
            nid=self.nid,
            did=self.did,
            ord=self.ord,
            ctype=self.type,
            queue=self.queue,
            due=self.due,
            ivl=self.ivl,
            factor=self.factor,
            reps=self.reps,
            lapses=self.lapses,
            left=self.left,
            odue=self.odue,
            odid=self.odid,
            flags=self.flags,
            data=self.data,
        )
        if self.id != 0:
            self.col.backend.update_card(card)
        else:
            self.id = self.col.backend.add_card(card)

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

    # legacy aliases
    flushSched = flush
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

    def replay_question_audio_on_answer_side(self) -> bool:
        conf = self.col.decks.confForDid(self.odid or self.did)
        return conf.get("replayq", True)

    def autoplay(self) -> bool:
        return self.col.decks.confForDid(self.odid or self.did)["autoplay"]

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
