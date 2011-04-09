# coding: utf-8

from tests.shared import getEmptyDeck

def test_findCards():
    deck = getEmptyDeck()
    f = deck.newFact()
    f['Front'] = u'dog'
    f['Back'] = u'cat'
    f.tags.append(u"monkey")
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'goats are fun'
    f['Back'] = u'sheep'
    f.tags.append(u"sheep goat horse")
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'cat'
    f['Back'] = u'sheep'
    deck.addFact(f)
    f = deck.newFact()
    f['Front'] = u'template test'
    f['Back'] = u'foo bar'
    f.model().templates[1]['actv'] = True
    deck.addFact(f)
    # tag searches
    assert not deck.findCards("tag:donkey")
    assert len(deck.findCards("tag:sheep")) == 1
    assert len(deck.findCards("tag:sheep tag:goat")) == 1
    assert len(deck.findCards("tag:sheep tag:monkey")) == 0
    assert len(deck.findCards("tag:monkey")) == 1
    assert len(deck.findCards("tag:sheep -tag:monkey")) == 1
    assert len(deck.findCards("-tag:sheep")) == 4
    deck.addTags(deck.db.list("select id from cards"), "foo bar")
    assert (len(deck.findCards("tag:foo")) ==
            len(deck.findCards("tag:bar")) ==
            5)
    deck.delTags(deck.db.list("select id from cards"), "foo")
    assert len(deck.findCards("tag:foo")) == 0
    assert len(deck.findCards("tag:bar")) == 5
    # text searches
    assert len(deck.findCards("cat")) == 2
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("cat -dog")) == 1
    assert len(deck.findCards("are goats")) == 1
    assert len(deck.findCards('"are goats"')) == 0
    assert len(deck.findCards('"goats are"')) == 1
    # card states
    c = f.cards()[0]
    c.type = 2
    assert deck.findCards("is:rev") == []
    c.flush()
    assert deck.findCards("is:rev") == [c.id]
    assert deck.findCards("is:due") == []
    c.due = 0; c.queue = 2
    c.flush()
    assert deck.findCards("is:due") == [c.id]
    c.queue = -1
    c.flush()
    assert deck.findCards("is:suspended") == [c.id]
    # fids
    assert deck.findCards("fid:54321") == []
    assert len(deck.findCards("fid:%d"%f.id)) == 2
    assert len(deck.findCards("fid:3,2")) == 2
    # templates
    assert len(deck.findCards("card:foo")) == 0
    assert len(deck.findCards("card:forward")) == 4
