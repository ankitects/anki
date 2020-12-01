# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import aqt
from anki.consts import *
from anki.lang import without_unicode_isolation
from anki.models import NoteType
from anki.rsbackend import TemplateError
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.schema_change_tracker import ChangeTracker
from aqt.utils import TR, askUser, getOnlyText, openHelp, showWarning, tooltip, tr


class FieldDialog(QDialog):
    def __init__(self, mw: AnkiQt, nt: NoteType, parent=None):
        QDialog.__init__(self, parent or mw)
        self.mw = mw
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = nt
        self.mm._remove_from_cache(self.model["id"])
        self.mw.checkpoint(tr(TR.EDITING_FIELDS))
        self.change_tracker = ChangeTracker(self.mw)
        self.form = aqt.forms.fields.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowTitle(
            without_unicode_isolation(tr(TR.FIELDS_FIELDS_FOR, val=self.model["name"]))
        )
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
        idx = self.currentIdx
        if dropPos == idx:
            return
        if indicatorPos == QAbstractItemView.OnViewport:  # to bottom.
            movePos = fieldList.count() - 1
        elif indicatorPos == QAbstractItemView.AboveItem:
            movePos = dropPos
        elif indicatorPos == QAbstractItemView.BelowItem:
            movePos = dropPos + 1
        # the item in idx is removed thus subtract 1.
        if idx < dropPos:
            movePos -= 1
        self.moveField(movePos + 1)  # convert to 1 based.

    def onRowChange(self, idx):
        if idx == -1:
            return
        self.saveField()
        self.loadField(idx)

    def _uniqueName(self, prompt, ignoreOrd=None, old=""):
        txt = getOnlyText(prompt, default=old).replace('"', "")
        if not txt:
            return
        for f in self.model["flds"]:
            if ignoreOrd is not None and f["ord"] == ignoreOrd:
                continue
            if f["name"] == txt:
                showWarning(tr(TR.FIELDS_THAT_FIELD_NAME_IS_ALREADY_USED))
                return
        return txt

    def onRename(self):
        idx = self.currentIdx
        f = self.model["flds"][idx]
        name = self._uniqueName(tr(TR.ACTIONS_NEW_NAME), self.currentIdx, f["name"])
        if not name:
            return

        old_name = f["name"]
        self.change_tracker.mark_basic()
        self.mm.rename_field(self.model, f, name)
        gui_hooks.fields_did_rename_field(self, f, old_name)

        self.saveField()
        self.fillFields()
        self.form.fieldList.setCurrentRow(idx)

    def onAdd(self):
        name = self._uniqueName(tr(TR.FIELDS_FIELD_NAME))
        if not name:
            return
        if not self.change_tracker.mark_schema():
            return
        self.saveField()
        f = self.mm.newField(name)
        self.mm.add_field(self.model, f)
        self.fillFields()
        self.form.fieldList.setCurrentRow(len(self.model["flds"]) - 1)

    def onDelete(self):
        if len(self.model["flds"]) < 2:
            return showWarning(tr(TR.FIELDS_NOTES_REQUIRE_AT_LEAST_ONE_FIELD))
        count = self.mm.useCount(self.model)
        c = tr(TR.BROWSING_NOTE_COUNT, count=count)
        if not askUser(tr(TR.FIELDS_DELETE_FIELD_FROM, val=c)):
            return
        if not self.change_tracker.mark_schema():
            return
        f = self.model["flds"][self.form.fieldList.currentRow()]
        self.mm.remove_field(self.model, f)
        gui_hooks.fields_did_delete_field(self, f)

        self.fillFields()
        self.form.fieldList.setCurrentRow(0)

    def onPosition(self, delta=-1):
        idx = self.currentIdx
        l = len(self.model["flds"])
        txt = getOnlyText(tr(TR.FIELDS_NEW_POSITION_1, val=l), default=str(idx + 1))
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
        if not self.change_tracker.mark_schema():
            return False
        # don't allow user to disable; it makes no sense
        self.form.sortField.setChecked(True)
        self.mm.set_sort_index(self.model, self.form.fieldList.currentRow())

    def moveField(self, pos):
        if not self.change_tracker.mark_schema():
            return False
        self.saveField()
        f = self.model["flds"][self.currentIdx]
        self.mm.reposition_field(self.model, f, pos - 1)
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
        font = f.fontFamily.currentFont().family()
        if fld["font"] != font:
            fld["font"] = font
            self.change_tracker.mark_basic()
        size = f.fontSize.value()
        if fld["size"] != size:
            fld["size"] = size
            self.change_tracker.mark_basic()
        sticky = f.sticky.isChecked()
        if fld["sticky"] != sticky:
            fld["sticky"] = sticky
            self.change_tracker.mark_basic()
        rtl = f.rtl.isChecked()
        if fld["rtl"] != rtl:
            fld["rtl"] = rtl
            self.change_tracker.mark_basic()

    def reject(self):
        if self.change_tracker.changed():
            if not askUser("Discard changes?"):
                return

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
            tooltip("Changes saved.", parent=self.mw)
            QDialog.accept(self)

        self.mw.taskman.with_progress(save, on_done, self)

    def onHelp(self):
        openHelp("editing?id=customizing-fields")
