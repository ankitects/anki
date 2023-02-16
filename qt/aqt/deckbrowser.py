# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import html
from copy import deepcopy
from dataclasses import dataclass
from typing import Any, List, Tuple

import aqt
import aqt.operations
from anki.collection import OpChanges
from anki.decks import DeckCollapseScope, DeckId, DeckTreeNode
from aqt import AnkiQt, gui_hooks
from aqt.deckoptions import display_options_for_deck_id
from aqt.operations import QueryOp
from aqt.operations.deck import (
    add_deck_dialog,
    remove_decks,
    rename_deck,
    reparent_decks,
    set_current_deck,
    set_deck_collapsed,
)
from aqt.qt import *
from aqt.sound import av_player
from aqt.toolbar import BottomBar
from aqt.utils import getOnlyText, openLink, shortcut, showInfo, tr


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
    current_deck_id: DeckId


class DeckBrowser:
    _dueTree: DeckTreeNode

    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.bottom = BottomBar(mw, mw.bottomWeb)
        self.scrollPos = QPoint(0, 0)
        self._v1_message_dismissed_at = 0
        self._refresh_needed = False
        gui_hooks.quick_actions_did_change.append(self.mark_refresh_needed)

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

    def op_executed(
        self, changes: OpChanges, handler: object | None, focused: bool
    ) -> bool:
        if changes.study_queues and handler is not self:
            self._refresh_needed = True

        if focused:
            self.refresh_if_needed()

        return self._refresh_needed

    def mark_refresh_needed(self) -> None:
        self._refresh_needed = True

    # Event handlers
    ##########################################################################

    def _linkHandler(self, url: str) -> Any:
        if ":" in url:
            (cmd, arg) = url.split(":", 1)
        else:
            cmd = url
        if cmd == "open":
            self.set_current_deck(DeckId(int(arg)))
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
            self._handle_drag_and_drop(DeckId(int(source)), DeckId(int(target or 0)))
        elif cmd == "collapse":
            self._collapse(DeckId(int(arg)))
        elif cmd == "v2upgrade":
            self._confirm_upgrade()
        elif cmd == "v2upgradeinfo":
            openLink("https://faqs.ankiweb.net/the-anki-2.1-scheduler.html")
        elif cmd == "rebuilddyndeck":
            self.mw.col.sched.rebuild_filtered_deck(int(arg))
            self.refresh()
        elif cmd == "emptydyndeck":
            self.mw.col.sched.empty_filtered_deck(int(arg))
            self.refresh()
        elif cmd == "stats":
            clickedDeckId = DeckId(int(arg))
            set_current_deck(parent=self.mw, deck_id=clickedDeckId).success(lambda _: self.mw.onStats()).run_in_background(initiator=self)
            self.mark_refresh_needed()
        elif cmd == "rename":
            self._rename(DeckId(int(arg)))
        elif cmd == "togglequickactions":
            self.mw.pm.set_show_deck_quick_actions(not self.mw.pm.show_deck_quick_actions())
            self.refresh()
        elif cmd == "configuration":
            clickedDeckId = DeckId(int(arg))
            self._options(clickedDeckId)

        return False

    def set_current_deck(self, deck_id: DeckId) -> None:
        set_current_deck(parent=self.mw, deck_id=deck_id).success(
            lambda _: self.mw.onOverview()
        ).run_in_background(initiator=self)

    # HTML generation
    ##########################################################################

    _body = """
<center>
<table cellspacing=0 cellpadding=3>
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
        self.web.eval("window.scrollTo(0, %d, 'instant');" % offset)

    def _renderStats(self) -> str:
        return '<div id="studiedToday"><span>{}</span></div>'.format(
            self.mw.col.studied_today(),
        )

    def _renderDeckTree(self, top: DeckTreeNode) -> str:
        buf = """
<tr><th colspan=5 align=start>{}</th>
<th class=count>{}</th>
<th class=count>{}</th>
<th class=count>{}</th>
<th class=optscol><a onclick='pycmd("togglequickactions")'>{}</a></th></tr>""".format(
            tr.decks_deck(),
            tr.actions_new(),
            tr.card_stats_review_log_type_learn(),
            tr.statistics_due_count(),
            "⋘" if self.mw.pm.show_deck_quick_actions() else "⋙"
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
            html.escape(node.name),
        )
        # due counts
        def nonzeroColour(cnt: int, klass: str) -> str:
            if not cnt:
                klass = "zero-count"
            return f'<span class="{klass}">{cnt}</span>'

        review = nonzeroColour(node.review_count, "review-count")
        learn = nonzeroColour(node.learn_count, "learn-count")

        buf += ("<td align=end>%s</td>" * 3) % (
            nonzeroColour(node.new_count, "new-count"),
            learn,
            review,
        )

        # deck actions            
        buf += "<td align=left class=opts>"
        buf += "<a class='quickaction' onclick='return pycmd(\"opts:%d\");'>☰</a>" % node.deck_id
        if self.mw.pm.show_deck_quick_actions():
            unconditionalQuickActions, conditionalQuickActions = self._create_deck_quick_actions(node)
            gui_hooks.deck_browser_did_generate_quick_actions_for_deck(node, self, unconditionalQuickActions, conditionalQuickActions)
            # display actions that are on every deck first
            for quickAction in unconditionalQuickActions:
                buf += quickAction
            for quickAction in conditionalQuickActions:
                buf += quickAction
        buf += "</td></tr>"

        # children
        if not node.collapsed:
            for child in node.children:
                buf += self._render_deck_node(child, ctx)
        return buf

    def _topLevelDragRow(self) -> str:
        return "<tr class='top-level-drag-row'><td colspan='6'>&nbsp;</td></tr>"

    def _create_deck_quick_actions(self, node: DeckTreeNode) -> Tuple[List[str], List[str]]:
        unconditionalQuickActions = []
        conditionalQuickActions = []
        
        isDyn = self.mw.col.decks.is_filtered(node.deck_id)

        def create_single_icon(icon: str, onclick: str, styleOverrides: str, title: str) -> str:
            return "<a class='quickaction' title='%s' onclick='%s' style='%s'>%s</a>" % (title, onclick, styleOverrides, icon)
        
        if self.mw.pm.should_show_deck_action("Options"):
            unconditionalQuickActions.append(create_single_icon("⚙️", "pycmd(\"configuration:%s\")" % node.deck_id, "cursor: pointer;", "Options"))
        if self.mw.pm.should_show_deck_action("Rename"):
            unconditionalQuickActions.append(create_single_icon("✎", "pycmd(\"rename:%s\")" % node.deck_id, "cursor: pointer; color: #e945ff;", "Rename"))
        if self.mw.pm.should_show_deck_action("Stats"):
            unconditionalQuickActions.append(create_single_icon("🗠", "pycmd(\"stats:%s\")" % node.deck_id, "cursor: pointer; color: #3d9bff;", "Stats"))

        if isDyn:
            if self.mw.pm.should_show_deck_action("Rebuild"):
                conditionalQuickActions.append(create_single_icon("↻", "pycmd(\"rebuilddyndeck:%s\")" % node.deck_id, "cursor: pointer; color: #0af0a7;", "Rebuild"))
            if self.mw.pm.should_show_deck_action("Empty"):
                conditionalQuickActions.append(create_single_icon("⤬", "pycmd(\"emptydyndeck:%s\")" % node.deck_id, "cursor: pointer; color: #ed1f94;", "Empty"))

        return unconditionalQuickActions, conditionalQuickActions

    # Options
    ##########################################################################

    def _showOptions(self, did: str) -> None:
        m = QMenu(self.mw)

        # If there is a quick action showed for these items, don't include them in the context menu
        if not self.mw.pm.show_deck_quick_actions() or not self.mw.pm.should_show_deck_action("Options"):
            a = m.addAction(tr.actions_options())
            qconnect(a.triggered, lambda b, did=did: self._options(DeckId(int(did))))
        if not self.mw.pm.show_deck_quick_actions() or not self.mw.pm.should_show_deck_action("Rename"):
            a = m.addAction(tr.actions_rename())
            qconnect(a.triggered, lambda b, did=did: self._rename(DeckId(int(did))))

        a = m.addAction(tr.actions_export())
        qconnect(a.triggered, lambda b, did=did: self._export(DeckId(int(did))))
        a = m.addAction(tr.actions_delete())
        qconnect(a.triggered, lambda b, did=did: self._delete(DeckId(int(did))))
        gui_hooks.deck_browser_will_show_options_menu(m, int(did))
        m.popup(QCursor.pos())

    def _export(self, did: DeckId) -> None:
        self.mw.onExport(did=did)

    def _rename(self, did: DeckId) -> None:
        def prompt(name: str) -> None:
            new_name = getOnlyText(tr.decks_new_deck_name(), default=name)
            if not new_name or new_name == name:
                return
            else:
                rename_deck(
                    parent=self.mw, deck_id=did, new_name=new_name
                ).run_in_background()

        QueryOp(
            parent=self.mw, op=lambda col: col.decks.name(did), success=prompt
        ).run_in_background()

    def _options(self, did: DeckId) -> None:
        display_options_for_deck_id(did)

    def _collapse(self, did: DeckId) -> None:
        node = self.mw.col.decks.find_deck_in_tree(self._dueTree, did)
        if node:
            node.collapsed = not node.collapsed
            set_deck_collapsed(
                parent=self.mw,
                deck_id=did,
                collapsed=node.collapsed,
                scope=DeckCollapseScope.REVIEWER,
            ).run_in_background()
            self._renderPage(reuse=True)

    def _handle_drag_and_drop(self, source: DeckId, target: DeckId) -> None:
        reparent_decks(
            parent=self.mw, deck_ids=[source], new_parent=target
        ).run_in_background()

    def _delete(self, did: DeckId) -> None:
        remove_decks(parent=self.mw, deck_ids=[did]).run_in_background()

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
        if op := add_deck_dialog(parent=self.mw):
            op.run_in_background()

    ######################################################################

    def _v1_upgrade_message(self) -> str:
        if self.mw.col.sched_ver() == 2:
            return ""

        return f"""
<center>
<div class=callout>
    <div>
      {tr.scheduling_update_required()}
    </div>
    <div>
      <button onclick='pycmd("v2upgrade")'>
        {tr.scheduling_update_button()}
      </button>
      <button onclick='pycmd("v2upgradeinfo")'>
        {tr.scheduling_update_more_info_button()}
      </button>
    </div>
</div>
</center>
"""

    def _confirm_upgrade(self) -> None:
        self.mw.col.mod_schema(check=True)
        self.mw.col.upgrade_to_v2_scheduler()

        showInfo(tr.scheduling_update_done())
        self.refresh()
