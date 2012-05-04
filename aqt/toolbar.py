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
                         self._css)

    # Available links
    ######################################################################

    def _rightIconsList(self):
        return [
            ["stats", "qrc:/icons/view-statistics.png",
             _("Show statistics. Shortcut key: %s") % "Shift+S"],
            ["sync", "qrc:/icons/view-refresh.png",
             _("Synchronize with AnkiWeb. Shortcut key: %s") % "Y"],
        ]

    def _centerLinks(self):
        links = [
            ["decks", _("Decks"), _("Shortcut key: %s") % "D"],
            ["study", _("Study"), _("Shortcut key: %s") % "S"],
            ["add", _("Add"), _("Shortcut key: %s") % "A"],
            ["browse", _("Browse"), _("Shortcut key: %s") % "B"],
        ]
        return self._linkHTML(links)

    def _linkHTML(self, links):
        buf = ""
        for ln, name, title in links:
            buf += '<a class=hitem title="%s" href="%s">%s</a>' % (
                title, ln, name)
            buf += "&nbsp;"*3
        return buf

    def _rightIcons(self):
        buf = ""
        for ln, icon, title in self._rightIconsList():
            buf += '<a class=hitem title="%s" href="%s"><img src="%s"></a>' % (
                title, ln, icon)
        return buf

    # Link handling
    ######################################################################

    def _linkHandler(self, l):
        # first set focus back to main window, or we're left with an ugly
        # focus ring around the clicked item
        self.mw.web.setFocus()
        if l  == "decks":
            self.mw.moveToState("deckBrowser")
        elif l == "study":
            # if overview already shown, switch to review
            if self.mw.state == "overview":
                self.mw.col.startTimebox()
                self.mw.moveToState("review")
            else:
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
margin-top: 4px;
font-weight: bold;
}

body {
background: -webkit-gradient(linear, left top, left bottom,
  from(#ddd), to(#fff));
margin: 0; padding: 0;
-webkit-user-select: none;
border-bottom: 1px solid #aaa;
}

* { -webkit-user-drag: none; }

.hitem {
padding-right: 6px;
text-decoration: none;
color: #000;
}
.hitem:hover {
text-decoration: underline;
}
"""

class BottomBar(Toolbar):

    _css = Toolbar._css + """
#header {
background: -webkit-gradient(linear, left top, left bottom,
from(#fff), to(#ddd));
border-bottom: 0;
border-top: 1px solid #aaa;
margin-bottom: 6px;
margin-top: 0;
}
td { font-size: 12px; }
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
