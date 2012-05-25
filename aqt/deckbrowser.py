# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import askUser, getOnlyText, openLink, showWarning, showInfo, \
    shortcut
from anki.utils import isMac, ids2str
import anki.js
from anki.errors import DeckRenameError
import aqt
from anki.sound import clearAudioQueue

class DeckBrowser(object):

    def __init__(self, mw):
        self.mw = mw
        self.web = mw.web
        self.bottom = aqt.toolbar.BottomBar(mw, mw.bottomWeb)

    def show(self):
        clearAudioQueue()
        self.web.setLinkHandler(self._linkHandler)
        self.web.setKeyHandler(None)
        self.mw.keyHandler = self._keyHandler
        self._renderPage()

    def refresh(self):
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
        elif cmd == "cram":
            self.mw.onCram()
        elif cmd == "create":
            showInfo(_("""\
To create a new deck, simply enter its name into any place that ask for \
a deck name, such as when adding notes, changing a card's deck while browsing, \
or importing text files."""))
        elif cmd == "drag":
            draggedDeckDid, ontoDeckDid = arg.split(',')
            self._dragDeckOnto(draggedDeckDid, ontoDeckDid)

    def _keyHandler(self, evt):
        key = unicode(evt.text())
        if key == "f":
            self.mw.onCram()

    def _selDeck(self, did):
        self.mw.col.decks.select(did)
        self.mw.onOverview()

    # HTML generation
    ##########################################################################

    _dragIndicatorBorderWidth = "1px"

    _css = """
tr { font-size: 12px; }
a.deck { color: #000; text-decoration: none; min-width: 5em;
         display:inline-block; }
a.deck:hover { text-decoration: underline; }
tr.deck td { border-bottom: %(width)s solid #e7e7e7; }
tr.top-level-drag-row td { border-bottom: %(width)s solid transparent; }
td { white-space: nowrap; }
tr.drag-hover td { border-bottom: %(width)s solid #aaa; }
.extra { font-size: 90%%; }
body { margin: 1em; -webkit-user-select: none; }
.current { background-color: #e7e7e7; }
.decktd { min-width: 15em; }
.count { width: 6em; text-align: right; }
""" % dict(width=_dragIndicatorBorderWidth)

    _body = """
<center>
<table cellspacing=0 cellpading=3>
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
            delay: 200,
            opacity: 0.7
        });
        $("tr.deck").droppable({
            drop: handleDropEvent,
            hoverClass: 'drag-hover',
        });
        $("tr.top-level-drag-row").droppable({
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
        self.web.stdHtml(self._body%dict(tree=tree), css=css,
                         js=anki.js.jquery+anki.js.ui)
        self._drawButtons()

    def _renderDeckTree(self, nodes, depth=0):
        if not nodes:
            return ""
        if depth == 0:
            buf = """
<tr><th colspan=5 align=left>%s</th><th class=count>%s</th>
<th class=count>%s</th><th class=count></th></tr>""" % (
            _("Deck"), _("Due"), _("New"))
            buf += self._topLevelDragRow()
        else:
            buf = ""
        for node in nodes:
            buf += self._deckRow(node, depth)
        if depth == 0:
            buf += self._topLevelDragRow()
        return buf

    def _deckRow(self, node, depth):
        name, did, due, lrn, new, children = node
        due += lrn
        def indent():
            return "&nbsp;"*6*depth
        if did == self.mw.col.conf['curDeck']:
            klass = 'deck current'
        else:
            klass = 'deck'
        buf = "<tr class='%s' id='%d'>" % (klass, did)
        # deck link
        buf += """
<td class=decktd colspan=5>%s<a class=deck href='open:%d'>%s</a></td>"""% (
            indent(), did, name)
        # due counts
        def nonzeroColour(cnt, colour):
            if not cnt:
                colour = "#e0e0e0"
            if cnt >= 1000:
                cnt = "1000+"
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

    def _topLevelDragRow(self):
        return "<tr class='top-level-drag-row'><td colspan='6'>&nbsp;</td></tr>"

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
        if str(did) == '1':
            return showWarning(_("The default deck can't be deleted."))
        self.mw.checkpoint(_("Delete Deck"))
        deck = self.mw.col.decks.get(did)
        if not deck['dyn']:
            dids = [did] + [r[1] for r in self.mw.col.decks.children(did)]
            cnt = self.mw.col.db.scalar(
                "select count() from cards where did in {0} or "
                "odid in {0}".format(ids2str(dids)))
            if cnt:
                extra = _(" It has %d cards.") % cnt
            else:
                extra = None
        if deck['dyn'] or not extra or askUser(
            (_("Are you sure you wish to delete %s?") % deck['name']) +
            extra):
            self.mw.progress.start(immediate=True)
            self.mw.col.decks.rem(did, True)
            self.mw.progress.finish()
            self.show()

    # Top buttons
    ######################################################################

    def _drawButtons(self):
        links = [
            ["", "shared", _("Get Shared")],
            ["", "create", _("Create")],
            ["Ctrl+I", "import", _("Import File")],
            ["F", "cram", _("Filter/Cram")],
        ]
        buf = ""
        for b in links:
            if b[0]:
                b[0] = _("Shortcut key: %s") % shortcut(b[0])
            buf += """
<button title='%s' onclick='py.link(\"%s\");'>%s</button>""" % tuple(b)
        self.bottom.draw(buf)
        self.bottom.web.setFixedHeight(isMac and 28 or 36)
        self.bottom.web.setLinkHandler(self._linkHandler)

    def _onShared(self):
        print "fixme: check & warn if schema modified first"
        openLink(aqt.appShared+"decks/")
