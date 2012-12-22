# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
from anki.consts import *
import aqt
from aqt.utils import showWarning, openHelp, getOnlyText, askUser

class FieldDialog(QDialog):

    def __init__(self, mw, note, ord=0, parent=None):
        QDialog.__init__(self, parent or mw) #, Qt.Window)
        self.mw = aqt.mw
        self.parent = parent or mw
        self.note = note
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = note.model()
        self.mw.checkpoint(_("Fields"))
        self.form = aqt.forms.fields.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Fields for %s") % self.model['name'])
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Close).setAutoDefault(False)
        self.currentIdx = None
        self.oldSortField = self.model['sortf']
        self.fillFields()
        self.setupSignals()
        self.form.fieldList.setCurrentRow(0)
        self.exec_()

    ##########################################################################

    def fillFields(self):
        self.currentIdx = None
        self.form.fieldList.clear()
        for f in self.model['flds']:
            self.form.fieldList.addItem(f['name'])

    def setupSignals(self):
        c = self.connect
        s = SIGNAL
        f = self.form
        c(f.fieldList, s("currentRowChanged(int)"), self.onRowChange)
        c(f.fieldAdd, s("clicked()"), self.onAdd)
        c(f.fieldDelete, s("clicked()"), self.onDelete)
        c(f.fieldRename, s("clicked()"), self.onRename)
        c(f.fieldPosition, s("clicked()"), self.onPosition)
        c(f.sortField, s("clicked()"), self.onSortField)
        c(f.buttonBox, s("helpRequested()"), self.onHelp)

    def onRowChange(self, idx):
        if idx == -1:
            return
        self.saveField()
        self.loadField(idx)

    def _uniqueName(self, prompt, ignoreOrd=None, old=""):
        txt = getOnlyText(prompt, default=old)
        if not txt:
            return
        for f in self.model['flds']:
            if ignoreOrd is not None and f['ord'] == ignoreOrd:
                continue
            if f['name'] == txt:
                showWarning(_("That field name is already used."))
                return
        return txt

    def onRename(self):
        idx = self.currentIdx
        f = self.model['flds'][idx]
        name = self._uniqueName(_("New name:"), self.currentIdx, f['name'])
        if not name:
            return
        self.mm.renameField(self.model, f, name)
        self.saveField()
        self.fillFields()
        self.form.fieldList.setCurrentRow(idx)

    def onAdd(self):
        name = self._uniqueName(_("Field name:"))
        if not name:
            return
        self.saveField()
        self.mw.progress.start()
        f = self.mm.newField(name)
        self.mm.addField(self.model, f)
        self.mw.progress.finish()
        self.fillFields()
        self.form.fieldList.setCurrentRow(len(self.model['flds'])-1)

    def onDelete(self):
        if len(self.model['flds']) < 2:
            return showWarning(_("Notes require at least one field."))
        c = self.mm.useCount(self.model)
        c = ngettext("%d note", "%d notes", c) % c
        if not askUser(_("Delete field from %s?") % c):
            return
        f = self.model['flds'][self.form.fieldList.currentRow()]
        self.mw.progress.start()
        self.mm.remField(self.model, f)
        self.mw.progress.finish()
        self.fillFields()
        self.form.fieldList.setCurrentRow(0)

    def onPosition(self, delta=-1):
        idx = self.currentIdx
        l = len(self.model['flds'])
        txt = getOnlyText(_("New position (1...%d):") % l, default=str(idx+1))
        if not txt:
            return
        try:
            pos = int(txt)
        except ValueError:
            return
        if not 0 < pos <= l:
            return
        self.saveField()
        f = self.model['flds'][self.currentIdx]
        self.mw.progress.start()
        self.mm.moveField(self.model, f, pos-1)
        self.mw.progress.finish()
        self.fillFields()
        self.form.fieldList.setCurrentRow(pos-1)

    def onSortField(self):
        # don't allow user to disable; it makes no sense
        self.form.sortField.setChecked(True)
        self.model['sortf'] = self.form.fieldList.currentRow()

    def loadField(self, idx):
        self.currentIdx = idx
        fld = self.model['flds'][idx]
        f = self.form
        f.fontFamily.setCurrentFont(QFont(fld['font']))
        f.fontSize.setValue(fld['size'])
        f.sticky.setChecked(fld['sticky'])
        f.sortField.setChecked(self.model['sortf'] == fld['ord'])
        f.rtl.setChecked(fld['rtl'])

    def saveField(self):
        # not initialized yet?
        if self.currentIdx is None:
            return
        idx = self.currentIdx
        fld = self.model['flds'][idx]
        f = self.form
        fld['font'] = f.fontFamily.currentFont().family()
        fld['size'] = f.fontSize.value()
        fld['sticky'] = f.sticky.isChecked()
        fld['rtl'] = f.rtl.isChecked()

    def reject(self):
        self.saveField()
        if self.oldSortField != self.model['sortf']:
            self.mw.progress.start()
            self.mw.col.updateFieldCache(self.mm.nids(self.model))
            self.mw.progress.finish()
        self.mm.save(self.model)
        self.mw.reset()
        QDialog.reject(self)

    def accept(self):
        self.reject()

    def onHelp(self):
        openHelp("fields")
