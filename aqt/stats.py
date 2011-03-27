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
            txt += _("<h1>Current</h1>")
            txt += d.cardStats(r.card)
        lc = r.lastCard()
        if lc:
            txt += _("<h1>Last</h1>")
            txt += d.cardStats(lc)
        if not txt:
            txt = _("No current card or last card.")
        self.web.setHtml("""
<html><head>
<style>table { font-size: 12px; } h1 { font-size: 14px; }</style>
</head><body><center>%s</center></body></html>"""%txt)

# Modal dialog that supports dumping to browser (for printing, etc)
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
        l = QVBoxLayout(self)
        l.setContentsMargins(0,0,0,0)
        l.addWidget(self.web)
        self.css = css
        if func:
            self.report = func()
            self.web.stdHtml(self.report, css=css)
        self.box = QDialogButtonBox(QDialogButtonBox.Close)
        b = self.box.addButton(_("Save Image"), QDialogButtonBox.ActionRole)
        b.connect(b, SIGNAL("clicked()"), self.browser)
        b.setAutoDefault(False)
        self.layout = QHBoxLayout()
        self.layout.setContentsMargins(0,0,0,0)
        self.layout.addWidget(self.box)
        l.addLayout(self.layout)
        self.setLayout(l)
        self.connect(self.box, SIGNAL("rejected()"), self, SLOT("reject()"))
        self.mw.progress.finish()

    def run(self):
        self.exec_()

    def reject(self):
        saveGeom(self, self.type)
        QDialog.reject(self)

    def browser(self):
        # dump to a temporary file
        tmpdir = tempfile.mkdtemp(prefix="anki")
        path = os.path.join(tmpdir, "report.png")
        p = self.web.page()
        oldsize = p.viewportSize()
        p.setViewportSize(p.mainFrame().contentsSize())
        image = QImage(p.viewportSize(), QImage.Format_ARGB32)
        painter = QPainter(image)
        p.mainFrame().render(painter)
        painter.end()
        image.save(path, "png")
        p.setViewportSize(oldsize)
        QDesktopServices.openUrl(QUrl("file://" + path))

# Deck stats
######################################################################

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
        css).run()

# Graphs
######################################################################

class Graphs(PrintableReport):

    def __init__(self, *args):
        self.period = 0
        self.periods = [
            _("1 month"),
            _("1 year"),
            _("deck life")]
        PrintableReport.__init__(self, *args)
        grp = QGroupBox()
        l = QHBoxLayout()
        l.setContentsMargins(6,6,6,6)
        chk = False
        for c, p in enumerate(self.periods):
            b = QRadioButton(p)
            if not chk:
                b.setChecked(True)
                chk = True
            b.connect(b, SIGNAL("clicked()"), lambda n=c: self.changePeriod(n))
            l.addWidget(b)
        grp.setLayout(l)
        self.layout.insertWidget(0, grp)
        self.layout.insertStretch(0, 10)
        self.refresh()

    def changePeriod(self, n):
        self.period = n
        self.refresh()

    def refresh(self):
        self.mw.progress.start(immediate=True)
        self.report = self.mw.deck.graphs().report(type=self.period)
        self.web.stdHtml(self.report, css=self.css)
        self.mw.progress.finish()

def graphs(mw):
    css=mw.sharedCSS+"""
body { margin: 2em; font-family: arial; background: #eee; }
h1 { font-size: 18px; border-bottom: 1px solid #000; margin-top: 1em;
     clear: both; margin-bottom: 0.5em; }
.info {float:right; padding: 10px; max-width: 300px; border-radius: 5px;
  background: #ddd; font-size: 14px; }
"""
    return Graphs(
        mw,
        "graphs",
        _("Graphs"),
        None,
        css).run()
