# Copyright: Richard Colley <richard.colley@rcolley.com>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4 import QtGui, QtCore
from anki.hooks import addHook
Qt = QtCore.Qt

class AnkiTrayIcon(QtCore.QObject):
    """
    Enable minimize to tray
    """

    def __init__(self, mw):
        QtCore.QObject.__init__(self, mw)
        self.mw = mw
        self.anki_visible = True
        self.tray_hidden = []
        self.last_focus = None
        if (QtGui.QSystemTrayIcon.isSystemTrayAvailable() and
                    mw.config['showTrayIcon']):
            self.ti = QtGui.QSystemTrayIcon(mw)
            self.ti.setObjectName("trayIcon")
            if self.ti:
                QtGui.QApplication.setQuitOnLastWindowClosed(False)
                addHook("quit", self.onQuit)
                self.ti.setIcon(QtGui.QIcon(":/icons/anki.png"))
                self.ti.setToolTip("Anki")
                # hook signls, and Anki state changes
                mw.addView(self)
                mw.connect(self.ti, QtCore.SIGNAL("activated(QSystemTrayIcon::ActivationReason)"),lambda i: self.activated(i))
                mw.connect(self.ti, QtCore.SIGNAL("messageClicked()"), lambda : self.messageClicked())
                mw.connect(self.mw.app, QtCore.SIGNAL("focusChanged(QWidget*,QWidget*)"), self.focusChanged)
                self.ti.show()

    def showAll(self):
        for w in self.tray_hidden:
            if w.isWindow() and w.isHidden():
                w.showNormal()
        active = self.last_focus or self.mw
        active.raise_()
        active.activateWindow()
        self.anki_visible = True
        self.tray_hidden = []
        self.updateTooltip()

    def hideAll(self):
        self.tray_hidden = []
        activeWindow = QtGui.QApplication.activeModalWidget()
        for w in QtGui.QApplication.topLevelWidgets():
            if w.isWindow() and not w.isHidden():
                if not w.children():
                    continue
                w.hide()
                self.tray_hidden.append(w)
        self.anki_visible = False
        self.updateTooltip()

    def activated(self, reason):
        if self.anki_visible:
            self.hideAll()
        else:
            self.showAll()

    def messageClicked(self):
        if not self.anki_visible:
            self.showAll()

    def focusChanged(self, old, now):
        if now == None:
            self.last_focus = old

    def setToolTip(self, message):
        self.ti.setToolTip(message)

    def showMessage(self, message):
        if self.ti.supportsMessages():
            self.ti.showMessage("Anki", message)

    def setState(self, state):
        self.state = state
        self.updateTooltip()

    def updateTooltip(self):
        state = self.state
        if self.mw.deck:
            name = self.mw.deck.name()
        else:
            name = "Anki"
        msg = name + ":\n"
        if state == "deckFinished":
            msg += _("Today's reviews are finished")
        elif self.mw.deck:
            msg += _("Cards are waiting")
        msg += "\n\n"
        if self.anki_visible:
            msg += _("Click to hide Anki")
        else:
            msg += _("Click to show Anki")
        self.setToolTip(msg)

    def onQuit(self):
        self.ti.deleteLater()
