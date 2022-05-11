# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from abc import ABC, abstractmethod
from itertools import chain
from typing import Type

import aqt.main
from anki.collection import Collection, ImportLogWithChanges, Progress
from anki.errors import Interrupted
from anki.foreign_data import mnemosyne
from anki.lang import without_unicode_isolation
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
            op=lambda _: mnemosyne.serialize(path),
            success=lambda json: import_json_string(mw, json),
        ).with_progress().run_in_background()


class CsvImporter(Importer):
    accepted_file_endings = [".csv", ".tsv", ".txt"]

    @staticmethod
    def do_import(mw: aqt.main.AnkiQt, path: str) -> None:
        import aqt.import_export.import_dialog

        aqt.import_export.import_dialog.ImportDialog(mw, path)


class JsonImporter(Importer):
    accepted_file_endings = [".json", ".anki-json"]

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


def import_file(mw: aqt.main.AnkiQt, path: str) -> None:
    filename = os.path.basename(path).lower()
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
                " ".join(f"*{ending}" for ending in all_accepted_file_endings())
            )
        )
    )
    if file := getFile(mw, tr.actions_import(), None, key="import", filter=filter):
        return str(file)
    return None


def all_accepted_file_endings() -> set[str]:
    return set(chain(*(importer.accepted_file_endings for importer in IMPORTERS)))


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
    total = len(log.conflicting) + len(log.updated) + len(log.new) + len(log.duplicate)
    return "\n".join(
        chain(
            (tr.importing_notes_found_in_file(val=total),),
            (
                template_string(val=len(row))
                for (row, template_string) in (
                    (log.conflicting, tr.importing_notes_that_could_not_be_imported),
                    (log.updated, tr.importing_notes_updated_as_file_had_newer),
                    (log.new, tr.importing_notes_added_from_file),
                    (log.duplicate, tr.importing_notes_skipped_as_theyre_already_in),
                )
                if row
            ),
            ("",),
            *(
                [f"[{action}] {', '.join(note.fields)}" for note in rows]
                for (rows, action) in (
                    (log.conflicting, tr.importing_skipped()),
                    (log.updated, tr.importing_updated()),
                    (log.new, tr.adding_added()),
                    (log.duplicate, tr.importing_identical()),
                )
            ),
        )
    )


def import_progress_update(progress: Progress, update: ProgressUpdate) -> None:
    if not progress.HasField("importing"):
        return
    update.label = progress.importing
    if update.user_wants_abort:
        update.abort = True
