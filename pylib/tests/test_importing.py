# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

import os
from tempfile import NamedTemporaryFile

import pytest

from anki.consts import *
from anki.importing import (
    Anki2Importer,
    AnkiPackageImporter,
    MnemosyneImporter,
    SupermemoXmlImporter,
    TextImporter,
)
from tests.shared import getEmptyCol, getUpgradeDeckPath

testDir = os.path.dirname(__file__)


def clear_tempfile(tf):
    """https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file"""
    try:
        tf.close()
        os.unlink(tf.name)
    except Exception:
        pass


def test_anki2_mediadupes():
    col = getEmptyCol()
    # add a note that references a sound
    n = col.newNote()
    n["Front"] = "[sound:foo.mp3]"
    mid = n.note_type()["id"]
    col.addNote(n)
    # add that sound to media folder
    with open(os.path.join(col.media.dir(), "foo.mp3"), "w") as note:
        note.write("foo")
    col.close()
    # it should be imported correctly into an empty deck
    empty = getEmptyCol()
    imp = Anki2Importer(empty, col.path)
    imp.run()
    assert os.listdir(empty.media.dir()) == ["foo.mp3"]
    # and importing again will not duplicate, as the file content matches
    empty.remove_cards_and_orphaned_notes(empty.db.list("select id from cards"))
    imp = Anki2Importer(empty, col.path)
    imp.run()
    assert os.listdir(empty.media.dir()) == ["foo.mp3"]
    n = empty.get_note(empty.db.scalar("select id from notes"))
    assert "foo.mp3" in n.fields[0]
    # if the local file content is different, and import should trigger a
    # rename
    empty.remove_cards_and_orphaned_notes(empty.db.list("select id from cards"))
    with open(os.path.join(empty.media.dir(), "foo.mp3"), "w") as note:
        note.write("bar")
    imp = Anki2Importer(empty, col.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", f"foo_{mid}.mp3"]
    n = empty.get_note(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]
    # if the localized media file already exists, we rewrite the note and
    # media
    empty.remove_cards_and_orphaned_notes(empty.db.list("select id from cards"))
    with open(os.path.join(empty.media.dir(), "foo.mp3"), "w") as note:
        note.write("bar")
    imp = Anki2Importer(empty, col.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", f"foo_{mid}.mp3"]
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", f"foo_{mid}.mp3"]
    n = empty.get_note(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]


def test_apkg():
    col = getEmptyCol()
    apkg = str(os.path.join(testDir, "support", "media.apkg"))
    imp = AnkiPackageImporter(col, apkg)
    assert os.listdir(col.media.dir()) == []
    imp.run()
    assert os.listdir(col.media.dir()) == ["foo.wav"]
    # importing again should be idempotent in terms of media
    col.remove_cards_and_orphaned_notes(col.db.list("select id from cards"))
    imp = AnkiPackageImporter(col, apkg)
    imp.run()
    assert os.listdir(col.media.dir()) == ["foo.wav"]
    # but if the local file has different data, it will rename
    col.remove_cards_and_orphaned_notes(col.db.list("select id from cards"))
    with open(os.path.join(col.media.dir(), "foo.wav"), "w") as note:
        note.write("xyz")
    imp = AnkiPackageImporter(col, apkg)
    imp.run()
    assert len(os.listdir(col.media.dir())) == 2


def test_anki2_diffmodel_templates():
    # different from the above as this one tests only the template text being
    # changed, not the number of cards/fields
    dst = getEmptyCol()
    # import the first version of the model
    col = getUpgradeDeckPath("diffmodeltemplates-1.apkg")
    imp = AnkiPackageImporter(dst, col)
    imp.dupeOnSchemaChange = True  # type: ignore
    imp.run()
    # then the version with updated template
    col = getUpgradeDeckPath("diffmodeltemplates-2.apkg")
    imp = AnkiPackageImporter(dst, col)
    imp.dupeOnSchemaChange = True  # type: ignore
    imp.run()
    # collection should contain the note we imported
    assert dst.note_count() == 1
    # the front template should contain the text added in the 2nd package
    tcid = dst.find_cards("")[0]  # only 1 note in collection
    tnote = dst.getCard(tcid).note()
    assert "Changed Front Template" in tnote.cards()[0].template()["qfmt"]


def test_anki2_updates():
    # create a new empty deck
    dst = getEmptyCol()
    col = getUpgradeDeckPath("update1.apkg")
    imp = AnkiPackageImporter(dst, col)
    imp.run()
    assert imp.dupes == 0
    assert imp.added == 1
    assert imp.updated == 0
    # importing again should be idempotent
    imp = AnkiPackageImporter(dst, col)
    imp.run()
    assert imp.dupes == 1
    assert imp.added == 0
    assert imp.updated == 0
    # importing a newer note should update
    assert dst.note_count() == 1
    assert dst.db.scalar("select flds from notes").startswith("hello")
    col = getUpgradeDeckPath("update2.apkg")
    imp = AnkiPackageImporter(dst, col)
    imp.run()
    assert imp.dupes == 0
    assert imp.added == 0
    assert imp.updated == 1
    assert dst.note_count() == 1
    assert dst.db.scalar("select flds from notes").startswith("goodbye")


def test_csv():
    col = getEmptyCol()
    file = str(os.path.join(testDir, "support", "text-2fields.txt"))
    i = TextImporter(col, file)
    i.initMapping()
    i.run()
    # four problems - too many & too few fields, a missing front, and a
    # duplicate entry
    assert len(i.log) == 5
    assert i.total == 5
    # if we run the import again, it should update instead
    i.run()
    assert len(i.log) == 10
    assert i.total == 5
    # but importing should not clobber tags if they're unmapped
    n = col.get_note(col.db.scalar("select id from notes"))
    n.add_tag("test")
    n.flush()
    i.run()
    n.load()
    assert n.tags == ["test"]
    # if add-only mode, count will be 0
    i.importMode = 1
    i.run()
    assert i.total == 0
    # and if dupes mode, will reimport everything
    assert col.card_count() == 5
    i.importMode = 2
    i.run()
    # includes repeated field
    assert i.total == 6
    assert col.card_count() == 11
    col.close()


def test_csv2():
    col = getEmptyCol()
    mm = col.models
    m = mm.current()
    note = mm.new_field("Three")
    mm.addField(m, note)
    mm.save(m)
    n = col.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Three"] = "3"
    col.addNote(n)
    # an update with unmapped fields should not clobber those fields
    file = str(os.path.join(testDir, "support", "text-update.txt"))
    i = TextImporter(col, file)
    i.initMapping()
    i.run()
    n.load()
    assert n["Front"] == "1"
    assert n["Back"] == "x"
    assert n["Three"] == "3"
    col.close()


def test_tsv_tag_modified():
    col = getEmptyCol()
    mm = col.models
    m = mm.current()
    note = mm.new_field("Top")
    mm.addField(m, note)
    mm.save(m)
    n = col.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Top"] = "3"
    n.add_tag("four")
    col.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1\tb\tc\n")
        tf.flush()
        i = TextImporter(col, tf.name)
        i.initMapping()
        i.tagModified = "boom"
        i.run()
        clear_tempfile(tf)

    n.load()
    assert n["Front"] == "1"
    assert n["Back"] == "b"
    assert n["Top"] == "c"
    assert "four" in n.tags
    assert "boom" in n.tags
    assert len(n.tags) == 2
    assert i.updateCount == 1

    col.close()


def test_tsv_tag_multiple_tags():
    col = getEmptyCol()
    mm = col.models
    m = mm.current()
    note = mm.new_field("Top")
    mm.addField(m, note)
    mm.save(m)
    n = col.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Top"] = "3"
    n.add_tag("four")
    n.add_tag("five")
    col.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1\tb\tc\n")
        tf.flush()
        i = TextImporter(col, tf.name)
        i.initMapping()
        i.tagModified = "five six"
        i.run()
        clear_tempfile(tf)

    n.load()
    assert n["Front"] == "1"
    assert n["Back"] == "b"
    assert n["Top"] == "c"
    assert list(sorted(n.tags)) == list(sorted(["four", "five", "six"]))

    col.close()


def test_csv_tag_only_if_modified():
    col = getEmptyCol()
    mm = col.models
    m = mm.current()
    note = mm.new_field("Left")
    mm.addField(m, note)
    mm.save(m)
    n = col.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Left"] = "3"
    col.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1,2,3\n")
        tf.flush()
        i = TextImporter(col, tf.name)
        i.initMapping()
        i.tagModified = "right"
        i.run()
        clear_tempfile(tf)

    n.load()
    assert n.tags == []
    assert i.updateCount == 0

    col.close()


@pytest.mark.filterwarnings("ignore:Using or importing the ABCs")
def test_supermemo_xml_01_unicode():
    col = getEmptyCol()
    file = str(os.path.join(testDir, "support", "supermemo1.xml"))
    i = SupermemoXmlImporter(col, file)
    # i.META.logToStdOutput = True
    i.run()
    assert i.total == 1
    cid = col.db.scalar("select id from cards")
    c = col.get_card(cid)
    # Applies A Factor-to-E Factor conversion
    assert c.factor == 2879
    assert c.reps == 7
    col.close()


def test_mnemo():
    col = getEmptyCol()
    file = str(os.path.join(testDir, "support", "mnemo.db"))
    i = MnemosyneImporter(col, file)
    i.run()
    assert col.card_count() == 7
    assert "a_longer_tag" in col.tags.all()
    assert col.db.scalar(f"select count() from cards where type = {CARD_TYPE_NEW}") == 1
    col.close()
