# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
from abc import ABC, abstractmethod
from dataclasses import dataclass
from itertools import chain
from typing import Any, Tuple, Type

import aqt.main
from anki.collection import (
    Collection,
    DupeResolution,
    ImportCsvRequest,
    ImportLogWithChanges,
    Progress,
)
from anki.errors import Interrupted
from anki.foreign_data import mnemosyne
from anki.lang import without_unicode_isolation
from aqt.import_export.import_csv_dialog import ImportCsvDialog
from aqt.operations import CollectionOp, QueryOp
from aqt.progress import ProgressUpdate
from aqt.qt import *
from aqt.utils import askUser, getFile, showText, showWarning, tooltip, tr


class Importer(ABC):
    accepted_file_endings: list[str]

    @classmethod
    def can_import(cls, lowercase_filename: str) -> bool:
        return any(
            lowercase_filename.endswith(ending) for ending in cls.accepted_file_endings
        )

    @classmethod
    @abstractmethod
    def do_import(cls, mw: aqt.main.AnkiQt, path: str) -> None:
        ...


class ColpkgImporter(Importer):
    accepted_file_endings = [".apkg", ".colpkg"]

    @staticmethod
    def can_import(filename: str) -> bool:
        return (
            filename == "collection.apkg"
            or (filename.startswith("backup-") and filename.endswith(".apkg"))
            or filename.endswith(".colpkg")
        )

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        if askUser(
            tr.importing_this_will_delete_your_existing_collection(),
            msgfunc=QMessageBox.warning,
            defaultno=True,
        ):
            ColpkgImporter._import(mw, path)

    @staticmethod
    def _import(mw: aqt.main.AnkiQt, file: str) -> None:
        def on_success() -> None:
            mw.loadCollection()
            tooltip(tr.importing_importing_complete())

        def on_failure(err: Exception) -> None:
            mw.loadCollection()
            if not isinstance(err, Interrupted):
                showWarning(str(err))

        QueryOp(
            parent=mw,
            op=lambda _: mw.create_backup_now(),
            success=lambda _: mw.unloadCollection(
                lambda: import_collection_package_op(mw, file, on_success)
                .failure(on_failure)
                .run_in_background()
            ),
        ).with_progress().run_in_background()


class ApkgImporter(Importer):
    accepted_file_endings = [".apkg", ".zip"]

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        CollectionOp(
            parent=mw,
            op=lambda col: col.import_anki_package(path),
        ).with_backend_progress(import_progress_update).success(
            show_import_log
        ).run_in_background()


class MnemosyneImporter(Importer):
    accepted_file_endings = [".db"]

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        QueryOp(
            parent=mw,
            op=lambda col: mnemosyne.serialize(path, col.decks.current()["id"]),
            success=lambda json: import_json_string(mw, json),
        ).with_progress().run_in_background()


class CsvImporter(Importer):
    accepted_file_endings = [".csv", ".tsv", ".txt"]

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        def on_accepted(request: ImportCsvRequest) -> None:
            CollectionOp(
                parent=mw,
                op=lambda col: col.import_csv(request),
            ).with_backend_progress(import_progress_update).success(
                show_import_log
            ).run_in_background()

        ImportCsvDialog(mw, path, on_accepted)


class JsonImporter(Importer):
    accepted_file_endings = [".anki-json"]

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        CollectionOp(
            parent=mw,
            op=lambda col: col.import_json_file(path),
        ).with_backend_progress(import_progress_update).success(
            show_import_log
        ).run_in_background()


IMPORTERS: list[Type[Importer]] = [
    ColpkgImporter,
    ApkgImporter,
    MnemosyneImporter,
    CsvImporter,
]


def legacy_file_endings(col: Collection) -> list[str]:
    from anki.importing import AnkiPackageImporter
    from anki.importing import MnemosyneImporter as LegacyMnemosyneImporter
    from anki.importing import TextImporter, importers

    return [
        ext
        for (text, importer) in importers(col)
        if importer not in (TextImporter, AnkiPackageImporter, LegacyMnemosyneImporter)
        for ext in re.findall(r"[( ]?\*(\..+?)[) ]", text)
    ]


def import_file(mw: aqt.main.AnkiQt, path: str) -> None:
    filename = os.path.basename(path).lower()

    if any(filename.endswith(ext) for ext in legacy_file_endings(mw.col)):
        import aqt.importing

        aqt.importing.importFile(mw, path)
        return

    for importer in IMPORTERS:
        if importer.can_import(filename):
            importer.do_import(mw, path)
            return

    showWarning("Unsupported file type.")


def prompt_for_file_then_import(mw: aqt.main.AnkiQt) -> None:
    if path := get_file_path(mw):
        import_file(mw, path)


def get_file_path(mw: aqt.main.AnkiQt) -> str | None:
    filter = without_unicode_isolation(
        tr.importing_all_supported_formats(
            val="({})".format(
                " ".join(f"*{ending}" for ending in all_accepted_file_endings(mw))
            )
        )
    )
    if file := getFile(mw, tr.actions_import(), None, key="import", filter=filter):
        return str(file)
    return None


def all_accepted_file_endings(mw: aqt.main.AnkiQt) -> set[str]:
    return set(
        chain(
            *(importer.accepted_file_endings for importer in IMPORTERS),
            legacy_file_endings(mw.col),
        )
    )


def import_collection_package_op(
    mw: aqt.main.AnkiQt, path: str, success: Callable[[], None]
) -> QueryOp[None]:
    def op(_: Collection) -> None:
        col_path = mw.pm.collectionPath()
        media_folder = os.path.join(mw.pm.profileFolder(), "collection.media")
        media_db = os.path.join(mw.pm.profileFolder(), "collection.media.db2")
        mw.backend.import_collection_package(
            col_path=col_path,
            backup_path=path,
            media_folder=media_folder,
            media_db=media_db,
        )

    return QueryOp(parent=mw, op=op, success=lambda _: success()).with_backend_progress(
        import_progress_update
    )


def import_json_string(mw: aqt.main.AnkiQt, json: str) -> None:
    CollectionOp(
        parent=mw, op=lambda col: col.import_json_string(json)
    ).with_backend_progress(import_progress_update).success(
        show_import_log
    ).run_in_background()


def show_import_log(log_with_changes: ImportLogWithChanges) -> None:
    showText(stringify_log(log_with_changes.log), plain_text_edit=True)


def stringify_log(log: ImportLogWithChanges.Log) -> str:
    queues = log_queues(log)
    return "\n".join(
        chain(
            (tr.importing_notes_found_in_file(val=log.found_notes),),
            (
                queue.summary_template(val=len(queue.notes))
                for queue in queues
                if queue.notes
            ),
            ("",),
            *(
                [
                    f"[{queue.action_string}] {', '.join(note.fields)}"
                    for note in queue.notes
                ]
                for queue in queues
            ),
        )
    )


def import_progress_update(progress: Progress, update: ProgressUpdate) -> None:
    if not progress.HasField("importing"):
        return
    update.label = progress.importing
    if update.user_wants_abort:
        update.abort = True


@dataclass
class LogQueue:
    notes: Any
    # Callable[[Union[str, int, float]], str] (if mypy understood kwargs)
    summary_template: Any
    action_string: str


def first_field_queue(log: ImportLogWithChanges.Log) -> LogQueue:
    if log.dupe_resolution == DupeResolution.ADD:
        summary_template = tr.importing_added_duplicate_with_first_field
        action_string = tr.adding_added()
    elif log.dupe_resolution == DupeResolution.IGNORE:
        summary_template = tr.importing_first_field_matched
        action_string = tr.importing_skipped()
    else:
        summary_template = tr.importing_first_field_matched
        action_string = tr.importing_updated()
    return LogQueue(log.first_field_match, summary_template, action_string)


def log_queues(log: ImportLogWithChanges.Log) -> Tuple[LogQueue, ...]:
    return (
        LogQueue(
            log.conflicting,
            tr.importing_notes_that_could_not_be_imported,
            tr.importing_skipped(),
        ),
        LogQueue(
            log.updated,
            tr.importing_notes_updated_as_file_had_newer,
            tr.importing_updated(),
        ),
        LogQueue(log.new, tr.importing_notes_added_from_file, tr.adding_added()),
        LogQueue(
            log.duplicate,
            tr.importing_notes_skipped_as_theyre_already_in,
            tr.importing_identical(),
        ),
        first_field_queue(log),
        LogQueue(
            log.missing_notetype,
            lambda val: f"Notes skipped, as their notetype was missing: {val}",
            tr.importing_skipped(),
        ),
        LogQueue(
            log.missing_deck,
            lambda val: f"Notes skipped, as their deck was missing: {val}",
            tr.importing_skipped(),
        ),
        LogQueue(
            log.empty_first_field,
            tr.importing_empty_first_field,
            tr.importing_skipped(),
        ),
    )
