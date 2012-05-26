# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from anki.consts import NEW_CARDS_RANDOM, dynOrderLabels
from anki.hooks import addHook
from aqt.utils import showInfo, openLink, shortcut
from anki.utils import isMac
import aqt
from anki.sound import clearAudioQueue

class Overview(object):
    "Deck overview."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)

    def show(self):
        clearAudioQueue()
        self.web.setLinkHandler(self._linkHandler)
        self.web.setKeyHandler(None)
        self.mw.keyHandler = self._keyHandler
        self.mw.web.setFocus()
        self.refresh()

    def refresh(self):
        self.mw.col.reset()
        self._renderPage()
        self._renderBottom()

    # Handlers
    ############################################################

    def _linkHandler(self, url):
        if url == "study":
            self.mw.col.startTimebox()
            self.mw.moveToState("review")
        elif url == "anki":
            print "anki menu"
        elif url == "opts":
            self.mw.onDeckConf()
        elif url == "cram":
            deck = self.mw.col.decks.current()
            self.mw.onCram("'deck:%s'" % deck['name'])
        elif url == "refresh":
            self.mw.col.sched.rebuildDyn()
            self.mw.reset()
        elif url == "empty":
            self.mw.col.sched.emptyDyn(self.mw.col.decks.selected())
            self.mw.reset()
        elif url == "decks":
            self.mw.moveToState("deckBrowser")
        elif url == "review":
            openLink(aqt.appShared+"info/%s?v=%s"%(self.sid, self.sidVer))
        elif url == "limits":
            self.onLimits()

    def _keyHandler(self, evt):
        cram = self.mw.col.decks.current()['dyn']
        key = unicode(evt.text())
        if key == "o":
            self.mw.onDeckConf()
        if key == "f" and not cram:
            self.mw.onCram()
        if key == "r" and cram:
            self.mw.col.sched.rebuildDyn()
            self.mw.reset()
        if key == "e" and cram:
            self.mw.col.sched.emptyDyn(self.mw.col.decks.selected())
            self.mw.reset()
        if key == "l":
            self.onLimits()

    # HTML
    ############################################################

    def _renderPage(self):
        but = self.mw.button
        deck = self.mw.col.decks.current()
        self.sid = deck.get("sharedFrom")
        if self.sid:
            self.sidVer = deck.get("ver", None)
            shareLink = '<a class=smallLink href="review">Reviews and Updates</a>'
        else:
            shareLink = ""
        self.web.stdHtml(self._body % dict(
            deck=deck['name'],
            shareLink=shareLink,
            desc=self._desc(deck),
            table=self._table()
            ), self.mw.sharedCSS + self._css)

    def _desc(self, deck):
        if deck['dyn']:
            search, limit, order  = deck['terms'][0]
            desc = "%s<br>%s" % (
                _("Search: %s") % search,
                _("Order: %s") % dynOrderLabels()[order].lower())
        else:
            desc = deck.get("desc", "")
        if not desc:
            return "<p>"
        if deck['dyn']:
            dyn = "dyn"
        else:
            dyn = ""
        if len(desc) < 160 or dyn:
            return '<div class="descfont descmid description %s">%s</div>' % (
                dyn, desc)
        else:
            return '''
<div class="descfont description descmid" id=shortdesc>%s\
 <a class=smallLink href=# onclick="$('#shortdesc').hide();$('#fulldesc').show();">...More</a></div>
<div class="descfont description descmid" id=fulldesc>%s</div>''' % (
                 desc[:160], desc)

    def _table(self):
        counts = list(self.mw.col.sched.counts())
        finished = not sum(counts)
        for n in range(len(counts)):
            if counts[n] == 1000:
                counts[n] = "1000+"
        but = self.mw.button
        if finished:
            return '<div style="white-space: pre-wrap;">%s</div>' % (
                self.mw.col.sched.finishedMsg())
        else:
            return '''
<table width=300 cellpadding=5>
<tr><td align=center valign=top>
<table cellspacing=5>
<tr><td>%s:</td><td><b><font color=#00a>%s</font></b></td></tr>
<tr><td>%s:</td><td><b><font color=#a00>%s</font></b></td></tr>
<tr><td>%s:</td><td><b><font color=#0a0>%s</font></b></td></tr>
</table>
</td><td align=center>
%s</td></tr></table>''' % (
    _("New"), counts[0],
    _("Learning"), counts[1],
    _("To Review"), counts[2],
    but("study", _("Study Now"), id="study"))


    _body = """
<center>
<h3>%(deck)s</h3>
%(shareLink)s
%(desc)s
%(table)s
</center>
<script>$(function () { $("#study").focus(); });</script>
"""

    _css = """
.smallLink { font-size: 10px; }
h3 { margin-bottom: 0; }
.descfont {
padding: 1em; color: #333;
}
.description {
white-space: pre-wrap;
}
#fulldesc {
display:none;
}
.descmid {
width: 70%;
margin: 0 auto 0;
text-align: left;
}
.dyn {
text-align: center;
}
"""

    # Bottom area
    ######################################################################

    def _renderBottom(self):
        links = [
            ["o", "opts", _("Options")],
        ]
        if self.mw.col.decks.current()['dyn']:
            links.append(["R", "refresh", _("Rebuild")])
            links.append(["E", "empty", _("Empty")])
        else:
            if not sum(self.mw.col.sched.counts()):
                if self.mw.col.sched.newDue() or \
                   self.mw.col.sched.revDue():
                    links.append(["L", "limits", _("Study More")])
            links.append(["F", "cram", _("Filter/Cram")])
        buf = ""
        for b in links:
            if b[0]:
                b[0] = _("Shortcut key: %s") % shortcut(b[0])
            buf += """
<button title="%s" onclick='py.link(\"%s\");'>%s</button>""" % tuple(b)
        self.bottom.draw(buf)
        if isMac:
            size = 28
        else:
            size = 36 + self.mw.fontHeightDelta*3
        self.bottom.web.setFixedHeight(size)
        self.bottom.web.setLinkHandler(self._linkHandler)

    # Today's limits
    ######################################################################

    def onLimits(self):
        d = QDialog(self.mw)
        frm = aqt.forms.limits.Ui_Dialog()
        frm.setupUi(d)
        deck = self.mw.col.decks.current()
        frm.newToday.setValue(deck.get('extendNew', 10))
        frm.revToday.setValue(deck.get('extendRev', 50))
        def accept():
            n = deck['extendNew'] = frm.newToday.value()
            r = deck['extendRev'] = frm.revToday.value()
            self.mw.col.decks.save(deck)
            self.mw.col.sched.extendLimits(n, r)
            self.mw.reset()
        d.connect(frm.buttonBox, SIGNAL("accepted()"), accept)
        d.exec_()

