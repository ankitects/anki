# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
from aqt.qt import *
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
        print "link", str
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
        self._curKey = None
        self.allowDrops = False
    def keyReleaseEvent(self, evt):
        if self._keyHandler:
            if self._keyHandler(evt):
                evt.accept()
                return
        QWebView.keyPressEvent(self, evt)
    def contextMenuEvent(self, evt):
        QWebView.contextMenuEvent(self, evt)
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
        if loadCB:
            self._loadFinishedCB = loadCB
        QWebView.setHtml(self, html)
    def stdHtml(self, body, css="", bodyID="", loadCB=None, head=""):
        self.setHtml("""
<html><head><style>%s</style>
<script>%s</script>
%s
</head>
<body id="%s">%s</body></html>""" % (
    css, anki.js.all, head, bodyID, body), loadCB)
    def setBridge(self, bridge):
        self._bridge.setBridge(bridge)
    def eval(self, js):
        self.page().mainFrame().evaluateJavaScript(js)
    def _openLinksExternally(self, url):
        QDesktopServices.openUrl(QUrl(url))
    def _jsErr(self, msg, line, srcID):
        sys.stderr.write(_("JS error on line %d: %s") % (line, msg+"\n"))
    def _linkHandler(self, url):
        self.linkHandler(url.toString())
    def _loadFinished(self):
        self.page().mainFrame().addToJavaScriptWindowObject("py", self._bridge)
        if self._loadFinishedCB:
            self._loadFinishedCB(self)
            self._loadFinishedCB = None
