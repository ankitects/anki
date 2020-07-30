# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from operator import itemgetter
from typing import Any, List, Optional, Sequence

import aqt.clayout
from anki import stdmodels
from anki.backend_pb2 import NoteTypeNameIDUseCount
from anki.lang import _, ngettext
from anki.models import NoteType
from anki.notes import Note
from anki.rsbackend import pb
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import (
    askUser,
    getText,
    maybeHideClose,
    openHelp,
    restoreGeom,
    saveGeom,
    showInfo,
)


class Models(QDialog):
    def __init__(self, mw: AnkiQt, parent=None, fromMain=False):
        self.mw = mw
        parent = parent or mw
        self.fromMain = fromMain
        QDialog.__init__(self, parent, Qt.Window)
        self.col = mw.col.weakref()
        assert self.col
        self.mm = self.col.models
        self.mw.checkpoint(_("Note Types"))
        self.form = aqt.forms.models.Ui_Dialog()
        self.form.setupUi(self)
        qconnect(self.form.buttonBox.helpRequested, lambda: openHelp("notetypes"))
        self.models: List[pb.NoteTypeNameIDUseCount] = []
        self.setupModels()
        restoreGeom(self, "models")
        self.exec_()

    # Models
    ##########################################################################

    def setupModels(self) -> None:
        self.model = None
        f = self.form
        box = f.buttonBox
        t = QDialogButtonBox.ActionRole
        b = box.addButton(_("Add"), t)
        qconnect(b.clicked, self.onAdd)
        b = box.addButton(_("Rename"), t)
        qconnect(b.clicked, self.onRename)
        b = box.addButton(_("Delete"), t)
        qconnect(b.clicked, self.onDelete)
        if self.fromMain:
            b = box.addButton(_("Fields..."), t)
            qconnect(b.clicked, self.onFields)
            b = box.addButton(_("Cards..."), t)
            qconnect(b.clicked, self.onCards)
        b = box.addButton(_("Options..."), t)
        qconnect(b.clicked, self.onAdvanced)
        qconnect(f.modelsList.itemDoubleClicked, self.onRename)

        def on_done(fut) -> None:
            self.updateModelsList(fut.result())

        self.mw.taskman.with_progress(self.col.models.all_use_counts, on_done, self)
        f.modelsList.setCurrentRow(0)
        maybeHideClose(box)

    def onRename(self) -> None:
        nt = self.current_notetype()
        txt = getText(_("New name:"), default=nt["name"])
        if txt[1] and txt[0]:
            nt["name"] = txt[0]
            self.saveAndRefresh(nt)

    def saveAndRefresh(self, nt: NoteType) -> None:
        def save() -> Sequence[pb.NoteTypeNameIDUseCount]:
            self.mm.save(nt)
            return self.col.models.all_use_counts()

        def on_done(fut) -> None:
            self.updateModelsList(fut.result())

        self.mw.taskman.with_progress(save, on_done, self)

    def updateModelsList(self, notetypes: List[NoteTypeNameIDUseCount]) -> None:
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        self.form.modelsList.clear()

        self.models = notetypes
        for m in self.models:
            mUse = ngettext("%d note", "%d notes", m.use_count) % m.use_count
            item = QListWidgetItem("%s [%s]" % (m.name, mUse))
            self.form.modelsList.addItem(item)
        self.form.modelsList.setCurrentRow(row)

    def current_notetype(self) -> NoteType:
        row = self.form.modelsList.currentRow()
        return self.mm.get(self.models[row].id)

    def onAdd(self) -> None:
        m = AddModel(self.mw, self).get()
        if m:
            txt = getText(_("Name:"), default=m["name"])[0]
            if txt:
                m["name"] = txt
            self.saveAndRefresh(m)

    def onDelete(self) -> None:
        if len(self.models) < 2:
            showInfo(_("Please add another note type first."), parent=self)
            return
        idx = self.form.modelsList.currentRow()
        if self.models[idx].use_count:
            msg = _("Delete this note type and all its cards?")
        else:
            msg = _("Delete this unused note type?")
        if not askUser(msg, parent=self):
            return

        self.col.modSchema(check=True)

        nt = self.current_notetype()

        def save() -> Sequence[pb.NoteTypeNameIDUseCount]:
            self.mm.rem(nt)
            return self.col.models.all_use_counts()

        def on_done(fut) -> None:
            self.updateModelsList(fut.result())

        self.mw.taskman.with_progress(save, on_done, self)

    def onAdvanced(self) -> None:
        nt = self.current_notetype()
        d = QDialog(self)
        frm = aqt.forms.modelopts.Ui_Dialog()
        frm.setupUi(d)
        frm.latexsvg.setChecked(nt.get("latexsvg", False))
        frm.latexHeader.setText(nt["latexPre"])
        frm.latexFooter.setText(nt["latexPost"])
        d.setWindowTitle(_("Options for %s") % nt["name"])
        qconnect(frm.buttonBox.helpRequested, lambda: openHelp("latex"))
        restoreGeom(d, "modelopts")
        gui_hooks.models_advanced_will_show(d)
        d.exec_()
        saveGeom(d, "modelopts")
        nt["latexsvg"] = frm.latexsvg.isChecked()
        nt["latexPre"] = str(frm.latexHeader.toPlainText())
        nt["latexPost"] = str(frm.latexFooter.toPlainText())
        self.saveAndRefresh(nt)

    def _tmpNote(self) -> Note:
        nt = self.current_notetype()
        return Note(self.col, nt)

    def onFields(self) -> None:
        from aqt.fields import FieldDialog

        FieldDialog(self.mw, self.current_notetype(), parent=self)

    def onCards(self) -> None:
        from aqt.clayout import CardLayout

        n = self._tmpNote()
        CardLayout(self.mw, n, ord=0, parent=self, fill_empty=True)

    # Cleanup
    ##########################################################################

    # need to flush model on change or reject

    def reject(self) -> None:
        self.mw.reset()
        saveGeom(self, "models")
        QDialog.reject(self)


class AddModel(QDialog):
    def __init__(self, mw: AnkiQt, parent: Optional[QWidget] = None):
        self.parent_ = parent or mw
        self.mw = mw
        self.col = mw.col
        QDialog.__init__(self, self.parent_, Qt.Window)
        self.model = None
        self.dialog = aqt.forms.addmodel.Ui_Dialog()
        self.dialog.setupUi(self)
        # standard models
        self.models = []
        for (name, func) in stdmodels.get_stock_notetypes(self.col):
            item = QListWidgetItem(_("Add: %s") % name)
            self.dialog.models.addItem(item)
            self.models.append((True, func))
        # add copies
        for m in sorted(self.col.models.all(), key=itemgetter("name")):
            item = QListWidgetItem(_("Clone: %s") % m["name"])
            self.dialog.models.addItem(item)
            self.models.append((False, m))  # type: ignore
        self.dialog.models.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        qconnect(s.activated, self.accept)
        # help
        qconnect(self.dialog.buttonBox.helpRequested, self.onHelp)

    def get(self) -> Any:
        self.exec_()
        return self.model

    def reject(self) -> None:
        QDialog.reject(self)

    def accept(self) -> None:
        (isStd, model) = self.models[self.dialog.models.currentRow()]
        if isStd:
            # create
            self.model = model(self.col)
        else:
            # add copy to deck
            self.model = self.mw.col.models.copy(model)
            self.mw.col.models.setCurrent(self.model)
        QDialog.accept(self)

    def onHelp(self) -> None:
        openHelp("notetypes")
