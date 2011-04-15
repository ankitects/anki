# Copyright: Damien Elmes <anki@ichi2.net>
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

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
from anki.utils import stripHTMLMedia
import aqt.editor, aqt.modelchooser

class AddCards(QDialog):

    def __init__(self, mw):
        windParent = None
        QDialog.__init__(self, windParent, Qt.Window)
        self.mw = mw
        self.form = aqt.forms.addcards.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Add"))
        self.setupChooser()
        self.setupEditor()
        self.setupButtons()
        self.onReset()
        self.history = []
        self.forceClose = False
        restoreGeom(self, "add")
        addHook('reset', self.onReset)
        self.setupNewFact()
        self.show()

    def setupEditor(self):
        self.editor = aqt.editor.Editor(self.mw, self.form.fieldsArea)

    def setupChooser(self):
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.form.modelArea)

    def helpRequested(self):
        aqt.openHelp("AddItems")

    def setupButtons(self):
        # add
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
        # close
        self.closeButton = QPushButton(_("Close"))
        self.closeButton.setAutoDefault(False)
        self.form.buttonBox.addButton(self.closeButton,
                                        QDialogButtonBox.RejectRole)
        # help
        self.helpButton = QPushButton(_("Help"))
        self.helpButton.setAutoDefault(False)
        self.form.buttonBox.addButton(self.helpButton,
                                        QDialogButtonBox.HelpRole)
        self.connect(self.helpButton, SIGNAL("clicked()"), self.helpRequested)
        # history
        b = self.form.buttonBox.addButton(
            _("History")+ u'▼', QDialogButtonBox.ActionRole)
        self.connect(b, SIGNAL("clicked()"), self.onHistory)
        b.setEnabled(False)
        self.historyButton = b

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
            for n in range(len(fact.fields)):
                try:
                    fact.fields[n] = oldFact.fields[n]
                except IndexError:
                    break
        self.editor.setFact(fact)

    def removeTempFact(self, fact):
        if not fact or not fact.id:
            return
        # we don't have to worry about cards; just the fact
        self.mw.deck._delFacts([fact.id])

    def addHistory(self, fact):
        txt = stripHTMLMedia(",".join(fact.fields))[:30]
        self.history.append((fact.id, txt))
        self.history = self.history[-15:]
        self.historyButton.setEnabled(True)

    def onHistory(self):
        m = QMenu(self)
        for fid, txt in self.history:
            a = m.addAction(_("Edit %s" % txt))
            a.connect(a, SIGNAL("activated()"),
                      lambda fid=fid: self.editHistory(fid))
        m.exec_(self.historyButton.mapToGlobal(QPoint(0,0)))

    def editHistory(self, fid):
        browser = aqt.dialogs.open("Browser", self.mw)
        browser.form.searchEdit.setText("fid:%d" % fid)
        browser.onSearch()

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
        self.addHistory(fact)
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
        self.mw.requireReset()
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
        self.mw.maybeReset()
        removeHook('reset', self.onReset)
        saveGeom(self, "add")
        aqt.dialogs.close("AddCards")
        QDialog.reject(self)

    def canClose(self):
        if (self.forceClose or self.editor.fieldsAreBlank() or
            askUser(_("Close and lose current input?"))):
            return True
        return False
