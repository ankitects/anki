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
from ankiqt.ui.utils import saveGeom, restoreGeom, getBase, mungeQA, \
     saveSplitter, restoreSplitter
from anki.hooks import runFilter
from ankiqt import ui

class ResizingTextEdit(QTextEdit):
    def sizeHint(self):
        return QSize(200, 800)

class CardLayout(QDialog):

    def __init__(self, parent, factedit, factOrModel, card=None):
        self.parent = parent
        QDialog.__init__(self, parent, Qt.Window)
        self.mw = ankiqt.mw
        self.deck = self.mw.deck
        self.factedit = factedit
        self.card = card
        if factedit is not None:
            self.fact = factOrModel
            self.model = self.fact.model
        else:
            self.model = factOrModel
            # see if there's an available fact
            id = self.deck.s.scalar(
                "select id from facts where modelId = :id", id=self.model.id)
            if id:
                self.fact = self.deck.s.query(Fact).get(id)
            else:
                # generate a dummy one
                self.fact = self.deck.newFact(self.model)
                for f in self.fact.keys():
                    self.fact[f] = f
        self.plastiqueStyle = None
        if (sys.platform.startswith("darwin") or
            sys.platform.startswith("win32")):
            self.plastiqueStyle = QStyleFactory.create("plastique")
        if self.card:
            # limited to an existing template
            self.cards = [self.deck.s.query(Card).get(id) for id in
                          self.deck.s.column0(
                "select id from cards where factId = :fid "
                "order by ordinal", fid=self.fact.id)]
            type = 0
        else:
            if factedit:
                # active & possible
                self.cards = self.deck.previewFact(self.fact)
                type = 1
            else:
                # all
                self.cards = self.deck.previewFact(self.fact, cms=self.model.cardModels)
                type = 2
            if not self.cards:
                ui.utils.showInfo(_(
                    "Please enter some text first."),
                                  parent=self.parent)
                return
        self.form = ankiqt.forms.clayout.Ui_Dialog()
        self.form.setupUi(self)
        restoreSplitter(self.form.splitter, "clayout")
        if type == 0:
            self.form.templateType.setText(
                _("Templates used by fact:"))
        elif type == 1:
            self.form.templateType.setText(
                _("Templates that will be created:"))
        else:
            self.form.templateType.setText(
                _("All templates:"))
        # FIXME: add this
        self.form.editTemplates.hide()
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.setupCards()
        self.setupFields()
        restoreGeom(self, "CardLayout")
        # hack to ensure we're focused on the active template in the model
        # properties
        if type == 2 and factOrModel.currentCard.ordinal != 0:
            idx = factOrModel.currentCard.ordinal
            self.form.cardList.setCurrentIndex(idx)
            self.cardChanged(idx)
        self.exec_()

    # Cards & Preview
    ##########################################################################

    def setupCards(self):
        self.needFormatRebuild = False
        self.updatingCards = False
        self.playedAudio = {}
        # replace with more appropriate size hints
        for e in ("cardQuestion", "cardAnswer"):
            w = getattr(self.form, e)
            idx = self.form.templateLayout.indexOf(w)
            r = self.form.templateLayout.getItemPosition(idx)
            self.form.templateLayout.removeWidget(w)
            w.hide()
            w.deleteLater()
            w = ResizingTextEdit(self)
            setattr(self.form, e, w)
            self.form.templateLayout.addWidget(w, r[0], r[1])
        self.connect(self.form.cardList, SIGNAL("activated(int)"),
                     self.cardChanged)
        # self.connect(self.form.editTemplates, SIGNAL("clicked())"),
        #              self.onEdit)
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
        def linkClicked(url):
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
        return fmt

    # def onEdit(self):
    #     ui.modelproperties.ModelProperties(
    #         self, self.deck, self.model, self.mw,
    #         onFinish=self.updateModelsList)

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
            qa = formatQA(None, self.fact.modelId, d, card.splitTags(),
                          card.cardModel, self.deck)
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
        self.form.alignment.setCurrentIndex(card.questionAlign)
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

    def fillCardList(self):
        self.form.cardList.clear()
        cards = []
        idx = 0
        for n, c in enumerate(self.cards):
            if c == self.card:
                cards.append(_("%s (current)") % c.cardModel.name)
                idx = n
            else:
                cards.append(c.cardModel.name)
        self.form.cardList.addItems(
            QStringList(cards))
        self.form.editTemplates.setEnabled(False)
        if idx != 0:
            self.form.cardList.setCurrentIndex(idx)
        self.cardChanged(idx)
        self.form.cardList.setFocus()

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
        new = QColorDialog.getColor(button.palette().window().color(), self,
                                    _("Choose Color"),
                                    QColorDialog.DontUseNativeDialog)
        if new.isValid():
            button.setPalette(QPalette(new))
            if type == "field":
                self.saveField()
            else:
                self.saveCard()

    def renderPreview(self):
        c = self.card
        styles = (self.deck.rebuildCSS() +
                  ("\nhtml { background: %s }" % c.cardModel.lastFontColour))
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
        if c.id not in self.playedAudio:
            playFromText(c.question)
            playFromText(c.answer)
            self.playedAudio[c.id] = True

    def reject(self):
        modified = False
        self.deck.startProgress()
        self.deck.updateProgress(_("Applying changes..."))
        reset=True
        if self.needFormatRebuild:
            # need to generate q/a templates
            self.deck.updateCardsFromModel(self.fact.model)
            self.deck.finishProgress()
            modified = True
        if len(self.fieldOrdinalUpdatedIds) > 0:
            self.deck.rebuildFieldOrdinals(self.model.id, self.fieldOrdinalUpdatedIds)
            modified = True
        if self.needFieldRebuild:
            modified = True
        if modified:
            self.fact.model.setModified()
            self.deck.flushMod()
            if self.factedit and self.factedit.onChange:
                self.factedit.onChange("all")
                reset=False
        if reset:
            self.mw.reset()
        self.deck.finishProgress()
        saveGeom(self, "CardLayout")
        saveSplitter(self.form.splitter, "clayout")
        QDialog.reject(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "CardLayout"))

    # Fields
    ##########################################################################

    def setupFields(self):
        self.fieldOrdinalUpdatedIds = []
        self.updatingFields = False
        self.needFieldRebuild = False
        self.fillFieldList()
        self.fieldChanged(0)
        self.readField()
        self.connect(self.form.fieldList, SIGNAL("currentRowChanged(int)"),
                     self.fieldChanged)
        self.connect(self.form.fieldAdd, SIGNAL("clicked()"),
                     self.addField)
        self.connect(self.form.fieldDelete, SIGNAL("clicked()"),
                     self.deleteField)
        self.connect(self.form.fieldUp, SIGNAL("clicked()"),
                     self.moveFieldUp)
        self.connect(self.form.fieldDown, SIGNAL("clicked()"),
                     self.moveFieldDown)
        self.connect(self.form.fieldName, SIGNAL("lostFocus()"),
                     self.fillFieldList)
        self.connect(self.form.fontFamily, SIGNAL("currentFontChanged(QFont)"),
                     self.saveField)
        self.connect(self.form.fontSize, SIGNAL("valueChanged(int)"),
                     self.saveField)
        self.connect(self.form.fontSizeEdit, SIGNAL("valueChanged(int)"),
                     self.saveField)
        self.connect(self.form.fieldName, SIGNAL("textEdited(QString)"),
                     self.saveField)
        self.connect(self.form.preserveWhitespace, SIGNAL("stateChanged(int)"),
                     self.saveField)
        self.connect(self.form.fieldUnique, SIGNAL("stateChanged(int)"),
                     self.saveField)
        self.connect(self.form.fieldRequired, SIGNAL("stateChanged(int)"),
                     self.saveField)
        self.connect(self.form.numeric, SIGNAL("stateChanged(int)"),
                     self.saveField)
        w = self.form.fontColour
        if self.plastiqueStyle:
            w.setStyle(self.plastiqueStyle)
        self.connect(w, SIGNAL("clicked()"),
                     lambda w=w: self.chooseColour(w))
        self.connect(self.form.rtl,
                     SIGNAL("stateChanged(int)"),
                     self.saveField)

    def fieldChanged(self, idx):
        if self.updatingFields:
            return
        self.field = self.model.fieldModels[idx]
        self.readField()
        self.enableFieldMoveButtons()

    def readField(self):
        field = self.field
        self.updatingFields = True
        self.form.fieldName.setText(field.name)
        self.form.fieldUnique.setChecked(field.unique)
        self.form.fieldRequired.setChecked(field.required)
        self.form.numeric.setChecked(field.numeric)
        if not field.quizFontFamily:
            # backwards compat
            field.quizFontFamily = u"Arial"
        self.form.fontFamily.setCurrentFont(QFont(
            field.quizFontFamily))
        self.form.fontSize.setValue(field.quizFontSize or 20)
        self.form.fontSizeEdit.setValue(field.editFontSize or 20)
        self.form.fontColour.setPalette(QPalette(QColor(
                        field.quizFontColour or "#000000")))
        self.form.rtl.setChecked(not not field.features)
        self.form.preserveWhitespace.setChecked(not not field.editFontFamily)
        self.updatingFields = False

    def saveField(self, *args):
        self.needFieldRebuild = True
        if self.updatingFields:
            return
        self.updatingFields = True
        field = self.field
        name = unicode(self.form.fieldName.text()) or _("Field")
        if field.name != name:
            oldVal = self.fact[field.name]
            self.deck.renameFieldModel(self.model, field, name)
            # the card models will have been updated
            self.readCard()
            # for add card case
            self.updateFact()
            self.fact[name] = oldVal
        field.unique = self.form.fieldUnique.isChecked()
        field.required = self.form.fieldRequired.isChecked()
        field.numeric = self.form.numeric.isChecked()
        field.quizFontFamily = toCanonicalFont(unicode(
            self.form.fontFamily.currentFont().family()))
        field.quizFontSize = int(self.form.fontSize.value())
        field.editFontSize = int(self.form.fontSizeEdit.value())
        field.quizFontColour = str(
            self.form.fontColour.palette().window().color().name())
        if self.form.rtl.isChecked():
            field.features = u"rtl"
        else:
            field.features = u""
        if self.form.preserveWhitespace.isChecked():
            field.editFontFamily = u"preserve"
        else:
            field.editFontFamily = u""
        field.model.setModified()
        self.deck.flushMod()
        self.renderPreview()
        self.fillFieldList()
        self.updatingFields = False

    def fillFieldList(self, row = None):
        oldRow = self.form.fieldList.currentRow()
        if oldRow == -1:
            oldRow = 0
        self.form.fieldList.clear()
        n = 1
        for field in self.model.fieldModels:
            label = field.name
            item = QListWidgetItem(label)
            self.form.fieldList.addItem(item)
            n += 1
        count = self.form.fieldList.count()
        if row != None:
            self.form.fieldList.setCurrentRow(row)
        else:
            while (count > 0 and oldRow > (count - 1)):
                    oldRow -= 1
            self.form.fieldList.setCurrentRow(oldRow)
        self.enableFieldMoveButtons()

    def enableFieldMoveButtons(self):
        row = self.form.fieldList.currentRow()
        if row < 1:
            self.form.fieldUp.setEnabled(False)
        else:
            self.form.fieldUp.setEnabled(True)
        if row == -1 or row >= (self.form.fieldList.count() - 1):
            self.form.fieldDown.setEnabled(False)
        else:
            self.form.fieldDown.setEnabled(True)

    def addField(self):
        f = FieldModel(required=False, unique=False)
        f.name = _("Field %d") % (len(self.model.fieldModels) + 1)
        self.deck.addFieldModel(self.model, f)
        try:
            self.deck.s.refresh(self.fact)
        except:
            # not yet added
            self.updateFact()
        self.fillFieldList()
        self.form.fieldList.setCurrentRow(len(self.model.fieldModels)-1)
        self.form.fieldName.setFocus()
        self.form.fieldName.selectAll()

    def updateFact(self):
        oldFact = self.fact
        model = self.deck.s.query(Model).get(oldFact.model.id)
        fact = self.deck.newFact(model)
        for field in fact.fields:
            try:
                fact[field.name] = oldFact[field.name]
            except KeyError:
                fact[field.name] = u""
        fact.tags = oldFact.tags
        self.fact = fact

    def deleteField(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if len(self.model.fieldModels) < 2:
            ui.utils.showInfo(
                _("Please add a new field first."),
                parent=self)
            return
        field = self.model.fieldModels[row]
        count = self.deck.fieldModelUseCount(field)
        if count:
            if not ui.utils.askUser(
                _("This field is used by %d cards. If you delete it,\n"
                  "all information in this field will be lost.\n"
                  "\nReally delete this field?") % count,
                parent=self):
                return
        self.deck.deleteFieldModel(self.model, field)
        self.fillFieldList()
        # need to update q/a format
        self.readCard()

    def moveFieldUp(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        field = self.model.fieldModels[row]
        tField = self.model.fieldModels[row - 1]
        self.model.fieldModels.remove(field)
        self.model.fieldModels.insert(row - 1, field)
        if field.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(field.id)
        if tField.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(tField.id)
        self.fillFieldList(row - 1)

    def moveFieldDown(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if row == len(self.model.fieldModels) - 1:
            return
        field = self.model.fieldModels[row]
        tField = self.model.fieldModels[row + 1]
        self.model.fieldModels.remove(field)
        self.model.fieldModels.insert(row + 1, field)
        if field.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(field.id)
        if tField.id not in self.fieldOrdinalUpdatedIds:
            self.fieldOrdinalUpdatedIds.append(tField.id)
        self.fillFieldList(row + 1)
