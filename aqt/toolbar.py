# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *

class Toolbar(object):

    def __init__(self, mw, web):
        self.mw = mw
        self.web = web
        self.web.page().mainFrame().setScrollBarPolicy(
            Qt.Vertical, Qt.ScrollBarAlwaysOff)
        self.web.setLinkHandler(self._linkHandler)
        self.link_handlers = {
            "decks": self._deckLinkHandler,
            "study": self._studyLinkHandler,
            "add": self._addLinkHandler,
            "browse": self._browseLinkHandler,
            "stats": self._statsLinkHandler,
            "sync": self._syncLinkHandler,
        }

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
            buf += '<a class=hitem title="%s" href="%s"><img width="16px" height="16px" src="%s"></a>' % (
                title, ln, icon)
        return buf

    # Link handling
    ######################################################################

    def _linkHandler(self, link):
        # first set focus back to main window, or we're left with an ugly
        # focus ring around the clicked item
        self.mw.web.setFocus()
        if link in self.link_handlers:
          self.link_handlers[link]()

    def _deckLinkHandler(self):
        self.mw.moveToState("deckBrowser")

    def _studyLinkHandler(self):
        # if overview already shown, switch to review
        if self.mw.state == "overview":
            self.mw.col.startTimebox()
            self.mw.moveToState("review")
        else:
          self.mw.onOverview()

    def _addLinkHandler(self):
        self.mw.onAddCard()

    def _browseLinkHandler(self):
        self.mw.onBrowse()

    def _statsLinkHandler(self):
        self.mw.onStats()

    def _syncLinkHandler(self):
        self.mw.onSync()

    # HTML & CSS
    ######################################################################

    _body = """
<table id=header width=100%%>
<tr>
<td width=16%% align=left>%s</td>
<td align=center>%s</td>
<td width=15%% align=right>%s</td>
</tr></table>
"""

    _css = """
#header {
margin:0;
margin-top: 4px;
font-weight: bold;
}

html {
height: 100%;
background: -webkit-gradient(linear, left top, left bottom,
  from(#ddd), to(#fff));
margin:0; padding:0;
}

body {
margin:0; padding:0;
position:absolute;
top:0;left:0;right:0;bottom:0;
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
