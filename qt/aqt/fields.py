# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import aqt
import aqt.forms
import aqt.operations
from anki.collection import OpChanges
from anki.lang import without_unicode_isolation
from anki.models import NotetypeDict
from aqt import AnkiQt, gui_hooks
from aqt.operations.notetype import update_notetype_legacy
from aqt.qt import *
from aqt.schema_change_tracker import ChangeTracker
from aqt.utils import (
    HelpPage,
    askUser,
    disable_help_button,
    getOnlyText,
    openHelp,
    show_warning,
    tooltip,
    tr,
)


class FieldDialog(QDialog):
    def __init__(
        self,
        mw: AnkiQt,
        nt: NotetypeDict,
        parent: QWidget | None = None,
        open_at: int = 0,
    ) -> None:
        QDialog.__init__(self, parent or mw)
        mw.garbage_collect_on_dialog_finish(self)
        self.mw = mw
        self.col = self.mw.col
        self.mm = self.mw.col.models
        self.model = nt
        self.mm._remove_from_cache(self.model["id"])
        self.change_tracker = ChangeTracker(self.mw)

        self.setWindowTitle(
            without_unicode_isolation(tr.fields_fields_for(val=self.model["name"]))
        )

        self.form = aqt.forms.fields.Ui_Dialog()
        self.form.setupUi(self)
        self.webview = None

        disable_help_button(self)
        help_button = self.form.buttonBox.button(QDialogButtonBox.StandardButton.Help)
        assert help_button is not None
        help_button.setAutoDefault(False)

        cancel_button = self.form.buttonBox.button(
            QDialogButtonBox.StandardButton.Cancel
        )
        assert cancel_button is not None
        cancel_button.setAutoDefault(False)

        save_button = self.form.buttonBox.button(QDialogButtonBox.StandardButton.Save)
        assert save_button is not None
        save_button.setAutoDefault(False)

        self.currentIdx: int | None = None
        self.fillFields()
        self.setupSignals()
        self.form.fieldList.setDragDropMode(QAbstractItemView.DragDropMode.InternalMove)
        self.form.fieldList.dropEvent = self.onDrop  # type: ignore[assignment]
        self.form.fieldList.setCurrentRow(open_at)
        self.exec()

    def _on_bridge_cmd(self, cmd: str) -> bool:
        return False

    ##########################################################################

    def fillFields(self) -> None:
        self.currentIdx = None
        self.form.fieldList.clear()
        for c, f in enumerate(self.model["flds"]):
            self.form.fieldList.addItem(f"{c + 1}: {f['name']}")

    def setupSignals(self) -> None:
        f = self.form
        qconnect(f.fieldList.currentRowChanged, self.onRowChange)
        qconnect(f.fieldAdd.clicked, self.onAdd)
        qconnect(f.fieldDelete.clicked, self.onDelete)
        qconnect(f.fieldRename.clicked, self.onRename)
        qconnect(f.fieldPosition.clicked, self.onPosition)
        qconnect(f.sortField.clicked, self.onSortField)
        qconnect(f.buttonBox.helpRequested, self.onHelp)

    def onDrop(self, ev: QDropEvent) -> None:
        fieldList = self.form.fieldList
        indicatorPos = fieldList.dropIndicatorPosition()
        if qtmajor == 5:
            pos = ev.pos()  # type: ignore
        else:
            pos = ev.position().toPoint()
        dropPos = fieldList.indexAt(pos).row()
        idx = self.currentIdx
        if dropPos == idx:
            return
        if (
            indicatorPos == QAbstractItemView.DropIndicatorPosition.OnViewport
        ):  # to bottom.
            movePos = fieldList.count() - 1
        elif indicatorPos == QAbstractItemView.DropIndicatorPosition.AboveItem:
            movePos = dropPos
        elif indicatorPos == QAbstractItemView.DropIndicatorPosition.BelowItem:
            movePos = dropPos + 1
        else:
            # for pylint
            return
        # the item in idx is removed thus subtract 1.
        assert idx is not None
        if idx < dropPos:
            movePos -= 1
        self.moveField(movePos + 1)  # convert to 1 based.

    def onRowChange(self, idx: int) -> None:
        if idx == -1:
            return
        self.saveField()
        self.loadField(idx)

    def _uniqueName(
        self, prompt: str, ignoreOrd: int | None = None, old: str = ""
    ) -> str | None:
        txt = getOnlyText(prompt, default=old).replace('"', "").strip()
        if not txt:
            return None
        if txt[0] in "#^/":
            show_warning(tr.fields_name_first_letter_not_valid())
            return None
        for letter in """:{"}""":
            if letter in txt:
                show_warning(tr.fields_name_invalid_letter())
                return None
        for f in self.model["flds"]:
            if ignoreOrd is not None and f["ord"] == ignoreOrd:
                continue
            if f["name"] == txt:
                show_warning(tr.fields_that_field_name_is_already_used())
                return None
        return txt

    def onRename(self) -> None:
        if self.currentIdx is None:
            return

        idx = self.currentIdx
        f = self.model["flds"][idx]
        name = self._uniqueName(tr.actions_new_name(), self.currentIdx, f["name"])
        if not name:
            return

        old_name = f["name"]
        self.change_tracker.mark_basic()
        self.mm.rename_field(self.model, f, name)
        gui_hooks.fields_did_rename_field(self, f, old_name)

        self.saveField()
        self.fillFields()
        self.form.fieldList.setCurrentRow(idx)

    def onAdd(self) -> None:
        name = self._uniqueName(tr.fields_field_name())
        if not name:
            return
        if not self.change_tracker.mark_schema():
            return
        self.saveField()
        f = self.mm.new_field(name)
        self.mm.add_field(self.model, f)
        gui_hooks.fields_did_add_field(self, f)

        self.fillFields()
        self.form.fieldList.setCurrentRow(len(self.model["flds"]) - 1)

    def onDelete(self) -> None:
        if len(self.model["flds"]) < 2:
            show_warning(tr.fields_notes_require_at_least_one_field())
            return
        field = self.model["flds"][self.form.fieldList.currentRow()]
        if field["preventDeletion"]:
            show_warning(tr.fields_field_is_required())
            return
        count = self.mm.use_count(self.model)
        c = tr.browsing_note_count(count=count)
        if not askUser(tr.fields_delete_field_from(val=c)):
            return
        if not self.change_tracker.mark_schema():
            return
        self.mm.remove_field(self.model, field)
        gui_hooks.fields_did_delete_field(self, field)

        self.fillFields()
        self.form.fieldList.setCurrentRow(0)

    def onPosition(self, delta: int = -1) -> None:
        idx = self.currentIdx
        assert idx is not None
        l = len(self.model["flds"])
        txt = getOnlyText(tr.fields_new_position_1(val=l), default=str(idx + 1))
        if not txt:
            return
        try:
            pos = int(txt)
        except ValueError:
            return
        if not 0 < pos <= l:
            return
        self.moveField(pos)

    def onSortField(self) -> None:
        if not self.change_tracker.mark_schema():
            return
        # don't allow user to disable; it makes no sense
        self.form.sortField.setChecked(True)
        self.mm.set_sort_index(self.model, self.form.fieldList.currentRow())

    def moveField(self, pos: int) -> None:
        if not self.change_tracker.mark_schema():
            return
        self.saveField()
        f = self.model["flds"][self.currentIdx]
        self.mm.reposition_field(self.model, f, pos - 1)
        self.fillFields()
        self.form.fieldList.setCurrentRow(pos - 1)

    def loadField(self, idx: int) -> None:
        self.currentIdx = idx
        fld = self.model["flds"][idx]
        f = self.form
        f.fontFamily.setCurrentFont(QFont(fld["font"]))
        f.fontSize.setValue(fld["size"])
        f.sortField.setChecked(self.model["sortf"] == fld["ord"])
        f.rtl.setChecked(fld["rtl"])
        f.plainTextByDefault.setChecked(fld["plainText"])
        f.collapseByDefault.setChecked(fld["collapsed"])
        f.excludeFromSearch.setChecked(fld["excludeFromSearch"])
        f.fieldDescription.setText(fld.get("description", ""))

    def saveField(self) -> None:
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
        rtl = f.rtl.isChecked()
        if fld["rtl"] != rtl:
            fld["rtl"] = rtl
            self.change_tracker.mark_basic()
        plain_text = f.plainTextByDefault.isChecked()
        if fld["plainText"] != plain_text:
            fld["plainText"] = plain_text
            self.change_tracker.mark_basic()
        collapsed = f.collapseByDefault.isChecked()
        if fld["collapsed"] != collapsed:
            fld["collapsed"] = collapsed
            self.change_tracker.mark_basic()
        exclude_from_search = f.excludeFromSearch.isChecked()
        if fld["excludeFromSearch"] != exclude_from_search:
            fld["excludeFromSearch"] = exclude_from_search
            self.change_tracker.mark_basic()
        desc = f.fieldDescription.text()
        if fld.get("description", "") != desc:
            fld["description"] = desc
            self.change_tracker.mark_basic()

    def reject(self) -> None:
        if self.webview:
            self.webview.cleanup()
            self.webview = None

        if self.change_tracker.changed():
            if not askUser("Discard changes?"):
                return

        QDialog.reject(self)

    def accept(self) -> None:
        self.saveField()

        def on_done(changes: OpChanges) -> None:
            tooltip(tr.card_templates_changes_saved(), parent=self.parentWidget())
            QDialog.accept(self)

        update_notetype_legacy(
            parent=self.mw, notetype=self.model, skip_checks=True
        ).success(on_done).run_in_background()

    def onHelp(self) -> None:
        openHelp(HelpPage.CUSTOMIZING_FIELDS)
