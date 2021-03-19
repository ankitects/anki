# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from typing import List, Optional, Sequence

import aqt
from anki.lang import TR
from aqt import AnkiQt, QWidget
from aqt.qt import QDialog, Qt
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
    show_invalid_search_error,
    tooltip,
    tr,
)


def find_and_replace(
    *,
    mw: AnkiQt,
    parent: QWidget,
    note_ids: Sequence[int],
    search: str,
    replacement: str,
    regex: bool,
    field_name: Optional[str],
    match_case: bool,
) -> None:
    mw.perform_op(
        lambda: mw.col.find_and_replace(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            field_name=field_name,
            match_case=match_case,
        ),
        success=lambda out: tooltip(
            tr(TR.FINDREPLACE_NOTES_UPDATED, changed=out.count, total=len(note_ids)),
            parent=parent,
        ),
        failure=lambda exc: show_invalid_search_error(exc, parent=parent),
    )


def find_and_replace_tag(
    *,
    mw: AnkiQt,
    parent: QWidget,
    note_ids: Sequence[int],
    search: str,
    replacement: str,
    regex: bool,
    match_case: bool,
) -> None:
    mw.perform_op(
        lambda: mw.col.tags.find_and_replace(
            note_ids=note_ids,
            search=search,
            replacement=replacement,
            regex=regex,
            match_case=match_case,
        ),
        success=lambda out: tooltip(
            tr(TR.FINDREPLACE_NOTES_UPDATED, changed=out.count, total=len(note_ids)),
            parent=parent,
        ),
        failure=lambda exc: show_invalid_search_error(exc, parent=parent),
    )


class FindAndReplaceDialog(QDialog):
    COMBO_NAME = "BrowserFindAndReplace"

    def __init__(self, parent: QWidget, *, mw: AnkiQt, note_ids: Sequence[int]) -> None:
        super().__init__(parent)
        self.mw = mw
        self.note_ids = note_ids
        self.field_names: List[str] = []

        # fetch field names and then show
        mw.query_op(
            lambda: mw.col.field_names_for_note_ids(note_ids),
            success=self._show,
        )

    def _show(self, field_names: Sequence[str]) -> None:
        # add "all fields" and "tags" to the top of the list
        self.field_names = [
            tr(TR.BROWSING_ALL_FIELDS),
            tr(TR.EDITING_TAGS),
        ] + list(field_names)

        disable_help_button(self)
        self.form = aqt.forms.findreplace.Ui_Dialog()
        self.form.setupUi(self)
        self.setWindowModality(Qt.WindowModal)

        self._find_history = restore_combo_history(
            self.form.find, self.COMBO_NAME + "Find"
        )
        self.form.find.completer().setCaseSensitivity(True)
        self._replace_history = restore_combo_history(
            self.form.replace, self.COMBO_NAME + "Replace"
        )
        self.form.replace.completer().setCaseSensitivity(True)

        restore_is_checked(self.form.re, self.COMBO_NAME + "Regex")
        restore_is_checked(self.form.ignoreCase, self.COMBO_NAME + "ignoreCase")

        self.form.field.addItems(self.field_names)
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

        if self.form.field.currentIndex() == 1:
            # tags
            find_and_replace_tag(
                mw=self.mw,
                parent=self.parentWidget(),
                note_ids=self.note_ids,
                search=search,
                replacement=replace,
                regex=regex,
                match_case=match_case,
            )
            return

        if self.form.field.currentIndex() == 0:
            field = None
        else:
            field = self.field_names[self.form.field.currentIndex() - 2]

        find_and_replace(
            mw=self.mw,
            parent=self.parentWidget(),
            note_ids=self.note_ids,
            search=search,
            replacement=replace,
            regex=regex,
            field_name=field,
            match_case=match_case,
        )

        super().accept()

    def show_help(self) -> None:
        openHelp(HelpPage.BROWSING_FIND_AND_REPLACE)
