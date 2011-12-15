# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import askUser, getOnlyText, openLink
import aqt

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)

    def show(self, _init=True):
        if _init:
            self.web.setLinkHandler(self._linkHandler)
        self._renderPage()

    # Event handlers
    ##########################################################################

    def _linkHandler(self, url):
        if ":" in url:
            (cmd, arg) = url.split(":")
        else:
            cmd = url
        if cmd == "open":
            self._selDeck(arg)
        elif cmd == "opts":
            self._showOptions(arg)
        elif cmd == "shared":
            self._onShared()
        elif cmd == "import":
            self.mw.onImport()

    def _selDeck(self, did):
        self.mw.col.decks.select(did)
        self.mw.onOverview()

    # HTML generation
    ##########################################################################

    _css = """
a.deck { color: #000; text-decoration: none; font-size: 12px; }
a.deck:hover { text-decoration: underline; }
td.opts { white-space: nowrap; }
td.deck { width: 90% }
.extra { font-size: 90%; }
body { margin: 1em; -webkit-user-select: none; }
"""

    _body = """
<center>
<table cellspacing=0 cellpading=3 width=100%%>
%(tree)s
</table>
</center>
"""

    def _renderPage(self):
        css = self.mw.sharedCSS + self._css
        tree = self._renderDeckTree(self.mw.col.sched.deckDueTree())
        self.web.stdHtml(self._body%dict(
                title=_("Decks"),
                tree=tree), css=css)
        self._drawButtons()

    def _renderDeckTree(self, nodes, depth=0):
        if not nodes:
            return ""
        buf = ""
        for node in nodes:
            buf += self._deckRow(node, depth)
        return buf

    def _deckRow(self, node, depth):
        name, did, due, new, children = node
        def indent():
            return "&nbsp;"*3*depth
        # due image
        buf = "<tr><td colspan=5>" + indent() + self._dueImg(due, new)
        # deck link
        buf += " <a class=deck href='open:%d'>%s</a></td>"% (did, name)
        # options
        buf += "<td align=right class=opts>%s</td></tr>" % self.mw.button(
            link="opts:%d"%did, name="<img valign=bottom src='qrc:/icons/gears.png'>&#9662;")
        # children
        buf += self._renderDeckTree(children, depth+1)
        return buf

    def _dueImg(self, due, new):
        if due:
            i = "clock-icon"
        elif new:
            i = "plus-circle"
        else:
            i = "none"
        return '<img valign=bottom src="qrc:/icons/%s.png">' % i

    # Options
    ##########################################################################

    def _showOptions(self, did):
        m = QMenu(self.mw)
        a = m.addAction(_("Rename"))
        a.connect(a, SIGNAL("triggered()"), lambda did=did: self._rename(did))
        a = m.addAction(_("Delete"))
        a.connect(a, SIGNAL("triggered()"), lambda did=did: self._delete(did))
        m.exec_(QCursor.pos())

    def _rename(self, did):
        self.mw.checkpoint(_("Rename Deck"))
        deck = self.mw.col.decks.get(did)
        newName = getOnlyText(_("New deck name:"))
        if not newName:
            return
        if deck in self.mw.col.decks.allNames():
            return showWarning(_("That deck already exists."))
        self.mw.col.decks.rename(deck, newName)
        self.show()

    def _delete(self, did):
        self.mw.checkpoint(_("Delete Deck"))
        if did == 1:
            return showWarning(_("The default deck can't be deleted."))
        deck = self.mw.col.decks.get(did)
        if askUser(_("""\
Are you sure you wish to delete all of the cards in %s?""")%deck['name']):
            self.mw.progress.start(immediate=True)
            self.mw.col.decks.rem(did, True)
            self.mw.progress.finish()
            self.show()

    # Top buttons
    ######################################################################

    def _drawButtons(self):
        links = [
            ["shared", _("Get Shared")],
            ["import", _("Import File")],
        ]
        buf = ""
        for b in links:
            buf += "<button onclick='py.link(\"%s\");'>%s</button>" % tuple(b)
        self.bottom.draw(buf)
        self.bottom.web.setFixedHeight(32)
        self.bottom.web.setLinkHandler(self._linkHandler)

    def _onShared(self):
        print "fixme: check & warn if schema modified first"
        openLink(aqt.appShared+"decks/")
