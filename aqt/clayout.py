# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re

from aqt.qt import *
from anki.consts import *
import aqt
from anki.sound import playFromText, clearAudioQueue
from aqt.utils import saveGeom, restoreGeom, getBase, mungeQA,\
    showInfo, askUser, getOnlyText, \
     showWarning, openHelp, downArrow
from anki.utils import isMac, isWin, joinFields
from aqt.webview import AnkiWebView
import anki.js


class CardLayout(QDialog):

    def __init__(self, mw, note, ord=0, parent=None, addMode=False):
        QDialog.__init__(self, parent or mw, Qt.Window)
        self.mw = aqt.mw
        self.parent = parent or mw
        self.note = note
        self.ord = ord
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = note.model()
        self.mw.checkpoint(_("Card Types"))
        self.addMode = addMode
        if addMode:
            # save it to DB temporarily
            self.emptyFields = []
            for name, val in note.items():
                if val.strip():
                    continue
                self.emptyFields.append(name)
                note[name] = "(%s)" % name
            note.flush()
        self.setupTabs()
        self.setupButtons()
        self.setWindowTitle(_("Card Types for %s") % self.model['name'])
        v1 = QVBoxLayout()
        v1.addWidget(self.tabs)
        v1.addLayout(self.buttons)
        self.setLayout(v1)
        self.redraw()
        restoreGeom(self, "CardLayout")
        self.exec_()

    def redraw(self):
        self.cards = self.col.previewCards(self.note, 2)
        self.redrawing = True
        self.updateTabs()
        self.redrawing = False
        idx = self.ord
        if idx >= len(self.cards):
            idx = len(self.cards) - 1
        self.selectCard(idx)

    def setupTabs(self):
        c = self.connect
        cloze = self.model['type'] == MODEL_CLOZE
        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(not cloze)
        self.tabs.setUsesScrollButtons(True)
        if not cloze:
            add = QPushButton("+")
            add.setFixedWidth(30)
            add.setToolTip(_("Add new card"))
            c(add, SIGNAL("clicked()"), self.onAddCard)
            self.tabs.setCornerWidget(add)
        c(self.tabs, SIGNAL("currentChanged(int)"), self.onCardSelected)
        c(self.tabs, SIGNAL("tabCloseRequested(int)"), self.onRemoveTab)

    def updateTabs(self):
        self.forms = []
        self.tabs.clear()
        for t in self.model['tmpls']:
            self.addTab(t)

    def addTab(self, t):
        c = self.connect
        w = QWidget()
        l = QHBoxLayout()
        l.setMargin(0)
        l.setSpacing(3)
        left = QWidget()
        # template area
        tform = aqt.forms.template.Ui_Form()
        tform.setupUi(left)
        tform.label1.setText(u" →")
        tform.label2.setText(u" →")
        tform.labelc1.setText(u" ↗")
        tform.labelc2.setText(u" ↘")
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            tform.tlayout1.setContentsMargins(0, 11, 0, 0)
            tform.tlayout2.setContentsMargins(0, 11, 0, 0)
            tform.tlayout3.setContentsMargins(0, 11, 0, 0)
        if len(self.cards) > 1:
            tform.groupBox_3.setTitle(_(
                "Styling (shared between cards)"))
        c(tform.front, SIGNAL("textChanged()"), self.saveCard)
        c(tform.css, SIGNAL("textChanged()"), self.saveCard)
        c(tform.back, SIGNAL("textChanged()"), self.saveCard)
        l.addWidget(left, 5)
        # preview area
        right = QWidget()
        pform = aqt.forms.preview.Ui_Form()
        pform.setupUi(right)
        if self.style().objectName() == "gtk+":
            # gtk+ requires margins in inner layout
            pform.frontPrevBox.setContentsMargins(0, 11, 0, 0)
            pform.backPrevBox.setContentsMargins(0, 11, 0, 0)
        # for cloze notes, show that it's one of n cards
        if self.model['type'] == MODEL_CLOZE:
            cnt = len(self.mm.availOrds(
                self.model, joinFields(self.note.fields)))
            for g in pform.groupBox, pform.groupBox_2:
                g.setTitle(g.title() + _(" (1 of %d)") % max(cnt, 1))
        pform.frontWeb = AnkiWebView()
        pform.frontPrevBox.addWidget(pform.frontWeb)
        pform.backWeb = AnkiWebView()
        pform.backPrevBox.addWidget(pform.backWeb)
        for wig in pform.frontWeb, pform.backWeb:
            wig.page().setLinkDelegationPolicy(
                QWebPage.DelegateExternalLinks)
        l.addWidget(right, 5)
        w.setLayout(l)
        self.forms.append({'tform': tform, 'pform': pform})
        self.tabs.addTab(w, t['name'])

    def onRemoveTab(self, idx):
        if len(self.model['tmpls']) < 2:
            return showInfo(_("At least one card type is required."))
        cards = self.mm.tmplUseCount(self.model, idx)
        cards = ngettext("%d card", "%d cards", cards) % cards
        msg = (_("Delete the '%(a)s' card type, and its %(b)s?") %
            dict(a=self.model['tmpls'][idx]['name'], b=cards))
        if not askUser(msg):
            return
        if not self.mm.remTemplate(self.model, self.cards[idx].template()):
            return showWarning(_("""\
Removing this card type would cause one or more notes to be deleted. \
Please create a new card type first."""))
        self.redraw()

    # Buttons
    ##########################################################################

    def setupButtons(self):
        c = self.connect
        l = self.buttons = QHBoxLayout()
        help = QPushButton(_("Help"))
        help.setAutoDefault(False)
        l.addWidget(help)
        c(help, SIGNAL("clicked()"), self.onHelp)
        l.addStretch()
        addField = QPushButton(_("Add Field"))
        addField.setAutoDefault(False)
        l.addWidget(addField)
        c(addField, SIGNAL("clicked()"), self.onAddField)
        if self.model['type'] != MODEL_CLOZE:
            flip = QPushButton(_("Flip"))
            flip.setAutoDefault(False)
            l.addWidget(flip)
            c(flip, SIGNAL("clicked()"), self.onFlip)
        more = QPushButton(_("More") + u" "+downArrow())
        more.setAutoDefault(False)
        l.addWidget(more)
        c(more, SIGNAL("clicked()"), lambda: self.onMore(more))
        l.addStretch()
        close = QPushButton(_("Close"))
        close.setAutoDefault(False)
        l.addWidget(close)
        c(close, SIGNAL("clicked()"), self.accept)

    # Cards
    ##########################################################################

    def selectCard(self, idx):
        if self.tabs.currentIndex() == idx:
            # trigger a re-read
            self.onCardSelected(idx)
        else:
            self.tabs.setCurrentIndex(idx)

    def onCardSelected(self, idx):
        if self.redrawing:
            return
        self.card = self.cards[idx]
        self.ord = idx
        self.tab = self.forms[idx]
        self.tabs.setCurrentIndex(idx)
        self.playedAudio = {}
        self.readCard()
        self.renderPreview()

    def readCard(self):
        t = self.card.template()
        self.redrawing = True
        self.tab['tform'].front.setPlainText(t['qfmt'])
        self.tab['tform'].css.setPlainText(self.model['css'])
        self.tab['tform'].back.setPlainText(t['afmt'])
        self.tab['tform'].front.setAcceptRichText(False)
        self.tab['tform'].css.setAcceptRichText(False)
        self.tab['tform'].back.setAcceptRichText(False)
        self.tab['tform'].front.setTabStopWidth(30)
        self.tab['tform'].css.setTabStopWidth(30)
        self.tab['tform'].back.setTabStopWidth(30)
        self.redrawing = False

    def saveCard(self):
        if self.redrawing:
            return
        text = self.tab['tform'].front.toPlainText()
        self.card.template()['qfmt'] = text
        text = self.tab['tform'].css.toPlainText()
        self.card.model()['css'] = text
        text = self.tab['tform'].back.toPlainText()
        self.card.template()['afmt'] = text
        self.renderPreview()

    # Preview
    ##########################################################################

    def renderPreview(self):
        c = self.card
        ti = self.maybeTextInput
        base = getBase(self.mw.col)
        self.tab['pform'].frontWeb.stdHtml(
            ti(mungeQA(self.mw.col, c.q(reload=True))), self.mw.reviewer._styles(),
            bodyClass="card card%d" % (c.ord+1), head=base,
            js=anki.js.browserSel)
        self.tab['pform'].backWeb.stdHtml(
            ti(mungeQA(self.mw.col, c.a()), type='a'), self.mw.reviewer._styles(),
            bodyClass="card card%d" % (c.ord+1), head=base,
            js=anki.js.browserSel)
        clearAudioQueue()
        if c.id not in self.playedAudio:
            playFromText(c.q())
            playFromText(c.a())
            self.playedAudio[c.id] = True

    def maybeTextInput(self, txt, type='q'):
        if "[[type:" not in txt:
            return txt
        origLen = len(txt)
        txt = txt.replace("<hr id=answer>", "")
        hadHR = origLen != len(txt)
        def answerRepl(match):
            res = self.mw.reviewer.correct(u"exomple", u"an example")
            if hadHR:
                res = "<hr id=answer>" + res
            return res
        if type == 'q':
            repl = "<input id='typeans' type=text value='exomple'>"
            repl = "<center>%s</center>" % repl
        else:
            repl = answerRepl
        return re.sub("\[\[type:.+?\]\]", repl, txt)

    # Card operations
    ######################################################################

    def onRename(self):
        name = getOnlyText(_("New name:"),
                           default=self.card.template()['name'])
        if not name:
            return
        if name in [c.template()['name'] for c in self.cards
                    if c.template()['ord'] != self.ord]:
            return showWarning(_("That name is already used."))
        self.card.template()['name'] = name
        self.tabs.setTabText(self.tabs.currentIndex(), name)

    def onReorder(self):
        n = len(self.cards)
        cur = self.card.template()['ord']+1
        pos = getOnlyText(
            _("Enter new card position (1...%s):") % n,
            default=str(cur))
        if not pos:
            return
        try:
            pos = int(pos)
        except ValueError:
            return
        if pos < 1 or pos > n:
            return
        if pos == cur:
            return
        pos -= 1
        self.mm.moveTemplate(self.model, self.card.template(), pos)
        self.ord = pos
        self.redraw()

    def _newCardName(self):
        n = len(self.cards) + 1
        while 1:
            name = _("Card %d") % n
            if name not in [c.template()['name'] for c in self.cards]:
                break
            n += 1
        return name

    def onAddCard(self):
        name = self._newCardName()
        t = self.mm.newTemplate(name)
        old = self.card.template()
        t['qfmt'] = "%s<br>\n%s" % (_("Edit to customize"), old['qfmt'])
        t['afmt'] = old['afmt']
        self.mm.addTemplate(self.model, t)
        self.ord = len(self.cards)
        self.redraw()

    def onFlip(self):
        old = self.card.template()
        self._flipQA(old, old)
        self.redraw()

    def _flipQA(self, src, dst):
        m = re.match("(?s)(.+)<hr id=answer>(.+)", src['afmt'])
        if not m:
            showInfo(_("""\
Anki couldn't find the line between the question and answer. Please \
adjust the template manually to switch the question and answer."""))
            return
        dst['afmt'] = "{{FrontSide}}\n\n<hr id=answer>\n\n%s" % src['qfmt']
        dst['qfmt'] = m.group(2).strip()
        return True

    def onMore(self, button):
        m = QMenu(self)
        a = m.addAction(_("Rename"))
        a.connect(a, SIGNAL("triggered()"),
                  self.onRename)
        if self.model['type'] != MODEL_CLOZE:
            a = m.addAction(_("Reposition"))
            a.connect(a, SIGNAL("triggered()"),
                      self.onReorder)
            t = self.card.template()
            if t['did']:
                s = _(" (on)")
            else:
                s = _(" (off)")
            a = m.addAction(_("Deck Override") + s)
            a.connect(a, SIGNAL("triggered()"),
                      self.onTargetDeck)
        a = m.addAction(_("Browser Appearance"))
        a.connect(a, SIGNAL("triggered()"),
                  self.onBrowserDisplay)
        m.exec_(button.mapToGlobal(QPoint(0,0)))

    def onBrowserDisplay(self):
        d = QDialog()
        f = aqt.forms.browserdisp.Ui_Dialog()
        f.setupUi(d)
        t = self.card.template()
        f.qfmt.setText(t.get('bqfmt', ""))
        f.afmt.setText(t.get('bafmt', ""))
        f.font.setCurrentFont(QFont(t.get('bfont', "Arial")))
        f.fontSize.setValue(t.get('bsize', 12))
        d.connect(f.buttonBox, SIGNAL("accepted()"),
                  lambda: self.onBrowserDisplayOk(f))
        d.exec_()

    def onBrowserDisplayOk(self, f):
        t = self.card.template()
        t['bqfmt'] = f.qfmt.text().strip()
        t['bafmt'] = f.afmt.text().strip()
        t['bfont'] = f.font.currentFont().family()
        t['bsize'] = f.fontSize.value()

    def onTargetDeck(self):
        from aqt.tagedit import TagEdit
        t = self.card.template()
        d = QDialog(self)
        d.setWindowTitle("Anki")
        d.setMinimumWidth(400)
        l = QVBoxLayout()
        lab = QLabel(_("""\
Enter deck to place new %s cards in, or leave blank:""") %
                           self.card.template()['name'])
        lab.setWordWrap(True)
        l.addWidget(lab)
        te = TagEdit(d, type=1)
        te.setCol(self.col)
        l.addWidget(te)
        if t['did']:
            te.setText(self.col.decks.get(t['did'])['name'])
            te.selectAll()
        bb = QDialogButtonBox(QDialogButtonBox.Close)
        self.connect(bb, SIGNAL("rejected()"), d, SLOT("close()"))
        l.addWidget(bb)
        d.setLayout(l)
        d.exec_()
        if not te.text().strip():
            t['did'] = None
        else:
            t['did'] = self.col.decks.id(te.text())

    def onAddField(self):
        diag = QDialog(self)
        form = aqt.forms.addfield.Ui_Dialog()
        form.setupUi(diag)
        fields = [f['name'] for f in self.model['flds']]
        form.fields.addItems(fields)
        form.font.setCurrentFont(QFont("Arial"))
        form.size.setValue(20)
        diag.show()
        # Work around a Qt bug,
        # https://bugreports.qt-project.org/browse/QTBUG-1894
        if isMac or isWin:
            # No problems on Macs or Windows.
            form.fields.showPopup()
        else:
            # Delay showing the pop-up.
            self.mw.progress.timer(200, form.fields.showPopup, False)
        if not diag.exec_():
            return
        if form.radioQ.isChecked():
            obj = self.tab['tform'].front
        else:
            obj = self.tab['tform'].back
        self._addField(obj,
                       fields[form.fields.currentIndex()],
                       form.font.currentFont().family(),
                       form.size.value())

    def _addField(self, widg, field, font, size):
        t = widg.toPlainText()
        t +="\n<div style='font-family: %s; font-size: %spx;'>{{%s}}</div>\n" % (
            font, size, field)
        widg.setPlainText(t)
        self.saveCard()

    # Closing & Help
    ######################################################################

    def accept(self):
        self.reject()

    def reject(self):
        clearAudioQueue()
        if self.addMode:
            # remove the filler fields we added
            for name in self.emptyFields:
                self.note[name] = ""
            self.mw.col.db.execute("delete from notes where id = ?",
                                   self.note.id)
        self.mm.save(self.model, templates=True)
        self.mw.reset()
        saveGeom(self, "CardLayout")
        return QDialog.reject(self)

    def onHelp(self):
        openHelp("templates")
