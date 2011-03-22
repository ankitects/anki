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
        # self.main.connect(self.body, SIGNAL("loadFinished(bool)"),
        #                   self.onLoadFinished)

    def show(self):
        self._getCard()

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
            self._maybeEnableSound()
            #self.updateMarkAction()
            self._showQuestion()
        else:
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

    # HTML helpers
    ##########################################################################

    _css = """
a.ansbut {
  display: block; position: fixed;
  bottom: 1em; width: 250px; left: 50%; margin-left: -125px;
  height: 40px; background-color: #ccc;
  border-radius: 5px;
  text-align: center;
  color: #000; text-decoration: none;
  -webkit-box-shadow: 2px 2px 6px rgba(0,0,0,0.6);
  border: 1px solid #aaa;

}
a.ansbut:focus {
background: #c7c7c7;
}
div.ansbut {
  position: relative; top: 25%;
}
div#filler {
  height: 20px;
}
"""

    def _renderQA(self, card, text):
        # we want to include enough space at the bottom to allow for the
        # answer buttons
        buf = "<div id=filler></div>"
        self.web.stdHtml(text+buf, self._styles(), bodyClass=card.cssClass())

    def _styles(self):
        css = self.mw.sharedCSS
        css += self.card.model().css
        css += self._css
        css = runFilter("addStyles", css)
        return css

    # Showing the question
    ##########################################################################

    _qHtml = """
%(q)s
%(but)s"""

    def _showQuestion(self):
        # fixme: timeboxing
        # fixme: q/a separation
        # fixme: prevent audio from repeating
        c = self.card
        # original question with sounds
        q = c.q()
        if (#self.state != self.oldState and not nosound
            self.mw.config['autoplaySounds']):
            playFromText(q)
        # render
        buf = self._qHtml % dict(
            q=mungeQA(q),
            but=self._questionButtons())
        self._renderQA(c, buf)
        runHook('showQuestion')

    # Question buttons
    ##########################################################################

    def _questionButtons(self):
        buf = self.typeAnsInput()
        # make sure to focus
        buf += """
<a href=ans class=ansbut><div class=ansbut>%s</div></a>
""" % _("Show Answer")
        return buf

    # Showing the answer
    ##########################################################################

    def _showAnswer(self):
        c = self.card
        # original question with sounds
        q = c.a()
        if self.mw.config['autoplaySounds']:
            playFromText(a)
        # render
        buf = self._qHtml % dict(
            q=mungeQA(a),
            but=self._answerButtons())
        self._renderQA(c, buf)
        runHook('showQuestion')

        # buf = self.typeAnsResult()
        # self.write(self.center('<span id=answer />'
        #                        + mungeQA(a)))

    def onLoadFinished(self, bool):
        if self.state == "showAnswer":
            if self.main.config['scrollToAnswer']:
                mf = self.body.page().mainFrame()
                mf.evaluateJavaScript("location.hash = 'answer'")

    # Answer buttons
    ##########################################################################

    def _answerButtons(self):
        # attach to mw.cardAnswered()
        self.updateEaseButtons()
        self.form.buttonStack.setCurrentIndex(1)
        self.form.buttonStack.show()
        self.form.buttonStack.setLayoutDirection(Qt.LeftToRight)
        if self.learningButtons():
            self.form.easeButton2.setText(_("Good"))
            self.form.easeButton3.setText(_("Easy"))
            self.form.easeButton4.setText(_("Very Easy"))
        else:
            self.form.easeButton2.setText(_("Hard"))
            self.form.easeButton3.setText(_("Good"))
            self.form.easeButton4.setText(_("Easy"))
        getattr(self.form, "easeButton%d" % self.defaultEaseButton()).\
                              setFocus()

    def learningButtons(self):
        return not self.currentCard.successive

    def defaultEaseButton(self):
        if not self.currentCard.successive:
            return 2
        else:
            return 3

    def updateEaseButtons(self):
        nextInts = {}
        for i in range(1, 5):
            l = getattr(self.form, "easeLabel%d" % i)
            if self.config['suppressEstimates']:
                l.setText("")
            elif i == 1:
                txt = _("Soon")
                if self.config['colourTimes']:
                    txt = '<span style="color: #700"><b>%s</b></span>' % txt
                l.setText(txt)
            else:
                txt = self.deck.nextIntervalStr(
                    self.currentCard, i)
                txt = "<b>" + txt + "</b>"
                if i == self.defaultEaseButton() and self.config['colourTimes']:
                    txt = '<span style="color: #070">' + txt + '</span>'
                l.setText(txt)

    # Font properties & output
    ##########################################################################

    def flush(self):
        "Write the current HTML buffer to the screen."
        self.buffer = self.addStyles() + self.buffer
        # hook for user css
        runHook("preFlushHook")
        self.buffer = '''<html><head>%s</head><body>%s</body></html>''' % (
            getBase(self.main.deck, self.card), self.buffer)
        #print self.buffer.encode("utf-8")
        b = self.buffer
        # Feeding webkit unicode can result in it not finding images, so on
        # linux/osx we percent escape the image paths as utf8. On Windows the
        # problem is more complicated - if we percent-escape as utf8 it fixes
        # some images but breaks others. When filenames are normalized by
        # dropbox they become unreadable if we escape them.
        if not sys.platform.startswith("win32") and self.main.deck:
            # and self.main.config['mediaLocation'] == "dropbox"):
            b = self.main.deck.media.escapeImages(b)
        self.body.setHtml(b)

    def write(self, text):
        if type(text) != types.UnicodeType:
            text = unicode(text, "utf-8")
        self.buffer += text

    def center(self, str, height=40):
        if not self.main.config['splitQA']:
            return "<center>" + str + "</center>"
        return '''\
<center><div style="display: table; height: %s%%; width:100%%; overflow: hidden;">\
<div style="display: table-cell; vertical-align: middle;">\
<div style="">%s</div></div></div></center>''' % (height, str)

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
        self.switchToWelcomeScreen()
        self.disableCardMenuItems()
