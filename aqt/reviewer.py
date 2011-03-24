# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import time, os, stat, shutil, difflib, simplejson
import unicodedata as ucd
from PyQt4.QtCore import *
from PyQt4.QtGui import *
from anki.utils import fmtTimeSpan, stripHTML
from anki.hooks import addHook, runHook, runFilter
from anki.sound import playFromText, clearAudioQueue
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
        self._setupStatus()

    def show(self):
        self.web.setKeyHandler(self._keyHandler)
        self.web.setLinkHandler(self._linkHandler)
        self._initWeb()

    def lastCard(self):
        if self._answeredIds:
            if not self.card or self._answeredIds[-1] != self.card.id:
                return self.mw.deck.getCard(self._answeredIds[-1])

    # Fetching a card
    ##########################################################################

    def _getCard(self):
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
            self._showQuestion()
        else:
            self._hideStatus()
            self.mw.disableCardMenuItems()
            if self.mw.deck.cardCount():
                self._showCongrats()
            else:
                self._showEmpty()

    def _maybeEnableSound(self):
        print "enable sound fixme"
        return
        snd = (hasSound(self.reviewer.card.q()) or
               (hasSound(self.reviewer.card.a()) and
                self.state != "getQuestion"))
        self.form.actionRepeatAudio.setEnabled(snd)

    # Initializing the webview
    ##########################################################################

    _revHtml = """
<table width=100%% height=100%%><tr valign=middle><td>
<div id=q></div>
<hr class=inv id=midhr>
<div id=a></div>
<div id=filler></div>
</td></tr></table>
<a id=ansbut class="but ansbut" href=ans onclick="showans();">
<div class=ansbut>%(showans)s</div>
</a>
<div id=easebuts>
</div>
<script>
var hideq;
var ans;
function updateQA (qa) {
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
};
function showans () {
    $(".inv").removeClass('inv');
    if (hideq) {
        $("#q").html(ans);
        $("#midhr").addClass("inv");
    } else {
        location.hash = "a";
    }
    $("#ansbut").hide();
};
$(document).ready(function () {
$(".ansbut").focus();
});
</script>
"""

    def _initWeb(self):
        self.web.stdHtml(self._revHtml % dict(
            showans=_("Show Answer")), self._styles(),
            loadCB=lambda x: self._getCard())

    # Showing the question (and preparing answer)
    ##########################################################################

    def _showQuestion(self):
        self.state = "question"
        # fixme: timeboxing
        # fixme: prevent audio from repeating
        # fixme: include placeholder for type answer result
        c = self.card
        # original question with sounds
        q = c.q()
        a = c.a()
        if (#self.state != self.oldState and not nosound
            self.mw.config['autoplaySounds']):
            playFromText(q)
        # render

        # buf = self.typeAnsResult()
        esc = self.mw.deck.media.escapeImages
        q=esc(mungeQA(q))
        a=esc(mungeQA(a))
        self.web.eval("updateQA(%s);" % simplejson.dumps(
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
<a %s class="but easebut" href=ease%d>%s</a>''' % (extra, i, label)
        for i in range(0, len(labels)):
            times.append(self._buttonTime(i, default-1))
            buttons.append(but(labels[i], i+1))
        buf = ("<table><tr><td align=center>" +
               "</td><td align=center>".join(times) + "</td></tr>")
        buf += ("<tr><td>" +
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
        txt = '<span class=time>%s</span>' % txt
        return txt

    # Answering a card
    ############################################################

    def _answerCard(self, ease):
        "Reschedule card and show next."
        self.mw.deck.sched.answerCard(self.card, ease)
        self._answeredIds.append(self.card.id)
        print "fixme: save"
        self._getCard()

    # Handlers
    ############################################################

    def _keyHandler(self, evt):
        if self.state == "question":
            show = False
            if evt.key() in (Qt.Key_Enter,
                             Qt.Key_Return):
                show = True
            elif evt.key() == Qt.Key_Space and not self.typeAns():
                show = True
            if show:
                self._showAnswer()
                self.web.eval("showans();")
                return True
        elif self.state == "answer":
            if evt.key() in (Qt.Key_Enter,
                             Qt.Key_Return,
                             Qt.Key_Space):
                self._answerCard(self._defaultEase())
                return True
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

    # CSS
    ##########################################################################

    _css = """
a.ansbut {
    bottom: 1em;
    height: 40px;
    left: 50%;
    margin-left: -125px;
    position: fixed;
    width: 250px;
    font-size: 100%;
}
a.ansbut:focus {
background: #c7c7c7;
}
div.ansbut {
  position: relative; top: 25%;
}

div#q, div#a {
margin: 0px;
}

#easebuts {
  bottom: 1em;
  height: 55px;
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
"""

    def _styles(self):
        css = self.mw.sharedCSS
        css += self.mw.deck.allCSS()
        css += self._css
        css = runFilter("addStyles", css)
        return css

    # Type in the answer
    ##########################################################################

    failedCharColour = "#FF0000"
    passedCharColour = "#00FF00"
    futureWarningColour = "#FF0000"

        # if self.card.cardModel.typeAnswer:
        #     try:
        #         cor = stripMedia(stripHTML(self.card.fact[
        #             self.card.cardModel.typeAnswer]))
        #     except KeyError:
        #         self.card.cardModel.typeAnswer = ""
        #         cor = ""
        #     if cor:
        #         given = unicode(self.main.typeAnswerField.text())
        #         res = self.correct(cor, given)
        #         a = res + "<br>" + a

        # fixme: type answer undo area shouldn't trigger global shortcut
        # class QLineEditNoUndo(QLineEdit):
        #     def __init__(self, parent):
        #         self.parent = parent
        #         QLineEdit.__init__(self, parent)
        #     def keyPressEvent(self, evt):
        #         if evt.matches(QKeySequence.Undo):
        #             evt.accept()
        #             if self.parent.form.actionUndo.isEnabled():
        #                 self.parent.onUndo()
        #         else:
        #             return QLineEdit.keyPressEvent(self, evt)

    def typeAns(self):
        "True if current card has answer typing enabled."
        return self.card.template()['typeAns']

    def typeAnsInput(self):
        return ""
        if self.card.cardModel.typeAnswer:
            self.adjustInputFont()

    def getFont(self):
        sz = 20
        fn = u"Arial"
        for fm in self.card.fact.model.fieldModels:
            if fm.name == self.card.cardModel.typeAnswer:
                sz = fm.quizFontSize or sz
                fn = fm.quizFontFamily or fn
                break
        return (fn, sz)

    def adjustInputFont(self):
        (fn, sz) = self.getFont()
        f = QFont()
        f.setFamily(fn)
        f.setPixelSize(sz)
        self.main.typeAnswerField.setFont(f)
        # add some extra space as layout is wrong on osx
        self.main.typeAnswerField.setFixedHeight(
            self.main.typeAnswerField.sizeHint().height() + 10)

    def calculateOkBadStyle(self):
        "Precalculates styles for correct and incorrect part of answer"
        (fn, sz) = self.getFont()
        st = "background: %s; color: #000; font-size: %dpx; font-family: %s;"
        self.styleOk  = st % (passedCharColour, sz, fn)
        self.styleBad = st % (failedCharColour, sz, fn)

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

    def head(self, a):
        return a[:len(a) - 1]

    def tail(self, a):
        return a[len(a) - 1:]

    def applyStyle(self, testChar, correct, wrong):
        "Calculates answer fragment depending on testChar's unicode category"
        ZERO_SIZE = 'Mn'
        if ucd.category(testChar) == ZERO_SIZE:
            return self.ok(self.head(correct)) + self.bad(self.tail(correct) + wrong)
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
        self.deck.db.flush()
        self.hideButtons()
        self.disableCardMenuItems()
        self.switchToCongratsScreen()
        self.form.learnMoreButton.setEnabled(
            not not self.deck.newAvail)
        self.startRefreshTimer()
        self.bodyView.setState(state)
        # focus finish button
        self.form.finishButton.setFocus()
        runHook('deckFinished')

    def drawDeckFinishedMessage(self):
        "Tell the user the deck is finished."
        self.main.mainWin.congratsLabel.setText(
            self.main.deck.deckFinishedMsg())

    # Deck empty case
    ##########################################################################

    def _showEmpty(self):
        self.state = "empty"
        self.switchToWelcomeScreen()
        self.disableCardMenuItems()

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
            url = "http://ichi2.net/anki/wiki/ProgressBars"
            def mouseReleaseEvent(self, evt):
                QDesktopServices.openUrl(QUrl(self.url))
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
