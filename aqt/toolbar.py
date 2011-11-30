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

    def draw(self):
        self.web.stdHtml(self._body % (
            # may want a context menu here in the future
            '&nbsp;'*20,
            self._centerLinks(),
            self._rightIcons()),
                         self._css, focus=False)

    # Available links
    ######################################################################

    rightIcons = [
        ["stats", "qrc:/icons/view-statistics.png"],
        ["sync", "qrc:/icons/view-refresh.png"],
    ]

    def _centerLinks(self):
        links = [
            ["decks", _("Decks")],
            ["study", _("Study")],
            ["add", _("Add")],
            ["browse", _("Browse")],
        ]
        return self._linkHTML(links)

    def _linkHTML(self, links):
        buf = ""
        for ln, name in links:
            buf += '<a class=hitem href="%s">%s</a>' % (ln, name)
            buf += "&nbsp;"*3
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
<td align=left>%s</td>
<td align=center>%s</td>
<td align=right>%s</td>
</tr></table>
"""

    _css = """
#header {
font-size: 12px;
margin:0;
background: -webkit-gradient(linear, left top, left bottom,
from(#ddd), to(#fff));
font-weight: bold;
margin-bottom: 1px;
border-bottom: 1px solid #aaa;
}

body {
margin: 0; padding: 0;
-webkit-user-select: none;
}

* { -webkit-user-drag: none; }

.hitem { display: inline-block; padding: 4px; padding-right: 6px;
text-decoration: none; color: #000;
}
.hitem:hover {
background: #333;
color: #fff;
}
"""

class BottomBar(Toolbar):

    _css = Toolbar._css + """
#header {
background: -webkit-gradient(linear, left top, left bottom,
from(#fff), to(#ddd));
border-bottom: 0;
border-top: 1px solid #aaa;
font-weight: normal;
margin-bottom: 6px;
}
"""

    _centerBody = """
<center><table width=100%% height=100%% id=header><tr><td align=center>
%s</td></tr></table></center>
"""

    def draw(self, buf):
        self.web.show()
        self.web.stdHtml(
            self._centerBody % buf,
            self._css)
