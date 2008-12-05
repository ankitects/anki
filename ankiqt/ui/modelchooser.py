# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki
from anki import stdmodels
from anki.models import *
from ankiqt import ui
import ankiqt.forms

class ModelChooser(QHBoxLayout):

    def __init__(self, parent, main, deck, onChangeFunc, cards=True):
        QHBoxLayout.__init__(self)
        self.parent = parent
        self.main = main
        self.deck = deck
        self.onChangeFunc = onChangeFunc
        self.setMargin(0)
        self.setSpacing(6)
        self.shortcuts = []
        label = QLabel(_("<b>Model</b>:"))
        self.addWidget(label)
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
        self.add = QPushButton()
        self.add.setIcon(QIcon(":/icons/list-add.png"))
        self.add.setToolTip(_("Add a new model"))
        self.add.setAutoDefault(False)
        self.addWidget(self.add)
        self.connect(self.add, SIGNAL("clicked()"), self.onAdd)
        self.edit = QPushButton()
        self.edit.setIcon(QIcon(":/icons/edit.png"))
        self.edit.setShortcut(_("Shift+Alt+e"))
        self.edit.setToolTip(_("Edit the current model"))
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

    def show(self):
        for i in range(self.count()):
            self.itemAt(i).widget().show()

    def hide(self):
        for i in range(self.count()):
            self.itemAt(i).widget().hide()

    def onEdit(self):
        idx = self.models.currentIndex()
        model = self.deck.models[idx]
        ui.modelproperties.ModelProperties(self.parent, model, self.main,
                                           onFinish=self.onModelEdited)
        self.drawModels()
        self.changed(model)

    def onModelEdited(self):
        self.drawModels()

    def onAdd(self):
        model = AddModel(self.parent, self.main).getModel()
        if model:
            self.deck.addModel(model)
            self.drawModels()
            self.changed(model)
            self.deck.setModified()

    def onChange(self, idx):
        model = self.deck.models[idx]
        self.deck.currentModel = model
        self.changed(model)
        self.deck.setModified()

    def changed(self, model):
        self.deck.addModel(model)
        self.onChangeFunc(model)
        self.drawCardModels()

    def drawModels(self):
        self.models.clear()
        self.models.addItems(QStringList(
            [m.name for m in self.deck.models]))
        idx = self.deck.models.index(self.deck.currentModel)
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

    def __init__(self, parent, main=None):
        QDialog.__init__(self, parent)
        self.parent = parent
        if not main:
            main = parent
        self.main = main
        self.model = None
        self.dialog = ankiqt.forms.addmodel.Ui_AddModel()
        self.dialog.setupUi(self)
        self.models = {}
        names = stdmodels.models.keys()
        names.sort()
        for name in names:
            m = stdmodels.byName(name)
            item = QListWidgetItem(m.name)
            self.dialog.models.addItem(item)
            self.models[m.name] = m
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
        self.model = self.models[
            unicode(self.dialog.models.currentItem().text())]
        QDialog.accept(self)

    def onHelp(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki +
                                      "AddModel"))
