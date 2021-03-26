# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
import json
import os
import re
import shutil
import traceback
import unicodedata
import zipfile
from concurrent.futures import Future
from typing import Any, Dict, Optional

import anki.importing as importing
import aqt.deckchooser
import aqt.forms
import aqt.modelchooser
from anki.importing.apkg import AnkiPackageImporter
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import (
    HelpPage,
    askUser,
    disable_help_button,
    getFile,
    getText,
    openHelp,
    showInfo,
    showText,
    showWarning,
    tooltip,
    tr,
)


class ChangeMap(QDialog):
    def __init__(self, mw: AnkiQt, model: Dict, current: str) -> None:
        QDialog.__init__(self, mw, Qt.Window)
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
        self.exec_()
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

    def __init__(self, mw: AnkiQt, importer: Any) -> None:
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.importer = importer
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        qconnect(
            self.frm.buttonBox.button(QDialogButtonBox.Help).clicked, self.helpRequested
        )
        disable_help_button(self)
        self.setupMappingFrame()
        self.setupOptions()
        self.modelChanged()
        self.frm.autoDetect.setVisible(self.importer.needDelimiter)
        gui_hooks.current_note_type_did_change.append(self.modelChanged)
        qconnect(self.frm.autoDetect.clicked, self.onDelimiter)
        self.updateDelimiterButtonText()
        self.frm.allowHTML.setChecked(self.mw.pm.profile.get("allowHTML", True))
        qconnect(self.frm.importMode.currentIndexChanged, self.importModeChanged)
        self.frm.importMode.setCurrentIndex(self.mw.pm.profile.get("importMode", 1))
        self.frm.tagModified.setText(self.mw.pm.profile.get("tagModified", ""))
        self.frm.tagModified.setCol(self.mw.col)
        # import button
        b = QPushButton(tr.actions_import())
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)
        self.exec_()

    def setupOptions(self) -> None:
        self.model = self.mw.col.models.current()
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.frm.modelArea, label=False
        )
        self.deck = aqt.deckchooser.DeckChooser(self.mw, self.frm.deckArea, label=False)

    def modelChanged(self, unused: Any = None) -> None:
        self.importer.model = self.mw.col.models.current()
        self.importer.initMapping()
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
                self.importer.delimiter = delim
                self.importer.updateDelimiter()
                self.updateDelimiterButtonText()

            self.showMapping(hook=updateDelim)

        else:
            # If the operation has been canceled, do not do anything
            pass

    def updateDelimiterButtonText(self) -> None:
        if not self.importer.needDelimiter:
            return
        if self.importer.delimiter:
            d = self.importer.delimiter
        else:
            d = self.importer.dialect.delimiter
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
        self.importer.mapping = self.mapping
        if not self.importer.mappingOk():
            showWarning(tr.importing_the_first_field_of_the_note())
            return
        self.importer.importMode = self.frm.importMode.currentIndex()
        self.mw.pm.profile["importMode"] = self.importer.importMode
        self.importer.allowHTML = self.frm.allowHTML.isChecked()
        self.mw.pm.profile["allowHTML"] = self.importer.allowHTML
        self.importer.tagModified = self.frm.tagModified.text()
        self.mw.pm.profile["tagModified"] = self.importer.tagModified
        did = self.deck.selected_deck_id
        self.importer.model["did"] = did
        self.mw.col.models.save(self.importer.model, updateReqs=False)
        self.mw.col.decks.select(did)
        self.mw.progress.start()
        self.mw.checkpoint(tr.actions_import())

        def on_done(future: Future) -> None:
            self.mw.progress.finish()

            try:
                future.result()
            except UnicodeDecodeError:
                showUnicodeWarning()
                return
            except Exception as e:
                msg = f"{tr.importing_failed_debug_info()}\n"
                err = repr(str(e))
                if "1-character string" in err:
                    msg += err
                elif "invalidTempFolder" in err:
                    msg += self.mw.errorHandler.tempFolderMsg()
                else:
                    msg += traceback.format_exc()
                showText(msg)
                return
            else:
                txt = f"{tr.importing_importing_complete()}\n"
                if self.importer.log:
                    txt += "\n".join(self.importer.log)
                self.close()
                showText(txt, plain_text_edit=True)
                self.mw.reset()

        self.mw.taskman.run_in_background(self.importer.run, on_done)

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
            self.mapping = self.importer.mapping
        self.frm.mappingGroup.show()
        assert self.importer.fields()
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
        fields = self.importer.fields()
        for num in range(len(self.mapping)):
            text = tr.importing_field_of_file_is(val=num + 1)
            self.grid.addWidget(QLabel(text), num, 0)
            if self.mapping[num] == "_tags":
                text = tr.importing_mapped_to_tags()
            elif self.mapping[num]:
                text = tr.importing_mapped_to(val=self.mapping[num])
            else:
                text = tr.importing_ignored()
            self.grid.addWidget(QLabel(text), num, 1)
            button = QPushButton(tr.importing_change())
            self.grid.addWidget(button, num, 2)
            qconnect(button.clicked, lambda _, s=self, n=num: s.changeMappingNum(n))

    def changeMappingNum(self, n: int) -> None:
        f = ChangeMap(self.mw, self.importer.model, self.mapping[n]).getField()
        try:
            # make sure we don't have it twice
            index = self.mapping.index(f)
            self.mapping[index] = None
        except ValueError:
            pass
        self.mapping[n] = f
        if getattr(self.importer, "delimiter", False):
            self.savedDelimiter = self.importer.delimiter

            def updateDelim() -> None:
                self.importer.delimiter = self.savedDelimiter

            self.showMapping(hook=updateDelim, keepMapping=True)
        else:
            self.showMapping(keepMapping=True)

    def reject(self) -> None:
        self.modelChooser.cleanup()
        self.deck.cleanup()
        gui_hooks.current_note_type_did_change.remove(self.modelChanged)
        QDialog.reject(self)

    def helpRequested(self) -> None:
        openHelp(HelpPage.IMPORTING)

    def importModeChanged(self, newImportMode: int) -> None:
        if newImportMode == 0:
            self.frm.tagModified.setEnabled(True)
        else:
            self.frm.tagModified.setEnabled(False)


def showUnicodeWarning() -> None:
    """Shorthand to show a standard warning."""
    showWarning(tr.importing_selected_file_was_not_in_utf8())


def onImport(mw: AnkiQt) -> None:
    filt = ";;".join([x[0] for x in importing.importers(mw.col)])
    file = getFile(mw, tr.actions_import(), None, key="import", filter=filt)
    if not file:
        return
    file = str(file)

    head, ext = os.path.splitext(file)
    ext = ext.lower()
    if ext == ".anki":
        showInfo(tr.importing_anki_files_are_from_a_very())
        return
    elif ext == ".anki2":
        showInfo(tr.importing_anki2_files_are_not_directly_importable())
        return

    importFile(mw, file)


def importFile(mw: AnkiQt, file: str) -> None:
    importerClass = None
    done = False
    for i in importing.importers(mw.col):
        if done:
            break
        for mext in re.findall(r"[( ]?\*\.(.+?)[) ]", i[0]):
            if file.endswith(f".{mext}"):
                importerClass = i[1]
                done = True
                break
    if not importerClass:
        # if no matches, assume TSV
        importerClass = importing.importers(mw.col)[0][1]
    importer = importerClass(mw.col, file)
    # need to show import dialog?
    if importer.needMapper:
        # make sure we can load the file first
        mw.progress.start(immediate=True)
        try:
            importer.open()
            mw.progress.finish()
            diag = ImportDialog(mw, importer)
        except UnicodeDecodeError:
            mw.progress.finish()
            showUnicodeWarning()
            return
        except Exception as e:
            mw.progress.finish()
            msg = repr(str(e))
            if msg == "'unknownFormat'":
                showWarning(tr.importing_unknown_file_format())
            else:
                msg = f"{tr.importing_failed_debug_info()}\n"
                msg += str(traceback.format_exc())
                showText(msg)
            return
        finally:
            importer.close()
    else:
        # if it's an apkg/zip, first test it's a valid file
        if isinstance(importer, AnkiPackageImporter):
            try:
                z = zipfile.ZipFile(importer.file)
                z.getinfo("collection.anki2")
            except:
                showWarning(invalidZipMsg())
                return
            # we need to ask whether to import/replace; if it's
            # a colpkg file then the rest of the import process
            # will happen in setupApkgImport()
            if not setupApkgImport(mw, importer):
                return

        # importing non-colpkg files
        mw.progress.start(immediate=True)

        def on_done(future: Future) -> None:
            mw.progress.finish()
            try:
                future.result()
            except zipfile.BadZipfile:
                showWarning(invalidZipMsg())
            except Exception as e:
                err = repr(str(e))
                if "invalidFile" in err:
                    msg = tr.importing_invalid_file_please_restore_from_backup()
                    showWarning(msg)
                elif "invalidTempFolder" in err:
                    showWarning(mw.errorHandler.tempFolderMsg())
                elif "readonly" in err:
                    showWarning(tr.importing_unable_to_import_from_a_readonly())
                else:
                    msg = f"{tr.importing_failed_debug_info()}\n"
                    msg += str(traceback.format_exc())
                    showText(msg)
            else:
                log = "\n".join(importer.log)
                if "\n" not in log:
                    tooltip(log)
                else:
                    showText(log, plain_text_edit=True)

            mw.reset()

        mw.taskman.run_in_background(importer.run, on_done)


def invalidZipMsg() -> str:
    return tr.importing_this_file_does_not_appear_to()


def setupApkgImport(mw: AnkiQt, importer: AnkiPackageImporter) -> bool:
    base = os.path.basename(importer.file).lower()
    full = (
        (base == "collection.apkg")
        or re.match("backup-.*\\.apkg", base)
        or base.endswith(".colpkg")
    )
    if not full:
        # adding
        return True
    if not mw.restoringBackup and not askUser(
        tr.importing_this_will_delete_your_existing_collection(),
        msgfunc=QMessageBox.warning,
        defaultno=True,
    ):
        return False

    replaceWithApkg(mw, importer.file, mw.restoringBackup)
    return False


def replaceWithApkg(mw: aqt.AnkiQt, file: str, backup: bool) -> None:
    mw.unloadCollection(lambda: _replaceWithApkg(mw, file, backup))


def _replaceWithApkg(mw: aqt.AnkiQt, filename: str, backup: bool) -> None:
    mw.progress.start(immediate=True)

    def do_import() -> None:
        z = zipfile.ZipFile(filename)

        # v2 scheduler?
        colname = "collection.anki21"
        try:
            z.getinfo(colname)
        except KeyError:
            colname = "collection.anki2"

        with z.open(colname) as source, open(mw.pm.collectionPath(), "wb") as target:
            # ignore appears related to https://github.com/python/typeshed/issues/4349
            # see if can turn off once issue fix is merged in
            shutil.copyfileobj(source, target)

        d = os.path.join(mw.pm.profileFolder(), "collection.media")
        for n, (cStr, file) in enumerate(
            json.loads(z.read("media").decode("utf8")).items()
        ):
            mw.taskman.run_on_main(
                lambda n=n: mw.progress.update(  # type: ignore
                    tr.importing_processed_media_file(count=n)
                )
            )
            size = z.getinfo(cStr).file_size
            dest = os.path.join(d, unicodedata.normalize("NFC", file))
            # if we have a matching file size
            if os.path.exists(dest) and size == os.stat(dest).st_size:
                continue
            data = z.read(cStr)
            with open(dest, "wb") as file:
                file.write(data)

        z.close()

    def on_done(future: Future) -> None:
        mw.progress.finish()

        try:
            future.result()
        except Exception as e:
            print(e)
            showWarning(tr.importing_the_provided_file_is_not_a())
            return

        if not mw.loadCollection():
            return
        if backup:
            mw.col.modSchema(check=False)

        tooltip(tr.importing_importing_complete())

    mw.taskman.run_in_background(do_import, on_done)
