# coding: utf-8

from tests.shared import assertException, getEmptyDeck, testDir

def test_basic():
    deck = getEmptyDeck()
    # we start with a standard deck
    assert len(deck.decks.decks) == 1
    # it should have an id of 1
    assert deck.decks.name(1)
    # create a new deck
    parentId = deck.decks.id("new deck")
    assert parentId
    assert len(deck.decks.decks) == 2
    # should get the same id
    assert deck.decks.id("new deck") == parentId
    # we start with the default deck selected
    assert deck.decks.selected() == 1
    assert deck.decks.active() == [1]
    # we can select a different deck
    deck.decks.select(parentId)
    assert deck.decks.selected() == parentId
    assert deck.decks.active() == [parentId]
    # let's create a child
    childId = deck.decks.id("new deck::child")
    # it should have been added to the active list
    assert deck.decks.selected() == parentId
    assert deck.decks.active() == [parentId, childId]
    # we can select the child individually too
    deck.decks.select(childId)
    assert deck.decks.selected() == childId
    assert deck.decks.active() == [childId]

def test_remove():
    deck = getEmptyDeck()
    # can't remove the default deck
    assertException(AssertionError, lambda: deck.decks.rem(1))
    # create a new deck, and add a note/card to it
    g1 = deck.decks.id("g1")
    f = deck.newNote()
    f['Front'] = u"1"
    f.did = g1
    deck.addNote(f)
    c = f.cards()[0]
    assert c.did == g1
    # by default deleting the deck leaves the cards with an invalid did
    assert deck.cardCount() == 1
    deck.decks.rem(g1)
    assert deck.cardCount() == 1
    c.load()
    assert c.did == g1
    # but if we try to get it, we get the default
    assert deck.decks.name(c.did) == "Default"
    # let's create another deck and explicitly set the card to it
    g2 = deck.decks.id("g2")
    c.did = g2; c.flush()
    # this time we'll delete the card/note too
    deck.decks.rem(g2, cardsToo=True)
    assert deck.cardCount() == 0
    assert deck.noteCount() == 0

def test_rename():
    d = getEmptyDeck()
    id = d.decks.id("hello::world")
    # should be able to rename into a completely different branch, creating
    # parents as necessary
    d.decks.rename(d.decks.get(id), "foo::bar")
    assert "foo" in d.decks.allNames()
    assert "foo::bar" in d.decks.allNames()
    assert "hello::world" not in d.decks.allNames()
    # create another deck
    id = d.decks.id("tmp")
    # we can't rename it if it conflicts
    assertException(
        Exception, lambda: d.decks.rename(d.decks.get(id), "foo"))
    # when renaming, the children should be renamed too
    d.decks.id("one::two::three")
    id = d.decks.id("one")
    d.decks.rename(d.decks.get(id), "yo")
    for n in "yo", "yo::two", "yo::two::three":
        assert n in d.decks.allNames()
