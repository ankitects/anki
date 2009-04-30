# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import ankiqt.forms
import anki
from anki.models import FieldModel, CardModel
from ankiqt import ui

tabs = ("General",
        "Fields",
        "Cards")

class ModelProperties(QDialog):

    def __init__(self, parent, deck, model, main=None, onFinish=None):
        QDialog.__init__(self, parent, Qt.Window)
        if not main:
            main = parent
        self.parent = main
        self.deck = deck
        self.origModTime = self.deck.modified
        self.m = model
        self.onFinish = onFinish
        self.dialog = ankiqt.forms.modelproperties.Ui_ModelProperties()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"),
                     self.helpRequested)
        self.setupFields()
        self.setupCards()
        self.readData()
        self.show()
        self.undoName = _("Model Properties")
        self.deck.setUndoStart(self.undoName)
        self.exec_()

    def readData(self):
        # properties section
        self.dialog.name.setText(self.m.name)
        self.dialog.tags.setText(self.m.tags)
        self.dialog.spacing.setText(str(self.m.spacing))
        self.dialog.initialSpacing.setText(str(self.m.initialSpacing/60))

    # Fields
    ##########################################################################

    def setupFields(self):
        self.fieldOrdinalUpdatedIds = []
        self.ignoreFieldUpdate = False
        self.currentField = None
        self.updateFields()
        self.readCurrentField()
        self.connect(self.dialog.fieldList, SIGNAL("currentRowChanged(int)"),
                     self.fieldRowChanged)
        self.connect(self.dialog.tabWidget, SIGNAL("currentChanged(int)"),
                     self.fieldRowChanged)
        self.connect(self.dialog.fieldAdd, SIGNAL("clicked()"),
                     self.addField)
        self.connect(self.dialog.fieldDelete, SIGNAL("clicked()"),
                     self.deleteField)
        self.connect(self.dialog.fieldUp, SIGNAL("clicked()"),
                     self.moveFieldUp)
        self.connect(self.dialog.fieldDown, SIGNAL("clicked()"),
                     self.moveFieldDown)
        self.connect(self.dialog.fieldName, SIGNAL("lostFocus()"),
                     self.updateFields)

    def updateFields(self, row = None):
        oldRow = self.dialog.fieldList.currentRow()
        if oldRow == -1:
            oldRow = 0
        self.dialog.fieldList.clear()
        n = 1
        for field in self.m.fieldModels:
            label = _("Field %(num)d: %(name)s [%(cards)s non-empty]") % {
                'num': n,
                'name': field.name,
                'cards': self.deck.fieldModelUseCount(field)
                }
            item = QListWidgetItem(label)
            self.dialog.fieldList.addItem(item)
            n += 1
        count = self.dialog.fieldList.count()
        if row != None:
            self.dialog.fieldList.setCurrentRow(row)
        else:
            while (count > 0 and oldRow > (count - 1)):
                    oldRow -= 1
            self.dialog.fieldList.setCurrentRow(oldRow)
        self.enableFieldMoveButtons()

    def fieldRowChanged(self):
        if self.ignoreFieldUpdate:
            return
        self.saveCurrentField()
        self.readCurrentField()

    def readCurrentField(self):
        if not len(self.m.fieldModels):
            self.dialog.fieldEditBox.hide()
            self.dialog.fieldUp.setEnabled(False)
            self.dialog.fieldDown.setEnabled(False)
            return
        else:
            self.dialog.fieldEditBox.show()
        self.currentField = self.m.fieldModels[self.dialog.fieldList.currentRow()]
        field = self.currentField
        self.dialog.fieldName.setText(field.name)
        self.dialog.fieldUnique.setChecked(field.unique)
        self.dialog.fieldRequired.setChecked(field.required)
        self.dialog.numeric.setChecked(field.numeric)

    def enableFieldMoveButtons(self):
        row = self.dialog.fieldList.currentRow()
        if row < 1:
            self.dialog.fieldUp.setEnabled(False)
        else:
            self.dialog.fieldUp.setEnabled(True)
        if row == -1 or row >= (self.dialog.fieldList.count() - 1):
            self.dialog.fieldDown.setEnabled(False)
        else:
            self.dialog.fieldDown.setEnabled(True)

    def saveCurrentField(self):
        if not self.currentField:
            return
        field = self.currentField
        name = unicode(self.dialog.fieldName.text()).strip()
        # renames
        if not name:
            name = _("Field %d") % (self.m.fieldModels.index(field) + 1)
        if name != field.name:
            self.deck.renameFieldModel(self.m, field, name)
            # the card models will have been updated
            self.readCurrentCard()
        # unique, required, numeric
        self.updateField(field, 'unique',
                         self.dialog.fieldUnique.checkState() == Qt.Checked)
        self.updateField(field, 'required',
                         self.dialog.fieldRequired.checkState() == Qt.Checked)
        self.updateField(field, 'numeric',
                         self.dialog.numeric.checkState() == Qt.Checked)
        self.ignoreFieldUpdate = True
        self.updateFields()
        self.ignoreFieldUpdate = False

    def addField(self):
        f = FieldModel()
        f.name = _("Field %d") % (len(self.m.fieldModels) + 1)
        self.deck.addFieldModel(self.m, f)
        self.updateFields()
        self.dialog.fieldList.setCurrentRow(len(self.m.fieldModels)-1)
        self.dialog.fieldName.setFocus()
        self.dialog.fieldName.selectAll()

    def deleteField(self):
        row = self.dialog.fieldList.currentRow()
        if row == -1:
            return
        if len(self.m.fieldModels) < 2:
            ui.utils.showInfo(
                _("Please add a new field first."),
                parent=self)
            return
        field = self.m.fieldModels[row]
        count = self.deck.fieldModelUseCount(field)
        if count:
            if not ui.utils.askUser(
                _("This field is used by %d cards. If you delete it,\n"
                  "all information in this field will be lost.\n"
                  "\nReally delete this field?") % count,
                parent=self):
                return
        self.deck.deleteFieldModel(self.m, field)
        self.currentField = None
        self.updateFields()
        # need to update q/a format
        self.readCurrentCard()

    def moveFieldUp(self):
        row = self.dialog.fieldList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        field = self.m.fieldModels[row]
        tField = self.m.fieldModels[row - 1]
        self.m.fieldModels.remove(field)
        self.m.fieldModels.insert(row - 1, field)
        if field.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(field.id)
        if tField.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(tField.id)
        self.ignoreFieldUpdate = True
        self.updateFields(row - 1)
        self.ignoreFieldUpdate = False

    def moveFieldDown(self):
        row = self.dialog.fieldList.currentRow()
        if row == -1:
            return
        if row == len(self.m.fieldModels) - 1:
            return
        field = self.m.fieldModels[row]
        tField = self.m.fieldModels[row + 1]
        self.m.fieldModels.remove(field)
        self.m.fieldModels.insert(row + 1, field)
        if field.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(field.id)
        if tField.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(tField.id)
        self.ignoreFieldUpdate = True
        self.updateFields(row + 1)
        self.ignoreFieldUpdate = False

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
        self.connect(self.dialog.cardName, SIGNAL("lostFocus()"),
                     self.updateCards)

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
            label = _("Card %(num)d (%(name)s): used %(cards)d times%(status)s") % {
                'num': n,
                'name': card.name,
                'status': status,
                'cards': self.deck.cardModelUseCount(card),
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
            self.dialog.cardEditBox.hide()
            self.dialog.cardToggle.setEnabled(False)
            self.dialog.cardDelete.setEnabled(False)
            self.dialog.cardUp.setEnabled(False)
            self.dialog.cardDown.setEnabled(False)
            return
        else:
            self.dialog.cardEditBox.show()
            self.dialog.cardToggle.setEnabled(True)
            self.dialog.cardDelete.setEnabled(True)
        self.currentCard = self.m.cardModels[self.dialog.cardList.currentRow()]
        card = self.currentCard
        self.dialog.cardName.setText(card.name)
        self.dialog.cardQuestion.setPlainText(card.qformat.replace("<br>", "<br>\n"))
        self.dialog.cardAnswer.setPlainText(card.aformat.replace("<br>", "<br>\n"))
        self.dialog.questionInAnswer.setChecked(card.questionInAnswer)
        self.dialog.allowEmptyAnswer.setChecked(card.allowEmptyAnswer)
        self.dialog.typeAnswer.clear()
        self.fieldNames = self.deck.s.column0("""
select fieldModels.name as n from fieldModels, cardModels
where cardModels.modelId = fieldModels.modelId
and cardModels.id = :id
order by n""", id=card.id)
        s = [_("Don't ask me to type in the answer")]
        s += [_("Compare with field '%s'") % f for f in self.fieldNames]
        self.dialog.typeAnswer.insertItems(0, QStringList(s))
        try:
            idx = self.fieldNames.index(card.typeAnswer)
        except ValueError:
            idx = -1
        self.dialog.typeAnswer.setCurrentIndex(idx + 1)
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
        newname = unicode(self.dialog.cardName.text())
        if not newname:
            newname = _("Card %d") % (self.m.cardModels.index(card) + 1)
        self.updateField(card, 'name', newname)
        s = unicode(self.dialog.cardQuestion.toPlainText())
        s = s.replace("<br>\n", "<br>")
        changed = self.updateField(card, 'qformat', s)
        s = unicode(self.dialog.cardAnswer.toPlainText())
        s = s.replace("<br>\n", "<br>")
        changed2 = self.updateField(card, 'aformat', s)
        changed = changed or changed2
        self.updateField(card, 'questionInAnswer', self.dialog.questionInAnswer.isChecked())
        self.updateField(card, 'allowEmptyAnswer', self.dialog.allowEmptyAnswer.isChecked())
        idx = self.dialog.typeAnswer.currentIndex()
        if not idx:
            self.updateField(card, 'typeAnswer', u"")
        else:
            self.updateField(card, 'typeAnswer', self.fieldNames[idx-1])
        if changed:
            # need to generate all question/answers for this card
            self.deck.updateCardsFromModel(self.m)
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
        name = _("Card %d") % (cards+1)
        cm = CardModel(name=name)
        self.m.addCardModel(cm)
        self.updateCards()
        self.dialog.cardList.setCurrentRow(len(self.m.cardModels)-1)
        self.dialog.cardName.setFocus()
        self.dialog.cardName.selectAll()

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
        idx = self.dialog.tabWidget.currentIndex()
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "ModelProperties#" +
                                      tabs[idx]))

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
        self.updateField(self.m, 'tags',
                         unicode(self.dialog.tags.text()))
        try:
            self.updateField(self.m, 'spacing',
                             float(self.dialog.spacing.text()))
            self.updateField(self.m, 'initialSpacing',
                             float(self.dialog.initialSpacing.text())*60)
        except ValueError:
            pass
        # before field, or it's overwritten
        self.saveCurrentCard()
        self.saveCurrentField()
        # rebuild ordinals if changed
        if len(self.fieldOrdinalUpdatedIds) > 0:
            self.deck.rebuildFieldOrdinals(self.m.id, self.fieldOrdinalUpdatedIds)
            self.m.setModified()
            self.deck.setModified()
        if len(self.cardOrdinalUpdatedIds) > 0:
            self.deck.rebuildCardOrdinals(self.cardOrdinalUpdatedIds)
            self.m.setModified()
            self.deck.setModified()
        # if changed, reset deck
        if self.origModTime != self.deck.modified:
            self.deck.updateTagsForModel(self.m)
            ankiqt.mw.reset()
        if self.onFinish:
            self.onFinish()
        self.deck.setUndoEnd(self.undoName)
        # check again
        self.deck.haveJapanese = None
        self.deck.finishProgress()
        QDialog.reject(self)
