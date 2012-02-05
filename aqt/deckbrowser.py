# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import askUser, getOnlyText, openLink, showWarning
from anki.utils import isMac
from anki.errors import DeckRenameError
import aqt

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)

    def show(self):
        self.web.setLinkHandler(self._linkHandler)
        self.web.setKeyHandler(None)
        self.mw.keyHandler = None
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
        elif cmd == "drag":
            draggedDeckDid, ontoDeckDid = arg.split(',')
            self._dragDeckOnto(draggedDeckDid, ontoDeckDid)

    def _selDeck(self, did):
        self.mw.col.decks.select(did)
        self.mw.onOverview()

    # HTML generation
    ##########################################################################

    _css = """
tr { font-size: 12px; }
a.deck { color: #000; text-decoration: none; }
a.deck:hover { text-decoration: underline; }
tr.deck td { border-bottom: thick solid transparent; }
td.opts { white-space: nowrap; }
tr.drag-hover td { border-bottom: 1px solid #aaa; }
.extra { font-size: 90%; }
body { margin: 1em; -webkit-user-select: none; }
"""

    _body = """
<center>
<table cellspacing=0 cellpading=3 width=100%%>
%(tree)s
</table>
</center>
<script>
    $( init );

    function init() {
        $("tr.deck").draggable({
            scroll: false,

            // can't use "helper: 'clone'" because of a bug in jQuery 1.5
            helper: function (event) {
                return $(this).clone(false);
            },
            opacity: 0.7
        });
        $("tr.deck").droppable({
            drop: handleDropEvent,
            hoverClass: 'drag-hover',
        });
        $("tr.bottom-row").droppable({
            drop: handleDropEvent,
            hoverClass: 'drag-hover',
        });
    }

    function handleDropEvent(event, ui) {
        var draggedDeckId = ui.draggable.attr('id');
        var ontoDeckId = $(this).attr('id');

        py.link("drag:" + draggedDeckId + "," + ontoDeckId);
    }
</script>
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
        if depth == 0:
            buf = """
<tr><th colspan=5 align=left>%s</th><th align=right>%s</th>
<th align=right>%s</th></tr>""" % (
            _("Deck"), _("Due"), _("New"))
            buf += self._bogusBottomRowForDraggingDeckToTopLevel()
        else:
            buf = ""
        for node in nodes:
            buf += self._deckRow(node, depth)
        if depth == 0:
            buf += self._bogusBottomRowForDraggingDeckToTopLevel()
        return buf

    def _deckRow(self, node, depth):
        name, did, due, new, children = node
        def indent():
            return "&nbsp;"*3*depth
        buf = "<tr class='deck' id='%d'>"% did
        # deck link
        buf += "<td colspan=5>%s<a class=deck href='open:%d'>%s</a></td>"% (
            indent(), did, name)
        # due counts
        def nonzeroColour(cnt, colour):
            if not cnt:
                colour = "#e0e0e0"
            return "<font color='%s'>%s</font>" % (colour, cnt)
        buf += "<td align=right>%s</td><td align=right>%s</td>" % (
            nonzeroColour(due, "#007700"),
            nonzeroColour(new, "#000099"))
        # options
        buf += "<td align=right class=opts>%s</td></tr>" % self.mw.button(
            link="opts:%d"%did, name="<img valign=bottom src='qrc:/icons/gears.png'>&#9662;")
        # children
        buf += self._renderDeckTree(children, depth+1)
        return buf

    def _bogusBottomRowForDraggingDeckToTopLevel(self):
        return "<tr class='bottom-row'><td colspan='6'>&nbsp;</td></tr>"

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
        oldName = deck['name']
        newName = getOnlyText(_("New deck name:"), default=oldName)
        newName = newName.replace("'", "").replace('"', "")
        if not newName or newName == oldName:
            return

        try:
            self.mw.col.decks.rename(deck, newName)
        except DeckRenameError, e:
            return showWarning(e.description)

        self.show()

    def _dragDeckOnto(self, draggedDeckDid, ontoDeckDid):
        try:
            self.mw.col.decks.renameForDragAndDrop(draggedDeckDid, ontoDeckDid)
        except DeckRenameError, e:
            return showWarning(e.description)

        self.show()

    def _delete(self, did):
        self.mw.checkpoint(_("Delete Deck"))
        if str(did) == '1':
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
        self.bottom.web.setFixedHeight(isMac and 28 or 36)
        self.bottom.web.setLinkHandler(self._linkHandler)

    def _onShared(self):
        print "fixme: check & warn if schema modified first"
        openLink(aqt.appShared+"decks/")
