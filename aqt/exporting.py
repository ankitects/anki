# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import re

from anki.utils import intTime
from aqt.qt import *
import  aqt
from aqt.utils import getSaveFile, tooltip, showWarning, askUser, \
    checkInvalidFilename
from anki.exporting import exporters
from anki.hooks import addHook, remHook
from anki.lang import ngettext


class ExportDialog(QDialog):

    def __init__(self, mw, did=None):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.col = mw.col
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.exporter = None
        self.setup(did)
        self.exec_()

    def setup(self, did):
        self.frm.format.insertItems(0, list(zip(*exporters()))[0])
        self.frm.format.activated.connect(self.exporterChanged)
        self.exporterChanged(0)
        self.decks = [_("All Decks")] + sorted(self.col.decks.allNames())
        self.frm.deck.addItems(self.decks)
        # save button
        b = QPushButton(_("Export..."))
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)
        # set default option if accessed through deck button
        if did:
            name = self.mw.col.decks.get(did)['name']
            index = self.frm.deck.findText(name)
            self.frm.deck.setCurrentIndex(index)

    def exporterChanged(self, idx):
        self.exporter = exporters()[idx][1](self.col)
        self.isApkg = hasattr(self.exporter, "includeSched")
        self.isTextNote = hasattr(self.exporter, "includeTags")
        self.hideTags = hasattr(self.exporter, "hideTags")
        self.frm.includeSched.setVisible(self.isApkg)
        self.frm.includeMedia.setVisible(hasattr(self.exporter, "includeMedia"))
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

        directory_export = hasattr(self.exporter, "directory_export")
        directory = None
        export_file = None

        if (self.isApkg and self.exporter.includeSched and not
            self.exporter.did):
            verbatim = True
            # it's a verbatim apkg export, so place on desktop instead of
            # choosing file; use homedir if no desktop
            usingHomedir = False
            export_file = os.path.join(QStandardPaths.writableLocation(
                QStandardPaths.DesktopLocation), "collection.apkg")
            if not os.path.exists(os.path.dirname(export_file)):
                usingHomedir = True
                export_file = os.path.join(QStandardPaths.writableLocation(
                    QStandardPaths.HomeLocation), "collection.apkg")
            if os.path.exists(export_file):
                if usingHomedir:
                    question = _("%s already exists in your home directory. Overwrite it?")
                else:
                    question = _("%s already exists on your desktop. Overwrite it?")
                if not askUser(question % "collection.apkg"):
                    return
        else:
            verbatim = False
            # Get deck name and remove invalid filename characters
            deck_name = self.decks[self.frm.deck.currentIndex()]
            deck_name = re.sub('[\\\\/?<>:*|"^]', '_', deck_name)
            filename = os.path.join(aqt.mw.pm.base,
                                    '{0}{1}'.format(deck_name, self.exporter.ext))
            while 1:
                if directory_export:
                    directory = str(QFileDialog.getExistingDirectory(caption="Select Export Directory",
                                                                     directory=filename))
                    if directory:
                        export_file = os.path.join(directory, str(intTime()))
                else:
                    export_file = getSaveFile(self, _("Export"), "export",
                                       self.exporter.key, self.exporter.ext,
                                       fname=filename)
                if not export_file:
                    return
                if checkInvalidFilename(os.path.basename(export_file), dirsep=False):
                    continue
                break
        self.hide()
        if export_file:
            self.mw.progress.start(immediate=True)
            try:
                f = open(export_file, "wb")
                f.close()
            except (OSError, IOError) as e:
                showWarning(_("Couldn't save file: %s") % str(e))
            else:
                os.unlink(export_file)
                exportedMedia = lambda cnt: self.mw.progress.update(
                        label=ngettext("Exported %d media file",
                                       "Exported %d media files", cnt) % cnt
                        )
                addHook("exportedMediaFiles", exportedMedia)
                self.exporter.exportInto(directory if directory_export else export_file)
                remHook("exportedMediaFiles", exportedMedia)
                if verbatim:
                    if usingHomedir:
                        msg = _("A file called %s was saved in your home directory.")
                    else:
                        msg = _("A file called %s was saved on your desktop.")
                    msg = msg % "collection.apkg"
                    period = 5000
                else:
                    period = 3000
                    if self.isTextNote:
                        msg = ngettext("%d note exported.", "%d notes exported.",
                                    self.exporter.count) % self.exporter.count
                    else:
                        msg = ngettext("%d card exported.", "%d cards exported.",
                                    self.exporter.count) % self.exporter.count
                tooltip(msg, period=period)
            finally:
                self.mw.progress.finish()
        QDialog.accept(self)
