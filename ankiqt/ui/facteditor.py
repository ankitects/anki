# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from PyQt4.QtSvg import *
from PyQt4.QtWebKit import QWebPage
import re, os, sys, tempfile, urllib2, ctypes
from anki.utils import stripHTML, tidyHTML, canonifyTags
from ankiqt.ui.sound import getAudio
import anki.sound
from ankiqt import ui
import ankiqt
from ankiqt.ui.utils import mungeQA, saveGeom, restoreGeom
from anki.hooks import addHook, removeHook, runHook, runFilter
from sqlalchemy.exceptions import InvalidRequestError

clozeColour = "#0000ff"

if sys.platform.startswith("win32"):
    ActivateKeyboardLayout = ctypes.windll.user32.ActivateKeyboardLayout
    ActivateKeyboardLayout.restype = ctypes.c_void_p
    ActivateKeyboardLayout.argtypes = [ctypes.c_void_p, ctypes.c_uint]
    GetKeyboardLayout = ctypes.windll.user32.GetKeyboardLayout
    GetKeyboardLayout.restype = ctypes.c_void_p
    GetKeyboardLayout.argtypes = [ctypes.c_uint]

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
        self.addMode = False
        self.setupFields()
        self.onChange = None
        self.onFactValid = None
        self.onFactInvalid = None
        self.lastFocusedEdit = None
        self.changeTimer = None
        self.lastCloze = None
        self.resetOnEdit = True
        self.card=None
        addHook("deckClosed", self.deckClosedHook)
        addHook("guiReset", self.refresh)
        addHook("colourChanged", self.colourChanged)

    def close(self):
        removeHook("deckClosed", self.deckClosedHook)
        removeHook("guiReset", self.refresh)
        removeHook("colourChanged", self.colourChanged)

    def setFact(self, fact, noFocus=False, check=False, scroll=False,
                forceRedraw=False):
        "Make FACT the current fact."
        self.fact = fact
        self.factState = None
        if self.changeTimer:
            self.changeTimer.stop()
            self.changeTimer = None
        if self.needToRedraw() or forceRedraw:
            if self.fact:
                self.drawFields(noFocus, check)
            else:
                self.widget.hide()
                return
        else:
            self.loadFields(check)
        self.widget.show()
        if scroll:
            self.fieldsScroll.ensureVisible(0, 0)
        if not noFocus:
            # update focus to first field
            self.fields[self.fact.fields[0].name][1].setFocus()
        self.fontChanged = False
        self.deck.setUndoBarrier()
        if self.deck.mediaDir(create=False):
            self.initMedia()

    def refresh(self):
        if self.fact:
            try:
                self.deck.s.refresh(self.fact)
            except InvalidRequestError:
                # not attached to session yet, add cards dialog will handle
                return
            self.setFact(self.fact, check=True, forceRedraw=True)

    def focusFirst(self):
        if self.focusTarget:
            self.focusTarget.setFocus()

    def initMedia(self):
        os.chdir(self.deck.mediaDir(create=True))

    def deckClosedHook(self):
        self.fact = None

    def setupFields(self):
        # init for later
        self.fields = {}
        # button styles for mac
        self.plastiqueStyle = QStyleFactory.create("plastique")
        self.widget.setStyle(self.plastiqueStyle)
        # top level vbox
        self.fieldsBox = QVBoxLayout(self.widget)
        self.fieldsBox.setMargin(0)
        self.fieldsBox.setSpacing(3)
        # icons
        self.iconsBox = QHBoxLayout()
        self.iconsBox2 = QHBoxLayout()
        self.fieldsBox.addLayout(self.iconsBox)
        self.fieldsBox.addLayout(self.iconsBox2)
        # card layout
        self.iconsBox.addItem(QSpacerItem(20,1, QSizePolicy.Expanding))
        self.clayout = QPushButton(_("Card Layout"))
        self.clayout.connect(self.clayout, SIGNAL("clicked()"), self.onCardLayout)
        self.clayout.setSizePolicy(QSizePolicy.Preferred,QSizePolicy.Preferred)
        self.clayout.setFixedHeight(20)
        # self.clayout.setFixedWidth(48)
        self.clayout.setIcon(QIcon(":/icons/layout.png"))
        #self.clayout.setIconSize(QSize(32,32))
        self.clayout.setToolTip(_("Edit how cards are displayed (F2)"))
        self.clayout.setShortcut(_("F2"))
        self.clayout.setFocusPolicy(Qt.NoFocus)
        self.iconsBox.addWidget(self.clayout)
        self.clayout.setStyle(self.plastiqueStyle)
        # scrollarea
        self.fieldsScroll = QScrollArea()
        self.fieldsScroll.setWidgetResizable(True)
        self.fieldsScroll.setLineWidth(0)
        self.fieldsScroll.setFrameStyle(0)
        self.fieldsScroll.setFocusPolicy(Qt.NoFocus)
        self.fieldsBox.addWidget(self.fieldsScroll)
        # tags
        self.tagsBox = QHBoxLayout()
        self.tagsLabel = QLabel(_("Tags"))
        self.tagsBox.addWidget(self.tagsLabel)
        self.tags = ui.tagedit.TagEdit(self.parent)
        self.tags.connect(self.tags, SIGNAL("lostFocus"),
                          self.onTagChange)
        self.tagsBox.addWidget(self.tags)
        self.fieldsBox.addLayout(self.tagsBox)
        # icons
        self.iconsBox.setMargin(0)
        self.iconsBox2.setMargin(0)
        # bold
        spc = QSpacerItem(5,5)
        self.iconsBox.addItem(spc)
        self.bold = QPushButton()
        self.bold.setFixedHeight(20)
        self.bold.setFixedWidth(20)
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
        self.italic.setFixedHeight(20)
        self.italic.setFixedWidth(20)
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
        self.underline.setFixedHeight(20)
        self.underline.setFixedWidth(20)
        self.underline.setCheckable(True)
        self.underline.connect(self.underline, SIGNAL("toggled(bool)"), self.toggleUnderline)
        self.underline.setIcon(QIcon(":/icons/text_under.png"))
        self.underline.setToolTip(_("Underline text (Ctrl+u)"))
        self.underline.setShortcut(_("Ctrl+u"))
        self.underline.setFocusPolicy(Qt.NoFocus)
        self.underline.setEnabled(False)
        self.iconsBox.addWidget(self.underline)
        self.underline.setStyle(self.plastiqueStyle)
        # foreground color
        self.foreground = QPushButton()
        self.foreground.connect(self.foreground, SIGNAL("clicked()"), self.onForeground)
        self.foreground.setToolTip(
            _("Set colour (F7; repeat to choose next; F6 to use)"))
        self.foreground.setShortcut(_("F7"))
        self.foreground.setFocusPolicy(Qt.NoFocus)
        self.foreground.setEnabled(False)
        self.foreground.setFixedWidth(20)
        self.foreground.setFixedHeight(20)
        self.foregroundFrame = QFrame()
        self.foregroundFrame.setAutoFillBackground(True)
        self.colourChanged()
        hbox = QHBoxLayout()
        hbox.addWidget(self.foregroundFrame)
        hbox.setMargin(5)
        self.foreground.setLayout(hbox)
        self.iconsBox.addWidget(self.foreground)
        self.foreground.setStyle(self.plastiqueStyle)
        self.iconsBox.addItem(QSpacerItem(5,1, QSizePolicy.Fixed))
        # cloze
        self.cloze = QPushButton(self.widget)
        self.cloze.setFixedHeight(20)
        self.clozeSC = QShortcut(QKeySequence(_("F9")), self.widget)
        self.cloze.connect(self.cloze, SIGNAL("clicked()"),
                                  self.onCloze)
        self.cloze.connect(self.clozeSC, SIGNAL("activated()"),
                                  self.onCloze)
        self.cloze.setToolTip(_("Cloze (F9)"))
        self.cloze.setFixedWidth(24)
        self.cloze.setText("[...]")
        self.cloze.setFocusPolicy(Qt.NoFocus)
        self.cloze.setEnabled(False)
        self.iconsBox.addWidget(self.cloze)
        self.cloze.setStyle(self.plastiqueStyle)
        # pictures
        self.addPicture = QPushButton(self.widget)
        self.addPicture.setFixedHeight(20)
        self.addPicture.setFixedWidth(20)
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
        self.addSound.setFixedHeight(20)
        self.addSound.setFixedWidth(20)
        self.addSound.connect(self.addSound, SIGNAL("clicked()"), self.onAddSound)
        self.addSound.setFocusPolicy(Qt.NoFocus)
        self.addSound.setShortcut(_("F4"))
        self.addSound.setEnabled(False)
        self.addSound.setIcon(QIcon(":/icons/text-speak.png"))
        self.addSound.setToolTip(_("Add audio/video (F4)"))
        self.iconsBox.addWidget(self.addSound)
        self.addSound.setStyle(self.plastiqueStyle)
        # sounds
        self.recSound = QPushButton(self.widget)
        self.recSound.setFixedHeight(20)
        self.recSound.setFixedWidth(20)
        self.recSound.connect(self.recSound, SIGNAL("clicked()"), self.onRecSound)
        self.recSound.setFocusPolicy(Qt.NoFocus)
        self.recSound.setShortcut(_("F5"))
        self.recSound.setEnabled(False)
        self.recSound.setIcon(QIcon(":/icons/media-record.png"))
        self.recSound.setToolTip(_("Record audio (F5)"))
        self.iconsBox.addWidget(self.recSound)
        self.recSound.setStyle(self.plastiqueStyle)
        # more
        self.more = QPushButton(self.widget)
        self.more.setFixedHeight(20)
        self.more.setFixedWidth(20)
        self.more.connect(self.more, SIGNAL("clicked()"),
                                  self.onMore)
        self.more.setToolTip(_("Show advanced options"))
        self.more.setText(">>")
        self.more.setFocusPolicy(Qt.NoFocus)
        self.iconsBox.addWidget(self.more)
        self.more.setStyle(self.plastiqueStyle)
        # latex
        spc = QSpacerItem(5,5, QSizePolicy.Expanding)
        self.iconsBox2.addItem(spc)
        self.latex = QPushButton(self.widget)
        self.latex.setFixedHeight(20)
        self.latex.setFixedWidth(20)
        self.latex.setToolTip(_("Latex (Ctrl+l then l)"))
        self.latexSC = QShortcut(QKeySequence(_("Ctrl+l, l")), self.widget)
        self.latex.connect(self.latex, SIGNAL("clicked()"), self.insertLatex)
        self.latex.connect(self.latexSC, SIGNAL("activated()"), self.insertLatex)
        self.latex.setIcon(QIcon(":/icons/tex.png"))
        self.latex.setFocusPolicy(Qt.NoFocus)
        self.latex.setEnabled(False)
        self.iconsBox2.addWidget(self.latex)
        self.latex.setStyle(self.plastiqueStyle)
        # latex eqn
        self.latexEqn = QPushButton(self.widget)
        self.latexEqn.setFixedHeight(20)
        self.latexEqn.setFixedWidth(20)
        self.latexEqn.setToolTip(_("Latex equation (Ctrl+l then e)"))
        self.latexEqnSC = QShortcut(QKeySequence(_("Ctrl+l, e")), self.widget)
        self.latexEqn.connect(self.latexEqn, SIGNAL("clicked()"), self.insertLatexEqn)
        self.latexEqn.connect(self.latexEqnSC, SIGNAL("activated()"), self.insertLatexEqn)
        self.latexEqn.setIcon(QIcon(":/icons/math_sqrt.png"))
        self.latexEqn.setFocusPolicy(Qt.NoFocus)
        self.latexEqn.setEnabled(False)
        self.iconsBox2.addWidget(self.latexEqn)
        self.latexEqn.setStyle(self.plastiqueStyle)
        # latex math env
        self.latexMathEnv = QPushButton(self.widget)
        self.latexMathEnv.setFixedHeight(20)
        self.latexMathEnv.setFixedWidth(20)
        self.latexMathEnv.setToolTip(_("Latex math environment (Ctrl+l then m)"))
        self.latexMathEnvSC = QShortcut(QKeySequence(_("Ctrl+l, m")), self.widget)
        self.latexMathEnv.connect(self.latexMathEnv, SIGNAL("clicked()"),
                                  self.insertLatexMathEnv)
        self.latexMathEnv.connect(self.latexMathEnvSC, SIGNAL("activated()"),
                                  self.insertLatexMathEnv)
        self.latexMathEnv.setIcon(QIcon(":/icons/math_matrix.png"))
        self.latexMathEnv.setFocusPolicy(Qt.NoFocus)
        self.latexMathEnv.setEnabled(False)
        self.iconsBox2.addWidget(self.latexMathEnv)
        self.latexMathEnv.setStyle(self.plastiqueStyle)
        # html
        self.htmlEdit = QPushButton(self.widget)
        self.htmlEdit.setFixedHeight(20)
        self.htmlEdit.setFixedWidth(20)
        self.htmlEdit.setToolTip(_("HTML Editor (Ctrl+F9)"))
        self.htmlEditSC = QShortcut(QKeySequence(_("Ctrl+F9")), self.widget)
        self.htmlEdit.connect(self.htmlEdit, SIGNAL("clicked()"),
                              self.onHtmlEdit)
        self.htmlEdit.connect(self.htmlEditSC, SIGNAL("activated()"),
                                self.onHtmlEdit)
        self.htmlEdit.setIcon(QIcon(":/icons/text-xml.png"))
        self.htmlEdit.setFocusPolicy(Qt.NoFocus)
        self.htmlEdit.setEnabled(False)
        self.iconsBox2.addWidget(self.htmlEdit)
        self.htmlEdit.setStyle(self.plastiqueStyle)
        #
        self.fieldsFrame = None
        self.widget.setLayout(self.fieldsBox)
        # show advanced buttons?
        if not ankiqt.mw.config['factEditorAdvanced']:
            self.onMore(False)

    def _makeGrid(self):
        "Rebuild the grid to avoid trigging QT bugs."
        self.fieldsFrame = QWidget()
        self.fieldsGrid = QGridLayout(self.fieldsFrame)
        self.fieldsFrame.setLayout(self.fieldsGrid)
        self.fieldsGrid.setMargin(0)
        self.fieldsGrid.setSpacing(5)

    def drawField(self, field, n):
        # label
        l = QLabel(field.name)
        self.labels.append(l)
        self.fieldsGrid.addWidget(l, n, 0)
        # edit widget
        w = FactEdit(self)
        w.setTabChangesFocus(True)
        w.setAcceptRichText(True)
        w.setMinimumSize(20, 60)
        w.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
        w.setHorizontalScrollBarPolicy(Qt.ScrollBarAlwaysOff)
        if field.fieldModel.features:
            w.setLayoutDirection(Qt.RightToLeft)
        else:
            w.setLayoutDirection(Qt.LeftToRight)
        runHook("makeField", w, field)
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
        return w

    def drawFields(self, noFocus=False, check=False):
        self.parent.setUpdatesEnabled(False)
        self._makeGrid()
        # add entries for each field
        fields = self.fact.fields
        self.fields = {}
        self.widgets = {}
        self.labels = []
        n = 0
        first = True
        last = None

        for field in fields:
            w = self.drawField(field, n)
            last = w
            if first:
                self.focusTarget = w
                first = False
            n += 1

        # update available tags
        self.tags.setDeck(self.deck)
        # update fields
        self.loadFields(check)
        self.parent.setUpdatesEnabled(True)
        self.fieldsScroll.setWidget(self.fieldsFrame)
        if sys.platform.startswith("darwin"):
            extra = 5
        elif sys.platform.startswith("win32"):
            extra = 3
        else:
            extra = 2
        tagsw = self.tagsLabel.sizeHint().width()
        self.tagsLabel.setFixedWidth(max(tagsw,
                                         max(*([
            l.width() for l in self.labels] + [0])))
                                     + extra)
        self.parent.setTabOrder(last, self.tags)

    def needToRedraw(self):
        if self.fact is None:
            return True
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
            # only update if something has changed
            if new != old:
                cur = w.textCursor()
                w.setHtml('<meta name="qrichtext" content="1"/>' + new)
                w.setTextCursor(cur)
            if font:
                # apply fonts
                font = QFont()
                # family
                family = field.fieldModel.quizFontFamily
                if family:
                    font.setFamily(family)
                # size
                size = field.fieldModel.editFontSize
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
            v = tidyHTML(unicode(w.toHtml()))
            if self.fact[f.name] != v:
                self.fact[f.name] = v
                modified = True
        if modified:
            self.fact.setModified(textChanged=True, deck=self.deck)
            if not self.fact.isNew():
                self.deck.setModified()
        self.deck.setUndoEnd(n)
        return modified

    def onFocusLost(self, widget):
        from ankiqt import mw
        if not self.fact:
            # editor or deck closed
            return
        if mw.inDbHandler:
            return
        modified = self.saveFields()
        field = self.widgets[widget]
        self.fact.focusLost(field)
        self.fact.setModified(textChanged=True, deck=self.deck)
        self.loadFields(font=False)
        if modified and self.resetOnEdit:
            ankiqt.mw.reset(runHooks=False)

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
        from ankiqt import mw
        interval = 250
        if not self.fact:
            return
        if mw.inDbHandler:
            self.changeTimer.start(interval)
            return
        self.checkValid()
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
                self.onChange('field')
        # save fields and run features
        w = self.focusedEdit()
        if w:
            self.onFocusLost(w)
        self.onTagChange()
        # ensure valid
        self.checkValid()

    def checkValid(self):
        empty = []
        dupe = []
        for field in self.fact.fields:
            p = QPalette()
            p.setColor(QPalette.Text, QColor("#000000"))
            if not self.fieldValid(field):
                empty.append(field)
                p.setColor(QPalette.Base, QColor("#ffffcc"))
                self.fields[field.name][1].setPalette(p)
            elif not self.fieldUnique(field):
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

    def textForField(self, field):
        "Current edited value for field."
        w = self.fields[field.name][1]
        v = tidyHTML(unicode(w.toHtml()))
        return v

    def fieldValid(self, field):
        return not (field.fieldModel.required and
                    not self.textForField(field).strip())

    def fieldUnique(self, field):
        if not field.fieldModel.unique:
            return True
        req = ("select value from fields "
               "where fieldModelId = :fmid and value = :val and id != :id")
        return not self.deck.s.scalar(
            req, val=self.textForField(field), fmid=field.fieldModel.id, id=field.id)

    def onTagChange(self):
        if not self.fact:
            return
        old = self.fact.tags
        self.fact.tags = canonifyTags(unicode(self.tags.text()))
        if old != self.fact.tags:
            self.deck.s.flush()
            self.deck.updateFactTags([self.fact.id])
            self.deck.updatePriorities([c.id for c in self.fact.cards])
            self.fact.setModified(textChanged=True, deck=self.deck)
            self.deck.flushMod()
            if self.resetOnEdit:
                ankiqt.mw.reset(runHooks=False)
        if self.onChange:
            self.onChange('tag')

    def focusField(self, fieldName):
        self.fields[fieldName][1].setFocus()

    def formatChanged(self, fmt):
        w = self.focusedEdit()
        if not w:
            return
        else:
            l = self.bold, self.italic, self.underline
            for b in l:
                b.blockSignals(True)
            self.bold.setChecked(w.fontWeight() == QFont.Bold)
            self.italic.setChecked(w.fontItalic())
            self.underline.setChecked(w.fontUnderline())
            for b in l:
                b.blockSignals(False)

    def resetFormatButtons(self):
        for b in self.bold, self.italic, self.underline:
            b.blockSignals(True)
            b.setChecked(False)
            b.blockSignals(False)

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
        self.cloze.setEnabled(val)
        self.htmlEdit.setEnabled(val)
        self.recSound.setEnabled(val)

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

    def _updateForegroundButton(self, txtcol):
        self.foregroundFrame.setPalette(QPalette(QColor(txtcol)))
        self.foregroundFrame.setStyleSheet("* {background-color: %s}" %
                                           txtcol)

    def colourChanged(self):
        recent = ankiqt.mw.config['recentColours']
        self._updateForegroundButton(recent[-1])

    def onForeground(self):
        self.lastFocusedEdit = self.focusedEdit()
        p = ColourPopup(self.parent)
        p.move(self.foreground.mapToGlobal(QPoint(0,0)))
        g = QGridLayout(p)
        g.setMargin(4)
        g.setSpacing(0)
        p.setLayout(g)
        lastWidget = None
        self.colourNext = QShortcut(QKeySequence("F7"), p)
        p.connect(self.colourNext, SIGNAL("activated()"),
                  self.onNextColour)
        self.colourChoose = QShortcut(QKeySequence("F6"), p)
        p.connect(self.colourChoose, SIGNAL("activated()"),
                  self.onChooseColourKey)
        for n, c in enumerate(reversed(ankiqt.mw.config['recentColours'])):
            col = QToolButton()
            col.setAutoRaise(True)
            col.setFixedWidth(64)
            col.setFixedHeight(16)
            col.setAutoFillBackground(True)
            col.setPalette(QPalette(QColor(c)))
            col.setStyleSheet("* {background-color: %s}" %
                              c)
            col.connect(col, SIGNAL("clicked()"),
                        lambda c=c: self.onChooseColour(c))
            g.addWidget(col, n, 0)
            if lastWidget:
                p.setTabOrder(lastWidget, col)
            lastWidget = col
            but = QPushButton("X")
            but.setFixedWidth(16)
            but.setFixedHeight(16)
            but.setAutoDefault(False)
            but.connect(but, SIGNAL("clicked()"),
                        lambda c=c: self.onRemoveColour(c))
            g.addWidget(but, n, 1)
        spc = QSpacerItem(5,10, QSizePolicy.Fixed)
        g.addItem(spc, n+1, 0)
        cb = QPushButton(_("+"))
        cb.connect(cb, SIGNAL("clicked()"), self.onNewColour)
        cb.setFixedWidth(80)
        cb.setFixedHeight(16)
        cb.setAutoDefault(False)
        g.addWidget(cb, n+2, 0, 1, 2)
        self.colourDiag = p
        p.show()

    def onRemoveColour(self, colour):
        recent = ankiqt.mw.config['recentColours']
        recent.remove(colour)
        if not recent:
            recent.append("#000000")
        self.colourDiag.close()
        self.onForeground()
        runHook("colourChanged")

    def onNextColour(self):
        try:
            self.colourDiag.focusWidget().nextInFocusChain().setFocus()
        except:
            ui.utils.showInfo("Your Qt version is too old to support this.")

    def onChooseColourKey(self):
        try:
            self.colourDiag.focusWidget().click()
        except:
            # dialog focused
            pass

    def onChooseColour(self, colour):
        recent = ankiqt.mw.config['recentColours']
        recent.remove(colour)
        recent.append(colour)
        w = self.lastFocusedEdit
        w.setTextColor(QColor(colour))
        self.fontChanged = True
        self.colourDiag.close()
        runHook("colourChanged")

    def onNewColour(self):
        new = QColorDialog.getColor(Qt.white, self.parent)
        self.parent.raise_()
        recent = ankiqt.mw.config['recentColours']
        if new.isValid():
            txtcol = unicode(new.name())
            if txtcol not in recent:
                recent.append(txtcol)
            runHook("colourChanged")
            self.onChooseColour(txtcol)

    def insertLatex(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[latex]%s[/latex]" % selected)
            cur.setPosition(pos+7)
            w.setTextCursor(cur)

    def insertLatexEqn(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[$]%s[/$]" % selected)
            cur.setPosition(pos+3)
            w.setTextCursor(cur)

    def insertLatexMathEnv(self):
        w = self.focusedEdit()
        if w:
            selected = w.textCursor().selectedText()
            self.deck.mediaDir(create=True)
            cur = w.textCursor()
            pos = cur.position()
            w.insertHtml("[$$]%s[/$$]" % selected)
            cur.setPosition(pos+4)
            w.setTextCursor(cur)

    def onMore(self, toggle=None):
        if toggle is None:
            toggle = not self.latex.isVisible()
            ankiqt.mw.config['factEditorAdvanced'] = toggle
        self.latex.setShown(toggle)
        self.latexEqn.setShown(toggle)
        self.latexMathEnv.setShown(toggle)
        self.htmlEdit.setShown(toggle)

    def onCardLayout(self):
        self.saveFields()
        ui.clayout.CardLayout(self.parent, self, self.fact, self.card)

    # FIXME: in some future version, we should use a different delimiter, as
    # [sound] et al conflicts
    def onCloze(self):
        src = self.focusedEdit()
        if not src:
            return
        re1 = "\[(?:<.+?>)?.+?(:(.+?))?\](?:</.+?>)?"
        re2 = "\[(?:<.+?>)?(.+?)(:.+?)?\](?:</.+?>)?"
        # add brackets because selected?
        cursor = src.textCursor()
        oldSrc = None
        if cursor.hasSelection():
            oldSrc = src.toHtml()
            s = cursor.selectionStart()
            e = cursor.selectionEnd()
            cursor.setPosition(e)
            cursor.insertText("]]")
            cursor.setPosition(s)
            cursor.insertText("[[")
            re1 = "\[" + re1 + "\]"
            re2 = "\[" + re2 + "\]"
        dst = None
        for field in self.fact.fields:
            w = self.fields[field.name][1]
            if w.hasFocus():
                dst = False
                continue
            if dst is False:
                dst = w
                break
        if not dst:
            dst = self.fields[self.fact.fields[0].name][1]
            if dst == w:
                return
        # check if there's alredy something there
        if not oldSrc:
            oldSrc = src.toHtml()
        oldDst = dst.toHtml()
        if unicode(dst.toPlainText()):
            if (self.lastCloze and
                self.lastCloze[1] == oldSrc and
                self.lastCloze[2] == oldDst):
                src.setHtml(self.lastCloze[0])
                dst.setHtml("")
                self.lastCloze = None
                self.saveFields()
                return
            else:
                ui.utils.showInfo(
                    _("Next field must be blank."),
                    help="ClozeDeletion",
                    parent=self.parent)
                return
        # check if there's anything to change
        if not re.search("\[.+?\]", unicode(src.toPlainText())):
            ui.utils.showInfo(
                _("You didn't specify anything to occlude."),
                help="ClozeDeletion",
                parent=self.parent)
            return
        # create
        s = unicode(src.toHtml())
        def repl(match):
            exp = ""
            if match.group(2):
                exp = match.group(2)
            return '<font color="%s"><b>[...%s]</b></font>' % (
                clozeColour, exp)
        new = re.sub(re1, repl, s)
        old = re.sub(re2, '<font color="%s"><b>\\1</b></font>'
                     % clozeColour, s)
        src.setHtml(new)
        dst.setHtml(old)
        self.lastCloze = (oldSrc, unicode(src.toHtml()),
                          unicode(dst.toHtml()))
        self.saveFields()

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
            w.setHtml(unicode(form.textEdit.toPlainText()).\
                      replace("\n", ""))
            self.saveFields()

    def fieldsAreBlank(self):
        for (field, widget) in self.fields.values():
            value = tidyHTML(unicode(widget.toHtml()))
            if value:
                return False
        return True

    def onAddPicture(self):
        # get this before we open the dialog
        w = self.focusedEdit()
        key = (_("Images") +
               " (*.jpg *.png *.gif *.tiff *.svg *.tif *.jpeg)")
        file = ui.utils.getFile(self.parent, _("Add an image"), "picture", key)
        if not file:
            return
        if file.lower().endswith(".svg"):
            # convert to a png
            s = QSvgRenderer(file)
            i = QImage(s.defaultSize(), QImage.Format_ARGB32_Premultiplied)
            p = QPainter()
            p.begin(i)
            s.render(p)
            p.end()
            (fd, name) = tempfile.mkstemp(prefix="anki", suffix=".png")
            file = unicode(name, sys.getfilesystemencoding())
            i.save(file)
        self._addPicture(file, widget=w)

    def _addPicture(self, file, widget=None):
        self.initMedia()
        if widget:
            w = widget
        else:
            w = self.focusedEdit()
        path = self._addMedia(file)
        self.maybeDelete(path, file)
        w.insertHtml('<img src="%s">' % path)

    def _addMedia(self, file):
        try:
            return self.deck.addMedia(file)
        except (IOError, OSError), e:
            ui.utils.showWarning(_("Unable to add media: %s") % unicode(e),
                                 parent=self.parent)

    def onAddSound(self):
        # get this before we open the dialog
        w = self.focusedEdit()
        key = (_("Sounds/Videos") +
               " (*.mp3 *.ogg *.wav *.avi *.ogv *.mpg *.mpeg *.mov *.mp4 " +
               "*.mkv *.ogx *.ogv *.oga *.flv *.swf *.flac)")
        file = ui.utils.getFile(self.parent, _("Add audio"), "audio", key)
        if not file:
            return
        self._addSound(file, widget=w)

    def _addSound(self, file, widget=None, copy=True):
        self.initMedia()
        if widget:
            w = widget
        else:
            w = self.focusedEdit()
        if copy:
            path = self._addMedia(file)
            self.maybeDelete(path, file)
        else:
            path = file
        anki.sound.play(path)
        w.insertHtml('[sound:%s]' % path)

    def maybeDelete(self, new, old):
        if not ankiqt.mw.config['deleteMedia']:
            return
        if new == os.path.basename(old):
            return
        try:
            os.unlink(old)
        except:
            pass

    def onRecSound(self):
        self.initMedia()
        w = self.focusedEdit()
        try:
            file = getAudio(self.parent)
        except:
            if sys.platform.startswith("darwin"):
                ui.utils.showInfo(_('''\
Please install <a href="http://www.thalictrum.com/software/lame-3.97.dmg.gz">lame</a>
to enable recording.'''), parent=self.parent)
                return
            raise
        if file:
            self._addSound(file, w, copy=False)

class FactEdit(QTextEdit):

    def __init__(self, parent, *args):
        QTextEdit.__init__(self, *args)
        self.parent = parent
        self._tmpDir = None
        if sys.platform.startswith("win32"):
            self._ownLayout = None

    def canInsertFromMimeData(self, source):
        return (source.hasUrls() or
                source.hasText() or
                source.hasImage() or
                source.hasHtml())

    def insertFromMimeData(self, source):
        if self._insertFromMimeData(source):
            self.emit(SIGNAL("lostFocus"))

    def _insertFromMimeData(self, source):
        pics = ("jpg", "jpeg", "png", "tif", "tiff", "gif")
        audio =  ("wav", "mp3", "ogg", "flac")
        errtxt = _("An error occured while opening %s")
        if source.hasHtml() and "qrichtext" in unicode(source.html()):
            self.insertHtml(source.html())
            return True
        if source.hasText() and (ankiqt.mw.config['stripHTML'] or
                                 not source.hasHtml()):
            txt = unicode(source.text())
            l = txt.lower()
            if l.startswith("http://") or l.startswith("file://"):
                hadN = False
                if "\n" in txt:
                    txt = txt.split("\n")[0]
                    hadN = True
                if "\r" in txt:
                    txt = txt.split("\r")[0]
                    hadN = True
                if not source.hasImage() or hadN:
                    # firefox on linux just gives us a url
                    ext = txt.split(".")[-1].lower()
                    try:
                        if ext in pics:
                            name = self._retrieveURL(txt)
                            self.parent._addPicture(name, widget=self)
                        elif ext in audio:
                            name = self._retrieveURL(txt)
                            self.parent._addSound(name, widget=self)
                        else:
                            # not image or sound, treat as plain text
                            self.insertPlainText(source.text())
                        return True
                    except urllib2.URLError, e:
                        ui.utils.showWarning(errtxt % e)
            else:
                self.insertPlainText(source.text())
                return True
        if source.hasImage():
            im = QImage(source.imageData())
            if im.hasAlphaChannel():
                (fd, name) = tempfile.mkstemp(prefix="paste", suffix=".png")
                uname = unicode(name, sys.getfilesystemencoding())
                im.save(uname)
            else:
                (fd, name) = tempfile.mkstemp(prefix="paste", suffix=".jpg")
                uname = unicode(name, sys.getfilesystemencoding())
                im.save(uname, None, 95)
            self.parent._addPicture(uname, widget=self)
            return True
        if source.hasUrls():
            for url in source.urls():
                url = unicode(url.toString())
                ext = url.split(".")[-1].lower()
                try:
                    if ext in pics:
                        name = self._retrieveURL(url)
                        self.parent._addPicture(name, widget=self)
                    elif ext in audio:
                        name = self._retrieveURL(url)
                        self.parent._addSound(name, widget=self)
                except urllib2.URLError, e:
                    ui.utils.showWarning(errtxt % e)
            return True
        if source.hasHtml():
            self.insertHtml(self.simplifyHTML(unicode(source.html())))
            return True

    def _retrieveURL(self, url):
        req = urllib2.Request(url, None, {
            'User-Agent': 'Mozilla/5.0 (compatible; Anki/%s)' %
            ankiqt.appVersion })
        filecontents = urllib2.urlopen(req).read()
        path = os.path.join(self.tmpDir(), os.path.basename(url))
        file = open(path, "wb")
        file.write(filecontents)
        file.close()
        return path

    def tmpDir(self):
        if not self._tmpDir:
            self._tmpDir = tempfile.mkdtemp(prefix="anki")
        return self._tmpDir

    def simplifyHTML(self, html):
        "Remove all style information and P tags."
        if not ankiqt.mw.config['stripHTML']:
            return html
        html = re.sub("\n", " ", html)
        html = re.sub("<br ?/?>", "\n", html)
        html = re.sub("<p ?/?>", "\n\n", html)
        html = re.sub('<style type="text/css">.*?</style>', "", html)
        html = stripHTML(html)
        html = html.replace("\n", "<br>")
        html = html.strip()
        return html

    def focusOutEvent(self, evt):
        QTextEdit.focusOutEvent(self, evt)
        self.parent.lastFocusedEdit = self
        self.parent.resetFormatButtons()
        self.parent.disableButtons()
        if ankiqt.mw.config['preserveKeyboard'] and sys.platform.startswith("win32"):
            self._ownLayout = GetKeyboardLayout(0)
            ActivateKeyboardLayout(self._programLayout, 0)
        self.emit(SIGNAL("lostFocus"))

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
        if ankiqt.mw.config['preserveKeyboard'] and sys.platform.startswith("win32"):
            self._programLayout = GetKeyboardLayout(0)
            if self._ownLayout == None:
                self._ownLayout = self._programLayout
            ActivateKeyboardLayout(self._ownLayout, 0)

class ColourPopup(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.FramelessWindowHint)

    def event(self, evt):
        if evt.type() == QEvent.WindowDeactivate:
            self.close()
        return QDialog.event(self, evt)
