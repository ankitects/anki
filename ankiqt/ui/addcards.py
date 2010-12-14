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
from anki.hooks import addHook, removeHook

class FocusButton(QPushButton):
    def focusInEvent(self, evt):
        if evt.reason() == Qt.TabFocusReason:
            self.emit(SIGNAL("tabIn"))
        QPushButton.focusInEvent(self, evt)

class AddCards(QDialog):

    def __init__(self, parent):
        if parent.config['standaloneWindows']:
            windParent = None
        else:
            windParent = parent
        QDialog.__init__(self, windParent, Qt.Window)
        self.parent = parent
        ui.utils.applyStyles(self)
        self.config = parent.config
        self.dialog = ankiqt.forms.addcards.Ui_AddCards()
        self.dialog.setupUi(self)
        self.setWindowTitle(_("Add Items - %s") % parent.deck.name())
        self.setupEditor()
        self.addChooser()
        self.addButtons()
        self.setupStatus()
        self.modelChanged()
        self.addedItems = 0
        self.forceClose = False
        restoreGeom(self, "add")
        restoreSplitter(self.dialog.splitter, "add")
        self.dialog.splitter.setChildrenCollapsible(False)
        self.show()
        addHook('guiReset', self.modelChanged)
        ui.dialogs.open("AddCards", self)

    def setupEditor(self):
        self.editor = ui.facteditor.FactEditor(self,
                                               self.dialog.fieldsArea,
                                               self.parent.deck)
        self.editor.addMode = True
        self.editor.resetOnEdit = False

    def addChooser(self):
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.parent,
                                                         self.parent.deck,
                                                         self.modelChanged)
        self.dialog.modelArea.setLayout(self.modelChooser)

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "AddItems"))

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

    def setupStatus(self):
        "Make the status background the same colour as the frame."
        if not sys.platform.startswith("darwin"):
            p = self.dialog.status.palette()
            c = unicode(p.color(QPalette.Window).name())
            self.dialog.status.setStyleSheet(
                "* { background: %s; }" % c)
        self.connect(self.dialog.status, SIGNAL("anchorClicked(QUrl)"),
                     self.onLink)

    def onLink(self, url):
        browser = ui.dialogs.get("CardList", self.parent)
        browser.dialog.filterEdit.setText("fid:" + url.toString())
        browser.updateSearch()
        browser.onFact()

    def modelChanged(self, model=None):
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
            fact = self.parent.deck.addFact(fact, False)
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
        f = self.parent.deck.newFact()
        f.tags = self.parent.deck.lastTags
        return f

    def clearOldFact(self, old_fact):
        f = self.initializeNewFact(old_fact)
        self.editor.setFact(f, check=True, scroll=True)
        # let completer know our extra tags
        self.editor.tags.addTags(parseTags(self.parent.deck.lastTags))
        return f

    def addCards(self):
        # make sure updated
        self.editor.saveFieldsNow()
        fact = self.editor.fact
        n = _("Add")
        self.parent.deck.setUndoStart(n)

        fact = self.addFact(fact)
        if not fact:
            return

        # stop anything playing
        clearAudioQueue()

        self.parent.deck.setUndoEnd(n)
        self.parent.deck.rebuildCounts()
        self.parent.updateTitleBar()
        self.parent.statusView.redraw()

        # start a new fact
        self.clearOldFact(fact)

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
            self.modelChooser.deinit()
            QDialog.reject(self)

    def onClose(self):
        removeHook('guiReset', self.modelChanged)
        # stop anything playing
        clearAudioQueue()
        if (self.forceClose or self.editor.fieldsAreBlank() or
            ui.utils.askUser(_("Close and lose current input?"),
                            self)):
            self.modelChooser.deinit()
            self.editor.close()
            ui.dialogs.close("AddCards")
            self.parent.deck.s.flush()
            self.parent.deck.rebuildCSS()
            self.parent.reset()
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
