# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
import math
from anki.hooks import runHook
from aqt.qt import *
from aqt.utils import openLink, showWarning
from anki.utils import isMac, isWin, isLin, devMode

# Page for debug messages
##########################################################################

class AnkiWebPage(QWebEnginePage):

    def __init__(self, onBridgeCmd):
        QWebEnginePage.__init__(self)
        self._onBridgeCmd = onBridgeCmd
        self._setupBridge()
        self.setBackgroundColor(Qt.transparent)

    def _setupBridge(self):
        class Bridge(QObject):
            @pyqtSlot(str)
            def cmd(self, str):
                self.onCmd(str)

        self._bridge = Bridge()
        self._bridge.onCmd = self._onCmd

        self._channel = QWebChannel(self)
        self._channel.registerObject("py", self._bridge)
        self.setWebChannel(self._channel)

        js = QFile(':/qtwebchannel/qwebchannel.js')
        assert js.open(QIODevice.ReadOnly)
        js = bytes(js.readAll()).decode('utf-8')

        script = QWebEngineScript()
        script.setSourceCode(js + '''
            var pycmd;
            new QWebChannel(qt.webChannelTransport, function(channel) {
                pycmd = channel.objects.py.cmd;
                pycmd("domDone");
            });
        ''')
        script.setWorldId(QWebEngineScript.MainWorld)
        script.setInjectionPoint(QWebEngineScript.DocumentReady)
        script.setRunsOnSubFrames(False)
        self.profile().scripts().insert(script)

    def javaScriptConsoleMessage(self, lvl, msg, line, srcID):
        # not translated because console usually not visible,
        # and may only accept ascii text
        sys.stdout.write("JS error on line %(a)d: %(b)s" %
             dict(a=line, b=msg+"\n"))

    def acceptNavigationRequest(self, url, navType, isMainFrame):
        if not isMainFrame:
            return True
        from aqt import mw
        # ignore href=#
        if url.toString().startswith(mw.serverURL()):
            return False
        # load all other links in browser
        openLink(url)
        return False

    def _onCmd(self, str):
        self._onBridgeCmd(str)

# Main web view
##########################################################################

class AnkiWebView(QWebEngineView):

    def __init__(self, parent=None):
        QWebEngineView.__init__(self, parent=parent)
        self.title = "default"
        self._page = AnkiWebPage(self._onBridgeCmd)

        self._domDone = True
        self._pendingActions = []
        self.setPage(self._page)

        self._page.profile().setHttpCacheType(QWebEngineProfile.NoCache)
        self.resetHandlers()
        self.allowDrops = False
        QShortcut(QKeySequence("Esc"), self,
                  context=Qt.WidgetWithChildrenShortcut, activated=self.onEsc)
        if isMac:
            for key, fn in [
                (QKeySequence.Copy, self.onCopy),
                (QKeySequence.Paste, self.onPaste),
                (QKeySequence.Cut, self.onCut),
                (QKeySequence.SelectAll, self.onSelectAll),
            ]:
                QShortcut(key, self,
                          context=Qt.WidgetWithChildrenShortcut,
                          activated=fn)
            QShortcut(QKeySequence("ctrl+shift+v"), self,
                      context=Qt.WidgetWithChildrenShortcut, activated=self.onPaste)

        self.focusProxy().installEventFilter(self)

    def eventFilter(self, obj, evt):
        # disable pinch to zoom gesture
        if isinstance(evt, QNativeGestureEvent):
            return True
        return False

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

    def onSelectAll(self):
        self.triggerPageAction(QWebEnginePage.SelectAll)

    def contextMenuEvent(self, evt):
        m = QMenu(self)
        a = m.addAction(_("Copy"))
        a.triggered.connect(self.onCopy)
        runHook("AnkiWebView.contextMenuEvent", self, m)
        m.popup(QCursor.pos())

    def dropEvent(self, evt):
        pass

    def setHtml(self, html):
        self._queueAction("setHtml", html)

    def _setHtml(self, html):
        app = QApplication.instance()
        oldFocus = app.focusWidget()
        self._domDone = False
        self._page.setHtml(html)
        # work around webengine stealing focus on setHtml()
        if oldFocus:
            oldFocus.setFocus()

    def zoomFactor(self):
        # overridden scale factor?
        webscale = os.environ.get("ANKI_WEBSCALE")
        if webscale:
            return float(webscale)

        if isMac:
            return 1
        screen = QApplication.desktop().screen()
        dpi = screen.logicalDpiX()
        factor = dpi / 96.0
        if isLin:
            factor = max(1, factor)
            return factor
        # compensate for qt's integer scaling on windows
        qtIntScale = self._getQtIntScale(screen)
        desiredScale = factor * qtIntScale
        newFactor = desiredScale / qtIntScale
        return max(1, newFactor)

    def _getQtIntScale(self, screen):
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

    def stdHtml(self, body, css=[], js=["jquery.js"], head=""):
        if isWin:
            buttonspec = "button { font-size: 12px; font-family:'Segoe UI'; }"
            fontspec = 'font-size:12px;font-family:"Segoe UI";'
        elif isMac:
            family="Helvetica"
            fontspec = 'font-size:15px;font-family:"%s";'% \
                       family
            buttonspec = """
button { font-size: 13px; -webkit-appearance: none; background: #fff; border: 1px solid #ccc;
border-radius:5px; font-family: Helvetica }"""
        else:
            buttonspec = ""
            family = self.font().family()
            fontspec = 'font-size:14px;font-family:%s;'%\
                family
        csstxt = "\n".join([self.bundledCSS("webview.css")]+
                           [self.bundledCSS(fname) for fname in css])
        jstxt = "\n".join([self.bundledScript("webview.js")]+
                          [self.bundledScript(fname) for fname in js])
        from aqt import mw
        head =  mw.baseHTML() + head + csstxt + jstxt

        html=f"""
<!doctype html>
<html><head>
<title>{self.title}</title>

<style>
body {{ zoom: {self.zoomFactor()}; {fontspec} }}
{buttonspec}
</style>
  
{head}
</head>

<body>{body}</body>
</html>"""
        #print(html)
        self.setHtml(html)

    def webBundlePath(self, path):
        from aqt import mw
        return "http://localhost:%d/_anki/%s" % (mw.mediaServer.getPort(), path)

    def bundledScript(self, fname):
        return '<script src="%s"></script>' % self.webBundlePath(fname)

    def bundledCSS(self, fname):
        return '<link rel="stylesheet" type="text/css" href="%s">' % self.webBundlePath(fname)

    def eval(self, js):
        self.evalWithCallback(js, None)

    def evalWithCallback(self, js, cb):
        self._queueAction("eval", js, cb)

    def _evalWithCallback(self, js, cb):
        if cb:
            self.page().runJavaScript(js, cb)
        else:
            self.page().runJavaScript(js)

    def _queueAction(self, name, *args):
        self._pendingActions.append((name, args))
        self._maybeRunActions()

    def _maybeRunActions(self):
        while self._pendingActions and self._domDone:
            name, args = self._pendingActions.pop(0)

            if name == "eval":
                self._evalWithCallback(*args)
            elif name == "setHtml":
                self._setHtml(*args)
            else:
                raise Exception("unknown action: {}".format(name))

    def _openLinksExternally(self, url):
        openLink(url)

    def _onBridgeCmd(self, cmd):
        # ignore webchannel messages that arrive after underlying webview
        # deleted
        if sip.isdeleted(self):
            return

        if cmd == "domDone":
            self._domDone = True
            self._maybeRunActions()
        else:
            self.onBridgeCmd(cmd)

    def defaultOnBridgeCmd(self, cmd):
        print("unhandled bridge cmd:", cmd)

    def resetHandlers(self):
        self.onBridgeCmd = self.defaultOnBridgeCmd

    def adjustHeightToFit(self):
        self.evalWithCallback("$(document.body).height()", self._onHeight)

    def _onHeight(self, qvar):
        height = math.ceil(qvar*self.zoomFactor())
        self.setFixedHeight(height)
