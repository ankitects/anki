# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import difflib
import html
import json
import re
import unicodedata as ucd
from typing import Any, Callable, List, Match, Optional, Sequence, Tuple, Union

from PyQt5.QtCore import Qt

from anki import hooks
from anki.cards import Card
from anki.collection import Config
from anki.utils import stripHTML
from aqt import AnkiQt, gui_hooks
from aqt.profiles import VideoDriver
from aqt.qt import *
from aqt.scheduling import set_due_date_dialog
from aqt.sound import av_player, play_clicked_audio, record_audio
from aqt.theme import theme_manager
from aqt.toolbar import BottomBar
from aqt.utils import (
    TR,
    askUserDialog,
    downArrow,
    qtMenuShortcutWorkaround,
    tooltip,
    tr,
)
from aqt.webview import AnkiWebView


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


class Reviewer:
    "Manage reviews.  Maintains a separate state."

    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.card: Optional[Card] = None
        self.cardQueue: List[Card] = []
        self.hadCardQueue = False
        self._answeredIds: List[int] = []
        self._recordedAudio: Optional[str] = None
        self.typeCorrect: str = None  # web init happens before this is set
        self.state: Optional[str] = None
        self.bottom = BottomBar(mw, mw.bottomWeb)
        hooks.card_did_leech.append(self.onLeech)

    def show(self) -> None:
        self.mw.col.reset()
        self.mw.setStateShortcuts(self._shortcutKeys())  # type: ignore
        self.web.set_bridge_command(self._linkHandler, self)
        self.bottom.web.set_bridge_command(self._linkHandler, ReviewerBottomBar(self))
        self._reps: int = None
        self.nextCard()

    def lastCard(self) -> Optional[Card]:
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                try:
                    return self.mw.col.getCard(self._answeredIds[-1])
                except TypeError:
                    # id was deleted
                    return None
        return None

    def cleanup(self) -> None:
        gui_hooks.reviewer_will_end()
        self.card = None

    # Fetching a card
    ##########################################################################

    def nextCard(self) -> None:
        elapsed = self.mw.col.timeboxReached()
        if elapsed:
            assert not isinstance(elapsed, bool)
            part1 = tr(TR.STUDYING_CARD_STUDIED_IN, count=elapsed[1])
            mins = int(round(elapsed[0] / 60))
            part2 = tr(TR.STUDYING_MINUTE, count=mins)
            fin = tr(TR.STUDYING_FINISH)
            diag = askUserDialog(f"{part1} {part2}", [tr(TR.STUDYING_CONTINUE), fin])
            diag.setIcon(QMessageBox.Information)
            if diag.run() == fin:
                return self.mw.moveToState("deckBrowser")
            self.mw.col.startTimebox()
        if self.cardQueue:
            # undone/edited cards to show
            c = self.cardQueue.pop()
            c.startTimer()
            self.hadCardQueue = True
        else:
            if self.hadCardQueue:
                # the undone/edited cards may be sitting in the regular queue;
                # need to reset
                self.mw.col.reset()
                self.hadCardQueue = False
            c = self.mw.col.sched.getCard()
        self.card = c
        if not c:
            self.mw.moveToState("overview")
            return
        if self._reps is None or self._reps % 100 == 0:
            # we recycle the webview periodically so webkit can free memory
            self._initWeb()
        self._showQuestion()

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
<div id=_mark>&#x2605;</div>
<div id=_flag>&#x2691;</div>
{fade}
<div id=qa></div>
{extra}
"""

    def _initWeb(self) -> None:
        self._reps = 0
        # main window
        self.web.stdHtml(
            self.revHtml(),
            css=["css/reviewer.css"],
            js=[
                "js/vendor/jquery.min.js",
                "js/vendor/css_browser_selector.min.js",
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
        q = c.q()
        # play audio?
        if c.autoplay():
            AnkiWebView.setPlaybackRequiresGesture(False)
            sounds = c.question_av_tags()
            gui_hooks.reviewer_will_play_question_sounds(c, sounds)
            av_player.play_tags(sounds)
        else:
            AnkiWebView.setPlaybackRequiresGesture(True)
            av_player.clear_queue_and_maybe_interrupt()
            sounds = []
            gui_hooks.reviewer_will_play_question_sounds(c, sounds)
            av_player.play_tags(sounds)
        # render & update bottom
        q = self._mungeQA(q)
        q = gui_hooks.card_will_show(q, c, "reviewQuestion")

        bodyclass = theme_manager.body_classes_for_card_ord(c.ord)

        self.web.eval(f"_showQuestion({json.dumps(q)},'{bodyclass}');")
        self._drawFlag()
        self._drawMark()
        self._showAnswerButton()
        self.mw.web.setFocus()
        # user hook
        gui_hooks.reviewer_did_show_question(c)

    def autoplay(self, card: Card) -> bool:
        print("use card.autoplay() instead of reviewer.autoplay(card)")
        return card.autoplay()

    def _drawFlag(self) -> None:
        self.web.eval(f"_drawFlag({self.card.userFlag()});")

    def _drawMark(self) -> None:
        self.web.eval(f"_drawMark({json.dumps(self.card.note().hasTag('marked'))});")

    # Showing the answer
    ##########################################################################

    def _showAnswer(self) -> None:
        if self.mw.state != "review":
            # showing resetRequired screen; ignore space
            return
        self.state = "answer"
        c = self.card
        a = c.a()
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

    def _answerCard(self, ease: int) -> None:
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
        self.mw.col.sched.answerCard(self.card, ease)
        gui_hooks.reviewer_did_answer_card(self, self.card, ease)
        self._answeredIds.append(self.card.id)
        self.mw.autosave()
        self.nextCard()

    # Handlers
    ############################################################

    def _shortcutKeys(
        self,
    ) -> List[Union[Tuple[str, Callable], Tuple[Qt.Key, Callable]]]:
        return [
            ("e", self.mw.onEditCurrent),
            (" ", self.onEnterKey),
            (Qt.Key_Return, self.onEnterKey),
            (Qt.Key_Enter, self.onEnterKey),
            ("m", self.showContextMenu),
            ("r", self.replayAudio),
            (Qt.Key_F5, self.replayAudio),
            ("Ctrl+1", lambda: self.setFlag(1)),
            ("Ctrl+2", lambda: self.setFlag(2)),
            ("Ctrl+3", lambda: self.setFlag(3)),
            ("Ctrl+4", lambda: self.setFlag(4)),
            ("*", self.onMark),
            ("=", self.onBuryNote),
            ("-", self.onBuryCard),
            ("!", self.onSuspend),
            ("@", self.onSuspendCard),
            ("Ctrl+Delete", self.onDelete),
            ("Ctrl+Shift+D", self.on_set_due),
            ("v", self.onReplayRecorded),
            ("Shift+v", self.onRecordVoice),
            ("o", self.onOptions),
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
            self._answerCard(int(val))
        else:
            self._answerCard(self._defaultEase())

    def _linkHandler(self, url: str) -> None:
        if url == "ans":
            self._getTypedAnswer()
        elif url.startswith("ease"):
            self._answerCard(int(url[4:]))
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
        for f in self.card.model()["flds"]:
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
                    warn = tr(TR.STUDYING_PLEASE_RUN_TOOLSEMPTY_CARDS)
                else:
                    warn = tr(TR.STUDYING_TYPE_ANSWER_UNKNOWN_FIELD, val=fld)
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
        cor = stripHTML(cor)
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
<span style="font-family: '%s'; font-size: %spx">%s</span>""" % (
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
    ) -> Tuple[List[Tuple[bool, str]], List[Tuple[bool, str]]]:
        # compare in NFC form so accents appear correct
        given = ucd.normalize("NFC", given)
        correct = ucd.normalize("NFC", correct)
        s = difflib.SequenceMatcher(None, given, correct, autojunk=False)
        givenElems: List[Tuple[bool, str]] = []
        correctElems: List[Tuple[bool, str]] = []
        givenPoint = 0
        correctPoint = 0
        offby = 0

        def logBad(old: int, new: int, s: str, array: List[Tuple[bool, str]]) -> None:
            if old != new:
                array.append((False, s[old:new]))

        def logGood(
            start: int, cnt: int, s: str, array: List[Tuple[bool, str]]
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
        self.web.evalWithCallback("typeans ? typeans.value : null", self._onTypedAnswer)

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
            edit=tr(TR.STUDYING_EDIT),
            editkey=tr(TR.ACTIONS_SHORTCUT_KEY, val="E"),
            more=tr(TR.STUDYING_MORE),
            downArrow=downArrow(),
            time=self.card.timeTaken() // 1000,
        )

    def _showAnswerButton(self) -> None:
        middle = """
<span class=stattxt>%s</span><br>
<button title="%s" id="ansbut" class="focus" onclick='pycmd("ans");'>%s</button>""" % (
            self._remaining(),
            tr(TR.ACTIONS_SHORTCUT_KEY, val=tr(TR.STUDYING_SPACE)),
            tr(TR.STUDYING_SHOW_ANSWER),
        )
        # wrap it in a table so it has the same top margin as the ease buttons
        middle = (
            "<table cellpadding=0><tr><td class=stat2 align=center>%s</td></tr></table>"
            % middle
        )
        if self.card.shouldShowTimer():
            maxTime = self.card.timeLimit() / 1000
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
        if self.hadCardQueue:
            # if it's come from the undo queue, don't count it separately
            counts: List[Union[int, str]] = list(self.mw.col.sched.counts())
        else:
            counts = list(self.mw.col.sched.counts(self.card))
        idx = self.mw.col.sched.countIdx(self.card)
        counts[idx] = f"<u>{counts[idx]}</u>"
        space = " + "
        ctxt = f"<span class=new-count>{counts[0]}</span>"
        ctxt += f"{space}<span class=learn-count>{counts[1]}</span>"
        ctxt += f"{space}<span class=review-count>{counts[2]}</span>"
        return ctxt

    def _defaultEase(self) -> int:
        if self.mw.col.sched.answerButtons(self.card) == 4:
            return 3
        else:
            return 2

    def _answerButtonList(self) -> Tuple[Tuple[int, str], ...]:
        button_count = self.mw.col.sched.answerButtons(self.card)
        if button_count == 2:
            buttons_tuple: Tuple[Tuple[int, str], ...] = (
                (1, tr(TR.STUDYING_AGAIN)),
                (2, tr(TR.STUDYING_GOOD)),
            )
        elif button_count == 3:
            buttons_tuple = (
                (1, tr(TR.STUDYING_AGAIN)),
                (2, tr(TR.STUDYING_GOOD)),
                (3, tr(TR.STUDYING_EASY)),
            )
        else:
            buttons_tuple = (
                (1, tr(TR.STUDYING_AGAIN)),
                (2, tr(TR.STUDYING_HARD)),
                (3, tr(TR.STUDYING_GOOD)),
                (4, tr(TR.STUDYING_EASY)),
            )
        buttons_tuple = gui_hooks.reviewer_will_init_answer_buttons(
            buttons_tuple, self, self.card
        )
        return buttons_tuple

    def _answerButtons(self) -> str:
        default = self._defaultEase()

        def but(i: int, label: str) -> str:
            if i == default:
                extra = """id="defease" class="focus" """
            else:
                extra = ""
            due = self._buttonTime(i)
            return """
<td align=center>%s<button %s title="%s" data-ease="%s" onclick='pycmd("ease%d");'>\
%s</button></td>""" % (
                due,
                extra,
                tr(TR.ACTIONS_SHORTCUT_KEY, val=i),
                i,
                i,
                label,
            )

        buf = "<center><table cellpading=0 cellspacing=0><tr>"
        for ease, label in self._answerButtonList():
            buf += but(ease, label)
        buf += "</tr></table>"
        script = """
<script>$(function () { $("#defease").focus(); });</script>"""
        return buf + script

    def _buttonTime(self, i: int) -> str:
        if not self.mw.col.conf["estTimes"]:
            return "<div class=spacer></div>"
        txt = self.mw.col.sched.nextIvlStr(self.card, i, True) or "&nbsp;"
        return f"<span class=nobold>{txt}</span><br>"

    # Leeches
    ##########################################################################

    def onLeech(self, card: Card) -> None:
        # for now
        s = tr(TR.STUDYING_CARD_WAS_A_LEECH)
        if card.queue < 0:
            s += f" {tr(TR.STUDYING_IT_HAS_BEEN_SUSPENDED)}"
        tooltip(s)

    # Context menu
    ##########################################################################

    # note the shortcuts listed here also need to be defined above
    def _contextMenu(self) -> List[Any]:
        currentFlag = self.card and self.card.userFlag()
        opts = [
            [
                tr(TR.STUDYING_FLAG_CARD),
                [
                    [
                        tr(TR.ACTIONS_RED_FLAG),
                        "Ctrl+1",
                        lambda: self.setFlag(1),
                        dict(checked=currentFlag == 1),
                    ],
                    [
                        tr(TR.ACTIONS_ORANGE_FLAG),
                        "Ctrl+2",
                        lambda: self.setFlag(2),
                        dict(checked=currentFlag == 2),
                    ],
                    [
                        tr(TR.ACTIONS_GREEN_FLAG),
                        "Ctrl+3",
                        lambda: self.setFlag(3),
                        dict(checked=currentFlag == 3),
                    ],
                    [
                        tr(TR.ACTIONS_BLUE_FLAG),
                        "Ctrl+4",
                        lambda: self.setFlag(4),
                        dict(checked=currentFlag == 4),
                    ],
                ],
            ],
            [tr(TR.STUDYING_MARK_NOTE), "*", self.onMark],
            [tr(TR.STUDYING_BURY_CARD), "-", self.onBuryCard],
            [tr(TR.STUDYING_BURY_NOTE), "=", self.onBuryNote],
            [tr(TR.ACTIONS_SET_DUE_DATE), "Ctrl+Shift+D", self.on_set_due],
            [tr(TR.ACTIONS_SUSPEND_CARD), "@", self.onSuspendCard],
            [tr(TR.STUDYING_SUSPEND_NOTE), "!", self.onSuspend],
            [tr(TR.STUDYING_DELETE_NOTE), "Ctrl+Delete", self.onDelete],
            [tr(TR.ACTIONS_OPTIONS), "O", self.onOptions],
            None,
            [tr(TR.ACTIONS_REPLAY_AUDIO), "R", self.replayAudio],
            [tr(TR.STUDYING_PAUSE_AUDIO), "5", self.on_pause_audio],
            [tr(TR.STUDYING_AUDIO_5S), "6", self.on_seek_backward],
            [tr(TR.STUDYING_AUDIO_AND5S), "7", self.on_seek_forward],
            [tr(TR.STUDYING_RECORD_OWN_VOICE), "Shift+V", self.onRecordVoice],
            [tr(TR.STUDYING_REPLAY_OWN_VOICE), "V", self.onReplayRecorded],
        ]
        return opts

    def showContextMenu(self) -> None:
        opts = self._contextMenu()
        m = QMenu(self.mw)
        self._addMenuItems(m, opts)

        gui_hooks.reviewer_will_show_context_menu(self, m)
        qtMenuShortcutWorkaround(m)
        m.exec_(QCursor.pos())

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
        self.mw.onDeckConf(self.mw.col.decks.get(self.card.odid or self.card.did))

    def setFlag(self, flag: int) -> None:
        # need to toggle off?
        if self.card.userFlag() == flag:
            flag = 0
        self.card.setUserFlag(flag)
        self.card.flush()
        self._drawFlag()

    def onMark(self) -> None:
        f = self.card.note()
        if f.hasTag("marked"):
            f.delTag("marked")
        else:
            f.addTag("marked")
        f.flush()
        self._drawMark()

    def on_set_due(self) -> None:
        if self.mw.state != "review" or not self.card:
            return

        set_due_date_dialog(
            mw=self.mw,
            parent=self.mw,
            card_ids=[self.card.id],
            default_key=Config.String.SET_DUE_REVIEWER,
            on_done=self.mw.reset,
        )

    def onSuspend(self) -> None:
        self.mw.checkpoint(tr(TR.STUDYING_SUSPEND))
        self.mw.col.sched.suspend_cards([c.id for c in self.card.note().cards()])
        tooltip(tr(TR.STUDYING_NOTE_SUSPENDED))
        self.mw.reset()

    def onSuspendCard(self) -> None:
        self.mw.checkpoint(tr(TR.STUDYING_SUSPEND))
        self.mw.col.sched.suspend_cards([self.card.id])
        tooltip(tr(TR.STUDYING_CARD_SUSPENDED))
        self.mw.reset()

    def onDelete(self) -> None:
        # need to check state because the shortcut is global to the main
        # window
        if self.mw.state != "review" or not self.card:
            return
        self.mw.checkpoint(tr(TR.ACTIONS_DELETE))
        cnt = len(self.card.note().cards())
        self.mw.col.remove_notes([self.card.note().id])
        self.mw.reset()
        tooltip(tr(TR.STUDYING_NOTE_AND_ITS_CARD_DELETED, count=cnt))

    def onBuryCard(self) -> None:
        self.mw.checkpoint(tr(TR.STUDYING_BURY))
        self.mw.col.sched.bury_cards([self.card.id])
        self.mw.reset()
        tooltip(tr(TR.STUDYING_CARD_BURIED))

    def onBuryNote(self) -> None:
        self.mw.checkpoint(tr(TR.STUDYING_BURY))
        self.mw.col.sched.bury_note(self.card.note())
        self.mw.reset()
        tooltip(tr(TR.STUDYING_NOTE_BURIED))

    def onRecordVoice(self) -> None:
        def after_record(path: str) -> None:
            self._recordedAudio = path
            self.onReplayRecorded()

        record_audio(self.mw, self.mw, False, after_record)

    def onReplayRecorded(self) -> None:
        if not self._recordedAudio:
            tooltip(tr(TR.STUDYING_YOU_HAVENT_RECORDED_YOUR_VOICE_YET))
            return
        av_player.play_file(self._recordedAudio)
