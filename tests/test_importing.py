# coding: utf-8

import nose, os, shutil
from tests.shared import assertException

from anki.errors import *
from anki import Deck
from anki.importing import Anki1Importer, Anki2Importer, TextImporter, \
    SupermemoXmlImporter
from anki.facts import Fact

from anki.db import *

testDir = os.path.dirname(__file__)

def test_csv():
    print "disabled"; return
    deck = Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-2fields.txt"))
    i = csvfile.TextImporter(deck, file)
    i.run()
    # four problems - missing front, dupe front, wrong num of fields
    assert len(i.log) == 4
    assert i.total == 5
    deck.close()

def test_csv_tags():
    print "disabled"; return
    deck = Deck()
    deck.addModel(BasicModel())
    file = unicode(os.path.join(testDir, "importing/text-tags.txt"))
    i = csvfile.TextImporter(deck, file)
    i.run()
    facts = deck.db.query(Fact).all()
    assert len(facts) == 2
    assert facts[0].tags == "baz qux" or facts[1].tags == "baz qux"
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
