# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
import time
from concurrent.futures import Future
from typing import List, Optional

import aqt
from anki import hooks
from anki.exporting import exporters
from anki.lang import _, ngettext
from aqt.qt import *
from aqt.utils import checkInvalidFilename, getSaveFile, showWarning, tooltip


class ExportDialog(QDialog):
    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        did: Optional[int] = None,
        cids: Optional[List[int]] = None,
    ):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.col = mw.col.weakref()
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.exporter = None
        self.cids = cids
        self.setup(did)
        self.exec_()

    def setup(self, did: Optional[int]):
        self.exporters = exporters()
        # if a deck specified, start with .apkg type selected
        idx = 0
        if did or self.cids:
            for c, (k, e) in enumerate(self.exporters):
                if e.ext == ".apkg":
                    idx = c
                    break
        self.frm.format.insertItems(0, [e[0] for e in self.exporters])
        self.frm.format.setCurrentIndex(idx)
        self.frm.format.activated.connect(self.exporterChanged)
        self.exporterChanged(idx)
        # deck list
        if self.cids is None:
            self.decks = [_("All Decks")] + sorted(self.col.decks.allNames())
        else:
            self.decks = [_("Selected Notes")]
        self.frm.deck.addItems(self.decks)
        # save button
        b = QPushButton(_("Export..."))
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)
        # set default option if accessed through deck button
        if did:
            name = self.mw.col.decks.get(did)["name"]
            index = self.frm.deck.findText(name)
            self.frm.deck.setCurrentIndex(index)

    def exporterChanged(self, idx):
        self.exporter = self.exporters[idx][1](self.col)
        self.isApkg = self.exporter.ext == ".apkg"
        self.isVerbatim = getattr(self.exporter, "verbatim", False)
        self.isTextNote = hasattr(self.exporter, "includeTags")
        self.frm.includeSched.setVisible(
            getattr(self.exporter, "includeSched", None) is not None
        )
        self.frm.includeMedia.setVisible(
            getattr(self.exporter, "includeMedia", None) is not None
        )
        self.frm.includeTags.setVisible(
            getattr(self.exporter, "includeTags", None) is not None
        )
        html = getattr(self.exporter, "includeHTML", None)
        if html is not None:
            self.frm.includeHTML.setVisible(True)
            self.frm.includeHTML.setChecked(html)
        else:
            self.frm.includeHTML.setVisible(False)
        # show deck list?
        self.frm.deck.setVisible(not self.isVerbatim)

    def accept(self):
        self.exporter.includeSched = self.frm.includeSched.isChecked()
        self.exporter.includeMedia = self.frm.includeMedia.isChecked()
        self.exporter.includeTags = self.frm.includeTags.isChecked()
        self.exporter.includeHTML = self.frm.includeHTML.isChecked()
        idx = self.frm.deck.currentIndex()
        if self.cids is not None:
            # Browser Selection
            self.exporter.cids = self.cids
            self.exporter.did = None
        elif idx == 0:
            # All decks
            self.exporter.did = None
            self.exporter.cids = None
        else:
            # Deck idx-1 in the list of decks
            self.exporter.cids = None
            name = self.decks[self.frm.deck.currentIndex()]
            self.exporter.did = self.col.decks.id(name)
        if self.isVerbatim:
            name = time.strftime("-%Y-%m-%d@%H-%M-%S", time.localtime(time.time()))
            deck_name = _("collection") + name
        else:
            # Get deck name and remove invalid filename characters
            deck_name = self.decks[self.frm.deck.currentIndex()]
            deck_name = re.sub('[\\\\/?<>:*|"^]', "_", deck_name)

        filename = "{0}{1}".format(deck_name, self.exporter.ext)
        while 1:
            file = getSaveFile(
                self,
                _("Export"),
                "export",
                self.exporter.key,
                self.exporter.ext,
                fname=filename,
            )
            if not file:
                return
            if checkInvalidFilename(os.path.basename(file), dirsep=False):
                continue
            if os.path.commonprefix([self.mw.pm.base, file]) == self.mw.pm.base:
                showWarning("Please choose a different export location.")
                continue
            break
        self.hide()
        if file:
            # check we can write to file
            try:
                f = open(file, "wb")
                f.close()
            except (OSError, IOError) as e:
                showWarning(_("Couldn't save file: %s") % str(e))
            else:
                os.unlink(file)

            # progress handler
            def exported_media(cnt):
                self.mw.taskman.run_on_main(
                    lambda: self.mw.progress.update(
                        label=ngettext(
                            "Exported %d media file", "Exported %d media files", cnt
                        )
                        % cnt
                    )
                )

            def do_export():
                self.exporter.exportInto(file)

            def on_done(future: Future):
                self.mw.progress.finish()
                hooks.media_files_did_export.remove(exported_media)
                # raises if exporter failed
                future.result()
                self.on_export_finished()

            self.mw.progress.start(immediate=True)
            hooks.media_files_did_export.append(exported_media)

            self.mw.taskman.run_in_background(do_export, on_done)

    def on_export_finished(self):
        if self.isVerbatim:
            msg = _("Collection exported.")
            self.mw.reopen()
        else:
            if self.isTextNote:
                msg = (
                    ngettext(
                        "%d note exported.", "%d notes exported.", self.exporter.count,
                    )
                    % self.exporter.count
                )
            else:
                msg = (
                    ngettext(
                        "%d card exported.", "%d cards exported.", self.exporter.count,
                    )
                    % self.exporter.count
                )
        tooltip(msg, period=3000)
        QDialog.reject(self)
