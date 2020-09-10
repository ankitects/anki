# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import dataclasses
import json
import math
import re
import sys
from typing import Any, Callable, List, Optional, Sequence, Tuple

import anki
from anki.lang import _, is_rtl
from anki.utils import isLin, isMac, isWin
from aqt import gui_hooks
from aqt.qt import *
from aqt.theme import theme_manager
from aqt.utils import openLink, showInfo

serverbaseurl = re.compile(r"^.+:\/\/[^\/]+")

# Page for debug messages
##########################################################################


class AnkiWebPage(QWebEnginePage):
    def __init__(self, onBridgeCmd):
        QWebEnginePage.__init__(self)
        self._onBridgeCmd = onBridgeCmd
        self._setupBridge()
        self.open_links_externally = True

    def _setupBridge(self) -> None:
        class Bridge(QObject):
            @pyqtSlot(str, result=str)  # type: ignore
            def cmd(self, str):
                return json.dumps(self.onCmd(str))

        self._bridge = Bridge()
        self._bridge.onCmd = self._onCmd

        self._channel = QWebChannel(self)
        self._channel.registerObject("py", self._bridge)
        self.setWebChannel(self._channel)

        qwebchannel = ":/qtwebchannel/qwebchannel.js"
        jsfile = QFile(qwebchannel)
        if not jsfile.open(QIODevice.ReadOnly):
            print(f"Error opening '{qwebchannel}': {jsfile.error()}", file=sys.stderr)
        jstext = bytes(jsfile.readAll()).decode("utf-8")
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
        script.setWorldId(QWebEngineScript.MainWorld)
        script.setInjectionPoint(QWebEngineScript.DocumentReady)
        script.setRunsOnSubFrames(False)
        self.profile().scripts().insert(script)

    def javaScriptConsoleMessage(self, level, msg, line, srcID):
        # not translated because console usually not visible,
        # and may only accept ascii text
        if srcID.startswith("data"):
            srcID = ""
        else:
            srcID = serverbaseurl.sub("", srcID[:80], 1)
        if level == QWebEnginePage.InfoMessageLevel:
            level = "info"
        elif level == QWebEnginePage.WarningMessageLevel:
            level = "warning"
        elif level == QWebEnginePage.ErrorMessageLevel:
            level = "error"
        buf = "JS %(t)s %(f)s:%(a)d %(b)s" % dict(
            t=level, a=line, f=srcID, b=msg + "\n"
        )
        # ensure we don't try to write characters the terminal can't handle
        buf = buf.encode(sys.stdout.encoding, "backslashreplace").decode(
            sys.stdout.encoding
        )
        # output to stdout because it may raise error messages on the anki GUI
        # https://github.com/ankitects/anki/pull/560
        sys.stdout.write(buf)

    def acceptNavigationRequest(self, url, navType, isMainFrame):
        if not self.open_links_externally:
            return super().acceptNavigationRequest(url, navType, isMainFrame)

        if not isMainFrame:
            return True
        # data: links generated by setHtml()
        if url.scheme() == "data":
            return True
        # catch buggy <a href='#' onclick='func()'> links
        from aqt import mw

        if url.matches(QUrl(mw.serverURL()), QUrl.RemoveFragment):
            print("onclick handler needs to return false")
            return False
        # load all other links in browser
        openLink(url)
        return False

    def _onCmd(self, str):
        return self._onBridgeCmd(str)

    def javaScriptAlert(self, url: QUrl, text: str):
        showInfo(text)


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
        changes only perform the minimum requried edits to make your add-on work.
        You should avoid overwriting or interfering with existing data as much
        as possible, instead opting to append your own changes, e.g.:

            def on_webview_will_set_content(web_content: WebContent, context):
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

              def on_webview_will_set_content(web_content: WebContent, context):
                  addon_package = mw.addonManager.addonFromModule(__name__)
                  web_content.css.append(
                      f"/_addons/{addon_package}/web/my-addon.css")
                  web_content.js.append(
                      f"/_addons/{addon_package}/web/my-addon.js")

          Note that '/' will also match the os specific path separator.
    """

    body: str = ""
    head: str = ""
    css: List[str] = dataclasses.field(default_factory=lambda: [])
    js: List[str] = dataclasses.field(default_factory=lambda: [])


# Main web view
##########################################################################


class AnkiWebView(QWebEngineView):
    def __init__(
        self, parent: Optional[QWidget] = None, title: str = "default"
    ) -> None:
        QWebEngineView.__init__(self, parent=parent)
        self.title = title  # type: ignore
        self._page = AnkiWebPage(self._onBridgeCmd)
        self._page.setBackgroundColor(self._getWindowColor())  # reduce flicker

        # in new code, use .set_bridge_command() instead of setting this directly
        self.onBridgeCmd: Callable[[str], Any] = self.defaultOnBridgeCmd

        self._domDone = True
        self._pendingActions: List[Tuple[str, Sequence[Any]]] = []
        self.requiresCol = True
        self.setPage(self._page)

        self._page.profile().setHttpCacheType(QWebEngineProfile.NoCache)
        self.resetHandlers()
        self.allowDrops = False
        self._filterSet = False
        QShortcut(  # type: ignore
            QKeySequence("Esc"),
            self,
            context=Qt.WidgetWithChildrenShortcut,
            activated=self.onEsc,
        )
        if isMac:
            for key, fn in [
                (QKeySequence.Copy, self.onCopy),
                (QKeySequence.Paste, self.onPaste),
                (QKeySequence.Cut, self.onCut),
                (QKeySequence.SelectAll, self.onSelectAll),
            ]:
                QShortcut(  # type: ignore
                    key, self, context=Qt.WidgetWithChildrenShortcut, activated=fn
                )
            QShortcut(  # type: ignore
                QKeySequence("ctrl+shift+v"),
                self,
                context=Qt.WidgetWithChildrenShortcut,
                activated=self.onPaste,
            )

    def eventFilter(self, obj: QObject, evt: QEvent) -> bool:
        # disable pinch to zoom gesture
        if isinstance(evt, QNativeGestureEvent):
            return True
        elif evt.type() == QEvent.MouseButtonRelease:
            if evt.button() == Qt.MidButton and isLin:
                self.onMiddleClickPaste()
                return True
            return False
        return False

    def set_open_links_externally(self, enable: bool) -> None:
        self._page.open_links_externally = enable

    def onEsc(self):
        w = self.parent()
        while w:
            if isinstance(w, QDialog) or isinstance(w, QMainWindow):
                from aqt import mw

                # esc in a child window closes the window
                if w != mw:
                    w.close()
                else:
                    # in the main window, removes focus from type in area
                    self.parent().setFocus()
                break
            w = w.parent()

    def onCopy(self):
        self.triggerPageAction(QWebEnginePage.Copy)

    def onCut(self):
        self.triggerPageAction(QWebEnginePage.Cut)

    def onPaste(self):
        self.triggerPageAction(QWebEnginePage.Paste)

    def onMiddleClickPaste(self):
        self.triggerPageAction(QWebEnginePage.Paste)

    def onSelectAll(self):
        self.triggerPageAction(QWebEnginePage.SelectAll)

    def contextMenuEvent(self, evt: QContextMenuEvent) -> None:
        m = QMenu(self)
        a = m.addAction(_("Copy"))
        qconnect(a.triggered, self.onCopy)
        gui_hooks.webview_will_show_context_menu(self, m)
        m.popup(QCursor.pos())

    def dropEvent(self, evt):
        pass

    def setHtml(self, html: str) -> None:  #  type: ignore
        # discard any previous pending actions
        self._pendingActions = []
        self._domDone = True
        self._queueAction("setHtml", html)

    def _setHtml(self, html: str) -> None:
        app = QApplication.instance()
        oldFocus = app.focusWidget()
        self._domDone = False
        self._page.setHtml(html)
        # work around webengine stealing focus on setHtml()
        if oldFocus:
            oldFocus.setFocus()

    def load(self, url: QUrl):
        # allow queuing actions when loading url directly
        self._domDone = False
        super().load(url)

    def zoomFactor(self) -> float:
        # overridden scale factor?
        webscale = os.environ.get("ANKI_WEBSCALE")
        if webscale:
            return float(webscale)

        if isMac:
            return 1
        screen = QApplication.desktop().screen()
        if screen is None:
            return 1

        dpi = screen.logicalDpiX()
        factor = dpi / 96.0
        if isLin:
            factor = max(1, factor)
            return factor
        # compensate for qt's integer scaling on windows?
        if qtminor >= 14:
            return 1
        qtIntScale = self._getQtIntScale(screen)
        desiredScale = factor * qtIntScale
        newFactor = desiredScale / qtIntScale
        return max(1, newFactor)

    def _getQtIntScale(self, screen) -> int:
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

    def _getWindowColor(self):
        if theme_manager.night_mode:
            return theme_manager.qcolor("window-bg")
        if isMac:
            # standard palette does not return correct window color on macOS
            return QColor("#ececec")
        return self.style().standardPalette().color(QPalette.Window)

    def standard_css(self) -> str:
        palette = self.style().standardPalette()
        color_hl = palette.color(QPalette.Highlight).name()

        if isWin:
            # T: include a font for your language on Windows, eg: "Segoe UI", "MS Mincho"
            family = _('"Segoe UI"')
            button_style = "button { font-family:%s; }" % family
            button_style += "\n:focus { outline: 1px solid %s; }" % color_hl
            font = "font-size:12px;font-family:%s;" % family
        elif isMac:
            family = "Helvetica"
            font = 'font-size:15px;font-family:"%s";' % family
            button_style = """
button { -webkit-appearance: none; background: #fff; border: 1px solid #ccc;
border-radius:5px; font-family: Helvetica }"""
        else:
            family = self.font().family()
            color_hl_txt = palette.color(QPalette.HighlightedText).name()
            color_btn = palette.color(QPalette.Button).name()
            font = 'font-size:14px;font-family:"%s";' % family
            button_style = """
/* Buttons */
button{ 
        background-color: %(color_btn)s;
        font-family:"%(family)s"; }
button:focus{ border-color: %(color_hl)s }
button:active, button:active:hover { background-color: %(color_hl)s; color: %(color_hl_txt)s;}
/* Input field focus outline */
textarea:focus, input:focus, input[type]:focus, .uneditable-input:focus,
div[contenteditable="true"]:focus {   
    outline: 0 none;
    border-color: %(color_hl)s;
}""" % {
                "family": family,
                "color_btn": color_btn,
                "color_hl": color_hl,
                "color_hl_txt": color_hl_txt,
            }

        zoom = self.zoomFactor()
        background = self._getWindowColor().name()

        if is_rtl(anki.lang.currentLang):
            lang_dir = "rtl"
        else:
            lang_dir = "ltr"

        return f"""
body {{ zoom: {zoom}; background: {background}; direction: {lang_dir}; {font} }}
{button_style}
:root {{ --window-bg: {background} }}
:root[class*=night-mode] {{ --window-bg: {background} }}
"""

    def stdHtml(
        self,
        body: str,
        css: Optional[List[str]] = None,
        js: Optional[List[str]] = None,
        head: str = "",
        context: Optional[Any] = None,
    ):

        web_content = WebContent(
            body=body,
            head=head,
            js=["webview.js"] + (["jquery.js"] if js is None else js),
            css=["webview.css"] + ([] if css is None else css),
        )

        gui_hooks.webview_will_set_content(web_content, context)

        csstxt = ""
        if "webview.css" in web_content.css:
            # we want our dynamic styling to override the defaults in
            # webview.css, but come before user-provided stylesheets so that
            # they can override us if necessary
            web_content.css.remove("webview.css")
            csstxt = self.bundledCSS("webview.css")
            csstxt += f"<style>{self.standard_css()}</style>"

        csstxt += "\n".join(self.bundledCSS(fname) for fname in web_content.css)
        jstxt = "\n".join(self.bundledScript(fname) for fname in web_content.js)

        from aqt import mw

        head = mw.baseHTML() + csstxt + jstxt + web_content.head
        body_class = theme_manager.body_class()

        if theme_manager.night_mode:
            doc_class = "night-mode"
        else:
            doc_class = ""

        html = f"""
<!doctype html>
<html class="{doc_class}">
<head>
    <title>{self.title}</title>
{head}
</head>

<body class="{body_class}">{web_content.body}</body>
</html>"""
        # print(html)
        self.setHtml(html)

    @classmethod
    def webBundlePath(cls, path: str) -> str:
        from aqt import mw

        if path.startswith("/"):
            subpath = ""
        else:
            subpath = "/_anki/"

        return f"http://127.0.0.1:{mw.mediaServer.getPort()}{subpath}{path}"

    def bundledScript(self, fname: str) -> str:
        return '<script src="%s"></script>' % self.webBundlePath(fname)

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

            def handler(val):
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
        while self._pendingActions and self._domDone:
            name, args = self._pendingActions.pop(0)

            if name == "eval":
                self._evalWithCallback(*args)
            elif name == "setHtml":
                self._setHtml(*args)
            else:
                raise Exception("unknown action: {}".format(name))

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
        self.evalWithCallback("$(document.body).height()", self._onHeight)

    def _onHeight(self, qvar: Optional[int]) -> None:
        from aqt import mw

        if qvar is None:

            mw.progress.timer(1000, mw.reset, False)
            return

        scaleFactor = self.zoomFactor()
        if scaleFactor == 1:
            scaleFactor = mw.pm.uiScale()

        height = math.ceil(qvar * scaleFactor)
        self.setFixedHeight(height)

    def set_bridge_command(self, func: Callable[[str], Any], context: Any) -> None:
        """Set a handler for pycmd() messages received from Javascript.

        Context is the object calling this routine, eg an instance of
        aqt.reviewer.Reviewer or aqt.deckbrowser.DeckBrowser."""
        self.onBridgeCmd = func
        self._bridge_context = context

    def hide_while_preserving_layout(self):
        "Hide but keep existing size."
        sp = self.sizePolicy()
        sp.setRetainSizeWhenHidden(True)
        self.setSizePolicy(sp)
        self.hide()

    def inject_dynamic_style_and_show(self):
        "Add dynamic styling, and reveal."
        css = self.standard_css()

        def after_style(arg):
            gui_hooks.webview_did_inject_style_into_page(self)
            self.show()

        self.evalWithCallback(
            f"""
const style = document.createElement('style');
style.innerHTML = `{css}`;
document.head.appendChild(style);
""",
            after_style,
        )

    def load_ts_page(self, name: str) -> None:
        from aqt import mw

        self.set_open_links_externally(False)
        if theme_manager.night_mode:
            extra = "#night"
        else:
            extra = ""
        self.hide_while_preserving_layout()
        self.load(QUrl(f"{mw.serverURL()}_anki/{name}.html" + extra))
        self.inject_dynamic_style_and_show()
