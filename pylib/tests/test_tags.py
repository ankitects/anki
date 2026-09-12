# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html


import pytest
from mock import MagicMock

from anki.collection import AddNoteRequest, Collection
from anki.decks import DEFAULT_DECK_ID
from anki.notes import Note
from tests.shared import getEmptyCol


@pytest.fixture
def col() -> Collection:
    return getEmptyCol()


@pytest.fixture
def basic_note(col: Collection) -> Note:
    model = col.models.by_name("Basic")
    note = col.new_note(model)
    note["Front"] = "f"
    note["Back"] = "b"
    return note


def test_all_tags_returned(col, basic_note):
    basic_note.tags = ["a", "b"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    assert sorted(col.tags.all()) == ["a", "b"]


def test_tags_tree(col, basic_note):
    # A basic test to ensure this routine is covered; exhaustive tests belong to the Rust backend
    basic_note.tags = ["a::b::c", "a::z"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    tree = col.tags.tree()
    assert tree.children[0].name == "a"
    assert len(tree.children[0].children) == 2


def test_clear_unused_tags(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    assert col.tags.all() == ["a"]
    basic_note.tags.clear()
    basic_note.flush()
    col.tags.clear_unused_tags()
    assert col.tags.all() == []


def test_set_collapsed(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    assert col.tags.tree().children[0].collapsed
    col.tags.set_collapsed("a", False)
    assert not col.tags.tree().children[0].collapsed


def test_bulk_add(col):
    model = col.models.by_name("Basic")
    notes: list[Note] = []
    for _ in range(10):
        note = col.new_note(model)
        notes.append(note)
    col.add_notes(
        [AddNoteRequest(note=note, deck_id=DEFAULT_DECK_ID) for note in notes]
    )
    col.tags.bulk_add([note.id for note in notes], "a b")
    for note in notes:
        note.load()
        assert note.tags == ["a", "b"]


def test_bulk_remove(col):
    model = col.models.by_name("Basic")
    notes: list[Note] = []
    for _ in range(10):
        note = col.new_note(model)
        note.tags = ["a", "b"]
        notes.append(note)
    col.add_notes(
        [AddNoteRequest(note=note, deck_id=DEFAULT_DECK_ID) for note in notes]
    )
    col.tags.bulk_remove([note.id for note in notes], "a")
    for note in notes:
        note.load()
        assert note.tags == ["b"]


def test_find_and_replace(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    col.tags.find_and_replace([basic_note.id], "a", "b", False, False)
    basic_note.load()
    assert basic_note.tags == ["b"]


def test_rename(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    col.tags.rename("a", "b")
    basic_note.load()
    assert basic_note.tags == ["b"]


def test_remove(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    col.tags.remove("a")
    basic_note.load()
    assert basic_note.tags == []


def test_reparent(col, basic_note):
    basic_note.tags = ["a"]
    col.add_note(basic_note, DEFAULT_DECK_ID)
    col.tags.reparent(["a"], "z")
    basic_note.load()
    assert basic_note.tags == ["z::a"]


def test_join(col):
    assert col.tags.join(["a", "b"]) == " a b "
    assert col.tags.join([]) == ""


def test_rem_from_str(col):
    assert col.tags.rem_from_str("bar", "foo bar baz BAR") == " foo baz "
    assert col.tags.rem_from_str("ba*", "foo") == " foo "


def test_legacy_register_notes(col, monkeypatch):
    mock = MagicMock()
    monkeypatch.setattr(col.tags, "clear_unused_tags", mock)
    col.tags._legacy_register_notes()
    mock.assert_called_once()


def test_legacy_bulk_add_remove(col, monkeypatch):
    add_mock = MagicMock()
    monkeypatch.setattr(col.tags, "bulk_add", add_mock)
    remove_mock = MagicMock()
    monkeypatch.setattr(col.tags, "bulk_remove", remove_mock)
    col.tags._legacy_bulk_add([], [], True)
    add_mock.assert_called_once()
    remove_mock.assert_not_called()
    col.tags._legacy_bulk_add([], [], False)
    remove_mock.assert_called()
    remove_mock.reset_mock()
    col.tags._legacy_bulk_rem([], [])
    remove_mock.assert_called()


def test_canonify(col):
    tags = ["a", "a", "A"]
    # No-op
    assert col.tags.canonify(tags) == tags
