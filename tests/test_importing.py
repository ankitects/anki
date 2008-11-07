# coding: utf-8

import nose, os, shutil
from tests.shared import assertException

from anki.errors import *
from anki import DeckStorage
from anki.importing import anki10, csv, mnemosyne10
from anki.stdmodels import BasicModel

from anki.db import *

testDir = os.path.dirname(__file__)

def test_csv():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-2fields.txt"))
    i = csv.TextImporter(deck, file)
    i.doImport()
    # three problems - missing front, missing back, dupe front
    assert len(i.log) == 3
    assert i.total == 4
    deck.s.close()

def test_mnemosyne10():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/test.mem"))
    i = mnemosyne10.Mnemosyne10Importer(deck, file)
    i.doImport()
    assert i.total == 5
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
    # import a deck into itself - 10-2 is the same as test10, but with one
    # card answered and another deleted. nothing should be synced to client
    deck = DeckStorage.Deck(file)
    i = anki10.Anki10Importer(deck, file2)
    i.doImport()
    assert i.total == 0
    deck.s.rollback()
