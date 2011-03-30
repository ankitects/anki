# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import os, tempfile
from aqt.webview import AnkiWebView
from aqt.utils import saveGeom, restoreGeom
from anki.hooks import addHook
import aqt

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

# Deck Stats
######################################################################

class DeckStats(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw)
        self.mw = mw
        self.name = "deckStats"
        self.period = 0
        self.sel = True
        self.form = aqt.forms.stats.Ui_Dialog()
        self.oldPos = None
        f = self.form
        f.setupUi(self)
        restoreGeom(self, self.name)
        b = f.buttonBox.addButton(_("Save Image"),
                                          QDialogButtonBox.ActionRole)
        b.connect(b, SIGNAL("clicked()"), self.browser)
        b.setAutoDefault(False)
        c = self.connect
        s = SIGNAL("clicked()")
        c(f.groups, s, lambda: self.changeSel(True))
        f.groups.setShortcut("g")
        c(f.all, s, lambda: self.changeSel(False))
        c(f.month, s, lambda: self.changePeriod(0))
        c(f.year, s, lambda: self.changePeriod(1))
        c(f.life, s, lambda: self.changePeriod(2))
        c(f.web, SIGNAL("loadFinished(bool)"), self.loadFin)
        self.refresh()
        self.exec_()

    def reject(self):
        saveGeom(self, self.name)
        QDialog.reject(self)

    def browser(self):
        # dump to a temporary file
        tmpdir = tempfile.mkdtemp(prefix="anki")
        path = os.path.join(tmpdir, "report.png")
        p = self.form.web.page()
        oldsize = p.viewportSize()
        p.setViewportSize(p.mainFrame().contentsSize())
        image = QImage(p.viewportSize(), QImage.Format_ARGB32)
        painter = QPainter(image)
        p.mainFrame().render(painter)
        painter.end()
        image.save(path, "png")
        p.setViewportSize(oldsize)
        QDesktopServices.openUrl(QUrl("file://" + path))

    def changePeriod(self, n):
        self.period = n
        self.refresh()

    def changeSel(self, sel):
        self.sel = sel
        if sel:
            self.mw.onGroups(self)
        self.refresh()

    def loadFin(self, b):
        self.form.web.page().mainFrame().setScrollPosition(self.oldPos)

    def refresh(self):
        self.mw.progress.start(immediate=True)
        self.oldPos = self.form.web.page().mainFrame().scrollPosition()
        self.report = self.mw.deck.stats().report(
            type=self.period, selective=self.sel)
        self.form.web.setHtml(self.report)

        self.mw.progress.finish()
