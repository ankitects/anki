# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Any, Callable, Sequence, Type, Union

from anki import hooks
from anki.collection import Collection
from anki.importing.anki2 import Anki2Importer
from anki.importing.apkg import AnkiPackageImporter
from anki.importing.base import Importer
from anki.importing.csvfile import TextImporter
from anki.importing.mnemo import MnemosyneImporter
from anki.importing.pauker import PaukerImporter
from anki.importing.supermemo_xml import SupermemoXmlImporter  # type: ignore
from anki.lang import TR


def importers(col: Collection) -> Sequence[tuple[str, type[Importer]]]:
    importers = [
        (col.tr.importing_text_separated_by_tabs_or_semicolons(), TextImporter),
        (
            col.tr.importing_packaged_anki_deckcollection_apkg_colpkg_zip(),
            AnkiPackageImporter,
        ),
        (col.tr.importing_mnemosyne_20_deck_db(), MnemosyneImporter),
        (col.tr.importing_supermemo_xml_export_xml(), SupermemoXmlImporter),
        (col.tr.importing_pauker_18_lesson_paugz(), PaukerImporter),
    ]
    hooks.importing_importers(importers)
    return importers
