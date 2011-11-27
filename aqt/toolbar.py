# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.webview import AnkiWebView

class Toolbar(object):

    def __init__(self, mw, web):
        self.mw = mw
        self.web = web
        self.web.page().mainFrame().setScrollBarPolicy(
            Qt.Vertical, Qt.ScrollBarAlwaysOff)
        self.web.setLinkHandler(self._linkHandler)
        self.draw()

    def draw(self):
        body = self._body % (self._centerLinks(), self._rightIcons())
        self.web.stdHtml(body, self._css)

    # Available links
    ######################################################################

    centerLinks = [
        ["decks", "Decks"],
        ["study", "Study"],
        ["add", "Add"],
        ["browse", "Browse"],
    ]

    rightIcons = [
        ["stats", "qrc:/icons/view-statistics.png"],
        ["sync", "qrc:/icons/view-refresh.png"],
    ]

    def _centerLinks(self):
        buf = ""
        for ln, name in self.centerLinks:
            buf += '<a class=hitem href="%s">%s</a>' % (ln, _(name))
        return buf

    def _rightIcons(self):
        buf = ""
        for ln, icon in self.rightIcons:
            buf += '<a class=hitem href="%s"><img src="%s"></a>' % (
                ln, icon)
        return buf

    # Link handling
    ######################################################################

    def _linkHandler(self, l):
        if l == "anki":
            self.showMenu()
        elif l  == "decks":
            self.mw.moveToState("deckBrowser")
        elif l == "study":
            self.mw.onOverview()
        elif l == "add":
            self.mw.onAddCard()
        elif l == "browse":
            self.mw.onBrowse()
        elif l == "stats":
            self.mw.onStats()
        elif l == "sync":
            self.mw.onSync()

    # HTML & CSS
    ######################################################################

    _body = """
<table id=header width=100%%>
<tr>
<td width=20%%><a class="hitem" href="anki">Anki &#9662</a></td>
<td align=center>%s</td>
<td width=20%% align=right>%s</td>
</tr></table>
"""

    _css = """
#header {
font-size: 12px;
margin:0;
background: -webkit-gradient(linear, left top, left bottom,
from(#ddd), to(#fff));
font-weight: bold;
height: 10px;
margin-bottom: 1px;
border-bottom: 1px solid #aaa;
}

body { margin: 0; padding: 0; }

.hitem { display: inline-block; padding: 4px; padding-right: 6px;
text-decoration: none; color: #000;
}
.hitem:hover {
background: #333;
color: #fff;
}
"""
