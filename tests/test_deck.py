# coding: utf-8

import nose, os, re
from tests.shared import assertException, getDeck

from anki.errors import *
from anki import Deck
from anki.db import *
from anki.models import FieldModel, Model, CardModel
from anki.stdmodels import BasicModel
from anki.utils import stripHTML

newPath = None
newModified = None

testDir = os.path.dirname(__file__)

## opening/closing

def test_attachNew():
    global newPath, newModified
    path = "/tmp/test_attachNew.anki"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck = Deck(path)
    # for attachOld()
    newPath = deck.path
    deck.save()
    newModified = deck.modified
    deck.close()
    del deck

def test_attachOld():
    deck = Deck(newPath, backup=False)
    assert deck.modified == newModified
    deck.close()

def test_attachReadOnly():
    # non-writeable dir
    assertException(Exception,
                    lambda: Deck("/attachroot"))
    # reuse tmp file from before, test non-writeable file
    os.chmod(newPath, 0)
    assertException(Exception,
                    lambda: Deck(newPath))
    os.chmod(newPath, 0666)
    os.unlink(newPath)

def test_saveAs():
    path = "/tmp/test_saveAs.anki"
    try:
        os.unlink(path)
    except OSError:
        pass
    path2 = "/tmp/test_saveAs2.anki"
    try:
        os.unlink(path2)
    except OSError:
        pass
    # start with an in-memory deck
    deck = getDeck()
    deck.addModel(BasicModel())
    # add a card
    f = deck.newFact()
    f['Front'] = u"foo"; f['Back'] = u"bar"
    deck.addFact(f)
    assert deck.cardCount() == 1
    # save in new deck
    newDeck = deck.saveAs(path)
    assert newDeck.cardCount() == 1
    # delete card
    id = newDeck.db.scalar("select id from cards")
    newDeck.deleteCard(id)
    # save into new deck
    newDeck2 = newDeck.saveAs(path2)
    # new deck should have zero cards
    assert newDeck2.cardCount() == 0
    # but old deck should have reverted the unsaved changes
    newDeck = Deck(path)
    assert newDeck.cardCount() == 1
    newDeck.close()

def test_factAddDelete():
    deck = getDeck()
    deck.addModel(BasicModel())
    # set rollback point
    deck.db.commit()
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

def test_fieldChecksum():
    deck = getDeck()
    deck.addModel(BasicModel())
    f = deck.newFact()
    f['Front'] = u"new"; f['Back'] = u"new2"
    deck.addFact(f)
    (id, sum) = deck.db.first(
        "select id, chksum from fields where value = 'new'")
    assert sum == "22af645d"
    # empty field should have no checksum
    f['Front'] = u""
    deck.db.flush()
    assert deck.db.scalar(
        "select chksum from fields where id = :id", id=id) == ""
    # changing the value should change the checksum
    f['Front'] = u"newx"
    deck.db.flush()
    assert deck.db.scalar(
        "select chksum from fields where id = :id", id=id) == "4b0e5a4c"
    # back should have no checksum, because it's not set to be unique
    (id, sum) = deck.db.first(
        "select id, chksum from fields where value = 'new2'")
    assert sum == ""
    # if we turn on unique, it should get a checksum
    fm = f.model.fieldModels[1]
    fm.unique = True
    deck.updateFieldChecksums(fm.id)
    assert deck.db.scalar(
        "select chksum from fields where id = :id", id=id) == "82f2ec5f"
    # and turning it off should zero the checksum again
    fm.unique = False
    deck.updateFieldChecksums(fm.id)
    assert deck.db.scalar(
        "select chksum from fields where id = :id", id=id) == ""

def test_modelAddDelete():
    deck = getDeck()
    deck.addModel(BasicModel())
    deck.addModel(BasicModel())
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    assert deck.cardCount() == 1
    deck.deleteModel(deck.currentModel)
    deck.reset()
    assert deck.cardCount() == 0
    deck.db.refresh(deck)

def test_modelCopy():
    deck = getDeck()
    m = BasicModel()
    assert len(m.fieldModels) == 2
    assert len(m.cardModels) == 2
    deck.addModel(m)
    f = deck.newFact()
    f['Front'] = u'1'
    deck.addFact(f)
    m2 = deck.copyModel(m)
    assert m2.name == "Basic copy"
    assert m2.id != m.id
    assert m2.fieldModels[0].id != m.fieldModels[0].id
    assert m2.cardModels[0].id != m.cardModels[0].id
    assert len(m2.fieldModels) == 2
    assert len(m.fieldModels) == 2
    assert len(m2.fieldModels) == len(m.fieldModels)
    assert len(m.cardModels) == 2
    assert len(m2.cardModels) == 2

def test_media():
    deck = getDeck()
    # create a media dir
    deck.mediaDir(create=True)
    # put a file into it
    file = unicode(os.path.join(testDir, "deck/fake.png"))
    deck.addMedia(file)
    # make sure it gets copied on saveas
    path = "/tmp/saveAs2.anki"
    sum = "fake.png"
    try:
        os.unlink(path)
    except OSError:
        pass
    deck.saveAs(path)
    assert os.path.exists("/tmp/saveAs2.media/%s" % sum)

def test_modelChange():
    deck = getDeck()
    m = Model(u"Japanese")
    m1 = m
    f = FieldModel(u'Expression', True, True)
    m.addFieldModel(f)
    m.addFieldModel(FieldModel(u'Meaning', False, False))
    f = FieldModel(u'Reading', False, False)
    m.addFieldModel(f)
    m.addCardModel(CardModel(u"Recognition",
                             u"%(Expression)s",
                             u"%(Reading)s<br>%(Meaning)s"))
    m.addCardModel(CardModel(u"Recall",
                             u"%(Meaning)s",
                             u"%(Expression)s<br>%(Reading)s",
                             active=False))
    m.tags = u"Japanese"
    m1.cardModels[1].active = True
    deck.addModel(m1)
    f = deck.newFact()
    f['Expression'] = u'e'
    f['Meaning'] = u'm'
    f['Reading'] = u'r'
    f = deck.addFact(f)
    f2 = deck.newFact()
    f2['Expression'] = u'e2'
    f2['Meaning'] = u'm2'
    f2['Reading'] = u'r2'
    deck.addFact(f2)
    m2 = BasicModel()
    m2.cardModels[1].active = True
    deck.addModel(m2)
    # convert to basic
    assert deck.modelUseCount(m1) == 2
    assert deck.modelUseCount(m2) == 0
    assert deck.cardCount() == 4
    assert deck.factCount() == 2
    fmap = {m1.fieldModels[0]: m2.fieldModels[0],
            m1.fieldModels[1]: None,
            m1.fieldModels[2]: m2.fieldModels[1]}
    cmap = {m1.cardModels[0]: m2.cardModels[0],
            m1.cardModels[1]: None}
    deck.changeModel([f.id], m2, fmap, cmap)
    deck.reset()
    assert deck.modelUseCount(m1) == 1
    assert deck.modelUseCount(m2) == 1
    assert deck.cardCount() == 3
    assert deck.factCount() == 2
    (q, a) = deck.db.first("""
select question, answer from cards where factId = :id""",
                          id=f.id)
    assert stripHTML(q) == u"e"
    assert stripHTML(a) == u"r"

def test_findCards():
    deck = getDeck()
    deck.addModel(BasicModel())
    f = deck.newFact()
    f['Front'] = u'dog'
    f['Back'] = u'cat'
    f.tags = u"monkey"
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'goats are fun'
    f['Back'] = u'sheep'
    f.tags = u"sheep goat horse"
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'cat'
    f['Back'] = u'sheep'
    deck.addFact(f)
    assert not deck.findCards("tag:donkey")
    assert len(deck.findCards("tag:sheep")) == 1
    assert len(deck.findCards("tag:sheep tag:goat")) == 1
    assert len(deck.findCards("tag:sheep tag:monkey")) == 0
    assert len(deck.findCards("tag:monkey")) == 1
    assert len(deck.findCards("tag:sheep -tag:monkey")) == 1
    assert len(deck.findCards("-tag:sheep")) == 2
    assert len(deck.findCards("cat")) == 2
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("are goats")) == 1
    assert len(deck.findCards('"are goats"')) == 0
    assert len(deck.findCards('"goats are"')) == 1
    # make sure card templates and models match too
    assert len(deck.findCards('tag:basic')) == 3
    assert len(deck.findCards('tag:forward')) == 3
    deck.addModel(BasicModel())
    f = deck.newFact()
    f['Front'] = u'foo'
    f['Back'] = u'bar'
    deck.addFact(f)
    deck.currentModel.cardModels[1].active = True
    f = deck.newFact()
    f['Front'] = u'baz'
    f['Back'] = u'qux'
    c = deck.addFact(f)
    assert len(deck.findCards('tag:forward')) == 5
    assert len(deck.findCards('tag:reverse')) == 1
