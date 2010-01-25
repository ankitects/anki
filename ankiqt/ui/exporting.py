# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

from PyQt4.QtGui import *
from PyQt4.QtCore import *
import anki, ankiqt
from anki.exporting import exporters as exporters_
from anki.utils import parseTags
from ankiqt import ui

class PackagedAnkiExporter(object):
    def __init__(self, *args):
        pass

def exporters():
    l = list(exporters_())
    l.insert(1, (_("Packaged Anki Deck (*.zip)"),
                 PackagedAnkiExporter))
    return l

class ExportDialog(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.deck = parent.deck
        self.dialog = ankiqt.forms.exporting.Ui_ExportDialog()
        self.dialog.setupUi(self)
        self.exporter = None
        self.setup()
        self.exec_()

    def setup(self):
        self.dialog.format.insertItems(
            0, QStringList(list(zip(*exporters())[0])))
        self.connect(self.dialog.format, SIGNAL("activated(int)"),
                     self.exporterChanged)
        self.exporterChanged(0)
        # fragile
        self.tags = ui.tagedit.TagEdit(self)
        self.tags.setDeck(self.deck)
        self.dialog.gridlayout.addWidget(self.tags,1,1)
        self.setTabOrder(self.dialog.format,
                         self.tags)
        self.setTabOrder(self.tags,
                         self.dialog.includeScheduling)
        # save button
        b = QPushButton(_("Export..."))
        self.dialog.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)

    def exporterChanged(self, idx):
        self.exporter = exporters()[idx][1](self.deck)
        if hasattr(self.exporter, "includeSchedulingInfo"):
            self.dialog.includeScheduling.show()
        else:
            self.dialog.includeScheduling.hide()
        if hasattr(self.exporter, "includeTags"):
            self.dialog.includeTags.show()
        else:
            self.dialog.includeTags.hide()

    def accept(self):
        if isinstance(self.exporter, PackagedAnkiExporter):
            self.parent.onShare(unicode(self.tags.text()))
            return QDialog.accept(self)
        file = ui.utils.getSaveFile(self, _("Choose file to export to"), "export",
                                    self.exporter.key, self.exporter.ext)
        self.hide()
        if file:
            self.exporter.includeSchedulingInfo = (
                self.dialog.includeScheduling.isChecked())
            self.exporter.includeTags = (
                self.dialog.includeTags.isChecked())
            self.exporter.limitTags = parseTags(unicode(self.tags.text()))
            self.exporter.exportInto(file)
            self.parent.setStatus(_("%d exported.") % self.exporter.count)
        QDialog.accept(self)
