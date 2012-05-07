# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
from aqt.qt import *
from aqt.utils import fontForPlatform, openLink
from anki.utils import isMac, isWin
import anki.js
QtConfig = pyqtconfig.Configuration()

# Bridge for Qt<->JS
##########################################################################

class Bridge(QObject):
    @pyqtSlot(str, result=str)
    def run(self, str):
        return unicode(self._bridge(unicode(str)))
    @pyqtSlot(str)
    def link(self, str):
        self._linkHandler(unicode(str))
    def setBridge(self, func):
        self._bridge = func
    def setLinkHandler(self, func):
        self._linkHandler = func

# Page for debug messages
##########################################################################

class AnkiWebPage(QWebPage):

    def __init__(self, jsErr):
        QWebPage.__init__(self)
        self._jsErr = jsErr
    def javaScriptConsoleMessage(self, msg, line, srcID):
        self._jsErr(msg, line, srcID)

# Main web view
##########################################################################

class AnkiWebView(QWebView):

    def __init__(self):
        QWebView.__init__(self)
        self.setObjectName("mainText")
        self._bridge = Bridge()
        self._page = AnkiWebPage(self._jsErr)
        self._loadFinishedCB = None
        self.setPage(self._page)
        self.page().setLinkDelegationPolicy(QWebPage.DelegateAllLinks)
        self.setLinkHandler()
        self.setKeyHandler()
        self.connect(self, SIGNAL("linkClicked(QUrl)"), self._linkHandler)
        self.connect(self, SIGNAL("loadFinished(bool)"), self._loadFinished)
        self.allowDrops = False

    def keyPressEvent(self, evt):
        if evt.matches(QKeySequence.Copy):
            self.triggerPageAction(QWebPage.Copy)
            evt.accept()
        # work around a bug with windows qt where shift triggers buttons
        if isWin and evt.modifiers() == Qt.ShiftModifier and not evt.text():
            evt.accept()
            return
        QWebView.keyPressEvent(self, evt)

    def keyReleaseEvent(self, evt):
        if self._keyHandler:
            if self._keyHandler(evt):
                evt.accept()
                return
        QWebView.keyPressEvent(self, evt)

    def contextMenuEvent(self, evt):
        # lazy: only run in reviewer
        import aqt
        if aqt.mw.state != "review":
            return
        m = QMenu(self)
        a = m.addAction(_("Copy"))
        a.connect(a, SIGNAL("activated()"),
                  lambda: self.triggerPageAction(QWebPage.Copy))
        m.popup(QCursor.pos())

    def dropEvent(self, evt):
        pass

    def setLinkHandler(self, handler=None):
        if handler:
            self.linkHandler = handler
        else:
            self.linkHandler = self._openLinksExternally
        self._bridge.setLinkHandler(self.linkHandler)

    def setKeyHandler(self, handler=None):
        # handler should return true if event should be swallowed
        self._keyHandler = handler

    def setHtml(self, html, loadCB=None):
        self._loadFinishedCB = loadCB
        QWebView.setHtml(self, html)

    def stdHtml(self, body, css="", bodyClass="", loadCB=None, js=None, head=""):
        if isMac:
            button = "font-weight: bold; height: 24px;"
        else:
            button = "font-weight: normal; font-size: 12px;"
        self.setHtml("""
<html><head><style>
body { font-family: "%s"; }
button {
%s
}
%s</style>
<script>%s</script>
%s

</head>
<body class="%s">%s</body></html>""" % (
    fontForPlatform(), button, css, js or anki.js.jquery+anki.js.browserSel,
    head, bodyClass, body), loadCB)

    def setBridge(self, bridge):
        self._bridge.setBridge(bridge)

    def eval(self, js):
        self.page().mainFrame().evaluateJavaScript(js)

    def _openLinksExternally(self, url):
        openLink(url)

    def _jsErr(self, msg, line, srcID):
        sys.stdout.write(
            _("JS error on line %(a)d: %(b)s") % dict(a=line, b=msg+"\n"))

    def _linkHandler(self, url):
        self.linkHandler(url.toString())

    def _loadFinished(self):
        self.page().mainFrame().addToJavaScriptWindowObject("py", self._bridge)
        if self._loadFinishedCB:
            self._loadFinishedCB(self)
            self._loadFinishedCB = None
