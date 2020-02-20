# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Dict, Optional

import aqt
from anki.lang import _
from aqt import gui_hooks
from aqt.qt import *
from aqt.webview import AnkiWebView


# wrapper class for set_bridge_command()
class TopToolbar:
    def __init__(self, toolbar: Toolbar):
        self.toolbar = toolbar


# wrapper class for set_bridge_command()
class BottomToolbar:
    def __init__(self, toolbar: Toolbar):
        self.toolbar = toolbar


@dataclass
class ToolbarLink:
    """Bundles together the data fields used to generate a link element in
    Anki's top toolbar
    
    Attributes:
        cmd {str} -- Command name used for the JS → Python bridge
        label {str} -- Display label of the link
        func {Callable} -- Callable to be called on clicking the link
        tip {Optional[str]} -- Optional tooltip text to show on hovering over the link
        id: {Optional[str]} -- Optional id attribute to supply the link with
    """

    cmd: str
    label: str
    func: Callable
    tip: Optional[str] = None
    id: Optional[str] = None


class Toolbar:
    def __init__(self, mw: aqt.AnkiQt, web: AnkiWebView) -> None:
        self.mw = mw
        self.web = web
        self.link_handlers: Dict[str, Callable] = {
            "study": self._studyLinkHandler,
        }
        self.web.setFixedHeight(30)
        self.web.requiresCol = False

    def draw(
        self,
        buf: str = "",
        web_context: Optional[Any] = None,
        link_handler: Optional[Callable[[str], Any]] = None,
    ):
        web_context = web_context or TopToolbar(self)
        link_handler = link_handler or self._linkHandler
        self.web.set_bridge_command(link_handler, web_context)
        self.web.stdHtml(
            self._body % self._centerLinks(), css=["toolbar.css"], context=web_context,
        )
        self.web.adjustHeightToFit()
        if self.mw.media_syncer.is_syncing():
            self.set_sync_active(True)

    # Available links
    ######################################################################

    def create_link(self, link: ToolbarLink):
        self.link_handlers[link.cmd] = link.func

        title_attr = f'title="{link.tip}"' if link.tip else ""
        id_attr = f"id={link.id}" if link.id else ""

        return (
            f"""<a class=hitem tabindex="-1" aria-label="{link.label}" """
            f"""{title_attr} {id_attr} href=# onclick="return pycmd('{link.cmd}')">"""
            f"""{link.label}</a>"""
        )

    def _centerLinks(self):
        links = [
            self.create_link(link)
            for link in [
                ToolbarLink(
                    cmd="decks",
                    label=_("Decks"),
                    tip=_("Shortcut key: %s") % "D",
                    id="decks",
                    func=self._deckLinkHandler,
                ),
                ToolbarLink(
                    cmd="add",
                    label=_("Add"),
                    tip=_("Shortcut key: %s") % "A",
                    id="add",
                    func=self._addLinkHandler,
                ),
                ToolbarLink(
                    cmd="browse",
                    label=_("Browse"),
                    tip=_("Shortcut key: %s") % "B",
                    id="browse",
                    func=self._browseLinkHandler,
                ),
                ToolbarLink(
                    cmd="stats",
                    label=_("Stats"),
                    tip=_("Shortcut key: %s") % "T",
                    id="stats",
                    func=self._statsLinkHandler,
                ),
            ]
        ]

        links.append(self._create_sync_link())

        gui_hooks.top_toolbar_did_init_links(links, self)

        return "\n".join(links)

    def _create_sync_link(self) -> str:
        name = _("Sync")
        title = _("Shortcut key: %s") % "Y"
        label = "sync"
        self.link_handlers[label] = self._syncLinkHandler

        return f"""
<a class=hitem tabindex="-1" aria-label="{name}" title="{title}" id="{label}" href=# onclick="return pycmd('{label}')">{name}
<img id=sync-spinner src='/_anki/imgs/refresh.svg'>        
</a>"""

    def set_sync_active(self, active: bool) -> None:
        if active:
            meth = "addClass"
        else:
            meth = "removeClass"
        self.web.eval(f"$('#sync-spinner').{meth}('spin')")

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


# Bottom bar
######################################################################


class BottomBar(Toolbar):

    _centerBody = """
<center id=outer><table width=100%% id=header><tr><td align=center>
%s</td></tr></table></center>
"""

    def draw(
        self,
        buf: str = "",
        web_context: Optional[Any] = None,
        link_handler: Optional[Callable[[str], Any]] = None,
    ):
        # note: some screens may override this
        web_context = web_context or BottomToolbar(self)
        link_handler = link_handler or self._linkHandler
        self.web.set_bridge_command(link_handler, web_context)
        self.web.stdHtml(
            self._centerBody % buf,
            css=["toolbar.css", "toolbar-bottom.css"],
            context=web_context,
        )
        self.web.adjustHeightToFit()
