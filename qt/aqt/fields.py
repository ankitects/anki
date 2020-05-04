# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
from anki.consts import *
from anki.lang import _, ngettext
from anki.models import NoteType
from anki.rsbackend import TemplateError
from aqt import AnkiQt
from aqt.qt import *
from aqt.utils import askUser, getOnlyText, openHelp, showWarning


class FieldDialog(QDialog):
    def __init__(self, mw: AnkiQt, nt: NoteType, parent=None):
        QDialog.__init__(self, parent or mw)
        self.mw = mw.weakref()
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = nt
        self.mw.checkpoint(_("Fields"))
        self.form = aqt.forms.fields.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(_("Fields for %s") % self.model["name"])
        self.form.buttonBox.button(QDialogButtonBox.Help).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Cancel).setAutoDefault(False)
        self.form.buttonBox.button(QDialogButtonBox.Save).setAutoDefault(False)
        self.currentIdx = None
        self.oldSortField = self.model["sortf"]
        self.fillFields()
        self.setupSignals()
        self.form.fieldList.setDragDropMode(QAbstractItemView.InternalMove)
        self.form.fieldList.dropEvent = self.onDrop
        self.form.fieldList.setCurrentRow(0)
        self.exec_()

    ##########################################################################

    def fillFields(self):
        self.currentIdx = None
        self.form.fieldList.clear()
        for c, f in enumerate(self.model["flds"]):
            self.form.fieldList.addItem("{}: {}".format(c + 1, f["name"]))

    def setupSignals(self):
        f = self.form
        qconnect(f.fieldList.currentRowChanged, self.onRowChange)
        qconnect(f.fieldAdd.clicked, self.onAdd)
        qconnect(f.fieldDelete.clicked, self.onDelete)
        qconnect(f.fieldRename.clicked, self.onRename)
        qconnect(f.fieldPosition.clicked, self.onPosition)
        qconnect(f.sortField.clicked, self.onSortField)
        qconnect(f.buttonBox.helpRequested, self.onHelp)

    def onDrop(self, ev):
        fieldList = self.form.fieldList
        indicatorPos = fieldList.dropIndicatorPosition()
        dropPos = fieldList.indexAt(ev.pos()).row()
        if indicatorPos == QAbstractItemView.OnViewport:  # to bottom
            self.moveField(fieldList.count())
        elif indicatorPos == QAbstractItemView.AboveItem:
            self.moveField(dropPos)
        elif indicatorPos == QAbstractItemView.BelowItem:
            self.moveField(dropPos + 1)

    def onRowChange(self, idx):
        if idx == -1:
            return
        self.saveField()
        self.loadField(idx)

    def _uniqueName(self, prompt, ignoreOrd=None, old=""):
        txt = getOnlyText(prompt, default=old)
        if not txt:
            return
        for f in self.model["flds"]:
            if ignoreOrd is not None and f["ord"] == ignoreOrd:
                continue
            if f["name"] == txt:
                showWarning(_("That field name is already used."))
                return
        return txt

    def onRename(self):
        idx = self.currentIdx
        f = self.model["flds"][idx]
        name = self._uniqueName(_("New name:"), self.currentIdx, f["name"])
        if not name:
            return
        self.mm.renameField(self.model, f, name, save=False)
        self.saveField()
        self.fillFields()
        self.form.fieldList.setCurrentRow(idx)

    def onAdd(self):
        name = self._uniqueName(_("Field name:"))
        if not name:
            return
        self.saveField()
        f = self.mm.newField(name)
        self.mm.addField(self.model, f, save=False)
        self.fillFields()
        self.form.fieldList.setCurrentRow(len(self.model["flds"]) - 1)

    def onDelete(self):
        if len(self.model["flds"]) < 2:
            return showWarning(_("Notes require at least one field."))
        c = self.mm.useCount(self.model)
        c = ngettext("%d note", "%d notes", c) % c
        if not askUser(_("Delete field from %s?") % c):
            return
        f = self.model["flds"][self.form.fieldList.currentRow()]
        self.mm.remField(self.model, f, save=False)
        self.fillFields()
        self.form.fieldList.setCurrentRow(0)

    def onPosition(self, delta=-1):
        idx = self.currentIdx
        l = len(self.model["flds"])
        txt = getOnlyText(_("New position (1...%d):") % l, default=str(idx + 1))
        if not txt:
            return
        try:
            pos = int(txt)
        except ValueError:
            return
        if not 0 < pos <= l:
            return
        self.moveField(pos)

    def onSortField(self):
        # don't allow user to disable; it makes no sense
        self.form.sortField.setChecked(True)
        self.model["sortf"] = self.form.fieldList.currentRow()

    def moveField(self, pos):
        self.saveField()
        f = self.model["flds"][self.currentIdx]
        self.mm.moveField(self.model, f, pos - 1, save=False)
        self.fillFields()
        self.form.fieldList.setCurrentRow(pos - 1)

    def loadField(self, idx):
        self.currentIdx = idx
        fld = self.model["flds"][idx]
        f = self.form
        f.fontFamily.setCurrentFont(QFont(fld["font"]))
        f.fontSize.setValue(fld["size"])
        f.sticky.setChecked(fld["sticky"])
        f.sortField.setChecked(self.model["sortf"] == fld["ord"])
        f.rtl.setChecked(fld["rtl"])

    def saveField(self):
        # not initialized yet?
        if self.currentIdx is None:
            return
        idx = self.currentIdx
        fld = self.model["flds"][idx]
        f = self.form
        fld["font"] = f.fontFamily.currentFont().family()
        fld["size"] = f.fontSize.value()
        fld["sticky"] = f.sticky.isChecked()
        fld["rtl"] = f.rtl.isChecked()

    def reject(self):
        self.mm._remove_from_cache(self.model["id"])
        QDialog.reject(self)

    def accept(self):
        self.saveField()

        def save():
            self.mm.save(self.model)

        def on_done(fut):
            try:
                fut.result()
            except TemplateError as e:
                # fixme: i18n
                showWarning("Unable to save changes: " + str(e))
                return
            self.mw.reset()
            QDialog.accept(self)

        self.mw.taskman.with_progress(save, on_done, self)

    def onHelp(self):
        openHelp("fields")
