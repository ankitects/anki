# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import aqt.forms
import anki
from anki.facts import Fact
from anki.errors import *
from anki.utils import stripHTML, parseTags
from aqt.utils import saveGeom, restoreGeom
from anki.sound import clearAudioQueue
from anki.hooks import addHook, removeHook
import aqt.editor, aqt.modelchooser

class FocusButton(QPushButton):
    def focusInEvent(self, evt):
        if evt.reason() == Qt.TabFocusReason:
            self.emit(SIGNAL("tabIn"))
        QPushButton.focusInEvent(self, evt)

class AddCards(QDialog):

    def __init__(self, mw):
        windParent = None
        QDialog.__init__(self, windParent, Qt.Window)
        self.mw = mw
        self.dialog = aqt.forms.addcards.Ui_Dialog()
        self.dialog.setupUi(self)
        self.setWindowTitle(_("Add"))
        self.setupEditor()
        self.addChooser()
        self.addButtons()
        self.modelChanged()
        self.addedItems = 0
        self.forceClose = False
        restoreGeom(self, "add")
        self.show()
        addHook('guiReset', self.modelChanged)

    def setupEditor(self):
        self.editor = aqt.editor.Editor(self.mw, self.dialog.fieldsArea)
        # get a fact for testing
        fact = self.mw.deck.getFact(3951)
        self.editor.setFact(fact)

    def addChooser(self):
        return
        self.modelChooser = aqt.modelchooser.ModelChooser(self,
                                                         self.mw,
                                                         self.mw.deck,
                                                         self.modelChanged)
        self.dialog.modelArea.setLayout(self.modelChooser)

    def helpRequested(self):
        aqt.openHelp("AddItems")

    def addButtons(self):
        self.addButton = QPushButton(_("Add"))
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
        self.closeButton = QPushButton(_("Close"))
        self.closeButton.setAutoDefault(False)
        self.dialog.buttonBox.addButton(self.closeButton,
                                        QDialogButtonBox.RejectRole)
        self.helpButton = QPushButton(_("Help"))
        self.helpButton.setAutoDefault(False)
        self.dialog.buttonBox.addButton(self.helpButton,
                                        QDialogButtonBox.HelpRole)
        self.connect(self.helpButton, SIGNAL("clicked()"), self.helpRequested)

    def onLink(self, url):
        browser = ui.dialogs.open("CardList", self.mw)
        browser.dialog.filterEdit.setText("fid:" + url.toString())
        browser.updateSearch()
        browser.onFact()

    def modelChanged(self, model=None):
        return
        oldFact = self.editor.fact
        # create a new fact
        fact = self.mw.deck.newFact()
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
            fact.tags = "last" #self.mw.deck.lastTags
        # set the new fact
        self.editor.setFact(fact, check=True, forceRedraw=True)
        self.setTabOrder(self.editor.tags, self.addButton)
        self.setTabOrder(self.addButton, self.closeButton)
        self.setTabOrder(self.closeButton, self.helpButton)

    def reportAddedFact(self, fact):
        self.dialog.status.append(
            _("Added %(num)d card(s) for <a href=\"%(id)d\">"
              "%(str)s</a>.") % {
            "num": len(fact.cards),
            "id": fact.id,
            # we're guaranteed that all fields will exist now
            "str": stripHTML(fact[fact.fields[0].name]),
            })

    def addFact(self, fact):
        try:
            fact = self.mw.deck.addFact(fact, False)
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

        self.reportAddedFact(fact)
        # we don't reset() until the add cards dialog is closed
        return fact

    def initializeNewFact(self, old_fact):
        f = self.mw.deck.newFact()
        f.tags = self.mw.deck.lastTags
        return f

    def clearOldFact(self, old_fact):
        f = self.initializeNewFact(old_fact)
        self.editor.setFact(f, check=True, scroll=True)
        # let completer know our extra tags
        self.editor.tags.addTags(parseTags(self.mw.deck.lastTags))
        return f

    def addCards(self):
        # make sure updated
        self.editor.saveFieldsNow()
        fact = self.editor.fact
        n = _("Add")
        self.mw.deck.setUndoStart(n)

        fact = self.addFact(fact)
        if not fact:
            return

        # stop anything playing
        clearAudioQueue()

        self.mw.deck.setUndoEnd(n)
        self.mw.deck.rebuildCounts()
        self.mw.updateTitleBar()

        # start a new fact
        self.clearOldFact(fact)

        self.mw.autosave()

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
        # remove dupe
        aqt.dialogs.close("AddCards")
        QDialog.reject(self)


        if self.onClose():
            self.modelChooser.deinit()
            QDialog.reject(self)

    def onClose(self):
        return
        removeHook('guiReset', self.modelChanged)
        # stop anything playing
        clearAudioQueue()
        if (self.forceClose or self.editor.fieldsAreBlank() or
            ui.utils.askUser(_("Close and lose current input?"),
                            self)):
            self.modelChooser.deinit()
            self.editor.close()
            ui.dialogs.close("AddCards")
            self.mw.deck.db.flush()
            self.mw.deck.rebuildCSS()
            self.mw.reset()
            saveGeom(self, "add")
            return True
        else:
            return False
