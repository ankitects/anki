# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""Stopgap / not production-ready."""

from __future__ import annotations

from typing import Optional, Sequence

import aqt.forms
import aqt.main
from anki.collection import CsvColumn, CsvMetadata
from anki.decks import DeckId
from anki.models import NotetypeDict, NotetypeId
from aqt.import_export.importing import import_progress_update, show_import_log
from aqt.operations import CollectionOp, QueryOp
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
    def __init__(self, mw: aqt.main.AnkiQt, path: str) -> None:
        QDialog.__init__(self, mw, Qt.WindowType.Window)
        self.mw = mw
        self.path = path
        self.options = CsvMetadata()
        QueryOp(
            parent=self,
            op=lambda col: col.get_csv_metadata(path, None),
            success=self._run,
        ).run_in_background()
        self._setup_ui()

    def _setup_ui(self) -> None:
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        qconnect(
            self.frm.buttonBox.button(QDialogButtonBox.StandardButton.Help).clicked,
            self.helpRequested,
        )
        disable_help_button(self)
        self.setupMappingFrame()
        qconnect(self.frm.autoDetect.clicked, self.onDelimiter)
        qconnect(self.frm.importMode.currentIndexChanged, self.importModeChanged)
        # import button
        b = QPushButton(tr.actions_import())
        self.frm.buttonBox.addButton(b, QDialogButtonBox.ButtonRole.AcceptRole)

    def _run(self, options: CsvMetadata) -> None:
        self._setup_options(options)
        self._setup_choosers()
        self.column_map = ColumnMap(self.columns, self.model)
        self._render_mapping()
        self._set_delimiter_button_text()
        self.frm.allowHTML.setChecked(self.is_html)
        self.frm.importMode.setCurrentIndex(self.mw.pm.profile.get("importMode", 1))
        self.frm.tagModified.setText(self.tags)
        self.frm.tagModified.setCol(self.mw.col)
        self.show()

    def _setup_options(self, options: CsvMetadata) -> None:
        self.delimiter = options.delimiter
        self.tags = self.options.tags or self.mw.pm.profile.get("tagModified", "")
        self.columns = options.columns
        self.deck_id = DeckId(
            self.options.deck_id or self.mw.col.get_config("curDeck", default=1)
        )
        if options.notetype_id:
            self.notetype_id = NotetypeId(self.options.notetype_id)
            self.model = self.mw.col.models.get(self.notetype_id)
        else:
            self.model = self.mw.col.models.current()
            self.notetype_id = self.model["id"]
        if self.options.is_html is None:
            self.is_html = self.mw.pm.profile.get("allowHTML", True)
        else:
            self.is_html = self.options.is_html

    def _setup_choosers(self) -> None:
        import aqt.deckchooser
        import aqt.notetypechooser

        def change_notetype(ntid: NotetypeId) -> None:
            self.model = self.mw.col.models.get(ntid)
            self.notetype_id = ntid
            self.column_map = ColumnMap(self.columns, self.model)
            self._render_mapping()

        self.modelChooser = aqt.notetypechooser.NotetypeChooser(
            mw=self.mw,
            widget=self.frm.modelArea,
            starting_notetype_id=self.notetype_id,
            on_notetype_changed=change_notetype,
        )
        self.deck = aqt.deckchooser.DeckChooser(self.mw, self.frm.deckArea, label=False)

    def onDelimiter(self) -> None:
        # Open a modal dialog to enter an delimiter
        # Todo/Idea Constrain the maximum width, so it doesnt take up that much screen space
        delim, ok = getText(
            tr.importing_by_default_anki_will_detect_the(),
            self,
            help=HelpPage.IMPORTING,
        )

        if not ok:
            return
        # Check if the entered value is valid and if not fallback to default
        # at the moment every single character entry as well as '\t' is valid
        delim = delim if len(delim) > 0 else "\t"
        delim = delim.replace("\\t", "\t")  # un-escape it
        delimiter = ord(delim)
        if delimiter > 255:
            showWarning(
                tr.importing_multicharacter_separators_are_not_supported_please()
            )
            return

        # self.hideMapping()
        # self.showMapping(hook=_update)
        self.delimiter = delimiter
        self._set_delimiter_button_text()

        def _update_columns(options: CsvMetadata) -> None:
            self.columns = options.columns
            self.column_map = ColumnMap(self.columns, self.model)
            self._render_mapping()

        QueryOp(
            parent=self,
            op=lambda col: col.get_csv_metadata(self.path, delimiter),
            success=_update_columns,
        ).run_in_background()

    def _set_delimiter_button_text(self) -> None:
        d = chr(self.delimiter)
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
                delimiter=self.delimiter,
                columns=self.column_map.csv_columns(),
                is_html=self.frm.allowHTML.isChecked(),
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

    def _render_mapping(self) -> None:
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
        for (num, column) in enumerate(self.column_map.columns):
            self.grid.addWidget(QLabel(column), num, 0)
            self.grid.addWidget(QLabel(self.column_map.map_label(num)), num, 1)
            button = QPushButton(tr.importing_change())
            self.grid.addWidget(button, num, 2)
            qconnect(button.clicked, lambda _, s=self, n=num: s.changeMappingNum(n))

    def changeMappingNum(self, n: int) -> None:
        f = ChangeMap(self.mw, self.model, self.column_map.map[n]).getField()
        self.column_map.update(n, f)
        self._render_mapping()

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


class ColumnMap:
    columns: list[str]
    fields: list[str]
    map: list[str]

    def __init__(self, columns: Sequence[str], notetype: NotetypeDict) -> None:
        self.columns = list(columns)
        self.fields = [f["name"] for f in notetype["flds"]] + ["_tags"]
        self.map = [""] * len(self.columns)
        for i in range(min(len(self.fields), len(self.columns))):
            self.map[i] = self.fields[i]

    def map_label(self, num: int) -> str:
        name = self.map[num]
        if not name:
            return tr.importing_ignored()
        if name == "_tags":
            tr.importing_mapped_to_tags()
        return tr.importing_mapped_to(val=name)

    def update(self, column: int, new_field: str | None) -> None:
        if new_field:
            try:
                idx = self.map.index(new_field)
            except ValueError:
                pass
            else:
                self.map[idx] = ""
        self.map[column] = new_field or ""

    def csv_columns(self) -> list[CsvColumn]:
        return [self._column_for_name(name) for name in self.map]

    def _column_for_name(self, name: str) -> CsvColumn:
        if not name:
            return CsvColumn(other=CsvColumn.IGNORE)
        if name == "_tags":
            return CsvColumn(other=CsvColumn.TAGS)
        return CsvColumn(field=self.fields.index(name))
