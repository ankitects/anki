# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.importing.anki2 import Anki2Importer
from anki.importing.apkg import AnkiPackageImporter
from anki.importing.csvfile import TextImporter
from anki.importing.mnemo import MnemosyneImporter
from anki.importing.pauker import PaukerImporter
from anki.importing.supermemo_xml import SupermemoXmlImporter  # type: ignore
from anki.lang import _

Importers = (
    (_("Text separated by tabs or semicolons (*)"), TextImporter),
    (_("Packaged Anki Deck/Collection (*.apkg *.colpkg *.zip)"), AnkiPackageImporter),
    (_("Mnemosyne 2.0 Deck (*.db)"), MnemosyneImporter),
    (_("Supermemo XML export (*.xml)"), SupermemoXmlImporter),
    (_("Pauker 1.8 Lesson (*.pau.gz)"), PaukerImporter),
)
