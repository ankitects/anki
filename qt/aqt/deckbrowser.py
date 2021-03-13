# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from concurrent.futures import Future
from copy import deepcopy
from dataclasses import dataclass
from typing import Any

import aqt
from anki.decks import DeckTreeNode
from anki.errors import DeckIsFilteredError
from anki.utils import intTime
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.sound import av_player
from aqt.toolbar import BottomBar
from aqt.utils import (
    TR,
    askUser,
    getOnlyText,
    openLink,
    shortcut,
    showInfo,
    showWarning,
    tr,
)


class DeckBrowserBottomBar:
    def __init__(self, deck_browser: DeckBrowser) -> None:
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
        self._v1_message_dismissed_at = 0

    def show(self) -> None:
        av_player.stop_and_clear_queue()
        self.web.set_bridge_command(self._linkHandler, self)
        self._renderPage()
        # redraw top bar for theme change
        self.mw.toolbar.redraw()

    def refresh(self) -> None:
        self._renderPage()

    # Event handlers
    ##########################################################################

    def _linkHandler(self, url: str) -> Any:
        if ":" in url:
            (cmd, arg) = url.split(":", 1)
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
            self._on_create()
        elif cmd == "drag":
            source, target = arg.split(",")
            self._handle_drag_and_drop(int(source), int(target or 0))
        elif cmd == "collapse":
            self._collapse(int(arg))
        elif cmd == "v2upgrade":
            self._confirm_upgrade()
        elif cmd == "v2upgradeinfo":
            openLink("https://faqs.ankiweb.net/the-anki-2.1-scheduler.html")
        elif cmd == "v2upgradelater":
            self._v1_message_dismissed_at = intTime()
            self.refresh()
        return False

    def _selDeck(self, did: str) -> None:
        self.mw.col.decks.select(int(did))
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

    def _renderPage(self, reuse: bool = False) -> None:
        if not reuse:
            self._dueTree = self.mw.col.sched.deck_due_tree()
            self.__renderPage(None)
            return
        self.web.evalWithCallback("window.pageYOffset", self.__renderPage)

    def __renderPage(self, offset: int) -> None:
        content = DeckBrowserContent(
            tree=self._renderDeckTree(self._dueTree),
            stats=self._renderStats(),
        )
        gui_hooks.deck_browser_will_render_content(self, content)
        self.web.stdHtml(
            self._v1_upgrade_message() + self._body % content.__dict__,
            css=["css/deckbrowser.css"],
            js=[
                "js/vendor/jquery.min.js",
                "js/vendor/jquery-ui.min.js",
                "js/deckbrowser.js",
            ],
            context=self,
        )
        self.web.key = "deckBrowser"
        self._drawButtons()
        if offset is not None:
            self._scrollToOffset(offset)
        gui_hooks.deck_browser_did_render(self)

    def _scrollToOffset(self, offset: int) -> None:
        self.web.eval("$(function() { window.scrollTo(0, %d, 'instant'); });" % offset)

    def _renderStats(self) -> str:
        return '<div id="studiedToday"><span>{}</span></div>'.format(
            self.mw.col.studied_today(),
        )

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

        def indent() -> str:
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
        def nonzeroColour(cnt: int, klass: str) -> str:
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

    def _topLevelDragRow(self) -> str:
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
        qconnect(a.triggered, lambda b, did=did: self._export(int(did)))
        a = m.addAction(tr(TR.ACTIONS_DELETE))
        qconnect(a.triggered, lambda b, did=did: self._delete(int(did)))
        gui_hooks.deck_browser_will_show_options_menu(m, int(did))
        m.exec_(QCursor.pos())

    def _export(self, did: int) -> None:
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
        except DeckIsFilteredError as err:
            showWarning(str(err))
            return
        self.show()

    def _options(self, did: str) -> None:
        # select the deck first, because the dyn deck conf assumes the deck
        # we're editing is the current one
        self.mw.col.decks.select(int(did))
        self.mw.onDeckConf()

    def _collapse(self, did: int) -> None:
        self.mw.col.decks.collapse(did)
        node = self.mw.col.decks.find_deck_in_tree(self._dueTree, did)
        if node:
            node.collapsed = not node.collapsed
        self._renderPage(reuse=True)

    def _handle_drag_and_drop(self, source: int, target: int) -> None:
        try:
            self.mw.col.decks.drag_drop_decks([source], target)
        except Exception as e:
            showWarning(str(e))
            return
        gui_hooks.sidebar_should_refresh_decks()
        self.show()

    def ask_delete_deck(self, did: int) -> bool:
        deck = self.mw.col.decks.get(did)
        if deck["dyn"]:
            return True

        count = self.mw.col.decks.card_count(did, include_subdecks=True)
        if not count:
            return True

        extra = tr(TR.DECKS_IT_HAS_CARD, count=count)
        if askUser(
            f"{tr(TR.DECKS_ARE_YOU_SURE_YOU_WISH_TO, val=deck['name'])} {extra}"
        ):
            return True
        return False

    def _delete(self, did: int) -> None:
        if self.ask_delete_deck(did):

            def do_delete() -> None:
                return self.mw.col.decks.rem(did, True)

            def on_done(fut: Future) -> None:
                self.show()
                res = fut.result()  # Required to check for errors
                self.mw.reset()

            self.mw.checkpoint(tr(TR.DECKS_DELETE_DECK))
            self.mw.taskman.with_progress(do_delete, on_done)

    # Top buttons
    ######################################################################

    drawLinks = [
        ["", "shared", tr(TR.DECKS_GET_SHARED)],
        ["", "create", tr(TR.DECKS_CREATE_DECK)],
        ["Ctrl+Shift+I", "import", tr(TR.DECKS_IMPORT_FILE)],
    ]

    def _drawButtons(self) -> None:
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

    def _onShared(self) -> None:
        openLink(f"{aqt.appShared}decks/")

    def _on_create(self) -> None:
        deck = getOnlyText(tr(TR.DECKS_NAME_FOR_DECK))
        if deck:
            try:
                self.mw.col.decks.id(deck)
            except DeckIsFilteredError as err:
                showWarning(str(err))
                return
            gui_hooks.sidebar_should_refresh_decks()
            self.refresh()

    ######################################################################

    def _v1_upgrade_message(self) -> str:
        if self.mw.col.schedVer() == 2:
            return ""
        if (intTime() - self._v1_message_dismissed_at) < 86_400:
            return ""

        return f"""
<center>
<div class=callout>
    <div>
      {tr(TR.SCHEDULING_UPDATE_SOON)}
    </div>
    <div>
      <button onclick='pycmd("v2upgrade")'>
        {tr(TR.SCHEDULING_UPDATE_BUTTON)}
      </button>
      <button onclick='pycmd("v2upgradeinfo")'>
        {tr(TR.SCHEDULING_UPDATE_MORE_INFO_BUTTON)}
      </button>
      <button onclick='pycmd("v2upgradelater")'>
        {tr(TR.SCHEDULING_UPDATE_LATER_BUTTON)}
      </button>
    </div>
</div>
</center>
"""

    def _confirm_upgrade(self) -> None:
        self.mw.col.modSchema(check=True)
        self.mw.col.upgrade_to_v2_scheduler()

        # not translated, as 2.15 should not be too far off
        if askUser(
            "Do you use AnkiDroid <= 2.14, or plan to use it in the near future? If unsure, choose No. You can adjust the setting later in the preferences screen.",
            defaultno=True,
        ):
            prefs = self.mw.col.get_preferences()
            prefs.sched.new_timezone = False
            self.mw.col.set_preferences(prefs)

        showInfo(tr(TR.SCHEDULING_UPDATE_DONE))
        self.refresh()
