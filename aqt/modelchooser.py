# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from operator import itemgetter
from anki import stdmodels
from anki.lang import ngettext
from anki.hooks import addHook, remHook, runHook
from aqt.utils import isMac
import aqt

class ModelChooser(QHBoxLayout):

    def __init__(self, mw, widget, cards=True, label=True):
        QHBoxLayout.__init__(self)
        self.widget = widget
        self.mw = mw
        self.deck = mw.col
        self.handleCards = cards
        self.label = label
        self._ignoreReset = False
        self.setMargin(0)
        self.setSpacing(4)
        self.setupModels()
        self.setupTemplates()
        addHook('reset', self.onReset)
        self.widget.setLayout(self)

    def setupModels(self):
        if self.label:
            self.modelLabel = QLabel(_("<b>Model</b>:"))
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
        if not isMac:
            self.edit.setFixedWidth(32)
        self.edit.setIcon(QIcon(":/icons/configure.png"))
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

    def setupTemplates(self):
        self.cardShortcuts = []
        if self.handleCards:
            self.cards = QPushButton()
            self.cards.setAutoDefault(False)
            self.connect(self.cards, SIGNAL("clicked()"), self.onCardChange)
            self.addWidget(self.cards)
            self.updateTemplates()

    def cleanup(self):
        remHook('reset', self.onReset)

    def onReset(self):
        if not self._ignoreReset:
            self.updateModels()
            self.updateTemplates()

    def show(self):
        self.widget.show()

    def hide(self):
        self.widget.hide()

    def onEdit(self):
        import aqt.models
        aqt.models.Models(self.mw, self.widget)

    def onModelChange(self, idx):
        model = self._models[idx]
        self.deck.conf['currentModelId'] = model.id
        self.updateTemplates()
        self._ignoreReset = True
        runHook("currentModelChanged")
        self._ignoreReset = False

    def updateModels(self):
        self.models.clear()
        self._models = sorted(self.deck.models.all(),
                              key=itemgetter("name"))
        self.models.addItems([m['name'] for m in self._models])
        for c, m in enumerate(self._models):
            if m['id'] == str(self.deck.conf['currentModelId']):
                self.models.setCurrentIndex(c)
                break

    def updateTemplates(self):
        if not self.handleCards:
            return
        # remove any shortcuts
        for s in self.cardShortcuts:
            s.setEnabled(False)
        self.cardShortcuts = []
        m = self.deck.models.current()
        ts = m['tmpls']
        active = [t['name'] for t in ts if t['actv']]
        txt = ngettext("%d card", "%d cards", len(active)) % len(active)
        self.cards.setText(txt)
        n = 1
        for t in ts:
            s = QShortcut(QKeySequence("Ctrl+%d" % n), self.widget)
            self.widget.connect(s, SIGNAL("activated()"),
                                lambda t=t: self.toggleTemplate(t))
            self.cardShortcuts.append(s)
            n += 1

    def onCardChange(self):
        m = QMenu(self.widget)
        model = self.deck.models.current()
        for template in model.templates:
            action = QAction(self.widget)
            action.setCheckable(True)
            if template['actv']:
                action.setChecked(True)
            action.setText(template['name'])
            self.connect(action, SIGNAL("triggered()"),
                         lambda t=template: \
                         self.toggleTemplate(t))
            m.addAction(action)
        m.exec_(self.cards.mapToGlobal(QPoint(0,0)))

    def canDisableTemplate(self):
        return len([t for t in self.deck.models.current()['tmpls']
                    if t['actv']]) > 1

    def toggleTemplate(self, card):
        if not card['actv']:
            card['actv'] = True
        elif self.canDisableTemplate():
            card['actv'] = False
        self.deck.models.current().flush()
        self.updateTemplates()

class AddModel(QDialog):

    def __init__(self, mw, parent=None):
        self.parent = parent or mw
        self.mw = mw
        self.deck = mw.col
        QDialog.__init__(self, self.parent, Qt.Window)
        self.model = None
        self.dialog = aqt.forms.addmodel.Ui_Dialog()
        self.dialog.setupUi(self)
        # standard models
        self.models = []
        for (name, func) in stdmodels.models:
            item = QListWidgetItem(_("Add: %s") % name)
            self.dialog.models.addItem(item)
            self.models.append((True, func))
        # add copies
        mids = self.deck.db.list("select id from models order by name")
        for m in [self.deck.getModel(mid, False) for mid in mids]:
            m.id = None
            item = QListWidgetItem(_("Copy: %s") % m['name'])
            self.dialog.models.addItem(item)
            m['name'] = _("%s copy") % m['name']
            self.models.append((False, m))
        self.dialog.models.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        self.connect(s, SIGNAL("activated()"), self.accept)
        # help
        self.connect(self.dialog.buttonBox, SIGNAL("helpRequested()"), self.onHelp)

    def get(self):
        self.exec_()
        return self.model

    def reject(self):
        self.accept()

    def accept(self):
        (isStd, model) = self.models[self.dialog.models.currentRow()]
        if isStd:
            # create
            self.model = model(self.deck)
        else:
            # add copy to deck
            self.mw.col.addModel(model)
            self.model = model
        QDialog.accept(self)

    def onHelp(self):
        openHelp("AddModel")
