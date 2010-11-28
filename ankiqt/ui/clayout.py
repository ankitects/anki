# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import sys, re
import ankiqt.forms
import anki
from anki.models import *
from anki.facts import *
from anki.fonts import toCanonicalFont
from anki.cards import Card
from ankiqt.ui.utils import saveGeom, restoreGeom
from ankiqt import ui

class CardLayout(QDialog):

    def __init__(self, parent, fact, card=None):
        QDialog.__init__(self, parent) #, Qt.Window)
        self.parent = parent
        self.mw = ankiqt.mw
        self.deck = self.mw.deck
        self.fact = fact
        self.model = fact.model
        self.card = card or self.model.cardModels[0]
        self.ignoreUpdate = False
        self.plastiqueStyle = None
        if (sys.platform.startswith("darwin") or
            sys.platform.startswith("win32")):
            self.plastiqueStyle = QStyleFactory.create("plastique")
        self.form = ankiqt.forms.clayout.Ui_Dialog()
        self.form.setupUi(self)
        # self.connect(self.form.helpButton, SIGNAL("clicked()"),
        #              self.onHelp)

        # self.setupChooser()
        self.setupCards()
        # self.setupFields()
        # self.setupButtons()
        restoreGeom(self, "CardLayout")
        self.exec_()

    def setupCards(self):
        self.connect(self.form.cardList, SIGNAL("activated(int)"),
                     self.cardChanged)
        self.connect(self.form.alignment,
                     SIGNAL("activated(int)"),
                     self.saveCard)
        self.connect(self.form.background,
                     SIGNAL("clicked()"),
                     lambda w=self.form.background:\
                     self.chooseColour(w))
        if self.plastiqueStyle:
            self.form.background.setStyle(self.plastiqueStyle)
        self.drawCards()


    def formatToScreen(self, fmt):
        fmt = fmt.replace("<br>", "<br>\n")
        fmt = re.sub("%\((.+?)\)s", "{{\\1}}", fmt)
        return fmt

    def screenToFormat(self, fmt):
        fmt = fmt.replace("<br>\n", "<br>")
        fmt = re.sub("{{(.+?)}}", "%(\\1)s", fmt)
        return fmt

    def saveCurrentCard(self):
        if not self.currentCard:
            return
        card = self.currentCard
        newname = unicode(self.form.cardName.text())
        if not newname:
            newname = _("Card-%d") % (self.model.cardModels.index(card) + 1)
        self.updateField(card, 'name', newname)
        s = unicode(self.form.cardQuestion.toPlainText())
        changed = self.updateField(card, 'qformat', self.screenToFormat(s))
        s = unicode(self.form.cardAnswer.toPlainText())
        changed2 = self.updateField(card, 'aformat', self.screenToFormat(s))
        self.needRebuild = self.needRebuild or changed or changed2
        self.updateField(card, 'questionInAnswer', self.form.questionInAnswer.isChecked())
        self.updateField(card, 'allowEmptyAnswer', self.form.allowEmptyAnswer.isChecked())
        idx = self.form.typeAnswer.currentIndex()
        if not idx:
            self.updateField(card, 'typeAnswer', u"")
        else:
            self.updateField(card, 'typeAnswer', self.fieldNames[idx-1])
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



    def drawCards(self):
        self.form.cardList.clear()
        self.form.cardList.addItems(
            QStringList([c.name for c in self.model.cardModels]))
        self.form.alignment.clear()
        self.form.alignment.addItems(
                QStringList(alignmentLabels().values()))
        self.cardChanged(0)

    def cardChanged(self, idx):
        self.card = self.model.cardModels[idx]
        self.readCard()
        self.drawQuestionAndAnswer()

    def readCard(self):
        card = self.card
        self.form.background.setPalette(QPalette(QColor(
            getattr(card, "lastFontColour"))))
        self.form.cardQuestion.setPlainText(self.formatToScreen(card.qformat))
        self.form.cardAnswer.setPlainText(self.formatToScreen(card.aformat))
        self.form.questionInAnswer.setChecked(card.questionInAnswer)
        self.form.allowEmptyAnswer.setChecked(card.allowEmptyAnswer)
        self.form.typeAnswer.clear()
        self.fieldNames = self.deck.s.column0("""
select fieldModels.name as n from fieldModels, cardModels
where cardModels.modelId = fieldModels.modelId
and cardModels.id = :id
order by n""", id=card.id)
        s = [_("Don't ask me to type in the answer")]
        s += [_("Compare with field '%s'") % f for f in self.fieldNames]
        self.form.typeAnswer.insertItems(0, QStringList(s))
        try:
            idx = self.fieldNames.index(card.typeAnswer)
        except ValueError:
            idx = -1
        self.form.typeAnswer.setCurrentIndex(idx + 1)

    def saveCard(self):
        if not self.card:
            return
        self.card.questionAlign = self.form.align.currentIndex()
        setattr(self.card, "lastFontColour", unicode(
            self.form.backgroundColour.palette().window().color().name()))
        self.card.model.setModified()
        self.deck.setModified()

        self.drawQuestionAndAnswer()

    def cwidget(self, name, type):
        "Return a card widget."
        return getattr(self.form, type + name)

    def setupFields(self):
        self.connect(self.form.fieldList, SIGNAL("currentRowChanged(int)"),
                     self.fieldChanged)
        for type in ("quiz", "edit"):
            self.connect(self.fwidget("fontFamily", type),
                         SIGNAL("currentFontChanged(QFont)"),
                         self.saveField)
            self.connect(self.fwidget("fontSize", type),
                         SIGNAL("valueChanged(int)"),
                         self.saveField)
            self.connect(self.fwidget("useFamily", type),
                         SIGNAL("stateChanged(int)"),
                         self.saveField)
            self.connect(self.fwidget("useSize", type),
                         SIGNAL("stateChanged(int)"),
                         self.saveField)
            if type == "quiz":
                self.connect(self.fwidget("useColour", type),
                             SIGNAL("stateChanged(int)"),
                             self.saveField)
                w = self.fwidget("fontColour", type)
                if self.plastiqueStyle:
                    w.setStyle(self.plastiqueStyle)
                self.connect(w,
                             SIGNAL("clicked()"),
                             lambda w=w: self.chooseColour(w))
            elif type == "edit":
                self.connect(self.form.rtl,
                             SIGNAL("stateChanged(int)"),
                             self.saveField)
        self.currentField = None
        self.drawFields()

    def drawFields(self):
        self.form.fieldList.clear()
        n = 1
        self.ignoreUpdate = True
        for field in self.model.fieldModels:
            item = QListWidgetItem(
                _("Field %(num)d: %(name)s") % {
                'num': n,
                'name': field.name,
                })
            self.form.fieldList.addItem(item)
            n += 1
        self.form.fieldList.setCurrentRow(0)
        self.fieldChanged(0)
        self.ignoreUpdate = False

    def fwidget(self, name, type):
        "Return a field widget."
        if type == "edit":
            return getattr(self.form, name+"Edit")
        else:
            return getattr(self.form, name)

    def fieldChanged(self, idx):
        self.saveField()
        self.currentField = None
        field = self.model.fieldModels[idx]
        for type in ("quiz", "edit"):
            # family
            if getattr(field, type + 'FontFamily'):
                self.fwidget("useFamily", type).setCheckState(Qt.Checked)
                self.fwidget("fontFamily", type).setCurrentFont(QFont(
                    getattr(field, type + 'FontFamily')))
                self.fwidget("fontFamily", type).setEnabled(True)
            else:
                self.fwidget("useFamily", type).setCheckState(Qt.Unchecked)
                self.fwidget("fontFamily", type).setEnabled(False)
            # size
            if getattr(field, type + 'FontSize'):
                self.fwidget("useSize", type).setCheckState(Qt.Checked)
                self.fwidget("fontSize", type).setValue(
                    getattr(field, type + 'FontSize'))
                self.fwidget("fontSize", type).setEnabled(True)
            else:
                self.fwidget("useSize", type).setCheckState(Qt.Unchecked)
                self.fwidget("fontSize", type).setEnabled(False)
            if type == "quiz":
                # colour
                if getattr(field, type + 'FontColour'):
                    self.fwidget("useColour", type).setCheckState(Qt.Checked)
                    self.fwidget("fontColour", type).setPalette(QPalette(QColor(
                        getattr(field, type + 'FontColour'))))
                    self.fwidget("fontColour", type).setEnabled(True)
                else:
                    self.fwidget("useColour", type).setCheckState(Qt.Unchecked)
                    self.fwidget("fontColour", type).setEnabled(False)
            elif type == "edit":
                self.form.rtl.setChecked(not not field.features)
        self.currentField = field

    def saveField(self, *args):
        if self.ignoreUpdate:
            return
        field = self.currentField
        if not field:
            return
        for type in ("quiz", "edit"):
            # family
            if self.fwidget("useFamily", type).isChecked():
                setattr(field, type + 'FontFamily', toCanonicalFont(unicode(
                    self.fwidget("fontFamily", type).currentFont().family())))
            else:
                setattr(field, type + 'FontFamily', None)
            # size
            if self.fwidget("useSize", type).isChecked():
                setattr(field, type + 'FontSize',
                        int(self.fwidget("fontSize", type).value()))
            else:
                setattr(field, type + 'FontSize', None)
            # colour
            if type == "quiz":
                if self.fwidget("useColour", type).isChecked():
                    w = self.fwidget("fontColour", type)
                    c = w.palette().window().color()
                    setattr(field, type + 'FontColour', str(c.name()))
                else:
                    setattr(field, type + 'FontColour', None)
            elif type == "edit":
                if self.form.rtl.isChecked():
                    field.features = u"rtl"
                else:
                    field.features = u""
        field.model.setModified()
        self.deck.flushMod()
        self.drawQuestionAndAnswer()

    def chooseColour(self, button):
        new = QColorDialog.getColor(button.palette().window().color(), self)
        if new.isValid():
            button.setPalette(QPalette(new))
            self.saveField()
            self.saveCard()

    def drawQuestionAndAnswer(self):
        print "draw qa"
        return
        self.deck.flushMod()
        f = self.deck.newFact()
        f.tags = u""
        for field in f.fields:
            f[field.name] = field.name
        f.model = self.model
        c = Card(f, self.card)
        t = "<body><br><center>" + c.htmlQuestion() + "</center></body>"
        bg = "body { background-color: %s; }\n" % self.card.lastFontColour
        self.form.question.setText(
            "<style>\n" + bg + self.deck.rebuildCSS() + "</style>\n" + t)
        t = "<body><br><center>" + c.htmlAnswer() + "</center></body>"
        self.form.answer.setText(
            "<style>\n" + bg + self.deck.rebuildCSS() + "</style>\n" + t)
        self.mw.updateViews(self.mw.state)


    def reject(self):
        saveGeom(self, "CardLayout")
        QDialog.reject(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "DisplayProperties"))

    # Fields
    ##########################################################################

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

#     def __init__(self, parent, deck, fact, *args):
#         QDialog.__init__(self, parent, *args)
#         self.deck = deck
#         self.fact = fact
#         cards = self.deck.previewFact(self.fact)
#         if not cards:
#             ui.utils.showInfo(_("No cards to preview."),
#                               parent=parent)
#             return
#         self.cards = cards
#         self.currentCard = 0
#         self.form = ankiqt.forms.previewcards.Ui_Dialog()
#         self.form.setupUi(self)
#         self.form.webView.page().setLinkDelegationPolicy(
#             QWebPage.DelegateExternalLinks)
#         self.connect(self.form.webView,
#                      SIGNAL("linkClicked(QUrl)"),
#                      self.linkClicked)
#         self.form.comboBox.addItems(QStringList(
#             [c.cardModel.name for c in self.cards]))
#         self.connect(self.form.comboBox, SIGNAL("activated(int)"),
#                      self.onChange)
#         self.updateCard()
#         restoreGeom(self, "preview")
#         self.exec_()

#     def linkClicked(self, url):
#         QDesktopServices.openUrl(QUrl(url))

#     def updateCard(self):
#         c = self.cards[self.currentCard]
#         styles = (self.deck.css +
#                   ("\nhtml { background: %s }" % c.cardModel.lastFontColour) +
#                   "\ndiv { white-space: pre-wrap; }")
#         styles = runFilter("addStyles", styles, c)
#         self.form.webView.setHtml(
#             ('<html><head>%s</head><body>' % getBase(self.deck, c)) +
#             "<style>" + styles + "</style>" +
#             runFilter("drawQuestion", mungeQA(self.deck, c.htmlQuestion()),
#                       c) +
#             "<br><br><hr><br><br>" +
#             runFilter("drawAnswer", mungeQA(self.deck, c.htmlAnswer()),
#                       c)
#             + "</body></html>")
#         clearAudioQueue()
#         playFromText(c.question)
#         playFromText(c.answer)

#     def onChange(self, idx):
#         self.currentCard = idx
#         self.updateCard()

#     def reject(self):
#         saveGeom(self, "preview")
#         QDialog.reject(self)
