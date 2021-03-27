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
from anki.cards import CardId
from anki.decks import DeckId
from anki.exporting import Exporter, exporters
from aqt.qt import *
from aqt.utils import (
    checkInvalidFilename,
    disable_help_button,
    getSaveFile,
    showWarning,
    tooltip,
    tr,
)


class ExportDialog(QDialog):
    def __init__(
        self,
        mw: aqt.main.AnkiQt,
        did: Optional[DeckId] = None,
        cids: Optional[List[CardId]] = None,
    ):
        QDialog.__init__(self, mw, Qt.Window)
        self.mw = mw
        self.col = mw.col.weakref()
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.exporter: Optional[Exporter] = None
        self.cids = cids
        disable_help_button(self)
        self.setup(did)
        self.exec_()

    def setup(self, did: Optional[DeckId]) -> None:
        self.exporters = exporters(self.col)
        # if a deck specified, start with .apkg type selected
        idx = 0
        if did or self.cids:
            for c, (k, e) in enumerate(self.exporters):
                if e.ext == ".apkg":
                    idx = c
                    break
        self.frm.format.insertItems(0, [e[0] for e in self.exporters])
        self.frm.format.setCurrentIndex(idx)
        qconnect(self.frm.format.activated, self.exporterChanged)
        self.exporterChanged(idx)
        # deck list
        if self.cids is None:
            self.decks = [tr.exporting_all_decks()]
            self.decks.extend(d.name for d in self.col.decks.all_names_and_ids())
        else:
            self.decks = [tr.exporting_selected_notes()]
        self.frm.deck.addItems(self.decks)
        # save button
        b = QPushButton(tr.exporting_export())
        self.frm.buttonBox.addButton(b, QDialogButtonBox.AcceptRole)
        # set default option if accessed through deck button
        if did:
            name = self.mw.col.decks.get(did)["name"]
            index = self.frm.deck.findText(name)
            self.frm.deck.setCurrentIndex(index)

    def exporterChanged(self, idx: int) -> None:
        self.exporter = self.exporters[idx][1](self.col)
        self.isApkg = self.exporter.ext == ".apkg"
        self.isVerbatim = getattr(self.exporter, "verbatim", False)
        self.isTextNote = getattr(self.exporter, "includeTags", False)
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

    def accept(self) -> None:
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
            deck_name = tr.exporting_collection() + name
        else:
            # Get deck name and remove invalid filename characters
            deck_name = self.decks[self.frm.deck.currentIndex()]
            deck_name = re.sub('[\\\\/?<>:*|"^]', "_", deck_name)

        filename = f"{deck_name}{self.exporter.ext}"
        if callable(self.exporter.key):
            key_str = self.exporter.key(self.col)
        else:
            key_str = self.exporter.key
        while 1:
            file = getSaveFile(
                self,
                tr.actions_export(),
                "export",
                key_str,
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
            except OSError as e:
                showWarning(tr.exporting_couldnt_save_file(val=str(e)))
            else:
                os.unlink(file)

            # progress handler
            def exported_media(cnt: int) -> None:
                self.mw.taskman.run_on_main(
                    lambda: self.mw.progress.update(
                        label=tr.exporting_exported_media_file(count=cnt)
                    )
                )

            def do_export() -> None:
                self.exporter.exportInto(file)

            def on_done(future: Future) -> None:
                self.mw.progress.finish()
                hooks.media_files_did_export.remove(exported_media)
                # raises if exporter failed
                future.result()
                self.on_export_finished()

            self.mw.progress.start()
            hooks.media_files_did_export.append(exported_media)

            self.mw.taskman.run_in_background(do_export, on_done)

    def on_export_finished(self) -> None:
        if self.isVerbatim:
            msg = tr.exporting_collection_exported()
            self.mw.reopen()
        else:
            if self.isTextNote:
                msg = tr.exporting_note_exported(count=self.exporter.count)
            else:
                msg = tr.exporting_card_exported(count=self.exporter.count)
        tooltip(msg, period=3000)
        QDialog.reject(self)
