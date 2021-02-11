# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional, Tuple

import aqt
from aqt import gui_hooks
from aqt.sound import av_player
from aqt.toolbar import BottomBar
from aqt.utils import TR, askUserDialog, openLink, shortcut, tooltip, tr


class OverviewBottomBar:
    def __init__(self, overview: Overview) -> None:
        self.overview = overview


@dataclass
class OverviewContent:
    """Stores sections of HTML content that the overview will be
    populated with.

    Attributes:
        deck {str} -- Plain text deck name
        shareLink {str} -- HTML of the share link section
        desc {str} -- HTML of the deck description section
        table {str} -- HTML of the deck stats table section
    """

    deck: str
    shareLink: str
    desc: str
    table: str


class Overview:
    "Deck overview."

    def __init__(self, mw: aqt.AnkiQt) -> None:
        self.mw = mw
        self.web = mw.web
        self.bottom = BottomBar(mw, mw.bottomWeb)

    def show(self) -> None:
        av_player.stop_and_clear_queue()
        self.web.set_bridge_command(self._linkHandler, self)
        self.mw.setStateShortcuts(self._shortcutKeys())
        self.refresh()

    def refresh(self) -> None:
        self.mw.col.reset()
        self._renderPage()
        self._renderBottom()
        self.mw.web.setFocus()
        gui_hooks.overview_did_refresh(self)

    # Handlers
    ############################################################

    def _linkHandler(self, url: str) -> bool:
        if url == "study":
            self.mw.col.startTimebox()
            self.mw.moveToState("review")
            if self.mw.state == "overview":
                tooltip(tr(TR.STUDYING_NO_CARDS_ARE_DUE_YET))
        elif url == "anki":
            print("anki menu")
        elif url == "opts":
            self.mw.onDeckConf()
        elif url == "cram":
            aqt.dialogs.open("DynDeckConfDialog", self.mw)
        elif url == "refresh":
            self.mw.col.sched.rebuild_filtered_deck(self.mw.col.decks.selected())
            self.mw.reset()
        elif url == "empty":
            self.mw.col.sched.empty_filtered_deck(self.mw.col.decks.selected())
            self.mw.reset()
        elif url == "decks":
            self.mw.moveToState("deckBrowser")
        elif url == "review":
            openLink(f"{aqt.appShared}info/{self.sid}?v={self.sidVer}")
        elif url == "studymore" or url == "customStudy":
            self.onStudyMore()
        elif url == "unbury":
            self.onUnbury()
        elif url.lower().startswith("http"):
            openLink(url)
        return False

    def _shortcutKeys(self) -> List[Tuple[str, Callable]]:
        return [
            ("o", self.mw.onDeckConf),
            ("r", self.onRebuildKey),
            ("e", self.onEmptyKey),
            ("c", self.onCustomStudyKey),
            ("u", self.onUnbury),
        ]

    def _filteredDeck(self) -> int:
        return self.mw.col.decks.current()["dyn"]

    def onRebuildKey(self) -> None:
        if self._filteredDeck():
            self.mw.col.sched.rebuild_filtered_deck(self.mw.col.decks.selected())
            self.mw.reset()

    def onEmptyKey(self) -> None:
        if self._filteredDeck():
            self.mw.col.sched.empty_filtered_deck(self.mw.col.decks.selected())
            self.mw.reset()

    def onCustomStudyKey(self) -> None:
        if not self._filteredDeck():
            self.onStudyMore()

    def onUnbury(self) -> None:
        if self.mw.col.schedVer() == 1:
            self.mw.col.sched.unburyCardsForDeck()
            self.mw.reset()
            return

        info = self.mw.col.sched.congratulations_info()
        if info.have_sched_buried and info.have_user_buried:
            opts = [
                tr(TR.STUDYING_MANUALLY_BURIED_CARDS),
                tr(TR.STUDYING_BURIED_SIBLINGS),
                tr(TR.STUDYING_ALL_BURIED_CARDS),
                tr(TR.ACTIONS_CANCEL),
            ]

            diag = askUserDialog(tr(TR.STUDYING_WHAT_WOULD_YOU_LIKE_TO_UNBURY), opts)
            diag.setDefault(0)
            ret = diag.run()
            if ret == opts[0]:
                self.mw.col.sched.unburyCardsForDeck(type="manual")
            elif ret == opts[1]:
                self.mw.col.sched.unburyCardsForDeck(type="siblings")
            elif ret == opts[2]:
                self.mw.col.sched.unburyCardsForDeck(type="all")
        else:
            self.mw.col.sched.unburyCardsForDeck(type="all")

        self.mw.reset()

    # HTML
    ############################################################

    def _renderPage(self) -> None:
        but = self.mw.button
        deck = self.mw.col.decks.current()
        self.sid = deck.get("sharedFrom")
        if self.sid:
            self.sidVer = deck.get("ver", None)
            shareLink = '<a class=smallLink href="review">Reviews and Updates</a>'
        else:
            shareLink = ""
        if self.mw.col.sched._is_finished():
            self._show_finished_screen()
            return
        table_text = self._table()
        content = OverviewContent(
            deck=deck["name"],
            shareLink=shareLink,
            desc=self._desc(deck),
            table=self._table(),
        )
        gui_hooks.overview_will_render_content(self, content)
        self.web.stdHtml(
            self._body % content.__dict__,
            css=["css/overview.css"],
            js=["js/vendor/jquery.min.js", "js/overview.js"],
            context=self,
        )

    def _show_finished_screen(self) -> None:
        self.web.load_ts_page("congrats")

    def _desc(self, deck: Dict[str, Any]) -> str:
        if deck["dyn"]:
            desc = tr(TR.STUDYING_THIS_IS_A_SPECIAL_DECK_FOR)
            desc += f" {tr(TR.STUDYING_CARDS_WILL_BE_AUTOMATICALLY_RETURNED_TO)}"
            desc += f" {tr(TR.STUDYING_DELETING_THIS_DECK_FROM_THE_DECK)}"
        else:
            desc = deck.get("desc", "")
            if deck.get("md", False):
                desc = self.mw.col.render_markdown(desc)
        if not desc:
            return "<p>"
        if deck["dyn"]:
            dyn = "dyn"
        else:
            dyn = ""
        return f'<div class="descfont descmid description {dyn}">{desc}</div>'

    def _table(self) -> Optional[str]:
        counts = list(self.mw.col.sched.counts())
        but = self.mw.button
        return """
<table width=400 cellpadding=5>
<tr><td align=center valign=top>
<table cellspacing=5>
<tr><td>%s:</td><td><b><span class=new-count>%s</span></b></td></tr>
<tr><td>%s:</td><td><b><span class=learn-count>%s</span></b></td></tr>
<tr><td>%s:</td><td><b><span class=review-count>%s</span></b></td></tr>
</table>
</td><td align=center>
%s</td></tr></table>""" % (
            tr(TR.ACTIONS_NEW),
            counts[0],
            tr(TR.SCHEDULING_LEARNING),
            counts[1],
            tr(TR.STUDYING_TO_REVIEW),
            counts[2],
            but("study", tr(TR.STUDYING_STUDY_NOW), id="study", extra=" autofocus"),
        )

    _body = """
<center>
<h3>%(deck)s</h3>
%(shareLink)s
%(desc)s
%(table)s
</center>
"""

    # Bottom area
    ######################################################################

    def _renderBottom(self) -> None:
        links = [
            ["O", "opts", tr(TR.ACTIONS_OPTIONS)],
        ]
        if self.mw.col.decks.current()["dyn"]:
            links.append(["R", "refresh", tr(TR.ACTIONS_REBUILD)])
            links.append(["E", "empty", tr(TR.STUDYING_EMPTY)])
        else:
            links.append(["C", "studymore", tr(TR.ACTIONS_CUSTOM_STUDY)])
            # links.append(["F", "cram", _("Filter/Cram")])
        if self.mw.col.sched.haveBuried():
            links.append(["U", "unbury", tr(TR.STUDYING_UNBURY)])
        buf = ""
        for b in links:
            if b[0]:
                b[0] = tr(TR.ACTIONS_SHORTCUT_KEY, val=shortcut(b[0]))
            buf += """
<button title="%s" onclick='pycmd("%s")'>%s</button>""" % tuple(
                b
            )
        self.bottom.draw(
            buf=buf, link_handler=self._linkHandler, web_context=OverviewBottomBar(self)
        )

    # Studying more
    ######################################################################

    def onStudyMore(self) -> None:
        import aqt.customstudy

        aqt.customstudy.CustomStudy(self.mw)
