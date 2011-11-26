# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import askUser

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web

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
            self._optsForRow(int(arg))
        elif cmd == "download":
            self.mw.onGetSharedDeck()
        elif cmd == "new":
            self.mw.onNew()
        elif cmd == "import":
            self.mw.onImport()
        elif cmd == "opensel":
            self.mw.onOpen()
        elif cmd == "synced":
            self.mw.onOpenOnline()
        elif cmd == "refresh":
            self.refresh()

    def _selDeck(self, did):
        self.mw.col.decks.select(did)
        self.mw.moveToState("overview")

    # HTML generation
    ##########################################################################

    _css = """
.sub { color: #555; }
a.deck { color: #000; text-decoration: none; font-size: 16px; }
.num { text-align: right; padding: 0 5 0 5; }
td.opts { white-space: nowrap; }
td.deck { width: 90% }
a { font-size: 80%; }
.extra { font-size: 90%; }
.due { vertical-align: text-bottom; }
table { margin: 1em; }
"""

    _body = """
<center>
<h1>%(title)s</h1>
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

    def _renderDeckTree(self, nodes, depth=0):
        if not nodes:
            return ""
        buf = ""
        for node in nodes:
            buf += self._deckRow(node, depth)
        return buf

    def _deckRow(self, node, depth):
        name, did, due, new, children = node
        # due image
        buf = "<tr><td colspan=5>" + self._dueImg(due, new)
        # deck link
        buf += " <a class=deck href='open:%d'>%s</a></td>"% (did, name)
        # options
        buf += "<td align=right class=opts>%s</td></tr>" % self.mw.button(
            link="opts:%d"%did, name=_("Options")+'&#9660')
        # children
        buf += self._renderDeckTree(children, depth+1)
        return buf

    def _dueImg(self, due, new):
        if due and new:
            i = "both"
        elif due:
            i = "green"
        elif new:
            i = "blue"
        else:
            i = "none"
        return '<img valign=bottom src="qrc:/icons/%s.png">' % i

    # Options
    ##########################################################################

    def _optsForRow(self, n):
        m = QMenu(self.mw)
        # delete
        a = m.addAction(QIcon(":/icons/editdelete.png"), _("Delete"))
        a.connect(a, SIGNAL("triggered()"), lambda n=n: self._deleteRow(n))
        m.exec_(QCursor.pos())

    def _deleteRow(self, c):
        d = self._decks[c]
        if d['state'] == 'missing':
            return self._hideRow(c)
        if askUser(_("""\
Delete %s? If this deck is synchronized the online version will \
not be touched.""") % d['name']):
            deck = d['path']
            os.unlink(deck)
            try:
                shutil.rmtree(re.sub(".anki$", ".media", deck))
            except OSError:
                pass
            self.mw.config.delRecentDeck(deck)
            self.refresh()
