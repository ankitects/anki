# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, copy, time, sys, re, traceback
from aqt.qt import *
import anki
import anki.importing as importing
from aqt.utils import getOnlyText, getFile, showText
from anki.errors import *
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
        for field in self.model.fieldModels:
            item = QListWidgetItem(_("Map to %s") % field.name)
            self.frm.fields.addItem(item)
            if current and current.name == field.name:
                setCurrent = True
                self.frm.fields.setCurrentRow(n)
            n += 1
        self.frm.fields.addItem(QListWidgetItem(_("Map to Tags")))
        self.frm.fields.addItem(QListWidgetItem(_("Discard field")))
        if not setCurrent:
            if current == 0:
                self.frm.fields.setCurrentRow(n)
            else:
                self.frm.fields.setCurrentRow(n+1)
        self.field = None

    def getField(self):
        self.exec_()
        return self.field

    def accept(self):
        row = self.frm.fields.currentRow()
        if row < len(self.model.fieldModels):
            self.field = self.model.fieldModels[row]
        elif row == self.frm.fields.count() - 1:
            self.field = None
        else:
            self.field = 0
        QDialog.accept(self)

class UpdateMap(QDialog):
    def __init__(self, mw, numFields, fieldModels):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.fieldModels = fieldModels
        self.frm = aqt.forms.importup.Ui_Dialog()
        self.frm.setupUi(self)
        self.connect(self.frm.buttonBox.button(QDialogButtonBox.Help),
                     SIGNAL("clicked()"), self.helpRequested)
        for i in range(numFields):
            self.frm.fileField.addItem("Field %d" % (i+1))
        for m in fieldModels:
            self.frm.colField.addItem(m.name)
        self.exec_()

    def helpRequested(self):
        openHelp("importing")

    def accept(self):
        self.updateKey = (
            self.frm.fileField.currentIndex(),
            self.fieldModels[self.frm.colField.currentIndex()].id)
        QDialog.accept(self)

class ImportDialog(QDialog):

    def __init__(self, mw, importer):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.frm = aqt.forms.importing.Ui_ImportDialog()
        self.frm.setupUi(self)
        self.connect(self.frm.buttonBox.button(QDialogButtonBox.Help),
                     SIGNAL("clicked()"), self.helpRequested)
        self.setupMappingFrame()
        self.setupOptions()

        if self.importer.needMapper:
            self.modelChooser.show()
        else:
            self.modelChooser.hide()
            self.frm.groupBox.setShown(False)
            self.frm.mappingGroup.setTitle("")
        self.frm.autoDetect.setShown(self.importer.needDelimiter)


        if not self.file:
            return
        self.frm.groupBox.setTitle(os.path.basename(self.file))
        self.maybePreview()
        self.connect(self.frm.autoDetect, SIGNAL("clicked()"),
                     self.onDelimiter)
        self.updateDelimiterButtonText()
        self.exec_()

    def setupOptions(self):
        self.model = self.mw.col.models.current()
        self.modelChooser = aqt.modelchooser.ModelChooser(
            self.mw, self.frm.modelArea)
        self.connect(self.frm.importButton, SIGNAL("clicked()"),
                     self.doImport)
        self.connect(self.frm.updateButton, SIGNAL("clicked()"),
                     self.doUpdate)

    def maybePreview(self):
        if self.file and self.importer.needMapper:
            self.frm.status.setText("")
            self.showMapping()
        else:
            self.hideMapping()

    def modelChanged(self, model):
        self.model = model
        self.maybePreview()

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
            d = "Tab"
        elif d == ",":
            d = "Comma"
        elif d == " ":
            d = "Space"
        elif d == ";":
            d = "Semicolon"
        elif d == ":":
            d = "Colon"
        else:
            d = `d`
        if self.importer.delimiter:
            txt = _("Manual &delimiter: %s") % d
        else:
            txt = _("Auto-detected &delimiter: %s") % d
        self.frm.autoDetect.setText(txt)

    def doUpdate(self):
        f = UpdateMap(self,
                      self.importer.fields(),
                      self.model.fieldModels)
        if not getattr(f, "updateKey", None):
            # user cancelled
            return
        self.importer.updateKey = f.updateKey
        self.doImport(True)

    def doImport(self, update=False):
        self.frm.status.setText(_("Importing..."))
        t = time.time()
        self.importer.mapping = self.mapping
        try:
            n = _("Import")
            self.mw.col.setUndoStart(n)
            try:
                self.importer.run()
            except Exception, e:
                msg = _("Import failed.\n")
                msg += unicode(traceback.format_exc(), "ascii", "replace")
                self.frm.status.setText(msg)
                return
        finally:
            self.mw.col.finishProgress()
            self.mw.col.setUndoEnd(n)
        txt = (
            _("Importing complete. %(num)d notes imported from %(file)s.\n") %
            {"num": self.importer.total, "file": os.path.basename(self.file)})
        self.frm.groupBox.setShown(False)
        self.frm.buttonBox.button(QDialogButtonBox.Close).setFocus()
        if self.importer.log:
            txt += _("Log of import:\n") + "\n".join(self.importer.log)
        self.frm.status.setText(txt)
        self.file = None
        self.maybePreview()
        self.mw.col.db.flush()
        self.mw.reset()
        self.modelChooser.deinit()

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
        # first, check that we can read the file
        try:
            self.importer = self.importer(self.mw.col, self.file)
            if hook:
                hook()
            if not keepMapping:
                self.mapping = self.importer.mapping
        except Exception, e:
            self.frm.status.setText(
                _("Unable to read file.\n\n%s") % unicode(
                    traceback.format_exc(), "ascii", "replace"))
            self.file = None
            self.maybePreview()
            return
        self.frm.mappingGroup.show()
        if self.importer.fields():
            self.frm.mappingArea.show()
        else:
            self.frm.mappingArea.hide()
            self.frm.updateButton.hide()
            return
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
            if self.mapping[num]:
                text = _("mapped to <b>%s</b>") % self.mapping[num].name
            elif self.mapping[num] is 0:
                text = _("mapped to <b>Tags</b>")
            else:
                text = _("<ignored>")
            self.grid.addWidget(QLabel(text), num, 1)
            button = QPushButton(_("Change"))
            self.grid.addWidget(button, num, 2)
            self.connect(button, SIGNAL("clicked()"),
                         lambda s=self,n=num: s.changeMappingNum(n))

    def changeMappingNum(self, n):
        f = ChangeMap(self.mw, self.model, self.mapping[n]).getField()
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
        self.modelChooser.deinit()
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
        diag = ImportDialog(mw, importer)
        self.modelChooser.show()
    else:
        try:
            mw.progress.start(immediate=True)
            importer.run()
            mw.progress.finish()
        except Exception, e:
            msg = _("Import failed.\n")
            msg += unicode(traceback.format_exc(), "ascii", "replace")
            showText(msg)
        else:
            showText("\n".join(importer.log))
