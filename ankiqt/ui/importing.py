# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

import os, copy, time, sys, re
from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki
import anki.importing as importing
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
        for field in self.model.fieldModels:
            item = QListWidgetItem(_("Map to %s") % field.name)
            self.dialog.fields.addItem(item)
            if current == field.name:
                self.dialog.fields.setCurrentRow(n)
            n += 1
        self.dialog.fields.addItem(QListWidgetItem(_("Map to Tags")))
        self.dialog.fields.addItem(QListWidgetItem(_("Discard field")))
        if current is None:
            self.dialog.fields.setCurrentRow(n)
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

class ImportDialog(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.dialog = ankiqt.forms.importing.Ui_ImportDialog()
        self.dialog.setupUi(self)
        self.tags = ui.tagedit.TagEdit(parent)
        self.tags.setDeck(parent.deck)
        self.dialog.topGrid.addWidget(self.tags,0,1,1,1)
        self.setupMappingFrame()
        self.setupOptions()
        self.getFile()
        if not self.file:
            return
        self.dialog.groupBox.setTitle(os.path.basename(self.file))
        self.maybePreview()
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
            self.dialog.tagDuplicates.show()
        else:
            self.modelChooser.hide()
            self.dialog.tagDuplicates.hide()

    def maybePreview(self):
        if self.file and self.model:
            self.dialog.status.setText("")
            self.showMapping()
        else:
            self.hideMapping()

    def modelChanged(self, model):
        self.model = model
        self.maybePreview()

    def doImport(self):
        self.dialog.status.setText(_("Importing..."))
        t = time.time()
        self.importer.mapping = self.mapping
        self.importer.tagsToAdd = unicode(self.tags.text())
        self.importer.tagDuplicates = self.dialog.tagDuplicates.isChecked()
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
                msg = _("Import failed: %s\n") % `e`
                self.dialog.status.setText(msg)
                return
        finally:
            self.parent.deck.finishProgress()
            self.parent.deck.setUndoEnd(n)
        txt = (
            _("Importing complete. %(num)d facts imported from %(file)s.\n") %
            {"num": self.importer.total, "file": os.path.basename(self.file)})
        self.dialog.groupBox.setShown(False)
        if self.importer.log:
            txt += _("Log of import:\n") + "\n".join(self.importer.log)
        self.dialog.status.setText(txt)
        self.file = None
        self.maybePreview()
        self.parent.deck.s.flush()
        if sys.platform.startswith("win32") and not self.parent.deck.path:
            # this fixes a strange bug in sqlite
            self.parent.deck.s.all("pragma integrity_check")
        self.parent.reset()

    def setupMappingFrame(self):
        # qt seems to have a bug with adding/removing from a grid, so we add
        # to a separate object and add/remove that instead
        self.mapbox = QVBoxLayout(self.dialog.mappingArea)
        self.mapwidget = None

    def hideMapping(self):
        self.dialog.mappingGroup.hide()

    def showMapping(self, keepMapping=False):
        # first, check that we can read the file
        try:
            self.importer = self.importerFunc(self.parent.deck, self.file)
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
            return
        # set up the mapping grid
        if self.mapwidget:
            self.mapbox.removeWidget(self.mapwidget)
            self.mapwidget.deleteLater()
        self.mapwidget = QWidget()
        self.mapbox.addWidget(self.mapwidget)
        self.grid = QGridLayout(self.mapwidget)
        self.mapwidget.setLayout(self.grid)
        self.grid.setMargin(6)
        self.grid.setSpacing(12)
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
        self.showMapping(keepMapping=True)
