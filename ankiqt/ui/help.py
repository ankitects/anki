# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sys
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt.forms

# Hideable help area widget
##########################################################################

class HelpArea(object):

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

    def show(self):
        "Show the help area."
        self.helpFrame.show()
        self.widget.show()

    def hide(self):
        self.currentKey = None
        self.helpFrame.hide()
        self.widget.hide()
        if self.mainWindow:
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
