# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from aqt.qt import *
from anki.consts import NEW_CARDS_RANDOM
from anki.hooks import addHook
from aqt.utils import showInfo, openLink
from anki.utils import isMac
import aqt

class Overview(object):
    "Deck overview."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)

    def show(self):
        self.web.setLinkHandler(self._linkHandler)
        self.web.setKeyHandler(None)
        self.mw.keyHandler = None
        self.refresh()

    def refresh(self):
        self.mw.col.reset()
        self._renderPage()
        self._renderBottom()
        self.mw.web.setFocus()

    # Handlers
    ############################################################

    def _linkHandler(self, url):
        print "link", url
        if url == "study":
            self.mw.moveToState("review")
        elif url == "anki":
            print "anki menu"
        elif url == "opts":
            self.mw.onDeckConf()
        elif url == "refresh":
            self.mw.col.sched.rebuildDyn()
            self.mw.reset()
        elif url == "decks":
            self.mw.moveToState("deckBrowser")
        elif url == "review":
            openLink(aqt.appShared+"info/%s?v=%s"%(self.sid, self.sidVer))

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
        desc = deck.get("desc", "")
        if not desc:
            return "<p>"
        if len(desc) < 160:
            return '<div class="descfont descmid description">%s</div>' % desc
        else:
            return '''
<div class="descfont description descmid" id=shortdesc>%s\
 <a class=smallLink href=# onclick="$('#shortdesc').hide();$('#fulldesc').show();">...More</a></div>
<div class="descfont description descmid" id=fulldesc>%s</div>''' % (
                 desc[:160], desc)

    def _table(self):
        counts = self.mw.col.sched.counts()
        finished = not sum(counts)
        but = self.mw.button
        if finished:
            return '<div class=fin style="white-space: pre-wrap;">%s</div>' % (
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
.fin { font-size: 12px; font-weight: normal; }
td { font-size: 14px; }
.descfont {
font-size: 12px;
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
"""

    # Bottom area
    ######################################################################

    def _renderBottom(self):
        links = [
            ["opts", _("Options")],
            ["refresh", _("Rebuild")],
        ]
        buf = ""
        for b in links:
            buf += "<button onclick='py.link(\"%s\");'>%s</button>" % tuple(b)
        self.bottom.draw(buf)
        self.bottom.web.setFixedHeight(isMac and 28 or 36)
        self.bottom.web.setLinkHandler(self._linkHandler)
