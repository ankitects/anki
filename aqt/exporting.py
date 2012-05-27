# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
from aqt.qt import *
import anki, aqt, aqt.tagedit
from aqt.utils import getSaveFile, tooltip, showWarning
from anki.exporting import exporters

class ExportDialog(QDialog):

    def __init__(self, mw):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.col = mw.col
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
        self.decks = [_("All Decks")] + sorted(self.col.decks.allNames())
        self.frm.deck.addItems(self.decks)
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
            self, _("Export"), "export",
            self.exporter.key, self.exporter.ext)
        self.hide()
        if file:
            self.exporter.includeSched = (
                self.frm.includeSched.isChecked())
            self.exporter.includeMedia = (
                self.frm.includeMedia.isChecked())
            self.exporter.includeTags = (
                self.frm.includeTags.isChecked())
            if not self.frm.deck.currentIndex():
                self.exporter.did = None
            else:
                name = self.decks[self.frm.deck.currentIndex()]
                self.exporter.did = self.col.decks.id(name)
            self.mw.progress.start(immediate=True)
            try:
                f = open(file, "wb")
                f.close()
            except (OSError, IOError), e:
                showWarning(_("Couldn't save file: %s") % unicode(e))
            else:
                os.unlink(file)
                self.exporter.exportInto(file)
                tooltip(_("%d exported.") % self.exporter.count)
            finally:
                self.mw.progress.finish()
        QDialog.accept(self)
