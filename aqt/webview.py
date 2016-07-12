# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
from anki.hooks import runHook
from aqt.qt import *
from aqt.utils import openLink
from anki.utils import isMac, isWin
import anki.js

# Page for debug messages
##########################################################################

class AnkiWebPage(QWebEnginePage):

    def __init__(self, onBridgeCmd):
        QWebEnginePage.__init__(self)
        self._onBridgeCmd = onBridgeCmd
        self._setupBridge()

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
        sys.stdout.write(
            (_("JS error on line %(a)d: %(b)s") %
             dict(a=line, b=msg+"\n")))

    def acceptNavigationRequest(self, url, navType, isMainFrame):
        # load all other links in browser
        openLink(url)
        return False

    def _onCmd(self, str):
        self._onBridgeCmd(str)

# Main web view
##########################################################################

class AnkiWebView(QWebEngineView):

    def __init__(self, canFocus=True):
        QWebEngineView.__init__(self)
        self.title = "default"
        self._page = AnkiWebPage(self._onBridgeCmd)

        self._loadFinishedCB = None
        self.setPage(self._page)
        self.resetHandlers()
        self.allowDrops = False
        self.setCanFocus(canFocus)
        self.installEventFilter(self)

    def eventFilter(self, obj, evt):
        if not isinstance(evt, QKeyEvent) or obj != self:
            return False
        if evt.matches(QKeySequence.Copy) and isMac:
            self.onCopy()
            return True
        if evt.matches(QKeySequence.Cut) and isMac:
            self.onCut()
            return True
        if evt.matches(QKeySequence.Paste) and isMac:
            self.onPaste()
            return True
        if evt.key() == Qt.Key_Escape:
            # cheap hack to work around webengine swallowing escape key that
            # usually closes dialogs
            w = self.parent()
            while w:
                if isinstance(w, QDialog) or isinstance(w, QMainWindow):
                    from aqt import mw
                    if w != mw:
                        w.close()
                    break
                w = w.parent()
            return True
        return False

    def onCopy(self):
        self.triggerPageAction(QWebEnginePage.Copy)

    def onCut(self):
        self.triggerPageAction(QWebEnginePage.Cut)

    def onPaste(self):
        self.triggerPageAction(QWebEnginePage.Paste)

    def contextMenuEvent(self, evt):
        if not self._canFocus:
            return
        m = QMenu(self)
        a = m.addAction(_("Copy"))
        a.triggered.connect(lambda: self.triggerPageAction(QWebEnginePage.Copy))
        runHook("AnkiWebView.contextMenuEvent", self, m)
        m.popup(QCursor.pos())

    def dropEvent(self, evt):
        pass

    def setHtml(self, html):
        app = QApplication.instance()
        oldFocus = app.focusWidget()
        self._page.setHtml(html)
        # work around webengine stealing focus on setHtml()
        if oldFocus:
            oldFocus.setFocus()

    def zoomFactor(self):
        screen = QApplication.desktop().screen()
        dpi = screen.logicalDpiX()
        return max(1, dpi / 96.0)

    def stdHtml(self, body, css="", bodyClass="", js=None, head=""):
        buttonspec = ""
        if isWin:
            fontspec = 'font-size:12px;font-family:"Segoe UI";'
        elif isMac:
            family=".AppleSystemUIFont"
            fontspec = 'font-size:16px;font-family:"%s";'% \
                       family
            buttonspec = """
button { font-size: 14px; -webkit-appearance: none; background: #fff; border: 1px solid #ccc;
border-radius:5px;}"""
        else:
            family = self.font().family()
            fontspec = 'font-size:14px;font-family:%s;'%\
                family

        self.setHtml("""
<!doctype html>
<html><head><title>%s</title><style>
body { zoom: %f; %s }
%s
%s</style>
<script>
%s
</script>
%s

</head>
<body class="%s">%s</body></html>""" % (
            self.title,
            self.zoomFactor(),
            fontspec,
            buttonspec,
            css, js or anki.js.jquery+anki.js.browserSel,
    head, bodyClass, body))

    def setCanFocus(self, canFocus=False):
        self._canFocus = canFocus
        if self._canFocus:
            self.setFocusPolicy(Qt.WheelFocus)
        else:
            self.setFocusPolicy(Qt.NoFocus)

    def eval(self, js):
        self.page().runJavaScript(js)

    def evalWithCallback(self, js, cb):
        self.page().runJavaScript(js, cb)

    def _openLinksExternally(self, url):
        openLink(url)

    def _onBridgeCmd(self, cmd):
        if cmd == "domDone":
            self.onLoadFinished()
        else:
            self.onBridgeCmd(cmd)

    def defaultOnBridgeCmd(self, cmd):
        print("unhandled bridge cmd:", cmd)

    def defaultOnLoadFinished(self):
        pass

    def resetHandlers(self):
        self.onBridgeCmd = self.defaultOnBridgeCmd
        self.onLoadFinished = self.defaultOnLoadFinished
