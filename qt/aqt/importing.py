# coding=utf-8
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

import anki.importing as importing
import aqt.deckchooser
import aqt.forms
import aqt.modelchooser
from anki.lang import _, ngettext
from aqt import AnkiQt, gui_hooks
from aqt.qt import *
from aqt.utils import (
    TR,
    askUser,
    getFile,
    getOnlyText,
    openHelp,
    showInfo,
    showText,
    showWarning,
    tooltip,
    tr,
)


class ChangeMap(QDialog):
    def __init__(self, mw: AnkiQt, model, current):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.model = model
        self.frm = aqt.forms.changemap.Ui_ChangeMap()
        self.frm.setupUi(self)
        n = 0
        setCurrent = False
        for field in self.model["flds"]:
            item = QListWidgetItem(_("Map to %s") % field["name"])
            self.frm.fields.addItem(item)
            if current == field["name"]:
                setCurrent = True
                self.frm.fields.setCurrentRow(n)
            n += 1
        self.frm.fields.addItem(QListWidgetItem(_("Map to Tags")))
        self.frm.fields.addItem(QListWidgetItem(_("Ignore field")))
        if not setCurrent:
            if current == "_tags":
                self.frm.fields.setCurrentRow(n)
            else:
                self.frm.fields.setCurrentRow(n + 1)
        self.field = None

    def getField(self):
        self.exec_()
        return self.field

    def accept(self):
        row = self.frm.fields.currentRow()
        if row < len(self.model["flds"]):
            self.field = self.model["flds"][row]["name"]
        elif row == self.frm.fields.count() - 2:
            self.field = "_tags"
        else:
            self.field = None
        QDialog.accept(self)

    def reject(self):
        self.accept()


# called by importFile() when importing a mappable file like .csv
class ImportDialog(QDialog):
    def __init__(self, mw: AnkiQt, importer) -> None:
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.importer = importer
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        self.frm.buttonBox.button(QDialogButtonBox.Help).clicked.connect(
            self.helpRequested
        )
        self.setupMappingFrame()
        self.setupOptions()
        self.modelChanged()
        self.frm.autoDetect.setVisible(self.importer.needDelimiter)
        gui_hooks.current_note_type_did_change.append(self.modelChanged)
        self.frm.autoDetect.clicked.connect(self.onDelimiter)
        self.updateDelimiterButtonText()
        self.frm.allowHTML.setChecked(self.mw.pm.profile.get("allowHTML", True))
        self.frm.importMode.currentIndexChanged.connect(self.importModeChanged)
        self.frm.importMode.setCurrentIndex(self.mw.pm.profile.get("importMode", 1))
        self.frm.tagModified.setText(self.mw.pm.profile.get("tagModified", ""))
        self.frm.tagModified.setCol(self.mw.col)
        # import button
        b = QPushButton(_("Import"))
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)
        self.exec_()

    def setupOptions(self):
        self.model = self.mw.col.models.current()
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.frm.modelArea, label=False
        )
        self.deck = aqt.deckchooser.DeckChooser(self.mw, self.frm.deckArea, label=False)

    def modelChanged(self, unused=None):
        self.importer.model = self.mw.col.models.current()
        self.importer.initMapping()
        self.showMapping()
        if self.mw.col.conf.get("addToCur", True):
            did = self.mw.col.conf["curDeck"]
            if self.mw.col.decks.isDyn(did):
                did = 1
        else:
            did = self.importer.model["did"]
        # self.deck.setText(self.mw.col.decks.name(did))

    def onDelimiter(self):
        str = (
            getOnlyText(
                _(
                    """\
By default, Anki will detect the character between fields, such as
a tab, comma, and so on. If Anki is detecting the character incorrectly,
you can enter it here. Use \\t to represent tab."""
                ),
                self,
                help="importing",
            )
            or "\t"
        )
        str = str.replace("\\t", "\t")
        if len(str) > 1:
            showWarning(
                _(
                    "Multi-character separators are not supported. "
                    "Please enter one character only."
                )
            )
            return
        self.hideMapping()

        def updateDelim():
            self.importer.delimiter = str
            self.importer.updateDelimiter()

        self.showMapping(hook=updateDelim)
        self.updateDelimiterButtonText()

    def updateDelimiterButtonText(self):
        if not self.importer.needDelimiter:
            return
        if self.importer.delimiter:
            d = self.importer.delimiter
        else:
            d = self.importer.dialect.delimiter
        if d == "\t":
            d = _("Tab")
        elif d == ",":
            d = _("Comma")
        elif d == " ":
            d = _("Space")
        elif d == ";":
            d = _("Semicolon")
        elif d == ":":
            d = _("Colon")
        else:
            d = repr(d)
        txt = _("Fields separated by: %s") % d
        self.frm.autoDetect.setText(txt)

    def accept(self):
        self.importer.mapping = self.mapping
        if not self.importer.mappingOk():
            showWarning(_("The first field of the note type must be mapped."))
            return
        self.importer.importMode = self.frm.importMode.currentIndex()
        self.mw.pm.profile["importMode"] = self.importer.importMode
        self.importer.allowHTML = self.frm.allowHTML.isChecked()
        self.mw.pm.profile["allowHTML"] = self.importer.allowHTML
        self.importer.tagModified = self.frm.tagModified.text()
        self.mw.pm.profile["tagModified"] = self.importer.tagModified
        did = self.deck.selectedId()
        if did != self.importer.model["did"]:
            self.importer.model["did"] = did
            self.mw.col.models.save(self.importer.model, updateReqs=False)
        self.mw.col.decks.select(did)
        self.mw.progress.start(immediate=True)
        self.mw.checkpoint(_("Import"))

        def on_done(future: Future):
            self.mw.progress.finish()

            try:
                future.result()
            except UnicodeDecodeError:
                showUnicodeWarning()
                return
            except Exception as e:
                msg = tr(TR.IMPORTING_FAILED_DEBUG_INFO) + "\n"
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
                txt = _("Importing complete.") + "\n"
                if self.importer.log:
                    txt += "\n".join(self.importer.log)
                self.close()
                showText(txt)
                self.mw.reset()

        self.mw.taskman.run_in_background(self.importer.run, on_done)

    def setupMappingFrame(self):
        # qt seems to have a bug with adding/removing from a grid, so we add
        # to a separate object and add/remove that instead
        self.frame = QFrame(self.frm.mappingArea)
        self.frm.mappingArea.setWidget(self.frame)
        self.mapbox = QVBoxLayout(self.frame)
        self.mapbox.setContentsMargins(0, 0, 0, 0)
        self.mapwidget = None

    def hideMapping(self):
        self.frm.mappingGroup.hide()

    def showMapping(self, keepMapping=False, hook=None):
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
            text = _("Field <b>%d</b> of file is:") % (num + 1)
            self.grid.addWidget(QLabel(text), num, 0)
            if self.mapping[num] == "_tags":
                text = _("mapped to <b>Tags</b>")
            elif self.mapping[num]:
                text = _("mapped to <b>%s</b>") % self.mapping[num]
            else:
                text = _("<ignored>")
            self.grid.addWidget(QLabel(text), num, 1)
            button = QPushButton(_("Change"))
            self.grid.addWidget(button, num, 2)
            button.clicked.connect(lambda _, s=self, n=num: s.changeMappingNum(n))

    def changeMappingNum(self, n):
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

            def updateDelim():
                self.importer.delimiter = self.savedDelimiter

            self.showMapping(hook=updateDelim, keepMapping=True)
        else:
            self.showMapping(keepMapping=True)

    def reject(self) -> None:
        self.modelChooser.cleanup()
        self.deck.cleanup()
        gui_hooks.current_note_type_did_change.remove(self.modelChanged)
        QDialog.reject(self)

    def helpRequested(self):
        openHelp("importing")

    def importModeChanged(self, newImportMode):
        if newImportMode == 0:
            self.frm.tagModified.setEnabled(True)
        else:
            self.frm.tagModified.setEnabled(False)


def showUnicodeWarning():
    """Shorthand to show a standard warning."""
    showWarning(
        _(
            "Selected file was not in UTF-8 format. Please see the "
            "importing section of the manual."
        )
    )


def onImport(mw):
    filt = ";;".join([x[0] for x in importing.Importers])
    file = getFile(mw, _("Import"), None, key="import", filter=filt)
    if not file:
        return
    file = str(file)

    head, ext = os.path.splitext(file)
    ext = ext.lower()
    if ext == ".anki":
        showInfo(
            _(
                ".anki files are from a very old version of Anki. You can import them with Anki 2.0, available on the Anki website."
            )
        )
        return
    elif ext == ".anki2":
        showInfo(
            _(
                ".anki2 files are not directly importable - please import the .apkg or .zip file you have received instead."
            )
        )
        return

    importFile(mw, file)


def importFile(mw, file):
    importerClass = None
    done = False
    for i in importing.Importers:
        if done:
            break
        for mext in re.findall(r"[( ]?\*\.(.+?)[) ]", i[0]):
            if file.endswith("." + mext):
                importerClass = i[1]
                done = True
                break
    if not importerClass:
        # if no matches, assume TSV
        importerClass = importing.Importers[0][1]
    importer = importerClass(mw.col, file)
    # need to show import dialog?
    if importer.needMapper:
        # make sure we can load the file first
        mw.progress.start(immediate=True)
        try:
            importer.open()
        except UnicodeDecodeError:
            mw.progress.finish()
            showUnicodeWarning()
            return
        except Exception as e:
            mw.progress.finish()
            msg = repr(str(e))
            if msg == "'unknownFormat'":
                showWarning(_("Unknown file format."))
            else:
                msg = tr(TR.IMPORTING_FAILED_DEBUG_INFO) + "\n"
                msg += str(traceback.format_exc())
                showText(msg)
            return
        finally:
            mw.progress.finish()
        diag = ImportDialog(mw, importer)
    else:
        # if it's an apkg/zip, first test it's a valid file
        if importer.__class__.__name__ == "AnkiPackageImporter":
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

        def on_done(future: Future):
            mw.progress.finish()
            try:
                future.result()
            except zipfile.BadZipfile:
                showWarning(invalidZipMsg())
            except Exception as e:
                err = repr(str(e))
                if "invalidFile" in err:
                    msg = _(
                        """\
    Invalid file. Please restore from backup."""
                    )
                    showWarning(msg)
                elif "invalidTempFolder" in err:
                    showWarning(mw.errorHandler.tempFolderMsg())
                elif "readonly" in err:
                    showWarning(
                        _(
                            """\
    Unable to import from a read-only file."""
                        )
                    )
                else:
                    msg = tr(TR.IMPORTING_FAILED_DEBUG_INFO) + "\n"
                    msg += str(traceback.format_exc())
                    showText(msg)
            else:
                log = "\n".join(importer.log)
                if "\n" not in log:
                    tooltip(log)
                else:
                    showText(log)

            mw.reset()

        mw.taskman.run_in_background(importer.run, on_done)


def invalidZipMsg():
    return _(
        """\
This file does not appear to be a valid .apkg file. If you're getting this \
error from a file downloaded from AnkiWeb, chances are that your download \
failed. Please try again, and if the problem persists, please try again \
with a different browser."""
    )


def setupApkgImport(mw, importer):
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
        _(
            """\
This will delete your existing collection and replace it with the data in \
the file you're importing. Are you sure?"""
        ),
        msgfunc=QMessageBox.warning,
        defaultno=True,
    ):
        return False

    replaceWithApkg(mw, importer.file, mw.restoringBackup)


def replaceWithApkg(mw, file, backup):
    mw.unloadCollection(lambda: _replaceWithApkg(mw, file, backup))


def _replaceWithApkg(mw, filename, backup):
    mw.progress.start(immediate=True)

    def do_import():
        z = zipfile.ZipFile(filename)

        # v2 scheduler?
        colname = "collection.anki21"
        try:
            z.getinfo(colname)
        except KeyError:
            colname = "collection.anki2"

        with z.open(colname) as source, open(mw.pm.collectionPath(), "wb") as target:
            shutil.copyfileobj(source, target)

        d = os.path.join(mw.pm.profileFolder(), "collection.media")
        for n, (cStr, file) in enumerate(
            json.loads(z.read("media").decode("utf8")).items()
        ):
            mw.taskman.run_on_main(
                lambda n=n: mw.progress.update(
                    ngettext("Processed %d media file", "Processed %d media files", n)
                    % n
                )
            )
            size = z.getinfo(cStr).file_size
            dest = os.path.join(d, unicodedata.normalize("NFC", file))
            # if we have a matching file size
            if os.path.exists(dest) and size == os.stat(dest).st_size:
                continue
            data = z.read(cStr)
            open(dest, "wb").write(data)

        z.close()

    def on_done(future: Future):
        mw.progress.finish()

        try:
            future.result()
        except Exception as e:
            print(e)
            showWarning(_("The provided file is not a valid .apkg file."))
            return

        if not mw.loadCollection():
            return
        if backup:
            mw.col.modSchema(check=False)

        tooltip(_("Importing complete."))

    mw.taskman.run_in_background(do_import, on_done)
