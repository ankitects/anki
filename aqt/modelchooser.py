# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from operator import itemgetter
from anki.hooks import addHook, remHook, runHook
from aqt.utils import isMac
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
            self.modelLabel = QLabel(_("Note Type:"))
            self.addWidget(self.modelLabel)
        # models box
        self.models = QPushButton()
        self.models.setStyleSheet("* { text-align: left; }")
        self.models.setToolTip(_("Change Note Type (Ctrl+N)"))
        s = QShortcut(QKeySequence(_("Ctrl+N")), self.widget)
        s.connect(s, SIGNAL("activated()"), self.onModelChange)
        self.addWidget(self.models)
        self.connect(self.models, SIGNAL("clicked()"), self.onModelChange)
        # edit button
        self.edit = QPushButton()
        if isMac:
            self.edit.setFixedWidth(24)
            self.edit.setFixedHeight(21)
        else:
            self.edit.setFixedWidth(32)
        self.edit.setIcon(QIcon(":/icons/gears.png"))
        self.edit.setToolTip(_("Customize Note Types"))
        self.edit.setAutoDefault(False)
        self.addWidget(self.edit)
        self.connect(self.edit, SIGNAL("clicked()"), self.onEdit)
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
        ret = StudyDeck(self.mw, names=sorted(self.deck.models.allNames()),
                        accept=_("Select"), title=_("Choose Note Type"),
                        help="_notes", parent=self.widget)
        if not ret.name:
            return
        print ret.name
        m = self.deck.models.byName(ret.name)
        self.deck.conf['curModel'] = m['id']
        cdeck = self.deck.decks.current()
        cdeck['mid'] = m['id']
        self.deck.decks.save(cdeck)
        runHook("currentModelChanged")
        self.updateModels()

    def updateModels(self):
        self.models.setText(self.deck.models.current()['name'])
