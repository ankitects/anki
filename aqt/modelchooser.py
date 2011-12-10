# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from operator import itemgetter
from anki.lang import ngettext
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
        self._ignoreReset = False
        self.setMargin(0)
        self.setSpacing(8)
        self.setupModels()
        addHook('reset', self.onReset)
        self.widget.setLayout(self)

    def setupModels(self):
        if self.label:
            self.modelLabel = QLabel(_("Note Type:"))
            self.addWidget(self.modelLabel)
        # models dropdown
        self.models = QComboBox()
        s = QShortcut(QKeySequence(_("Shift+Alt+m")), self.widget)
        s.connect(s, SIGNAL("activated()"),
                  lambda: self.models.showPopup())
        self.addWidget(self.models)
        self.connect(self.models, SIGNAL("activated(int)"), self.onModelChange)
        # edit button
        self.edit = QPushButton()
        if isMac:
            self.edit.setFixedWidth(24)
            self.edit.setFixedHeight(21)
        else:
            self.edit.setFixedWidth(32)
        self.edit.setIcon(QIcon(":/icons/gears.png"))
        self.edit.setShortcut(_("Shift+Alt+e"))
        self.edit.setToolTip(_("Customize Models"))
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
        if not self._ignoreReset:
            self.updateModels()

    def show(self):
        self.widget.show()

    def hide(self):
        self.widget.hide()

    def onEdit(self):
        import aqt.models
        aqt.models.Models(self.mw, self.widget)

    def onModelChange(self, idx):
        model = self._models[idx]
        self.deck.conf['curModel'] = model.id
        self._ignoreReset = True
        runHook("currentModelChanged")
        self._ignoreReset = False

    def updateModels(self):
        self.models.clear()
        self._models = sorted(self.deck.models.all(),
                              key=itemgetter("name"))
        self.models.addItems([m['name'] for m in self._models])
        for c, m in enumerate(self._models):
            if m['id'] == str(self.deck.conf['curModel']):
                self.models.setCurrentIndex(c)
                break
