# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from aqt.webview import AnkiWebView
from anki.hooks import addHook

# Card stats
######################################################################

class CardStats(object):
    def __init__(self, mw):
        self.mw = mw
        self.shown = False
        addHook("showQuestion", self._update)
        addHook("deckClosing", self.hide)

    def show(self):
        if not self.shown:
            self.web = AnkiWebView(self.mw)
            self.web.setMaximumWidth(400)
            self.shown = self.mw.addDockable(_("Card Statistics"), self.web)
            self.shown.connect(self.shown, SIGNAL("visibilityChanged(bool)"),
                               self._visChange)
        self._update()

    def hide(self):
        if self.shown:
            self.mw.rmDockable(self.shown)
            self.shown = None

    def _visChange(self, vis):
        if not vis:
            # schedule removal for after evt has finished
            self.mw.progress.timer(100, self.hide, False)

    def _update(self):
        if not self.shown:
            return
        txt = ""
        r = self.mw.reviewer
        d = self.mw.deck
        if r.card:
            txt += _("<h1>Current card</h1>")
            txt += d.cardStats(r.card)
        lc = r.lastCard()
        if lc:
            txt += _("<h1>Last card</h1>")
            txt += d.cardStats(lc)
        if not txt:
            txt = _("No current card or last card.")
        print txt
        self.web.setHtml("""
<html><head>
<style>table { font-size: 12px; } h1 { font-size: 14px; }</style>
</head><body><center>%s</center></body></html>"""%txt)

# Deck stats
######################################################################

class DeckStats(QDialog):

    def __init__(self, mw):
        self.mw = mw
        QDialog.__init__(self, mw)
        self.setWindowTitle(_("Deck Statistics"))
        self.setModal(True)
        self.mw.progress.start()
        self.web = AnkiWebView(self)
        stats = self.mw.deck.deckStats()
        l = QVBoxLayout(self)
        l.setContentsMargins(0,0,0,0)
        l.addWidget(self.web)
        self.setLayout(l)
        self.web.stdHtml(stats, css=self.mw.sharedCSS+"""
body { margin: 2em; }
h1 { font-size: 18px; border-bottom: 1px solid #000; margin-top: 1em;
     clear: both; }
.info {float:right; padding: 10px; max-width: 300px; border-radius: 5px;
  background: #ddd; font-size: 14px; }
""")
        self.mw.progress.finish()
        self.exec_()
