# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from aqt.utils import showInfo, askUser, getText, maybeHideClose
import aqt.modelchooser, aqt.clayout

class Models(QDialog):
    def __init__(self, mw, parent=None):
        self.mw = mw
        self.parent = parent or mw
        QDialog.__init__(self, self.parent, Qt.Window)
        self.deck = mw.deck
        self.mw.checkpoint(_("Models"))
        self.form = aqt.forms.models.Ui_Dialog()
        self.form.setupUi(self)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     lambda: aqt.openHelp("Models"))
        self.setupModels()
        self.exec_()

    # Models
    ##########################################################################

    def setupModels(self):
        self.model = None
        c = self.connect; f = self.form; box = f.buttonBox
        s = SIGNAL("clicked()")
        t = QDialogButtonBox.ActionRole
        b = box.addButton(_("Layout..."), t)
        c(b, s, self.onLayout)
        b.setDefault(True)
        b = box.addButton(_("Add"), t)
        c(b, s, self.onAdd)
        b = box.addButton(_("Rename"), t)
        c(b, s, self.onRename)
        b = box.addButton(_("Delete"), t)
        c(b, s, self.onDelete)
        b = box.addButton(_("Advanced..."), t)
        c(b, s, self.onAdvanced)
        c(f.modelsList, SIGNAL("currentRowChanged(int)"), self.modelChanged)
        c(f.modelsList, SIGNAL("itemDoubleClicked(QListWidgetItem*)"),
          self.onRename)
        self.updateModelsList()
        f.modelsList.setCurrentRow(0)
        maybeHideClose(box)

    def onRename(self):
        txt = getText(_("New name?"), default=self.model.name)
        if txt[0]:
            self.model.name = txt[0]
        self.updateModelsList()

    def updateModelsList(self):
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        mids = self.deck.db.list("select id from models order by name")
        self.models = [self.deck.getModel(mid) for mid in mids]
        self.form.modelsList.clear()
        for m in self.models:
            item = QListWidgetItem(_("%(name)s [%(facts)d facts]") % dict(
                name=m.name, facts=m.useCount()))
            self.form.modelsList.addItem(item)
        self.form.modelsList.setCurrentRow(row)

    def modelChanged(self):
        if self.model:
            self.saveModel()
        idx = self.form.modelsList.currentRow()
        self.model = self.models[idx]

    def onAdd(self):
        m = aqt.modelchooser.AddModel(self.mw, self).get()
        if m:
            self.deck.addModel(m)
            self.updateModelsList()

    def onLayout(self):
        # set to current
        # # see if there's an available fact
        dummy = False
        id = self.deck.db.scalar(
            "select id from facts where mid = ?", self.model.id)
        if id:
            fact = self.deck.getFact(id)
        else:
            # generate a dummy one
            self.deck.conf['currentModelId'] = self.model.id
            fact = self.deck.newFact()
            for f in fact.keys():
                fact[f] = f
            self.deck.addFact(fact)
            dummy = True
        aqt.clayout.CardLayout(self.mw, fact, type=2, parent=self)
        if dummy:
            self.deck._delFacts([fact.id])

    def onDelete(self):
        if len(self.models) < 2:
            showInfo(_("Please add another model first."),
                     parent=self)
            return
        if not askUser(
            _("Delete this model and all its cards?"),
            parent=self):
            return
        self.deck.delModel(self.model.id)
        self.model = None
        self.updateModelsList()

    def onAdvanced(self):
        d = QDialog(self)
        frm = aqt.forms.modelopts.Ui_Dialog()
        frm.setupUi(d)
        frm.latexHeader.setText(self.model.conf['latexPre'])
        frm.latexFooter.setText(self.model.conf['latexPost'])
        d.exec_()
        self.model.conf['latexPre'] = unicode(frm.latexHeader.toPlainText())
        self.model.conf['latexPost'] = unicode(frm.latexFooter.toPlainText())

    def saveModel(self):
        self.model.flush()

    # Cleanup
    ##########################################################################

    # need to flush model on change or reject

    def reject(self):
        self.saveModel()
        self.mw.reset()
        QDialog.reject(self)
