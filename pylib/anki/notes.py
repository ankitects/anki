# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
from typing import NewType, Sequence

import anki  # pylint: disable=unused-import
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
        return join_fields(self.fields)

    def ephemeral_card(
        self,
        ord: int = 0,
        *,
        custom_note_type: NotetypeDict = None,
        custom_template: TemplateDict = None,
        fill_empty: bool = False,
    ) -> anki.cards.Card:
        card = anki.cards.Card(self.col)
        card.ord = ord
        card.did = anki.decks.DEFAULT_DECK_ID

        model = custom_note_type or self.note_type()
        template = copy.copy(
            custom_template
            or (
                model["tmpls"][ord] if model["type"] == MODEL_STD else model["tmpls"][0]
            )
        )
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
        return [self.col.get_card(id) for id in self.card_ids()]

    def card_ids(self) -> Sequence[anki.cards.CardId]:
        return self.col.card_ids_of_note(self.id)

    def note_type(self) -> NotetypeDict | None:
        return self.col.models.get(self.mid)

    _note_type = property(note_type)

    def cloze_numbers_in_fields(self) -> Sequence[int]:
        return self.col._backend.cloze_numbers_in_note(self._to_backend_note())

    # Dict interface
    ##################################################

    def keys(self) -> list[str]:
        return list(self._fmap.keys())

    def values(self) -> list[str]:
        return self.fields

    def items(self) -> list[tuple[str, str]]:
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
        return self.col.tags.in_list(tag, self.tags)

    def remove_tag(self, tag: str) -> None:
        rem = []
        for tag_ in self.tags:
            if tag_.lower() == tag.lower():
                rem.append(tag_)
        for tag_ in rem:
            self.tags.remove(tag_)

    def add_tag(self, tag: str) -> None:
        "Add tag. Duplicates will be stripped on save."
        self.tags.append(tag)

    def string_tags(self) -> str:
        return self.col.tags.join(self.tags)

    def set_tags_from_str(self, tags: str) -> None:
        self.tags = self.col.tags.split(tags)

    # Unique/duplicate/cloze check
    ##################################################

    def fields_check(self) -> NoteFieldsCheckResult.V:
        return self.col._backend.note_fields_check(self._to_backend_note()).state

    dupeOrEmpty = duplicate_or_empty = fields_check


Note.register_deprecated_aliases(
    delTag=Note.remove_tag, _fieldOrd=Note._field_index, model=Note.note_type
)
