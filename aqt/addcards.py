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
from aqt.utils import saveGeom, restoreGeom, showWarning, askUser
from anki.sound import clearAudioQueue
from anki.hooks import addHook, removeHook
import aqt.editor, aqt.modelchooser

# todo:
        # if field.fieldModel.features:
        #     w.setLayoutDirection(Qt.RightToLeft)
        # else:
        #     w.setLayoutDirection(Qt.LeftToRight)

class AddCards(QDialog):

    def __init__(self, mw):
        windParent = None
        QDialog.__init__(self, windParent, Qt.Window)
        self.mw = mw
        self.form = aqt.forms.addcards.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Add"))
        self.setupEditor()
        self.setupChooser()
        self.setupButtons()
        self.onReset()
        self.addedItems = 0
        self.forceClose = False
        restoreGeom(self, "add")
        addHook('reset', self.onReset)
        self.setupNewFact()
        self.show()

    def setupEditor(self):
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea)
        # get a fact for testing
        #fact = self.mw.deck.getFact(3951)
        #self.editor.setFact(fact)

    def setupChooser(self):
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.form.modelArea)
        # modelChanged func

    def helpRequested(self):
        aqt.openHelp("AddItems")

    def setupButtons(self):
        self.addButton = QPushButton(_("Add"))
        self.form.buttonBox.addButton(self.addButton,
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
        self.form.buttonBox.addButton(self.closeButton,
                                        QDialogButtonBox.RejectRole)
        self.helpButton = QPushButton(_("Help"))
        self.helpButton.setAutoDefault(False)
        self.form.buttonBox.addButton(self.helpButton,
                                        QDialogButtonBox.HelpRole)
        self.connect(self.helpButton, SIGNAL("clicked()"), self.helpRequested)

    def onLink(self, url):
        browser = ui.dialogs.open("CardList", self.mw)
        browser.dialog.filterEdit.setText("fid:" + url.toString())
        browser.updateSearch()
        browser.onFact()

    # FIXME: need to make sure to clean up fact on exit
    def setupNewFact(self, set=True):
        f = self.mw.deck.newFact()
        if self.editor.fact:
            # keep tags?
            f.tags = self.editor.fact.tags
        if set:
            self.editor.setFact(f)
        return f

    def onReset(self, model=None):
        oldFact = self.editor.fact
        fact = self.setupNewFact(set=False)
        # copy fields from old fact
        if oldFact:
            self.removeTempFact(oldFact)
            for n in range(len(fact._fields)):
                try:
                    fact._fields[n] = oldFact._fields[n]
                except IndexError:
                    break
        self.editor.setFact(fact)
        #self.setTabOrder(self.editor.tags, self.addButton)
        #self.setTabOrder(self.addButton, self.closeButton)
        #self.setTabOrder(self.closeButton, self.helpButton)

    def removeTempFact(self, fact):
        if not fact:
            return
        # we don't have to worry about cards; just the fact
        self.mw.deck._delFacts([fact.id])

    def reportAddedFact(self, fact):
        return
        self.form.status.append(
            _("Added %(num)d card(s) for <a href=\"%(id)d\">"
              "%(str)s</a>.") % {
            "num": len(fact.cards),
            "id": fact.id,
            # we're guaranteed that all fields will exist now
            "str": stripHTML(fact[fact.fields[0].name]),
            })

    def addFact(self, fact):
        if any(fact.problems()):
            showWarning(_(
                "Some fields are missing or not unique."),
                     help="AddItems#AddError")
            return
        cards = self.mw.deck.addFact(fact)
        if not cards:
            showWarning(_("""\
The input you have provided would make an empty
question or answer on all cards."""), help="AddItems")
            return
        self.reportAddedFact(fact)
        # FIXME: return to overview on add?
        return fact

    def addCards(self):
        self.editor.saveNow()
        fact = self.editor.fact
        fact = self.addFact(fact)
        if not fact:
            return
        # stop anything playing
        clearAudioQueue()
        self.setupNewFact()
        self.mw.deck.autosave()

    def keyPressEvent(self, evt):
        "Show answer on RET or register answer."
        if (evt.key() in (Qt.Key_Enter, Qt.Key_Return)
            and self.editor.tags.hasFocus()):
            evt.accept()
            return
        return QDialog.keyPressEvent(self, evt)

    def reject(self):
        if not self.canClose():
            return
        clearAudioQueue()
        self.removeTempFact(self.editor.fact)
        self.editor.setFact(None)
        self.modelChooser.cleanup()
        removeHook('reset', self.onReset)
        saveGeom(self, "add")
        aqt.dialogs.close("AddCards")
        QDialog.reject(self)

    def canClose(self):
        if (self.forceClose or self.editor.fieldsAreBlank() or
            askUser(_("Close and lose current input?"))):
            return True
        return False
