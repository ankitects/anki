# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import simplejson
from aqt.qt import *
from anki.consts import NEW_CARDS_RANDOM
from anki.hooks import addHook
from aqt.utils import showInfo
import aqt

class Overview(object):
    "Deck overview."

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)
        addHook("reset", self.refresh)

    def show(self):
        self.web.setLinkHandler(self._linkHandler)
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
        elif url == "cram":
            return showInfo("not yet implemented")
            #self.mw.col.cramGroups()
            #self.mw.moveToState("review")
        elif url == "opts":
            self.mw.onDeckConf()
        elif url == "decks":
            self.mw.moveToState("deckBrowser")

    # HTML
    ############################################################

    def _renderPage(self):
        but = self.mw.button
        deck = self.mw.col.decks.current()
        sid = deck.get("sharedFrom")
        if sid:
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
            return ""
        if len(desc) < 160:
            return '<div class="descfont description">%s</div>' % desc
        else:
            return '''
<div class="descfont description descmid" id=shortdesc>%s\
<a href=# onclick="$('shortdesc').hide();$('fulldesc').show();">...More</a></div>
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
    _("Reviewing"), counts[2],
    but("study", _("Study Now"), id="study"))


    _body = """
<center>
<h3>%(deck)s</h3>
%(shareLink)s
%(desc)s
<p>
%(table)s
</center>
<script>$(function () { $("#study").focus(); });</script>
"""

    _css = """
.smallLink { font-size: 10px; }
h3 { margin-bottom: 0; }
.fin { font-size: 12px; font-weight: normal; }
td { font-size: 14px; }
button { font-weight: bold; }
"""

    # Bottom area
    ######################################################################

    def _renderBottom(self):
        links = [
            ["opts", _("Options")],
            ["cram", _("Cram")],
        ]
        buf = ""
        for b in links:
            buf += "<button onclick='py.link(\"%s\");'>%s</button>" % tuple(b)
        self.bottom.draw(buf)
        self.bottom.web.setFixedHeight(32)
        self.bottom.web.setLinkHandler(self._linkHandler)

