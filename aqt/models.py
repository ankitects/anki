# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from operator import itemgetter
from aqt.utils import showInfo, askUser, getText, maybeHideClose, openHelp
import aqt.modelchooser, aqt.clayout
from anki import stdmodels
from aqt.utils import saveGeom, restoreGeom

class Models(QDialog):
    def __init__(self, mw, parent=None, fromMain=False):
        self.mw = mw
        self.parent = parent or mw
        self.fromMain = fromMain
        QDialog.__init__(self, self.parent, Qt.Window)
        self.col = mw.col
        self.mm = self.col.models
        self.mw.checkpoint(_("Note Types"))
        self.form = aqt.forms.models.Ui_Dialog()
        self.form.setupUi(self)
        self.connect(self.form.buttonBox, SIGNAL("helpRequested()"),
                     lambda: openHelp("notetypes"))
        self.setupModels()
        restoreGeom(self, "models")
        self.exec_()

    # Models
    ##########################################################################

    def setupModels(self):
        self.model = None
        c = self.connect; f = self.form; box = f.buttonBox
        s = SIGNAL("clicked()")
        t = QDialogButtonBox.ActionRole
        b = box.addButton(_("Add"), t)
        c(b, s, self.onAdd)
        b = box.addButton(_("Rename"), t)
        c(b, s, self.onRename)
        b = box.addButton(_("Delete"), t)
        c(b, s, self.onDelete)
        if self.fromMain:
            b = box.addButton(_("Fields..."), t)
            c(b, s, self.onFields)
            b = box.addButton(_("Cards..."), t)
            c(b, s, self.onCards)
        b = box.addButton(_("Options..."), t)
        c(b, s, self.onAdvanced)
        c(f.modelsList, SIGNAL("currentRowChanged(int)"), self.modelChanged)
        c(f.modelsList, SIGNAL("itemDoubleClicked(QListWidgetItem*)"),
          self.onRename)
        self.updateModelsList()
        f.modelsList.setCurrentRow(0)
        maybeHideClose(box)

    def onRename(self):
        txt = getText(_("New name:"), default=self.model['name'])
        if txt[1] and txt[0]:
            self.model['name'] = txt[0]
            self.mm.save(self.model)
        self.updateModelsList()

    def updateModelsList(self):
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        self.models = self.col.models.all()
        self.models.sort(key=itemgetter("name"))
        self.form.modelsList.clear()
        for m in self.models:
            mUse = self.mm.useCount(m)
            mUse = ngettext("%d note", "%d notes", mUse) % mUse
            item = QListWidgetItem("%s [%s]" % (m['name'], mUse))
            self.form.modelsList.addItem(item)
        self.form.modelsList.setCurrentRow(row)

    def modelChanged(self):
        if self.model:
            self.saveModel()
        idx = self.form.modelsList.currentRow()
        self.model = self.models[idx]

    def onAdd(self):
        m = AddModel(self.mw, self).get()
        if m:
            txt = getText(_("Name:"), default=m['name'])[0]
            if txt:
                m['name'] = txt
            self.mm.ensureNameUnique(m)
            self.mm.save(m)
            self.updateModelsList()

    def onDelete(self):
        if len(self.models) < 2:
            showInfo(_("Please add another note type first."),
                     parent=self)
            return
        if self.mm.useCount(self.model):
            msg = _("Delete this note type and all its cards?")
        else:
            msg = _("Delete this unused note type?")
        if not askUser(msg, parent=self):
            return
        self.mm.rem(self.model)
        self.model = None
        self.updateModelsList()

    def onAdvanced(self):
        d = QDialog(self)
        frm = aqt.forms.modelopts.Ui_Dialog()
        frm.setupUi(d)
        frm.latexHeader.setText(self.model['latexPre'])
        frm.latexFooter.setText(self.model['latexPost'])
        d.setWindowTitle(_("Options for %s") % self.model['name'])
        self.connect(
            frm.buttonBox, SIGNAL("helpRequested()"),
            lambda: openHelp("latex"))
        restoreGeom(d, "modelopts")
        d.exec_()
        saveGeom(d, "modelopts")
        self.model['latexPre'] = unicode(frm.latexHeader.toPlainText())
        self.model['latexPost'] = unicode(frm.latexFooter.toPlainText())

    def saveModel(self):
        self.mm.save(self.model)

    def _tmpNote(self):
        self.mm.setCurrent(self.model)
        n = self.col.newNote(forDeck=False)
        for name in n.keys():
            n[name] = "("+name+")"
        try:
            if "{{cloze:Text}}" in self.model['tmpls'][0]['qfmt']:
                n['Text'] = _("This is a {{c1::sample}} cloze deletion.")
        except:
            # invalid cloze
            pass
        return n

    def onFields(self):
        from aqt.fields import FieldDialog
        n = self._tmpNote()
        FieldDialog(self.mw, n, parent=self)

    def onCards(self):
        from aqt.clayout import CardLayout
        n = self._tmpNote()
        CardLayout(self.mw, n, ord=0, parent=self, addMode=True)

    # Cleanup
    ##########################################################################

    # need to flush model on change or reject

    def reject(self):
        self.saveModel()
        self.mw.reset()
        saveGeom(self, "models")
        QDialog.reject(self)

class AddModel(QDialog):

    def __init__(self, mw, parent=None):
        self.parent = parent or mw
        self.mw = mw
        self.col = mw.col
        QDialog.__init__(self, self.parent, Qt.Window)
        self.model = None
        self.dialog = aqt.forms.addmodel.Ui_Dialog()
        self.dialog.setupUi(self)
        # standard models
        self.models = []
        for (name, func) in stdmodels.models:
            if callable(name):
                name = name()
            item = QListWidgetItem(_("Add: %s") % name)
            self.dialog.models.addItem(item)
            self.models.append((True, func))
        # add copies
        for m in sorted(self.col.models.all(), key=itemgetter("name")):
            item = QListWidgetItem(_("Clone: %s") % m['name'])
            self.dialog.models.addItem(item)
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
        QDialog.reject(self)

    def accept(self):
        (isStd, model) = self.models[self.dialog.models.currentRow()]
        if isStd:
            # create
            self.model = model(self.col)
        else:
            # add copy to deck
            self.model = self.mw.col.models.copy(model)
            self.mw.col.models.setCurrent(self.model)
        QDialog.accept(self)

    def onHelp(self):
        openHelp("notetypes")
