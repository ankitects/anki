# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import difflib
import html
import json
import random
import re
import unicodedata as ucd
from dataclasses import dataclass
from enum import Enum, auto
from typing import Any, Callable, Literal, Match, Sequence, cast

import aqt
import aqt.browser
import aqt.operations
from anki import hooks
from anki.cards import Card, CardId
from anki.collection import Config, OpChanges, OpChangesWithCount
from anki.scheduler.base import ScheduleCardsAsNew
from anki.scheduler.v3 import CardAnswer, NextStates, QueuedCards
from anki.scheduler.v3 import Scheduler as V3Scheduler
from anki.tags import MARKED_TAG
from anki.types import assert_exhaustive
from anki.utils import strip_html
from aqt import AnkiQt, gui_hooks
from aqt.browser.card_info import PreviousReviewerCardInfo, ReviewerCardInfo
from aqt.deckoptions import confirm_deck_then_display_options
from aqt.operations.card import set_card_flag
from aqt.operations.note import remove_notes
from aqt.operations.scheduling import (
    answer_card,
    bury_cards,
    bury_notes,
    forget_cards,
    set_due_date_dialog,
    suspend_cards,
    suspend_note,
)
from aqt.operations.tag import add_tags_to_notes, remove_tags_from_notes
from aqt.profiles import VideoDriver
from aqt.qt import *
from aqt.sound import av_player, play_clicked_audio, record_audio
from aqt.theme import theme_manager
from aqt.toolbar import BottomBar
from aqt.utils import askUserDialog, downArrow, qtMenuShortcutWorkaround, tooltip, tr


class RefreshNeeded(Enum):
    NOTE_TEXT = auto()
    QUEUES = auto()
    FLAG = auto()


class ReviewerBottomBar:
    def __init__(self, reviewer: Reviewer) -> None:
        self.reviewer = reviewer


def replay_audio(card: Card, question_side: bool) -> None:
    if question_side:
        av_player.play_tags(card.question_av_tags())
    else:
        tags = card.answer_av_tags()
        if card.replay_question_audio_on_answer_side():
            tags = card.question_av_tags() + tags
        av_player.play_tags(tags)


@dataclass
class V3CardInfo:
    """2021 test scheduler info.

    next_states is copied from the top card on initialization, and can be
    mutated to alter the default scheduling.
    """

    queued_cards: QueuedCards
    next_states: NextStates

    @staticmethod
    def from_queue(queued_cards: QueuedCards) -> V3CardInfo:
        return V3CardInfo(
            queued_cards=queued_cards, next_states=queued_cards.cards[0].next_states
        )

    def top_card(self) -> QueuedCards.QueuedCard:
        return self.queued_cards.cards[0]

    def counts(self) -> tuple[int, list[int]]:
        "Returns (idx, counts)."
        counts = [
            self.queued_cards.new_count,
            self.queued_cards.learning_count,
            self.queued_cards.review_count,
        ]
        card = self.top_card()
        if card.queue == QueuedCards.NEW:
            idx = 0
        elif card.queue == QueuedCards.LEARNING:
            idx = 1
        else:
            idx = 2
        return idx, counts

    @staticmethod
    def rating_from_ease(ease: int) -> CardAnswer.Rating.V:
        if ease == 1:
            return CardAnswer.AGAIN
        elif ease == 2:
            return CardAnswer.HARD
        elif ease == 3:
            return CardAnswer.GOOD
        else:
            return CardAnswer.EASY


class Reviewer:
    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.card: Card | None = None
        self.cardQueue: list[Card] = []
        self.previous_card: Card | None = None
        self.hadCardQueue = False
        self._answeredIds: list[CardId] = []
        self._recordedAudio: str | None = None
        self.typeCorrect: str = None  # web init happens before this is set
        self.state: Literal["question", "answer", "transition", None] = None
        self._refresh_needed: RefreshNeeded | None = None
        self._v3: V3CardInfo | None = None
        self._state_mutation_key = str(random.randint(0, 2**64 - 1))
        self.bottom = BottomBar(mw, mw.bottomWeb)
        self._card_info = ReviewerCardInfo(self.mw)
        self._previous_card_info = PreviousReviewerCardInfo(self.mw)
        hooks.card_did_leech.append(self.onLeech)

    def show(self) -> None:
        if self.mw.col.sched_ver() == 1:
            self.mw.moveToState("deckBrowser")
            return
        self.mw.setStateShortcuts(self._shortcutKeys())  # type: ignore
        self.web.set_bridge_command(self._linkHandler, self)
        self.bottom.web.set_bridge_command(self._linkHandler, ReviewerBottomBar(self))
        self._state_mutation_js = self.mw.col.get_config("cardStateCustomizer")
        self._reps: int = None
        self._refresh_needed = RefreshNeeded.QUEUES
        self.refresh_if_needed()

    # this is only used by add-ons
    def lastCard(self) -> Card | None:
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                try:
                    return self.mw.col.get_card(self._answeredIds[-1])
                except TypeError:
                    # id was deleted
                    return None
        return None

    def cleanup(self) -> None:
        gui_hooks.reviewer_will_end()
        self.card = None

    def refresh_if_needed(self) -> None:
        if self._refresh_needed is RefreshNeeded.QUEUES:
            self.mw.col.reset()
            self.nextCard()
            self.mw.fade_in_webview()
            self._refresh_needed = None
        elif self._refresh_needed is RefreshNeeded.NOTE_TEXT:
            self._redraw_current_card()
            self.mw.fade_in_webview()
            self._refresh_needed = None
        elif self._refresh_needed is RefreshNeeded.FLAG:
            self.card.load()
            self._update_flag_icon()
            # for when modified in browser
            self.mw.fade_in_webview()
            self._refresh_needed = None
        elif self._refresh_needed:
            assert_exhaustive(self._refresh_needed)

    def op_executed(
        self, changes: OpChanges, handler: object | None, focused: bool
    ) -> bool:
        if handler is not self:
            if changes.study_queues:
                self._refresh_needed = RefreshNeeded.QUEUES
            elif changes.note_text:
                self._refresh_needed = RefreshNeeded.NOTE_TEXT
            elif changes.card:
                self._refresh_needed = RefreshNeeded.FLAG

        if focused and self._refresh_needed:
            self.refresh_if_needed()

        return bool(self._refresh_needed)

    def _redraw_current_card(self) -> None:
        self.card.load()
        if self.state == "answer":
            self._showAnswer()
        else:
            self._showQuestion()

    # Fetching a card
    ##########################################################################

    def nextCard(self) -> None:
        self.previous_card = self.card
        self.card = None
        self._v3 = None

        if self.mw.col.sched.version < 3:
            self._get_next_v1_v2_card()
        else:
            self._get_next_v3_card()

        self._previous_card_info.set_card(self.previous_card)
        self._card_info.set_card(self.card)

        if not self.card:
            self.mw.moveToState("overview")
            return

        if self._reps is None or self._reps % 100 == 0:
            # we recycle the webview periodically so webkit can free memory
            self._initWeb()

        self._showQuestion()

    def _get_next_v1_v2_card(self) -> None:
        if self.cardQueue:
            # undone/edited cards to show
            card = self.cardQueue.pop()
            card.start_timer()
            self.hadCardQueue = True
        else:
            if self.hadCardQueue:
                # the undone/edited cards may be sitting in the regular queue;
                # need to reset
                self.mw.col.reset()
                self.hadCardQueue = False
            card = self.mw.col.sched.getCard()
        self.card = card

    def _get_next_v3_card(self) -> None:
        assert isinstance(self.mw.col.sched, V3Scheduler)
        output = self.mw.col.sched.get_queued_cards()
        if not output.cards:
            return
        self._v3 = V3CardInfo.from_queue(output)
        self.card = Card(self.mw.col, backend_card=self._v3.top_card().card)
        self.card.start_timer()

    def get_next_states(self) -> NextStates | None:
        if v3 := self._v3:
            return v3.next_states
        else:
            return None

    def set_next_states(self, key: str, states: NextStates) -> None:
        if key != self._state_mutation_key:
            return

        if v3 := self._v3:
            v3.next_states = states

    def _run_state_mutation_hook(self) -> None:
        if self._v3 and (js := self._state_mutation_js):
            self.web.eval(
                f"anki.mutateNextCardStates('{self._state_mutation_key}', (states) => {{ {js} }})"
            )

    # Audio
    ##########################################################################

    def replayAudio(self) -> None:
        if self.state == "question":
            replay_audio(self.card, True)
        elif self.state == "answer":
            replay_audio(self.card, False)

    # Initializing the webview
    ##########################################################################

    def revHtml(self) -> str:
        extra = self.mw.col.conf.get("reviewExtra", "")
        fade = ""
        if self.mw.pm.video_driver() == VideoDriver.Software:
            fade = "<script>qFade=0;</script>"
        return f"""
<div id="_mark" hidden>&#x2605;</div>
<div id="_flag" hidden>&#x2691;</div>
{fade}
<div id="qa"></div>
{extra}
"""

    def _initWeb(self) -> None:
        self._reps = 0
        # main window
        self.web.stdHtml(
            self.revHtml(),
            css=["css/reviewer.css"],
            js=[
                "js/mathjax.js",
                "js/vendor/mathjax/tex-chtml.js",
                "js/reviewer.js",
            ],
            context=self,
        )
        # show answer / ease buttons
        self.bottom.web.show()
        self.bottom.web.stdHtml(
            self._bottomHTML(),
            css=["css/toolbar-bottom.css", "css/reviewer-bottom.css"],
            js=["js/vendor/jquery.min.js", "js/reviewer-bottom.js"],
            context=ReviewerBottomBar(self),
        )

    # Showing the question
    ##########################################################################

    def _mungeQA(self, buf: str) -> str:
        return self.typeAnsFilter(self.mw.prepare_card_text_for_display(buf))

    def _showQuestion(self) -> None:
        self._reps += 1
        self.state = "question"
        self.typedAnswer: str = None
        c = self.card
        # grab the question and play audio
        q = c.question()
        # play audio?
        if c.autoplay():
            self.web.setPlaybackRequiresGesture(False)
            sounds = c.question_av_tags()
            gui_hooks.reviewer_will_play_question_sounds(c, sounds)
            av_player.play_tags(sounds)
        else:
            self.web.setPlaybackRequiresGesture(True)
            av_player.clear_queue_and_maybe_interrupt()
            sounds = []
            gui_hooks.reviewer_will_play_question_sounds(c, sounds)
            av_player.play_tags(sounds)
        # render & update bottom
        q = self._mungeQA(q)
        q = gui_hooks.card_will_show(q, c, "reviewQuestion")
        self._run_state_mutation_hook()

        bodyclass = theme_manager.body_classes_for_card_ord(c.ord)
        a = self.mw.col.media.escape_media_filenames(c.answer())

        self.web.eval(
            f"_showQuestion({json.dumps(q)}, {json.dumps(a)}, '{bodyclass}');"
        )
        self._update_flag_icon()
        self._update_mark_icon()
        self._showAnswerButton()
        self.mw.web.setFocus()
        # user hook
        gui_hooks.reviewer_did_show_question(c)

    def autoplay(self, card: Card) -> bool:
        print("use card.autoplay() instead of reviewer.autoplay(card)")
        return card.autoplay()

    def _update_flag_icon(self) -> None:
        self.web.eval(f"_drawFlag({self.card.user_flag()});")

    def _update_mark_icon(self) -> None:
        self.web.eval(f"_drawMark({json.dumps(self.card.note().has_tag(MARKED_TAG))});")

    _drawMark = _update_mark_icon
    _drawFlag = _update_flag_icon

    # Showing the answer
    ##########################################################################

    def _showAnswer(self) -> None:
        if self.mw.state != "review":
            # showing resetRequired screen; ignore space
            return
        self.state = "answer"
        c = self.card
        a = c.answer()
        # play audio?
        if c.autoplay():
            sounds = c.answer_av_tags()
            gui_hooks.reviewer_will_play_answer_sounds(c, sounds)
            av_player.play_tags(sounds)
        else:
            av_player.clear_queue_and_maybe_interrupt()
            sounds = []
            gui_hooks.reviewer_will_play_answer_sounds(c, sounds)
            av_player.play_tags(sounds)
        a = self._mungeQA(a)
        a = gui_hooks.card_will_show(a, c, "reviewAnswer")
        # render and update bottom
        self.web.eval(f"_showAnswer({json.dumps(a)});")
        self._showEaseButtons()
        self.mw.web.setFocus()
        # user hook
        gui_hooks.reviewer_did_show_answer(c)

    # Answering a card
    ############################################################

    def _answerCard(self, ease: Literal[1, 2, 3, 4]) -> None:
        "Reschedule card and show next."
        if self.mw.state != "review":
            # showing resetRequired screen; ignore key
            return
        if self.state != "answer":
            return
        if self.mw.col.sched.answerButtons(self.card) < ease:
            return
        proceed, ease = gui_hooks.reviewer_will_answer_card(
            (True, ease), self, self.card
        )
        if not proceed:
            return

        if (v3 := self._v3) and (sched := cast(V3Scheduler, self.mw.col.sched)):
            answer = sched.build_answer(
                card=self.card, states=v3.next_states, rating=v3.rating_from_ease(ease)
            )

            def after_answer(changes: OpChanges) -> None:
                if gui_hooks.reviewer_did_answer_card.count() > 0:
                    self.card.load()
                self._after_answering(ease)
                if sched.state_is_leech(answer.new_state):
                    self.onLeech()

            self.state = "transition"
            answer_card(parent=self.mw, answer=answer).success(
                after_answer
            ).run_in_background(initiator=self)
        else:
            self.mw.col.sched.answerCard(self.card, ease)
            self._after_answering(ease)

    def _after_answering(self, ease: Literal[1, 2, 3, 4]) -> None:
        gui_hooks.reviewer_did_answer_card(self, self.card, ease)
        self._answeredIds.append(self.card.id)
        self.mw.autosave()
        if not self.check_timebox():
            self.nextCard()

    # Handlers
    ############################################################

    def _shortcutKeys(
        self,
    ) -> Sequence[Union[tuple[str, Callable], tuple[Qt.Key, Callable]]]:
        return [
            ("e", self.mw.onEditCurrent),
            (" ", self.onEnterKey),
            (Qt.Key.Key_Return, self.onEnterKey),
            (Qt.Key.Key_Enter, self.onEnterKey),
            ("m", self.showContextMenu),
            ("r", self.replayAudio),
            (Qt.Key.Key_F5, self.replayAudio),
            *(
                (f"Ctrl+{flag.index}", self.set_flag_func(flag.index))
                for flag in self.mw.flags.all()
            ),
            ("*", self.toggle_mark_on_current_note),
            ("=", self.bury_current_note),
            ("-", self.bury_current_card),
            ("!", self.suspend_current_note),
            ("@", self.suspend_current_card),
            ("Ctrl+Alt+N", self.forget_current_card),
            ("Ctrl+Alt+E", self.on_create_copy),
            ("Ctrl+Delete", self.delete_current_note),
            ("Ctrl+Shift+D", self.on_set_due),
            ("v", self.onReplayRecorded),
            ("Shift+v", self.onRecordVoice),
            ("o", self.onOptions),
            ("i", self.on_card_info),
            ("Ctrl+Alt+i", self.on_previous_card_info),
            ("1", lambda: self._answerCard(1)),
            ("2", lambda: self._answerCard(2)),
            ("3", lambda: self._answerCard(3)),
            ("4", lambda: self._answerCard(4)),
            ("5", self.on_pause_audio),
            ("6", self.on_seek_backward),
            ("7", self.on_seek_forward),
        ]

    def on_pause_audio(self) -> None:
        av_player.toggle_pause()

    seek_secs = 5

    def on_seek_backward(self) -> None:
        av_player.seek_relative(-self.seek_secs)

    def on_seek_forward(self) -> None:
        av_player.seek_relative(self.seek_secs)

    def onEnterKey(self) -> None:
        if self.state == "question":
            self._getTypedAnswer()
        elif self.state == "answer":
            self.bottom.web.evalWithCallback(
                "selectedAnswerButton()", self._onAnswerButton
            )

    def _onAnswerButton(self, val: str) -> None:
        # button selected?
        if val and val in "1234":
            val2: Literal[1, 2, 3, 4] = int(val)  # type: ignore
            self._answerCard(val2)
        else:
            self._answerCard(self._defaultEase())

    def _linkHandler(self, url: str) -> None:
        if url == "ans":
            self._getTypedAnswer()
        elif url.startswith("ease"):
            val: Literal[1, 2, 3, 4] = int(url[4:])  # type: ignore
            self._answerCard(val)
        elif url == "edit":
            self.mw.onEditCurrent()
        elif url == "more":
            self.showContextMenu()
        elif url.startswith("play:"):
            play_clicked_audio(url, self.card)
        else:
            print("unrecognized anki link:", url)

    # Type in the answer
    ##########################################################################

    typeAnsPat = r"\[\[type:(.+?)\]\]"

    def typeAnsFilter(self, buf: str) -> str:
        if self.state == "question":
            return self.typeAnsQuestionFilter(buf)
        else:
            return self.typeAnsAnswerFilter(buf)

    def typeAnsQuestionFilter(self, buf: str) -> str:
        self.typeCorrect = None
        clozeIdx = None
        m = re.search(self.typeAnsPat, buf)
        if not m:
            return buf
        fld = m.group(1)
        # if it's a cloze, extract data
        if fld.startswith("cloze:"):
            # get field and cloze position
            clozeIdx = self.card.ord + 1
            fld = fld.split(":")[1]
        # loop through fields for a match
        for f in self.card.note_type()["flds"]:
            if f["name"] == fld:
                self.typeCorrect = self.card.note()[f["name"]]
                if clozeIdx:
                    # narrow to cloze
                    self.typeCorrect = self._contentForCloze(self.typeCorrect, clozeIdx)
                self.typeFont = f["font"]
                self.typeSize = f["size"]
                break
        if not self.typeCorrect:
            if self.typeCorrect is None:
                if clozeIdx:
                    warn = tr.studying_please_run_toolsempty_cards()
                else:
                    warn = tr.studying_type_answer_unknown_field(val=fld)
                return re.sub(self.typeAnsPat, warn, buf)
            else:
                # empty field, remove type answer pattern
                return re.sub(self.typeAnsPat, "", buf)
        return re.sub(
            self.typeAnsPat,
            f"""
<center>
<input type=text id=typeans onkeypress="_typeAnsPress();"
   style="font-family: '{self.typeFont}'; font-size: {self.typeSize}px;">
</center>
""",
            buf,
        )

    def typeAnsAnswerFilter(self, buf: str) -> str:
        if not self.typeCorrect:
            return re.sub(self.typeAnsPat, "", buf)
        origSize = len(buf)
        buf = buf.replace("<hr id=answer>", "")
        hadHR = len(buf) != origSize
        # munge correct value
        cor = self.mw.col.media.strip(self.typeCorrect)
        cor = re.sub("(\n|<br ?/?>|</?div>)+", " ", cor)
        cor = strip_html(cor)
        # ensure we don't chomp multiple whitespace
        cor = cor.replace(" ", "&nbsp;")
        cor = html.unescape(cor)
        cor = cor.replace("\xa0", " ")
        cor = cor.strip()
        given = self.typedAnswer
        # compare with typed answer
        res = self.correct(given, cor, showBad=False)
        # and update the type answer area
        def repl(match: Match) -> str:
            # can't pass a string in directly, and can't use re.escape as it
            # escapes too much
            s = """
<span style="font-family: '{}'; font-size: {}px">{}</span>""".format(
                self.typeFont,
                self.typeSize,
                res,
            )
            if hadHR:
                # a hack to ensure the q/a separator falls before the answer
                # comparison when user is using {{FrontSide}}
                s = f"<hr id=answer>{s}"
            return s

        return re.sub(self.typeAnsPat, repl, buf)

    def _contentForCloze(self, txt: str, idx: int) -> str:
        matches = re.findall(r"\{\{c%s::(.+?)\}\}" % idx, txt, re.DOTALL)
        if not matches:
            return None

        def noHint(txt: str) -> str:
            if "::" in txt:
                return txt.split("::")[0]
            return txt

        matches = [noHint(txt) for txt in matches]
        uniqMatches = set(matches)
        if len(uniqMatches) == 1:
            txt = matches[0]
        else:
            txt = ", ".join(matches)
        return txt

    def tokenizeComparison(
        self, given: str, correct: str
    ) -> tuple[list[tuple[bool, str]], list[tuple[bool, str]]]:
        # compare in NFC form so accents appear correct
        given = ucd.normalize("NFC", given)
        correct = ucd.normalize("NFC", correct)
        s = difflib.SequenceMatcher(None, given, correct, autojunk=False)
        givenElems: list[tuple[bool, str]] = []
        correctElems: list[tuple[bool, str]] = []
        givenPoint = 0
        correctPoint = 0
        offby = 0

        def logBad(old: int, new: int, s: str, array: list[tuple[bool, str]]) -> None:
            if old != new:
                array.append((False, s[old:new]))

        def logGood(
            start: int, cnt: int, s: str, array: list[tuple[bool, str]]
        ) -> None:
            if cnt:
                array.append((True, s[start : start + cnt]))

        for x, y, cnt in s.get_matching_blocks():
            # if anything was missed in correct, pad given
            if cnt and y - offby > x:
                givenElems.append((False, "-" * (y - x - offby)))
                offby = y - x
            # log any proceeding bad elems
            logBad(givenPoint, x, given, givenElems)
            logBad(correctPoint, y, correct, correctElems)
            givenPoint = x + cnt
            correctPoint = y + cnt
            # log the match
            logGood(x, cnt, given, givenElems)
            logGood(y, cnt, correct, correctElems)
        return givenElems, correctElems

    def correct(self, given: str, correct: str, showBad: bool = True) -> str:
        "Diff-corrects the typed-in answer."
        givenElems, correctElems = self.tokenizeComparison(given, correct)

        def good(s: str) -> str:
            return f"<span class=typeGood>{html.escape(s)}</span>"

        def bad(s: str) -> str:
            return f"<span class=typeBad>{html.escape(s)}</span>"

        def missed(s: str) -> str:
            return f"<span class=typeMissed>{html.escape(s)}</span>"

        if given == correct:
            res = good(given)
        else:
            res = ""
            for ok, txt in givenElems:
                txt = self._noLoneMarks(txt)
                if ok:
                    res += good(txt)
                else:
                    res += bad(txt)
            res += "<br><span id=typearrow>&darr;</span><br>"
            for ok, txt in correctElems:
                txt = self._noLoneMarks(txt)
                if ok:
                    res += good(txt)
                else:
                    res += missed(txt)
        res = f"<div><code id=typeans>{res}</code></div>"
        return res

    def _noLoneMarks(self, s: str) -> str:
        # ensure a combining character at the start does not join to
        # previous text
        if s and ucd.category(s[0]).startswith("M"):
            return f"\xa0{s}"
        return s

    def _getTypedAnswer(self) -> None:
        self.web.evalWithCallback("getTypedAnswer();", self._onTypedAnswer)

    def _onTypedAnswer(self, val: None) -> None:
        self.typedAnswer = val or ""
        self._showAnswer()

    # Bottom bar
    ##########################################################################

    def _bottomHTML(self) -> str:
        return """
<center id=outer>
<table id=innertable width=100%% cellspacing=0 cellpadding=0>
<tr>
<td align=left width=50 valign=top class=stat>
<br>
<button title="%(editkey)s" onclick="pycmd('edit');">%(edit)s</button></td>
<td align=center valign=top id=middle>
</td>
<td width=50 align=right valign=top class=stat><span id=time class=stattxt>
</span><br>
<button onclick="pycmd('more');">%(more)s %(downArrow)s</button>
</td>
</tr>
</table>
</center>
<script>
time = %(time)d;
</script>
""" % dict(
            rem=self._remaining(),
            edit=tr.studying_edit(),
            editkey=tr.actions_shortcut_key(val="E"),
            more=tr.studying_more(),
            downArrow=downArrow(),
            time=self.card.time_taken() // 1000,
        )

    def _showAnswerButton(self) -> None:
        middle = """
<span class=stattxt>{}</span><br>
<button title="{}" id="ansbut" class="focus" onclick='pycmd("ans");'>{}</button>""".format(
            self._remaining(),
            tr.actions_shortcut_key(val=tr.studying_space()),
            tr.studying_show_answer(),
        )
        # wrap it in a table so it has the same top margin as the ease buttons
        middle = (
            "<table cellpadding=0><tr><td class=stat2 align=center>%s</td></tr></table>"
            % middle
        )
        if self.card.should_show_timer():
            maxTime = self.card.time_limit() / 1000
        else:
            maxTime = 0
        self.bottom.web.eval("showQuestion(%s,%d);" % (json.dumps(middle), maxTime))
        self.bottom.web.adjustHeightToFit()

    def _showEaseButtons(self) -> None:
        middle = self._answerButtons()
        self.bottom.web.eval(f"showAnswer({json.dumps(middle)});")

    def _remaining(self) -> str:
        if not self.mw.col.conf["dueCounts"]:
            return ""

        counts: list[Union[int, str]]
        if v3 := self._v3:
            idx, counts_ = v3.counts()
            counts = cast(list[Union[int, str]], counts_)
        else:
            # v1/v2 scheduler
            if self.hadCardQueue:
                # if it's come from the undo queue, don't count it separately
                counts = list(self.mw.col.sched.counts())
            else:
                counts = list(self.mw.col.sched.counts(self.card))
            idx = self.mw.col.sched.countIdx(self.card)

        counts[idx] = f"<u>{counts[idx]}</u>"

        return f"""
<span class=new-count>{counts[0]}</span> +
<span class=learn-count>{counts[1]}</span> +
<span class=review-count>{counts[2]}</span>
"""

    def _defaultEase(self) -> Literal[2, 3]:
        if self.mw.col.sched.answerButtons(self.card) == 4:
            return 3
        else:
            return 2

    def _answerButtonList(self) -> tuple[tuple[int, str], ...]:
        button_count = self.mw.col.sched.answerButtons(self.card)
        if button_count == 2:
            buttons_tuple: tuple[tuple[int, str], ...] = (
                (1, tr.studying_again()),
                (2, tr.studying_good()),
            )
        elif button_count == 3:
            buttons_tuple = (
                (1, tr.studying_again()),
                (2, tr.studying_good()),
                (3, tr.studying_easy()),
            )
        else:
            buttons_tuple = (
                (1, tr.studying_again()),
                (2, tr.studying_hard()),
                (3, tr.studying_good()),
                (4, tr.studying_easy()),
            )
        buttons_tuple = gui_hooks.reviewer_will_init_answer_buttons(
            buttons_tuple, self, self.card
        )
        return buttons_tuple

    def _answerButtons(self) -> str:
        default = self._defaultEase()

        if v3 := self._v3:
            assert isinstance(self.mw.col.sched, V3Scheduler)
            labels = self.mw.col.sched.describe_next_states(v3.next_states)
        else:
            labels = None

        def but(i: int, label: str) -> str:
            if i == default:
                extra = """id="defease" class="focus" """
            else:
                extra = ""
            due = self._buttonTime(i, v3_labels=labels)
            return """
<td align=center>%s<button %s title="%s" data-ease="%s" onclick='pycmd("ease%d");'>\
%s</button></td>""" % (
                due,
                extra,
                tr.actions_shortcut_key(val=i),
                i,
                i,
                label,
            )

        buf = "<center><table cellpading=0 cellspacing=0><tr>"
        for ease, label in self._answerButtonList():
            buf += but(ease, label)
        buf += "</tr></table>"
        return buf

    def _buttonTime(self, i: int, v3_labels: Sequence[str] | None = None) -> str:
        if not self.mw.col.conf["estTimes"]:
            return "<div class=spacer></div>"
        if v3_labels:
            txt = v3_labels[i - 1]
        else:
            txt = self.mw.col.sched.nextIvlStr(self.card, i, True) or "&nbsp;"
        return f"<span class=nobold>{txt}</span><br>"

    # Leeches
    ##########################################################################

    def onLeech(self, card: Card | None = None) -> None:
        # for now
        s = tr.studying_card_was_a_leech()
        # v3 scheduler doesn't report this
        if card and card.queue < 0:
            s += f" {tr.studying_it_has_been_suspended()}"
        tooltip(s)

    # Timebox
    ##########################################################################

    def check_timebox(self) -> bool:
        "True if answering should be aborted."
        elapsed = self.mw.col.timeboxReached()
        if elapsed:
            assert not isinstance(elapsed, bool)
            part1 = tr.studying_card_studied_in(count=elapsed[1])
            mins = int(round(elapsed[0] / 60))
            part2 = tr.studying_minute(count=mins)
            fin = tr.studying_finish()
            diag = askUserDialog(f"{part1} {part2}", [tr.studying_continue(), fin])
            diag.setIcon(QMessageBox.Icon.Information)
            if diag.run() == fin:
                self.mw.moveToState("deckBrowser")
                return True
            self.mw.col.startTimebox()
        return False

    # Context menu
    ##########################################################################

    # note the shortcuts listed here also need to be defined above
    def _contextMenu(self) -> list[Any]:
        currentFlag = self.card and self.card.user_flag()
        opts = [
            [
                tr.studying_flag_card(),
                [
                    [
                        flag.label,
                        f"Ctrl+{flag.index}",
                        self.set_flag_func(flag.index),
                        dict(checked=currentFlag == flag.index),
                    ]
                    for flag in self.mw.flags.all()
                ],
            ],
            [tr.studying_bury_card(), "-", self.bury_current_card],
            [tr.actions_forget_card(), "Ctrl+Alt+N", self.forget_current_card],
            [
                tr.actions_with_ellipsis(action=tr.actions_set_due_date()),
                "Ctrl+Shift+D",
                self.on_set_due,
            ],
            [tr.actions_suspend_card(), "@", self.suspend_current_card],
            [tr.actions_options(), "O", self.onOptions],
            [tr.actions_card_info(), "I", self.on_card_info],
            [tr.actions_previous_card_info(), "Ctrl+Alt+I", self.on_previous_card_info],
            None,
            [tr.studying_mark_note(), "*", self.toggle_mark_on_current_note],
            [tr.studying_bury_note(), "=", self.bury_current_note],
            [tr.studying_suspend_note(), "!", self.suspend_current_note],
            [
                tr.actions_with_ellipsis(action=tr.actions_create_copy()),
                "Ctrl+Alt+E",
                self.on_create_copy,
            ],
            [tr.studying_delete_note(), "Ctrl+Delete", self.delete_current_note],
            None,
            [tr.actions_replay_audio(), "R", self.replayAudio],
            [tr.studying_pause_audio(), "5", self.on_pause_audio],
            [tr.studying_audio_5s(), "6", self.on_seek_backward],
            [tr.studying_audio_and5s(), "7", self.on_seek_forward],
            [tr.studying_record_own_voice(), "Shift+V", self.onRecordVoice],
            [tr.studying_replay_own_voice(), "V", self.onReplayRecorded],
        ]
        return opts

    def showContextMenu(self) -> None:
        opts = self._contextMenu()
        m = QMenu(self.mw)
        self._addMenuItems(m, opts)

        gui_hooks.reviewer_will_show_context_menu(self, m)
        qtMenuShortcutWorkaround(m)
        m.popup(QCursor.pos())

    def _addMenuItems(self, m: QMenu, rows: Sequence) -> None:
        for row in rows:
            if not row:
                m.addSeparator()
                continue
            if len(row) == 2:
                subm = m.addMenu(row[0])
                self._addMenuItems(subm, row[1])
                qtMenuShortcutWorkaround(subm)
                continue
            if len(row) == 4:
                label, scut, func, opts = row
            else:
                label, scut, func = row
                opts = {}
            a = m.addAction(label)
            if scut:
                a.setShortcut(QKeySequence(scut))
            if opts.get("checked"):
                a.setCheckable(True)
                a.setChecked(True)
            qconnect(a.triggered, func)

    def onOptions(self) -> None:
        confirm_deck_then_display_options(self.card)

    def on_previous_card_info(self) -> None:
        self._previous_card_info.toggle()

    def on_card_info(self) -> None:
        self._card_info.toggle()

    def set_flag_on_current_card(self, desired_flag: int) -> None:
        # need to toggle off?
        if self.card.user_flag() == desired_flag:
            flag = 0
        else:
            flag = desired_flag

        set_card_flag(parent=self.mw, card_ids=[self.card.id], flag=flag).success(
            lambda _: None
        ).run_in_background()

    def set_flag_func(self, desired_flag: int) -> Callable:
        return lambda: self.set_flag_on_current_card(desired_flag)

    def toggle_mark_on_current_note(self) -> None:
        def redraw_mark(out: OpChangesWithCount) -> None:
            self.card.load()
            self._update_mark_icon()

        note = self.card.note()
        if note.has_tag(MARKED_TAG):
            remove_tags_from_notes(
                parent=self.mw, note_ids=[note.id], space_separated_tags=MARKED_TAG
            ).success(redraw_mark).run_in_background(initiator=self)
        else:
            add_tags_to_notes(
                parent=self.mw,
                note_ids=[note.id],
                space_separated_tags=MARKED_TAG,
            ).success(redraw_mark).run_in_background(initiator=self)

    def on_set_due(self) -> None:
        if self.mw.state != "review" or not self.card:
            return

        if op := set_due_date_dialog(
            parent=self.mw,
            card_ids=[self.card.id],
            config_key=Config.String.SET_DUE_REVIEWER,
        ):
            op.run_in_background()

    def suspend_current_note(self) -> None:
        suspend_note(
            parent=self.mw,
            note_ids=[self.card.nid],
        ).success(lambda _: tooltip(tr.studying_note_suspended())).run_in_background()

    def suspend_current_card(self) -> None:
        suspend_cards(
            parent=self.mw,
            card_ids=[self.card.id],
        ).success(lambda _: tooltip(tr.studying_card_suspended())).run_in_background()

    def bury_current_note(self) -> None:
        bury_notes(parent=self.mw, note_ids=[self.card.nid],).success(
            lambda res: tooltip(tr.studying_cards_buried(count=res.count))
        ).run_in_background()

    def bury_current_card(self) -> None:
        bury_cards(parent=self.mw, card_ids=[self.card.id],).success(
            lambda res: tooltip(tr.studying_cards_buried(count=res.count))
        ).run_in_background()

    def forget_current_card(self) -> None:
        if op := forget_cards(
            parent=self.mw,
            card_ids=[self.card.id],
            context=ScheduleCardsAsNew.Context.REVIEWER,
        ):
            op.run_in_background()

    def on_create_copy(self) -> None:
        if self.card:
            aqt.dialogs.open("AddCards", self.mw).set_note(
                self.card.note(), self.card.did
            )

    def delete_current_note(self) -> None:
        # need to check state because the shortcut is global to the main
        # window
        if self.mw.state != "review" or not self.card:
            return

        remove_notes(parent=self.mw, note_ids=[self.card.nid]).run_in_background()

    def onRecordVoice(self) -> None:
        def after_record(path: str) -> None:
            self._recordedAudio = path
            self.onReplayRecorded()

        record_audio(self.mw, self.mw, False, after_record)

    def onReplayRecorded(self) -> None:
        if not self._recordedAudio:
            tooltip(tr.studying_you_havent_recorded_your_voice_yet())
            return
        av_player.play_file(self._recordedAudio)

    # legacy

    onBuryCard = bury_current_card
    onBuryNote = bury_current_note
    onSuspend = suspend_current_note
    onSuspendCard = suspend_current_card
    onDelete = delete_current_note
    onMark = toggle_mark_on_current_note
    setFlag = set_flag_on_current_card
