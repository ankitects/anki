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


    # Showing the question
    ##########################################################################

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
        q = mungeQA(q)
        self.handleTypeAnsQ()
        self._renderQA(c, q)

    def _renderQA(self, card, text):
        self.web.stdHtml(text, card.model().css, bodyClass=card.bgClass())

    def addStyles(self):
        # card styles
        s = "<style>\n"
        if self.main.deck:
            s += self.main.deck.css
        s = runFilter("addStyles", s, self.card)
        s += "</style>"
        return s


    # Q/A support
    ##########################################################################

    def _showQuestionState(self, oldState):
        # ensure cwd set to media dir
        self.deck.mediaDir()
        self.showAnswerButton()
        self.updateMarkAction()
        runHook('showQuestion')

    # Showing the answer
    ##########################################################################

        # elif self.state == "showAnswer":
        #     self.setBackground()
        #     if not self.card.cardModel.questionInAnswer:
        #         self.drawQuestion(nosound=True)
        #     if self.drawRule:
        #         self.write("<hr>")
        #     self.drawAnswer()


    def _showAnswerState(self, oldState):
        self.showEaseButtons()

    def drawAnswer(self):
        "Show the answer."
        a = self.card.htmlAnswer()
        a = runFilter("drawAnswer", a, self.card)
        if self.card.cardModel.typeAnswer:
            try:
                cor = stripMedia(stripHTML(self.card.fact[
                    self.card.cardModel.typeAnswer]))
            except KeyError:
                self.card.cardModel.typeAnswer = ""
                cor = ""
            if cor:
                given = unicode(self.main.typeAnswerField.text())
                res = self.correct(cor, given)
                a = res + "<br>" + a
        self.write(self.center('<span id=answer />'
                               + mungeQA(a)))
        if self.state != self.oldState and self.main.config['autoplaySounds']:
            playFromText(a)

    def onLoadFinished(self, bool):
        if self.state == "showAnswer":
            if self.main.config['scrollToAnswer']:
                mf = self.body.page().mainFrame()
                mf.evaluateJavaScript("location.hash = 'answer'")

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

    def handleTypeAnsQ(self):
        return
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
