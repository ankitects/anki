# coding: utf-8

from tests.shared import getEmptyDeck

def test_findCards():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'dog'
    f['Back'] = u'cat'
    f.addTags(u"monkey")
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'goats are fun'
    f['Back'] = u'sheep'
    f.addTags(u"sheep goat horse")
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
    deck.addTags(deck.db.list("select id from cards"), "foo bar")
    assert (len(deck.findCards("tag:foo")) ==
            len(deck.findCards("tag:bar")) ==
            3)
    deck.delTags(deck.db.list("select id from cards"), "foo")
    assert len(deck.findCards("tag:foo")) == 0
    assert len(deck.findCards("tag:bar")) == 3
