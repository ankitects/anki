# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtWebKit import QWebPage, QWebView
import re
from anki.consts import *
import aqt
from anki.sound import playFromText, clearAudioQueue
from aqt.utils import saveGeom, restoreGeom, getBase, mungeQA, \
     saveSplitter, restoreSplitter, showInfo, isMac, isWin, askUser, \
     getText
import aqt.templates

# fixme: replace font substitutions with native comma list

class ResizingTextEdit(QTextEdit):
    def sizeHint(self):
        return QSize(200, 800)

class CardLayout(QDialog):

    # type is previewCards() type
    def __init__(self, mw, fact, type=0, ord=0, parent=None):
        QDialog.__init__(self, parent or mw, Qt.Window)
        self.mw = aqt.mw
        self.parent = parent or mw
        self.fact = fact
        self.type = type
        self.ord = ord
        self.deck = self.mw.deck
        self.model = fact.model()
        self.form = aqt.forms.clayout.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("%s Layout") % self.model.name)
        self.plastiqueStyle = None
        if isMac or isWin:
            self.plastiqueStyle = QStyleFactory.create("plastique")
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     self.onHelp)
        self.setupCards()
        self.setupFields()
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        restoreSplitter(self.form.splitter, "clayout")
        restoreGeom(self, "CardLayout")
        if not self.reload(first=True):
            return
        self.exec_()

    def reload(self, first=False):
        self.cards = self.deck.previewCards(self.fact, self.type)
        if not self.cards:
            self.accept()
            if first:
                showInfo(_("Please enter some text first."))
            else:
                showInfo(_("The current fact was deleted."))
            return
        self.fillCardList()
        self.fillFieldList()
        self.fieldChanged()
        self.readField()
        return True

    # Cards & Preview
    ##########################################################################

    def setupCards(self):
        self.updatingCards = False
        self.playedAudio = {}
        f = self.form
        if self.type == 0:
            f.templateType.setText(
                _("Templates that will be created:"))
        elif self.type == 1:
            f.templateType.setText(
                _("Templates used by fact:"))
        else:
            f.templateType.setText(
                _("All templates:"))
        # replace with more appropriate size hints
        for e in ("cardQuestion", "cardAnswer"):
            w = getattr(f, e)
            idx = f.templateLayout.indexOf(w)
            r = f.templateLayout.getItemPosition(idx)
            f.templateLayout.removeWidget(w)
            w.hide()
            w.deleteLater()
            w = ResizingTextEdit(self)
            setattr(f, e, w)
            f.templateLayout.addWidget(w, r[0], r[1])
        c = self.connect
        c(f.cardList, SIGNAL("activated(int)"), self.cardChanged)
        c(f.editTemplates, SIGNAL("clicked()"), self.onEdit)
        c(f.cardQuestion, SIGNAL("textChanged()"), self.formatChanged)
        c(f.cardAnswer, SIGNAL("textChanged()"), self.formatChanged)
        c(f.alignment, SIGNAL("activated(int)"), self.saveCard)
        c(f.background, SIGNAL("clicked()"),
                     lambda w=f.background:\
                     self.chooseColour(w, "card"))
        c(f.questionInAnswer, SIGNAL("clicked()"), self.saveCard)
        c(f.allowEmptyAnswer, SIGNAL("clicked()"), self.saveCard)
        c(f.typeAnswer, SIGNAL("activated(int)"), self.saveCard)
        c(f.flipButton, SIGNAL("clicked()"), self.onFlip)
        def linkClicked(url):
            QDesktopServices.openUrl(QUrl(url))
        f.preview.page().setLinkDelegationPolicy(
            QWebPage.DelegateExternalLinks)
        self.connect(f.preview,
                     SIGNAL("linkClicked(QUrl)"),
                     linkClicked)
        if self.plastiqueStyle:
            f.background.setStyle(self.plastiqueStyle)
        f.alignment.addItems(
                QStringList(alignmentLabels().values()))
        self.typeFieldNames = self.model.fieldMap()
        s = [_("Don't ask me to type in the answer")]
        s += [_("Compare with field '%s'") % fi
              for fi in self.typeFieldNames.keys()]
        f.typeAnswer.insertItems(0, QStringList(s))

    def formatToScreen(self, fmt):
        fmt = fmt.replace("}}<br>", "}}\n")
        return fmt

    def screenToFormat(self, fmt):
        fmt = fmt.replace("}}\n", "}}<br>")
        return fmt

    def onEdit(self):
        aqt.templates.Templates(self.mw, self.model, self)
        self.reload()

    def formatChanged(self):
        if self.updatingCards:
            return
        text = unicode(self.form.cardQuestion.toPlainText())
        self.card.template()['qfmt'] = self.screenToFormat(text)
        text = unicode(self.form.cardAnswer.toPlainText())
        self.card.template()['afmt'] = self.screenToFormat(text)
        self.renderPreview()

    def onFlip(self):
        q = unicode(self.form.cardQuestion.toPlainText())
        a = unicode(self.form.cardAnswer.toPlainText())
        self.form.cardAnswer.setPlainText(q)
        self.form.cardQuestion.setPlainText(a)

    def readCard(self):
        self.updatingCards = True
        t = self.card.template()
        f = self.form
        f.background.setPalette(QPalette(QColor(t['bg'])))
        f.cardQuestion.setPlainText(self.formatToScreen(t['qfmt']))
        f.cardAnswer.setPlainText(self.formatToScreen(t['afmt']))
        f.questionInAnswer.setChecked(t['hideQ'])
        f.allowEmptyAnswer.setChecked(t['emptyAns'])
        f.alignment.setCurrentIndex(t['align'])
        if t['typeAns'] is None:
            f.typeAnswer.setCurrentIndex(0)
        else:
            f.typeAnswer.setCurrentIndex(t['typeAns'] + 1)
        self.updatingCards = False

    def fillCardList(self):
        self.form.cardList.clear()
        cards = []
        idx = 0
        for n, c in enumerate(self.cards):
            if c.ord == self.ord:
                cards.append(_("%s (current)") % c.template()['name'])
                idx = n
            else:
                cards.append(c.template()['name'])
        self.form.cardList.addItems(
            QStringList(cards))
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
        t = self.card.template()
        t['align'] = self.form.alignment.currentIndex()
        t['bg'] = unicode(
            self.form.background.palette().window().color().name())
        t['hideQ'] = self.form.questionInAnswer.isChecked()
        t['emptyAns'] = self.form.allowEmptyAnswer.isChecked()
        idx = self.form.typeAnswer.currentIndex()
        if not idx:
            t['typeAns'] = None
        else:
            t['typeAns'] = idx-1
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
        styles = self.model.genCSS()
        self.form.preview.setHtml(
            ('<html><head>%s</head><body class="%s">' %
             (getBase(self.deck), c.cssClass())) +
            "<style>" + styles + "</style>" +
            mungeQA(c.q(reload=True)) +
            self.maybeTextInput() +
            "<hr>" +
            mungeQA(c.a())
            + "</body></html>")
        clearAudioQueue()
        if c.id not in self.playedAudio:
            playFromText(c.q())
            playFromText(c.a())
            self.playedAudio[c.id] = True

    def maybeTextInput(self):
        if self.card.template()['typeAns'] is not None:
            return "<center><input type=text></center>"
        return ""

    def accept(self):
        self.reject()

    def reject(self):
        self.model.flush()
        saveGeom(self, "CardLayout")
        saveSplitter(self.form.splitter, "clayout")
        self.mw.reset()
        return QDialog.reject(self)

        self.fact.model.setModified()

        modified = False
        self.mw.startProgress()
        self.deck.updateProgress(_("Applying changes..."))
        reset=True
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
        QDialog.reject(self)

    def onHelp(self):
        aqt.openHelp("CardLayout")

    # Fields
    ##########################################################################

    def setupFields(self):
        self.fieldOrdinalUpdatedIds = []
        self.updatingFields = False
        self.needFieldRebuild = False
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
                     self.saveField)
        self.connect(self.form.fontFamily, SIGNAL("currentFontChanged(QFont)"),
                     self.saveField)
        self.connect(self.form.fontSize, SIGNAL("valueChanged(int)"),
                     self.saveField)
        self.connect(self.form.fontSizeEdit, SIGNAL("valueChanged(int)"),
                     self.saveField)
        self.connect(self.form.preserveWhitespace, SIGNAL("stateChanged(int)"),
                     self.saveField)
        self.connect(self.form.fieldUnique, SIGNAL("stateChanged(int)"),
                     self.saveField)
        self.connect(self.form.fieldRequired, SIGNAL("stateChanged(int)"),
                     self.saveField)
        w = self.form.fontColour
        if self.plastiqueStyle:
            w.setStyle(self.plastiqueStyle)
        self.connect(w, SIGNAL("clicked()"),
                     lambda w=w: self.chooseColour(w))
        self.connect(self.form.rtl,
                     SIGNAL("stateChanged(int)"),
                     self.saveField)

    def fieldChanged(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            row = 0
        self.field = self.model.fields[row]
        self.readField()
        self.enableFieldMoveButtons()

    def readField(self):
        fld = self.field
        f = self.form
        self.updatingFields = True
        f.fieldName.setText(fld['name'])
        f.fieldUnique.setChecked(fld['uniq'])
        f.fieldRequired.setChecked(fld['req'])
        f.fontFamily.setCurrentFont(QFont(fld['font']))
        f.fontSize.setValue(fld['qsize'])
        f.fontSizeEdit.setValue(fld['esize'])
        f.fontColour.setPalette(QPalette(QColor(fld['qcol'])))
        f.rtl.setChecked(fld['rtl'])
        f.preserveWhitespace.setChecked(fld['pre'])
        self.updatingFields = False

    def saveField(self, *args):
        self.needFieldRebuild = True
        if self.updatingFields:
            return
        self.updatingFields = True
        fld = self.field
        # get name; we'll handle it last
        name = unicode(self.form.fieldName.text())
        if not name:
            return
        fld['uniq'] = self.form.fieldUnique.isChecked()
        fld['req'] = self.form.fieldRequired.isChecked()
        fld['font'] = unicode(
            self.form.fontFamily.currentFont().family())
        fld['qsize'] = self.form.fontSize.value()
        fld['esize'] = self.form.fontSizeEdit.value()
        fld['qcol'] = str(
            self.form.fontColour.palette().window().color().name())
        fld['rtl'] = self.form.rtl.isChecked()
        fld['pre'] = self.form.preserveWhitespace.isChecked()
        self.updatingFields = False
        if fld['name'] != name:
            self.model.renameField(fld, name)
            # as the field name has changed, we have to regenerate cards
            self.cards = self.deck.previewCards(self.fact, self.type)
            self.cardChanged(0)
        self.renderPreview()
        self.fillFieldList()

    def fillFieldList(self, row = None):
        oldRow = self.form.fieldList.currentRow()
        if oldRow == -1:
            oldRow = 0
        self.form.fieldList.clear()
        n = 1
        for field in self.model.fields:
            label = field['name']
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
        f = self.model.newField()
        l = len(self.model.fields)
        f['name'] = _("Field %d") % l
        self.mw.progress.start()
        self.model.addField(f)
        self.mw.progress.finish()
        self.reload()
        self.form.fieldList.setCurrentRow(l)
        self.form.fieldName.setFocus()
        self.form.fieldName.selectAll()

    def deleteField(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if len(self.model.fields) < 2:
            showInfo(_("Please add a new field first."))
            return
        if askUser(_("Delete this field and its data from all facts?")):
            self.mw.progress.start()
            self.model.delField(self.field)
            self.mw.progress.finish()
        # need to update q/a format
        self.reload()

    def moveFieldUp(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if row == 0:
            return
        self.mw.progress.start()
        self.model.moveField(self.field, row-1)
        self.mw.progress.finish()
        self.form.fieldList.setCurrentRow(row-1)
        self.reload()

    def moveFieldDown(self):
        row = self.form.fieldList.currentRow()
        if row == -1:
            return
        if row == len(self.model.fields) - 1:
            return
        self.mw.progress.start()
        self.model.moveField(self.field, row+1)
        self.mw.progress.finish()
        self.form.fieldList.setCurrentRow(row+1)
        self.reload()
