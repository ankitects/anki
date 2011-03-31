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
        #self.setupCards()
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
        mids = self.deck.db.list("select id from models order by name")
        self.models = [self.deck.getModel(mid) for mid in mids]
        self.form.modelsList.clear()
        for m in self.models:
            item = QListWidgetItem(m.name)
            self.form.modelsList.addItem(item)
            # if foo:
            #self.form.modelsList.setCurrentItem(item)

    def modelChanged(self):
        print "changed"
        if self.model:
            self.saveModel()
        idx = self.form.modelsList.currentRow()
        self.model = self.models[idx]

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
        self.ignoreCardUpdate = False
        self.currentCard = None
        self.updateCards()
        self.readCurrentCard()
        self.connect(self.form.cardList, SIGNAL("currentRowChanged(int)"),
                     self.cardRowChanged)
        self.connect(self.form.cardAdd, SIGNAL("clicked()"),
                     self.addCard)
        self.connect(self.form.cardDelete, SIGNAL("clicked()"),
                     self.deleteCard)
        self.connect(self.form.cardToggle, SIGNAL("clicked()"),
                     self.toggleCard)
        self.connect(self.form.cardUp, SIGNAL("clicked()"),
                     self.moveCardUp)
        self.connect(self.form.cardDown, SIGNAL("clicked()"),
                     self.moveCardDown)
        self.connect(self.form.cardRename, SIGNAL("clicked()"),
                     self.renameCard)
        self.connect(self.form.cardLayout, SIGNAL("clicked()"),
                     self.cardLayout)

    def renameCard(self):
        txt = ui.utils.getText(_("New name?"), parent=self)
        if txt[0]:
            self.currentCard.name = txt[0]
        self.needRebuild = True
        self.deck.updateCardTags(self.deck.db.column0(
            "select id from cards where cardModelId = :id",
            id=self.currentCard.id))
        self.updateCards()

    def updateCards(self, row = None):
        oldRow = self.form.cardList.currentRow()
        if oldRow == -1:
            oldRow = 0
        self.form.cardList.clear()
        n = 1
        for card in self.model.cardModels:
            if card.active:
                status=""
            else:
                status=_("; disabled")
            cards = self.deck.cardModelUseCount(card)
            label = "%(name)s [%(cards)s%(status)s]" % {
                'num': n,
                'name': card.name,
                'status': status,
                'cards': ngettext("%d fact", "%d facts", cards) % cards
                }
            item = QListWidgetItem(label)
            self.form.cardList.addItem(item)
            n += 1
        count = self.form.cardList.count()
        if row != None:
            self.form.cardList.setCurrentRow(row)
        else:
            while (count > 0 and oldRow > (count - 1)):
                oldRow -= 1
            self.form.cardList.setCurrentRow(oldRow)
        self.enableCardMoveButtons()

    def cardRowChanged(self):
        if self.ignoreCardUpdate:
            return
        self.saveCurrentCard()
        self.readCurrentCard()

    def readCurrentCard(self):
        if not len(self.model.cardModels):
            self.form.cardToggle.setEnabled(False)
            self.form.cardDelete.setEnabled(False)
            self.form.cardUp.setEnabled(False)
            self.form.cardDown.setEnabled(False)
            return
        else:
            self.form.cardToggle.setEnabled(True)
            self.form.cardDelete.setEnabled(True)
        self.currentCard = self.model.cardModels[self.form.cardList.currentRow()]
        card = self.currentCard
        self.updateToggleButtonText(card)

    def enableCardMoveButtons(self):
        row = self.form.cardList.currentRow()
        if row < 1:
            self.form.cardUp.setEnabled(False)
        else:
            self.form.cardUp.setEnabled(True)
        if row == -1 or row >= (self.form.cardList.count() - 1):
            self.form.cardDown.setEnabled(False)
        else:
            self.form.cardDown.setEnabled(True)

    def updateToggleButtonText(self, card):
        if card.active:
            self.form.cardToggle.setText(_("Disa&ble"))
        else:
            self.form.cardToggle.setText(_("Ena&ble"))

    def saveCurrentCard(self):
        if not self.currentCard:
            return
        card = self.currentCard
        self.ignoreCardUpdate = True
        self.updateCards()
        self.ignoreCardUpdate = False

    def updateField(self, obj, field, value):
        if getattr(obj, field) != value:
            setattr(obj, field, value)
            self.model.setModified()
            self.deck.setModified()
            return True
        return False

    def addCard(self):
        cards = len(self.model.cardModels)
        name = _("Template_%d") % (cards+1)
        fields = self.model.fieldModels
        qformat = "{{%s}}" % fields[0].name
        if len(fields) > 1:
            aformat = "{{%s}}" % fields[1].name
        else:
            aformat = ""
        cm = CardModel(name, qformat, aformat)
        self.deck.addCardModel(m, cm)
        self.updateCards()
        self.form.cardList.setCurrentRow(len(self.model.cardModels)-1)

    def deleteCard(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        if len (self.model.cardModels) < 2:
            ui.utils.showWarning(
                _("Please add a new template first."),
                parent=self)
            return
        card = self.model.cardModels[row]
        count = self.deck.cardModelUseCount(card)
        if count:
            if not ui.utils.askUser(
                _("This template is used by %d cards. If you delete it,\n"
                  "all the cards will be deleted too. If you just\n"
                  "want to prevent the creation of future cards with\n"
                  "this template, please use the 'disable'  button\n"
                  "instead.\n\nReally delete these cards?") % count,
                parent=self):
                return
        self.deck.deleteCardModel(self.m, card)
        self.updateCards()

    def toggleCard(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        card = self.model.cardModels[row]
        active = 0
        for c in self.model.cardModels:
            if c.active:
                active += 1
        if active < 2 and card.active:
            ui.utils.showWarning(
                _("Please enable a different template first."),
                parent=self)
            return
        card.active = not card.active
        self.updateToggleButtonText(card)
        self.updateCards()
        self.model.setModified()
        self.deck.setModified()

    def moveCardUp(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        self.mw.progress.start()
        self.model.moveTemplate(self.template, row-1)
        self.mw.progress.finish()
        self.ignoreCardUpdate = True
        self.updateCards(row-1)
        self.ignoreCardUpdate = False

    def moveCardDown(self):
        row = self.form.cardList.currentRow()
        if row == -1:
            return
        if row == len(self.model.cardModels) - 1:
            return
        self.model.moveTemplate(self.template, row+1)
        self.mw.progress.finish()
        self.ignoreCardUpdate = True
        self.updateCards(row+1)
        self.ignoreCardUpdate = False

    # Cleanup
    ##########################################################################

    # need to flush model on change or reject

    def reject(self):
        #self.saveCurrentCard()
        self.saveModel()
        self.mw.reset()
        QDialog.reject(self)
