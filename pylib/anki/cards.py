# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import pprint
import time
from typing import NewType

import anki
import anki.collection
import anki.decks
import anki.notes
import anki.template
from anki import cards_pb2, hooks
from anki._legacy import DeprecatedNamesMixin, deprecated
from anki.consts import *
from anki.models import NotetypeDict, TemplateDict
from anki.notes import Note
from anki.sound import AVTag

# Cards
##########################################################################

# Type: 0=new, 1=learning, 2=due
# Queue: same as above, and:
#        -1=suspended, -2=user buried, -3=sched buried
# Due is used differently for different queues.
# - new queue: position
# - rev queue: integer day
# - lrn queue: integer timestamp

# types
CardId = NewType("CardId", int)
BackendCard = cards_pb2.Card
FSRSMemoryState = cards_pb2.FsrsMemoryState


class Card(DeprecatedNamesMixin):
    """A card: one question and answer generated from a note.

    Usually obtained from the collection rather than constructed directly:

    >>> card = col.get_card(1234567890123)        # doctest: +SKIP
    >>> cards = col.find_cards("deck:Default")    # doctest: +SKIP

    A card is always tied to a notetype template, which is what `ord` selects.
    The note it came from is fetched on demand, so reading a card does not cost
    a second query:

    >>> note = card.note()                        # doctest: +SKIP
    >>> card.question()                           # doctest: +SKIP
    '<div>the rendered front, with styling</div>'

    Scheduling state lives in `queue` and `type`, which are distinct: `type` is
    the stage the card is at (`CARD_TYPE_NEW`, `CARD_TYPE_LRN`, `CARD_TYPE_REV`
    or `CARD_TYPE_RELEARNING`), and `queue` is where it sits, which is the same
    for a scheduled card but is negative for a suspended or buried one. See the
    `QUEUE_TYPE_*` constants in `anki.consts`. The meaning of `due` depends on
    the queue: a position in the new queue, a day number in the review queue, or
    a timestamp in the learning queue.

    Changes are saved through the collection, not the card:

    >>> col.update_card(card)                     # doctest: +SKIP
    """

    _note: Note | None
    lastIvl: int
    ord: int
    nid: anki.notes.NoteId
    id: CardId
    did: anki.decks.DeckId
    odid: anki.decks.DeckId
    queue: CardQueue
    type: CardType
    memory_state: FSRSMemoryState | None
    desired_retention: float | None
    decay: float | None
    last_review_time: int | None

    def __init__(
        self,
        col: anki.collection.Collection,
        id: CardId | None = None,
        backend_card: BackendCard | None = None,
    ) -> None:
        self.col = col.weakref()
        self.timer_started: float | None = None
        self._render_output: anki.template.TemplateRenderOutput | None = None
        if id:
            # existing card
            self.id = id
            self.load()
        elif backend_card:
            self._load_from_backend_card(backend_card)
        else:
            # new card with defaults
            self._load_from_backend_card(cards_pb2.Card())

    def load(self) -> None:
        """Discard the in-memory copy and reload the card from the database.

        Also drops the cached render output and note, so both are fetched again
        on next use.
        """
        card = self.col._backend.get_card(self.id)
        assert card
        self._load_from_backend_card(card)

    def _load_from_backend_card(self, card: cards_pb2.Card) -> None:
        self._render_output = None
        self._note = None
        self.id = CardId(card.id)
        self.nid = anki.notes.NoteId(card.note_id)
        self.did = anki.decks.DeckId(card.deck_id)
        self.ord = card.template_idx
        self.mod = card.mtime_secs
        self.usn = card.usn
        self.type = CardType(card.ctype)
        self.queue = CardQueue(card.queue)
        self.due = card.due
        self.ivl = card.interval
        self.factor = card.ease_factor
        self.reps = card.reps
        self.lapses = card.lapses
        self.left = card.remaining_steps
        self.odue = card.original_due
        self.odid = anki.decks.DeckId(card.original_deck_id)
        self.flags = card.flags
        self.original_position = (
            card.original_position if card.HasField("original_position") else None
        )
        self.custom_data = card.custom_data
        self.memory_state = card.memory_state if card.HasField("memory_state") else None
        self.desired_retention = (
            card.desired_retention if card.HasField("desired_retention") else None
        )
        self.decay = card.decay if card.HasField("decay") else None
        self.last_review_time = (
            card.last_review_time_secs
            if card.HasField("last_review_time_secs")
            else None
        )

    def _to_backend_card(self) -> cards_pb2.Card:
        # mtime & usn are set by backend
        return cards_pb2.Card(
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
            original_position=self.original_position,
            custom_data=self.custom_data,
            memory_state=self.memory_state,
            desired_retention=self.desired_retention,
            decay=self.decay,
            last_review_time_secs=self.last_review_time,
        )

    @deprecated(info="please use col.update_card()")
    def flush(self) -> None:
        hooks.card_will_flush(self)
        if self.id != 0:
            self.col._backend.update_cards(
                cards=[self._to_backend_card()], skip_undo_entry=True
            )
        else:
            raise Exception("card.flush() expects an existing card")

    def question(self, reload: bool = False, browser: bool = False) -> str:
        """The rendered front of the card, with styling, as HTML.

        `browser` selects browser rendering rules rather than reviewer ones.
        See `render_output()`.
        """
        return self.render_output(reload, browser).question_and_style()

    def answer(self) -> str:
        """The rendered back of the card, with styling, as HTML."""
        return self.render_output().answer_and_style()

    def question_av_tags(self) -> list[AVTag]:
        """The sound and video files referenced by the front, in order."""
        return self.render_output().question_av_tags

    def answer_av_tags(self) -> list[AVTag]:
        """The sound and video files referenced by the back, in order."""
        return self.render_output().answer_av_tags

    def render_output(
        self, reload: bool = False, browser: bool = False
    ) -> anki.template.TemplateRenderOutput:
        """The rendered card, caching the result.

        The cached output is reused until `reload` is passed, so this is much
        cheaper than `question()` and `answer()` in a loop over many cards.
        `browser` renders as the browser does rather than the reviewer.

        The output is dropped by `load()`, and is not written to the database, so
        changing a notetype's templates does not by itself invalidate it.
        """
        if not self._render_output or reload:
            self._render_output = (
                anki.template.TemplateRenderContext.from_existing_card(
                    self, browser
                ).render()
            )
        return self._render_output

    def set_render_output(self, output: anki.template.TemplateRenderOutput) -> None:
        """Store render output to be returned by `render_output()` unchanged.

        Used when the output was rendered from something other than the stored
        card, as `anki.notes.Note.ephemeral_card()` does.
        """
        self._render_output = output

    def note(self, reload: bool = False) -> Note:
        """The note this card was generated from, fetched on first use."""
        if not self._note or reload:
            self._note = self.col.get_note(self.nid)
        return self._note

    def note_type(self) -> NotetypeDict:
        """The notetype this card's template belongs to."""
        return self.col.models.get(self.note().mid)

    def template(self) -> TemplateDict:
        """The template this card renders with.

        A cloze notetype has a single template, so the card's `ord` selects a
        cloze deletion rather than a template, and the first one is returned.
        """
        notetype = self.note_type()
        templates = notetype["tmpls"]
        if notetype["type"] == MODEL_STD:
            return templates[self.ord]
        else:
            return templates[0]

    def start_timer(self) -> None:
        """Start the answer timer, for use with `time_taken()`."""
        self.timer_started = time.time()

    def current_deck_id(self) -> anki.decks.DeckId:
        """The deck the card's deck options apply to.

        This is the original deck if the card has one, so a card that was moved
        to a filtered deck still uses the options it was scheduled with.
        """
        return anki.decks.DeckId(self.odid or self.did)

    def time_limit(self) -> int:
        "Time limit for answering in milliseconds."
        conf = self.col.decks.config_dict_for_deck_id(self.current_deck_id())
        return conf["maxTaken"] * 1000

    def should_show_timer(self) -> bool:
        """Whether the reviewer shows a countdown for this card."""
        conf = self.col.decks.config_dict_for_deck_id(self.current_deck_id())
        return conf["timer"]

    def replay_question_audio_on_answer_side(self) -> bool:
        """Whether the front's audio plays again on the answer side."""
        conf = self.col.decks.config_dict_for_deck_id(self.current_deck_id())
        return conf.get("replayq", True)

    def autoplay(self) -> bool:
        """Whether the reviewer plays audio and video without being asked."""
        return self.col.decks.config_dict_for_deck_id(self.current_deck_id())[
            "autoplay"
        ]

    def time_taken(self, capped: bool = True) -> int:
        """Time taken since card timer started, in integer MS.
        If `capped` is true, returned time is limited to deck preset setting.

        Requires `start_timer()` to have been called, otherwise there is no
        start time to measure from.
        """
        total = int((time.time() - self.timer_started) * 1000)
        if capped:
            total = min(total, self.time_limit())
        return total

    def description(self) -> str:
        """The card's state as a readable string, for debugging.

        Includes every scheduling field, but not the note or the rendered
        output, which would otherwise make this unusable.
        """
        dict_copy = dict(self.__dict__)
        # remove non-useful elements
        del dict_copy["_note"]
        del dict_copy["_render_output"]
        del dict_copy["col"]
        del dict_copy["timer_started"]
        return f"{super().__repr__()} {pprint.pformat(dict_copy, width=300)}"

    def user_flag(self) -> int:
        """The user flag, an integer from 0 to 7.

        0 means no flag. The other values are given a colour in the browser:
        1 red, 2 orange, 3 green, 4 blue, 5 pink, 6 turquoise, 7 purple.
        """
        return self.flags & 0b111

    def set_user_flag(self, flag: int) -> None:
        """Set the user flag, which must be between 0 and 7 inclusive.

        Only changes the card in memory; saving is done by the collection.
        """
        print("use col.set_user_flag_for_cards() instead")
        if not 0 <= flag <= 7:
            raise Exception("invalid flag")
        self.flags = (self.flags & ~0b111) | flag

    @deprecated(info="use card.render_output() directly")
    def css(self) -> str:
        return f"<style>{self.render_output().css}</style>"

    @deprecated(info="handled by template rendering")
    def is_empty(self) -> bool:
        return False


Card.register_deprecated_aliases(
    flushSched=Card.flush,
    q=Card.question,
    a=Card.answer,
    model=Card.note_type,
)
