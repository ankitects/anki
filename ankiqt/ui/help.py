# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import sys
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import ankiqt.forms
from anki.hooks import runHook

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
        self.handlers = []
        self.widget.connect(self.widget, SIGNAL("anchorClicked(QUrl)"),
                            self.anchorClicked)
        if sys.platform.startswith("darwin"):
            self.widget.setFixedWidth(300)
        self.hide()

    def show(self):
        "Show the help area."
        self.helpFrame.show()
        self.widget.show()

    def hide(self):
        self.helpFrame.hide()
        self.widget.hide()
        if self.mainWindow:
            runHook("helpChanged")

    def showText(self, text, py={}):
        if "hide" in self.handlers:
            if self.handlers['hide'] != py.get('hide'):
                self.handlers["hide"]()
        self.show()
        self.buffer = text
        self.addHider()
        self.handlers = py
        self.flush()
        if self.mainWindow:
            runHook("helpChanged")

    def flush(self):
        if sys.platform.startswith("darwin"):
            font = "helvetica"
        else:
            font = "arial"
        # qt seems to ignore font-size on elements like h1
        style = "" #("<style>#content { font-family: %s; " +
                 #"font-size: 12px; }</style>\n") % font
        self.widget.setHtml(style + '<div id="content">' +
                            self.buffer + '</div>')

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
            if "hide" in self.handlers:
                self.handlers["hide"]()
        elif addr.startswith("py:"):
            key = addr[3:]
            if key in self.handlers:
                self.handlers[key]()
        else:
            # open in browser
            QDesktopServices.openUrl(QUrl(url))
        if self.focus:
            self.focus.setFocus()
