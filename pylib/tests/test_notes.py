# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy

import pytest
from mock import MagicMock

from anki.collection import Collection
from anki.notes import Note, NoteFieldsCheckResult, NoteId
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


def test_id_and_model_are_mutually_exclusive(col):
    model = col.models.current()
    nid = NoteId(1)
    with pytest.raises(Exception, match="only model or id should be provided"):
        Note(col=col, id=nid, model=model)


def test_cannot_flush_new_note(col):
    model = col.models.current()
    note = col.new_note(model)
    with pytest.raises(Exception, match="can't flush a new note"):
        note.flush()


def test_join_fields(col, basic_note):
    assert basic_note.joined_fields().count("\x1f") == 1


@pytest.fixture
def from_card_layout(monkeypatch) -> MagicMock:
    from_card_layout_mock = MagicMock()
    monkeypatch.setattr(
        "anki.template.TemplateRenderContext.from_card_layout", from_card_layout_mock
    )
    return from_card_layout_mock


def test_ephemeral_card_default(col, from_card_layout):
    model = col.models.by_name("Basic (and reversed card)")
    note = col.new_note(model)
    model = note.note_type()
    note.ephemeral_card()
    kwargs = from_card_layout.call_args.kwargs
    assert kwargs["notetype"] == model
    assert kwargs["template"] == model["tmpls"][0]


def test_ephemeral_card_custom_ordinal(col, from_card_layout):
    model = col.models.by_name("Basic (and reversed card)")
    note = col.new_note(model)
    note.ephemeral_card(ord=1)
    kwargs = from_card_layout.call_args.kwargs
    assert kwargs["notetype"] == model
    assert kwargs["template"] == model["tmpls"][1]


def test_ephemeral_card_cloze(col, from_card_layout):
    model = col.models.by_name("Cloze")
    note = col.new_note(model)
    ordinal = 1
    note.ephemeral_card(ord=ordinal)
    kwargs = from_card_layout.call_args.kwargs
    assert kwargs["notetype"] == model
    # Ordinal is different in cloze case
    assert kwargs["template"] == {**model["tmpls"][0], "ord": ordinal}


def test_ephemeral_card_custom_model_and_template(col, from_card_layout):
    note = col.new_note(col.models.current())
    custom_model = col.models.by_name("Basic (and reversed card)")
    custom_template = copy.copy(custom_model["tmpls"][0])
    custom_template["qfmt"] += "test"
    note.ephemeral_card(custom_note_type=custom_model, custom_template=custom_template)
    kwargs = from_card_layout.call_args.kwargs
    assert kwargs["notetype"] == custom_model
    assert kwargs["template"] == custom_template


def test_cloze_numbers_in_fields(col):
    model = col.models.by_name("Cloze")
    note = col.new_note(model)
    note["Text"] = "{{c3::single}} {{c1,2::multi}}"
    assert sorted(note.cloze_numbers_in_fields()) == [1, 2, 3]


def test_note_keys_and_values(basic_note):
    assert sorted(basic_note.keys()) == ["Back", "Front"]
    assert sorted(basic_note.values()) == ["b", "f"]
    assert "Front" in basic_note
    assert "Back" in basic_note


def test_raises_keyerror(col):
    model = col.models.by_name("Basic")
    note = col.new_note(model)
    field_name = "foo"
    with pytest.raises(KeyError, match=field_name):
        note[field_name] = "f"


def test_has_tag(basic_note):
    basic_note.tags = ["tag1"]
    assert basic_note.has_tag("tag1")
    assert not basic_note.has_tag("tag2")


def test_remove_tag(basic_note):
    tags = ["tag1", "Tag1", "tag2"]
    for to_remove in ("tag1", "Tag1"):
        basic_note.tags = tags
        basic_note.remove_tag(to_remove)
        assert basic_note.tags == ["tag2"]


def test_add_tag(basic_note):
    basic_note.add_tag("tag1")
    basic_note.add_tag("Tag1")
    basic_note.add_tag("tag2")
    assert basic_note.tags == ["tag1", "Tag1", "tag2"]


def test_str_tags(basic_note):
    tags = ["tag1", "Tag1", "tag2"]
    tags_str = " tag1 Tag1 tag2 "

    basic_note.tags = tags
    assert basic_note.string_tags() == tags_str
    basic_note.tags = []
    basic_note.set_tags_from_str(tags_str)
    assert basic_note.tags == tags


def test_fields_check_normal(basic_note):
    assert basic_note.fields_check() == NoteFieldsCheckResult.NORMAL
