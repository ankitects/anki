# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
import time
from concurrent.futures import Future

import aqt
import aqt.forms
import aqt.main
from anki import hooks
from anki.cards import CardId
from anki.decks import DeckId
from anki.exporting import Exporter, exporters
from aqt import gui_hooks
from aqt.errors import show_exception
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
        did: DeckId | None = None,
        cids: list[CardId] | None = None,
        parent: QWidget | None = None,
    ):
        QDialog.__init__(self, parent or mw, Qt.WindowType.Window)
        self.mw = mw
        self.col = mw.col.weakref()
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.frm.legacy_support.setVisible(False)
        self.exporter: Exporter | None = None
        self.cids = cids
        disable_help_button(self)
        self.setup(did)
        self.exec()

    def setup(self, did: DeckId | None) -> None:
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
        self.frm.buttonBox.addButton(b, QDialogButtonBox.ButtonRole.AcceptRole)
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
        # used by the new export screen
        self.frm.includeDeck.setVisible(False)
        self.frm.includeNotetype.setVisible(False)
        self.frm.includeGuid.setVisible(False)

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
            file = os.path.normpath(file)
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

            # progress handler: old apkg exporter
            def exported_media_count(cnt: int) -> None:
                self.mw.taskman.run_on_main(
                    lambda: self.mw.progress.update(
                        label=tr.exporting_exported_media_file(count=cnt)
                    )
                )

            # progress handler: adaptor for new colpkg importer into old exporting screen.
            # don't rename this; there's a hack in pylib/exporting.py that assumes this
            # name
            def exported_media(progress: str) -> None:
                self.mw.taskman.run_on_main(
                    lambda: self.mw.progress.update(label=progress)
                )

            def do_export() -> None:
                self.exporter.exportInto(file)

            def on_done(future: Future) -> None:
                self.mw.progress.finish()
                hooks.media_files_did_export.remove(exported_media_count)
                hooks.legacy_export_progress.remove(exported_media)
                try:
                    # raises if exporter failed
                    future.result()
                except Exception as exc:
                    show_exception(parent=self.mw, exception=exc)
                    self.on_export_failed()
                else:
                    self.on_export_finished()

            gui_hooks.legacy_exporter_will_export(self.exporter)
            if self.isVerbatim:
                gui_hooks.collection_will_temporarily_close(self.mw.col)
            self.mw.progress.start()
            hooks.media_files_did_export.append(exported_media_count)
            hooks.legacy_export_progress.append(exported_media)

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
        gui_hooks.legacy_exporter_did_export(self.exporter)
        tooltip(msg, period=3000)
        QDialog.reject(self)

    def on_export_failed(self) -> None:
        if self.isVerbatim:
            self.mw.reopen()
        QDialog.reject(self)
