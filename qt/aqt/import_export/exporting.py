# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import os
import re
import time
from abc import ABC, abstractmethod
from collections.abc import Sequence
from dataclasses import dataclass

import aqt.forms
import aqt.main
from anki.collection import (
    DeckIdLimit,
    ExportAnkiPackageOptions,
    ExportLimit,
    NoteIdsLimit,
    Progress,
)
from anki.decks import DeckId, DeckNameId
from anki.notes import NoteId
from aqt import gui_hooks
from aqt.errors import show_exception
from aqt.operations import QueryOp
from aqt.progress import ProgressUpdate
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
        nids: Sequence[NoteId] | None = None,
        parent: QWidget | None = None,
    ):
        QDialog.__init__(self, parent or mw, Qt.WindowType.Window)
        self.mw = mw
        self.col = mw.col.weakref()
        self.frm = aqt.forms.exporting.Ui_ExportDialog()
        self.frm.setupUi(self)
        self.exporter: Exporter
        self.nids = nids
        disable_help_button(self)
        self.setup(did)
        self.open()

    def setup(self, did: DeckId | None) -> None:
        self.exporter_classes: list[type[Exporter]] = [
            ApkgExporter,
            ColpkgExporter,
            NoteCsvExporter,
            CardCsvExporter,
        ]
        gui_hooks.exporters_list_did_initialize(self.exporter_classes)
        self.frm.format.insertItems(
            0, [f"{e.name()} (.{e.extension})" for e in self.exporter_classes]
        )
        qconnect(self.frm.format.activated, self.exporter_changed)
        if self.nids is None and not did:
            # file>export defaults to colpkg
            default_exporter_idx = 1
        else:
            default_exporter_idx = 0
        self.frm.format.setCurrentIndex(default_exporter_idx)
        self.exporter_changed(default_exporter_idx)
        # deck list
        if self.nids is None:
            self.all_decks = self.col.decks.all_names_and_ids()
            decks = [tr.exporting_all_decks()]
            decks.extend(d.name for d in self.all_decks)
        else:
            decks = [tr.exporting_selected_notes()]
        self.frm.deck.addItems(decks)
        # save button
        b = QPushButton(tr.exporting_export())
        self.frm.buttonBox.addButton(b, QDialogButtonBox.ButtonRole.AcceptRole)
        self.frm.includeHTML.setChecked(True)
        # set default option if accessed through deck button
        if did:
            deck = self.mw.col.decks.get(did)
            assert deck is not None
            name = deck["name"]
            index = self.frm.deck.findText(name)
            self.frm.deck.setCurrentIndex(index)
            self.frm.includeSched.setChecked(False)

    def exporter_changed(self, idx: int) -> None:
        self.exporter = self.exporter_classes[idx]()
        self.frm.includeSched.setVisible(self.exporter.show_include_scheduling)
        self.frm.include_deck_configs.setVisible(
            self.exporter.show_include_deck_configs
        )
        self.frm.includeMedia.setVisible(self.exporter.show_include_media)
        self.frm.includeTags.setVisible(self.exporter.show_include_tags)
        self.frm.includeHTML.setVisible(self.exporter.show_include_html)
        self.frm.includeDeck.setVisible(self.exporter.show_include_deck)
        self.frm.includeNotetype.setVisible(self.exporter.show_include_notetype)
        self.frm.includeGuid.setVisible(self.exporter.show_include_guid)
        self.frm.legacy_support.setVisible(self.exporter.show_legacy_support)
        self.frm.deck.setVisible(self.exporter.show_deck_list)

    def accept(self) -> None:
        if not (out_path := self.get_out_path()):
            return
        self.exporter.export(self.mw, self.options(out_path))
        QDialog.reject(self)

    def get_out_path(self) -> str | None:
        filename = self.filename()
        while True:
            path = getSaveFile(
                parent=self,
                title=tr.actions_export(),
                dir_description="export",
                key=self.exporter.name(),
                ext="." + self.exporter.extension,
                fname=filename,
            )
            if not path:
                return None
            if checkInvalidFilename(os.path.basename(path), dirsep=False):
                continue
            path = os.path.normpath(path)
            if os.path.commonprefix([self.mw.pm.base, path]) == self.mw.pm.base:
                showWarning("Please choose a different export location.")
                continue
            break
        return path

    def options(self, out_path: str) -> ExportOptions:
        limit: ExportLimit | None = None
        if self.nids:
            limit = NoteIdsLimit(self.nids)
        elif current_deck_id := self.current_deck_id():
            limit = DeckIdLimit(current_deck_id)

        return ExportOptions(
            out_path=out_path,
            include_scheduling=self.frm.includeSched.isChecked(),
            include_deck_configs=self.frm.include_deck_configs.isChecked(),
            include_media=self.frm.includeMedia.isChecked(),
            include_tags=self.frm.includeTags.isChecked(),
            include_html=self.frm.includeHTML.isChecked(),
            include_deck=self.frm.includeDeck.isChecked(),
            include_notetype=self.frm.includeNotetype.isChecked(),
            include_guid=self.frm.includeGuid.isChecked(),
            legacy_support=self.frm.legacy_support.isChecked(),
            limit=limit,
        )

    def current_deck_id(self) -> DeckId | None:
        return (deck := self.current_deck()) and DeckId(deck.id) or None

    def current_deck(self) -> DeckNameId | None:
        if self.exporter.show_deck_list:
            if idx := self.frm.deck.currentIndex():
                return self.all_decks[idx - 1]
        return None

    def filename(self) -> str:
        if self.exporter.show_deck_list:
            deck_name = self.frm.deck.currentText()
            stem = re.sub('[\\\\/?<>:*|"^]', "_", deck_name)
        else:
            time_str = time.strftime("%Y-%m-%d@%H-%M-%S", time.localtime(time.time()))
            stem = f"{tr.exporting_collection()}-{time_str}"
        return f"{stem}.{self.exporter.extension}"


@dataclass
class ExportOptions:
    out_path: str
    include_scheduling: bool
    include_deck_configs: bool
    include_media: bool
    include_tags: bool
    include_html: bool
    include_deck: bool
    include_notetype: bool
    include_guid: bool
    legacy_support: bool
    limit: ExportLimit


class Exporter(ABC):
    extension: str
    show_deck_list = False
    show_include_scheduling = False
    show_include_deck_configs = False
    show_include_media = False
    show_include_tags = False
    show_include_html = False
    show_legacy_support = False
    show_include_deck = False
    show_include_notetype = False
    show_include_guid = False

    @abstractmethod
    def export(self, mw: aqt.main.AnkiQt, options: ExportOptions) -> None:
        pass

    @staticmethod
    @abstractmethod
    def name() -> str:
        pass


class ColpkgExporter(Exporter):
    extension = "colpkg"
    show_include_media = True
    show_legacy_support = True

    @staticmethod
    def name() -> str:
        return tr.exporting_anki_collection_package()

    def export(self, mw: aqt.main.AnkiQt, options: ExportOptions) -> None:
        options = gui_hooks.exporter_will_export(options, self)

        def on_success(_: None) -> None:
            mw.reopen()
            gui_hooks.exporter_did_export(options, self)
            tooltip(tr.exporting_collection_exported(), parent=mw)

        def on_failure(exception: Exception) -> None:
            mw.reopen()
            show_exception(parent=mw, exception=exception)

        gui_hooks.collection_will_temporarily_close(mw.col)
        QueryOp(
            parent=mw,
            op=lambda col: col.export_collection_package(
                options.out_path,
                include_media=options.include_media,
                legacy=options.legacy_support,
            ),
            success=on_success,
        ).with_backend_progress(export_progress_update).failure(
            on_failure
        ).run_in_background()


class ApkgExporter(Exporter):
    extension = "apkg"
    show_deck_list = True
    show_include_scheduling = True
    show_include_deck_configs = True
    show_include_media = True
    show_legacy_support = True

    @staticmethod
    def name() -> str:
        return tr.exporting_anki_deck_package()

    def export(self, mw: aqt.main.AnkiQt, options: ExportOptions) -> None:
        options = gui_hooks.exporter_will_export(options, self)

        def on_success(count: int) -> None:
            gui_hooks.exporter_did_export(options, self)
            tooltip(tr.exporting_note_exported(count=count), parent=mw)

        QueryOp(
            parent=mw,
            op=lambda col: col.export_anki_package(
                out_path=options.out_path,
                limit=options.limit,
                options=ExportAnkiPackageOptions(
                    with_scheduling=options.include_scheduling,
                    with_deck_configs=options.include_deck_configs,
                    with_media=options.include_media,
                    legacy=options.legacy_support,
                ),
            ),
            success=on_success,
        ).with_backend_progress(export_progress_update).run_in_background()


class NoteCsvExporter(Exporter):
    extension = "txt"
    show_deck_list = True
    show_include_html = True
    show_include_tags = True
    show_include_deck = True
    show_include_notetype = True
    show_include_guid = True

    @staticmethod
    def name() -> str:
        return tr.exporting_notes_in_plain_text()

    def export(self, mw: aqt.main.AnkiQt, options: ExportOptions) -> None:
        options = gui_hooks.exporter_will_export(options, self)

        def on_success(count: int) -> None:
            gui_hooks.exporter_did_export(options, self)
            tooltip(tr.exporting_note_exported(count=count), parent=mw)

        QueryOp(
            parent=mw,
            op=lambda col: col.export_note_csv(
                out_path=options.out_path,
                limit=options.limit,
                with_html=options.include_html,
                with_tags=options.include_tags,
                with_deck=options.include_deck,
                with_notetype=options.include_notetype,
                with_guid=options.include_guid,
            ),
            success=on_success,
        ).with_backend_progress(export_progress_update).run_in_background()


class CardCsvExporter(Exporter):
    extension = "txt"
    show_deck_list = True
    show_include_html = True

    @staticmethod
    def name() -> str:
        return tr.exporting_cards_in_plain_text()

    def export(self, mw: aqt.main.AnkiQt, options: ExportOptions) -> None:
        options = gui_hooks.exporter_will_export(options, self)

        def on_success(count: int) -> None:
            gui_hooks.exporter_did_export(options, self)
            tooltip(tr.exporting_card_exported(count=count), parent=mw)

        QueryOp(
            parent=mw,
            op=lambda col: col.export_card_csv(
                out_path=options.out_path,
                limit=options.limit,
                with_html=options.include_html,
            ),
            success=on_success,
        ).with_backend_progress(export_progress_update).run_in_background()


def export_progress_update(progress: Progress, update: ProgressUpdate) -> None:
    if not progress.HasField("exporting"):
        return
    update.label = progress.exporting
    if update.user_wants_abort:
        update.abort = True
