# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import List, Optional, Sequence

import aqt
from anki.notes import NoteId
from aqt import AnkiQt
from aqt.operations import QueryOp
from aqt.operations.note import find_and_replace
from aqt.operations.tag import find_and_replace_tag
from aqt.qt import *
from aqt.utils import (
    HelpPage,
    disable_help_button,
    openHelp,
    qconnect,
    restore_combo_history,
    restore_combo_index_for_session,
    restore_is_checked,
    restoreGeom,
    save_combo_history,
    save_combo_index_for_session,
    save_is_checked,
    saveGeom,
    tooltip,
    tr,
)


class FindAndReplaceDialog(QDialog):
    COMBO_NAME = "BrowserFindAndReplace"

    def __init__(
        self,
        parent: QWidget,
        *,
        mw: AnkiQt,
        note_ids: Sequence[NoteId],
        field: Optional[str] = None,
    ) -> None:
        """
        If 'field' is passed, only this is added to the field selector.
        Otherwise, the fields belonging to the 'note_ids' are added.
        """
        super().__init__(parent)
        self.mw = mw
        self.note_ids = note_ids
        self.field_names: List[str] = []
        self._field = field

        if field:
            self._show([field])
        elif note_ids:
            # fetch field names and then show
            QueryOp(
                parent=mw,
                op=lambda col: col.field_names_for_note_ids(note_ids),
                success=self._show,
            ).run_in_background()
        else:
            self._show([])

    def _show(self, field_names: Sequence[str]) -> None:
        # add "all fields" and "tags" to the top of the list
        self.field_names = [
            tr.browsing_all_fields(),
            tr.editing_tags(),
        ] + list(field_names)

        disable_help_button(self)
        self.form = aqt.forms.findreplace.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowModality(Qt.WindowModal)

        self._find_history = restore_combo_history(
            self.form.find, self.COMBO_NAME + "Find"
        )
        self.form.find.completer().setCaseSensitivity(Qt.CaseSensitive)
        self._replace_history = restore_combo_history(
            self.form.replace, self.COMBO_NAME + "Replace"
        )
        self.form.replace.completer().setCaseSensitivity(Qt.CaseSensitive)

        if not self.note_ids:
            # no selected notes to affect
            self.form.selected_notes.setChecked(False)
            self.form.selected_notes.setEnabled(False)
        elif self._field:
            self.form.selected_notes.setChecked(False)

        restore_is_checked(self.form.re, self.COMBO_NAME + "Regex")
        restore_is_checked(self.form.ignoreCase, self.COMBO_NAME + "ignoreCase")

        self.form.field.addItems(self.field_names)
        if self._field:
            self.form.field.setCurrentIndex(self.field_names.index(self._field))
        else:
            restore_combo_index_for_session(
                self.form.field, self.field_names, self.COMBO_NAME + "Field"
            )

        qconnect(self.form.buttonBox.helpRequested, self.show_help)

        restoreGeom(self, "findreplace")
        self.show()
        self.form.find.setFocus()

    def accept(self) -> None:
        saveGeom(self, "findreplace")
        save_combo_index_for_session(self.form.field, self.COMBO_NAME + "Field")

        search = save_combo_history(
            self.form.find, self._find_history, self.COMBO_NAME + "Find"
        )
        replace = save_combo_history(
            self.form.replace, self._replace_history, self.COMBO_NAME + "Replace"
        )
        regex = self.form.re.isChecked()
        match_case = not self.form.ignoreCase.isChecked()
        save_is_checked(self.form.re, self.COMBO_NAME + "Regex")
        save_is_checked(self.form.ignoreCase, self.COMBO_NAME + "ignoreCase")

        if not self.form.selected_notes.isChecked():
            # an empty list means *all* notes
            self.note_ids = []

        # tags?
        if self.form.field.currentIndex() == 1:
            op = find_and_replace_tag(
                parent=self.parentWidget(),
                note_ids=self.note_ids,
                search=search,
                replacement=replace,
                regex=regex,
                match_case=match_case,
            )
        else:
            # fields
            if self.form.field.currentIndex() == 0:
                field = None
            else:
                field = self.field_names[self.form.field.currentIndex()]

            op = find_and_replace(
                parent=self.parentWidget(),
                note_ids=self.note_ids,
                search=search,
                replacement=replace,
                regex=regex,
                field_name=field,
                match_case=match_case,
            )

        if not self.note_ids:
            op.success(
                lambda out: tooltip(
                    tr.browsing_notes_updated(count=out.count),
                    parent=self.parentWidget(),
                )
            )
        op.run_in_background()

        super().accept()

    def show_help(self) -> None:
        openHelp(HelpPage.BROWSING_FIND_AND_REPLACE)
