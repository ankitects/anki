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
from ankiqt import ui

class DisplayProperties(QDialog):

    def __init__(self, parent, main=None):
        QDialog.__init__(self, parent, Qt.Window)
        if not main:
            main = parent
        self.parent = parent
        self.main = main
        self.deck = main.deck
        self.ignoreUpdate = False
        self.plastiqueStyle = None
        if (sys.platform.startswith("darwin") or
            sys.platform.startswith("win32")):
            self.plastiqueStyle = QStyleFactory.create("plastique")
        self.dialog = ankiqt.forms.displayproperties.Ui_DisplayProperties()
        self.dialog.setupUi(self)
        self.model = self.deck.currentModel
        self.setupChooser()
        self.setupCards()
        self.setupFields()
        self.setupButtons()
        self.show()
        ui.dialogs.open("DisplayProperties", self)

    def setupChooser(self):
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.main,
                                                         self.main.deck,
                                                         self.modelChanged,
                                                         cards=False)
        self.dialog.modelArea.setLayout(self.modelChooser)

    def modelChanged(self, model):
        self.model = model
        self.drawCards()
        self.drawFields()

    def setupButtons(self):
        self.connect(self.dialog.preview, SIGNAL("clicked()"),
                     self.previewClicked)
        self.connect(self.dialog.helpButton, SIGNAL("clicked()"),
                     self.onHelp)
        if self.main.config['showFontPreview']:
            self.dialog.preview.setChecked(True)
        else:
            self.dialog.preview.setChecked(False)
        self.previewClicked()

    def previewClicked(self):
        if self.dialog.preview.isChecked():
            self.dialog.previewGroup.show()
            self.setMaximumWidth(16777215)
            self.setMinimumWidth(790)
            self.main.config['showFontPreview'] = True
        else:
            self.dialog.previewGroup.hide()
            self.setFixedWidth(340)
            self.main.config['showFontPreview'] = False

    def setupCards(self):
        self.connect(self.dialog.cardList, SIGNAL("activated(int)"),
                     self.cardChanged)
        for type in ("question", "answer"):
            self.connect(self.cwidget("Font", type),
                         SIGNAL("currentFontChanged(QFont)"),
                         self.saveCard)
            self.connect(self.cwidget("Size", type),
                         SIGNAL("valueChanged(int)"),
                         self.saveCard)
            w = self.cwidget("Colour", type)
            if self.plastiqueStyle:
                w.setStyle(self.plastiqueStyle)
            self.connect(w,
                         SIGNAL("clicked()"),
                         lambda w=w, t=type: self.chooseColour(w, t))
            self.connect(self.cwidget("Align", type),
                         SIGNAL("activated(int)"),
                         self.saveCard)
        # set the background colour to the current system-wide background colour
        p = QPalette()
        p.setColor(QPalette.Base, QColor(self.main.config['backgroundColour']))
        self.dialog.question.setPalette(p)
        self.dialog.answer.setPalette(p)
        self.drawCards()

    def drawCards(self):
        self.dialog.cardList.clear()
        self.dialog.cardList.addItems(
            QStringList([c.name for c in self.model.cardModels]))
        for t in ("question", "answer"):
            self.cwidget("Align", t).clear()
            self.cwidget("Align", t).addItems(
                QStringList(alignmentLabels().values()))
        self.cardChanged(0)

    def cardChanged(self, idx):
        self.card = self.model.cardModels[idx]
        self.readCard()
        self.drawQuestionAndAnswer()

    def readCard(self):
        card = self.card
        self.card = None
        for type in ("question", "answer"):
            self.cwidget("Font", type).setCurrentFont(QFont(
                getattr(card, type + "FontFamily")))
            self.cwidget("Size", type).setValue(
                getattr(card, type + "FontSize"))
            self.cwidget("Colour", type).setPalette(QPalette(QColor(
                getattr(card, type + "FontColour"))))
            self.cwidget("Align", type).setCurrentIndex(
                getattr(card, type + "Align"))
        self.card = card

    def saveCard(self):
        if not self.card:
            return
        for type in ("question", "answer"):
            setattr(self.card, type + "FontFamily", toCanonicalFont(unicode(
                self.cwidget("Font", type).currentFont().family())))
            setattr(self.card, type + "FontSize", int(
                self.cwidget("Size", type).value()))
            setattr(self.card, type + "Align", int(
                self.cwidget("Align", type).currentIndex()))
            w = self.cwidget("Colour", type)
            c = w.palette().window().color()
            setattr(self.card, type + "FontColour", unicode(c.name()))
            self.card.model.setModified()
            self.deck.setModified()
        self.drawQuestionAndAnswer()

    def cwidget(self, name, type):
        "Return a card widget."
        return getattr(self.dialog, type + name)

    def setupFields(self):
        self.connect(self.dialog.fieldList, SIGNAL("currentRowChanged(int)"),
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
                             lambda w=w, t=type: self.chooseColour(w, t))
        self.currentField = None
        self.drawFields()

    def drawFields(self):
        self.dialog.fieldList.clear()
        n = 1
        self.ignoreUpdate = True
        for field in self.model.fieldModels:
            item = QListWidgetItem(
                _("Field %(num)d: %(name)s") % {
                'num': n,
                'name': field.name,
                })
            self.dialog.fieldList.addItem(item)
            n += 1
        self.dialog.fieldList.setCurrentRow(0)
        self.fieldChanged(0)
        self.ignoreUpdate = False

    def fwidget(self, name, type):
        "Return a field widget."
        if type == "edit":
            return getattr(self.dialog, name+"Edit")
        else:
            return getattr(self.dialog, name)

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
            # colour
            if type == "quiz":
                if getattr(field, type + 'FontColour'):
                    self.fwidget("useColour", type).setCheckState(Qt.Checked)
                    self.fwidget("fontColour", type).setPalette(QPalette(QColor(
                        getattr(field, type + 'FontColour'))))
                    self.fwidget("fontColour", type).setEnabled(True)
                else:
                    self.fwidget("useColour", type).setCheckState(Qt.Unchecked)
                    self.fwidget("fontColour", type).setEnabled(False)
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
        field.model.setModified()
        self.deck.flushMod()
        self.drawQuestionAndAnswer()

    def chooseColour(self, button, type):
        new = QColorDialog.getColor(button.palette().window().color(), self)
        if new.isValid():
            button.setPalette(QPalette(new))
            self.saveField()
            self.saveCard()

    def drawQuestionAndAnswer(self):
        self.deck.flushMod()
        f = self.deck.newFact()
        f.tags = u""
        for field in f.fields:
            f[field.name] = field.name
        f.model = self.model
        c = Card(f, self.card)
        t = "<br><center>" + c.htmlQuestion() + "</center>"
        self.dialog.question.setText(
            "<style>\n" + self.deck.rebuildCSS() + "</style>\n" + t)
        t = "<br><center>" + c.htmlAnswer() + "</center>"
        self.dialog.answer.setText(
            "<style>\n" + self.deck.rebuildCSS() + "</style>\n" + t)
        self.main.updateViews(self.main.state)

    def reject(self):
        ui.dialogs.close("DisplayProperties")
        QDialog.reject(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "DisplayProperties"))
