# coding: utf-8

from anki.errors import DeckRenameError
from tests.shared import assertException, getEmptyCol


def test_basic():
    col = getEmptyCol()
    # we start with a standard col
    assert len(col.decks.all_names_and_ids()) == 1
    # it should have an id of 1
    assert col.decks.name(1)
    # create a new col
    parentId = col.decks.id("new deck")
    assert parentId
    assert len(col.decks.all_names_and_ids()) == 2
    # should get the same id
    assert col.decks.id("new deck") == parentId
    # we start with the default col selected
    assert col.decks.selected() == 1
    assert col.decks.active() == [1]
    # we can select a different col
    col.decks.select(parentId)
    assert col.decks.selected() == parentId
    assert col.decks.active() == [parentId]
    # let's create a child
    childId = col.decks.id("new deck::child")
    col.sched.reset()
    # it should have been added to the active list
    assert col.decks.selected() == parentId
    assert col.decks.active() == [parentId, childId]
    # we can select the child individually too
    col.decks.select(childId)
    assert col.decks.selected() == childId
    assert col.decks.active() == [childId]
    # parents with a different case should be handled correctly
    col.decks.id("ONE")
    m = col.models.current()
    m["did"] = col.decks.id("one::two")
    col.models.save(m, updateReqs=False)
    n = col.newNote()
    n["Front"] = "abc"
    col.addNote(n)


def test_remove():
    col = getEmptyCol()
    # create a new col, and add a note/card to it
    deck1 = col.decks.id("deck1")
    note = col.newNote()
    note["Front"] = "1"
    note.model()["did"] = deck1
    col.addNote(note)
    c = note.cards()[0]
    assert c.did == deck1
    assert col.cardCount() == 1
    col.decks.rem(deck1)
    assert col.cardCount() == 0
    # if we try to get it, we get the default
    assert col.decks.name(c.did) == "[no deck]"


def test_rename():
    col = getEmptyCol()
    id = col.decks.id("hello::world")
    # should be able to rename into a completely different branch, creating
    # parents as necessary
    col.decks.rename(col.decks.get(id), "foo::bar")
    names = [n.name for n in col.decks.all_names_and_ids()]
    assert "foo" in names
    assert "foo::bar" in names
    assert "hello::world" not in names
    # create another col
    id = col.decks.id("tmp")
    # automatically adjusted if a duplicate name
    col.decks.rename(col.decks.get(id), "FOO")
    names = [n.name for n in col.decks.all_names_and_ids()]
    assert "FOO+" in names
    # when renaming, the children should be renamed too
    col.decks.id("one::two::three")
    id = col.decks.id("one")
    col.decks.rename(col.decks.get(id), "yo")
    names = [n.name for n in col.decks.all_names_and_ids()]
    for n in "yo", "yo::two", "yo::two::three":
        assert n in names
    # over filtered
    filteredId = col.decks.new_filtered("filtered")
    filtered = col.decks.get(filteredId)
    childId = col.decks.id("child")
    child = col.decks.get(childId)
    assertException(DeckRenameError, lambda: col.decks.rename(child, "filtered::child"))
    assertException(DeckRenameError, lambda: col.decks.rename(child, "FILTERED::child"))


def test_renameForDragAndDrop():
    col = getEmptyCol()

    def deckNames():
        return [n.name for n in col.decks.all_names_and_ids(skip_empty_default=True)]

    languages_did = col.decks.id("Languages")
    chinese_did = col.decks.id("Chinese")
    hsk_did = col.decks.id("Chinese::HSK")

    # Renaming also renames children
    col.decks.renameForDragAndDrop(chinese_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto itself is a no-op
    col.decks.renameForDragAndDrop(languages_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto its parent is a no-op
    col.decks.renameForDragAndDrop(hsk_did, chinese_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Dragging a deck onto a descendant is a no-op
    col.decks.renameForDragAndDrop(languages_did, hsk_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Can drag a grandchild onto its grandparent.  It becomes a child
    col.decks.renameForDragAndDrop(hsk_did, languages_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::HSK"]

    # Can drag a deck onto its sibling
    col.decks.renameForDragAndDrop(hsk_did, chinese_did)
    assert deckNames() == ["Languages", "Languages::Chinese", "Languages::Chinese::HSK"]

    # Can drag a deck back to the top level
    col.decks.renameForDragAndDrop(chinese_did, None)
    assert deckNames() == ["Chinese", "Chinese::HSK", "Languages"]

    # Dragging a top level col to the top level is a no-op
    col.decks.renameForDragAndDrop(chinese_did, None)
    assert deckNames() == ["Chinese", "Chinese::HSK", "Languages"]

    # decks are renamed if necessary
    new_hsk_did = col.decks.id("hsk")
    col.decks.renameForDragAndDrop(new_hsk_did, chinese_did)
    assert deckNames() == ["Chinese", "Chinese::HSK", "Chinese::hsk+", "Languages"]
    col.decks.rem(new_hsk_did)

    # '' is a convenient alias for the top level DID
    col.decks.renameForDragAndDrop(hsk_did, "")
    assert deckNames() == ["Chinese", "HSK", "Languages"]
