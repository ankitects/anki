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
    print dst.path

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

def test_csv_tags():
    print "disabled"; return
    deck = getEmptyDeck()
    file = unicode(os.path.join(testDir, "support/text-tags.txt"))
    i = TextImporter(deck, file)
    i.run()
    notes = deck.db.query(Note).all()
    assert len(notes) == 2
    assert notes[0].tags == "baz qux" or notes[1].tags == "baz qux"
    deck.close()

def test_supermemo_xml_01_unicode():
    print "disabled"; return
    deck = Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/supermemo1.xml"))
    i = supermemo_xml.SupermemoXmlImporter(deck, file)
    #i.META.logToStdOutput = True
    i.run()
    # only returning top-level elements?
    assert i.total == 1
    deck.close()

def test_updating():
    print "disabled"; return
    # get the standard csv deck first
    deck = Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-2fields.txt"))
    i = csvfile.TextImporter(deck, file)
    i.run()
    # now update
    file = unicode(os.path.join(testDir, "importing/text-update.txt"))
    i = csvfile.TextImporter(deck, file)
    # first field
    i.updateKey = (0, deck.currentModel.fieldModels[0].id)
    i.multipleCardsAllowed = False
    i.run()
    ans = deck.db.scalar(
        u"select answer from cards where question like '%食べる%'")
    assert "to ate" in ans
    # try again with tags
    i.updateKey = (0, deck.currentModel.fieldModels[0].id)
    i.mapping[1] = 0
    i.run()
    deck.close()

def test_mnemo():
    deck = getEmptyDeck()
    file = unicode(os.path.join(testDir, "support/mnemo.db"))
    i = MnemosyneImporter(deck, file)
    i.run()
    assert deck.cardCount() == 7
    assert "a_longer_tag" in deck.tags.all()
    assert deck.db.scalar("select count() from cards where type = 0") == 1
