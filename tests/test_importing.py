# coding: utf-8

import nose, os, shutil
from tests.shared import assertException, getUpgradeDeckPath, getEmptyDeck
from anki.upgrade import Upgrader
from anki.utils import ids2str
from anki.errors import *
from anki.importing import Anki1Importer, Anki2Importer, TextImporter, \
    SupermemoXmlImporter, MnemosyneImporter
from anki.notes import Note

from anki.db import *

testDir = os.path.dirname(__file__)

srcNotes=None
srcCards=None

def test_anki2():
    global srcNotes, srcCards
    # get the deck to import
    tmp = getUpgradeDeckPath()
    u = Upgrader()
    src = u.upgrade(tmp)
    srcpath = src.path
    srcNotes = src.noteCount()
    srcCards = src.cardCount()
    srcRev = src.db.scalar("select count() from revlog")
    # add a media file for testing
    open(os.path.join(src.media.dir(), "foo.jpg"), "w").write("foo")
    src.close()
    # create a new empty deck
    dst = getEmptyDeck()
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
        assert dst.fixIntegrity().startswith("Database rebuilt")
    check()
    # importing should be idempotent
    imp.run()
    check()
    assert len(os.listdir(dst.media.dir())) == 1
    #print dst.path

def test_anki1():
    # get the deck path to import
    tmp = getUpgradeDeckPath()
    # make sure media is imported properly through the upgrade
    mdir = tmp.replace(".anki", ".media")
    if not os.path.exists(mdir):
        os.mkdir(mdir)
    open(os.path.join(mdir, "foo.jpg"), "w").write("foo")
    # create a new empty deck
    dst = getEmptyDeck()
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

def test_csv():
    deck = getEmptyDeck()
    file = unicode(os.path.join(testDir, "support/text-2fields.txt"))
    i = TextImporter(deck, file)
    i.initMapping()
    i.run()
    # four problems - too many & too few fields, a missing front, and a
    # duplicate entry
    assert len(i.log) == 4
    assert i.total == 5
    # if we run the import again, it should update instead
    i.run()
    assert len(i.log) == 4
    assert i.total == 5
    # if updating is disabled, count will be 0
    i.update = False
    i.run()
    assert i.total == 0
    deck.close()

def test_csv2():
    deck = getEmptyDeck()
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
    deck = getEmptyDeck()
    file = unicode(os.path.join(testDir, "support/supermemo1.xml"))
    i = SupermemoXmlImporter(deck, file)
    #i.META.logToStdOutput = True
    i.run()
    assert i.total == 1
    cid = deck.db.scalar("select id from cards")
    c = deck.getCard(cid)
    assert c.factor == 5701
    assert c.reps == 7
    deck.close()

def test_mnemo():
    deck = getEmptyDeck()
    file = unicode(os.path.join(testDir, "support/mnemo.db"))
    i = MnemosyneImporter(deck, file)
    i.run()
    assert deck.cardCount() == 7
    assert "a_longer_tag" in deck.tags.all()
    assert deck.db.scalar("select count() from cards where type = 0") == 1
    deck.close()
