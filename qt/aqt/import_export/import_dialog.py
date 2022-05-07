# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Stopgap / not production-ready."""

from __future__ import annotations

from typing import Any, Optional

import aqt.forms
import aqt.main
from anki.collection import CsvColumn
from aqt.import_export.importing import import_progress_update, show_import_log
from aqt.operations import CollectionOp
from aqt.qt import *
from aqt.utils import HelpPage, disable_help_button, getText, openHelp, showWarning, tr


class ChangeMap(QDialog):
    def __init__(self, mw: aqt.main.AnkiQt, model: dict, current: str) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.model = model
        self.frm = aqt.forms.changemap.Ui_ChangeMap()
        self.frm.setupUi(self)
        disable_help_button(self)
        n = 0
        setCurrent = False
        for field in self.model["flds"]:
            item = QListWidgetItem(tr.importing_map_to(val=field["name"]))
            self.frm.fields.addItem(item)
            if current == field["name"]:
                setCurrent = True
                self.frm.fields.setCurrentRow(n)
            n += 1
        self.frm.fields.addItem(QListWidgetItem(tr.importing_map_to_tags()))
        self.frm.fields.addItem(QListWidgetItem(tr.importing_ignore_field()))
        if not setCurrent:
            if current == "_tags":
                self.frm.fields.setCurrentRow(n)
            else:
                self.frm.fields.setCurrentRow(n + 1)
        self.field: Optional[str] = None

    def getField(self) -> str:
        self.exec()
        return self.field

    def accept(self) -> None:
        row = self.frm.fields.currentRow()
        if row < len(self.model["flds"]):
            self.field = self.model["flds"][row]["name"]
        elif row == self.frm.fields.count() - 2:
            self.field = "_tags"
        else:
            self.field = None
        QDialog.accept(self)

    def reject(self) -> None:
        self.accept()


# called by importFile() when importing a mappable file like .csv
# ImportType = Union[Importer,AnkiPackageImporter, TextImporter]


class ImportDialog(QDialog):
    _DEFAULT_FILE_DELIMITER = "\t"

    def __init__(self, mw: aqt.main.AnkiQt, path: str) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.path = path
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        qconnect(
            self.frm.buttonBox.button(QDialogButtonBox.StandardButton.Help).clicked,
            self.helpRequested,
        )
        disable_help_button(self)
        self.setupMappingFrame()
        self.setupOptions()
        self.modelChanged()
        qconnect(self.frm.autoDetect.clicked, self.onDelimiter)
        self.updateDelimiterButtonText(self._DEFAULT_FILE_DELIMITER)
        self.frm.allowHTML.setChecked(self.mw.pm.profile.get("allowHTML", True))
        qconnect(self.frm.importMode.currentIndexChanged, self.importModeChanged)
        self.frm.importMode.setCurrentIndex(self.mw.pm.profile.get("importMode", 1))
        self.frm.tagModified.setText(self.mw.pm.profile.get("tagModified", ""))
        self.frm.tagModified.setCol(self.mw.col)
        # import button
        b = QPushButton(tr.actions_import())
        self.frm.buttonBox.addButton(b, QDialogButtonBox.ButtonRole.AcceptRole)
        self.exec()

    def setupOptions(self) -> None:
        import aqt.deckchooser
        import aqt.modelchooser

        self.model = self.mw.col.models.current()
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.frm.modelArea, label=False
        )
        self.deck = aqt.deckchooser.DeckChooser(self.mw, self.frm.deckArea, label=False)

    def modelChanged(self, unused: Any = None) -> None:
        self.showMapping()

    def onDelimiter(self) -> None:

        # Open a modal dialog to enter an delimiter
        # Todo/Idea Constrain the maximum width, so it doesnt take up that much screen space
        delim, ok = getText(
            tr.importing_by_default_anki_will_detect_the(),
            self,
            help=HelpPage.IMPORTING,
        )

        # If the modal dialog has been confirmed, update the delimiter
        if ok:
            # Check if the entered value is valid and if not fallback to default
            # at the moment every single character entry as well as '\t' is valid

            delim = delim if len(delim) > 0 else self._DEFAULT_FILE_DELIMITER
            delim = delim.replace("\\t", "\t")  # un-escape it
            if len(delim) > 1:
                showWarning(
                    tr.importing_multicharacter_separators_are_not_supported_please()
                )
                return
            self.hideMapping()

            def updateDelim() -> None:
                self.updateDelimiterButtonText(delim)

            self.showMapping(hook=updateDelim)

        else:
            # If the operation has been canceled, do not do anything
            pass

    def updateDelimiterButtonText(self, d: str) -> None:
        if d == "\t":
            d = tr.importing_tab()
        elif d == ",":
            d = tr.importing_comma()
        elif d == " ":
            d = tr.studying_space()
        elif d == ";":
            d = tr.importing_semicolon()
        elif d == ":":
            d = tr.importing_colon()
        else:
            d = repr(d)
        txt = tr.importing_fields_separated_by(val=d)
        self.frm.autoDetect.setText(txt)
        self.delim = d

    def accept(self) -> None:
        # self.mw.pm.profile["importMode"] = self.importer.importMode
        self.mw.pm.profile["allowHTML"] = self.frm.allowHTML.isChecked()
        # self.mw.pm.profile["tagModified"] = self.importer.tagModified
        self.mw.col.set_aux_notetype_config(
            self.model["id"], "lastDeck", self.deck.selected_deck_id
        )
        self.close()

        CollectionOp(
            parent=self.mw,
            op=lambda col: col.import_csv(
                path=self.path,
                deck_id=self.deck.selected_deck_id,
                notetype_id=self.model["id"],
                delimiter=self.delim,
                columns=self.columns(),
                allow_html=self.frm.allowHTML.isChecked(),
            ),
        ).with_backend_progress(import_progress_update).success(
            show_import_log
        ).run_in_background()

    def setupMappingFrame(self) -> None:
        # qt seems to have a bug with adding/removing from a grid, so we add
        # to a separate object and add/remove that instead
        self.frame = QFrame(self.frm.mappingArea)
        self.frm.mappingArea.setWidget(self.frame)
        self.mapbox = QVBoxLayout(self.frame)
        self.mapbox.setContentsMargins(0, 0, 0, 0)
        self.mapwidget: Optional[QWidget] = None

    def hideMapping(self) -> None:
        self.frm.mappingGroup.hide()

    def showMapping(
        self, keepMapping: bool = False, hook: Optional[Callable] = None
    ) -> None:
        if hook:
            hook()
        if not keepMapping:
            self.mapping = [f["name"] for f in self.model["flds"]] + ["_tags"] + [None]
        self.frm.mappingGroup.show()
        # set up the mapping grid
        if self.mapwidget:
            self.mapbox.removeWidget(self.mapwidget)
            self.mapwidget.deleteLater()
        self.mapwidget = QWidget()
        self.mapbox.addWidget(self.mapwidget)
        self.grid = QGridLayout(self.mapwidget)
        self.mapwidget.setLayout(self.grid)
        self.grid.setContentsMargins(3, 3, 3, 3)
        self.grid.setSpacing(6)
        for (num, value) in enumerate(self.mapping):
            text = tr.importing_field_of_file_is(val=num + 1)
            self.grid.addWidget(QLabel(text), num, 0)
            if value == "_tags":
                text = tr.importing_mapped_to_tags()
            elif value:
                text = tr.importing_mapped_to(val=value)
            else:
                text = tr.importing_ignored()
            self.grid.addWidget(QLabel(text), num, 1)
            button = QPushButton(tr.importing_change())
            self.grid.addWidget(button, num, 2)
            qconnect(button.clicked, lambda _, s=self, n=num: s.changeMappingNum(n))

    def changeMappingNum(self, n: int) -> None:
        f = ChangeMap(self.mw, self.model, self.mapping[n]).getField()
        try:
            # make sure we don't have it twice
            index = self.mapping.index(f)
            self.mapping[index] = None
        except ValueError:
            pass
        self.mapping[n] = f
        self.showMapping(keepMapping=True)

    def reject(self) -> None:
        self.modelChooser.cleanup()
        self.deck.cleanup()
        QDialog.reject(self)

    def helpRequested(self) -> None:
        openHelp(HelpPage.IMPORTING)

    def importModeChanged(self, newImportMode: int) -> None:
        if newImportMode == 0:
            self.frm.tagModified.setEnabled(True)
        else:
            self.frm.tagModified.setEnabled(False)

    def columns(self) -> list[CsvColumn]:
        return [self.column_for_value(value) for value in self.mapping]

    def column_for_value(self, value: str) -> CsvColumn:
        if value == "_tags":
            return CsvColumn(other=CsvColumn.TAGS)
        elif value is None:
            return CsvColumn(other=CsvColumn.IGNORE)
        else:
            return CsvColumn(field=[f["name"] for f in self.model["flds"]].index(value))


def showUnicodeWarning() -> None:
    """Shorthand to show a standard warning."""
    showWarning(tr.importing_selected_file_was_not_in_utf8())
