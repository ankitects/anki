# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import re, os, sys
from anki.utils import stripHTML, tidyHTML, canonifyTags
from anki.sound import playFromText
import anki.sound
from ankiqt import ui
import ankiqt
from ankiqt.ui.utils import mungeQA, saveGeom, restoreGeom

class FactEditor(object):
    """An editor for new/existing facts.

    The fact is updated as it is edited.
    Extra widgets can be added to 'fieldsGrid' to represent card-specific
    information, etc."""

    def __init__(self, parent, widget, deck=None):
        self.widget = widget
        self.parent = parent
        self.deck = deck
        self.fact = None
        self.fontChanged = False
        self.setupFields()
        self.onChange = None
        self.onFactValid = None
        self.onFactInvalid = None
        self.lastFocusedEdit = None
        self.changeTimer = None

    def setFact(self, fact, noFocus=False, check=False):
        "Make FACT the current fact."
        self.fact = fact
        self.factState = None
        if self.changeTimer:
            self.changeTimer.stop()
            self.changeTimer = None
        if self.needToRedraw():
            self.drawFields(noFocus, check)
        else:
            self.loadFields(check)
        if not noFocus:
            # update focus to first field
            self.fields[self.fact.fields[0].name][1].setFocus()
        self.fontChanged = False
        self.deck.setUndoBarrier()

    def initMedia(self):
        os.chdir(self.deck.mediaDir(create=True))

    def setupFields(self):
        # init for later
        self.fields = {}
        # top level vbox
        self.fieldsBox = QVBoxLayout(self.widget)
        self.fieldsBox.setMargin(0)
        self.fieldsBox.setSpacing(3)
        # icons
        self.iconsBox = QHBoxLayout()
        self.fieldsBox.addLayout(self.iconsBox)
        # scrollarea
        self.fieldsScroll = QScrollArea()
        self.fieldsScroll.setWidgetResizable(True)
        self.fieldsScroll.setLineWidth(0)
        self.fieldsScroll.setFrameStyle(0)
        self.fieldsScroll.setFocusPolicy(Qt.NoFocus)
        self.fieldsBox.addWidget(self.fieldsScroll)
        self.iconsBox.setMargin(0)
        self.iconsBox.addItem(QSpacerItem(20,20, QSizePolicy.Expanding))
        # button styles for mac
        self.plastiqueStyle = QStyleFactory.create("plastique")
        self.widget.setStyle(self.plastiqueStyle)
        # bold
        self.bold = QPushButton()
        self.bold.setCheckable(True)
        self.bold.connect(self.bold, SIGNAL("toggled(bool)"), self.toggleBold)
        self.bold.setIcon(QIcon(":/icons/text_bold.png"))
        self.bold.setToolTip(_("Bold text (Ctrl+b)"))
        self.bold.setShortcut(_("Ctrl+b"))
        self.bold.setFocusPolicy(Qt.NoFocus)
        self.bold.setEnabled(False)
        self.iconsBox.addWidget(self.bold)
        self.bold.setStyle(self.plastiqueStyle)
        # italic
        self.italic = QPushButton(self.widget)
        self.italic.setCheckable(True)
        self.italic.connect(self.italic, SIGNAL("toggled(bool)"), self.toggleItalic)
        self.italic.setIcon(QIcon(":/icons/text_italic.png"))
        self.italic.setToolTip(_("Italic text (Ctrl+i)"))
        self.italic.setShortcut(_("Ctrl+i"))
        self.italic.setFocusPolicy(Qt.NoFocus)
        self.italic.setEnabled(False)
        self.iconsBox.addWidget(self.italic)
        self.italic.setStyle(self.plastiqueStyle)
        # underline
        self.underline = QPushButton(self.widget)
        self.underline.setCheckable(True)
        self.underline.connect(self.underline, SIGNAL("toggled(bool)"), self.toggleUnderline)
        self.underline.setIcon(QIcon(":/icons/text_under.png"))
        self.underline.setToolTip(_("Underline text (Ctrl+u)"))
        self.underline.setShortcut(_("Ctrl+u"))
        self.underline.setFocusPolicy(Qt.NoFocus)
        self.underline.setEnabled(False)
        self.iconsBox.addWidget(self.underline)
        self.underline.setStyle(self.plastiqueStyle)
        # foreground color - not working on mac
        self.foreground = QPushButton()
        self.foreground.connect(self.foreground, SIGNAL("clicked()"), self.selectForeground)
        self.foreground.setToolTip(_("Foreground colour (Ctrl+r)"))
        self.foreground.setShortcut(_("Ctrl+r"))
        self.foreground.setFocusPolicy(Qt.NoFocus)
        self.foreground.setEnabled(False)
        self.foreground.setFixedWidth(30)
        self.foregroundFrame = QFrame()
        self.foregroundFrame.setAutoFillBackground(True)
        hbox = QHBoxLayout()
        hbox.addWidget(self.foregroundFrame)
        hbox.setMargin(1)
        self.foreground.setLayout(hbox)
        self.iconsBox.addWidget(self.foreground)
        self.foreground.setStyle(self.plastiqueStyle)
        # preview
        self.preview = QPushButton(self.widget)
        self.preview.connect(self.preview, SIGNAL("clicked()"),
                                  self.onPreview)
        self.preview.setToolTip(_("Preview (F2)"))
        self.preview.setShortcut(_("F2"))
        self.preview.setIcon(QIcon(":/icons/document-preview.png"))
        self.preview.setFocusPolicy(Qt.NoFocus)
        self.preview.setEnabled(False)
        self.iconsBox.addWidget(self.preview)
        self.preview.setStyle(self.plastiqueStyle)
        # pictures
        spc = QSpacerItem(10,10)
        self.iconsBox.addItem(spc)
        self.addPicture = QPushButton(self.widget)
        self.addPicture.connect(self.addPicture, SIGNAL("clicked()"), self.onAddPicture)
        self.addPicture.setFocusPolicy(Qt.NoFocus)
        self.addPicture.setShortcut(_("F3"))
        self.addPicture.setIcon(QIcon(":/icons/colors.png"))
        self.addPicture.setEnabled(False)
        self.addPicture.setToolTip(_("Add a picture (F3)"))
        self.iconsBox.addWidget(self.addPicture)
        self.addPicture.setStyle(self.plastiqueStyle)
        # sounds
        self.addSound = QPushButton(self.widget)
        self.addSound.connect(self.addSound, SIGNAL("clicked()"), self.onAddSound)
        self.addSound.setFocusPolicy(Qt.NoFocus)
        self.addSound.setShortcut(_("F4"))
        self.addSound.setEnabled(False)
        self.addSound.setIcon(QIcon(":/icons/text-speak.png"))
        self.addSound.setToolTip(_("Add audio (F4)"))
        self.iconsBox.addWidget(self.addSound)
        self.addSound.setStyle(self.plastiqueStyle)
        # latex
        spc = QSpacerItem(10,10)
        self.iconsBox.addItem(spc)
        self.latex = QPushButton(self.widget)
        self.latex.connect(self.latex, SIGNAL("clicked()"), self.insertLatex)
        self.latex.setToolTip(_("Latex (Ctrl+l, l)"))
        self.latex.setShortcut(_("Ctrl+l, l"))
        self.latex.setIcon(QIcon(":/icons/tex.png"))
        self.latex.setFocusPolicy(Qt.NoFocus)
        self.latex.setEnabled(False)
        self.iconsBox.addWidget(self.latex)
        self.latex.setStyle(self.plastiqueStyle)
        # latex eqn
        self.latexEqn = QPushButton(self.widget)
        self.latexEqn.connect(self.latexEqn, SIGNAL("clicked()"), self.insertLatexEqn)
        self.latexEqn.setToolTip(_("Latex equation (Ctrl+l, e)"))
        self.latexEqn.setShortcut(_("Ctrl+l, e"))
        self.latexEqn.setIcon(QIcon(":/icons/math_sqrt.png"))
        self.latexEqn.setFocusPolicy(Qt.NoFocus)
        self.latexEqn.setEnabled(False)
        self.iconsBox.addWidget(self.latexEqn)
        self.latexEqn.setStyle(self.plastiqueStyle)
        # latex math env
        self.latexMathEnv = QPushButton(self.widget)
        self.latexMathEnv.connect(self.latexMathEnv, SIGNAL("clicked()"),
                                  self.insertLatexMathEnv)
        self.latexMathEnv.setToolTip(_("Latex math environment (Ctrl+l, m)"))
        self.latexMathEnv.setShortcut(_("Ctrl+l, m"))
        self.latexMathEnv.setIcon(QIcon(":/icons/math_matrix.png"))
        self.latexMathEnv.setFocusPolicy(Qt.NoFocus)
        self.latexMathEnv.setEnabled(False)
        self.iconsBox.addWidget(self.latexMathEnv)
        self.latexMathEnv.setStyle(self.plastiqueStyle)
        # html
        self.htmlEdit = QPushButton(self.widget)
        self.htmlEdit.connect(self.htmlEdit, SIGNAL("clicked()"),
                                  self.onHtmlEdit)
        self.htmlEdit.setToolTip(_("HTML Editor (F9)"))
        self.htmlEdit.setShortcut(_("F9"))
        self.htmlEdit.setIcon(QIcon(":/icons/text-xml.png"))
        self.htmlEdit.setFocusPolicy(Qt.NoFocus)
        self.htmlEdit.setEnabled(False)
        self.iconsBox.addWidget(self.htmlEdit)
        self.htmlEdit.setStyle(self.plastiqueStyle)

        self.fieldsFrame = None
        self.widget.setLayout(self.fieldsBox)

    def _makeGrid(self):
        "Rebuild the grid to avoid trigging QT bugs."
        self.fieldsFrame = QWidget()
        self.fieldsGrid = QGridLayout(self.fieldsFrame)
        self.fieldsFrame.setLayout(self.fieldsGrid)
        self.fieldsGrid.setMargin(0)

    def drawFields(self, noFocus=False, check=False):
        self.parent.setUpdatesEnabled(False)
        self._makeGrid()
        # add entries for each field
        fields = self.fact.fields
        self.fields = {}
        self.widgets = {}
        n = 0
        first = None
        for field in fields:
            # label
            l = QLabel(field.name)
            self.fieldsGrid.addWidget(l, n, 0)
            # edit widget
            w = FactEdit(self)
            w.setTabChangesFocus(True)
            w.setAcceptRichText(True)
            w.setMinimumSize(20, 60)
            w.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
            w.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
            self.fieldsGrid.addWidget(w, n, 1)
            self.fields[field.name] = (field, w)
            self.widgets[w] = field
            # catch changes
            w.connect(w, SIGNAL("lostFocus"),
                      lambda w=w: self.onFocusLost(w))
            w.connect(w, SIGNAL("textChanged()"),
                      self.onTextChanged)
            w.connect(w, SIGNAL("currentCharFormatChanged(QTextCharFormat)"),
                      lambda w=w: self.formatChanged(w))
            n += 1
        # tags
        self.fieldsGrid.addWidget(QLabel(_("Tags")), n, 0)
        self.tags = ui.tagedit.TagEdit(self.parent)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.onTagChange)
        # update available tags
        self.tags.setDeck(self.deck)
        self.fieldsGrid.addWidget(self.tags, n, 1)
        # update fields
        self.loadFields(check)
        self.parent.setUpdatesEnabled(True)
        self.fieldsScroll.setWidget(self.fieldsFrame)

    def needToRedraw(self):
        if len(self.fact.fields) != len(self.fields):
            return True
        for field in self.fact.fields:
            if field.name not in self.fields:
                return True
        return self.fontChanged

    def loadFields(self, check=True, font=True):
        "Update field text (if changed) and font/colours."
        # text
        for field in self.fact.fields:
            w = self.fields[field.name][1]
            self.fields[field.name] = (field, w)
            self.widgets[w] = field
            new = self.fact[field.name]
            old = tidyHTML(unicode(w.toHtml()))
            # only update if something has changed, to preserve the cursor
            if new != old:
                w.setHtml(new)
            if font:
                # apply fonts
                font = QFont()
                # family
                family = (field.fieldModel.editFontFamily or
                          field.fieldModel.quizFontFamily)
                if family:
                    font.setFamily(family)
                # size
                size = (field.fieldModel.editFontSize or
                        field.fieldModel.quizFontSize)
                if size:
                    font.setPixelSize(size)
                w.setFont(font)
        self.tags.blockSignals(True)
        self.tags.setText(self.fact.tags)
        self.tags.blockSignals(False)
        if check:
            self.checkValid()

    def saveFields(self):
        "Save field text into fact."
        modified = False
        n = _("Edit")
        self.deck.setUndoStart(n, merge=True)
        for (w, f) in self.widgets.items():
            v = tidyHTML(unicode(w.toHtml())).strip()
            if self.fact[f.name] != v:
                self.fact[f.name] = v
                modified = True
        if modified:
            self.fact.setModified(textChanged=True)
            self.deck.setModified()
        self.deck.setUndoEnd(n)

    def onFocusLost(self, widget):
        self.saveFields()
        field = self.widgets[widget]
        self.fact.onKeyPress(field, field.value)
        self.loadFields(font=False)

    def onTextChanged(self):
        interval = 250
        if self.changeTimer:
            self.changeTimer.setInterval(interval)
        else:
            self.changeTimer = QTimer(self.parent)
            self.changeTimer.setSingleShot(True)
            self.changeTimer.start(interval)
            self.parent.connect(self.changeTimer,
                                SIGNAL("timeout()"),
                                self.onChangeTimer)

    def onChangeTimer(self):
        self.saveFields()
        self.checkValid()
        if self.onChange:
            self.onChange()
        self.changeTimer = None

    def saveFieldsNow(self):
        "Must call this before adding cards, closing dialog, etc."
        if not self.fact:
            return
        # disable timer
        if self.changeTimer:
            self.changeTimer.stop()
            self.changeTimer = None
            if self.onChange:
                self.onChange()
        # save fields and run features
        w = self.focusedEdit()
        if w:
            self.onFocusLost(w)
        # ensure valid
        self.checkValid()

    def checkValid(self):
        empty = []
        dupe = []
        for field in self.fact.fields:
            p = QPalette()
            p.setColor(QPalette.Text, QColor("#000000"))
            if not self.fact.fieldValid(field):
                empty.append(field)
                p.setColor(QPalette.Base, QColor("#ffffcc"))
                self.fields[field.name][1].setPalette(p)
            elif not self.fact.fieldUnique(field, self.deck.s):
                dupe.append(field)
                p.setColor(QPalette.Base, QColor("#ffcccc"))
                self.fields[field.name][1].setPalette(p)
            else:
                p.setColor(QPalette.Base, QColor("#ffffff"))
                self.fields[field.name][1].setPalette(p)
        # call relevant hooks
        invalid = len(empty+dupe)
        if self.factState != "valid" and not invalid:
            if self.onFactValid:
                self.onFactValid(self.fact)
            self.factState = "valid"
        elif self.factState != "invalid" and invalid:
            if self.onFactInvalid:
                self.onFactInvalid(self.fact)
            self.factState = "invalid"

    def onTagChange(self):
        self.fact.tags = canonifyTags(unicode(self.tags.text()))
        if self.onChange:
            self.onChange(None)
        self.fact.setModified(textChanged=True)
        self.deck.setModified()

    def focusField(self, fieldName):
        self.fields[fieldName][1].setFocus()

    def formatChanged(self, fmt):
        w = self.focusedEdit()
        if not w or w.textCursor().hasSelection():
            return
        else:
            self.bold.setChecked(w.fontWeight() == QFont.Bold)
            self.italic.setChecked(w.fontItalic())
            self.underline.setChecked(w.fontUnderline())
            self.foregroundFrame.setPalette(QPalette(w.textColor()))
            self.foregroundFrame.setStyleSheet("* {background-color: %s}" %
                                               unicode(w.textColor().name()))

    def resetFormatButtons(self):
        self.bold.setChecked(False)
        self.italic.setChecked(False)
        self.underline.setChecked(False)

    def enableButtons(self, val=True):
        self.bold.setEnabled(val)
        self.italic.setEnabled(val)
        self.underline.setEnabled(val)
        self.foreground.setEnabled(val)
        self.addPicture.setEnabled(val)
        self.addSound.setEnabled(val)
        self.latex.setEnabled(val)
        self.latexEqn.setEnabled(val)
        self.latexMathEnv.setEnabled(val)
        self.preview.setEnabled(val)
        self.htmlEdit.setEnabled(val)

    def disableButtons(self):
        self.enableButtons(False)

    def focusedEdit(self):
        for (name, (field, w)) in self.fields.items():
            if w.hasFocus():
                return w
        return None

    def toggleBold(self, bool):
        w = self.focusedEdit()
        if w:
            self.fontChanged = True
            w.setFontWeight(bool and QFont.Bold or QFont.Normal)

    def toggleItalic(self, bool):
        w = self.focusedEdit()
        if w:
            self.fontChanged = True
            w.setFontItalic(bool)

    def toggleUnderline(self, bool):
        w = self.focusedEdit()
        if w:
            self.fontChanged = True
            w.setFontUnderline(bool)

    def selectForeground(self):
        w = self.focusedEdit()
        if w:
            # we lose the selection when we open the colour dialog on win32,
            # so we need to save it
            cursor = w.textCursor()
            new = QColorDialog.getColor(w.textColor(), self.parent)
            if new.isValid():
                w.setTextCursor(cursor)
                self.foregroundFrame.setPalette(QPalette(new))
                w.setTextColor(new)
                # now we clear the selection
                cursor.clearSelection()
                w.setTextCursor(cursor)
            self.fontChanged = True

    def insertLatex(self):
        w = self.focusedEdit()
        if w:
            self.deck.mediaDir(create=True)
            w.insertHtml("[latex][/latex]")
            w.moveCursor(QTextCursor.PreviousWord)
            w.moveCursor(QTextCursor.PreviousCharacter)
            w.moveCursor(QTextCursor.PreviousCharacter)

    def insertLatexEqn(self):
        w = self.focusedEdit()
        if w:
            self.deck.mediaDir(create=True)
            w.insertHtml("[$][/$]")
            w.moveCursor(QTextCursor.PreviousWord)
            w.moveCursor(QTextCursor.PreviousCharacter)
            w.moveCursor(QTextCursor.PreviousCharacter)

    def insertLatexMathEnv(self):
        w = self.focusedEdit()
        if w:
            self.deck.mediaDir(create=True)
            w.insertHtml("[$$][/$$]")
            w.moveCursor(QTextCursor.PreviousWord)
            w.moveCursor(QTextCursor.PreviousCharacter)
            w.moveCursor(QTextCursor.PreviousCharacter)

    def onPreview(self):
        PreviewDialog(self.parent, self.deck, self.fact).exec_()

    def onHtmlEdit(self):
        def helpRequested():
            QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                          "HtmlEditor"))
        w = self.focusedEdit()
        if w:
            self.saveFields()
            d = QDialog(self.parent)
            form = ankiqt.forms.edithtml.Ui_Dialog()
            form.setupUi(d)
            d.connect(form.buttonBox, SIGNAL("helpRequested()"),
                     helpRequested)
            form.textEdit.setPlainText(self.widgets[w].value)
            form.textEdit.moveCursor(QTextCursor.End)
            d.exec_()
            w.setText(unicode(form.textEdit.toPlainText()).\
                      replace("\n", ""))
            self.saveFields()

    def fieldsAreBlank(self):
        for (field, widget) in self.fields.values():
            value = tidyHTML(unicode(widget.toHtml()))
            if value:
                return False
        return True

    def onAddPicture(self):
        self.initMedia()
        # get this before we open the dialog
        w = self.focusedEdit()
        key = _("Images (*.jpg *.png *.gif *.tiff *.svg *.tif *.jpeg)")
        file = ui.utils.getFile(self.parent, _("Add an image"), "picture", key)
        if not file:
            return
        path = self.deck.addMedia(file)
        w.insertHtml('<img src="%s">' % path)

    def onAddSound(self):
        self.initMedia()
        # get this before we open the dialog
        w = self.focusedEdit()
        key = _("Sounds (*.mp3 *.ogg *.wav)")
        file = ui.utils.getFile(self.parent, _("Add audio"), "audio", key)
        if not file:
            return
        anki.sound.play(file)
        path = self.deck.addMedia(file)
        w.insertHtml('[sound:%s]' % path)

class FactEdit(QTextEdit):

    def __init__(self, parent, *args):
        QTextEdit.__init__(self, *args)
        self.parent = parent

    def insertFromMimeData(self, source):
        if source.hasText():
            self.insertPlainText(source.text())
        elif source.hasHtml():
            self.insertHtml(self.simplyHTML(unicode(source.html())))

    def simplifyHTML(self, html):
        "Remove all style information and P tags."
        html = re.sub("\n", " ", html)
        html = re.sub("<br ?/?>", "\n", html)
        html = re.sub("<p ?/?>", "\n\n", html)
        html = re.sub('<style type="text/css">.*?</style>', "", html)
        html = stripHTML(html)
        html = html.replace("\n", "<br>")
        html = re.sub("\s\s+", " ", html).strip()
        return html

    def focusOutEvent(self, evt):
        QTextEdit.focusOutEvent(self, evt)
        self.parent.lastFocusedEdit = self
        self.parent.resetFormatButtons()
        self.parent.disableButtons()
        self.emit(SIGNAL("lostFocus"))

    # this shouldn't be necessary if/when we move away from kakasi
    def mouseDoubleClickEvent(self, evt):
        r = QRegExp("\\{(.*[|,].*)\\}")
        r.setMinimal(True)

        mouseposition = self.textCursor().position()

        blockoffset = 0
        result = r.indexIn(self.toPlainText(), 0)

        found = ""

        while result != -1:
            if mouseposition > result and mouseposition < result + r.matchedLength():
                mouseposition -= result + 1
                frompos = 0
                topos = 0

                string = r.cap(1)
                offset = 0
                bits = re.split("[|,]", unicode(string))
                for index in range(0, len(bits)):
                    offset += len(bits[index]) + 1
                    if mouseposition < offset:
                        found = bits[index]
                        break
                break

            blockoffset= result + r.matchedLength()
            result = r.indexIn(self.toPlainText(), blockoffset)

        if found == "":
            QTextEdit.mouseDoubleClickEvent(self,evt)
            return
        self.setPlainText(self.toPlainText().replace(result, r.matchedLength(), found))

    def focusInEvent(self, evt):
        if (self.parent.lastFocusedEdit and
            self.parent.lastFocusedEdit is not self):
            # remove selection from previous widget
            try:
                cur = self.parent.lastFocusedEdit.textCursor()
                cur.clearSelection()
                self.parent.lastFocusedEdit.setTextCursor(cur)
            except RuntimeError:
                # old widget was deleted
                pass
            self.lastFocusedEdit = None
        QTextEdit.focusInEvent(self, evt)
        self.parent.formatChanged(None)
        self.parent.enableButtons()

class PreviewDialog(QDialog):

    def __init__(self, parent, deck, fact, *args):
        QDialog.__init__(self, parent, *args)
        self.deck = deck
        self.fact = fact
        cards = self.deck.previewFact(self.fact)
        if not cards:
            ui.utils.showInfo(_("No cards to preview."),
                              parent=self.parent)
            return
        self.cards = cards
        self.currentCard = 0
        self.dialog = ankiqt.forms.previewcards.Ui_Dialog()
        self.dialog.setupUi(self)
        self.dialog.comboBox.addItems(QStringList(
            [c.cardModel.name for c in self.cards]))
        self.connect(self.dialog.comboBox, SIGNAL("activated(int)"),
                     self.onChange)
        self.updateCard()
        restoreGeom(self, "preview")

    def updateCard(self):
        c = self.cards[self.currentCard]
        self.dialog.webView.setHtml(
            "<style>" + self.deck.css + "</style>" +
            mungeQA(self.deck, c.htmlQuestion()) +
            mungeQA(self.deck, c.htmlAnswer()))
        playFromText(c.question)
        playFromText(c.answer)

    def onChange(self, idx):
        self.currentCard = idx
        self.updateCard()

    def reject(self):
        saveGeom(self, "preview")
        QDialog.reject(self)
