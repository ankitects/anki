# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Any, Callable, Sequence, Tuple, Type, Union

from anki import Collection
from anki.importing.anki2 import Anki2Importer
from anki.importing.apkg import AnkiPackageImporter
from anki.importing.base import Importer
from anki.importing.csvfile import TextImporter
from anki.importing.mnemo import MnemosyneImporter
from anki.importing.pauker import PaukerImporter
from anki.importing.supermemo_xml import SupermemoXmlImporter  # type: ignore
from anki.lang import TR


def importers(col: Collection) -> Sequence[Tuple[str, Type[Importer]]]:
    return (
        (col.tr(TR.IMPORTING_TEXT_SEPARATED_BY_TABS_OR_SEMICOLONS), TextImporter),
        (
            col.tr(TR.IMPORTING_PACKAGED_ANKI_DECKCOLLECTION_APKG_COLPKG_ZIP),
            AnkiPackageImporter,
        ),
        (col.tr(TR.IMPORTING_MNEMOSYNE_20_DECK_DB), MnemosyneImporter),
        (col.tr(TR.IMPORTING_SUPERMEMO_XML_EXPORT_XML), SupermemoXmlImporter),
        (col.tr(TR.IMPORTING_PAUKER_18_LESSON_PAUGZ), PaukerImporter),
    )
