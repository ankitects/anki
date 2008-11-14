# Copyright: Richard Colley <richard.colley@rcolley.com>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4 import QtGui, QtCore
Qt = QtCore.Qt

class AnkiTrayIcon(QtCore.QObject):
    """
    Enable minimize to tray
    """

    def __init__(self, mw):
        QtCore.QObject.__init__(self, mw)
        self.mw = mw
        self.anki_visible = True
        if (QtGui.QSystemTrayIcon.isSystemTrayAvailable() and
                    mw.config['showTrayIcon']):
            self.ti = QtGui.QSystemTrayIcon(mw)
            if self.ti:
                self.mw.addHook("quit", self.onQuit)
                self.ti.setIcon(QtGui.QIcon(":/icons/anki.png"))
                self.ti.setToolTip("Anki")
                # hook signls, and Anki state changes
                mw.addView(self)
                mw.connect(self.ti, QtCore.SIGNAL("activated(QSystemTrayIcon::ActivationReason)"),lambda i: self.activated(i))
                mw.connect(self.ti, QtCore.SIGNAL("messageClicked()"), lambda : self.messageClicked())
                self.ti.show()

    def showMain(self):
        self.mw.showNormal()
        self.mw.activateWindow()
        self.mw.raise_()
        self.anki_visible = True
        self.updateTooltip()

    def hideMain(self):
        self.mw.hide()
        self.anki_visible = False
        self.updateTooltip()

    def activated(self, reason):
        if self.anki_visible:
            self.hideMain()
        else:
            self.showMain()

    def messageClicked(self):
        if not self.anki_visible:
            self.showMain()

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
        msg = name + ":<br>"
        if state == "deckFinished":
            msg += _("<b>Today's reviews are finished</b><br>")
        elif self.mw.deck:
            msg += _("<b>Cards are waiting</b><br>")
        if self.anki_visible:
            msg += _("Click to hide Anki")
        else:
            msg += _("Click to show Anki")
        self.setToolTip(msg)

    def onQuit(self):
        self.ti.deleteLater()
