# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, stat, shutil, difflib, simplejson, re
import unicodedata as ucd
from aqt.qt import *
from anki.utils import fmtTimeSpan, stripHTML, isMac
from anki.hooks import addHook, runHook, runFilter
from anki.sound import playFromText, clearAudioQueue, hasSound
from aqt.utils import mungeQA, getBase, shortcut, openLink, tooltip
import aqt

class Reviewer(object):
    "Manage reviews.  Maintains a separate state."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.card = None
        self.cardQueue = []
        self.hadCardQueue = False
        self._answeredIds = []
        self.state = None
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)
        addHook("leech", self.onLeech)

    def show(self):
        self.mw.col.reset()
        self.mw.keyHandler = self._keyHandler
        self.web.setLinkHandler(self._linkHandler)
        self.web.setKeyHandler(self._catchEsc)
        if isMac:
            self.bottom.web.setFixedHeight(46)
        else:
            self.bottom.web.setFixedHeight(52)
        self.bottom.web.setLinkHandler(self._linkHandler)
        self.nextCard()

    def lastCard(self):
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                return self.mw.col.getCard(self._answeredIds[-1])

    def cleanup(self):
        runHook("reviewCleanup")

    # Fetching a card
    ##########################################################################

    def nextCard(self):
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
        clearAudioQueue()
        if c:
            self._initWeb()
        else:
            self.mw.moveToState("overview")

    # Audio
    ##########################################################################

    def replayAudio(self):
        clearAudioQueue()
        c = self.card
        if self.state == "question":
            playFromText(c.q())
        elif self.state == "answer":
            playFromText(c.a())

    # Initializing the webview
    ##########################################################################

    _revHtml = """
<div id=qa></div>
<script>
var ankiPlatform = "desktop";
var typeans;
function _updateQA (q, answerMode) {
    $("#qa").html(q);
    typeans = document.getElementById("typeans");
    if (typeans) {
        typeans.focus();
    }
    if (answerMode) {
        window.location = "#answer";
    }
};
function _getTypedText () {
    if (typeans) {
        py.link("typeans:"+typeans.value);
    }
};
function _typeAnsPress() {
    if (window.event.keyCode === 13) {
        py.link("ans");
    }
}
</script>
"""

    def _initWeb(self):
        base = getBase(self.mw.col)
        self.web.stdHtml(self._revHtml, self._styles(),
            bodyClass="card", loadCB=lambda x: self._showQuestion(),
            head=base)

    # Showing the question
    ##########################################################################

    def _mungeQA(self, buf):
        return self.mw.col.media.escapeImages(
            self.typeAnsFilter(mungeQA(buf)))

    def _showQuestion(self):
        self.state = "question"
        c = self.card
        # grab the question and play audio
        q = c.q()
        if self.mw.col.decks.confForDid(self.card.did)['autoplay']:
            playFromText(q)
        # render & update bottom
        q = self._mungeQA(q)
        self.web.eval("_updateQA(%s);" % simplejson.dumps(q))
        self._showAnswerButton()
        # if we have a type answer field, focus main web
        if self.typeCorrect:
            self.mw.web.setFocus()
        # user hook
        runHook('showQuestion')

    # Showing the answer
    ##########################################################################

    def _showAnswer(self):
        self.state = "answer"
        c = self.card
        a = c.a()
        # play audio?
        if self.mw.col.decks.confForDid(self.card.did)['autoplay']:
            playFromText(a)
        # render and update bottom
        a = self._mungeQA(a)
        self.web.eval("_updateQA(%s, true);" % simplejson.dumps(a))
        self._showEaseButtons()
        # user hook
        runHook('showAnswer')

    # Answering a card
    ############################################################

    def _answerCard(self, ease):
        "Reschedule card and show next."
        if self.state != "answer":
            return
        if self.mw.col.sched.answerButtons(self.card) < ease:
            return
        self.mw.col.sched.answerCard(self.card, ease)
        self._answeredIds.append(self.card.id)
        self.mw.autosave()
        self.nextCard()

    # Handlers
    ############################################################

    def _catchEsc(self, evt):
        if evt.key() == Qt.Key_Escape:
            self.web.eval("$('#typeans').blur();")
            return True

    def _keyHandler(self, evt):
        key = unicode(evt.text())
        if key == "e":
            self.mw.onEditCurrent()
        elif key == " " and self.state == "question":
            self._showAnswer()
        elif key == "r":
            self.replayAudio()
        elif key == "*":
            self.mw.onMark()
        elif key == "-":
            self.mw.onBuryNote()
        elif key == "=":
            self.mw.onSuspend()
        elif key in ("1", "2", "3", "4"):
            self._answerCard(int(key))
        elif evt.key() == Qt.Key_Delete:
            self.mw.onDelete()

    def _linkHandler(self, url):
        if url == "ans":
            self._showAnswer()
        elif url.startswith("ease"):
            self._answerCard(int(url[4:]))
        elif url == "edit":
            self.mw.onEditCurrent()
        elif url == "more":
            self.showContextMenu()
        elif url.startswith("typeans:"):
            (cmd, arg) = url.split(":", 1)
            self.typedAnswer = arg
        else:
            openLink(url)

    # CSS
    ##########################################################################

    _css = """
hr { background-color:#ccc; margin: 1em; }
body { margin:1.5em; }
img { max-width: 95%; max-height: 95%; }
"""

    def _styles(self):
        return self._css

    # Type in the answer
    ##########################################################################

    failedCharColour = "#FF0000"
    passedCharColour = "#00FF00"
    typeAnsPat = "\[\[type:(.+?)\]\]"

    def typeAnsFilter(self, buf):
        if self.state == "question":
            return self.typeAnsQuestionFilter(buf)
        else:
            return self.typeAnsAnswerFilter(buf)

    def typeAnsQuestionFilter(self, buf):
        self.typeCorrect = None
        clozeIdx = None
        m = re.search(self.typeAnsPat, buf)
        if not m:
            return buf
        fld = m.group(1)
        # if it's a cloze, extract data
        if fld.startswith("cq:"):
            # get field and cloze position
            m = re.match("cq:(\d+):(.+)", fld)
            if not m:
                return re.sub(
                    self.typeAnsPat, _("Type answer: invalid cloze pattern"),
                    buf)
            clozeIdx = m.group(1)
            fld = m.group(2)
        # loop through fields for a match
        for f in self.card.model()['flds']:
            if f['name'] == fld:
                self.typeCorrect = self.card.note()[f['name']]
                if clozeIdx:
                    # narrow to cloze
                    self.typeCorrect = self._contentForCloze(self.typeCorrect, clozeIdx)
                self.typeFont = f['font']
                self.typeSize = f['size']
                break
        if not self.typeCorrect:
            if self.typeCorrect is None:
                return re.sub(
                    self.typeAnsPat, _("Type answer: unknown field %s") % fld, buf)
            else:
                # empty field, remove type answer pattern
                return re.sub(self.typeAnsPat, "", buf)
        return re.sub(self.typeAnsPat, """
<center>
<input type=text id=typeans onkeypress="_typeAnsPress();"
   style="font-family: '%s'; font-size: %spx;">
</center>
""" % (self.typeFont, self.typeSize), buf)

    def typeAnsAnswerFilter(self, buf):
        if not self.typeCorrect:
            return re.sub(self.typeAnsPat, "", buf)
        # tell webview to call us back with the input content
        self.web.eval("_getTypedText();")
        # munge correct value
        cor = self.mw.col.media.strip(stripHTML(self.typeCorrect))
        # compare with typed answer
        res = self.correct(cor, self.typedAnswer)
        # and update the type answer area
        return re.sub(self.typeAnsPat, """
<span style="font-family: '%s'; font-size: %spx">%s</span>""" %
                      (self.typeFont, self.typeSize, res), buf)

    def _contentForCloze(self, txt, idx):
        matches = re.findall("\{\{c%s::(.+?)\}\}"%idx, txt)
        if len(matches) > 1:
            txt = ", ".join(matches)
        else:
            txt = matches[0]
        return txt

    # following type answer functions thanks to Bernhard
    def calculateOkBadStyle(self):
        "Precalculates styles for correct and incorrect part of answer"
        st = "background: %s; color: #000;"
        self.styleOk  = st % self.passedCharColour
        self.styleBad = st % self.failedCharColour

    def ok(self, a):
        "returns given sring in style correct (green)"
        if len(a) == 0:
            return ""
        return "<span style='%s'>%s</span>" % (self.styleOk, a)

    def bad(self, a):
        "returns given sring in style incorrect (red)"
        if len(a) == 0:
            return ""
        return "<span style='%s'>%s</span>" % (self.styleBad, a)

    def applyStyle(self, testChar, correct, wrong):
        "Calculates answer fragment depending on testChar's unicode category"
        ZERO_SIZE = 'Mn'
        def head(a):
            return a[:len(a) - 1]
        def tail(a):
            return a[len(a) - 1:]
        if ucd.category(testChar) == ZERO_SIZE:
            return self.ok(head(correct)) + self.bad(tail(correct) + wrong)
        return self.ok(correct) + self.bad(wrong)

    def correct(self, a, b):
        "Diff-corrects the typed-in answer."
        if b == "":
            return "";
        self.calculateOkBadStyle()
        ret = ""
        lastEqual = ""
        s = difflib.SequenceMatcher(None, b, a)
        for tag, i1, i2, j1, j2 in s.get_opcodes():
            if tag == "equal":
                lastEqual = b[i1:i2]
            elif tag == "replace":
                ret += self.applyStyle(b[i1], lastEqual,
                                 b[i1:i2] + ("-" * ((j2 - j1) - (i2 - i1))))
                lastEqual = ""
            elif tag == "delete":
                ret += self.applyStyle(b[i1], lastEqual, b[i1:i2])
                lastEqual = ""
            elif tag == "insert":
                if ucd.category(a[j1]) != 'Mn':
                    dashNum = (j2 - j1)
                else:
                    dashNum = ((j2 - j1) - 1)
                ret += self.applyStyle(a[j1], lastEqual, "-" * dashNum)
                lastEqual = ""
        return ret + self.ok(lastEqual)

    # Bottom bar
    ##########################################################################

    _bottomCSS = """
body {
background: -webkit-gradient(linear, left top, left bottom,
from(#fff), to(#ddd));
border-bottom: 0;
border-top: 1px solid #aaa;
margin: 0;
padding: 0px;
padding-left: 5px; padding-right: 5px;
}
button {
min-width: 60px;
}
td { font-weight: bold; font-size: 12px; }
.hitem { margin-top: 2px; }
.stat { padding-top: 5px; }
.stat2 { padding-top: 3px; font-weight: normal; }
.stattxt { padding-left: 5px; padding-right: 5px; white-space: nowrap; }
.nobold { font-weight: normal; display: inline-block; padding-top: 4px; }
.spacer { height: 18px; }
.spacer2 { height: 16px; }
"""

    def _bottomHTML(self, middle):
        if not self.card.deckConf().get('timer'):
            maxTime = 0
        else:
            maxTime = self.card.deckConf()['maxTaken']
        return """
<table width=100%% cellspacing=0 cellpadding=0>
<tr>
<td align=left width=50 valign=top class=stat>
<br>
<button onclick="py.link('edit');">%(edit)s</button></td>
<td align=center valign=top>
%(middle)s
</td>
<td width=50 align=right valign=top class=stat><span id=time class=stattxt>
</span><br>
<button onclick="py.link('more');">%(more)s &#9662;</button>
</td>
</tr>
</table>
<script>
var time = %(time)d;
$(function () {
$("#ansbut").focus();
updateTime();
setInterval(function () { time += 1; updateTime() }, 1000);
});

var updateTime = function () {
    if (!%(maxTime)s) {
        return;
    }
    time = Math.min(%(maxTime)s, time);
    var m = Math.floor(time / 60);
    var s = time %% 60;
    if (s < 10) {
        s = "0" + s;
    }
    var e = $("#time").text(time);
    e.text(m + ":" + s);
}
</script>
""" % dict(middle=middle, rem=self._remaining(), edit=_("Edit"),
           more=_("More"), time=self.card.timeTaken()/1000,
           maxTime=maxTime)

    def _showAnswerButton(self):
        self.bottom.web.setFocus()
        middle = '''
<span class=stattxt>%s</span><br>
<button id=ansbut onclick='py.link(\"ans\");'>%s</button>''' % (
        self._remaining(), _("Show Answer"))
        # wrap it in a table so it has the same top margin as the ease buttons
        middle = "<table cellpadding=0><tr><td class=stat2 align=center>%s</td></tr></table>" % middle

        self.bottom.web.stdHtml(
            self._bottomHTML(middle),
            self.bottom._css + self._bottomCSS)

    def _showEaseButtons(self):
        self.bottom.web.setFocus()
        self.bottom.web.stdHtml(
            self._bottomHTML(self._answerButtons()),
            self.bottom._css + self._bottomCSS)

    def _remaining(self):
        if not self.mw.col.conf['dueCounts']:
            return ""
        counts = list(self.mw.col.sched.counts(self.card))
        idx = self.mw.col.sched.countIdx(self.card)
        counts[idx] = "<u>%s</u>" % (counts[idx])
        space = " + "
        ctxt = '<font color="#000099">%s</font>' % counts[0]
        ctxt += space + '<font color="#990000">%s</font>' % counts[1]
        ctxt += space + '<font color="#007700">%s</font>' % counts[2]
        return ctxt

    def _defaultEase(self):
        if self.mw.col.sched.answerButtons(self.card) == 4:
            return 3
        else:
            return 2

    def _answerButtons(self):
        if self.mw.col.sched.answerButtons(self.card) == 4:
            labels = (_("Again"), _("Hard"), _("Good"), _("Easy"))
        else:
            labels = (_("Again"), _("Good"), _("Easy"))
        times = []
        buttons = []
        default = self._defaultEase()
        def but(label, i):
            if i == default:
                extra = "id=defease"
            else:
                extra = ""
            due = self._buttonTime(i-1, default-1)
            return '''
<td align=center>%s<button %s onclick='py.link("ease%d");'>\
%s</button></td>''' % (due, extra, i, label)
        buf = "<center><table cellpading=0 cellspacing=0><tr>"
        for i in range(0, len(labels)):
            buf += but(labels[i], i+1)
        buf += "</tr></table>"
        script = """
<script>$(function () { $("#defease").focus(); });</script>"""
        return buf + script

    def _buttonTime(self, i, green):
        if not self.mw.col.conf['estTimes']:
            return "<div class=spacer></div>"
        txt = self.mw.col.sched.nextIvlStr(self.card, i+1, True)
        return '<span class=nobold>%s</span><br>' % txt

    # Leeches
    ##########################################################################

    # fixme: update; clear on card transition
    def onLeech(self, card):
        # for now
        tooltip("Card was a leech.")
        return
        link = aqt.appHelpSite + "Leeches.html"
        txt = (_("""\
Card was a <a href="%s">leech</a>.""") % link)
        if isLeech and self.deck.db.scalar(
            "select 1 from cards where id = :id and type < 0", id=cardId):
            txt += _(" It has been suspended.")
        self.setNotice(txt)

    # Context menu
    ##########################################################################

    def showContextMenu(self):
        opts = [
            [_("Replay Audio"), "r", self.replayAudio],
            [_("Mark Note"), "*", self.mw.onMark],
            [_("Bury Note"), "-", self.mw.onBuryNote],
            [_("Suspend Note"), "=", self.mw.onSuspend],
            [_("Delete Note"), "Delete", self.mw.onDelete]
        ]
        m = QMenu(self.mw)
        for label, scut, func in opts:
            a = m.addAction(label)
            a.setShortcut(QKeySequence(scut))
            a.connect(a, SIGNAL("triggered()"), func)
        m.exec_(QCursor.pos())
