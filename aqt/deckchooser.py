# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from anki.hooks import addHook, remHook
from aqt.utils import  shortcut
from anki.lang import _

class DeckChooser(QHBoxLayout):

    def __init__(self, mw, widget, label=True, start=None):
        QHBoxLayout.__init__(self)
        self.widget = widget
        self.mw = mw
        self.deck = mw.col
        self.label = label
        self.setContentsMargins(0,0,0,0)
        self.setSpacing(8)
        self.setupDecks()
        self.widget.setLayout(self)
        addHook('currentModelChanged', self.onModelChange)

    def setupDecks(self):
        if self.label:
            self.deckLabel = QLabel(_("Deck"))
            self.addWidget(self.deckLabel)
        # decks box
        self.deck = QPushButton(clicked=self.onDeckChange)
        self.deck.setToolTip(shortcut(_("Target Deck (Ctrl+D)")))
        s = QShortcut(QKeySequence(_("Ctrl+D")), self.widget, activated=self.onDeckChange)
        self.addWidget(self.deck)
        # starting label
        if self.mw.col.conf.get("addToCur", True):
            col = self.mw.col
            did = col.conf['curDeck']
            if col.decks.isDyn(did):
                # if they're reviewing, try default to current card
                c = self.mw.reviewer.card
                if self.mw.state == "review" and c:
                    if not c.odid:
                        did = c.did
                    else:
                        did = c.odid
                else:
                    did = 1
            self.setDeckName(self.mw.col.decks.nameOrNone(
                did) or _("Default"))
        else:
            self.setDeckName(self.mw.col.decks.nameOrNone(
                self.mw.col.models.current()['did']) or _("Default"))
        # layout
        sizePolicy = QSizePolicy(
            QSizePolicy.Policy(7),
            QSizePolicy.Policy(0))
        self.deck.setSizePolicy(sizePolicy)

    def show(self):
        self.widget.show()

    def hide(self):
        self.widget.hide()

    def cleanup(self):
        remHook('currentModelChanged', self.onModelChange)

    def onModelChange(self):
        if not self.mw.col.conf.get("addToCur", True):
            self.setDeckName(self.mw.col.decks.nameOrNone(
                self.mw.col.models.current()['did']) or _("Default"))

    def onDeckChange(self):
        from aqt.studydeck import StudyDeck
        current = self.deckName()
        ret = StudyDeck(
            self.mw, current=current, accept=_("Choose"),
            title=_("Choose Deck"), help="addingnotes",
            cancel=False, parent=self.widget, geomKey="selectDeck")
        if ret.name:
            self.setDeckName(ret.name)

    def setDeckName(self, name):
        self.deck.setText(name.replace("&", "&&"))
        self._deckName = name

    def deckName(self):
        return self._deckName

    def selectedId(self):
        # save deck name
        name = self.deckName()
        if not name.strip():
            did = 1
        else:
            did = self.mw.col.decks.id(name)
        return did
