# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import tempfile

from anki import exporting, importing
from anki.collection import Collection
from anki.decks import DeckId
from tests.shared import getEmptyCol


def _apkg_path() -> str:
    dir = tempfile.mkdtemp(prefix="anki")
    return os.path.join(dir, "export.apkg")


def _add_note(col: Collection, front: str, deck_id: DeckId) -> None:
    note = col.newNote()
    note["Front"] = front
    col.add_note(note, deck_id)


def _review_first_card(col: Collection) -> None:
    card = col.sched.getCard()
    assert card is not None
    col.sched.answerCard(card, 3)
    assert col.sched.answerButtons(card) == 4


# The module-level __getattr__ is hidden from type checkers, so the
# deprecated names must be looked up dynamically.
def _importer(col: Collection, path: str) -> importing._LegacyAnkiPackageImporter:
    return getattr(importing, "AnkiPackageImporter")(col, path)


def _exporter(col: Collection) -> exporting._LegacyAnkiPackageExporter:
    return getattr(exporting, "AnkiPackageExporter")(col)


def test_deprecated_aliases(capsys) -> None:
    importer = getattr(importing, "AnkiPackageImporter")
    assert importer is importing._LegacyAnkiPackageImporter
    assert "AnkiPackageImporter is deprecated" in capsys.readouterr().out

    exporter = getattr(exporting, "AnkiPackageExporter")
    assert exporter is exporting._LegacyAnkiPackageExporter
    assert "AnkiPackageExporter is deprecated" in capsys.readouterr().out


def test_export_whole_collection_and_import() -> None:
    col = getEmptyCol()
    deck_id = col.decks.id("Other")
    _add_note(col, "one", DeckId(1))
    _add_note(col, "two", deck_id)
    path = _apkg_path()

    exporter = _exporter(col)
    exporter.exportInto(path)
    assert os.path.getsize(path) > 0
    col.close()

    col2 = getEmptyCol()
    _importer(col2, path).run()
    assert sorted(col2.find_notes("")) != []
    assert col2.note_count() == 2
    assert col2.decks.by_name("Other") is not None
    assert len(col2.find_notes('"deck:Other"')) == 1
    col2.close()


def test_export_deck_limit() -> None:
    col = getEmptyCol()
    deck_id = col.decks.id("Other")
    _add_note(col, "one", DeckId(1))
    _add_note(col, "two", deck_id)
    path = _apkg_path()

    exporter = _exporter(col)
    exporter.did = deck_id
    exporter.exportInto(path)
    col.close()

    col2 = getEmptyCol()
    _importer(col2, path).run()
    assert col2.note_count() == 1
    assert col2.decks.by_name("Other") is not None
    col2.close()


def test_export_without_scheduling() -> None:
    col = getEmptyCol()
    _add_note(col, "one", DeckId(1))
    _review_first_card(col)
    path = _apkg_path()

    exporter = _exporter(col)
    assert exporter.includeSched is False
    exporter.exportInto(path)
    col.close()

    col2 = getEmptyCol()
    _importer(col2, path).run()
    (card_id,) = col2.find_cards("")
    card = col2.get_card(card_id)
    assert card.type == 0
    assert card.reps == 0
    col2.close()


def test_export_with_scheduling() -> None:
    col = getEmptyCol()
    _add_note(col, "one", DeckId(1))
    _review_first_card(col)
    path = _apkg_path()

    exporter = _exporter(col)
    exporter.includeSched = True
    exporter.exportInto(path)
    col.close()

    col2 = getEmptyCol()
    _importer(col2, path).run()
    (card_id,) = col2.find_cards("")
    card = col2.get_card(card_id)
    assert card.type != 0
    assert card.reps == 1
    col2.close()


def test_export_with_media() -> None:
    col = getEmptyCol()
    media_dir = tempfile.mkdtemp(prefix="anki")
    media_path = os.path.join(media_dir, "foo.jpg")
    with open(media_path, "w") as file:
        file.write("hello")
    fname = col.media.add_file(media_path)
    _add_note(col, f'<img src="{fname}">', DeckId(1))
    path = _apkg_path()

    _exporter(col).exportInto(path)
    col.close()

    col2 = getEmptyCol()
    _importer(col2, path).run()
    assert col2.media.have(fname)
    col2.close()
