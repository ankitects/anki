# coding: utf-8

import time
from anki.db import DB
from anki.consts import *
from anki.utils import hexifyID
from tests.shared import getEmptyDeck

def test_genCards():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    deck.genCards([f.id], f.model()['tmpls'])
    assert deck.cardCount() == 2

def test_previewCards():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    # non-empty and active
    cards = deck.previewCards(f, 0)
    assert len(cards) == 1
    assert cards[0].ord == 0
    # all templates
    cards = deck.previewCards(f, 2)
    assert len(cards) == 2
    # add the fact, and test existing preview
    deck.addFact(f)
    cards = deck.previewCards(f, 1)
    assert len(cards) == 1
    assert cards[0].ord == 0
    # make sure we haven't accidentally added cards to the db
    assert deck.cardCount() == 1

def test_delete():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    deck.addFact(f)
    cid = f.cards()[0].id
    deck.reset()
    deck.sched.answerCard(deck.sched.getCard(), 2)
    assert deck.db.scalar("select count() from revlog") == 1
    deck.remCards([cid])
    assert deck.cardCount() == 0
    assert deck.factCount() == 0
    assert deck.db.scalar("select count() from facts") == 0
    assert deck.db.scalar("select count() from cards") == 0
    assert deck.db.scalar("select count() from fsums") == 0
    assert deck.db.scalar("select count() from revlog") == 0
    assert deck.db.scalar("select count() from graves") == 2

def test_misc():
    d = getEmptyDeck()
    f = d.newFact()
    f['Front'] = u'1'
    f['Back'] = u'2'
    d.addFact(f)
    c = f.cards()[0]
    id = d.models.current()['id']
    assert c.cssClass() == "cm%s-0" % hexifyID(id)
    assert c.template()['ord'] == 0
