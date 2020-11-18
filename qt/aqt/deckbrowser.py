# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass

import aqt
from anki.errors import DeckRenameError
from anki.rsbackend import TR, DeckTreeNode
from anki.utils import ids2str
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.sound import av_player
from aqt.toolbar import BottomBar
from aqt.utils import TR, askUser, getOnlyText, openLink, shortcut, showWarning, tr


class DeckBrowserBottomBar:
    def __init__(self, deck_browser: DeckBrowser):
        self.deck_browser = deck_browser


@dataclass
class DeckBrowserContent:
    """Stores sections of HTML content that the deck browser will be
    populated with.

    Attributes:
        tree {str} -- HTML of the deck tree section
        stats {str} -- HTML of the stats section
    """

    tree: str
    stats: str


@dataclass
class RenderDeckNodeContext:
    current_deck_id: int


class DeckBrowser:
    _dueTree: DeckTreeNode

    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.bottom = BottomBar(mw, mw.bottomWeb)
        self.scrollPos = QPoint(0, 0)

    def show(self):
        av_player.stop_and_clear_queue()
        self.web.set_bridge_command(self._linkHandler, self)
        self._renderPage()
        # redraw top bar for theme change
        self.mw.toolbar.redraw()

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
        elif cmd == "create":
            deck = getOnlyText(tr(TR.DECKS_NAME_FOR_DECK))
            if deck:
                self.mw.col.decks.id(deck)
                gui_hooks.sidebar_should_refresh_decks()
                self.refresh()
        elif cmd == "drag":
            draggedDeckDid, ontoDeckDid = arg.split(",")
            self._dragDeckOnto(draggedDeckDid, ontoDeckDid)
        elif cmd == "collapse":
            self._collapse(int(arg))
        return False

    def _selDeck(self, did):
        self.mw.col.decks.select(did)
        self.mw.onOverview()

    # HTML generation
    ##########################################################################

    _body = """
<center>
<table cellspacing=0 cellpading=3>
%(tree)s
</table>

<br>
%(stats)s
</center>
"""

    def _renderPage(self, reuse=False):
        if not reuse:
            self._dueTree = self.mw.col.sched.deck_due_tree()
            self.__renderPage(None)
            return
        self.web.evalWithCallback("window.pageYOffset", self.__renderPage)

    def __renderPage(self, offset):
        content = DeckBrowserContent(
            tree=self._renderDeckTree(self._dueTree),
            stats=self._renderStats(),
        )
        gui_hooks.deck_browser_will_render_content(self, content)
        self.web.stdHtml(
            self._body % content.__dict__,
            css=["css/deckbrowser.css"],
            js=["js/vendor/jquery.js", "js/vendor/jquery-ui.js", "js/deckbrowser.js"],
            context=self,
        )
        self.web.key = "deckBrowser"
        self._drawButtons()
        if offset is not None:
            self._scrollToOffset(offset)
        gui_hooks.deck_browser_did_render(self)

    def _scrollToOffset(self, offset):
        self.web.eval("$(function() { window.scrollTo(0, %d, 'instant'); });" % offset)

    def _renderStats(self):
        return self.mw.col.studied_today()

    def _renderDeckTree(self, top: DeckTreeNode) -> str:
        buf = """
<tr><th colspan=5 align=start>%s</th><th class=count>%s</th>
<th class=count>%s</th><th class=optscol></th></tr>""" % (
            tr(TR.DECKS_DECK),
            tr(TR.STATISTICS_DUE_COUNT),
            tr(TR.ACTIONS_NEW),
        )
        buf += self._topLevelDragRow()

        ctx = RenderDeckNodeContext(current_deck_id=self.mw.col.conf["curDeck"])

        for child in top.children:
            buf += self._render_deck_node(child, ctx)

        return buf

    def _render_deck_node(self, node: DeckTreeNode, ctx: RenderDeckNodeContext) -> str:
        if node.collapsed:
            prefix = "+"
        else:
            prefix = "-"

        due = node.review_count + node.learn_count

        def indent():
            return "&nbsp;" * 6 * (node.level - 1)

        if node.deck_id == ctx.current_deck_id:
            klass = "deck current"
        else:
            klass = "deck"

        buf = "<tr class='%s' id='%d'>" % (klass, node.deck_id)
        # deck link
        if node.children:
            collapse = (
                "<a class=collapse href=# onclick='return pycmd(\"collapse:%d\")'>%s</a>"
                % (node.deck_id, prefix)
            )
        else:
            collapse = "<span class=collapse></span>"
        if node.filtered:
            extraclass = "filtered"
        else:
            extraclass = ""
        buf += """

        <td class=decktd colspan=5>%s%s<a class="deck %s"
        href=# onclick="return pycmd('open:%d')">%s</a></td>""" % (
            indent(),
            collapse,
            extraclass,
            node.deck_id,
            node.name,
        )
        # due counts
        def nonzeroColour(cnt, klass):
            if not cnt:
                klass = "zero-count"
            return f'<span class="{klass}">{cnt}</span>'

        buf += "<td align=right>%s</td><td align=right>%s</td>" % (
            nonzeroColour(due, "review-count"),
            nonzeroColour(node.new_count, "new-count"),
        )
        # options
        buf += (
            "<td align=center class=opts><a onclick='return pycmd(\"opts:%d\");'>"
            "<img src='/_anki/imgs/gears.svg' class=gears></a></td></tr>" % node.deck_id
        )
        # children
        if not node.collapsed:
            for child in node.children:
                buf += self._render_deck_node(child, ctx)
        return buf

    def _topLevelDragRow(self):
        return "<tr class='top-level-drag-row'><td colspan='6'>&nbsp;</td></tr>"

    # Options
    ##########################################################################

    def _showOptions(self, did: str) -> None:
        m = QMenu(self.mw)
        a = m.addAction(tr(TR.ACTIONS_RENAME))
        qconnect(a.triggered, lambda b, did=did: self._rename(int(did)))
        a = m.addAction(tr(TR.ACTIONS_OPTIONS))
        qconnect(a.triggered, lambda b, did=did: self._options(did))
        a = m.addAction(tr(TR.ACTIONS_EXPORT))
        qconnect(a.triggered, lambda b, did=did: self._export(did))
        a = m.addAction(tr(TR.ACTIONS_DELETE))
        qconnect(a.triggered, lambda b, did=did: self._delete(int(did)))
        gui_hooks.deck_browser_will_show_options_menu(m, int(did))
        m.exec_(QCursor.pos())

    def _export(self, did):
        self.mw.onExport(did=did)

    def _rename(self, did: int) -> None:
        self.mw.checkpoint(tr(TR.ACTIONS_RENAME_DECK))
        deck = self.mw.col.decks.get(did)
        oldName = deck["name"]
        newName = getOnlyText(tr(TR.DECKS_NEW_DECK_NAME), default=oldName)
        newName = newName.replace('"', "")
        if not newName or newName == oldName:
            return
        try:
            self.mw.col.decks.rename(deck, newName)
            gui_hooks.sidebar_should_refresh_decks()
        except DeckRenameError as e:
            return showWarning(e.description)
        self.show()

    def _options(self, did):
        # select the deck first, because the dyn deck conf assumes the deck
        # we're editing is the current one
        self.mw.col.decks.select(did)
        self.mw.onDeckConf()

    def _collapse(self, did: int) -> None:
        self.mw.col.decks.collapse(did)
        node = self.mw.col.decks.find_deck_in_tree(self._dueTree, did)
        if node:
            node.collapsed = not node.collapsed
        self._renderPage(reuse=True)

    def _dragDeckOnto(self, draggedDeckDid, ontoDeckDid):
        try:
            self.mw.col.decks.renameForDragAndDrop(draggedDeckDid, ontoDeckDid)
            gui_hooks.sidebar_should_refresh_decks()
        except DeckRenameError as e:
            return showWarning(e.description)

        self.show()

    def _delete(self, did):
        self.mw.checkpoint(tr(TR.DECKS_DELETE_DECK))
        deck = self.mw.col.decks.get(did)
        if not deck["dyn"]:
            dids = [did] + [r[1] for r in self.mw.col.decks.children(did)]
            cnt = self.mw.col.db.scalar(
                "select count() from cards where did in {0} or "
                "odid in {0}".format(ids2str(dids))
            )
            if cnt:
                extra = tr(TR.DECKS_IT_HAS_CARD, count=cnt)
            else:
                extra = None
        if (
            deck["dyn"]
            or not extra
            or askUser(
                (tr(TR.DECKS_ARE_YOU_SURE_YOU_WISH_TO, val=deck["name"])) + extra
            )
        ):
            self.mw.progress.start()
            self.mw.col.decks.rem(did, True)
            self.mw.progress.finish()
            self.show()

    # Top buttons
    ######################################################################

    drawLinks = [
        ["", "shared", tr(TR.DECKS_GET_SHARED)],
        ["", "create", tr(TR.DECKS_CREATE_DECK)],
        ["Ctrl+Shift+I", "import", tr(TR.DECKS_IMPORT_FILE)],
    ]

    def _drawButtons(self):
        buf = ""
        drawLinks = deepcopy(self.drawLinks)
        for b in drawLinks:
            if b[0]:
                b[0] = tr(TR.ACTIONS_SHORTCUT_KEY, val=shortcut(b[0]))
            buf += """
<button title='%s' onclick='pycmd(\"%s\");'>%s</button>""" % tuple(
                b
            )
        self.bottom.draw(
            buf=buf,
            link_handler=self._linkHandler,
            web_context=DeckBrowserBottomBar(self),
        )

    def _onShared(self):
        openLink(aqt.appShared + "decks/")
