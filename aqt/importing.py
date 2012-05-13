# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, copy, time, sys, re, traceback
from aqt.qt import *
import anki
import anki.importing as importing
from aqt.utils import getOnlyText, getFile, showText, showWarning
from anki.errors import *
from anki.hooks import addHook, remHook
import aqt.forms, aqt.modelchooser

class ChangeMap(QDialog):
    def __init__(self, mw, model, current):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.model = model
        self.frm = aqt.forms.changemap.Ui_ChangeMap()
        self.frm.setupUi(self)
        n = 0
        setCurrent = False
        for field in self.model['flds']:
            item = QListWidgetItem(_("Map to %s") % field['name'])
            self.frm.fields.addItem(item)
            if current == field['name']:
                setCurrent = True
                self.frm.fields.setCurrentRow(n)
            n += 1
        self.frm.fields.addItem(QListWidgetItem(_("Map to Tags")))
        self.frm.fields.addItem(QListWidgetItem(_("Discard field")))
        if not setCurrent:
            if current == "_tags":
                self.frm.fields.setCurrentRow(n)
            else:
                self.frm.fields.setCurrentRow(n+1)
        self.field = None

    def getField(self):
        self.exec_()
        return self.field

    def accept(self):
        row = self.frm.fields.currentRow()
        if row < len(self.model['flds']):
            self.field = self.model['flds'][row]['name']
        elif row == self.frm.fields.count() - 2:
            self.field = "_tags"
        else:
            self.field = None
        QDialog.accept(self)

    def reject(self):
        self.accept()

class ImportDialog(QDialog):

    def __init__(self, mw, importer):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.importer = importer
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        from aqt.tagedit import TagEdit
        self.deck = TagEdit(self, type=1)
        self.frm.gridLayout_2.addWidget(self.deck, 1, 1)
        self.deck.setCol(self.mw.col)
        self.connect(self.frm.buttonBox.button(QDialogButtonBox.Help),
                     SIGNAL("clicked()"), self.helpRequested)
        self.setupMappingFrame()
        self.setupOptions()
        self.modelChanged()
        self.frm.autoDetect.setShown(self.importer.needDelimiter)
        addHook("currentModelChanged", self.modelChanged)
        self.connect(self.frm.autoDetect, SIGNAL("clicked()"),
                     self.onDelimiter)
        self.updateDelimiterButtonText()
        self.exec_()

    def setupOptions(self):
        self.model = self.mw.col.models.current()
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.frm.modelArea, label=False)
        self.connect(self.frm.importButton, SIGNAL("clicked()"),
                     self.doImport)

    def modelChanged(self):
        self.importer.model = self.mw.col.models.current()
        self.importer.initMapping()
        self.showMapping()
        if self.mw.col.conf.get("addToCur", True):
            did = self.mw.col.conf['curDeck']
            if self.mw.col.decks.isDyn(did):
                did = 1
        else:
            did = self.importer.model['did']
        self.deck.setText(self.mw.col.decks.name(did))

    def onDelimiter(self):
        str = getOnlyText(_("""\
By default, Anki will detect the character between fields, such as
a tab, comma, and so on. If Anki is detecting the character incorrectly,
you can enter it here. Use \\t to represent tab."""),
                self, help="importing")
        str = str.replace("\\t", "\t")
        str = str.encode("ascii")
        self.hideMapping()
        def updateDelim():
            self.importer.delimiter = str
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
            d = `d`
        if self.importer.delimiter:
            txt = _("Manual &delimiter: %s") % d
        else:
            txt = _("Auto-detected &delimiter: %s") % d
        self.frm.autoDetect.setText(txt)

    def doImport(self, update=False):
        self.importer.mapping = self.mapping
        if not self.importer.mappingOk():
            showWarning(
                _("The first field of the note type must be mapped."))
            return
        deck = self.deck.text().strip()
        if not deck:
            deck = _("Default")
        did = self.mw.col.decks.id(deck)
        if did != self.importer.model['did']:
            self.importer.model['did'] = did
            self.mw.col.models.save(self.importer.model)
        self.mw.progress.start(immediate=True)
        self.mw.checkpoint(_("Import"))
        try:
            self.importer.run()
        except Exception, e:
            msg = _("Import failed.\n")
            err = unicode(e)
            if "1-character string" in err:
                msg += err
            else:
                msg += unicode(traceback.format_exc(), "ascii", "replace")
            showText(msg)
            return
        finally:
            self.mw.progress.finish()
        txt = _("Importing complete.") + "\n"
        if self.importer.log:
            txt += "\n".join(self.importer.log)
        self.close()
        showText(txt)
        self.mw.reset()

    def setupMappingFrame(self):
        # qt seems to have a bug with adding/removing from a grid, so we add
        # to a separate object and add/remove that instead
        self.frame = QFrame(self.frm.mappingArea)
        self.frm.mappingArea.setWidget(self.frame)
        self.mapbox = QVBoxLayout(self.frame)
        self.mapbox.setContentsMargins(0,0,0,0)
        self.mapwidget = None

    def hideMapping(self):
        self.frm.mappingGroup.hide()

    def showMapping(self, keepMapping=False, hook=None):
        if hook:
            hook()
        if not keepMapping:
            self.mapping = self.importer.mapping

        # except Exception, e:
        #     self.frm.status.setText(
        #         _("Unable to read file.\n\n%s") % unicode(
        #             traceback.format_exc(), "ascii", "replace"))
        #     self.file = None
        #     self.maybePreview()
        #     return
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
        self.grid.setMargin(3)
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
            self.connect(button, SIGNAL("clicked()"),
                         lambda s=self,n=num: s.changeMappingNum(n))

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

    def reject(self):
        self.modelChooser.cleanup()
        remHook("currentModelChanged", self.modelChanged)
        QDialog.reject(self)

    def helpRequested(self):
        openHelp("FileImport")

def onImport(mw):
    filt = ";;".join([x[0] for x in importing.Importers])
    file = getFile(mw, _("Import"), None, key="import",
                   filter=filt)
    if not file:
        return
    file = unicode(file)
    ext = os.path.splitext(file)[1]
    importer = None
    done = False
    for i in importing.Importers:
        if done:
            break
        for mext in re.findall("[( ]?\*\.(.+?)[) ]", i[0]):
            if ext == "." + mext:
                importer = i[1]
                done = True
                break
    if not importer:
        # if no matches, assume TSV
        importer = importing.Importers[0][1]
    importer = importer(mw.col, file)
    # need to show import dialog?
    if importer.needMapper:
        # make sure we can load the file first
        mw.progress.start(immediate=True)
        try:
            importer.open()
        except UnicodeDecodeError:
            showWarning(_("Selected file was not in UTF-8 format."))
            return
        except Exception, e:
            msg = unicode(e)
            if msg == "unknownFormat":
                showWarning(_("Unknown file format."))
            else:
                msg = _("Import failed. Debugging info:\n")
                msg += unicode(traceback.format_exc(), "ascii", "replace")
                showText(msg)
            return
        finally:
            mw.progress.finish()
        diag = ImportDialog(mw, importer)
    else:
        mw.progress.start(immediate=True)
        try:
            importer.run()
        except Exception, e:
            if "invalidFile" in unicode(e):
                showWarning(_("""\
Invalid file. Please run a DB check in Anki 1.2 and try again."""))
            else:
                msg = _("Import failed.\n")
                msg += unicode(traceback.format_exc(), "ascii", "replace")
                showText(msg)
        else:
            showText("\n".join(importer.log))
        finally:
            mw.progress.finish()
        mw.reset()
