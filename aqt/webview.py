# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
from anki.hooks import runHook
from aqt.qt import *
from aqt.utils import openLink
from anki.utils import isMac, isWin, devMode

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
        self._pendingJS = []
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
        if not self._domDone or self._pendingJS:
            # defer update until previous page has initialized
            if devMode:
                print("deferring setHtml() until page is ready")
            from aqt import mw
            mw.progress.timer(25, lambda: self.setHtml(html), False)
            return
        app = QApplication.instance()
        oldFocus = app.focusWidget()
        self._domDone = False
        self._pendingJS = []
        self._page.setHtml(html)
        # work around webengine stealing focus on setHtml()
        if oldFocus:
            oldFocus.setFocus()

    def zoomFactor(self):
        screen = QApplication.desktop().screen()
        dpi = screen.logicalDpiX()
        return max(1, dpi / 96.0)

    def stdHtml(self, body, css="", bodyClass="", js=["jquery.js"], head=""):
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
        jstxt = "\n".join([self.bundledScript("webview.js")]+
                          [self.bundledScript(fname) for fname in js])

        html="""
<!doctype html>
<html><head><title>%s</title><style>
body { zoom: %f; %s }
%s
%s</style>
%s
%s

</head>
<body class="%s">%s</body></html>""" % (
            self.title,
            self.zoomFactor(),
            fontspec,
            buttonspec,
            css, jstxt,
    head, bodyClass, body)
        #print(html)
        self.setHtml(html)

    def webBundlePath(self, path):
        from aqt import mw
        return "http://localhost:%d/_anki/%s" % (mw.mediaServer.port, path)

    def bundledScript(self, fname):
        return '<script src="%s"></script>' % self.webBundlePath(fname)

    def bundledCSS(self, fname):
        return '<link rel="stylesheet" type="text/css" href="%s">' % self.webBundlePath(fname)

    def eval(self, js):
        self.evalWithCallback(js, None)

    def evalWithCallback(self, js, cb):
        if self._domDone:
            # If the AnkiWebView object was deleted (i.e. the window was
            # closed before the DOM was ready) a RuntimeError is raised -
            # hide this from the user as it doesn't cause any problems
            try:
                if cb:
                    self.page().runJavaScript(js, cb)
                else:
                    self.page().runJavaScript(js)
            except RuntimeError:
                pass
        else:
            self._pendingJS.append([js, cb])

    def _openLinksExternally(self, url):
        openLink(url)

    def _onBridgeCmd(self, cmd):
        # ignore webchannel messages that arrive after underlying webview
        # deleted
        if sip.isdeleted(self):
            return

        if cmd == "domDone":
            self._domDone = True
            # run through any pending js calls
            for js, cb in self._pendingJS:
                self.evalWithCallback(js, cb)
            self._pendingJS = []
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

    def adjustHeightToFit(self):
        self.evalWithCallback("$(document.body).height()", self._onHeight)

    def _onHeight(self, qvar):
        height = int(qvar*self.zoomFactor())
        self.setFixedHeight(height)
