# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable, Sequence
from concurrent.futures import Future
from operator import itemgetter

import aqt.clayout
from anki import stdmodels
from anki.collection import Collection, OpChangesWithCount, OpChangesWithId
from anki.lang import without_unicode_isolation
from anki.models import NotetypeDict, NotetypeId, NotetypeNameIdUseCount
from anki.notes import Note
from aqt import AnkiQt, gui_hooks
from aqt.operations import QueryOp
from aqt.operations.notetype import (
    add_notetype_legacy,
    remove_notetype,
    remove_notetypes,
    selected_notetype_ids_to_remove,
    update_notetype_legacy,
)
from aqt.qt import *
from aqt.schema_change_tracker import ChangeTracker
from aqt.utils import (
    HelpPage,
    askUser,
    disable_help_button,
    getText,
    maybeHideClose,
    openHelp,
    restoreGeom,
    saveGeom,
    showInfo,
    tooltip,
    tr,
)


class Models(QDialog):
    def __init__(
        self,
        mw: AnkiQt,
        parent: QWidget | None = None,
        fromMain: bool = False,
        selected_notetype_id: NotetypeId | None = None,
    ):
        self.mw = mw
        parent = parent or mw
        self.fromMain = fromMain
        self.selected_notetype_id = selected_notetype_id
        QDialog.__init__(self, parent or mw)
        self.col = mw.col.weakref()
        assert self.col
        self.mm = self.col.models
        self.form = aqt.forms.models.Ui_Dialog()
        self.form.setupUi(self)
        qconnect(
            self.form.buttonBox.helpRequested,
            lambda: openHelp(HelpPage.ADDING_A_NOTE_TYPE),
        )
        self.models: Sequence[NotetypeNameIdUseCount] = []
        self.setupModels()

        self.setWindowFlags(
            self.windowFlags()
            | Qt.WindowType.WindowMaximizeButtonHint
            | Qt.WindowType.WindowMinimizeButtonHint
        )
        restoreGeom(self, "models")

        self.show()

    # Models
    ##########################################################################

    def maybe_select_provided_notetype(
        self, selected_notetype_id: NotetypeId | None = None, row: int = 0
    ) -> None:
        """Select the provided notetype ID, if any.
        Otherwise the one at `self.selected_notetype_id`,
        otherwise the `row`-th element."""
        selected_notetype_id = selected_notetype_id or self.selected_notetype_id
        if not selected_notetype_id:
            self.form.modelsList.setCurrentRow(row)
            return
        for i, m in enumerate(self.models):
            if m.id == selected_notetype_id:
                self.form.modelsList.setCurrentRow(i)
                break

    def setupModels(self) -> None:
        self.model = None
        f = self.form
        box = f.buttonBox

        default_buttons = [
            (tr.actions_add(), self.onAdd),
            (tr.actions_rename(), self.onRename),
            (tr.actions_delete(), self.onDelete),
        ]

        if self.fromMain:
            default_buttons.extend(
                [
                    (tr.notetypes_fields(), self.onFields),
                    (tr.notetypes_cards(), self.onCards),
                ]
            )

        default_buttons.append((tr.notetypes_options(), self.onAdvanced))

        for label, func in gui_hooks.models_did_init_buttons(default_buttons, self):
            button = box.addButton(label, QDialogButtonBox.ButtonRole.ActionRole)
            qconnect(button.clicked, func)

        f.modelsList.setSelectionMode(QAbstractItemView.SelectionMode.ExtendedSelection)
        qconnect(f.modelsList.itemDoubleClicked, self.onRename)

        def on_done(fut: Future) -> None:
            self.updateModelsList(fut.result())
            self.maybe_select_provided_notetype()

        self.mw.taskman.with_progress(self.col.models.all_use_counts, on_done, self)
        maybeHideClose(box)

    def refresh_list(self, selected_notetype_id: NotetypeId | None = None) -> None:
        QueryOp(
            parent=self,
            op=lambda col: col.models.all_use_counts(),
            success=lambda notetypes: self.updateModelsList(
                notetypes, selected_notetype_id
            ),
        ).run_in_background()

    def onRename(self) -> None:
        nt = self.current_notetype()
        text, ok = getText(tr.actions_new_name(), default=nt["name"])
        if ok and text.strip():
            selected_notetype_id = nt["id"]
            nt["name"] = text

            update_notetype_legacy(parent=self, notetype=nt).success(
                lambda _: self.refresh_list(selected_notetype_id)
            ).run_in_background()

    def updateModelsList(
        self,
        notetypes: Sequence[NotetypeNameIdUseCount],
        selected_notetype_id: NotetypeId | None = None,
    ) -> None:
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        self.form.modelsList.clear()

        self.models = notetypes
        for m in self.models:
            mUse = tr.browsing_note_count(count=m.use_count)
            item = QListWidgetItem(f"{m.name} [{mUse}]")
            self.form.modelsList.addItem(item)
        self.maybe_select_provided_notetype(selected_notetype_id, row)

    def current_notetype(self) -> NotetypeDict:
        row = self.form.modelsList.currentRow()
        return self.mm.get(NotetypeId(self.models[row].id))

    def selected_notetype_rows(self) -> list[int]:
        rows = sorted({index.row() for index in self.form.modelsList.selectedIndexes()})
        if rows:
            return rows

        row = self.form.modelsList.currentRow()
        if row != -1:
            return [row]

        return []

    def onAdd(self) -> None:
        def on_success(notetype: NotetypeDict) -> None:
            # if legacy add-ons already added the notetype, skip adding
            nid = notetype["id"]
            if nid:
                self.refresh_list(nid)
                return

            # prompt for name
            text, ok = getText(tr.actions_name(), default=notetype["name"], parent=self)
            if not ok or not text.strip():
                return
            notetype["name"] = text

            def refresh_list(op: OpChangesWithId) -> None:
                self.refresh_list(NotetypeId(op.id))

            add_notetype_legacy(parent=self, notetype=notetype).success(
                refresh_list
            ).run_in_background()

        AddModel(self.mw, on_success, self)

    def onDelete(self) -> None:
        if len(self.models) < 2:
            showInfo(tr.notetypes_please_add_another_note_type_first(), parent=self)
            return

        selected_rows = self.selected_notetype_rows()
        if not selected_rows:
            return

        if len(selected_rows) > 1:
            self.onDeleteSelected(selected_rows)
            return

        idx = selected_rows[0]
        if self.models[idx].use_count:
            msg = tr.notetypes_delete_this_note_type_and_all()
        else:
            msg = tr.notetypes_delete_this_unused_note_type()
        if not askUser(msg, parent=self):
            return

        tracker = ChangeTracker(self.mw)
        if not tracker.mark_schema():
            return

        remove_notetype(
            parent=self, notetype_id=NotetypeId(self.models[idx].id)
        ).success(lambda _: self.refresh_list(None)).run_in_background()

    def onDeleteSelected(self, selected_rows: Sequence[int]) -> None:
        selected_notetype_ids = [
            NotetypeId(self.models[row].id)
            for row in selected_rows
            if 0 <= row < len(self.models)
        ]
        current_row = self.form.modelsList.currentRow()
        protected_notetype_id = (
            NotetypeId(self.models[current_row].id)
            if len(selected_notetype_ids) == len(self.models) and current_row != -1
            else None
        )
        notetype_ids = selected_notetype_ids_to_remove(
            self.models,
            selected_notetype_ids,
            protected_notetype_id,
        )
        if not notetype_ids:
            showInfo(tr.notetypes_please_add_another_note_type_first(), parent=self)
            return

        use_counts = {
            NotetypeId(notetype.id): notetype.use_count for notetype in self.models
        }
        has_notes = any(use_counts[notetype_id] for notetype_id in notetype_ids)
        msg = (
            tr.notetypes_delete_selected_note_types_and_all(count=len(notetype_ids))
            if has_notes
            else tr.notetypes_delete_selected_note_types(count=len(notetype_ids))
        )
        if not askUser(
            msg,
            parent=self,
        ):
            return

        tracker = ChangeTracker(self.mw)
        if not tracker.mark_schema():
            return

        def on_success(out: OpChangesWithCount) -> None:
            if out.count:
                tooltip(
                    tr.notetypes_selected_note_types_removed(count=out.count),
                    parent=self,
                )
            else:
                showInfo(tr.notetypes_please_add_another_note_type_first(), parent=self)
            self.refresh_list(None)

        remove_notetypes(
            parent=self,
            notetype_ids=notetype_ids,
            protected_notetype_id=protected_notetype_id,
        ).success(on_success).run_in_background()

    def onAdvanced(self) -> None:
        nt = self.current_notetype()
        d = QDialog(self)
        disable_help_button(d)
        frm = aqt.forms.modelopts.Ui_Dialog()
        frm.setupUi(d)
        frm.latexsvg.setChecked(nt.get("latexsvg", False))
        frm.latexHeader.setText(nt["latexPre"])
        frm.latexFooter.setText(nt["latexPost"])
        d.setWindowTitle(
            without_unicode_isolation(tr.actions_options_for(val=nt["name"]))
        )
        qconnect(frm.buttonBox.helpRequested, lambda: openHelp(HelpPage.LATEX))
        restoreGeom(d, "modelopts")
        gui_hooks.models_advanced_will_show(d)
        d.exec()
        saveGeom(d, "modelopts")
        nt["latexsvg"] = frm.latexsvg.isChecked()
        nt["latexPre"] = str(frm.latexHeader.toPlainText())
        nt["latexPost"] = str(frm.latexFooter.toPlainText())
        update_notetype_legacy(parent=self, notetype=nt).success(
            lambda _: self.refresh_list(nt["id"])
        ).run_in_background()

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

    def reject(self) -> None:
        saveGeom(self, "models")
        QDialog.reject(self)


class AddModel(QDialog):
    model: NotetypeDict | None

    def __init__(
        self,
        mw: AnkiQt,
        on_success: Callable[[NotetypeDict], None],
        parent: QWidget | None = None,
    ) -> None:
        self.parent_ = parent or mw
        self.mw = mw
        self.col = mw.col
        QDialog.__init__(self, self.parent_, Qt.WindowType.Window)
        self.model = None
        self.dialog = aqt.forms.addmodel.Ui_Dialog()
        self.dialog.setupUi(self)
        self.setWindowModality(Qt.WindowModality.ApplicationModal)
        disable_help_button(self)
        # standard models
        self.notetypes: list[NotetypeDict | Callable[[Collection], NotetypeDict]] = []
        for name, func in stdmodels.get_stock_notetypes(self.col):
            item = QListWidgetItem(tr.notetypes_add(val=name))
            self.dialog.models.addItem(item)
            self.notetypes.append(func)
        # add copies
        for m in sorted(self.col.models.all(), key=itemgetter("name")):
            item = QListWidgetItem(tr.notetypes_clone(val=m["name"]))
            self.dialog.models.addItem(item)
            self.notetypes.append(m)
        self.dialog.models.setCurrentRow(0)
        # the list widget will swallow the enter key
        s = QShortcut(QKeySequence("Return"), self)
        qconnect(s.activated, self.accept)
        # help
        qconnect(self.dialog.buttonBox.helpRequested, self.onHelp)
        self.on_success = on_success
        self.show()

    def reject(self) -> None:
        QDialog.reject(self)

    def accept(self) -> None:
        model = self.notetypes[self.dialog.models.currentRow()]
        if isinstance(model, dict):
            # clone existing
            self.model = self.mw.col.models.copy(model, add=False)
        else:
            self.model = model(self.col)
        QDialog.accept(self)
        # On mac, we need to allow time for the existing modal to close or
        # Qt gets confused.
        self.mw.progress.single_shot(100, lambda: self.on_success(self.model), True)

    def onHelp(self) -> None:
        openHelp(HelpPage.ADDING_A_NOTE_TYPE)
