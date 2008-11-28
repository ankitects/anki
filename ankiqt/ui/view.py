# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, anki.utils
from anki.sound import playFromText, stripSounds
from anki.latex import renderLatex, stripLatex
from anki.utils import stripHTML
import types, time, re, os, urllib, sys
from ankiqt import ui
from ankiqt.ui.utils import mungeQA
from PyQt4.QtWebKit import QWebPage, QWebView

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
        if self.state == "noDeck":
            return
        self.clearWindow()
        self.setBackgroundColour()
        if not self.main.deck.isEmpty():
            if not self.main.lastCard or (
                not self.main.config['showLastCardContent'] and
                not self.main.config['showLastCardInterval']):
                self.buffer += "<br>"
            else:
                self.drawTopSection()
        if self.state == "showQuestion":
            self.drawQuestion()
        elif self.state == "showAnswer":
            if not self.main.currentCard.cardModel.questionInAnswer:
                self.drawQuestion(nosound=True)
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
        # last card
        for base in ("lastCard", "interface"):
            family = self.main.config[base + "FontFamily"]
            size = self.main.config[base + "FontSize"]
            color = ("; color: " + self.main.config[base + "Colour"])
            s += ('.%s {font-family: "%s"; font-size: %spx%s}\n' %
                            (base, family, size, color))
        # standard margins
        s += "</style>"
        return s

    def clearWindow(self):
        self.body.setHtml("")
        self.buffer = ""

    # Font properties & output
    ##########################################################################

    def flush(self):
        "Write the current HTML buffer to the screen."
        self.body.setHtml(self.addStyles() + '<div class="interface">' + self.buffer + "</div>")

    def write(self, text):
        if type(text) != types.UnicodeType:
            text = unicode(text, "utf-8")
        self.buffer += text

    def setBackgroundColour(self):
        p = QPalette()
        p.setColor(QPalette.Base, QColor(self.main.config['backgroundColour']))
        self.body.setPalette(p)
        if self.frame:
            p.setColor(QPalette.Background, QColor(self.main.config['backgroundColour']))
            self.frame.setPalette(p)

    # Question and answer
    ##########################################################################

    def drawQuestion(self, nosound=False):
        "Show the question."
        q = self.main.currentCard.htmlQuestion()
        self.write(mungeQA(self.main.deck, q))
        if self.state != self.oldState and not nosound:
            playFromText(q)

    def drawAnswer(self):
        "Show the answer."
        a = self.main.currentCard.htmlAnswer()
        self.write('<span id=answer />' + mungeQA(self.main.deck, a))
        if self.state != self.oldState:
            playFromText(a)

    def onLoadFinished(self):
        if self.state == "showAnswer":
            if self.main.config['scrollToAnswer']:
                mf = self.body.page().mainFrame()
                mf.evaluateJavaScript("location.hash = 'answer'")

    # Top section
    ##########################################################################

    def drawTopSection(self):
        "Show previous card, next scheduled time, and stats."
        self.buffer += "<center>"
        self.drawLastCard()
        self.buffer += "</center>"

    def drawLastCard(self):
        "Show the last card if not the current one, and next time."
        if self.main.lastCard:
            if self.main.config['showLastCardContent']:
                if (self.state == "deckFinished" or
                    self.main.currentCard.id != self.main.lastCard.id):
                    q = self.main.lastCard.question.replace("<br>", "  ")
                    q = stripHTML(q)
                    if len(q) > 50:
                        q = q[:50] + "..."
                    a = self.main.lastCard.answer.replace("<br>", "  ")
                    a = stripHTML(a)
                    if len(a) > 50:
                        a = a[:50] + "..."
                    s = "%s<br>%s" % (q, a)
                    s = stripLatex(s)
                    self.write('<span class="lastCard">%s</span><br>' % s)
            if self.main.config['showLastCardInterval']:
                if self.main.lastQuality > 1:
                    msg = _("Well done! This card will appear again in "
                            "<b>%(next)s</b>.") % \
                            {"next":self.main.lastScheduledTime}
                else:
                    msg = _("This card will appear again in less than "
                            "<b>%(next)s</b>.") % \
                            {"next":self.main.lastScheduledTime}
                self.write(msg)
            self.write("<br>")

    # Welcome/empty/finished deck messages
    ##########################################################################

    def drawWelcomeMessage(self):
        self.main.mainWin.welcomeText.setText(_("""
<h1>Welcome to Anki!</h1>
<p>
<table>

<tr>
<td width=50>
<a href="welcome:addfacts"><img src=":/icons/list-add.png"></a>
</td>
<td valign=middle><h1><a href="welcome:addfacts">Add material</a></h1>
Start adding your own material.</td>
</tr>

</table>

<br>
<table>

<tr>
<td>
<a href="welcome:open"><img src=":/icons/document-open.png"></a>
</td>
<td valign=middle><h2><a href="welcome:open">Open Local Deck</a></h2></td>
</tr>

<tr>
<td>
<a href="welcome:openrem"><img src=":/icons/document-open-remote.png"></a>
</td>
<td valign=middle><h2><a href="welcome:openrem">Open Online Deck</a></h2></td>
</tr>

<tr>
<td width=50>
<a href="welcome:sample"><img src=":/icons/anki.png"></a>
</td>
<td valign=middle><h2><a href="welcome:sample">Open Sample Deck</a></h2></td>
</tr>

<tr>
<td width=50>
<a href="welcome:more"><img src=":/icons/khtml_kget.png"></a>
</td>
<td valign=middle><h2><a href="welcome:more">Get More Decks</a></h2></td>
</tr>

</table>"""))

    def drawDeckFinishedMessage(self):
        "Tell the user the deck is finished."
        self.write("<br><center><table width=250><tr><td align='left'>" +
                   self.main.deck.deckFinishedMsg() +
                   "</td></tr></table></center>")

class AnkiWebView(QWebView):

    def keyPressEvent(self, evt):
        if evt.matches(QKeySequence.Copy):
            self.triggerPageAction(QWebPage.Copy)
            evt.accept()
        evt.ignore()

    def contextMenuEvent(self, evt):
        QWebView.contextMenuEvent(self, evt)
