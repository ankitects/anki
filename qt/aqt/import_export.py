# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from concurrent.futures import Future

import aqt.main
from anki.errors import Interrupted
from aqt.operations import (
    ClosedCollectionOpWithBackendProgress,
    CollectionOpWithBackendProgress,
)
from aqt.qt import *
from aqt.utils import askUser, getFile, showInfo, showText, showWarning, tooltip, tr


def import_file(mw: aqt.main.AnkiQt) -> None:
    if not (path := get_file_path(mw)):
        return

    filename = os.path.basename(path).lower()
    if filename.endswith(".anki"):
        showInfo(tr.importing_anki_files_are_from_a_very())
    elif filename.endswith(".anki2"):
        showInfo(tr.importing_anki2_files_are_not_directly_importable())
    elif is_collection_package(filename):
        maybe_import_collection_package(mw, path)
    elif filename.endswith(".apkg"):
        import_anki_package(mw, path)
    else:
        raise NotImplementedError


def get_file_path(mw: aqt.main.AnkiQt) -> str | None:
    if file := getFile(
        mw,
        tr.actions_import(),
        None,
        key="import",
        filter=tr.importing_packaged_anki_deckcollection_apkg_colpkg_zip(),
    ):
        return str(file)
    return None


def is_collection_package(filename: str) -> bool:
    return (
        filename == "collection.apkg"
        or (filename.startswith("backup-") and filename.endswith(".apkg"))
        or filename.endswith(".colpkg")
    )


def maybe_import_collection_package(mw: aqt.main.AnkiQt, path: str) -> None:
    if askUser(
        tr.importing_this_will_delete_your_existing_collection(),
        msgfunc=QMessageBox.warning,
        defaultno=True,
    ):
        import_collection_package(mw, path)


def import_collection_package(mw: aqt.main.AnkiQt, file: str) -> None:
    def on_success(_future: Future) -> None:
        mw.loadCollection()
        tooltip(tr.importing_importing_complete())

    def on_failure(err: Exception) -> None:
        mw.loadCollection()
        if not isinstance(err, Interrupted):
            showWarning(str(err))

    import_collection_package_op(mw, file).success(on_success).failure(
        on_failure
    ).run_in_background()


def import_collection_package_op(
    mw: aqt.main.AnkiQt, path: str
) -> ClosedCollectionOpWithBackendProgress:
    def op() -> None:
        col_path = mw.pm.collectionPath()
        media_folder = os.path.join(mw.pm.profileFolder(), "collection.media")
        mw.backend.import_collection_package(
            col_path=col_path, backup_path=path, media_folder=media_folder
        )

    return ClosedCollectionOpWithBackendProgress(
        parent=mw,
        op=op,
        key="importing",
    )


def import_anki_package(mw: aqt.main.AnkiQt, path: str) -> None:
    CollectionOpWithBackendProgress(
        parent=mw,
        op=lambda col: col.import_anki_package(path),
        key="importing",
    ).success(show_import_log).run_in_background()


def show_import_log(future: Future) -> None:
    log = future.log  # type: ignore
    total = len(log.conflicting) + len(log.updated) + len(log.new) + len(log.duplicate)

    text = f"""{tr.importing_notes_found_in_file(val=total)}
{tr.importing_notes_that_could_not_be_imported(val=len(log.conflicting))}
{tr.importing_notes_updated_as_file_had_newer(val=len(log.updated))}
{tr.importing_notes_added_from_file(val=len(log.new))}
{tr.importing_notes_skipped_as_theyre_already_in(val=len(log.duplicate))}

{log_rows(log.conflicting, tr.importing_skipped())}
{log_rows(log.updated, tr.importing_updated())}
{log_rows(log.new, tr.adding_added())}
{log_rows(log.duplicate, tr.importing_identical())}
    """

    showText(text, plain_text_edit=True)


def log_rows(rows: list, action: str) -> str:
    return "\n".join(f"[{action}] {', '.join(note.fields)}" for note in rows)
