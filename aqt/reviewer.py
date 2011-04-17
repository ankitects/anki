# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import time, os, stat, shutil, difflib, simplejson
import unicodedata as ucd
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki.utils import fmtTimeSpan, stripHTML
from anki.hooks import addHook, runHook, runFilter
from anki.sound import playFromText, clearAudioQueue, hasSound
from aqt.utils import mungeQA, getBase
import aqt

class Reviewer(object):
    "Manage reviews.  Maintains a separate state."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.card = None
        self.cardQueue = []
        self._answeredIds = []
        self.state = None
        self.keep = False
        self._setupStatus()
        addHook("leech", self.onLeech)

    def show(self):
        self.web.setKeyHandler(self._keyHandler)
        self.web.setLinkHandler(self._linkHandler)
        if self.keep:
            self._initWeb()
        else:
            self.nextCard()
        self.keep = False

    def lastCard(self):
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                return self.mw.deck.getCard(self._answeredIds[-1])

    def cleanup(self):
        self._hideStatus()
        self.mw.disableCardMenuItems()
        runHook("reviewCleanup")

    # Fetching a card
    ##########################################################################

    def nextCard(self):
        if self.cardQueue:
            # a card has been retrieved from undo
            c = self.cardQueue.pop()
        else:
            c = self.mw.deck.sched.getCard()
        self.card = c
        clearAudioQueue()
        if c:
            self.mw.enableCardMenuItems()
            self._showStatus()
            self._maybeEnableSound()
            #self.updateMarkAction()
            self.state = "question"
            self._initWeb()
        else:
            self._hideStatus()
            self.mw.disableCardMenuItems()
            if self.mw.deck.cardCount():
                self._showCongrats()
            else:
                self._showEmpty()

    # Audio
    ##########################################################################

    def _maybeEnableSound(self):
        self.mw.form.actionRepeatAudio.setEnabled(
            hasSound(self.card.q() + self.card.a()))

    def replayAudio(self):
        clearAudioQueue()
        c = self.card
        if not c.template()['hideQ'] or self.state == "question":
            playFromText(c.q())
        if self.state == "answer":
            playFromText(c.a())

    # Initializing the webview
    ##########################################################################

    _revHtml = """
<table width=100%% height=100%%><tr valign=%(align)s><td>
<div id=q></div>
<hr class=inv id=midhr>
<div id=a></div>
<div id=filler></div>
</td></tr></table>
<a id=ansbut class="ansbut ansbutbig" href=ans onclick="_showans();">
<div class=ansbuttxt>%(showans)s</div>
</a>
<div id=easebuts>
</div>
<script>
var ankiPlatform = "desktop";
var hideq;
var ans;
var typeans;
function _updateQA (qa) {
    hideq = qa[4];
    location.hash = "";
    $("#q").html(qa[0]);
    if (hideq) {
        ans = qa[1];
        $("#a").html("");
    } else {
        $("#a").html(qa[1]).addClass("inv");
    }
    $("#midhr").addClass("inv");
    $("#easebuts").html(qa[2]).addClass("inv");
    $("#ansbut").show();
    $("body").removeClass().addClass(qa[3]);
    typeans = document.getElementById("typeans");
    if (typeans) {
        typeans.focus();
    }
    // user hook
    if (typeof(onQuestion) === "function") {
        onQuestion();
    }
};
function _showans () {
    if (typeans) {
        py.link("typeans:"+typeans.value);
    }
    $(".inv").removeClass('inv');
    if (hideq) {
        $("#q").html(ans);
        $("#midhr").addClass("inv");
    } else {
        location.hash = "a";
    }
    $("#ansbut").hide();
    $("#defease").focus();
    // user hook
    if (typeof(onAnswer) === "function") {
        onAnswer();
    }
};
function _processTyped (res) {
    $("#typeans").replaceWith(res);
}
function _onSpace() {
    if (/^ease/.test(document.activeElement.href)) {
        py.link(document.activeElement.href);
    }
}
$(document).ready(function () {
$(".ansbut").focus();
});
</script>
"""

    def _initWeb(self):
        self.web.stdHtml(self._revHtml % dict(
            align="middle" if self.mw.config['centerQA'] else "top",
            showans=_("Show Answer")), self._styles(),
            loadCB=lambda x: self._showQuestion())

    # Showing the question (and preparing answer)
    ##########################################################################

    def _showQuestion(self):
        # fixme: timeboxing
        # fixme: timer
        self.state = "question"
        c = self.card
        q = c.q()
        a = c.a()
        if self.mw.config['autoplaySounds']:
            playFromText(q)
        # render
        esc = self.mw.deck.media.escapeImages
        q=esc(mungeQA(q)) + self.typeAnsInput()
        a=esc(mungeQA(a))
        self.web.eval("_updateQA(%s);" % simplejson.dumps(
            [q, a, self._answerButtons(), c.cssClass(),
             c.template()['hideQ']]))
        runHook('showQuestion')

    # Showing the answer
    ##########################################################################

    def _showAnswer(self):
        self.state = "answer"
        c = self.card
        a = c.a()
        if self.mw.config['autoplaySounds']:
            playFromText(a)
        # render
        runHook('showAnswer')

    # Ease buttons
    ##########################################################################

    def _defaultEase(self):
        if self.mw.deck.sched.answerButtons(self.card) == 4:
            return 3
        else:
            return 2

    def _answerButtons(self):
        if self.mw.deck.sched.answerButtons(self.card) == 4:
            labels = (_("Again"), _("Hard"), _("Good"), _("Easy"))
        else:
            labels = (_("Again"), _("Good"), _("Easy"))
        times = []
        buttons = []
        default = self._defaultEase()
        def but(label, i):
            if i == default:
                extra=" id=defease"
            else:
                extra = ""
            return '''
<a %s class="ansbut easebut" href=ease%d>%s</a>''' % (extra, i, label)
        for i in range(0, len(labels)):
            l = labels[i]
            l += "<br><small>%s</small>" % self._buttonTime(i, default-1)
            buttons.append(but(l, i+1))
        buf = ("<table><tr><td>" +
               "</td><td>".join(buttons) + "</td></tr></table>")
        return "<center>" + buf + "</center>"
        return buf

    def _buttonTime(self, i, green):
        if self.mw.config['suppressEstimates']:
            return ""
        txt = self.mw.deck.sched.nextIvlStr(self.card, i+1, True)
        if i == 0:
            txt = '<span style="color: #700">%s</span>' % txt
        elif i == green:
            txt = '<span style="color: #070">%s</span>' % txt
        return txt

    # Answering a card
    ############################################################

    def _answerCard(self, ease):
        "Reschedule card and show next."
        self.mw.deck.sched.answerCard(self.card, ease)
        self._answeredIds.append(self.card.id)
        print "fixme: save"
        self.nextCard()

    # Handlers
    ############################################################

    def _keyHandler(self, evt):
        if self.state == "question":
            show = False
            if evt.key() == Qt.Key_Space and self.typeAns() is None:
                show = True
            elif evt.key() == Qt.Key_Escape:
                self.web.eval("$('#typeans').blur();")
            if show:
                self._showAnswer()
                self.web.eval("_showans();")
                return True
        elif self.state == "answer":
            if evt.key() == Qt.Key_Space:
                self.web.eval("_onSpace();")
            else:
                key = unicode(evt.text())
                if key and key >= "1" and key <= "4":
                    key=int(key)
                    if self.card.queue == 2 or key < 4:
                        self._answerCard(key)
                        return True

    def _linkHandler(self, url):
        if url == "ans":
            self._showAnswer()
        elif url.startswith("ease"):
            self._answerCard(int(url[4:]))
        elif url == "add":
            self.mw.onAddCard()
        elif url == "dlist":
            self.mw.close()
        elif url == "ov":
            self.mw.moveToState("overview")
        elif url.startswith("typeans:"):
            (cmd, arg) = url.split(":")
            self.processTypedAns(arg)
        else:
            QDesktopServices.openUrl(QUrl(url))

    # CSS
    ##########################################################################

    _css = """
.ansbut {
    -webkit-box-shadow: 2px 2px 6px rgba(0,0,0,0.6);
    -webkit-user-drag: none;
    -webkit-user-select: none;
    background-color: #ddd;
    border-radius: 5px;
    border: 1px solid #aaa;
    color: #000;
    display: inline-block;
    font-size: 80%;
    margin: 0 5 0 5;
    padding: 3;
    text-decoration: none;
    text-align: center;
}
.but:focus, .but:hover { background-color: #aaa; }
.ansbutbig {
    bottom: 1em;
    height: 40px;
    left: 50%;
    margin-left: -125px !important;
    position: fixed;
    width: 250px;
    font-size: 100%;
}
.ansbut:focus {
font-weight: bold;
}
div.ansbuttxt {
  position: relative; top: 25%;
}

div#q, div#a {
margin: 0px;
}

#easebuts {
  bottom: 1em;
  height: 47px;
  left: 50%;
  margin-left: -200px;
  position: fixed;
  width: 400px;
  font-size: 100%;
}

.easebut {
  width: 60px;
  font-size: 100%;
}

.time {
  background: #eee;
  padding: 5px;
  border-radius: 10px;
}

div#filler {
  height: 30px;
}

.q { margin-bottom: 1em; }
.a { margin-top: 1em; }
.inv { visibility: hidden; }

.cloze { font-weight: bold; color: blue; }
"""

    def _styles(self):
        css = self.mw.sharedCSS
        css += self.mw.deck.allCSS()
        css += self._css
        return css

    # Type in the answer
    ##########################################################################

    failedCharColour = "#FF0000"
    passedCharColour = "#00FF00"

    def typeAns(self):
        "None if answer typing disabled."
        return self.card.template()['typeAns']

    def typeAnsInput(self):
        if self.typeAns() is None:
            return ""
        return """
<center>
<input type=text id=typeans style="font-family: '%s'; font-size: %s;">
</center>
""" % (
    self.getFont())

    def processTypedAns(self, given):
        ord = self.typeAns()
        try:
            cor = self.mw.deck.media.strip(
                stripHTML(self.card.fact().fields[ord]))
        except IndexError:
            self.card.template()['typeAns'] = None
            self.card.model().flush()
            cor = ""
        if cor:
            res = self.correct(cor, given)
            self.web.eval("_processTyped(%s);" % simplejson.dumps(res))

    def getFont(self):
        f = self.card.model().fields[self.typeAns()]
        return (f['font'], f['qsize'])

    def calculateOkBadStyle(self):
        "Precalculates styles for correct and incorrect part of answer"
        (fn, sz) = self.getFont()
        st = "background: %s; color: #000; font-size: %dpx; font-family: %s;"
        self.styleOk  = st % (self.passedCharColour, sz, fn)
        self.styleBad = st % (self.failedCharColour, sz, fn)

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
                dashNum = (j2 - j1) if ucd.category(a[j1]) != 'Mn' else ((j2 - j1) - 1)
                ret += self.applyStyle(a[j1], lastEqual, "-" * dashNum)
                lastEqual = ""
        return ret + self.ok(lastEqual)

    # Deck finished case
    ##########################################################################

    def _showCongrats(self):
        self.state = "congrats"
        self.card = None
        buf = """
<center>
%s
<p>
%s %s
<script>$("#ov").focus();</script>
</center>""" % (self.mw.deck.sched.finishedMsg(),
                self.mw.button(key="o", name=_("Overview"), link="ov", id='ov'),
                self.mw.button(key="o", name=_("Deck List"), link="dlist"))

        self.web.stdHtml(buf, css=self.mw.sharedCSS)
        runHook('deckFinished')

    def drawDeckFinishedMessage(self):
        "Tell the user the deck is finished."

    # Deck empty case
    ##########################################################################

    def _showEmpty(self):
        self.state = "empty"
        buf = """
<h1>%(welcome)s</h1>
<p>
<table>
<tr>
<td width=40>
<a href="add"><img src="qrc:/icons/list-add.png"></a>
</td>
<td valign=middle><b><a href="add">%(add)s</a></b>
<br>%(start)s</td>
</tr>
</table>
<br>
<table>
<tr>
<td width=40>
<a href="welcome:back"><img src="qrc:/icons/go-previous.png"></a>
</td>
<td valign=middle><b><a href="dlist">%(back)s</a></b></td>
</tr>
</table>""" % \
        {"welcome":_("Welcome to Anki!"),
         "add":_("Add Cards"),
         "start":_("Start adding your own material."),
         "back":_("Deck List"),
         }
        self.web.stdHtml(buf, css=self.mw.sharedCSS)

    # Status bar
    ##########################################################################

    def _setupStatus(self):
        self._statusWidgets = []
        sb = self.mw.form.statusbar
        def addWgt(w, stretch=0):
            w.setShown(False)
            sb.addWidget(w, stretch)
            self._statusWidgets.append(w)
        def vertSep():
            spacer = QFrame()
            spacer.setFrameStyle(QFrame.VLine)
            spacer.setFrameShadow(QFrame.Plain)
            spacer.setStyleSheet("* { color: #888; }")
            return spacer
        # left spacer
        space = QWidget()
        addWgt(space, 1)
        # remaining
        self.remText = QLabel()
        addWgt(self.remText, 0)
        # progress
        addWgt(vertSep())
        class QClickableProgress(QProgressBar):
            def mouseReleaseEvent(self, evt):
                aqt.openHelp("ProgressBars")
        progressBarSize = (50, 14)
        self.progressBar = QClickableProgress()
        self.progressBar.setFixedSize(*progressBarSize)
        self.progressBar.setMaximum(100)
        self.progressBar.setTextVisible(False)
        if QApplication.instance().style().objectName() != "plastique":
            self.plastiqueStyle = QStyleFactory.create("plastique")
            self.progressBar.setStyle(self.plastiqueStyle)
        addWgt(self.progressBar, 0)

    def _showStatus(self):
        self._showStatusWidgets(True)
        self._updateRemaining()
        self._updateProgress()

    def _hideStatus(self):
        self._showStatusWidgets(False)

    def _showStatusWidgets(self, shown=True):
        for w in self._statusWidgets:
            w.setShown(shown)
        self.mw.form.statusbar.hideOrShow()

    # fixme: only show progress for reviews, and only when revs due?
    def _updateRemaining(self):
        counts = list(self.mw.deck.sched.counts())
        idx = self.mw.deck.sched.countIdx(self.card)
        counts[idx] = "<u>%s</u>" % (counts[idx]+1)
        space = "&nbsp;" * 2
        ctxt = '<font color="#000099">%s</font>' % counts[0]
        ctxt += space + '<font color="#990000">%s</font>' % counts[1]
        ctxt += space + '<font color="#007700">%s</font>' % counts[2]
        buf = _("Remaining: %s") % ctxt
        self.remText.setText(buf)

    def _updateProgress(self):
        p = QPalette()
        p.setColor(QPalette.Base, QColor("black"))
        p.setColor(QPalette.Button, QColor("black"))
        perc = 50
        if perc == 0:
            p.setColor(QPalette.Highlight, QColor("black"))
        elif perc < 50:
            p.setColor(QPalette.Highlight, QColor("#ee0000"))
        elif perc < 65:
            p.setColor(QPalette.Highlight, QColor("#ee7700"))
        elif perc < 75:
            p.setColor(QPalette.Highlight, QColor("#eeee00"))
        else:
            p.setColor(QPalette.Highlight, QColor("#00ee00"))
        self.progressBar.setPalette(p)
        self.progressBar.setValue(perc)

    # Leeches
    ##########################################################################

    # fixme: update; clear on card transition
    def onLeech(self, card):
        print "leech"
        return
        link = aqt.appHelpSite + "Leeches.html"
        txt = (_("""\
Card was a <a href="%s">leech</a>.""") % link)
        if isLeech and self.deck.db.scalar(
            "select 1 from cards where id = :id and type < 0", id=cardId):
            txt += _(" It has been suspended.")
        self.setNotice(txt)
