# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtWebKit import QWebPage, QWebView
import sys, re
import ankiqt.forms
import anki
from anki.models import *
from anki.facts import *
from anki.fonts import toCanonicalFont
from anki.cards import Card
from anki.sound import playFromText, clearAudioQueue
from ankiqt.ui.utils import saveGeom, restoreGeom, getBase, mungeQA
from anki.hooks import runFilter
from ankiqt import ui

class CardLayout(QDialog):

    def __init__(self, factedit, fact, card=None):
        self.parent = factedit.parent
        QDialog.__init__(self, self.parent)
        self.factedit = factedit
        self.mw = ankiqt.mw
        self.deck = self.mw.deck
        self.fact = fact
        self.model = fact.model
        self.card = card
        self.ignoreUpdate = False
        self.needFormatRebuild = False
        self.plastiqueStyle = None
        if (sys.platform.startswith("darwin") or
            sys.platform.startswith("win32")):
            self.plastiqueStyle = QStyleFactory.create("plastique")

        if self.card:
            # limited to an existing card
            self.cards = [self.card]
        else:
            # active & possible
            self.cards = self.deck.previewFact(self.fact)
            if not self.cards:
                ui.utils.showInfo(_(
                    "Please enter some text first."),
                                  parent=self.parent)
                return
        self.form = ankiqt.forms.clayout.Ui_Dialog()
        self.form.setupUi(self)
        # self.connect(self.form.helpButton, SIGNAL("clicked()"),
        #              self.onHelp)

        self.setupCards()
        # self.setupFields()
        restoreGeom(self, "CardLayout")
        self.exec_()

    # Cards & Preview
    ##########################################################################

    def setupCards(self):
        self.connect(self.form.cardList, SIGNAL("activated(int)"),
                     self.cardChanged)
        self.connect(self.form.cardQuestion, SIGNAL("textChanged()"),
                     lambda: self.formatChanged("question"))
        self.connect(self.form.cardAnswer, SIGNAL("textChanged()"),
                     lambda: self.formatChanged("answer"))
        self.connect(self.form.alignment,
                     SIGNAL("activated(int)"),
                     self.saveCard)
        self.connect(self.form.background,
                     SIGNAL("clicked()"),
                     lambda w=self.form.background:\
                     self.chooseColour(w, "card"))
        self.connect(self.form.questionInAnswer,
                     SIGNAL("clicked()"), self.saveCard)
        self.connect(self.form.allowEmptyAnswer,
                     SIGNAL("clicked()"), self.saveCard)
        self.connect(self.form.typeAnswer, SIGNAL("activated(int)"),
                     self.saveCard)
        self.connect(self.form.flipButton, SIGNAL("clicked()"),
                     self.onFlip)
        def linkClicked(self, url):
            QDesktopServices.openUrl(QUrl(url))
        self.form.preview.page().setLinkDelegationPolicy(
            QWebPage.DelegateExternalLinks)
        self.connect(self.form.preview,
                     SIGNAL("linkClicked(QUrl)"),
                     linkClicked)
        if self.plastiqueStyle:
            self.form.background.setStyle(self.plastiqueStyle)
        self.form.alignment.clear()
        self.form.alignment.addItems(
                QStringList(alignmentLabels().values()))
        self.fillCardList()

    def formatToScreen(self, fmt):
        fmt = re.sub("%\((.+?)\)s", "{{\\1}}", fmt)
        fmt = fmt.replace("}}<br>", "}}\n")
        return fmt

    def screenToFormat(self, fmt):
        fmt = fmt.replace("}}\n", "}}<br>")
        fmt = re.sub("{{(.+?)}}", "%(\\1)s", fmt)
        return fmt

    # def realCardModel(self, card):
    #     # get on disk representation from detached object
    #     for cm in self.fact.model.cardModels:
    #         if cm.id == card.cardModelId:
    #             return cm

    def formatChanged(self, type):
        if self.updatingCards:
            return
        if type == "question":
            text = unicode(self.form.cardQuestion.toPlainText())
            text = self.screenToFormat(text)
            #self.realCardModel(self.card).qformat = text
            self.card.cardModel.qformat = text
        else:
            text = unicode(self.form.cardAnswer.toPlainText())
            text = self.screenToFormat(text)
            self.card.cardModel.aformat = text
        self.fact.model.setModified()
        self.deck.flushMod()
        d = {}
        for f in self.fact.model.fieldModels:
            d[f.name] = (f.id, self.fact[f.name])
        for card in self.cards:
            qa = formatQA(None, self.fact.modelId, d, card.splitTags(), card.cardModel)
            card.question = qa['question']
            card.answer = qa['answer']
            card.setModified()
        self.deck.setModified()
        self.needFormatRebuild = True
        self.renderPreview()

    def onFlip(self):
        q = unicode(self.form.cardQuestion.toPlainText())
        a = unicode(self.form.cardAnswer.toPlainText())
        self.form.cardAnswer.setPlainText(q)
        self.form.cardQuestion.setPlainText(a)

    def readCard(self):
        card = self.card.cardModel
        self.form.background.setPalette(QPalette(QColor(
            getattr(card, "lastFontColour"))))
        self.updatingCards = True
        self.form.cardQuestion.setPlainText(self.formatToScreen(card.qformat))
        self.form.cardAnswer.setPlainText(self.formatToScreen(card.aformat))
        self.form.questionInAnswer.setChecked(card.questionInAnswer)
        self.form.allowEmptyAnswer.setChecked(card.allowEmptyAnswer)
        self.form.typeAnswer.clear()
        self.typeFieldNames = self.deck.s.column0("""
select fieldModels.name as n from fieldModels, cardModels
where cardModels.modelId = fieldModels.modelId
and cardModels.id = :id
order by n""", id=card.id)
        s = [_("Don't ask me to type in the answer")]
        s += [_("Compare with field '%s'") % f for f in self.typeFieldNames]
        self.form.typeAnswer.insertItems(0, QStringList(s))
        try:
            idx = self.typeFieldNames.index(card.typeAnswer)
        except ValueError:
            idx = -1
        self.form.typeAnswer.setCurrentIndex(idx + 1)
        self.updatingCards = False

    # def updateField(self, obj, field, value):
    #     if getattr(obj, field) != value:
    #         setattr(obj, field, value)
    #         self.model.setModified()
    #         self.deck.setModified()
    #         return True
    #     return False

    def fillCardList(self):
        self.form.cardList.clear()
        self.form.cardList.addItems(
            QStringList([c.cardModel.name for c in self.cards]))
        if [self.card] == self.cards:
            self.form.cardList.setEnabled(False)
            self.form.editTemplates.setEnabled(False)
        self.cardChanged(0)

    def cardChanged(self, idx):
        self.card = self.cards[idx]
        self.readCard()
        self.renderPreview()

    def saveCard(self):
        if self.updatingCards:
            return
        card = self.card.cardModel
        card.questionAlign = self.form.alignment.currentIndex()
        card.lastFontColour = unicode(
            self.form.background.palette().window().color().name())
        card.questionInAnswer = self.form.questionInAnswer.isChecked()
        card.allowEmptyAnswer = self.form.allowEmptyAnswer.isChecked()
        idx = self.form.typeAnswer.currentIndex()
        if not idx:
            card.typeAnswer = u""
        else:
            card.typeAnswer = self.typeFieldNames[idx-1]
        card.model.setModified()
        self.deck.flushMod()
        self.renderPreview()

    def chooseColour(self, button, type="field"):
        new = QColorDialog.getColor(button.palette().window().color(), self)
        if new.isValid():
            button.setPalette(QPalette(new))
            if type == "field":
                self.saveField()
            else:
                self.saveCard()

    def renderPreview(self):
        if self.card:
            c = self.card
        else:
            # we'll need to generate one
            cards = self.deck.previewFact(self.fact)
            if not cards:
                ui.utils.showInfo(_("No cards to preview."),
                                  parent=parent)
                return
            pass
        styles = (self.deck.rebuildCSS() +
                  ("\nhtml { background: %s }" % c.cardModel.lastFontColour) +
                  "\ndiv { white-space: pre-wrap; }")
        styles = runFilter("addStyles", styles, c)
        self.form.preview.setHtml(
            ('<html><head>%s</head><body>' % getBase(self.deck, c)) +
            "<style>" + styles + "</style>" +
            runFilter("drawQuestion", mungeQA(self.deck, c.htmlQuestion()),
                      c) +
            "<hr>" +
            runFilter("drawAnswer", mungeQA(self.deck, c.htmlAnswer()),
                      c)
            + "</body></html>")
        clearAudioQueue()
        playFromText(c.question)
        playFromText(c.answer)

    def reject(self):
        if self.needFormatRebuild:
            # need to generate q/a templates
            self.deck.startProgress()
            self.deck.updateProgress(_("Applying template..."))
            self.deck.updateCardsFromModel(self.fact.model)
            self.deck.finishProgress()
            if self.factedit.onChange:
                self.factedit.onChange("all")
            else:
                self.mw.reset()
        saveGeom(self, "CardLayout")
        QDialog.reject(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "DisplayProperties"))

    # Fields
    ##########################################################################

    # def setupFields(self):
    #     self.connect(self.form.fieldList, SIGNAL("currentRowChanged(int)"),
    #                  self.fieldChanged)
    #     for type in ("quiz", "edit"):
    #         self.connect(self.fwidget("fontFamily", type),
    #                      SIGNAL("currentFontChanged(QFont)"),
    #                      self.saveField)
    #         self.connect(self.fwidget("fontSize", type),
    #                      SIGNAL("valueChanged(int)"),
    #                      self.saveField)
    #         self.connect(self.fwidget("useFamily", type),
    #                      SIGNAL("stateChanged(int)"),
    #                      self.saveField)
    #         self.connect(self.fwidget("useSize", type),
    #                      SIGNAL("stateChanged(int)"),
    #                      self.saveField)
    #         if type == "quiz":
    #             self.connect(self.fwidget("useColour", type),
    #                          SIGNAL("stateChanged(int)"),
    #                          self.saveField)
    #             w = self.fwidget("fontColour", type)
    #             if self.plastiqueStyle:
    #                 w.setStyle(self.plastiqueStyle)
    #             self.connect(w,
    #                          SIGNAL("clicked()"),
    #                          lambda w=w: self.chooseColour(w))
    #         elif type == "edit":
    #             self.connect(self.form.rtl,
    #                          SIGNAL("stateChanged(int)"),
    #                          self.saveField)
    #     self.currentField = None
    #     self.drawFields()

    # def drawFields(self):
    #     self.form.fieldList.clear()
    #     n = 1
    #     self.ignoreUpdate = True
    #     for field in self.model.fieldModels:
    #         item = QListWidgetItem(
    #             _("Field %(num)d: %(name)s") % {
    #             'num': n,
    #             'name': field.name,
    #             })
    #         self.form.fieldList.addItem(item)
    #         n += 1
    #     self.form.fieldList.setCurrentRow(0)
    #     self.fieldChanged(0)
    #     self.ignoreUpdate = False

    # def fwidget(self, name, type):
    #     "Return a field widget."
    #     if type == "edit":
    #         return getattr(self.form, name+"Edit")
    #     else:
    #         return getattr(self.form, name)

    # def fieldChanged(self, idx):
    #     self.saveField()
    #     self.currentField = None
    #     field = self.model.fieldModels[idx]
    #     for type in ("quiz", "edit"):
    #         # family
    #         if getattr(field, type + 'FontFamily'):
    #             self.fwidget("useFamily", type).setCheckState(Qt.Checked)
    #             self.fwidget("fontFamily", type).setCurrentFont(QFont(
    #                 getattr(field, type + 'FontFamily')))
    #             self.fwidget("fontFamily", type).setEnabled(True)
    #         else:
    #             self.fwidget("useFamily", type).setCheckState(Qt.Unchecked)
    #             self.fwidget("fontFamily", type).setEnabled(False)
    #         # size
    #         if getattr(field, type + 'FontSize'):
    #             self.fwidget("useSize", type).setCheckState(Qt.Checked)
    #             self.fwidget("fontSize", type).setValue(
    #                 getattr(field, type + 'FontSize'))
    #             self.fwidget("fontSize", type).setEnabled(True)
    #         else:
    #             self.fwidget("useSize", type).setCheckState(Qt.Unchecked)
    #             self.fwidget("fontSize", type).setEnabled(False)
    #         if type == "quiz":
    #             # colour
    #             if getattr(field, type + 'FontColour'):
    #                 self.fwidget("useColour", type).setCheckState(Qt.Checked)
    #                 self.fwidget("fontColour", type).setPalette(QPalette(QColor(
    #                     getattr(field, type + 'FontColour'))))
    #                 self.fwidget("fontColour", type).setEnabled(True)
    #             else:
    #                 self.fwidget("useColour", type).setCheckState(Qt.Unchecked)
    #                 self.fwidget("fontColour", type).setEnabled(False)
    #         elif type == "edit":
    #             self.form.rtl.setChecked(not not field.features)
    #     self.currentField = field

    # def saveField(self, *args):
    #     if self.ignoreUpdate:
    #         return
    #     field = self.currentField
    #     if not field:
    #         return
    #     for type in ("quiz", "edit"):
    #         # family
    #         if self.fwidget("useFamily", type).isChecked():
    #             setattr(field, type + 'FontFamily', toCanonicalFont(unicode(
    #                 self.fwidget("fontFamily", type).currentFont().family())))
    #         else:
    #             setattr(field, type + 'FontFamily', None)
    #         # size
    #         if self.fwidget("useSize", type).isChecked():
    #             setattr(field, type + 'FontSize',
    #                     int(self.fwidget("fontSize", type).value()))
    #         else:
    #             setattr(field, type + 'FontSize', None)
    #         # colour
    #         if type == "quiz":
    #             if self.fwidget("useColour", type).isChecked():
    #                 w = self.fwidget("fontColour", type)
    #                 c = w.palette().window().color()
    #                 setattr(field, type + 'FontColour', str(c.name()))
    #             else:
    #                 setattr(field, type + 'FontColour', None)
    #         elif type == "edit":
    #             if self.form.rtl.isChecked():
    #                 field.features = u"rtl"
    #             else:
    #                 field.features = u""
    #     field.model.setModified()
    #     self.deck.flushMod()
    #     self.drawQuestionAndAnswer()

    # def setupFields(self):
    #     self.fieldOrdinalUpdatedIds = []
    #     self.ignoreFieldUpdate = False
    #     self.currentField = None
    #     self.updateFields()
    #     self.readCurrentField()
    #     self.connect(self.form.fieldList, SIGNAL("currentRowChanged(int)"),
    #                  self.fieldRowChanged)
    #     self.connect(self.form.tabWidget, SIGNAL("currentChanged(int)"),
    #                  self.fieldRowChanged)
    #     self.connect(self.form.fieldAdd, SIGNAL("clicked()"),
    #                  self.addField)
    #     self.connect(self.form.fieldDelete, SIGNAL("clicked()"),
    #                  self.deleteField)
    #     self.connect(self.form.fieldUp, SIGNAL("clicked()"),
    #                  self.moveFieldUp)
    #     self.connect(self.form.fieldDown, SIGNAL("clicked()"),
    #                  self.moveFieldDown)
    #     self.connect(self.form.fieldName, SIGNAL("lostFocus()"),
    #                  self.updateFields)

    # def updateFields(self, row = None):
    #     oldRow = self.form.fieldList.currentRow()
    #     if oldRow == -1:
    #         oldRow = 0
    #     self.form.fieldList.clear()
    #     n = 1
    #     for field in self.model.fieldModels:
    #         label = _("Field %(num)d: %(name)s [%(cards)s non-empty]") % {
    #             'num': n,
    #             'name': field.name,
    #             'cards': self.deck.fieldModelUseCount(field)
    #             }
    #         item = QListWidgetItem(label)
    #         self.form.fieldList.addItem(item)
    #         n += 1
    #     count = self.form.fieldList.count()
    #     if row != None:
    #         self.form.fieldList.setCurrentRow(row)
    #     else:
    #         while (count > 0 and oldRow > (count - 1)):
    #                 oldRow -= 1
    #         self.form.fieldList.setCurrentRow(oldRow)
    #     self.enableFieldMoveButtons()

    # def fieldRowChanged(self):
    #     if self.ignoreFieldUpdate:
    #         return
    #     self.saveCurrentField()
    #     self.readCurrentField()

    # def readCurrentField(self):
    #     if not len(self.model.fieldModels):
    #         self.form.fieldEditBox.hide()
    #         self.form.fieldUp.setEnabled(False)
    #         self.form.fieldDown.setEnabled(False)
    #         return
    #     else:
    #         self.form.fieldEditBox.show()
    #     self.currentField = self.model.fieldModels[self.form.fieldList.currentRow()]
    #     field = self.currentField
    #     self.form.fieldName.setText(field.name)
    #     self.form.fieldUnique.setChecked(field.unique)
    #     self.form.fieldRequired.setChecked(field.required)
    #     self.form.numeric.setChecked(field.numeric)

    # def enableFieldMoveButtons(self):
    #     row = self.form.fieldList.currentRow()
    #     if row < 1:
    #         self.form.fieldUp.setEnabled(False)
    #     else:
    #         self.form.fieldUp.setEnabled(True)
    #     if row == -1 or row >= (self.form.fieldList.count() - 1):
    #         self.form.fieldDown.setEnabled(False)
    #     else:
    #         self.form.fieldDown.setEnabled(True)

    # def saveCurrentField(self):
    #     if not self.currentField:
    #         return
    #     field = self.currentField
    #     name = unicode(self.form.fieldName.text()).strip()
    #     # renames
    #     if not name:
    #         name = _("Field %d") % (self.model.fieldModels.index(field) + 1)
    #     if name != field.name:
    #         self.deck.renameFieldModel(self.m, field, name)
    #         # the card models will have been updated
    #         self.readCurrentCard()
    #     # unique, required, numeric
    #     self.updateField(field, 'unique',
    #                      self.form.fieldUnique.checkState() == Qt.Checked)
    #     self.updateField(field, 'required',
    #                      self.form.fieldRequired.checkState() == Qt.Checked)
    #     self.updateField(field, 'numeric',
    #                      self.form.numeric.checkState() == Qt.Checked)
    #     self.ignoreFieldUpdate = True
    #     self.updateFields()
    #     self.ignoreFieldUpdate = False

    # def addField(self):
    #     f = FieldModel(required=False, unique=False)
    #     f.name = _("Field %d") % (len(self.model.fieldModels) + 1)
    #     self.deck.addFieldModel(self.m, f)
    #     self.updateFields()
    #     self.form.fieldList.setCurrentRow(len(self.model.fieldModels)-1)
    #     self.form.fieldName.setFocus()
    #     self.form.fieldName.selectAll()

    # def deleteField(self):
    #     row = self.form.fieldList.currentRow()
    #     if row == -1:
    #         return
    #     if len(self.model.fieldModels) < 2:
    #         ui.utils.showInfo(
    #             _("Please add a new field first."),
    #             parent=self)
    #         return
    #     field = self.model.fieldModels[row]
    #     count = self.deck.fieldModelUseCount(field)
    #     if count:
    #         if not ui.utils.askUser(
    #             _("This field is used by %d cards. If you delete it,\n"
    #               "all information in this field will be lost.\n"
    #               "\nReally delete this field?") % count,
    #             parent=self):
    #             return
    #     self.deck.deleteFieldModel(self.m, field)
    #     self.currentField = None
    #     self.updateFields()
    #     # need to update q/a format
    #     self.readCurrentCard()

    # def moveFieldUp(self):
    #     row = self.form.fieldList.currentRow()
    #     if row == -1:
    #         return
    #     if row == 0:
    #         return
    #     field = self.model.fieldModels[row]
    #     tField = self.model.fieldModels[row - 1]
    #     self.model.fieldModels.remove(field)
    #     self.model.fieldModels.insert(row - 1, field)
    #     if field.id not in self.fieldOrdinalUpdatedIds:
    #         self.fieldOrdinalUpdatedIds.append(field.id)
    #     if tField.id not in self.fieldOrdinalUpdatedIds:
    #         self.fieldOrdinalUpdatedIds.append(tField.id)
    #     self.ignoreFieldUpdate = True
    #     self.updateFields(row - 1)
    #     self.ignoreFieldUpdate = False

    # def moveFieldDown(self):
    #     row = self.form.fieldList.currentRow()
    #     if row == -1:
    #         return
    #     if row == len(self.model.fieldModels) - 1:
    #         return
    #     field = self.model.fieldModels[row]
    #     tField = self.model.fieldModels[row + 1]
    #     self.model.fieldModels.remove(field)
    #     self.model.fieldModels.insert(row + 1, field)
    #     if field.id not in self.fieldOrdinalUpdatedIds:
    #         self.fieldOrdinalUpdatedIds.append(field.id)
    #     if tField.id not in self.fieldOrdinalUpdatedIds:
    #         self.fieldOrdinalUpdatedIds.append(tField.id)
    #     self.ignoreFieldUpdate = True
    #     self.updateFields(row + 1)
    #     self.ignoreFieldUpdate = False



        # rebuild ordinals if changed
        # if len(self.fieldOrdinalUpdatedIds) > 0:
        #     self.deck.rebuildFieldOrdinals(self.model.id, self.fieldOrdinalUpdatedIds)
        #     self.model.setModified()
        #     self.deck.setModified()







# class PreviewDialog(QDialog):

#         cards = self.deck.previewFact(self.fact)
#         if not cards:
#             ui.utils.showInfo(_("No cards to preview."),
#                               parent=parent)
#             return

