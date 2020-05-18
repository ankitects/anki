# coding: utf-8

import os
from tempfile import NamedTemporaryFile

import pytest

from anki.importing import (
    Anki2Importer,
    AnkiPackageImporter,
    MnemosyneImporter,
    SupermemoXmlImporter,
    TextImporter,
)
from tests.shared import getEmptyCol, getUpgradeDeckPath

testDir = os.path.dirname(__file__)

srcNotes = None
srcCards = None


def clear_tempfile(tf):
    """ https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file """
    try:
        tf.close()
        os.unlink(tf.name)
    except:
        pass


def test_anki2_mediadupes():
    tmp = getEmptyCol()
    # add a note that references a sound
    n = tmp.newNote()
    n["Front"] = "[sound:foo.mp3]"
    mid = n.model()["id"]
    tmp.addNote(n)
    # add that sound to media folder
    with open(os.path.join(tmp.media.dir(), "foo.mp3"), "w") as f:
        f.write("foo")
    tmp.close()
    # it should be imported correctly into an empty deck
    empty = getEmptyCol()
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert os.listdir(empty.media.dir()) == ["foo.mp3"]
    # and importing again will not duplicate, as the file content matches
    empty.remCards(empty.db.list("select id from cards"))
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert os.listdir(empty.media.dir()) == ["foo.mp3"]
    n = empty.getNote(empty.db.scalar("select id from notes"))
    assert "foo.mp3" in n.fields[0]
    # if the local file content is different, and import should trigger a
    # rename
    empty.remCards(empty.db.list("select id from cards"))
    with open(os.path.join(empty.media.dir(), "foo.mp3"), "w") as f:
        f.write("bar")
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", "foo_%s.mp3" % mid]
    n = empty.getNote(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]
    # if the localized media file already exists, we rewrite the note and
    # media
    empty.remCards(empty.db.list("select id from cards"))
    with open(os.path.join(empty.media.dir(), "foo.mp3"), "w") as f:
        f.write("bar")
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", "foo_%s.mp3" % mid]
    assert sorted(os.listdir(empty.media.dir())) == ["foo.mp3", "foo_%s.mp3" % mid]
    n = empty.getNote(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]


def test_apkg():
    tmp = getEmptyCol()
    apkg = str(os.path.join(testDir, "support/media.apkg"))
    imp = AnkiPackageImporter(tmp, apkg)
    assert os.listdir(tmp.media.dir()) == []
    imp.run()
    assert os.listdir(tmp.media.dir()) == ["foo.wav"]
    # importing again should be idempotent in terms of media
    tmp.remCards(tmp.db.list("select id from cards"))
    imp = AnkiPackageImporter(tmp, apkg)
    imp.run()
    assert os.listdir(tmp.media.dir()) == ["foo.wav"]
    # but if the local file has different data, it will rename
    tmp.remCards(tmp.db.list("select id from cards"))
    with open(os.path.join(tmp.media.dir(), "foo.wav"), "w") as f:
        f.write("xyz")
    imp = AnkiPackageImporter(tmp, apkg)
    imp.run()
    assert len(os.listdir(tmp.media.dir())) == 2


def test_anki2_diffmodel_templates():
    # different from the above as this one tests only the template text being
    # changed, not the number of cards/fields
    dst = getEmptyCol()
    # import the first version of the model
    tmp = getUpgradeDeckPath("diffmodeltemplates-1.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    # then the version with updated template
    tmp = getUpgradeDeckPath("diffmodeltemplates-2.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    # collection should contain the note we imported
    assert dst.noteCount() == 1
    # the front template should contain the text added in the 2nd package
    tcid = dst.findCards("")[0]  # only 1 note in collection
    tnote = dst.getCard(tcid).note()
    assert "Changed Front Template" in tnote.cards()[0].template()["qfmt"]


def test_anki2_updates():
    # create a new empty deck
    dst = getEmptyCol()
    tmp = getUpgradeDeckPath("update1.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.run()
    assert imp.dupes == 0
    assert imp.added == 1
    assert imp.updated == 0
    # importing again should be idempotent
    imp = AnkiPackageImporter(dst, tmp)
    imp.run()
    assert imp.dupes == 1
    assert imp.added == 0
    assert imp.updated == 0
    # importing a newer note should update
    assert dst.noteCount() == 1
    assert dst.db.scalar("select flds from notes").startswith("hello")
    tmp = getUpgradeDeckPath("update2.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.run()
    assert imp.dupes == 0
    assert imp.added == 0
    assert imp.updated == 1
    assert dst.noteCount() == 1
    assert dst.db.scalar("select flds from notes").startswith("goodbye")


def test_csv():
    deck = getEmptyCol()
    file = str(os.path.join(testDir, "support/text-2fields.txt"))
    i = TextImporter(deck, file)
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
    n = deck.getNote(deck.db.scalar("select id from notes"))
    n.addTag("test")
    n.flush()
    i.run()
    n.load()
    assert n.tags == ["test"]
    # if add-only mode, count will be 0
    i.importMode = 1
    i.run()
    assert i.total == 0
    # and if dupes mode, will reimport everything
    assert deck.cardCount() == 5
    i.importMode = 2
    i.run()
    # includes repeated field
    assert i.total == 6
    assert deck.cardCount() == 11
    deck.close()


def test_csv2():
    deck = getEmptyCol()
    mm = deck.models
    m = mm.current()
    f = mm.newField("Three")
    mm.addField(m, f)
    mm.save(m)
    n = deck.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Three"] = "3"
    deck.addNote(n)
    # an update with unmapped fields should not clobber those fields
    file = str(os.path.join(testDir, "support/text-update.txt"))
    i = TextImporter(deck, file)
    i.initMapping()
    i.run()
    n.load()
    assert n["Front"] == "1"
    assert n["Back"] == "x"
    assert n["Three"] == "3"
    deck.close()


def test_tsv_tag_modified():
    deck = getEmptyCol()
    mm = deck.models
    m = mm.current()
    f = mm.newField("Top")
    mm.addField(m, f)
    mm.save(m)
    n = deck.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Top"] = "3"
    n.addTag("four")
    deck.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1\tb\tc\n")
        tf.flush()
        i = TextImporter(deck, tf.name)
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

    deck.close()


def test_tsv_tag_multiple_tags():
    deck = getEmptyCol()
    mm = deck.models
    m = mm.current()
    f = mm.newField("Top")
    mm.addField(m, f)
    mm.save(m)
    n = deck.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Top"] = "3"
    n.addTag("four")
    n.addTag("five")
    deck.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1\tb\tc\n")
        tf.flush()
        i = TextImporter(deck, tf.name)
        i.initMapping()
        i.tagModified = "five six"
        i.run()
        clear_tempfile(tf)

    n.load()
    assert n["Front"] == "1"
    assert n["Back"] == "b"
    assert n["Top"] == "c"
    assert list(sorted(n.tags)) == list(sorted(["four", "five", "six"]))

    deck.close()


def test_csv_tag_only_if_modified():
    deck = getEmptyCol()
    mm = deck.models
    m = mm.current()
    f = mm.newField("Left")
    mm.addField(m, f)
    mm.save(m)
    n = deck.newNote()
    n["Front"] = "1"
    n["Back"] = "2"
    n["Left"] = "3"
    deck.addNote(n)

    # https://stackoverflow.com/questions/23212435/permission-denied-to-write-to-my-temporary-file
    with NamedTemporaryFile(mode="w", delete=False) as tf:
        tf.write("1,2,3\n")
        tf.flush()
        i = TextImporter(deck, tf.name)
        i.initMapping()
        i.tagModified = "right"
        i.run()
        clear_tempfile(tf)

    n.load()
    assert n.tags == []
    assert i.updateCount == 0

    deck.close()


@pytest.mark.filterwarnings("ignore:Using or importing the ABCs")
def test_supermemo_xml_01_unicode():
    deck = getEmptyCol()
    file = str(os.path.join(testDir, "support/supermemo1.xml"))
    i = SupermemoXmlImporter(deck, file)
    # i.META.logToStdOutput = True
    i.run()
    assert i.total == 1
    cid = deck.db.scalar("select id from cards")
    c = deck.getCard(cid)
    # Applies A Factor-to-E Factor conversion
    assert c.factor == 2879
    assert c.reps == 7
    deck.close()


def test_mnemo():
    deck = getEmptyCol()
    file = str(os.path.join(testDir, "support/mnemo.db"))
    i = MnemosyneImporter(deck, file)
    i.run()
    assert deck.cardCount() == 7
    assert "a_longer_tag" in deck.tags.all()
    assert deck.db.scalar("select count() from cards where type = 0") == 1
    deck.close()
