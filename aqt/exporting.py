# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re

from aqt.qt import *
import  aqt
from aqt.utils import getSaveFile, tooltip, showWarning, askUser, \
    checkInvalidFilename
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
        self.isApkg = hasattr(self.exporter, "includeSched")
        self.hideTags = hasattr(self.exporter, "hideTags")
        self.frm.includeSched.setVisible(self.isApkg)
        self.frm.includeMedia.setVisible(self.isApkg)
        self.frm.includeTags.setVisible(
            not self.isApkg and not self.hideTags)

    def accept(self):
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
        if (self.isApkg and self.exporter.includeSched and not
            self.exporter.did):
            verbatim = True
            # it's a verbatim apkg export, so place on desktop instead of
            # choosing file
            file = os.path.join(QDesktopServices.storageLocation(
                QDesktopServices.DesktopLocation), "collection.apkg")
            if os.path.exists(file):
                if not askUser(
                    _("%s already exists on your desktop. Overwrite it?")%
                    "collection.apkg"):
                    return
        else:
            verbatim = False
            # Get deck name and remove invalid filename characters
            deck_name = self.decks[self.frm.deck.currentIndex()]
            deck_name = re.sub('[\\\\/?<>:*|"^]', '_', deck_name)
            filename = os.path.join(aqt.mw.pm.base,
                                    u'{0}{1}'.format(deck_name, self.exporter.ext))
            while 1:
                file = getSaveFile(self, _("Export"), "export",
                                   self.exporter.key, self.exporter.ext,
                                   fname=filename)
                if not file:
                    return
                if checkInvalidFilename(os.path.basename(file), dirsep=False):
                    continue
                break
        self.hide()
        if file:
            self.mw.progress.start(immediate=True)
            try:
                f = open(file, "wb")
                f.close()
            except (OSError, IOError), e:
                showWarning(_("Couldn't save file: %s") % unicode(e))
            else:
                os.unlink(file)
                self.exporter.exportInto(file)
                if verbatim:
                    msg = _("A file called collection.apkg was saved on your desktop.")
                    period = 5000
                else:
                    period = 3000
                    msg = ngettext("%d card exported.", "%d cards exported.", \
                                self.exporter.count) % self.exporter.count
                tooltip(msg, period=period)
            finally:
                self.mw.progress.finish()
        QDialog.accept(self)
