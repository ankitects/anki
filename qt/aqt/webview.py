# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import dataclasses
import json
import os
import re
import sys
from collections.abc import Callable, Sequence
from enum import Enum
from typing import TYPE_CHECKING, Any, cast

import anki
import anki.lang
from anki._legacy import deprecated
from anki.lang import is_rtl
from anki.utils import hmr_mode, is_lin, is_mac, is_win
from aqt import colors, gui_hooks
from aqt.qt import *
from aqt.qt import sip
from aqt.theme import theme_manager
from aqt.utils import askUser, is_gesture_or_zoom_event, openLink, showInfo, tr

serverbaseurl = re.compile(r"^.+:\/\/[^\/]+")

if TYPE_CHECKING:
    from aqt.mediasrv import PageContext


# Page for debug messages
##########################################################################

BridgeCommandHandler = Callable[[str], Any]


class AnkiWebPage(QWebEnginePage):
    def __init__(self, onBridgeCmd: BridgeCommandHandler) -> None:
        QWebEnginePage.__init__(self)
        self._onBridgeCmd = onBridgeCmd
        self._setupBridge()
        self.open_links_externally = True

    def _setupBridge(self) -> None:
        class Bridge(QObject):
            def __init__(self, bridge_handler: Callable[[str], Any]) -> None:
                super().__init__()
                self.onCmd = bridge_handler

            @pyqtSlot(str, result=str)  # type: ignore
            def cmd(self, str: str) -> Any:
                return json.dumps(self.onCmd(str))

        self._bridge = Bridge(self._onCmd)

        self._channel = QWebChannel(self)
        self._channel.registerObject("py", self._bridge)
        self.setWebChannel(self._channel)

        qwebchannel = ":/qtwebchannel/qwebchannel.js"
        jsfile = QFile(qwebchannel)
        if not jsfile.open(QIODevice.OpenModeFlag.ReadOnly):
            print(f"Error opening '{qwebchannel}': {jsfile.error()}", file=sys.stderr)
        jstext = bytes(cast(bytes, jsfile.readAll())).decode("utf-8")
        jsfile.close()

        script = QWebEngineScript()
        script.setSourceCode(
            jstext
            + """
            var pycmd, bridgeCommand;
            new QWebChannel(qt.webChannelTransport, function(channel) {
                bridgeCommand = pycmd = function (arg, cb) {
                    var resultCB = function (res) {
                        // pass result back to user-provided callback
                        if (cb) {
                            cb(JSON.parse(res));
                        }
                    }
                
                    channel.objects.py.cmd(arg, resultCB);
                    return false;                   
                }
                pycmd("domDone");
            });
        """
        )
        script.setWorldId(QWebEngineScript.ScriptWorldId.MainWorld)
        script.setInjectionPoint(QWebEngineScript.InjectionPoint.DocumentReady)
        script.setRunsOnSubFrames(False)
        self.profile().scripts().insert(script)

    def javaScriptConsoleMessage(
        self,
        level: QWebEnginePage.JavaScriptConsoleMessageLevel,
        msg: str,
        line: int,
        srcID: str,
    ) -> None:
        # not translated because console usually not visible,
        # and may only accept ascii text
        if srcID.startswith("data"):
            srcID = ""
        else:
            srcID = serverbaseurl.sub("", srcID[:80], 1)
        if level == QWebEnginePage.JavaScriptConsoleMessageLevel.InfoMessageLevel:
            level_str = "info"
        elif level == QWebEnginePage.JavaScriptConsoleMessageLevel.WarningMessageLevel:
            level_str = "warning"
        elif level == QWebEnginePage.JavaScriptConsoleMessageLevel.ErrorMessageLevel:
            level_str = "error"
        else:
            level_str = str(level)
        buf = "JS %(t)s %(f)s:%(a)d %(b)s" % dict(
            t=level_str, a=line, f=srcID, b=f"{msg}\n"
        )
        if "MathJax localStorage" in buf:
            # silence localStorage noise
            return
        elif "link preload" in buf:
            # silence 'link preload' warning on the first card
            return
        # ensure we don't try to write characters the terminal can't handle
        buf = buf.encode(sys.stdout.encoding, "backslashreplace").decode(
            sys.stdout.encoding
        )
        # output to stdout because it may raise error messages on the anki GUI
        # https://github.com/ankitects/anki/pull/560
        sys.stdout.write(buf)

    def acceptNavigationRequest(
        self, url: QUrl, navType: Any, isMainFrame: bool
    ) -> bool:
        from aqt.mediasrv import is_sveltekit_page

        if (
            not self.open_links_externally
            or "_anki/pages" in url.path()
            or url.path() == "/_anki/legacyPageData"
            or is_sveltekit_page(url.path()[1:])
        ):
            return super().acceptNavigationRequest(url, navType, isMainFrame)

        if not isMainFrame:
            return True
        # data: links generated by setHtml()
        if url.scheme() == "data":
            return True
        # catch buggy <a href='#' onclick='func()'> links
        from aqt import mw

        if url.matches(
            QUrl(mw.serverURL()), cast(Any, QUrl.UrlFormattingOption.RemoveFragment)
        ):
            print("onclick handler needs to return false")
            return False
        # load all other links in browser
        openLink(url)
        return False

    def _onCmd(self, str: str) -> Any:
        return self._onBridgeCmd(str)

    def javaScriptAlert(self, frame: Any, text: str) -> None:
        showInfo(text)

    def javaScriptConfirm(self, frame: Any, text: str) -> bool:
        return askUser(text)


# Add-ons
##########################################################################


@dataclasses.dataclass
class WebContent:
    """Stores all dynamically modified content that a particular web view
    will be populated with.

    Attributes:
        body {str} -- HTML body
        head {str} -- HTML head
        css {List[str]} -- List of media server subpaths,
                           each pointing to a CSS file
        js {List[str]} -- List of media server subpaths,
                          each pointing to a JS file

    Important Notes:
        - When modifying the attributes specified above, please make sure your
        changes only perform the minimum required edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:

            def on_webview_will_set_content(web_content: WebContent, context) -> None:
                web_content.body += "<my_html>"
                web_content.head += "<my_head>"

        - The paths specified in `css` and `js` need to be accessible by Anki's
          media server. All list members without a specified subpath are assumed
          to be located under `/_anki`, which is the media server subpath used
          for all web assets shipped with Anki.

          Add-ons may expose their own web assets by utilizing
          aqt.addons.AddonManager.setWebExports(). Web exports registered
          in this manner may then be accessed under the `/_addons` subpath.

          E.g., to allow access to a `my-addon.js` and `my-addon.css` residing
          in a "web" subfolder in your add-on package, first register the
          corresponding web export:

          > from aqt import mw
          > mw.addonManager.setWebExports(__name__, r"web/.*(css|js)")

          Then append the subpaths to the corresponding web_content fields
          within a function subscribing to gui_hooks.webview_will_set_content:

              def on_webview_will_set_content(web_content: WebContent, context) -> None:
                  addon_package = mw.addonManager.addonFromModule(__name__)
                  web_content.css.append(
                      f"/_addons/{addon_package}/web/my-addon.css")
                  web_content.js.append(
                      f"/_addons/{addon_package}/web/my-addon.js")

          Note that '/' will also match the os specific path separator.
    """

    body: str = ""
    head: str = ""
    css: list[str] = dataclasses.field(default_factory=lambda: [])
    js: list[str] = dataclasses.field(default_factory=lambda: [])


# Main web view
##########################################################################


class AnkiWebViewKind(Enum):
    """Enum registry of all web views managed by Anki

    The value of each entry corresponds to the web view's title.

    When introducing a new web view, please add it to the registry below.
    """

    DEFAULT = "default"
    MAIN = "main webview"
    TOP_TOOLBAR = "top toolbar"
    BOTTOM_TOOLBAR = "bottom toolbar"
    DECK_OPTIONS = "deck options"
    EDITOR = "editor"
    LEGACY_DECK_STATS = "legacy deck stats"
    DECK_STATS = "deck stats"
    PREVIEWER = "previewer"
    CHANGE_NOTETYPE = "change notetype"
    CARD_LAYOUT = "card layout"
    BROWSER_CARD_INFO = "browser card info"
    IMPORT_CSV = "csv import"
    EMPTY_CARDS = "empty cards"
    FIND_DUPLICATES = "find duplicates"
    FIELDS = "fields"
    IMPORT_LOG = "import log"
    IMPORT_ANKI_PACKAGE = "anki package import"


class AnkiWebView(QWebEngineView):
    allow_drops = False
    _kind: AnkiWebViewKind

    def __init__(
        self,
        parent: QWidget | None = None,
        title: str = "",  # used by add-ons; in Anki code use kind instead to set title
        kind: AnkiWebViewKind = AnkiWebViewKind.DEFAULT,
    ) -> None:
        QWebEngineView.__init__(self, parent=parent)
        self.set_kind(kind)
        if title:
            self.set_title(title)
        self._page = AnkiWebPage(self._onBridgeCmd)
        # reduce flicker
        self._page.setBackgroundColor(theme_manager.qcolor(colors.CANVAS))

        # in new code, use .set_bridge_command() instead of setting this directly
        self.onBridgeCmd: Callable[[str], Any] = self.defaultOnBridgeCmd

        self._domDone = True
        self._pendingActions: list[tuple[str, Sequence[Any]]] = []
        self.requiresCol = True
        self.setPage(self._page)
        self._disable_zoom = False

        self.resetHandlers()
        self._filterSet = False
        gui_hooks.theme_did_change.append(self.on_theme_did_change)
        gui_hooks.body_classes_need_update.append(self.on_body_classes_need_update)

        qconnect(self.loadFinished, self._on_load_finished)

    def _on_load_finished(self) -> None:
        self.eval(
            """
        document.addEventListener("keydown", function(evt) {
            if (evt.key === "Escape") {
                pycmd("close");
            }
        });
        """
        )

    def set_kind(self, kind: AnkiWebViewKind) -> None:
        self._kind = kind
        self.set_title(kind.value)

    @property
    def kind(self) -> AnkiWebViewKind:
        """Used by add-ons to identify the webview kind"""
        return self._kind

    def set_title(self, title: str) -> None:
        self.title = title  # type: ignore[assignment]

    def disable_zoom(self) -> None:
        self._disable_zoom = True

    def createWindow(self, windowType: QWebEnginePage.WebWindowType) -> QWebEngineView:
        # intercept opening a new window (hrefs
        # with target="_blank") and return view
        return AnkiWebView()

    def eventFilter(self, obj: QObject, evt: QEvent) -> bool:
        if self._disable_zoom and is_gesture_or_zoom_event(evt):
            return True

        if (
            isinstance(evt, QMouseEvent)
            and evt.type() == QEvent.Type.MouseButtonRelease
        ):
            if evt.button() == Qt.MouseButton.MiddleButton and is_lin:
                self.onMiddleClickPaste()
                return True

        return False

    def set_open_links_externally(self, enable: bool) -> None:
        self._page.open_links_externally = enable

    def onEsc(self) -> None:
        w = self.parent()
        while w:
            if isinstance(w, QDialog) or isinstance(w, QMainWindow):
                from aqt import mw

                # esc in a child window closes the window
                if w != mw:
                    w.close()
                else:
                    # in the main window, removes focus from type in area
                    parent = self.parent()
                    assert isinstance(parent, QWidget)
                    parent.setFocus()
                break
            w = w.parent()

    def onCopy(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Copy)

    def onCut(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Cut)

    def onPaste(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Paste)

    def onMiddleClickPaste(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.Paste)

    def onSelectAll(self) -> None:
        self.triggerPageAction(QWebEnginePage.WebAction.SelectAll)

    def contextMenuEvent(self, evt: QContextMenuEvent) -> None:
        m = QMenu(self)
        self._maybe_add_copy_action(m)
        gui_hooks.webview_will_show_context_menu(self, m)
        m.popup(QCursor.pos())

    def _maybe_add_copy_action(self, menu: QMenu) -> None:
        if self.hasSelection():
            a = menu.addAction(tr.actions_copy())
            qconnect(a.triggered, self.onCopy)

    def dropEvent(self, evt: QDropEvent) -> None:
        if self.allow_drops:
            super().dropEvent(evt)

    def setHtml(  #  type: ignore[override]
        self, html: str, context: PageContext | None = None
    ) -> None:
        from aqt.mediasrv import PageContext

        # discard any previous pending actions
        self._pendingActions = []
        self._domDone = True
        if context is None:
            context = PageContext.UNKNOWN
        self._queueAction("setHtml", html, context)
        self.set_open_links_externally(True)
        self.allow_drops = False
        self.show()

    def _setHtml(self, html: str, context: PageContext) -> None:
        """Send page data to media server, then surf to it.

        This function used to be implemented by QWebEngine's
        .setHtml() call. It is no longer used, as it has a
        maximum size limit, and due to security changes, it
        will stop working in the future."""
        from aqt import mw

        oldFocus = mw.app.focusWidget()
        self._domDone = False

        webview_id = id(self)
        mw.mediaServer.set_page_html(webview_id, html, context)
        self.load_url(QUrl(f"{mw.serverURL()}_anki/legacyPageData?id={webview_id}"))

        # work around webengine stealing focus on setHtml()
        # fixme: check which if any qt versions this is still required on
        if oldFocus:
            oldFocus.setFocus()

    def load_url(self, url: QUrl) -> None:
        # allow queuing actions when loading url directly
        self._domDone = False
        self.allow_drops = False
        super().load(url)

    def app_zoom_factor(self) -> float:
        # overridden scale factor?
        webscale = os.environ.get("ANKI_WEBSCALE")
        if webscale:
            return float(webscale)

        if qtmajor > 5 or is_mac:
            return 1
        screen = QApplication.desktop().screen()  # type: ignore
        if screen is None:
            return 1

        dpi = screen.logicalDpiX()
        factor = dpi / 96.0
        if is_lin:
            factor = max(1, factor)
            return factor
        return 1

    def setPlaybackRequiresGesture(self, value: bool) -> None:
        self.settings().setAttribute(
            QWebEngineSettings.WebAttribute.PlaybackRequiresUserGesture, value
        )

    def _getQtIntScale(self, screen: QWidget) -> int:
        # try to detect if Qt has scaled the screen
        # - qt will round the scale factor to a whole number, so a dpi of 125% = 1x,
        #   and a dpi of 150% = 2x
        # - a screen with a normal physical dpi of 72 will have a dpi of 32
        #   if the scale factor has been rounded to 2x
        # - different screens have different physical DPIs (eg 72, 93, 102)
        # - until a better solution presents itself, assume a physical DPI at
        #   or above 70 is unscaled
        if screen.physicalDpiX() > 70:
            return 1
        elif screen.physicalDpiX() > 35:
            return 2
        else:
            return 3

    def standard_css(self) -> str:
        color_hl = theme_manager.var(colors.BORDER_FOCUS)

        if is_win:
            # T: include a font for your language on Windows, eg: "Segoe UI", "MS Mincho"
            family = tr.qt_misc_segoe_ui()
            button_style = f"""
button {{ font-family: {family}; }}
            """
            font = f"font-family:{family};"
        elif is_mac:
            family = "Helvetica"
            font = f'font-family:"{family}";'
            button_style = """
button {
    --canvas: #fff;
    -webkit-appearance: none;
    background: var(--canvas);
    border-radius: var(--border-radius);
    padding: 3px 12px;
    border: 1px solid var(--border);
    box-shadow: 0px 1px 3px var(--border-subtle);
    font-family: Helvetica
}
.night-mode button { --canvas: #606060; --fg: #eee; }
"""
        else:
            family = self.font().family()
            font = f'font-family:"{family}", sans-serif;'
            button_style = """
/* Buttons */
button{{ 
    font-family: "{family}", sans-serif;
}}
/* Input field focus outline */
textarea:focus, input:focus, input[type]:focus, .uneditable-input:focus,
div[contenteditable="true"]:focus {{   
    outline: 0 none;
    border-color: {color_hl};
}}""".format(
                family=family,
                color_hl=color_hl,
            )

        zoom = self.app_zoom_factor()

        return f"""
body {{ zoom: {zoom}; background-color: var(--canvas); }}
html {{ {font} }}
{button_style}
:root {{ --canvas: {colors.CANVAS["light"]} }}
:root[class*=night-mode] {{ --canvas: {colors.CANVAS["dark"]} }}
"""

    def stdHtml(
        self,
        body: str,
        css: list[str] | None = None,
        js: list[str] | None = None,
        head: str = "",
        context: Any | None = None,
        default_css: bool = True,
    ) -> None:
        css = (["css/webview.css"] if default_css else []) + (
            [] if css is None else css
        )
        web_content = WebContent(
            body=body,
            head=head,
            js=["js/webview.js"] + (["js/vendor/jquery.min.js"] if js is None else js),
            css=css,
        )

        gui_hooks.webview_will_set_content(web_content, context)

        csstxt = ""
        if "css/webview.css" in css:
            # we want our dynamic styling to override the defaults in
            # css/webview.css, but come before user-provided stylesheets so that
            # they can override us if necessary
            web_content.css.remove("css/webview.css")
            csstxt = self.bundledCSS("css/webview.css")
            csstxt += f"<style>{self.standard_css()}</style>"

        csstxt += "\n".join(self.bundledCSS(fname) for fname in web_content.css)
        jstxt = "\n".join(self.bundledScript(fname) for fname in web_content.js)

        from aqt import mw

        head = mw.baseHTML() + csstxt + web_content.head
        body_class = theme_manager.body_class()

        if theme_manager.night_mode:
            doc_class = "night-mode"
            bs_theme = "dark"
        else:
            doc_class = ""
            bs_theme = "light"

        if is_rtl(anki.lang.current_lang):
            lang_dir = "rtl"
        else:
            lang_dir = "ltr"

        html = f"""
<!doctype html>
<html class="{doc_class}" dir="{lang_dir}" data-bs-theme="{bs_theme}">
<head>
    <title>{self.title}</title>
{head}
</head>

<body class="{body_class}">
{jstxt}
{web_content.body}</body>
</html>"""
        # print(html)
        import aqt.browser.previewer
        import aqt.clayout
        import aqt.editor
        import aqt.reviewer
        from aqt.mediasrv import PageContext

        if isinstance(context, aqt.editor.Editor):
            page_context = PageContext.EDITOR
        elif isinstance(context, aqt.reviewer.Reviewer):
            page_context = PageContext.REVIEWER
        elif isinstance(context, aqt.browser.previewer.Previewer):
            page_context = PageContext.PREVIEWER
        elif isinstance(context, aqt.clayout.CardLayout):
            page_context = PageContext.CARD_LAYOUT
        else:
            page_context = PageContext.UNKNOWN
        self.setHtml(html, page_context)

    @classmethod
    def webBundlePath(cls, path: str) -> str:
        from aqt import mw

        if path.startswith("/"):
            subpath = ""
        else:
            subpath = "/_anki/"

        return f"http://127.0.0.1:{mw.mediaServer.getPort()}{subpath}{path}"

    def bundledScript(self, fname: str) -> str:
        return f'<script src="{self.webBundlePath(fname)}"></script>'

    def bundledCSS(self, fname: str) -> str:
        return '<link rel="stylesheet" type="text/css" href="%s">' % self.webBundlePath(
            fname
        )

    def eval(self, js: str) -> None:
        self.evalWithCallback(js, None)

    def evalWithCallback(self, js: str, cb: Callable) -> None:
        self._queueAction("eval", js, cb)

    def _evalWithCallback(self, js: str, cb: Callable[[Any], Any]) -> None:
        if cb:

            def handler(val: Any) -> None:
                if self._shouldIgnoreWebEvent():
                    print("ignored late js callback", cb)
                    return
                cb(val)

            self.page().runJavaScript(js, handler)
        else:
            self.page().runJavaScript(js)

    def _queueAction(self, name: str, *args: Any) -> None:
        self._pendingActions.append((name, args))
        self._maybeRunActions()

    def _maybeRunActions(self) -> None:
        if sip.isdeleted(self):
            return
        while self._pendingActions and self._domDone:
            name, args = self._pendingActions.pop(0)

            if name == "eval":
                self._evalWithCallback(*args)
            elif name == "setHtml":
                self._setHtml(*args)
            else:
                raise Exception(f"unknown action: {name}")

    def _openLinksExternally(self, url: str) -> None:
        openLink(url)

    def _shouldIgnoreWebEvent(self) -> bool:
        # async web events may be received after the profile has been closed
        # or the underlying webview has been deleted
        from aqt import mw

        if sip.isdeleted(self):
            return True
        if not mw.col and self.requiresCol:
            return True
        return False

    def _onBridgeCmd(self, cmd: str) -> Any:
        if self._shouldIgnoreWebEvent():
            print("ignored late bridge cmd", cmd)
            return

        if not self._filterSet:
            self.focusProxy().installEventFilter(self)
            self._filterSet = True

        if cmd == "domDone":
            self._domDone = True
            self._maybeRunActions()
        elif cmd == "close":
            self.onEsc()
        else:
            handled, result = gui_hooks.webview_did_receive_js_message(
                (False, None), cmd, self._bridge_context
            )
            if handled:
                return result
            else:
                return self.onBridgeCmd(cmd)

    def defaultOnBridgeCmd(self, cmd: str) -> None:
        print("unhandled bridge cmd:", cmd)

    # legacy
    def resetHandlers(self) -> None:
        self.onBridgeCmd = self.defaultOnBridgeCmd
        self._bridge_context = None

    def adjustHeightToFit(self) -> None:
        self.evalWithCallback("document.documentElement.offsetHeight", self._onHeight)

    def _onHeight(self, qvar: int | None) -> None:
        from aqt import mw

        if qvar is None:
            mw.progress.single_shot(1000, mw.reset)
            return

        self.setFixedHeight(int(qvar))

    def set_bridge_command(self, func: Callable[[str], Any], context: Any) -> None:
        """Set a handler for pycmd() messages received from Javascript.

        Context is the object calling this routine, eg an instance of
        aqt.reviewer.Reviewer or aqt.deckbrowser.DeckBrowser."""
        self.onBridgeCmd = func
        self._bridge_context = context

    def hide_while_preserving_layout(self) -> None:
        "Hide but keep existing size."
        sp = self.sizePolicy()
        sp.setRetainSizeWhenHidden(True)
        self.setSizePolicy(sp)
        self.hide()

    def add_dynamic_styling_and_props_then_show(self) -> None:
        "Add dynamic styling, title, set platform-specific body classes and reveal."
        css = self.standard_css()
        body_classes = theme_manager.body_class().split(" ")

        def after_injection(arg: Any) -> None:
            gui_hooks.webview_did_inject_style_into_page(self)
            self.show()

        if theme_manager.night_mode:
            night_mode = 'document.documentElement.classList.add("night-mode");'
        else:
            night_mode = ""
        self.evalWithCallback(
            f"""
(function(){{
    document.title = `{self.title}`;
    const style = document.createElement('style');
    style.innerHTML = `{css}`;
    document.head.appendChild(style);
    document.body.classList.add({", ".join([f'"{c}"' for c in body_classes])});
    {night_mode}
}})();
""",
            after_injection,
        )

    def load_ts_page(self, name: str) -> None:
        from aqt import mw

        self.set_open_links_externally(True)
        if theme_manager.night_mode:
            extra = "#night"
        else:
            extra = ""
        self.load_url(QUrl(f"{mw.serverURL()}_anki/pages/{name}.html{extra}"))
        self.add_dynamic_styling_and_props_then_show()

    def load_sveltekit_page(self, path: str) -> None:
        from aqt import mw

        self.set_open_links_externally(True)
        if theme_manager.night_mode:
            extra = "#night"
        else:
            extra = ""

        if hmr_mode:
            server = "http://127.0.0.1:5173/"
        else:
            server = mw.serverURL()

        self.load_url(QUrl(f"{server}{path}{extra}"))
        self.add_dynamic_styling_and_props_then_show()

    def force_load_hack(self) -> None:
        """Force process to initialize.
        Must be done on Windows prior to changing current working directory."""
        self.requiresCol = False
        self._domReady = False
        self._page.setContent(cast(QByteArray, bytes("", "ascii")))

    def cleanup(self) -> None:
        try:
            from aqt import mw
        except ImportError:
            # this will fail when __del__ is called during app shutdown
            return

        gui_hooks.theme_did_change.remove(self.on_theme_did_change)
        gui_hooks.body_classes_need_update.remove(self.on_body_classes_need_update)
        # defer page cleanup so that in-flight requests have a chance to complete first
        # https://forums.ankiweb.net/t/error-when-exiting-browsing-when-the-software-is-installed-in-the-path-c-program-files-anki/38363
        mw.progress.single_shot(5000, lambda: mw.mediaServer.clear_page_html(id(self)))
        self._page.deleteLater()

    def on_theme_did_change(self) -> None:
        # avoid flashes if page reloaded
        self._page.setBackgroundColor(theme_manager.qcolor(colors.CANVAS))
        # update night-mode class, and legacy nightMode/night-mode body classes
        self.eval(
            f"""
(function() {{
    const doc = document.documentElement;
    const body = document.body.classList;
    if ({1 if theme_manager.night_mode else 0}) {{
        doc.dataset.bsTheme = "dark";
        doc.classList.add("night-mode");
        body.add("night_mode");
        body.add("nightMode");
        {"body.add('macos-dark-mode');" if theme_manager.macos_dark_mode() else ""}
    }} else {{
        doc.dataset.bsTheme = "light";
        doc.classList.remove("night-mode");
        body.remove("night_mode");
        body.remove("nightMode");
        body.remove("macos-dark-mode");
    }}
}})();
"""
        )

    def on_body_classes_need_update(self) -> None:
        from aqt import mw

        self.eval(
            f"""document.body.classList.toggle("fancy", {json.dumps(not mw.pm.minimalist_mode())}); """
        )
        self.eval(
            f"""document.body.classList.toggle("reduce-motion", {json.dumps(mw.pm.reduce_motion())}); """
        )

    @deprecated(info="use theme_manager.qcolor() instead")
    def get_window_bg_color(self, night_mode: bool | None = None) -> QColor:
        return theme_manager.qcolor(colors.CANVAS)
