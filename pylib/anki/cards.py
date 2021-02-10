# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import pprint
import time
from typing import List, Optional

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki import hooks
from anki.consts import *
from anki.models import NoteType, Template
from anki.notes import Note
from anki.sound import AVTag

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
        self, col: anki.collection.Collection, id: Optional[int] = None
    ) -> None:
        self.col = col.weakref()
        self.timerStarted = None
        self._render_output: Optional[anki.template.TemplateRenderOutput] = None
        if id:
            # existing card
            self.id = id
            self.load()
        else:
            # new card with defaults
            self._load_from_backend_card(_pb.Card())

    def load(self) -> None:
        c = self.col._backend.get_card(self.id)
        assert c
        self._load_from_backend_card(c)

    def _load_from_backend_card(self, c: _pb.Card) -> None:
        self._render_output = None
        self._note = None
        self.id = c.id
        self.nid = c.note_id
        self.did = c.deck_id
        self.ord = c.template_idx
        self.mod = c.mtime_secs
        self.usn = c.usn
        self.type = c.ctype
        self.queue = c.queue
        self.due = c.due
        self.ivl = c.interval
        self.factor = c.ease_factor
        self.reps = c.reps
        self.lapses = c.lapses
        self.left = c.remaining_steps
        self.odue = c.original_due
        self.odid = c.original_deck_id
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
        card = _pb.Card(
            id=self.id,
            note_id=self.nid,
            deck_id=self.did,
            template_idx=self.ord,
            ctype=self.type,
            queue=self.queue,
            due=self.due,
            interval=self.ivl,
            ease_factor=self.factor,
            reps=self.reps,
            lapses=self.lapses,
            remaining_steps=self.left,
            original_due=self.odue,
            original_deck_id=self.odid,
            flags=self.flags,
            data=self.data,
        )
        if self.id != 0:
            self.col._backend.update_card(card)
        else:
            self.id = self.col._backend.add_card(card)

    def question(self, reload: bool = False, browser: bool = False) -> str:
        return self.render_output(reload, browser).question_and_style()

    def answer(self) -> str:
        return self.render_output().answer_and_style()

    def question_av_tags(self) -> List[AVTag]:
        return self.render_output().question_av_tags

    def answer_av_tags(self) -> List[AVTag]:
        return self.render_output().answer_av_tags

    # legacy
    def css(self) -> str:
        return f"<style>{self.render_output().css}</style>"

    def render_output(
        self, reload: bool = False, browser: bool = False
    ) -> anki.template.TemplateRenderOutput:
        if not self._render_output or reload:
            self._render_output = (
                anki.template.TemplateRenderContext.from_existing_card(
                    self, browser
                ).render()
            )
        return self._render_output

    def set_render_output(self, output: anki.template.TemplateRenderOutput) -> None:
        self._render_output = output

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

    # legacy
    def isEmpty(self) -> bool:
        return False

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        # remove non-useful elements
        del d["_note"]
        del d["_render_output"]
        del d["col"]
        del d["timerStarted"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    def userFlag(self) -> int:
        return self.flags & 0b111

    def setUserFlag(self, flag: int) -> None:
        assert 0 <= flag <= 7
        self.flags = (self.flags & ~0b111) | flag
