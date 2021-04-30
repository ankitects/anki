# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import pprint
from typing import Any, List, NewType, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki import hooks
from anki.consts import MODEL_STD
from anki.models import NotetypeDict, NotetypeId, TemplateDict
from anki.utils import joinFields

DuplicateOrEmptyResult = _pb.NoteIsDuplicateOrEmptyOut.State

# types
NoteId = NewType("NoteId", int)


class Note:
    # not currently exposed
    flags = 0
    data = ""
    id: NoteId
    mid: NotetypeId

    def __init__(
        self,
        col: anki.collection.Collection,
        model: Optional[Union[NotetypeDict, NotetypeId]] = None,
        id: Optional[NoteId] = None,
    ) -> None:
        assert not (model and id)
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
        n = self.col._backend.get_note(self.id)
        assert n
        self._load_from_backend_note(n)

    def _load_from_backend_note(self, n: _pb.Note) -> None:
        self.id = NoteId(n.id)
        self.guid = n.guid
        self.mid = NotetypeId(n.notetype_id)
        self.mod = n.mtime_secs
        self.usn = n.usn
        self.tags = list(n.tags)
        self.fields = list(n.fields)
        self._fmap = self.col.models.fieldMap(self.model())

    def _to_backend_note(self) -> _pb.Note:
        hooks.note_will_flush(self)
        return _pb.Note(
            id=self.id,
            guid=self.guid,
            notetype_id=self.mid,
            mtime_secs=self.mod,
            usn=self.usn,
            tags=self.tags,
            fields=self.fields,
        )

    def flush(self) -> None:
        """This preserves any current checkpoint.
        For an undo entry, use col.update_note() instead."""
        assert self.id != 0
        self.col._backend.update_note(
            note=self._to_backend_note(), skip_undo_entry=True
        )

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    def joinedFields(self) -> str:
        return joinFields(self.fields)

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

        model = custom_note_type or self.model()
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

    def cards(self) -> List[anki.cards.Card]:
        return [self.col.getCard(id) for id in self.card_ids()]

    def card_ids(self) -> Sequence[anki.cards.CardId]:
        return self.col.card_ids_of_note(self.id)

    def model(self) -> Optional[NotetypeDict]:
        return self.col.models.get(self.mid)

    _model = property(model)

    def cloze_numbers_in_fields(self) -> Sequence[int]:
        return self.col._backend.cloze_numbers_in_note(self._to_backend_note())

    # Dict interface
    ##################################################

    def keys(self) -> List[str]:
        return list(self._fmap.keys())

    def values(self) -> List[str]:
        return self.fields

    def items(self) -> List[Tuple[Any, Any]]:
        return [(f["name"], self.fields[ord]) for ord, f in sorted(self._fmap.values())]

    def _fieldOrd(self, key: str) -> Any:
        try:
            return self._fmap[key][0]
        except Exception as exc:
            raise KeyError(key) from exc

    def __getitem__(self, key: str) -> str:
        return self.fields[self._fieldOrd(key)]

    def __setitem__(self, key: str, value: str) -> None:
        self.fields[self._fieldOrd(key)] = value

    def __contains__(self, key: str) -> bool:
        return key in self._fmap

    # Tags
    ##################################################

    def has_tag(self, tag: str) -> bool:
        return self.col.tags.inList(tag, self.tags)

    def remove_tag(self, tag: str) -> None:
        rem = []
        for t in self.tags:
            if t.lower() == tag.lower():
                rem.append(t)
        for r in rem:
            self.tags.remove(r)

    def add_tag(self, tag: str) -> None:
        "Add tag. Duplicates will be stripped on save."
        self.tags.append(tag)

    def stringTags(self) -> Any:
        return self.col.tags.join(self.col.tags.canonify(self.tags))

    def setTagsFromStr(self, tags: str) -> None:
        self.tags = self.col.tags.split(tags)

    hasTag = has_tag
    addTag = add_tag
    delTag = remove_tag

    # Unique/duplicate check
    ##################################################

    def duplicate_or_empty(self) -> DuplicateOrEmptyResult.V:
        return self.col._backend.note_is_duplicate_or_empty(
            self._to_backend_note()
        ).state

    dupeOrEmpty = duplicate_or_empty
