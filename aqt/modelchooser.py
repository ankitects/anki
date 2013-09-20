# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from anki.hooks import addHook, remHook, runHook
from aqt.utils import  shortcut
import aqt

class ModelChooser(QHBoxLayout):

    def __init__(self, mw, widget, label=True):
        QHBoxLayout.__init__(self)
        self.widget = widget
        self.mw = mw
        self.deck = mw.col
        self.label = label
        self.setMargin(0)
        self.setSpacing(8)
        self.setupModels()
        addHook('reset', self.onReset)
        self.widget.setLayout(self)

    def setupModels(self):
        if self.label:
            self.modelLabel = QLabel(_("Type"))
            self.addWidget(self.modelLabel)
        # models box
        self.models = QPushButton()
        #self.models.setStyleSheet("* { text-align: left; }")
        self.models.setToolTip(shortcut(_("Change Note Type (Ctrl+N)")))
        s = QShortcut(QKeySequence(_("Ctrl+N")), self.widget)
        s.connect(s, SIGNAL("activated()"), self.onModelChange)
        self.models.setAutoDefault(False)
        self.addWidget(self.models)
        self.connect(self.models, SIGNAL("clicked()"), self.onModelChange)
        # layout
        sizePolicy = QSizePolicy(
            QSizePolicy.Policy(7),
            QSizePolicy.Policy(0))
        self.models.setSizePolicy(sizePolicy)
        self.updateModels()

    def cleanup(self):
        remHook('reset', self.onReset)

    def onReset(self):
        self.updateModels()

    def show(self):
        self.widget.show()

    def hide(self):
        self.widget.hide()

    def onEdit(self):
        import aqt.models
        aqt.models.Models(self.mw, self.widget)

    def onModelChange(self):
        from aqt.studydeck import StudyDeck
        current = self.deck.models.current()['name']
        # edit button
        edit = QPushButton(_("Manage"))
        self.connect(edit, SIGNAL("clicked()"), self.onEdit)
        def nameFunc():
            return sorted(self.deck.models.allNames())
        ret = StudyDeck(
            self.mw, names=nameFunc,
            accept=_("Choose"), title=_("Choose Note Type"),
            help="_notes", current=current, parent=self.widget,
            buttons=[edit], cancel=True, geomKey="selectModel")
        if not ret.name:
            return
        m = self.deck.models.byName(ret.name)
        self.deck.conf['curModel'] = m['id']
        cdeck = self.deck.decks.current()
        cdeck['mid'] = m['id']
        self.deck.decks.save(cdeck)
        runHook("currentModelChanged")
        self.mw.reset()

    def updateModels(self):
        self.models.setText(self.deck.models.current()['name'])
