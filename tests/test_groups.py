# coding: utf-8

from tests.shared import assertException, getEmptyDeck, testDir

def test_basic():
    deck = getEmptyDeck()
    # we start with a standard group
    assert len(deck.groups.groups) == 1
    # it should have an id of 1
    assert deck.groups.name(1)
    # create a new group
    parentId = deck.groups.id("new group")
    assert parentId
    assert len(deck.groups.groups) == 2
    # should get the same id
    assert deck.groups.id("new group") == parentId
    # we start with the default group selected
    assert deck.groups.selected() == 1
    assert deck.groups.active() == [1]
    assert deck.groups.top()['id'] == 1
    # we can select a different group
    deck.groups.select(parentId)
    assert deck.groups.selected() == parentId
    assert deck.groups.active() == [parentId]
    assert deck.groups.top()['id'] == parentId
    # let's create a child
    childId = deck.groups.id("new group::child")
    # it should have been added to the active list
    assert deck.groups.selected() == parentId
    assert deck.groups.active() == [parentId, childId]
    assert deck.groups.top()['id'] == parentId
    # we can select the child individually too
    deck.groups.select(childId)
    assert deck.groups.selected() == childId
    assert deck.groups.active() == [childId]
    assert deck.groups.top()['id'] == parentId

def test_remove():
    deck = getEmptyDeck()
    # can't remove the default group
    assertException(AssertionError, lambda: deck.groups.rem(1))
    # create a new group, and add a fact/card to it
    g1 = deck.groups.id("g1")
    f = deck.newFact()
    f['Front'] = u"1"
    f.gid = g1
    deck.addFact(f)
    c = f.cards()[0]
    assert c.gid == g1
    # by default deleting the group leaves the cards with an invalid gid
    assert deck.cardCount() == 1
    deck.groups.rem(g1)
    assert deck.cardCount() == 1
    c.load()
    assert c.gid == g1
    # but if we try to get it, we get the default
    assert deck.groups.name(c.gid) == "Default"
    # let's create another group and explicitly set the card to it
    g2 = deck.groups.id("g2")
    c.gid = g2; c.flush()
    # this time we'll delete the card/fact too
    deck.groups.rem(g2, cardsToo=True)
    assert deck.cardCount() == 0
    assert deck.factCount() == 0

