# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from collections.abc import Callable, Sequence
from concurrent.futures import Future
from operator import itemgetter
from typing import Any

import aqt.clayout
from anki import stdmodels
from anki.collection import Collection
from anki.lang import without_unicode_isolation
from anki.models import NotetypeDict, NotetypeId, NotetypeNameIdUseCount
from anki.notes import Note
from aqt import AnkiQt, gui_hooks
from aqt.operations import QueryOp
from aqt.operations.notetype import (
    add_notetype_legacy,
    remove_notetype,
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
        restoreGeom(self, "models")

        self.setWindowFlags(
            self.windowFlags()
            | Qt.WindowType.WindowMaximizeButtonHint
            | Qt.WindowType.WindowMinimizeButtonHint
        )

        self.show()

    # Models
    ##########################################################################

    def maybe_select_provided_notetype(self) -> None:
        if not self.selected_notetype_id:
            self.form.modelsList.setCurrentRow(0)
            return
        for i, m in enumerate(self.models):
            if m.id == self.selected_notetype_id:
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

        qconnect(f.modelsList.itemDoubleClicked, self.onRename)

        def on_done(fut: Future) -> None:
            self.updateModelsList(fut.result())
            self.maybe_select_provided_notetype()

        self.mw.taskman.with_progress(self.col.models.all_use_counts, on_done, self)
        maybeHideClose(box)

    def refresh_list(self, *ignored_args: Any) -> None:
        QueryOp(
            parent=self,
            op=lambda col: col.models.all_use_counts(),
            success=self.updateModelsList,
        ).run_in_background()

    def onRename(self) -> None:
        nt = self.current_notetype()
        text, ok = getText(tr.actions_new_name(), default=nt["name"])
        if ok and text.strip():
            nt["name"] = text

            update_notetype_legacy(parent=self, notetype=nt).success(
                self.refresh_list
            ).run_in_background()

    def updateModelsList(self, notetypes: Sequence[NotetypeNameIdUseCount]) -> None:
        row = self.form.modelsList.currentRow()
        if row == -1:
            row = 0
        self.form.modelsList.clear()

        self.models = notetypes
        for m in self.models:
            mUse = tr.browsing_note_count(count=m.use_count)
            item = QListWidgetItem(f"{m.name} [{mUse}]")
            self.form.modelsList.addItem(item)
        self.form.modelsList.setCurrentRow(row)

    def current_notetype(self) -> NotetypeDict:
        row = self.form.modelsList.currentRow()
        return self.mm.get(NotetypeId(self.models[row].id))

    def onAdd(self) -> None:
        def on_success(notetype: NotetypeDict) -> None:
            # if legacy add-ons already added the notetype, skip adding
            if notetype["id"]:
                self.refresh_list()
                return

            # prompt for name
            text, ok = getText(tr.actions_name(), default=notetype["name"], parent=self)
            if not ok or not text.strip():
                return
            notetype["name"] = text

            add_notetype_legacy(parent=self, notetype=notetype).success(
                self.refresh_list
            ).run_in_background()

        AddModel(self.mw, on_success, self)

    def onDelete(self) -> None:
        if len(self.models) < 2:
            showInfo(tr.notetypes_please_add_another_note_type_first(), parent=self)
            return
        idx = self.form.modelsList.currentRow()
        if self.models[idx].use_count:
            msg = tr.notetypes_delete_this_note_type_and_all()
        else:
            msg = tr.notetypes_delete_this_unused_note_type()
        if not askUser(msg, parent=self):
            return

        tracker = ChangeTracker(self.mw)
        if not tracker.mark_schema():
            return

        nt = self.current_notetype()
        remove_notetype(parent=self, notetype_id=nt["id"]).success(
            lambda _: self.refresh_list()
        ).run_in_background()

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
            self.refresh_list
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
