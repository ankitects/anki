# coding: utf-8

from anki.errors import DeckRenameError
from tests.shared import assertException, getEmptyCol


def test_basic():
    deck = getEmptyCol()
    # we start with a standard deck
    assert len(deck.decks.all_names_and_ids()) == 1
    # it should have an id of 1
    assert deck.decks.name(1)
    # create a new deck
    parentId = deck.decks.id("new deck")
    assert parentId
    assert len(deck.decks.all_names_and_ids()) == 2
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
    # parents with a different case should be handled correctly
    deck.decks.id("ONE")
    m = deck.models.current()
    m["did"] = deck.decks.id("one::two")
    deck.models.save(m, updateReqs=False)
    n = deck.newNote()
    n["Front"] = "abc"
    deck.addNote(n)
    # this will error if child and parent case don't match
    deck.sched.deckDueList()


def test_remove():
    deck = getEmptyCol()
    # create a new deck, and add a note/card to it
    g1 = deck.decks.id("g1")
    f = deck.newNote()
    f["Front"] = "1"
    f.model()["did"] = g1
    deck.addNote(f)
    c = f.cards()[0]
    assert c.did == g1
    assert deck.cardCount() == 1
    deck.decks.rem(g1)
    assert deck.cardCount() == 0
    # if we try to get it, we get the default
    assert deck.decks.name(c.did) == "[no deck]"


def test_rename():
    d = getEmptyCol()
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
    assertException(Exception, lambda: d.decks.rename(d.decks.get(id), "foo"))
    # when renaming, the children should be renamed too
    d.decks.id("one::two::three")
    id = d.decks.id("one")
    d.decks.rename(d.decks.get(id), "yo")
    for n in "yo", "yo::two", "yo::two::three":
        assert n in d.decks.allNames()
    # over filtered
    filteredId = d.decks.newDyn("filtered")
    filtered = d.decks.get(filteredId)
    childId = d.decks.id("child")
    child = d.decks.get(childId)
    assertException(DeckRenameError, lambda: d.decks.rename(child, "filtered::child"))
    assertException(DeckRenameError, lambda: d.decks.rename(child, "FILTERED::child"))
    # changing case
    parentId = d.decks.id("PARENT")
    d.decks.id("PARENT::CHILD")
    assertException(DeckRenameError, lambda: d.decks.rename(child, "PARENT::CHILD"))
    assertException(DeckRenameError, lambda: d.decks.rename(child, "PARENT::child"))


def test_renameForDragAndDrop():
    d = getEmptyCol()

    def deckNames():
        return [name for name in sorted(d.decks.allNames()) if name != "Default"]

    languages_did = d.decks.id("Languages")
    chinese_did = d.decks.id("Chinese")
    hsk_did = d.decks.id("Chinese::HSK")

    # Renaming also renames children
    d.decks.renameForDragAndDrop(chinese_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto itself is a no-op
    d.decks.renameForDragAndDrop(languages_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto its parent is a no-op
    d.decks.renameForDragAndDrop(hsk_did, chinese_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto a descendant is a no-op
    d.decks.renameForDragAndDrop(languages_did, hsk_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Can drag a grandchild onto its grandparent.  It becomes a child
    d.decks.renameForDragAndDrop(hsk_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::HSK"]

    # Can drag a deck onto its sibling
    d.decks.renameForDragAndDrop(hsk_did, chinese_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Can drag a deck back to the top level
    d.decks.renameForDragAndDrop(chinese_did, None)
    assert deckNames() == ["Chinese", "Chinese::HSK", "Languages"]

    # Dragging a top level deck to the top level is a no-op
    d.decks.renameForDragAndDrop(chinese_did, None)
    assert deckNames() == ["Chinese", "Chinese::HSK", "Languages"]

    # can't drack a deck where sibling have same name
    new_hsk_did = d.decks.id("HSK")
    assertException(
        DeckRenameError, lambda: d.decks.renameForDragAndDrop(new_hsk_did, chinese_did)
    )
    d.decks.rem(new_hsk_did)

    # can't drack a deck where sibling have same name different case
    new_hsk_did = d.decks.id("hsk")
    assertException(
        DeckRenameError, lambda: d.decks.renameForDragAndDrop(new_hsk_did, chinese_did)
    )
    d.decks.rem(new_hsk_did)

    # '' is a convenient alias for the top level DID
    d.decks.renameForDragAndDrop(hsk_did, "")
    assert deckNames() == ["Chinese", "HSK", "Languages"]


def test_check():
    d = getEmptyCol()

    # currently disabled - see 5418af00f733ca62b0c087d1422feae01d6571b0
    # foo_did = d.decks.id("foo")
    # FOO_did = d.decks.id("bar")
    # FOO = d.decks.byName("bar")
    # FOO["name"] = "FOO"
    # d.decks.save(FOO)
    # d.decks._checkDeckTree()
    # assert "foo" not in d.decks.allNames() or "FOO" not in d.decks.allNames()
