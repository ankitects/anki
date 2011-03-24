# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, tempfile
from aqt.webview import AnkiWebView
from aqt.utils import saveGeom, restoreGeom
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
            self.shown = self.mw.addDockable(_("Card Info"), self.web)
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
        self.web.setHtml("""
<html><head>
<style>table { font-size: 12px; } h1 { font-size: 14px; }</style>
</head><body><center>%s</center></body></html>"""%txt)

# Deck stats
######################################################################

class PrintableReport(QDialog):

    def __init__(self, mw, type, title, func, css):
        self.mw = mw
        QDialog.__init__(self, mw)
        restoreGeom(self, type)
        self.type = type
        self.setWindowTitle(title)
        self.setModal(True)
        self.mw.progress.start()
        self.web = AnkiWebView(self)
        stats = func()
        l = QVBoxLayout(self)
        l.setContentsMargins(0,0,0,0)
        l.addWidget(self.web)
        self.setLayout(l)
        self.report = func()
        self.css = css
        self.web.stdHtml(self.report, css=css)
        box = QDialogButtonBox(QDialogButtonBox.Close)
        b = box.addButton(_("Open In Browser"), QDialogButtonBox.AcceptRole)
        box.button(QDialogButtonBox.Close).setDefault(True)
        l.addWidget(box)
        self.connect(box, SIGNAL("accepted()"), self.browser)
        self.connect(box, SIGNAL("rejected()"), self, SLOT("reject()"))
        self.mw.progress.finish()
        self.exec_()

    def reject(self):
        saveGeom(self, self.type)
        QDialog.reject(self)

    def browser(self):
        # dump to a temporary file
        tmpdir = tempfile.mkdtemp(prefix="anki")
        path = os.path.join(tmpdir, "report.html")
        open(path, "w").write("""
<html><head><style>%s</style></head><body>%s</body></html>""" % (
    self.css, self.report))
        QDesktopServices.openUrl(QUrl("file://" + path))

def deckStats(mw):
    css=mw.sharedCSS+"""
body { margin: 2em; font-family: arial; }
h1 { font-size: 18px; border-bottom: 1px solid #000; margin-top: 1em;
     clear: both; margin-bottom: 0.5em; }
.info {float:right; padding: 10px; max-width: 300px; border-radius: 5px;
  background: #ddd; font-size: 14px; }
"""
    return PrintableReport(
        mw,
        "deckstats",
        _("Deck Statistics"),
        mw.deck.deckStats,
        css)
