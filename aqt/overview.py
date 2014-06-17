# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.utils import  openLink, shortcut, tooltip
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
            if self.mw.state == "overview":
                tooltip(_("No cards are due yet."))
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
        elif url == "studymore":
            self.onStudyMore()
        elif url == "unbury":
            self.mw.col.sched.unburyCardsForDeck()
            self.mw.reset()
        elif url.lower().startswith("http"):
            openLink(url)

    def _keyHandler(self, evt):
        cram = self.mw.col.decks.current()['dyn']
        key = unicode(evt.text())
        if key == "o":
            self.mw.onDeckConf()
        if key == "r" and cram:
            self.mw.col.sched.rebuildDyn()
            self.mw.reset()
        if key == "e" and cram:
            self.mw.col.sched.emptyDyn(self.mw.col.decks.selected())
            self.mw.reset()
        if key == "c" and not cram:
            self.onStudyMore()
        if key == "u":
            self.mw.col.sched.unburyCardsForDeck()
            self.mw.reset()

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
            desc = _("""\
This is a special deck for studying outside of the normal schedule.""")
            desc += " " + _("""\
Cards will be automatically returned to their original decks after you review \
them.""")
            desc += " " + _("""\
Deleting this deck from the deck list will return all remaining cards \
to their original deck.""")
        else:
            desc = deck.get("desc", "")
        if not desc:
            return "<p>"
        if deck['dyn']:
            dyn = "dyn"
        else:
            dyn = ""
        return '<div class="descfont descmid description %s">%s</div>' % (
                dyn, desc)

    def _table(self):
        counts = list(self.mw.col.sched.counts())
        finished = not sum(counts)
        for n in range(len(counts)):
            if counts[n] >= 1000:
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
<tr><td>%s:</td><td><b><font color=#C35617>%s</font></b></td></tr>
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
            ["O", "opts", _("Options")],
        ]
        if self.mw.col.decks.current()['dyn']:
            links.append(["R", "refresh", _("Rebuild")])
            links.append(["E", "empty", _("Empty")])
        else:
            links.append(["C", "studymore", _("Custom Study")])
            #links.append(["F", "cram", _("Filter/Cram")])
        if self.mw.col.sched.haveBuried():
            links.append(["U", "unbury", _("Unbury")])
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

    # Studying more
    ######################################################################

    def onStudyMore(self):
        import aqt.customstudy
        aqt.customstudy.CustomStudy(self.mw)
