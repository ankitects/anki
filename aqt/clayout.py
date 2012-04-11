# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import re
from anki.consts import *
import aqt
from anki.sound import playFromText, clearAudioQueue
from aqt.utils import saveGeom, restoreGeom, getBase, mungeQA, \
     saveSplitter, restoreSplitter, showInfo, askUser, getOnlyText, \
     showWarning, openHelp, openLink
from anki.utils import isMac, isWin
from aqt.webview import AnkiWebView

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
        self.selectCard(self.ord)

    def setupTabs(self):
        c = self.connect
        self.tabs = QTabWidget()
        self.tabs.setTabsClosable(True)
        self.tabs.setUsesScrollButtons(True)
        add = QPushButton("+")
        add.setFixedWidth(30)
        c(add, SIGNAL("clicked()"), self.onAddCard)
        self.tabs.setCornerWidget(add)
        c(self.tabs, SIGNAL("currentChanged(int)"), self.selectCard)
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
        c(tform.front, SIGNAL("textChanged()"), self.saveCard)
        c(tform.css, SIGNAL("textChanged()"), self.saveCard)
        c(tform.back, SIGNAL("textChanged()"), self.saveCard)
        l.addWidget(left, 5)
        # preview area
        right = QWidget()
        pform = aqt.forms.preview.Ui_Form()
        pform.setupUi(right)
        pform.frontWeb = AnkiWebView()
        pform.frontPrevBox.addWidget(pform.frontWeb)
        pform.backWeb = AnkiWebView()
        pform.backPrevBox.addWidget(pform.backWeb)
        def linkClicked(url):
            openLink(url)
        for wig in pform.frontWeb, pform.backWeb:
            wig.page().setLinkDelegationPolicy(
                QWebPage.DelegateExternalLinks)
            c(wig, SIGNAL("linkClicked(QUrl)"), linkClicked)
        l.addWidget(right, 5)
        w.setLayout(l)
        self.forms.append({'tform': tform, 'pform': pform})
        self.tabs.addTab(w, t['name'])

    def onRemoveTab(self, idx):
        if not askUser(_("Remove all cards of this type?")):
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
        rename = QPushButton(_("Rename..."))
        rename.setAutoDefault(False)
        l.addWidget(rename)
        c(rename, SIGNAL("clicked()"), self.onRename)
        repos = QPushButton(_("Reposition..."))
        repos.setAutoDefault(False)
        l.addWidget(repos)
        c(repos, SIGNAL("clicked()"), self.onReorder)
        tgt = QPushButton(_("Deck..."))
        tgt.setAutoDefault(False)
        l.addWidget(tgt)
        c(tgt, SIGNAL("clicked()"), self.onTargetDeck)
        l.addStretch()
        close = QPushButton(_("Close"))
        close.setAutoDefault(False)
        l.addWidget(close)
        c(close, SIGNAL("clicked()"), self.accept)

    # Cards
    ##########################################################################

    def selectCard(self, idx):
        if self.redrawing:
            return
        self.ord = idx
        if idx >= len(self.cards):
            idx = len(self.cards) - 1
        self.card = self.cards[idx]
        self.tab = self.forms[idx]
        self.tabs.setCurrentIndex(idx)
        self.playedAudio = {}
        self.readCard()
        self.renderPreview()

    def readCard(self):
        t = self.card.template()
        self.redrawing = True
        self.tab['tform'].front.setPlainText(t['qfmt'])
        self.tab['tform'].css.setPlainText(t['css'])
        self.tab['tform'].back.setPlainText(t['afmt'])
        self.redrawing = False

    def saveCard(self):
        if self.redrawing:
            return
        text = self.tab['tform'].front.toPlainText()
        self.card.template()['qfmt'] = text
        text = self.tab['tform'].css.toPlainText()
        self.card.template()['css'] = text
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
            ti(mungeQA(c.q(reload=True))), self.mw.reviewer._styles(),
            bodyClass="card", head=base)
        self.tab['pform'].backWeb.stdHtml(
            ti(mungeQA(c.a())), self.mw.reviewer._styles(),
            bodyClass="card", head=base)
        clearAudioQueue()
        if c.id not in self.playedAudio:
            playFromText(c.q())
            playFromText(c.a())
            self.playedAudio[c.id] = True

    def maybeTextInput(self, txt, type='q'):
        if type == 'q':
            repl = "<center><input type=text value='%s'></center>" % _(
                "(text is typed in here)")
        else:
            repl = _("(typing comparison appears here)")
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

    def onAddCard(self):
        name = getOnlyText(_("Name:"))
        if not name:
            return
        if name in [c.template()['name'] for c in self.cards]:
            return showWarning(_("That name is already used."))
        t = self.mm.newTemplate(name)
        self.mm.addTemplate(self.model, t)
        self.redraw()

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

    # Closing & Help
    ######################################################################

    def accept(self):
        self.reject()

    def reject(self):
        clearAudioQueue()
        if self.addMode:
            self.mw.col.db.execute("delete from notes where id = ?",
                                   self.note.id)
        self.mm.save(self.model, templates=True)
        self.mw.reset()
        saveGeom(self, "CardLayout")
        return QDialog.reject(self)

    def onHelp(self):
        openHelp("templates")
