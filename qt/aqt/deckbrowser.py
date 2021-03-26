# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from typing import Any

import aqt
from anki.collection import OpChanges
from anki.decks import DeckID, DeckTreeNode
from anki.utils import intTime
from aqt import AnkiQt, gui_hooks
from aqt.deck_ops import add_deck_dialog, remove_decks, rename_deck, reparent_decks
from aqt.qt import *
from aqt.sound import av_player
from aqt.toolbar import BottomBar
from aqt.utils import askUser, getOnlyText, openLink, shortcut, showInfo, tr


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
    current_deck_id: DeckID


class DeckBrowser:
    _dueTree: DeckTreeNode

    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.bottom = BottomBar(mw, mw.bottomWeb)
        self.scrollPos = QPoint(0, 0)
        self._v1_message_dismissed_at = 0
        self._refresh_needed = False

    def show(self) -> None:
        av_player.stop_and_clear_queue()
        self.web.set_bridge_command(self._linkHandler, self)
        self._renderPage()
        # redraw top bar for theme change
        self.mw.toolbar.redraw()
        self.refresh()

    def refresh(self) -> None:
        self._renderPage()
        self._refresh_needed = False

    def refresh_if_needed(self) -> None:
        if self._refresh_needed:
            self.refresh()

    def op_executed(self, changes: OpChanges, focused: bool) -> bool:
        if self.mw.col.op_affects_study_queue(changes):
            self._refresh_needed = True

        if focused:
            self.refresh_if_needed()

        return self._refresh_needed

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
            self._handle_drag_and_drop(DeckID(int(source)), DeckID(int(target or 0)))
        elif cmd == "collapse":
            self._collapse(DeckID(int(arg)))
        elif cmd == "v2upgrade":
            self._confirm_upgrade()
        elif cmd == "v2upgradeinfo":
            openLink("https://faqs.ankiweb.net/the-anki-2.1-scheduler.html")
        elif cmd == "v2upgradelater":
            self._v1_message_dismissed_at = intTime()
            self.refresh()
        return False

    def _selDeck(self, did: str) -> None:
        self.mw.col.decks.select(DeckID(int(did)))
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
            tr.decks_deck(),
            tr.statistics_due_count(),
            tr.actions_new(),
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
        a = m.addAction(tr.actions_rename())
        qconnect(a.triggered, lambda b, did=did: self._rename(DeckID(int(did))))
        a = m.addAction(tr.actions_options())
        qconnect(a.triggered, lambda b, did=did: self._options(DeckID(int(did))))
        a = m.addAction(tr.actions_export())
        qconnect(a.triggered, lambda b, did=did: self._export(DeckID(int(did))))
        a = m.addAction(tr.actions_delete())
        qconnect(a.triggered, lambda b, did=did: self._delete(DeckID(int(did))))
        gui_hooks.deck_browser_will_show_options_menu(m, int(did))
        m.exec_(QCursor.pos())

    def _export(self, did: DeckID) -> None:
        self.mw.onExport(did=did)

    def _rename(self, did: DeckID) -> None:
        deck = self.mw.col.decks.get(did)
        current_name = deck["name"]
        new_name = getOnlyText(tr.decks_new_deck_name(), default=current_name)
        if not new_name or new_name == current_name:
            return

        rename_deck(mw=self.mw, deck_id=did, new_name=new_name)

    def _options(self, did: DeckID) -> None:
        # select the deck first, because the dyn deck conf assumes the deck
        # we're editing is the current one
        self.mw.col.decks.select(did)
        self.mw.onDeckConf()

    def _collapse(self, did: DeckID) -> None:
        self.mw.col.decks.collapse(did)
        node = self.mw.col.decks.find_deck_in_tree(self._dueTree, did)
        if node:
            node.collapsed = not node.collapsed
        self._renderPage(reuse=True)

    def _handle_drag_and_drop(self, source: DeckID, target: DeckID) -> None:
        reparent_decks(mw=self.mw, parent=self.mw, deck_ids=[source], new_parent=target)

    def _delete(self, did: DeckID) -> None:
        remove_decks(mw=self.mw, parent=self.mw, deck_ids=[did])

    # Top buttons
    ######################################################################

    drawLinks = [
        ["", "shared", tr.decks_get_shared()],
        ["", "create", tr.decks_create_deck()],
        ["Ctrl+Shift+I", "import", tr.decks_import_file()],
    ]

    def _drawButtons(self) -> None:
        buf = ""
        drawLinks = deepcopy(self.drawLinks)
        for b in drawLinks:
            if b[0]:
                b[0] = tr.actions_shortcut_key(val=shortcut(b[0]))
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
        add_deck_dialog(mw=self.mw, parent=self.mw)

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
      {tr.scheduling_update_soon()}
    </div>
    <div>
      <button onclick='pycmd("v2upgrade")'>
        {tr.scheduling_update_button()}
      </button>
      <button onclick='pycmd("v2upgradeinfo")'>
        {tr.scheduling_update_more_info_button()}
      </button>
      <button onclick='pycmd("v2upgradelater")'>
        {tr.scheduling_update_later_button()}
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
            prefs.scheduling.new_timezone = False
            self.mw.col.set_preferences(prefs)

        showInfo(tr.scheduling_update_done())
        self.refresh()
