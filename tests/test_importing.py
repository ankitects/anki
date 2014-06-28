# coding: utf-8

import  os
from tests.shared import  getUpgradeDeckPath, getEmptyCol
from anki.upgrade import Upgrader
from anki.utils import ids2str
from anki.importing import Anki1Importer, Anki2Importer, TextImporter, \
    SupermemoXmlImporter, MnemosyneImporter, AnkiPackageImporter

testDir = os.path.dirname(__file__)

srcNotes=None
srcCards=None

def test_anki2():
    global srcNotes, srcCards
    # get the deck to import
    tmp = getUpgradeDeckPath()
    u = Upgrader()
    u.check(tmp)
    src = u.upgrade()
    srcpath = src.path
    srcNotes = src.noteCount()
    srcCards = src.cardCount()
    srcRev = src.db.scalar("select count() from revlog")
    # add a media file for testing
    open(os.path.join(src.media.dir(), "_foo.jpg"), "w").write("foo")
    src.close()
    # create a new empty deck
    dst = getEmptyCol()
    # import src into dst
    imp = Anki2Importer(dst, srcpath)
    imp.run()
    def check():
        assert dst.noteCount() == srcNotes
        assert dst.cardCount() == srcCards
        assert srcRev == dst.db.scalar("select count() from revlog")
        mids = [int(x) for x in dst.models.models.keys()]
        assert not dst.db.scalar(
            "select count() from notes where mid not in "+ids2str(mids))
        assert not dst.db.scalar(
            "select count() from cards where nid not in (select id from notes)")
        assert not dst.db.scalar(
            "select count() from revlog where cid not in (select id from cards)")
        assert dst.fixIntegrity()[0].startswith("Database rebuilt")
    check()
    # importing should be idempotent
    imp.run()
    check()
    assert len(os.listdir(dst.media.dir())) == 1

def test_anki2_mediadupes():
    tmp = getEmptyCol()
    # add a note that references a sound
    n = tmp.newNote()
    n['Front'] = "[sound:foo.mp3]"
    mid = n.model()['id']
    tmp.addNote(n)
    # add that sound to media folder
    open(os.path.join(tmp.media.dir(), "foo.mp3"), "w").write("foo")
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
    open(os.path.join(empty.media.dir(), "foo.mp3"), "w").write("bar")
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == [
        "foo.mp3", "foo_%s.mp3" % mid]
    n = empty.getNote(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]
    # if the localized media file already exists, we rewrite the note and
    # media
    empty.remCards(empty.db.list("select id from cards"))
    open(os.path.join(empty.media.dir(), "foo.mp3"), "w").write("bar")
    imp = Anki2Importer(empty, tmp.path)
    imp.run()
    assert sorted(os.listdir(empty.media.dir())) == [
        "foo.mp3", "foo_%s.mp3" % mid]
    assert sorted(os.listdir(empty.media.dir())) == [
        "foo.mp3", "foo_%s.mp3" % mid]
    n = empty.getNote(empty.db.scalar("select id from notes"))
    assert "_" in n.fields[0]

def test_apkg():
    tmp = getEmptyCol()
    apkg = unicode(os.path.join(testDir, "support/media.apkg"))
    imp = AnkiPackageImporter(tmp, apkg)
    assert os.listdir(tmp.media.dir()) == []
    imp.run()
    assert os.listdir(tmp.media.dir()) == ['foo.wav']
    # importing again should be idempotent in terms of media
    tmp.remCards(tmp.db.list("select id from cards"))
    imp = AnkiPackageImporter(tmp, apkg)
    imp.run()
    assert os.listdir(tmp.media.dir()) == ['foo.wav']
    # but if the local file has different data, it will rename
    tmp.remCards(tmp.db.list("select id from cards"))
    open(os.path.join(tmp.media.dir(), "foo.wav"), "w").write("xyz")
    imp = AnkiPackageImporter(tmp, apkg)
    imp.run()
    assert len(os.listdir(tmp.media.dir())) == 2

def test_anki1():
    # get the deck path to import
    tmp = getUpgradeDeckPath()
    # make sure media is imported properly through the upgrade
    mdir = tmp.replace(".anki2", ".media")
    if not os.path.exists(mdir):
        os.mkdir(mdir)
    open(os.path.join(mdir, "_foo.jpg"), "w").write("foo")
    # create a new empty deck
    dst = getEmptyCol()
    # import src into dst
    imp = Anki1Importer(dst, tmp)
    imp.run()
    def check():
        assert dst.noteCount() == srcNotes
        assert dst.cardCount() == srcCards
        assert len(os.listdir(dst.media.dir())) == 1
    check()
    # importing should be idempotent
    imp = Anki1Importer(dst, tmp)
    imp.run()
    check()

def test_anki1_diffmodels():
    # create a new empty deck
    dst = getEmptyCol()
    # import the 1 card version of the model
    tmp = getUpgradeDeckPath("diffmodels1.anki")
    imp = Anki1Importer(dst, tmp)
    imp.run()
    before = dst.noteCount()
    # repeating the process should do nothing
    imp = Anki1Importer(dst, tmp)
    imp.run()
    assert before == dst.noteCount()
    # then the 2 card version
    tmp = getUpgradeDeckPath("diffmodels2.anki")
    imp = Anki1Importer(dst, tmp)
    imp.run()
    after = dst.noteCount()
    # as the model schemas differ, should have been imported as new model
    assert after == before + 1
    # repeating the process should do nothing
    beforeModels = len(dst.models.all())
    imp = Anki1Importer(dst, tmp)
    imp.run()
    after = dst.noteCount()
    assert after == before + 1
    assert beforeModels == len(dst.models.all())

def test_suspended():
    # create a new empty deck
    dst = getEmptyCol()
    # import the 1 card version of the model
    tmp = getUpgradeDeckPath("suspended12.anki")
    imp = Anki1Importer(dst, tmp)
    imp.run()
    assert dst.db.scalar("select due from cards") < 0

def test_anki2_diffmodels():
    # create a new empty deck
    dst = getEmptyCol()
    # import the 1 card version of the model
    tmp = getUpgradeDeckPath("diffmodels2-1.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    before = dst.noteCount()
    # repeating the process should do nothing
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    assert before == dst.noteCount()
    # then the 2 card version
    tmp = getUpgradeDeckPath("diffmodels2-2.apkg")
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    after = dst.noteCount()
    # as the model schemas differ, should have been imported as new model
    assert after == before + 1
    # and the new model should have both cards
    assert dst.cardCount() == 3
    # repeating the process should do nothing
    imp = AnkiPackageImporter(dst, tmp)
    imp.dupeOnSchemaChange = True
    imp.run()
    after = dst.noteCount()
    assert after == before + 1
    assert dst.cardCount() == 3

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
    assert(dst.noteCount() == 1)
    # the front template should contain the text added in the 2nd package
    tcid = dst.findCards("")[0] # only 1 note in collection
    tnote = dst.getCard(tcid).note()
    assert("Changed Front Template" in dst.findTemplates(tnote)[0]['qfmt'])

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
    assert imp.dupes == 1
    assert imp.added == 0
    assert imp.updated == 1
    assert dst.noteCount() == 1
    assert dst.db.scalar("select flds from notes").startswith("goodbye")

def test_csv():
    deck = getEmptyCol()
    file = unicode(os.path.join(testDir, "support/text-2fields.txt"))
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
    assert n.tags == ['test']
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
    n['Front'] = "1"
    n['Back'] = "2"
    n['Three'] = "3"
    deck.addNote(n)
    # an update with unmapped fields should not clobber those fields
    file = unicode(os.path.join(testDir, "support/text-update.txt"))
    i = TextImporter(deck, file)
    i.initMapping()
    i.run()
    n.load()
    assert n['Front'] == "1"
    assert n['Back'] == "x"
    assert n['Three'] == "3"
    deck.close()

def test_supermemo_xml_01_unicode():
    deck = getEmptyCol()
    file = unicode(os.path.join(testDir, "support/supermemo1.xml"))
    i = SupermemoXmlImporter(deck, file)
    #i.META.logToStdOutput = True
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
    file = unicode(os.path.join(testDir, "support/mnemo.db"))
    i = MnemosyneImporter(deck, file)
    i.run()
    assert deck.cardCount() == 7
    assert "a_longer_tag" in deck.tags.all()
    assert deck.db.scalar("select count() from cards where type = 0") == 1
    deck.close()
