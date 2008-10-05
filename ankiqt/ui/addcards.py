# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import ankiqt.forms
import anki
from anki.facts import Fact
from anki.errors import *
from anki.utils import stripHTML
from ankiqt import ui

class AddCards(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.config = parent.config
        self.dialog = ankiqt.forms.addcards.Ui_AddCards()
        self.dialog.setupUi(self)
        self.setupEditor()
        self.addChooser()
        self.addButtons()
        self.setupStatus()
        self.modelChanged(self.parent.deck.currentModel)
        self.addedItems = 0
        self.show()
        ui.dialogs.open("AddCards", self)

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(self,
                                               self.dialog.fieldsArea,
                                               self.parent.deck)
        self.editor.onFactValid = self.onValid
        self.editor.onFactInvalid = self.onInvalid

    def addChooser(self):
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.parent,
                                                         self.parent.deck,
                                                         self.modelChanged)
        self.dialog.modelArea.setLayout(self.modelChooser)

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "AddFacts"))

    def addButtons(self):
        self.addButton = QPushButton(_("&Add cards"))
        self.dialog.buttonBox.addButton(self.addButton,
                                        QDialogButtonBox.ActionRole)
        self.addButton.setShortcut(_("Ctrl+Return"))
        self.addButton.setDefault(True)
        s = QShortcut(QKeySequence(_("Ctrl+Enter")), self)
        s.connect(s, SIGNAL("activated()"), self.addButton, SLOT("click()"))
        s = QShortcut(QKeySequence(_("Alt+A")), self)
        s.connect(s, SIGNAL("activated()"), self.addButton, SLOT("click()"))
        self.connect(self.addButton, SIGNAL("clicked()"), self.addCards)
        self.closeButton = QPushButton(_("Close"))
        self.dialog.buttonBox.addButton(self.closeButton,
                                        QDialogButtonBox.RejectRole)
        self.helpButton = QPushButton(_("Help"))
        self.dialog.buttonBox.addButton(self.helpButton,
                                        QDialogButtonBox.HelpRole)
        self.connect(self.helpButton, SIGNAL("clicked()"), self.helpRequested)

    def setupStatus(self):
        "Make the status background the same colour as the frame."
        p = self.dialog.statusGroup.palette()
        c = unicode(p.color(QPalette.Window).name())
        self.dialog.status.setStyleSheet(
            "* { background: %s; color: #000000; }" % c)

    def modelChanged(self, model):
        oldFact = self.editor.fact
        # create a new fact
        fact = self.parent.deck.newFact()
        fact.tags = self.parent.deck.lastTags
        # set the new fact
        self.editor.setFact(fact, check=True)

    def onValid(self, fact):
        self.addButton.setEnabled(True)

    def onInvalid(self, fact):
        self.addButton.setEnabled(False)

    def addCards(self):
        fact = self.editor.fact
        cards = self.parent.deck.addFact(fact)
        self.dialog.status.append(_("Added %(num)d card(s) for '%(str)s'.") % {
            "num": len(cards),
            # we're guaranteed that all fields will exist now
            "str": stripHTML(fact[fact.fields[0].name]),
            })
        self.parent.updateTitleBar()
        # start a new fact
        f = self.parent.deck.newFact()
        f.tags = self.parent.deck.lastTags
        self.editor.setFact(f, check=True)
        self.maybeSave()

    def closeEvent(self, evt):
        if self.onClose():
            evt.accept()
        else:
            evt.ignore()

    def reject(self):
        if self.onClose():
            QDialog.reject(self)

    def onClose(self):
        if (self.editor.fieldsAreBlank() or
            ui.utils.askUser(_("Close and lose current input?"),
                            self)):
            ui.dialogs.close("AddCards")
            self.parent.deck.s.flush()
            self.parent.moveToState("auto")
            return True
        else:
            return False

    def maybeSave(self):
        "Increment added count and maybe save deck."
        self.addedItems += 1
        if not self.parent.config['saveAfterAdding']:
            return
        if (self.addedItems % self.parent.config['saveAfterAddingNum']) == 0:
            self.parent.saveDeck()
