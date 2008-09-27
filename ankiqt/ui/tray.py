# Copyright: Richard Colley <richard.colley@rcolley.com>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4 import QtGui, QtCore
Qt = QtCore.Qt

class AnkiTrayIcon( QtCore.QObject ):
	"""
	Enable minimize to tray
	"""

	def __init__( self, mw ):
		QtCore.QObject.__init__( self, mw )
		self.mw = mw
		self.anki_visible = True
		if (QtGui.QSystemTrayIcon.isSystemTrayAvailable() and
                    mw.config['showTray']):
			self.ti = QtGui.QSystemTrayIcon( mw )
			if self.ti:
                                self.mw.addHook("quit", self.onQuit)
				self.ti.setIcon( QtGui.QIcon(":/icons/anki.png") )
				self.ti.setToolTip( "Anki" )

				# hook signls, and Anki state changes
				mw.addView( self )
				mw.connect(self.ti, QtCore.SIGNAL("activated(QSystemTrayIcon::ActivationReason)"), lambda i: self.activated(i))
				mw.connect(self.ti, QtCore.SIGNAL("messageClicked()"), lambda : self.messageClicked())
                                self.ti.show()

	def showMain( self ):
		self.mw.showNormal()
		self.mw.activateWindow()
		self.mw.raise_()
		self.anki_visible = True

	def hideMain( self ):
		self.mw.hide()
		self.anki_visible = False

	def activated( self, reason ):
		if self.anki_visible:
			self.hideMain()
		else:
			self.showMain()

	def messageClicked( self ):
		if not self.anki_visible:
			self.showMain()

	def setToolTip( self, message ):
		self.ti.setToolTip( message )

	def showMessage( self, message ):
		if self.ti.supportsMessages():
			self.ti.showMessage( "Anki", message )

	def setState( self, state ):
		if state == "showQuestion":
			if not self.anki_visible:
				self.showMessage( "A new card is available for review, click this message to display Anki" )
			self.setToolTip( "Anki - displaying question" )
		elif state == "showAnswer":
			self.setToolTip( "Anki - displaying answer" )
		elif state == "noDeck":
			self.setToolTip( "Anki - no deck" )
		elif state == "deckFinished":
			if self.mw and self.mw.deck:
				self.setToolTip( "Anki - next card in " + self.mw.deck.earliestTimeStr() )
		else:
			self.setToolTip( "Anki" )

        def onQuit(self):
                self.ti.deleteLater()
