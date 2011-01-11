# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, copy, time, sys, re, traceback
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki
import anki.importing as importing
from ankiqt.ui.utils import getOnlyText
from anki.errors import *
import ankiqt.forms
from ankiqt import ui

class ChangeMap(QDialog):
    def __init__(self, parent, model, current):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.model = model
        self.dialog = ankiqt.forms.changemap.Ui_ChangeMap()
        self.dialog.setupUi(self)
        n = 0
        setCurrent = False
        for field in self.model.fieldModels:
            item = QListWidgetItem(_("Map to %s") % field.name)
            self.dialog.fields.addItem(item)
            if current and current.name == field.name:
                setCurrent = True
                self.dialog.fields.setCurrentRow(n)
            n += 1
        self.dialog.fields.addItem(QListWidgetItem(_("Map to Tags")))
        self.dialog.fields.addItem(QListWidgetItem(_("Discard field")))
        if not setCurrent:
            if current == 0:
                self.dialog.fields.setCurrentRow(n)
            else:
                self.dialog.fields.setCurrentRow(n+1)
        self.field = None

    def getField(self):
        self.exec_()
        return self.field

    def accept(self):
        row = self.dialog.fields.currentRow()
        if row < len(self.model.fieldModels):
            self.field = self.model.fieldModels[row]
        elif row == self.dialog.fields.count() - 1:
            self.field = None
        else:
            self.field = 0
        QDialog.accept(self)

class UpdateMap(QDialog):
    def __init__(self, parent, numFields, fieldModels):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.fieldModels = fieldModels
        self.dialog = ankiqt.forms.importup.Ui_Dialog()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox.button(QDialogButtonBox.Help),
                     SIGNAL("clicked()"), self.helpRequested)
        for i in range(numFields):
            self.dialog.fileField.addItem("Field %d" % (i+1))
        for m in fieldModels:
            self.dialog.deckField.addItem(m.name)
        self.exec_()

    def helpRequested(self):
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "FileImport"))

    def accept(self):
        self.updateKey = (
            self.dialog.fileField.currentIndex(),
            self.fieldModels[self.dialog.deckField.currentIndex()].id)
        QDialog.accept(self)

class ImportDialog(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.dialog = ankiqt.forms.importing.Ui_ImportDialog()
        self.dialog.setupUi(self)
        self.connect(self.dialog.buttonBox.button(QDialogButtonBox.Help),
                     SIGNAL("clicked()"), self.helpRequested)
        self.setupMappingFrame()
        self.setupOptions()
        self.getFile()
        if not self.file:
            return
        self.dialog.groupBox.setTitle(os.path.basename(self.file))
        self.maybePreview()
        self.connect(self.dialog.autoDetect, SIGNAL("clicked()"),
                     self.onDelimiter)
        self.updateDelimiterButtonText()
        self.exec_()

    def setupOptions(self):
        self.model = self.parent.deck.currentModel
        self.modelChooser = ui.modelchooser.ModelChooser(self,
                                                         self.parent,
                                                         self.parent.deck,
                                                         self.modelChanged)
        self.dialog.modelArea.setLayout(self.modelChooser)
        self.connect(self.dialog.importButton, SIGNAL("clicked()"),
                     self.doImport)
        self.connect(self.dialog.updateButton, SIGNAL("clicked()"),
                     self.doUpdate)

    def getFile(self):
        key = ";;".join([x[0] for x in importing.Importers])
        file = ui.utils.getFile(self.parent, _("Import"), "import", key)
        if not file:
            self.file = None
            return
        self.file = unicode(file)
        ext = os.path.splitext(self.file)[1]
        self.importer = None
        for i in importing.Importers:
            for mext in re.findall("[( ]?\*\.(.+?)[) ]", i[0]):
                if ext == "." + mext:
                    self.importer = i
                    break
        if not self.importer:
            self.importer = importing.Importers[0]
        self.importerFunc = self.importer[1]
        if self.importerFunc.needMapper:
            self.modelChooser.show()
        else:
            self.modelChooser.hide()
            self.dialog.groupBox.setShown(False)
            self.dialog.mappingGroup.setTitle("")
        self.dialog.autoDetect.setShown(self.importerFunc.needDelimiter)

    def maybePreview(self):
        if self.file and self.model:
            self.dialog.status.setText("")
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
                self, help="FileImport")
        str = str.replace("\\t", "\t")
        str = str.encode("ascii")
        self.hideMapping()
        def updateDelim():
            self.importer.delimiter = str
        self.showMapping(hook=updateDelim)
        self.updateDelimiterButtonText()

    def updateDelimiterButtonText(self):
        if not self.importerFunc.needDelimiter:
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
        self.dialog.autoDetect.setText(txt)

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
        self.dialog.status.setText(_("Importing..."))
        t = time.time()
        self.importer.mapping = self.mapping
        try:
            n = _("Import")
            self.parent.deck.setUndoStart(n)
            try:
                self.importer.doImport()
            except ImportFormatError, e:
                msg = _("Importing failed.\n")
                msg += e.data['info']
                self.dialog.status.setText(msg)
                return
            except Exception, e:
                msg = _("Import failed.\n")
                msg += unicode(traceback.format_exc(), "ascii", "replace")
                self.dialog.status.setText(msg)
                return
        finally:
            self.parent.deck.finishProgress()
            self.parent.deck.setUndoEnd(n)
        txt = (
            _("Importing complete. %(num)d facts imported from %(file)s.\n") %
            {"num": self.importer.total, "file": os.path.basename(self.file)})
        self.dialog.groupBox.setShown(False)
        self.dialog.buttonBox.button(QDialogButtonBox.Close).setFocus()
        if self.importer.log:
            txt += _("Log of import:\n") + "\n".join(self.importer.log)
        self.dialog.status.setText(txt)
        self.file = None
        self.maybePreview()
        self.parent.deck.s.flush()
        self.parent.reset()
        self.modelChooser.deinit()

    def setupMappingFrame(self):
        # qt seems to have a bug with adding/removing from a grid, so we add
        # to a separate object and add/remove that instead
        self.frame = QFrame(self.dialog.mappingArea)
        self.dialog.mappingArea.setWidget(self.frame)
        self.mapbox = QVBoxLayout(self.frame)
        self.mapbox.setContentsMargins(0,0,0,0)
        self.mapwidget = None

    def hideMapping(self):
        self.dialog.mappingGroup.hide()

    def showMapping(self, keepMapping=False, hook=None):
        # first, check that we can read the file
        try:
            self.importer = self.importerFunc(self.parent.deck, self.file)
            if hook:
                hook()
            if not keepMapping:
                self.mapping = self.importer.mapping
        except ImportFormatError, e:
            self.dialog.status.setText(
                _("Unable to read file.\n\n%(info)s") % {
                'type': e.data.get('type', ""),
                'info': e.data.get('info', ""),
                })
            self.file = None
            self.maybePreview()
            return
        self.dialog.mappingGroup.show()
        if self.importer.fields():
            self.dialog.mappingArea.show()
        else:
            self.dialog.mappingArea.hide()
            self.dialog.updateButton.hide()
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
        f = ChangeMap(self.parent, self.model, self.mapping[n]).getField()
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
        QDesktopServices.openUrl(QUrl(ankiqt.appWiki + "FileImport"))
