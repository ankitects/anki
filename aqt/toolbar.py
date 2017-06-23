# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *

class Toolbar:

    def __init__(self, mw, web):
        self.mw = mw
        self.web = web
        self.link_handlers = {
            "decks": self._deckLinkHandler,
            "study": self._studyLinkHandler,
            "add": self._addLinkHandler,
            "browse": self._browseLinkHandler,
            "stats": self._statsLinkHandler,
            "sync": self._syncLinkHandler,
        }
        self.web.setFixedHeight(30)

    def onLoaded(self):
        self.web.evalWithCallback("$(document.body).height()", self.onHeight)

    def onHeight(self, qvar):
        height = int(qvar*self.web.zoomFactor())
        self.web.setFixedHeight(height)

    def draw(self):
        self.web.onBridgeCmd = self._linkHandler
        self.web.onLoadFinished = self.onLoaded
        self.web.stdHtml(self._body % self._centerLinks(), self._css)

    # Available links
    ######################################################################

    def _centerLinks(self):
        links = [
            ["decks", _("Decks"), _("Shortcut key: %s") % "D"],
            ["add", _("Add"), _("Shortcut key: %s") % "A"],
            ["browse", _("Browse"), _("Shortcut key: %s") % "B"],
            ["stats", _("Stats"), _("Shortcut key: %s") % "Shift+S"],
            ["sync", _("Sync"), _("Shortcut key: %s") % "Y"],
        ]
        return self._linkHTML(links)

    def _linkHTML(self, links):
        buf = ""
        for ln, name, title in links:
            buf += '''
            <a class=hitem title="%s" href=# onclick="pycmd('%s')">%s</a>''' % (
                title, ln, name)
            buf += "&nbsp;"*3
        return buf

    # Link handling
    ######################################################################

    def _linkHandler(self, link):
        if link in self.link_handlers:
            self.link_handlers[link]()
        return False

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
<center id=outer>
<table id=header width=100%%>
<tr>
<td class=tdcenter align=center>%s</td>
</tr></table>
</center>
"""

    _css = """
#header {
padding:3px;
font-weight: bold;
border-bottom: 1px solid #aaa;
background: %s;
}

.tdcenter { white-space: nowrap; }

body {
margin:0; padding:0;
-webkit-user-select: none;
overflow: hidden;
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
border-bottom: 0;
border-top: 1px solid #aaa;
margin-bottom: 6px;
margin-top: 0;
}
"""

    _centerBody = """
<center id=outer><table width=100%% id=header><tr><td align=center>
%s</td></tr></table></center>
"""

    def draw(self, buf):
        self.web.onBridgeCmd = self._linkHandler
        self.web.onLoadFinished = self.onLoaded
        self.web.stdHtml(
            self._centerBody % buf,
            self._css)
