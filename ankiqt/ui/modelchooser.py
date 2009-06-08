# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
from operator import attrgetter
import anki, sys
from anki import stdmodels
from anki.models import *
from ankiqt import ui
import ankiqt.forms
from anki.hooks import addHook, removeHook

class ModelChooser(QHBoxLayout):

    def __init__(self, parent, main, deck, onChangeFunc=None, cards=True, label=True):
        QHBoxLayout.__init__(self)
        self.parent = parent
        self.main = main
        self.deck = deck
        self.onChangeFunc = onChangeFunc
        self.setMargin(0)
        self.setSpacing(4)
        self.shortcuts = []
        if label:
            self.modelLabel = QLabel(_("<b>Model</b>:"))
            self.addWidget(self.modelLabel)
        self.models = QComboBox()
        s = QShortcut(QKeySequence(_("Shift+Alt+m")), self.parent)
        s.connect(s, SIGNAL("activated()"),
                  lambda: self.models.showPopup())
        self.drawModels()
        sizePolicy = QSizePolicy(
            QSizePolicy.Policy(7),
            QSizePolicy.Policy(0))
        self.models.setSizePolicy(sizePolicy)
        self.addWidget(self.models)
        self.edit = QPushButton()
        if not sys.platform.startswith("darwin"):
            self.edit.setFixedWidth(32)
        self.edit.setIcon(QIcon(":/icons/configure.png"))
        self.edit.setShortcut(_("Shift+Alt+e"))
        self.edit.setToolTip(_("Customize Models"))
        self.edit.setAutoDefault(False)
        self.addWidget(self.edit)
        self.connect(self.edit, SIGNAL("clicked()"), self.onEdit)
        self.connect(self.models, SIGNAL("activated(int)"), self.onChange)
        self.handleCards = False
        if cards:
            self.handleCards = True
            label = QLabel(_("<b>Cards</b>:"))
            self.addWidget(label)
            self.cards = QPushButton()
            self.cards.setAutoDefault(False)
            self.connect(self.cards, SIGNAL("clicked()"), self.onCardChange)
            self.addWidget(self.cards)
            self.drawCardModels()
        addHook('guiReset', self.onModelEdited)

    def deinit(self):
        removeHook('guiReset', self.onModelEdited)

    def show(self):
        for i in range(self.count()):
            self.itemAt(i).widget().show()

    def hide(self):
        for i in range(self.count()):
            self.itemAt(i).widget().hide()

    def onEdit(self):
        ui.deckproperties.DeckProperties(self.parent, self.deck,
                                           onFinish=self.onModelEdited)

    def onModelEdited(self):
        # hack
        from ankiqt import mw
        self.deck = mw.deck
        self.drawModels()
        self.changed(self.deck.currentModel)

    def onChange(self, idx):
        model = self._models[idx]
        self.deck.currentModel = model
        self.changed(self.deck.currentModel)
        self.deck.setModified()

    def changed(self, model):
        self.deck.addModel(model)
        if self.onChangeFunc:
            self.onChangeFunc(model)
        self.drawCardModels()

    def drawModels(self):
        self.models.clear()
        self._models = sorted(self.deck.models, key=attrgetter("name"))
        self.models.addItems(QStringList(
            [m.name for m in self._models]))
        idx = self._models.index(self.deck.currentModel)
        self.models.setCurrentIndex(idx)

    def drawCardModels(self):
        if not self.handleCards:
            return
        # remove any shortcuts
        for s in self.shortcuts:
            s.setEnabled(False)
        self.shortcuts = []
        m = self.deck.currentModel
        txt = ", ".join([c.name for c in m.cardModels if c.active])
        if len(txt) > 30:
            txt = txt[0:30] + "..."
        self.cards.setText(txt)
        n = 1
        for c in m.cardModels:
            s = QShortcut(QKeySequence("Ctrl+%d" % n), self.parent)
            self.parent.connect(s, SIGNAL("activated()"),
                                lambda c=c: self.toggleCard(c))
            self.shortcuts.append(s)
            n += 1

    def onCardChange(self):
        m = QMenu(self.parent)
        m.setTitle("menu")
        model = self.deck.currentModel
        for card in model.cardModels:
            action = QAction(self.parent)
            action.setCheckable(True)
            if card.active:
                action.setChecked(True)
            action.setText(card.name)
            self.connect(action, SIGNAL("toggled(bool)"),
                         lambda b, a=action, c=card: \
                         self.cardChangeTriggered(b,a,c))
            m.addAction(action)
        m.exec_(self.cards.mapToGlobal(QPoint(0,0)))

    def cardChangeTriggered(self, bool, action, card):
        if bool:
            card.active = True
        elif self.canDisableModel():
            card.active = False
        self.drawCardModels()

    def canDisableModel(self):
        active = 0
        model = self.deck.currentModel
        for c in model.cardModels:
            if c.active:
                active += 1
        if active > 1:
            return True
        return False

    def toggleCard(self, card):
        if not card.active:
            card.active = True
        elif self.canDisableModel():
            card.active = False
        self.drawCardModels()

class AddModel(QDialog):

    def __init__(self, parent, main, deck):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        if not main:
            main = parent
        self.main = main
        self.model = None
        self.deck = deck
        self.dialog = ankiqt.forms.addmodel.Ui_AddModel()
        self.dialog.setupUi(self)
        self.models = []
        names = stdmodels.models.keys()
        names.sort()
        for name in names:
            m = stdmodels.byName(name)
            item = QListWidgetItem(_("Add: %s") % m.name)
            self.dialog.models.addItem(item)
            self.models.append((True, m))
        # add local decks
        models = sorted(deck.models, key=attrgetter("name"))
        for m in models:
            item = QListWidgetItem(_("Copy: %s") % m.name)
            self.dialog.models.addItem(item)
            self.models.append((False, m))
        self.dialog.models.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        self.connect(s, SIGNAL("activated()"), self.accept)
        # help
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.onHelp)

    def getModel(self):
        self.exec_()
        return self.model

    def accept(self):
        (isStd, self.model) = self.models[
            self.dialog.models.currentRow()]
        if not isStd:
            # not a standard model, so duplicate
            self.model = self.deck.copyModel(self.model)
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "AddModel"))
