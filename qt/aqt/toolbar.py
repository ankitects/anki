# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
from __future__ import annotations

import re
from typing import Any, Callable, Optional, cast

import aqt
from anki.sync import SyncStatus
from aqt import gui_hooks, props
from aqt.qt import *
from aqt.sync import get_sync_status
from aqt.theme import theme_manager
from aqt.utils import tr
from aqt.webview import AnkiWebView


# wrapper class for set_bridge_command()
class TopToolbar:
    def __init__(self, toolbar: Toolbar) -> None:
        self.toolbar = toolbar


# wrapper class for set_bridge_command()
class BottomToolbar:
    def __init__(self, toolbar: Toolbar) -> None:
        self.toolbar = toolbar


class ToolbarWebView(AnkiWebView):
    hide_condition: Callable[..., bool]

    def __init__(self, mw: aqt.AnkiQt, title: str) -> None:
        AnkiWebView.__init__(self, mw, title=title)
        self.mw = mw
        self.setFocusPolicy(Qt.FocusPolicy.WheelFocus)
        self.disable_zoom()
        self.hidden = False
        self.hide_timer = QTimer()
        self.hide_timer.setSingleShot(True)
        self.hide_timer.setInterval(1000)

    def reset_timer(self) -> None:
        self.hide_timer.stop()
        self.hide_timer.setInterval(1000)

    def hide(self) -> None:
        self.hidden = True

    def show(self) -> None:
        self.hidden = False


class TopWebView(ToolbarWebView):
    def __init__(self, mw: aqt.AnkiQt, title: str) -> None:
        super().__init__(mw, title=title)
        self.web_height = 0
        qconnect(self.hide_timer.timeout, self.hide_if_allowed)

    def eventFilter(self, obj, evt):
        if handled := super().eventFilter(obj, evt):
            return handled

        # prevent collapse of both toolbars if pointer is inside one of them
        if evt.type() == QEvent.Type.Enter:
            self.reset_timer()
            self.mw.bottomWeb.reset_timer()
            return True

        return False

    def on_body_classes_need_update(self) -> None:
        super().on_body_classes_need_update()
        self.adjustHeightToFit()

        if self.mw.state == "review":
            if self.mw.pm.hide_top_bar():
                self.eval("""document.body.classList.remove("flat"); """)
            else:
                self.flatten()

        self.show()

    def _onHeight(self, qvar: Optional[int]) -> None:
        super()._onHeight(qvar)
        self.web_height = int(qvar)

    def hide_if_allowed(self) -> None:
        if self.mw.state != "review":
            return

        if self.mw.pm.hide_top_bar():
            self.hide()

    def hide(self) -> None:
        super().hide()

        self.hidden = True
        self.eval(
            """document.body.classList.add("hidden"); """,
        )

    def show(self) -> None:
        super().show()

        self.eval("""document.body.classList.remove("hidden"); """)

    def flatten(self) -> None:
        self.eval("""document.body.classList.add("flat"); """)

    def elevate(self) -> None:
        self.eval(
            """
            document.body.classList.remove("flat");
            document.body.style.removeProperty("background");
            """
        )

    def update_background_image(self) -> None:
        if self.mw.pm.minimalist_mode():
            return

        def set_background(val: str) -> None:
            # remove offset from copy
            background = re.sub(r"-\d+px ", "0%", val)
            # change computedStyle px value back to 100vw
            background = re.sub(r"\d+px", "100vw", background)

            self.eval(
                f"""document.body.style.setProperty("background", '{background}'); """
            )
            # offset reviewer background by toolbar height
            self.mw.web.eval(
                f"""document.body.style.setProperty("background-position-y", "-{self.web_height}px"); """
            )

        self.mw.web.evalWithCallback(
            """window.getComputedStyle(document.body).background; """,
            set_background,
        )


class BottomWebView(ToolbarWebView):
    def __init__(self, mw: aqt.AnkiQt, title: str) -> None:
        super().__init__(mw, title=title)
        qconnect(self.hide_timer.timeout, self.hide_if_allowed)

    def eventFilter(self, obj, evt):
        if handled := super().eventFilter(obj, evt):
            return handled

        if evt.type() == QEvent.Type.Enter:
            self.reset_timer()
            self.mw.toolbarWeb.reset_timer()
            return True

        return False

    def on_body_classes_need_update(self) -> None:
        super().on_body_classes_need_update()

        self.adjustHeightToFit()
        self.show()

    def _onHeight(self, qvar: Optional[int]) -> None:
        self.web_height = int(qvar)

        if qvar is None:
            self.mw.progress.single_shot(1000, self.mw.reset)
            return

        if self.mw.pm.reduce_motion():
            self.setFixedHeight(int(qvar))
        else:
            # Collapse/Expand animation
            self.setMinimumHeight(0)
            self.animation = QPropertyAnimation(
                self, cast(QByteArray, b"maximumHeight")
            )
            self.animation.setDuration(int(theme_manager.var(props.TRANSITION)))
            self.animation.setStartValue(self.height())
            self.animation.setEndValue(int(qvar))
            qconnect(self.animation.finished, lambda: self.setFixedHeight(int(qvar)))
            self.animation.start()

    def hide_if_allowed(self) -> None:
        if self.mw.state != "review":
            return

        if self.mw.pm.hide_bottom_bar():
            self.hide()

    def hide(self) -> None:
        super().hide()

        self._onHeight(1)

    def show(self) -> None:
        super().show()

        self.hidden = False
        self.adjustHeightToFit()


class Toolbar:
    def __init__(self, mw: aqt.AnkiQt, web: AnkiWebView) -> None:
        self.mw = mw
        self.web = web
        self.link_handlers: dict[str, Callable] = {
            "study": self._studyLinkHandler,
        }
        self.web.requiresCol = False

    def draw(
        self,
        buf: str = "",
        web_context: Any | None = None,
        link_handler: Callable[[str], Any] | None = None,
    ) -> None:
        web_context = web_context or TopToolbar(self)
        link_handler = link_handler or self._linkHandler
        self.web.set_bridge_command(link_handler, web_context)
        body = self._body.format(
            toolbar_content=self._centerLinks(),
            left_tray_content=self._left_tray_content(),
            right_tray_content=self._right_tray_content(),
        )
        self.web.stdHtml(
            body,
            css=["css/toolbar.css"],
            js=["js/vendor/jquery.min.js", "js/toolbar.js"],
            context=web_context,
        )
        self.web.adjustHeightToFit()

    def redraw(self) -> None:
        self.set_sync_active(self.mw.media_syncer.is_syncing())
        self.update_sync_status()
        gui_hooks.top_toolbar_did_redraw(self)

    # Available links
    ######################################################################

    def create_link(
        self,
        cmd: str,
        label: str,
        func: Callable,
        tip: str | None = None,
        id: str | None = None,
    ) -> str:
        """Generates HTML link element and registers link handler

        Arguments:
            cmd {str} -- Command name used for the JS → Python bridge
            label {str} -- Display label of the link
            func {Callable} -- Callable to be called on clicking the link

        Keyword Arguments:
            tip {Optional[str]} -- Optional tooltip text to show on hovering
                                   over the link (default: {None})
            id: {Optional[str]} -- Optional id attribute to supply the link with
                                   (default: {None})

        Returns:
            str -- HTML link element
        """

        self.link_handlers[cmd] = func

        title_attr = f'title="{tip}"' if tip else ""
        id_attr = f'id="{id}"' if id else ""

        return (
            f"""<a class=hitem tabindex="-1" aria-label="{label}" """
            f"""{title_attr} {id_attr} href=# onclick="return pycmd('{cmd}')">"""
            f"""{label}</a>"""
        )

    def _centerLinks(self) -> str:
        links = [
            self.create_link(
                "decks",
                tr.actions_decks(),
                self._deckLinkHandler,
                tip=tr.actions_shortcut_key(val="D"),
                id="decks",
            ),
            self.create_link(
                "add",
                tr.actions_add(),
                self._addLinkHandler,
                tip=tr.actions_shortcut_key(val="A"),
                id="add",
            ),
            self.create_link(
                "browse",
                tr.qt_misc_browse(),
                self._browseLinkHandler,
                tip=tr.actions_shortcut_key(val="B"),
                id="browse",
            ),
            self.create_link(
                "stats",
                tr.qt_misc_stats(),
                self._statsLinkHandler,
                tip=tr.actions_shortcut_key(val="T"),
                id="stats",
            ),
        ]

        links.append(self._create_sync_link())

        gui_hooks.top_toolbar_did_init_links(links, self)

        return "\n".join(links)

    # Add-ons
    ######################################################################

    def _left_tray_content(self) -> str:
        left_tray_content: list[str] = []
        gui_hooks.top_toolbar_will_set_left_tray_content(left_tray_content, self)
        return self._process_tray_content(left_tray_content)

    def _right_tray_content(self) -> str:
        right_tray_content: list[str] = []
        gui_hooks.top_toolbar_will_set_right_tray_content(right_tray_content, self)
        return self._process_tray_content(right_tray_content)

    def _process_tray_content(self, content: list[str]) -> str:
        return "\n".join(f"""<div class="tray-item">{item}</div>""" for item in content)

    # Sync
    ######################################################################

    def _create_sync_link(self) -> str:
        name = tr.qt_misc_sync()
        title = tr.actions_shortcut_key(val="Y")
        label = "sync"
        self.link_handlers[label] = self._syncLinkHandler

        return f"""
<a class=hitem tabindex="-1" aria-label="{name}" title="{title}" id="{label}" href=# onclick="return pycmd('{label}')"
>{name}<img id=sync-spinner src='/_anki/imgs/refresh.svg'>
</a>"""

    def set_sync_active(self, active: bool) -> None:
        method = "add" if active else "remove"
        self.web.eval(
            f"document.getElementById('sync-spinner').classList.{method}('spin')"
        )

    def set_sync_status(self, status: SyncStatus) -> None:
        self.web.eval(f"updateSyncColor({status.required})")

    def update_sync_status(self) -> None:
        get_sync_status(self.mw, self.mw.toolbar.set_sync_status)

    # Link handling
    ######################################################################

    def _linkHandler(self, link: str) -> bool:
        if link in self.link_handlers:
            self.link_handlers[link]()
        return False

    def _deckLinkHandler(self) -> None:
        self.mw.moveToState("deckBrowser")

    def _studyLinkHandler(self) -> None:
        # if overview already shown, switch to review
        if self.mw.state == "overview":
            self.mw.col.startTimebox()
            self.mw.moveToState("review")
        else:
            self.mw.onOverview()

    def _addLinkHandler(self) -> None:
        self.mw.onAddCard()

    def _browseLinkHandler(self) -> None:
        self.mw.onBrowse()

    def _statsLinkHandler(self) -> None:
        self.mw.onStats()

    def _syncLinkHandler(self) -> None:
        self.mw.on_sync_button_clicked()

    # HTML & CSS
    ######################################################################

    _body = """
<div class="header">
  <div class="left-tray">{left_tray_content}</div>
  <div class="toolbar">{toolbar_content}</div>
  <div class="right-tray">{right_tray_content}</div>
</div>
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
        web_context: Any | None = None,
        link_handler: Callable[[str], Any] | None = None,
    ) -> None:
        # note: some screens may override this
        web_context = web_context or BottomToolbar(self)
        link_handler = link_handler or self._linkHandler
        self.web.set_bridge_command(link_handler, web_context)
        self.web.stdHtml(
            self._centerBody % buf,
            css=["css/toolbar.css", "css/toolbar-bottom.css"],
            context=web_context,
        )
        self.web.adjustHeightToFit()
