# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from aqt.utils import showInfo, askUser
import aqt.modelchooser, aqt.clayout

class Models(QDialog):
    def __init__(self, mw, parent=None):
        self.mw = mw
        self.parent = parent or mw
        QDialog.__init__(self, self.parent, Qt.Window)
        self.deck = mw.deck
        self.deck.save(_("Models"))
        #self.m = model
        self.form = aqt.forms.models.Ui_Dialog()
        self.form.setupUi(self)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     lambda: openHelp("Models"))
        self.setupModels()
        self.setupCards()
        self.exec_()

    # Models
    ##########################################################################

    def setupModels(self):
        self.model = None
        c = self.connect; f = self.form; s = SIGNAL("clicked()")
        c(f.modelsAdd, s, self.onAdd)
        c(f.modelsLayout, s, self.onLayout)
        c(f.modelsDelete, s, self.onDelete)
        self.connect(self.form.modelsList, SIGNAL("currentRowChanged(int)"),
                     self.modelChanged)
        self.updateModelsList()
        self.form.modelsList.setCurrentRow(0)

    def updateModelsList(self):
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        mids = self.deck.db.list("select id from models order by name")
        self.models = [self.deck.getModel(mid) for mid in mids]
        self.form.modelsList.clear()
        for m in self.models:
            item = QListWidgetItem(_("%(name)s [%(facts)d facts]") % dict(
                name=m.name, facts=m.useCount()))
            self.form.modelsList.addItem(item)
        self.form.modelsList.setCurrentRow(row)

    def modelChanged(self):
        if self.model:
            self.saveModel()
        idx = self.form.modelsList.currentRow()
        self.model = self.models[idx]
        self.updateCards()

    def onAdd(self):
        m = aqt.modelchooser.AddModel(self.mw, self).get()
        if m:
            self.deck.addModel(m)
            self.updateModelsList()

    def onLayout(self):
        # set to current
        # # see if there's an available fact
        dummy = False
        id = self.deck.db.scalar(
            "select id from facts where mid = ?", self.model.id)
        if id:
            fact = self.deck.getFact(id)
        else:
            # generate a dummy one
            self.deck.conf['currentModelId'] = self.model.id
            fact = self.deck.newFact()
            for f in fact.keys():
                fact[f] = f
            self.deck.addFact(fact)
            dummy = True
        aqt.clayout.CardLayout(self.mw, fact, type=2, parent=self)
        if dummy:
            self.deck._delFacts([fact.id])

    def onDelete(self):
        if len(self.models) < 2:
            showInfo(_("Please add another model first."),
                     parent=self)
            return
        if not askUser(
            _("Delete this model and all its cards?"),
            parent=self):
            return
        self.deck.delModel(self.model.id)
        self.model = None
        self.updateModelsList()

    def saveModel(self):
        self.model.flush()

    # Cards
    ##########################################################################

    def setupCards(self):
        f = self.form; c = self.connect; s = SIGNAL("clicked()")
        c(f.cardList, SIGNAL("currentRowChanged(int)"),
                     self.cardRowChanged)
        c(f.cardList, SIGNAL("itemDoubleClicked(QListWidgetItem*)"),
                     self.renameCard)
        c(f.cardAdd, s, self.addCard)
        c(f.cardDelete, s, self.deleteCard)
        c(f.cardUp, s, self.moveCardUp)
        c(f.cardDown, s, self.moveCardDown)
        self.updateCards()

    def renameCard(self, item):
        txt = ui.utils.getText(_("New name?"), parent=self)
        if txt[0]:
            self.template['name'] = txt[0]

    def updateCards(self, row = None):
        row = self.form.cardList.currentRow() or 0
        if row == -1:
            row = 0
        self.form.cardList.clear()
        for card in self.model.templates:
            item = QListWidgetItem(card['name'])
            self.form.cardList.addItem(item)
        count = self.form.cardList.count()
        self.form.cardList.setCurrentRow(row)

    def cardRowChanged(self):
        self.template = self.model.templates[self.form.cardList.currentRow()]
        self.enableCardMoveButtons()

    def enableCardMoveButtons(self):
        f = self.form
        row = f.cardList.currentRow()
        f.cardUp.setEnabled(row >= 1)
        f.cardDown.setEnabled(row < (f.cardList.count() - 1))

    def addCard(self):
        cards = len(self.model.templates)
        t = self.model.newTemplate()
        t['name'] = _("Template %d") % (cards+1)
        fields = self.model.fields
        t['qfmt'] = "{{%s}}" % fields[0]['name']
        if len(fields) > 1:
            t['afmt'] = "{{%s}}" % fields[1]['name']
        else:
            t['afmt'] = ""
        self.model.addTemplate(t)
        self.updateCards()

    def deleteCard(self):
        if len (self.model.templates) < 2:
            ui.utils.showWarning(
                _("Please add a new template first."),
                parent=self)
            return
        if not askUser(
            _("Delete this template and all cards that use it?")):
            return
        self.model.delTemplate(self.template)
        self.updateCards()

    def moveCardUp(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        self.mw.progress.start()
        self.model.moveTemplate(self.template, row-1)
        self.mw.progress.finish()
        self.updateCards()

    def moveCardDown(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        if row == len(self.model.cardModels) - 1:
            return
        self.model.moveTemplate(self.template, row+1)
        self.mw.progress.finish()
        self.updateCards()

    # Cleanup
    ##########################################################################

    # need to flush model on change or reject

    def reject(self):
        #self.saveCurrentCard()
        self.saveModel()
        self.mw.reset()
        QDialog.reject(self)
