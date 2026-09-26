# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
from collections.abc import Sequence
from typing import NewType

import anki
import anki.cards
import anki.collection
import anki.decks
import anki.template
from anki import hooks, notes_pb2
from anki._legacy import DeprecatedNamesMixin, deprecated
from anki.consts import MODEL_STD
from anki.models import NotetypeDict, NotetypeId, TemplateDict
from anki.utils import join_fields

DuplicateOrEmptyResult = notes_pb2.NoteFieldsCheckResponse.State
NoteFieldsCheckResult = notes_pb2.NoteFieldsCheckResponse.State
DefaultsForAdding = notes_pb2.DeckAndNotetype

# types
NoteId = NewType("NoteId", int)


class Note(DeprecatedNamesMixin):
    """A note: the data half of an Anki card.

    Notes are created with either a notetype, for a note you have yet to add to
    the collection, or an id, to load a note that is already saved:

    >>> note = col.new_note()                     # doctest: +SKIP
    >>> note = col.get_note(1234567890123)        # doctest: +SKIP

    A new note is not saved until you pass it to `Collection.add_note()`. Both
    forms give you the same object afterwards.

    Fields can be read and written by name:

    >>> note["Front"] = "A bird in the hand"       # doctest: +SKIP
    >>> note["Back"]                              # doctest: +SKIP
    'Two in the bush'

    Saving is done through the collection, not the note, so that one undo entry
    covers everything you changed together:

    >>> col.update_note(note)                     # doctest: +SKIP
    """

    # not currently exposed
    flags = 0
    data = ""
    id: NoteId
    mid: NotetypeId

    def __init__(
        self,
        col: anki.collection.Collection,
        model: NotetypeDict | NotetypeId | None = None,
        id: NoteId | None = None,
    ) -> None:
        if model and id:
            raise Exception("only model or id should be provided")
        notetype_id = model["id"] if isinstance(model, dict) else model
        self.col = col.weakref()

        if id:
            # existing note
            self.id = id
            self.load()
        else:
            # new note for provided notetype
            self._load_from_backend_note(self.col._backend.new_note(notetype_id))

    def load(self) -> None:
        """Discard the in-memory copy and reload the note from the database.

        Useful to pick up changes another part of your code made to the note
        behind your back, for instance through a direct database write.
        """
        note = self.col._backend.get_note(self.id)
        assert note
        self._load_from_backend_note(note)

    def _load_from_backend_note(self, note: notes_pb2.Note) -> None:
        self.id = NoteId(note.id)
        self.guid = note.guid
        self.mid = NotetypeId(note.notetype_id)
        self.mod = note.mtime_secs
        self.usn = note.usn
        self.tags = list(note.tags)
        self.fields = list(note.fields)
        self._fmap = self.col.models.field_map(self.note_type())

    def _to_backend_note(self) -> notes_pb2.Note:
        hooks.note_will_flush(self)
        return notes_pb2.Note(
            id=self.id,
            guid=self.guid,
            notetype_id=self.mid,
            mtime_secs=self.mod,
            usn=self.usn,
            tags=self.tags,
            fields=self.fields,
        )

    @deprecated(info="please use col.update_note()")
    def flush(self) -> None:
        """For an undo entry, use col.update_note() instead."""
        if self.id == 0:
            raise Exception("can't flush a new note")
        self.col._backend.update_notes(
            notes=[self._to_backend_note()], skip_undo_entry=True
        )

    def joined_fields(self) -> str:
        """The fields joined by `\\x1f`, for use in a note's sort field.

        This is the form the search index stores, so it is what you want if you
        are building a search query by hand. The other helpers in
        `anki.utils` (`join_fields()` and `split_fields()`) do the same thing.
        """
        return join_fields(self.fields)

    def ephemeral_card(
        self,
        ord: int = 0,
        *,
        custom_note_type: NotetypeDict | None = None,
        custom_template: TemplateDict | None = None,
        fill_empty: bool = False,
    ) -> anki.cards.Card:
        """Render a card for this note without saving it.

        Returns a `Card` that is not in the database and has no id, so it can
        only be used to inspect the question and answer text. To modify the
        layout that gets rendered, pass `custom_note_type` with a modified
        notetype, or `custom_template` with a single template from the
        notetype.

        `ord` selects a template by position, and defaults to the first one.
        `fill_empty` substitutes a placeholder for empty fields, which is what
        the reviewer does, rather than leaving them blank.
        """
        card = anki.cards.Card(self.col)
        card.ord = ord
        card.did = anki.decks.DEFAULT_DECK_ID

        if custom_note_type is None:
            model = self.note_type()
        else:
            model = custom_note_type
        if model is None:
            raise NotImplementedError

        if custom_template is not None:
            template = custom_template
        elif model["type"] == MODEL_STD:
            template = model["tmpls"][ord]
        else:
            template = model["tmpls"][0]
        template = copy.copy(template)
        # may differ in cloze case
        template["ord"] = card.ord

        output = anki.template.TemplateRenderContext.from_card_layout(
            self,
            card,
            notetype=model,
            template=template,
            fill_empty=fill_empty,
        ).render()
        card.set_render_output(output)
        card._note = self
        return card

    def cards(self) -> list[anki.cards.Card]:
        """The cards generated by this note, in ordinal order.

        A note generates one card per template in its notetype, and cloze
        notetypes generate one per cloze deletion instead.
        """
        return [self.col.get_card(id) for id in self.card_ids()]

    def card_ids(self) -> Sequence[anki.cards.CardId]:
        """The ids of the cards generated by this note."""
        return self.col.card_ids_of_note(self.id)

    def note_type(self) -> NotetypeDict | None:
        """The notetype this note belongs to, or `None` if it is not found."""
        return self.col.models.get(self.mid)

    _note_type = property(note_type)

    def cloze_numbers_in_fields(self) -> Sequence[int]:
        """The cloze deletion numbers in this note's fields, without duplicates.

        Only meaningful for a cloze notetype. The order is whatever the backend
        produced, so sort the result if you need it in order.
        """
        return self.col._backend.cloze_numbers_in_note(self._to_backend_note())

    # Dict interface
    ##################################################

    def keys(self) -> list[str]:
        """The field names, in ordinal order."""
        return list(self._fmap.keys())

    def values(self) -> list[str]:
        """The field values, in ordinal order."""
        return self.fields

    def items(self) -> list[tuple[str, str]]:
        """The (name, value) pairs, in ordinal order."""
        return [(f["name"], self.fields[ord]) for ord, f in sorted(self._fmap.values())]

    def _field_index(self, key: str) -> int:
        try:
            return self._fmap[key][0]
        except Exception as exc:
            raise KeyError(key) from exc

    def __getitem__(self, key: str) -> str:
        return self.fields[self._field_index(key)]

    def __setitem__(self, key: str, value: str) -> None:
        self.fields[self._field_index(key)] = value

    def __contains__(self, key: str) -> bool:
        return key in self._fmap

    # Tags
    ##################################################

    def has_tag(self, tag: str) -> bool:
        """Whether the note has the given tag, compared case-insensitively."""
        return self.col.tags.in_list(tag, self.tags)

    def remove_tag(self, tag: str) -> None:
        """Remove a tag, compared case-insensitively.

        Does nothing if the note does not have the tag.
        """
        rem = [tag_ for tag_ in self.tags if tag_.lower() == tag.lower()]
        for tag_ in rem:
            self.tags.remove(tag_)

    def add_tag(self, tag: str) -> None:
        "Add tag. Duplicates will be stripped on save."
        self.tags.append(tag)

    def string_tags(self) -> str:
        """The tags as a single string, for use in a search query.

        The result has a leading and trailing space, and an empty string
        becomes `""`, so the output can be dropped straight into `tag:`.
        """
        return self.col.tags.join(self.tags)

    def set_tags_from_str(self, tags: str) -> None:
        """Replace the tags with those in a space-separated string.

        Runs of spaces and any leading or trailing spaces produce no empty
        tags, and an ideographic space (U+3000) counts as a separator.
        """
        self.tags = self.col.tags.split(tags)

    # Unique/duplicate/cloze check
    ##################################################

    def fields_check(self) -> NoteFieldsCheckResult.V:
        """Whether the note looks acceptable, as a `NoteFieldsCheckResult` value.

        The result is one of:

        - `NORMAL`: the note is fine
        - `EMPTY`: the first field is empty, so the note has nothing to be
          reviewed from
        - `DUPLICATE`: the first field matches an existing note
        - `MISSING_CLOZE`: a cloze note with no deletions in its fields
        - `NOTETYPE_NOT_CLOZE`: a cloze deletion in a notetype that is not a
          cloze notetype
        - `FIELD_NOT_CLOZE`: a cloze deletion in a field that is not a cloze
          field

        The first field is stripped of HTML before the `EMPTY` and `DUPLICATE`
        checks, so a first field holding only markup counts as empty, and two
        notes that differ only in markup count as duplicates. If the
        `normalizeNoteText` preference is enabled, the first field is also
        normalized to NFC first.

        `DUPLICATE` is looked up by the first field alone rather than the sort
        field, across notes of the same notetype, ignoring the note itself. An
        already-saved note is therefore reported as a duplicate too, which
        matters because `Collection.add_notes()` keeps existing duplicates and
        only rejects genuinely new ones.
        """
        return self.col._backend.note_fields_check(self._to_backend_note()).state

    dupeOrEmpty = duplicate_or_empty = fields_check


Note.register_deprecated_aliases(
    delTag=Note.remove_tag, _fieldOrd=Note._field_index, model=Note.note_type
)
