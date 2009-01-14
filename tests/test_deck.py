# coding: utf-8

import nose, os, re
from tests.shared import assertException

from anki.errors import *
from anki import DeckStorage
from anki.db import *
from anki.models import FieldModel
from anki.stdmodels import JapaneseModel, BasicModel

newPath = None
newModified = None

testDir = os.path.dirname(__file__)

## opening/closing

def test_new():
    deck = DeckStorage.Deck()
    assert not deck.path
    assert deck.engine
    assert deck.modified

def test_attachNew():
    global newPath, newModified
    path = "/tmp/test_attachNew"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck = DeckStorage.Deck(path)
    # for attachOld()
    newPath = deck.path
    deck.save()
    newModified = deck.modified
    deck.close()
    del deck

def test_attachOld():
    deck = DeckStorage.Deck(newPath)
    assert deck.modified == newModified
    deck.close()

def test_attachReadOnly():
    # non-writeable dir
    assertException(Exception,
                    lambda: DeckStorage.Deck("/attachroot"))
    # reuse tmp file from before, test non-writeable file
    os.chmod(newPath, 0)
    assertException(Exception,
                    lambda: DeckStorage.Deck(newPath))
    os.chmod(newPath, 0666)
    os.unlink(newPath)

def test_saveAs():
    path = "/tmp/test_saveAs"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    # add a card
    f = deck.newFact()
    f['Front'] = u"foo"; f['Back'] = u"bar"
    deck.addFact(f)
    # save in new deck
    newDeck = deck.saveAs(path)
    assert newDeck.cardCount == 1
    newDeck.close()
    deck.close()

def test_factAddDelete():
    deck = DeckStorage.Deck()
    deck.addModel(BasicModel())
    # set rollback point
    deck.s.commit()
    f = deck.newFact()
    # empty fields
    try:
        deck.addFact(f)
    except Exception, e:
        pass
    assert e.data['type'] == 'fieldEmpty'
    # add a fact
    f['Front'] = u"one"; f['Back'] = u"two"
    f = deck.addFact(f)
    assert len(f.cards) == 1
    deck.rollback()
    # try with two cards
    f = deck.newFact()
    f['Front'] = u"one"; f['Back'] = u"two"
    f.model.cardModels[1].active = True
    f = deck.addFact(f)
    assert len(f.cards) == 2
    # ensure correct order
    c0 = [c for c in f.cards if c.ordinal == 0][0]
    assert re.sub("</?.+?>", "", c0.question) == u"one"
    # now let's make a duplicate
    f2 = deck.newFact()
    f2['Front'] = u"one"; f2['Back'] = u"three"
    try:
        f2 = deck.addFact(f2)
    except Exception, e:
        pass
    assert e.data['type'] == 'fieldNotUnique'
    # try delete the first card
    id1 = f.cards[0].id; id2 = f.cards[1].id
    deck.deleteCard(id1)
    # and the second should clear the fact
    deck.deleteCard(id2)

def test_cardOrder():
    deck = DeckStorage.Deck()
    deck.addModel(JapaneseModel())
    f = deck.newFact()
    f['Expression'] = u'1'
    f['Meaning'] = u'2'
    deck.addFact(f)
    card = deck.getCard()
    # recognition should come first
    assert card.cardModel.name == u"Recognition"

def test_modelAddDelete():
    deck = DeckStorage.Deck()
    deck.addModel(JapaneseModel())
    deck.addModel(JapaneseModel())
    f = deck.newFact()
    f['Expression'] = u'1'
    f['Meaning'] = u'2'
    deck.addFact(f)
    assert deck.cardCount == 1
    deck.deleteModel(deck.currentModel)
    assert deck.cardCount == 0
    deck.s.refresh(deck)

def test_modelCopy():
    deck = DeckStorage.Deck()
    m = JapaneseModel()
    deck.addModel(m)
    f = deck.newFact()
    f['Expression'] = u'1'
    deck.addFact(f)
    m2 = deck.copyModel(m)
    assert m2.name == "Japanese copy"
    assert m2.id != m.id
    assert m2.fieldModels[0].id != m.fieldModels[0].id
    assert m2.cardModels[0].id != m.cardModels[0].id

def test_media():
    deck = DeckStorage.Deck()
    # create a media dir
    deck.mediaDir(create=True)
    # put a file into it
    file = unicode(os.path.join(testDir, "deck/fake.png"))
    deck.addMedia(file)
    # make sure it gets copied on saveas
    path = "/tmp/saveAs2.anki"
    sum = "0bee89b07a248e27c83fc3d5951213c1.png"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck.saveAs(path)
    assert os.path.exists("/tmp/saveAs2.media/%s" % sum)
