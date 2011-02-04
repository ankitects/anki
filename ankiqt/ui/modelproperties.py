# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import ankiqt.forms
import anki
from anki.models import FieldModel, CardModel
from ankiqt import ui

class ModelProperties(QDialog):

    def __init__(self, parent, deck, model, main=None, onFinish=None):
        QDialog.__init__(self, parent, Qt.Window)
        if not main:
            main = parent
        self.parent = main
        self.deck = deck
        self.origModTime = self.deck.modified
        self.m = model
        self.needRebuild = False
        self.onFinish = onFinish
        self.dialog = ankiqt.forms.modelproperties.Ui_ModelProperties()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.helpRequested)
        self.readData()
        self.setupCards()
        self.show()
        self.undoName = _("Model Properties")
        self.deck.setUndoStart(self.undoName)
        self.exec_()

    def readData(self):
        # properties section
        self.dialog.name.setText(self.m.name)

    # Cards
    ##########################################################################

    def setupCards(self):
        self.cardOrdinalUpdatedIds = []
        self.ignoreCardUpdate = False
        self.currentCard = None
        self.updateCards()
        self.readCurrentCard()
        self.connect(self.dialog.cardList, SIGNAL("currentRowChanged(int)"),
                     self.cardRowChanged)
        self.connect(self.dialog.cardAdd, SIGNAL("clicked()"),
                     self.addCard)
        self.connect(self.dialog.cardDelete, SIGNAL("clicked()"),
                     self.deleteCard)
        self.connect(self.dialog.cardToggle, SIGNAL("clicked()"),
                     self.toggleCard)
        self.connect(self.dialog.cardUp, SIGNAL("clicked()"),
                     self.moveCardUp)
        self.connect(self.dialog.cardDown, SIGNAL("clicked()"),
                     self.moveCardDown)
        self.connect(self.dialog.cardRename, SIGNAL("clicked()"),
                     self.renameCard)
        self.connect(self.dialog.cardLayout, SIGNAL("clicked()"),
                     self.cardLayout)

    def cardLayout(self):
        self.m.currentCard = self.currentCard
        ui.clayout.CardLayout(self, None, self.m)

    def renameCard(self):
        txt = ui.utils.getText(_("New name?"), parent=self)
        if txt[0]:
            self.currentCard.name = txt[0]
        self.needRebuild = True
        self.deck.updateCardTags(self.deck.s.column0(
            "select id from cards where cardModelId = :id",
            id=self.currentCard.id))
        self.updateCards()

    def updateCards(self, row = None):
        oldRow = self.dialog.cardList.currentRow()
        if oldRow == -1:
            oldRow = 0
        self.dialog.cardList.clear()
        n = 1
        for card in self.m.cardModels:
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
            self.dialog.cardList.addItem(item)
            n += 1
        count = self.dialog.cardList.count()
        if row != None:
            self.dialog.cardList.setCurrentRow(row)
        else:
            while (count > 0 and oldRow > (count - 1)):
                oldRow -= 1
            self.dialog.cardList.setCurrentRow(oldRow)
        self.enableCardMoveButtons()

    def cardRowChanged(self):
        if self.ignoreCardUpdate:
            return
        self.saveCurrentCard()
        self.readCurrentCard()

    def readCurrentCard(self):
        if not len(self.m.cardModels):
            self.dialog.cardToggle.setEnabled(False)
            self.dialog.cardDelete.setEnabled(False)
            self.dialog.cardUp.setEnabled(False)
            self.dialog.cardDown.setEnabled(False)
            return
        else:
            self.dialog.cardToggle.setEnabled(True)
            self.dialog.cardDelete.setEnabled(True)
        self.currentCard = self.m.cardModels[self.dialog.cardList.currentRow()]
        card = self.currentCard
        self.updateToggleButtonText(card)

    def enableCardMoveButtons(self):
        row = self.dialog.cardList.currentRow()
        if row < 1:
            self.dialog.cardUp.setEnabled(False)
        else:
            self.dialog.cardUp.setEnabled(True)
        if row == -1 or row >= (self.dialog.cardList.count() - 1):
            self.dialog.cardDown.setEnabled(False)
        else:
            self.dialog.cardDown.setEnabled(True)

    def updateToggleButtonText(self, card):
        if card.active:
            self.dialog.cardToggle.setText(_("Disa&ble"))
        else:
            self.dialog.cardToggle.setText(_("Ena&ble"))

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
            self.m.setModified()
            self.deck.setModified()
            return True
        return False

    def addCard(self):
        cards = len(self.m.cardModels)
        name = _("Template_%d") % (cards+1)
        fields = self.m.fieldModels
        qformat = "{{%s}}" % fields[0].name
        if len(fields) > 1:
            aformat = "{{%s}}" % fields[1].name
        else:
            aformat = ""
        cm = CardModel(name, qformat, aformat)
        self.m.addCardModel(cm)
        self.updateCards()
        self.dialog.cardList.setCurrentRow(len(self.m.cardModels)-1)

    def deleteCard(self):
        row = self.dialog.cardList.currentRow()
        if row == -1:
            return
        if len (self.m.cardModels) < 2:
            ui.utils.showWarning(
                _("Please add a new template first."),
                parent=self)
            return
        card = self.m.cardModels[row]
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
        row = self.dialog.cardList.currentRow()
        if row == -1:
            return
        card = self.m.cardModels[row]
        active = 0
        for c in self.m.cardModels:
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
        self.m.setModified()
        self.deck.setModified()

    def moveCardUp(self):
        row = self.dialog.cardList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        card = self.m.cardModels[row]
        tCard = self.m.cardModels[row - 1]
        self.m.cardModels.remove(card)
        self.m.cardModels.insert(row - 1, card)
        if card.id not in self.cardOrdinalUpdatedIds:
            self.cardOrdinalUpdatedIds.append(card.id)
        if tCard.id not in self.cardOrdinalUpdatedIds:
            self.cardOrdinalUpdatedIds.append(tCard.id)
        self.ignoreCardUpdate = True
        self.updateCards(row - 1)
        self.ignoreCardUpdate = False

    def moveCardDown(self):
        row = self.dialog.cardList.currentRow()
        if row == -1:
            return
        if row == len(self.m.cardModels) - 1:
            return
        card = self.m.cardModels[row]
        tCard = self.m.cardModels[row + 1]
        self.m.cardModels.remove(card)
        self.m.cardModels.insert(row + 1, card)
        if card.id not in self.cardOrdinalUpdatedIds:
            self.cardOrdinalUpdatedIds.append(card.id)
        if tCard.id not in self.cardOrdinalUpdatedIds:
            self.cardOrdinalUpdatedIds.append(tCard.id)
        self.ignoreCardUpdate = True
        self.updateCards(row + 1)
        self.ignoreCardUpdate = False

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "ModelProperties"))

    # Cleanup
    ##########################################################################

    def reject(self):
        "Save user settings on close."
        # update properties
        self.deck.startProgress()
        mname = unicode(self.dialog.name.text())
        if not mname:
            mname = _("Model")
        self.updateField(self.m, 'name', mname)
        self.updateField(self.m, 'tags', mname)
        self.saveCurrentCard()
        # if changed, reset deck
        reset = False
        if len(self.cardOrdinalUpdatedIds) > 0:
            self.deck.rebuildCardOrdinals(self.cardOrdinalUpdatedIds)
            self.m.setModified()
            self.deck.setModified()
        if self.origModTime != self.deck.modified:
            self.deck.updateTagsForModel(self.m)
            reset = True
        if self.needRebuild:
            # need to generate q/a templates
            self.deck.updateCardsFromModel(self.m)
            reset = True
        if reset:
            ankiqt.mw.reset()
        if self.onFinish:
            self.onFinish()
        self.deck.setUndoEnd(self.undoName)
        # check again
        self.deck.finishProgress()
        QDialog.reject(self)
