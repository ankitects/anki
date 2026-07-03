# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# coding: utf-8

from anki.errors import DeckRenameError
from tests.shared import assertException, getEmptyCol


def test_basic():
    col = getEmptyCol()
    col.set_v3_scheduler(False)
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
    # we can select a different col
    col.decks.select(parentId)
    assert col.decks.selected() == parentId
    # let's create a child
    childId = col.decks.id("new deck::child")
    # it should have been added to the active list
    assert col.decks.selected() == parentId
    # we can select the child individually too
    col.decks.select(childId)
    assert col.decks.selected() == childId
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
    note_type = note.note_type()
    note_type["did"] = deck1
    col.models.update_dict(note_type)
    col.addNote(note)
    c = note.cards()[0]
    assert c.did == deck1
    assert col.card_count() == 1
    col.decks.remove([deck1])
    assert col.card_count() == 0
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
