# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from aqt.qt import *
import anki, aqt, aqt.tagedit
from aqt.utils import getSaveFile, tooltip
from anki.exporting import exporters

class ExportDialog(QDialog):

    def __init__(self, parent):
        QDialog.__init__(self, parent, Qt.Window)
        self.parent = parent
        self.col = parent.col
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.exporter = None
        self.setup()
        self.exec_()

    def setup(self):
        self.frm.format.insertItems(0, list(zip(*exporters())[0]))
        self.connect(self.frm.format, SIGNAL("activated(int)"),
                     self.exporterChanged)
        self.exporterChanged(0)
        self.frm.deck.addItems([_("All Decks")] + sorted(
            self.col.decks.allNames()))
        # save button
        b = QPushButton(_("Export..."))
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)

    def exporterChanged(self, idx):
        self.exporter = exporters()[idx][1](self.col)
        isAnki = hasattr(self.exporter, "includeSched")
        self.frm.includeSched.setShown(isAnki)
        self.frm.includeMedia.setShown(isAnki)
        self.frm.includeTags.setShown(not isAnki)

    def accept(self):
        file = getSaveFile(
            self, _("Choose file to export to"), "export",
            self.exporter.key, self.exporter.ext)
        self.hide()
        print file
        if file:
            self.exporter.includeSched = (
                self.frm.includeSched.isChecked())
            self.exporter.includeTags = (
                self.frm.includeTags.isChecked())
            if not self.frm.deck.currentIndex():
                self.exporter.did = None
            else:
                self.exporter.did = self.frm.deck.currentIndex() - 1
            self.exporter.exportInto(file)
            tooltip(_("%d exported.") % self.exporter.count)
        QDialog.accept(self)
