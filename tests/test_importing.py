# coding: utf-8

import nose, os, shutil
from tests.shared import assertException

from anki.errors import *
from anki import DeckStorage
from anki.importing import anki10, csvfile, mnemosyne10, supermemo_xml, dingsbums
from anki.stdmodels import BasicModel
from anki.facts import Fact
from anki.sync import SyncClient, SyncServer

from anki.db import *

testDir = os.path.dirname(__file__)

def test_csv():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-2fields.txt"))
    i = csvfile.TextImporter(deck, file)
    i.doImport()
    # four problems - missing front, dupe front, wrong num of fields
    assert len(i.log) == 4
    assert i.total == 5
    deck.s.close()

def test_csv_tags():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-tags.txt"))
    i = csvfile.TextImporter(deck, file)
    i.doImport()
    facts = deck.s.query(Fact).all()
    assert len(facts) == 2
    assert facts[0].tags == "baz qux" or facts[1].tags == "baz qux"
    deck.s.close()

def test_mnemosyne10():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/test.mem"))
    i = mnemosyne10.Mnemosyne10Importer(deck, file)
    i.doImport()
    assert i.total == 5
    deck.s.close()

def test_supermemo_xml_01_unicode():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/supermemo1.xml"))
    i = supermemo_xml.SupermemoXmlImporter(deck, file)
    #i.META.logToStdOutput = True
    i.doImport()
    # only returning top-level elements?
    assert i.total == 1
    deck.s.close()

def test_anki10():
    # though these are not modified, sqlite updates the mtime, so copy to tmp
    # first
    file_ = unicode(os.path.join(testDir, "importing/test10.anki"))
    file = "/tmp/test10.anki"
    shutil.copy(file_, file)
    file2_ = unicode(os.path.join(testDir, "importing/test10-2.anki"))
    file2 = "/tmp/test10-2.anki"
    shutil.copy(file2_, file2)
    deck = DeckStorage.Deck()
    i = anki10.Anki10Importer(deck, file)
    i.doImport()
    assert i.total == 2
    deck.s.rollback()
    deck.close()
    # import a deck into itself - 10-2 is the same as test10, but with one
    # card answered and another deleted. nothing should be synced to client
    deck = DeckStorage.Deck(file, backup=False)
    i = anki10.Anki10Importer(deck, file2)
    i.doImport()
    assert i.total == 0
    deck.s.rollback()

def test_anki10_modtime():
    deck1 = DeckStorage.Deck()
    deck2 = DeckStorage.Deck()
    client = SyncClient(deck1)
    server = SyncServer(deck2)
    client.setServer(server)
    deck1.addModel(BasicModel())
    f = deck1.newFact()
    f['Front'] = u"foo"; f['Back'] = u"bar"
    deck1.addFact(f)
    assert deck1.cardCount == 1
    assert deck2.cardCount == 0
    client.sync()
    assert deck1.cardCount == 1
    assert deck2.cardCount == 1
    file_ = unicode(os.path.join(testDir, "importing/test10-3.anki"))
    file = "/tmp/test10-3.anki"
    shutil.copy(file_, file)
    i = anki10.Anki10Importer(deck1, file)
    i.doImport()
    client.sync()
    assert i.total == 1
    assert deck2.s.scalar("select count(*) from cards") == 2
    assert deck2.s.scalar("select count(*) from facts") == 2
    assert deck2.s.scalar("select count(*) from models") == 2

def test_dingsbums():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    startNumberOfFacts = deck.factCount
    file = unicode(os.path.join(testDir, "importing/dingsbums.xml"))
    i = dingsbums.DingsBumsImporter(deck, file)
    i.doImport()
    assert 7 == i.total
    deck.s.close()

def test_updating():
    # get the standard csv deck first
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-2fields.txt"))
    i = csvfile.TextImporter(deck, file)
    i.doImport()
    # now update
    file = unicode(os.path.join(testDir, "importing/text-update.txt"))
    i = csvfile.TextImporter(deck, file)
    # first field
    i.updateKey = (0, deck.currentModel.fieldModels[0].id)
    i.multipleCardsAllowed = False
    i.doImport()
    ans = deck.s.scalar(
        u"select answer from cards where question like '%食べる%'")
    assert "to ate" in ans
    # try again with tags
    i.updateKey = (0, deck.currentModel.fieldModels[0].id)
    i.mapping[1] = 0
    i.doImport()
    deck.s.close()
