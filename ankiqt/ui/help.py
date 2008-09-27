# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sys
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt.forms

# Hideable help area widget
##########################################################################

class HelpArea(object):

    helpAreaWidth = 300
    minAppWidth = 550

    def __init__(self, helpFrame, config, mainWindow=None, focus=None):
        self.helpFrame = helpFrame
        self.widget = helpFrame.findChild(QTextBrowser)
        self.mainWindow = mainWindow
        if mainWindow:
            self.focus=mainWindow
        else:
            self.focus=focus
        self.config = config
        self.widget.connect(self.widget, SIGNAL("anchorClicked(QUrl)"),
                            self.anchorClicked)
        self.hide()
        self.data = HelpData()

    def getMinAppWidth(self):
        if self.config['easeButtonStyle'] == 'compact':
            return self.minAppWidth - 150
        return self.minAppWidth

    def show(self):
        "Show the help area."
        if self.mainWindow:
            self.mainWindow.setMinimumWidth(
                self.getMinAppWidth()+self.helpAreaWidth)
        self.helpFrame.show()
        self.widget.show()

    def hide(self):
        self.currentKey = None
        self.helpFrame.hide()
        self.widget.hide()
        if self.mainWindow:
            self.mainWindow.setMinimumWidth(self.getMinAppWidth())
            # force resize
            g = self.mainWindow.geometry()
            if g.width() < self.getMinAppWidth():
                self.mainWindow.setGeometry(QRect(g.left(),
                                                  g.top(),
                                                  self.getMinAppWidth(),
                                                  g.height()))
            self.mainWindow.runHook("helpChanged")

    def showKey(self, key, noFlush=False, dict=False):
        "Look up KEY in DATA and show."
        text = self.data[key]
        # accomodate some quirks in QTextEdit's html interpreter
        text = text.strip()
        if dict:
            text = text % dict
        self.showText(text, noFlush, key=key)

    def showHideableKey(self, key, dict=False):
        "Look up a hideable KEY in DATA and show."
        if self.config.get("hide:" + key, False):
            # user requested not to see this key. if previous key was help, we
            # need to hide it
            if self.currentKey in self.data:
                self.hide()
            return
        self.showKey(key, noFlush=True, dict=dict)
        self.addRemover(key)
        self.flush()

    def showText(self, text, noFlush=False, py={}, key="misc"):
        self.show()
        self.buffer = text
        self.addHider()
        self.handlers = py
        if not noFlush:
            self.flush()
        self.currentKey = key
        if self.mainWindow:
            self.mainWindow.runHook("helpChanged")

    def flush(self):
        if sys.platform.startswith("darwin"):
            font = "helvetica"
        else:
            font = "arial"
        # qt seems to ignore font-size on elements like h1
        style = ("<style>#content { font-family: %s; " +
                 "font-size: 12px; }</style>\n") % font
        self.widget.setHtml(style + '<div id="content">' +
                            self.buffer + '</div>')

    def addRemover(self, key):
        self.buffer += (" / <a href=hide:%s>" +
                        _("Don't show this again.")
                        + "</a>") % key

    def addHider(self):
        self.buffer += _("<p><a href=hide:>Hide this</a>")

    def anchorClicked(self, url):
        # prevent the link being handled
        self.widget.setSource(QUrl(""))
        addr = unicode(url.toString())
        if addr.startswith("hide:"):
            if len(addr) > 5:
                # hide for good
                self.config[addr] = True
            self.hide()
        elif addr.startswith("py:"):
            key = addr[3:]
            if key in self.handlers:
                self.handlers[key]()
        else:
            # open in browser
            QDesktopServices.openUrl(QUrl(url))
        if self.focus:
            self.focus.setFocus()

# Text strings
##########################################################################

class HelpData(dict):

    def __init__(self):
        self['learn'] = _("""
<h1>Learning new cards</h1>Anki is currently in 'learning mode'.
<p>
As an alternative to using the mouse, spacebar and the number keys are
available.
<p>
<a href="http://ichi2.net/anki/wiki/Learning_new_cards">More information</a>
""")

        self['review'] = _("""
<h1>Reviewing</h1>You are currently looking at a card you have seen before.
Unlike new cards, it's important to try and review previously seen cards as
promptly as possible, in order to ensure your previous effort spent
remembering the cards is not wasted.<p> At the bottom of the main window, the
"Remaining" figure indicates how many previously reviewed words are waiting
for you today. Once this number reaches 0, you can close Anki, or continue
studying new cards.""")

        self['finalReview'] = _("""<h1>Final review</h1>You are now being
shown cards that are due soon (in the next 5 hours by default). This includes
any cards you failed recently. You can answer them now, or come back later -
it's up to you.""")

        self['add'] = _("""
<h1>Adding cards</h1>
Please enter some things you want to learn.
<h2>Shortcuts</h2>
<table width=230>
<tr><td><b>Tab</b></td><td> change between fields.</td></tr>
<tr><td><b>Ctrl+Enter</b></td><td> add the current card.</td></tr>
<tr><td><b>Esc</b></td><td> close the dialog.</td></tr>
<tr><td><b>Ctrl+B</b></td><td> bold</td></tr>
<tr><td><b>Ctrl+I</b></td><td> italic</td></tr>
<tr><td><b>Ctrl+U</b></td><td> underline</td></tr>
<tr><td><b>Alt+1</b></td><td> enable/disable card model 1</td></tr>
<tr><td><b>Alt+2</b></td><td> enable/disable card model 2</td></tr>
</table>

<h2>Cards</h2>Depending on the language you selected, more than one card may
be generated. This allows you to practice both <b>Production</b> (trying to produce
the target idea/phrase yourself), and <b>Recognition</b> (quickly recognizing and
understanding the target idea/phrase). To change which cards are automatically
generated, click the rightmost button at the top.""")
