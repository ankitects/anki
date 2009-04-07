# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import ankiqt.forms
import anki
from anki.facts import Fact
from anki.errors import *
from anki.utils import stripHTML, parseTags
from ankiqt.ui.utils import saveGeom, restoreGeom, saveSplitter, restoreSplitter
from ankiqt import ui
from anki.sound import clearAudioQueue

class FocusButton(QPushButton):
    def focusInEvent(self, evt):
        if evt.reason() == Qt.TabFocusReason:
            self.emit(SIGNAL("tabIn"))
        QPushButton.focusInEvent(self, evt)

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
        self.forceClose = False
        restoreGeom(self, "add")
        restoreSplitter(self.dialog.splitter, "add")
        self.show()
        ui.dialogs.open("AddCards", self)

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(self,
                                               self.dialog.fieldsArea,
                                               self.parent.deck)

    def addChooser(self):
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.parent,
                                                         self.parent.deck,
                                                         self.modelChanged)
        self.dialog.modelArea.setLayout(self.modelChooser)

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "AddItems"))

    def addButtons(self):
        self.addButton = FocusButton(_("Add"))
        self.dialog.buttonBox.addButton(self.addButton,
                                        QDialogButtonBox.ActionRole)
        self.addButton.setShortcut(_("Ctrl+Return"))
        if sys.platform.startswith("darwin"):
            self.addButton.setToolTip(_("Add (shortcut: command+return)"))
        else:
            self.addButton.setToolTip(_("Add (shortcut: ctrl+return)"))
        s = QShortcut(QKeySequence(_("Ctrl+Enter")), self)
        s.connect(s, SIGNAL("activated()"), self.addButton, SLOT("click()"))
        self.connect(self.addButton, SIGNAL("clicked()"), self.addCards)
        self.connect(self.addButton, SIGNAL("tabIn"), self.maybeAddCards)
        self.closeButton = QPushButton(_("Close"))
        self.closeButton.setAutoDefault(False)
        self.dialog.buttonBox.addButton(self.closeButton,
                                        QDialogButtonBox.RejectRole)
        self.helpButton = QPushButton(_("Help"))
        self.helpButton.setAutoDefault(False)
        self.dialog.buttonBox.addButton(self.helpButton,
                                        QDialogButtonBox.HelpRole)
        self.connect(self.helpButton, SIGNAL("clicked()"), self.helpRequested)

    def setupStatus(self):
        "Make the status background the same colour as the frame."
        p = self.dialog.status.palette()
        c = unicode(p.color(QPalette.Window).name())
        self.dialog.status.setStyleSheet(
            "* { background: %s; color: #000000; }" % c)

    def modelChanged(self, model):
        oldFact = self.editor.fact
        # create a new fact
        fact = self.parent.deck.newFact()
        # copy fields from old fact
        if oldFact:
            n = 0
            for field in fact.fields:
                try:
                    field.value = oldFact.fields[n].value
                except IndexError:
                    break
                n += 1
            fact.tags = oldFact.tags
        else:
            fact.tags = self.parent.deck.lastTags
        # set the new fact
        self.editor.setFact(fact, check=True)
        self.setTabOrder(self.editor.tags, self.addButton)

    def maybeAddCards(self):
        self.editor.saveFieldsNow()
        fact = self.editor.fact
        try:
            fact.assertValid()
            fact.assertUnique(self.parent.deck.s)
        except FactInvalidError:
            return
        self.addCards()

    def addCards(self):
        # make sure updated
        self.editor.saveFieldsNow()
        fact = self.editor.fact
        n = _("Add")
        self.parent.deck.setUndoStart(n)
        try:
            fact = self.parent.deck.addFact(fact)
        except FactInvalidError:
            ui.utils.showInfo(_(
                "Some fields are missing or not unique."),
                              parent=self, help="AddItems#AddError")
            return
        if not fact:
            ui.utils.showWarning(_("""\
The input you have provided would make an empty
question or answer on all cards."""), parent=self)
            return
        self.dialog.status.append(_("Added %(num)d card(s) for '%(str)s'.") % {
            "num": len(fact.cards),
            # we're guaranteed that all fields will exist now
            "str": stripHTML(fact[fact.fields[0].name]),
            })
        # stop anything playing
        clearAudioQueue()
        self.parent.deck.setUndoEnd(n)
        self.parent.updateTitleBar()
        # start a new fact
        f = self.parent.deck.newFact()
        f.tags = self.parent.deck.lastTags
        self.editor.setFact(f, check=True)
        # let completer know our extra tags
        self.editor.tags.addTags(parseTags(self.parent.deck.lastTags))
        self.maybeSave()

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if (evt.key() in (Qt.Key_Enter, Qt.Key_Return)
            and self.editor.tags.hasFocus()):
            evt.accept()
            return
        return QDialog.keyPressEvent(self, evt)

    def closeEvent(self, evt):
        if self.onClose():
            evt.accept()
        else:
            evt.ignore()

    def reject(self):
        if self.onClose():
            QDialog.reject(self)

    def onClose(self):
        # stop anything playing
        clearAudioQueue()
        if (self.forceClose or self.editor.fieldsAreBlank() or
            ui.utils.askUser(_("Close and lose current input?"),
                            self)):
            self.editor.close()
            ui.dialogs.close("AddCards")
            self.parent.deck.s.flush()
            self.parent.deck.rebuildCSS()
            self.parent.moveToState("auto")
            saveGeom(self, "add")
            saveSplitter(self.dialog.splitter, "add")
            return True
        else:
            return False

    def maybeSave(self):
        "Increment added count and maybe save deck."
        self.addedItems += 1
        if not self.parent.config['saveAfterAdding']:
            return
        if (self.addedItems % self.parent.config['saveAfterAddingNum']) == 0:
            self.parent.save()
