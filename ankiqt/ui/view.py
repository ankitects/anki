# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, anki.utils
from anki.sound import playFromText
from anki.utils import stripHTML
from anki.hooks import runHook, runFilter
from anki.media import stripMedia, escapeImages
import types, time, re, os, urllib, sys, difflib
import unicodedata as ucd
from ankiqt import ui
from ankiqt.ui.utils import mungeQA, getBase
from anki.utils import fmtTimeSpan
from PyQt4.QtWebKit import QWebPage, QWebView

failedCharColour = "#FF0000"
passedCharColour = "#00FF00"
futureWarningColour = "#FF0000"

# Views - define the way a user is prompted for questions, etc
##########################################################################

class View(object):
    "Handle the main window update as we transition through various states."

    def __init__(self, parent, body, frame=None):
        self.main = parent
        self.body = body
        self.frame = frame
        self.main.connect(self.body, SIGNAL("loadFinished(bool)"),
                          self.onLoadFinished)

    # State control
    ##########################################################################

    def setState(self, state):
        "Change to STATE, and update the display."
        self.oldState = getattr(self, 'state', None)
        self.state = state
        if self.state == "initial":
            return
        elif self.state == "noDeck":
            self.clearWindow()
            self.drawWelcomeMessage()
            self.flush()
            return
        self.redisplay()

    def redisplay(self):
        "Idempotently display the current state (prompt for question, etc)"
        if self.state == "noDeck" or self.state == "studyScreen":
            return
        self.buffer = ""
        self.haveTop = self.needFutureWarning()
        self.drawRule = (self.main.config['qaDivider'] and
                         self.main.currentCard and
                         not self.main.currentCard.cardModel.questionInAnswer)
        if not self.main.deck.isEmpty():
            if self.haveTop:
                self.drawTopSection()
        if self.state == "showQuestion":
            self.setBackground()
            self.drawQuestion()
            if self.drawRule:
                self.write("<hr>")
        elif self.state == "showAnswer":
            self.setBackground()
            if not self.main.currentCard.cardModel.questionInAnswer:
                self.drawQuestion(nosound=True)
            if self.drawRule:
                self.write("<hr>")
            self.drawAnswer()
        elif self.state == "deckEmpty":
            self.drawWelcomeMessage()
        elif self.state == "deckFinished":
            self.drawDeckFinishedMessage()
        self.flush()

    def addStyles(self):
        # card styles
        s = "<style>\n"
        if self.main.deck:
            s += self.main.deck.css
        s = runFilter("addStyles", s, self.main.currentCard)
        s += "</style>"
        return s

    def clearWindow(self):
        self.body.setHtml("")
        self.buffer = ""

    def setBackground(self):
        col = self.main.currentCard.cardModel.lastFontColour
        self.write("<style>html { background: %s;}</style>" % col)

    # Font properties & output
    ##########################################################################

    def flush(self):
        "Write the current HTML buffer to the screen."
        self.buffer = self.addStyles() + self.buffer
        # hook for user css
        runHook("preFlushHook")
        self.buffer = '''<html><head>%s</head><body>%s</body></html>''' % (
            getBase(self.main.deck, self.main.currentCard), self.buffer)
        #print self.buffer.encode("utf-8")
        b = self.buffer
        # Feeding webkit unicode can result in it not finding images, so on
        # linux/osx we percent escape the image paths as utf8. On Windows the
        # problem is more complicated - if we percent-escape as utf8 it fixes
        # some images but breaks others. When filenames are normalized by
        # dropbox they become unreadable if we escape them.
        if not sys.platform.startswith("win32"):
            # and self.main.config['mediaLocation'] == "dropbox"):
            b = escapeImages(b)
        self.body.setHtml(b)

    def write(self, text):
        if type(text) != types.UnicodeType:
            text = unicode(text, "utf-8")
        self.buffer += text

    # Question and answer
    ##########################################################################

    def center(self, str, height=40):
        if not self.main.config['splitQA']:
            return "<center>" + str + "</center>"
        return '''\
<center><div style="display: table; height: %s%%; width:100%%; overflow: hidden;">\
<div style="display: table-cell; vertical-align: middle;">\
<div style="">%s</div></div></div></center>''' % (height, str)

    def drawQuestion(self, nosound=False):
        "Show the question."
        if not self.main.config['splitQA']:
            self.write("<br>")
        q = self.main.currentCard.htmlQuestion()
        if self.haveTop:
            height = 35
        elif self.main.currentCard.cardModel.questionInAnswer:
            height = 40
        else:
            height = 45
        q = runFilter("drawQuestion", q, self.main.currentCard)
        self.write(self.center(self.mungeQA(self.main.deck, q), height))
        if (self.state != self.oldState and not nosound
            and self.main.config['autoplaySounds']):
            playFromText(q)
        if self.main.currentCard.cardModel.typeAnswer:
            self.adjustInputFont()

    def getFont(self):
        sz = 20
        fn = u"Arial"
        for fm in self.main.currentCard.fact.model.fieldModels:
            if fm.name == self.main.currentCard.cardModel.typeAnswer:
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

    def drawAnswer(self):
        "Show the answer."
        a = self.main.currentCard.htmlAnswer()
        a = runFilter("drawAnswer", a, self.main.currentCard)
        if self.main.currentCard.cardModel.typeAnswer:
            try:
                cor = stripMedia(stripHTML(self.main.currentCard.fact[
                    self.main.currentCard.cardModel.typeAnswer]))
            except KeyError:
                self.main.currentCard.cardModel.typeAnswer = ""
                cor = ""
            if cor:
                given = unicode(self.main.typeAnswerField.text())
                res = self.correct(cor, given)
                a = res + "<br>" + a
        self.write(self.center('<span id=answer />'
                               + self.mungeQA(self.main.deck, a)))
        if self.state != self.oldState and self.main.config['autoplaySounds']:
            playFromText(a)

    def mungeQA(self, deck, txt):
        txt = mungeQA(deck, txt)
        # hack to fix thai presentation issues
        if self.main.config['addZeroSpace']:
            txt = txt.replace("</span>", "&#8203;</span>")
        return txt

    def onLoadFinished(self, bool):
        if self.state == "showAnswer":
            if self.main.config['scrollToAnswer']:
                mf = self.body.page().mainFrame()
                mf.evaluateJavaScript("location.hash = 'answer'")

    # Top section
    ##########################################################################

    def drawTopSection(self):
        "Show previous card, next scheduled time, and stats."
        self.buffer += "<center>"
        self.drawFutureWarning()
        self.buffer += "</center>"

    def needFutureWarning(self):
        if not self.main.currentCard:
            return
        if self.main.currentCard.due <= self.main.deck.dueCutoff:
            return
        if self.main.currentCard.due - time.time() <= self.main.deck.delay0:
            return
        if self.main.deck.scheduler == "cram":
            return
        return True

    def drawFutureWarning(self):
        if not self.needFutureWarning():
            return
        self.write("<span style='color: %s'>" % futureWarningColour +
                   _("This card was due in %s.") % fmtTimeSpan(
            self.main.currentCard.due - time.time(), after=True) +
                   "</span>")

    # Welcome/empty/finished deck messages
    ##########################################################################

    def drawWelcomeMessage(self):
        self.main.mainWin.welcomeText.setText("""\
<h1>%(welcome)s</h1>
<p>
<table>

<tr>
<td width=50>
<a href="welcome:addfacts"><img src=":/icons/list-add.png"></a>
</td>
<td valign=middle><h1><a href="welcome:addfacts">%(add)s</a></h1>
%(start)s</td>
</tr>

</table>

<br>
<table>

<tr>
<td width=50>
<a href="welcome:back"><img src=":/icons/go-previous.png"></a>
</td>
<td valign=middle><h2><a href="welcome:back">%(back)s</a></h2></td>
</tr>

</table>""" % \
	{"welcome":_("Welcome to Anki!"),
         "add":_("Add Material"),
         "start":_("Start adding your own material."),
         "back":_("Back to Deck Browser"),
         })

    def drawDeckFinishedMessage(self):
        "Tell the user the deck is finished."
        self.main.mainWin.congratsLabel.setText(
            self.main.deck.deckFinishedMsg())

class AnkiWebView(QWebView):

    def __init__(self, *args):
        QWebView.__init__(self, *args)
        self.setObjectName("mainText")

    def keyPressEvent(self, evt):
        if evt.matches(QKeySequence.Copy):
            self.triggerPageAction(QWebPage.Copy)
            evt.accept()
        QWebView.keyPressEvent(self, evt)

    def contextMenuEvent(self, evt):
        QWebView.contextMenuEvent(self, evt)

    def dropEvent(self, evt):
        pass
