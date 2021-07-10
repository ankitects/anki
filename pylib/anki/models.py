# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: enable=invalid-name

from __future__ import annotations

import copy
import pprint
import sys
import time
from typing import Any, Dict, List, NewType, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
from anki import notetypes_pb2
from anki._legacy import DeprecatedNamesMixin, deprecated, print_deprecation_warning
from anki.collection import OpChanges, OpChangesWithId
from anki.consts import *
from anki.errors import NotFoundError
from anki.lang import without_unicode_isolation
from anki.stdmodels import StockNotetypeKind
from anki.utils import checksum, from_json_bytes, to_json_bytes

# public exports
NotetypeNameId = notetypes_pb2.NotetypeNameId
NotetypeNameIdUseCount = notetypes_pb2.NotetypeNameIdUseCount
NotetypeNames = notetypes_pb2.NotetypeNames
ChangeNotetypeInfo = notetypes_pb2.ChangeNotetypeInfo
ChangeNotetypeRequest = notetypes_pb2.ChangeNotetypeRequest

# legacy types
NotetypeDict = Dict[str, Any]
NoteType = NotetypeDict
FieldDict = Dict[str, Any]
TemplateDict = Dict[str, Union[str, int, None]]
NotetypeId = NewType("NotetypeId", int)
sys.modules["anki.models"].NoteType = NotetypeDict  # type: ignore


class ModelsDictProxy:
    def __init__(self, col: anki.collection.Collection):
        self._col = col.weakref()

    def _warn(self) -> None:
        print_deprecation_warning(
            "add-on should use methods on col.decks, not col.decks.decks dict"
        )

    def __getitem__(self, item: Any) -> Any:
        self._warn()
        return self._col.models.get(NotetypeId(int(item)))

    def __setitem__(self, key: str, val: Any) -> None:
        self._warn()
        self._col.models.save(val)

    def __len__(self) -> int:
        self._warn()
        return len(self._col.models.all_names_and_ids())

    def keys(self) -> Any:
        self._warn()
        return [str(nt.id) for nt in self._col.models.all_names_and_ids()]

    def values(self) -> Any:
        self._warn()
        return self._col.models.all()

    def items(self) -> Any:
        self._warn()
        return [(str(nt["id"]), nt) for nt in self._col.models.all()]

    def __contains__(self, item: Any) -> bool:
        self._warn()
        return self._col.models.have(item)


class ModelManager(DeprecatedNamesMixin):
    # Saving/loading registry
    #############################################################

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.models = ModelsDictProxy(col)
        # do not access this directly!
        self._cache = {}

    def __repr__(self) -> str:
        attrs = dict(self.__dict__)
        del attrs["col"]
        return f"{super().__repr__()} {pprint.pformat(attrs, width=300)}"

    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!

    _cache: Dict[NotetypeId, NotetypeDict] = {}

    def _update_cache(self, notetype: NotetypeDict) -> None:
        self._cache[notetype["id"]] = notetype

    def _remove_from_cache(self, ntid: NotetypeId) -> None:
        if ntid in self._cache:
            del self._cache[ntid]

    def _get_cached(self, ntid: NotetypeId) -> Optional[NotetypeDict]:
        return self._cache.get(ntid)

    def _clear_cache(self) -> None:
        self._cache = {}

    # Listing note types
    #############################################################

    def all_names_and_ids(self) -> Sequence[NotetypeNameId]:
        return self.col._backend.get_notetype_names()

    def all_use_counts(self) -> Sequence[NotetypeNameIdUseCount]:
        return self.col._backend.get_notetype_names_and_counts()

    # only used by importing code
    def have(self, id: NotetypeId) -> bool:
        if isinstance(id, str):
            id = int(id)
        return any(True for e in self.all_names_and_ids() if e.id == id)

    # Current note type
    #############################################################

    def current(self, for_deck: bool = True) -> NotetypeDict:
        "Get current model. In new code, prefer col.defaults_for_adding()"
        notetype = self.get(self.col.decks.current().get("mid"))
        if not for_deck or not notetype:
            notetype = self.get(self.col.conf["curModel"])
        if notetype:
            return notetype
        return self.get(NotetypeId(self.all_names_and_ids()[0].id))

    # Retrieving and creating models
    #############################################################

    def id_for_name(self, name: str) -> Optional[NotetypeId]:
        try:
            return NotetypeId(self.col._backend.get_notetype_id_by_name(name))
        except NotFoundError:
            return None

    def get(self, id: NotetypeId) -> Optional[NotetypeDict]:
        "Get model with ID, or None."
        # deal with various legacy input types
        if id is None:
            return None
        elif isinstance(id, str):
            id = int(id)

        notetype = self._get_cached(id)
        if not notetype:
            try:
                notetype = from_json_bytes(self.col._backend.get_notetype_legacy(id))
                self._update_cache(notetype)
            except NotFoundError:
                return None
        return notetype

    def all(self) -> List[NotetypeDict]:
        "Get all models."
        return [self.get(NotetypeId(nt.id)) for nt in self.all_names_and_ids()]

    def by_name(self, name: str) -> Optional[NotetypeDict]:
        "Get model with NAME."
        id = self.id_for_name(name)
        if id:
            return self.get(id)
        else:
            return None

    def new(self, name: str) -> NotetypeDict:
        "Create a new model, and return it."
        # caller should call save() after modifying
        notetype = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        notetype["flds"] = []
        notetype["tmpls"] = []
        notetype["name"] = name
        return notetype

    def remove_all_notetypes(self) -> None:
        for notetype in self.all_names_and_ids():
            self._remove_from_cache(NotetypeId(notetype.id))
            self.col._backend.remove_notetype(notetype.id)

    def remove(self, id: NotetypeId) -> OpChanges:
        "Modifies schema."
        self._remove_from_cache(id)
        return self.col._backend.remove_notetype(id)

    def add(self, notetype: NotetypeDict) -> OpChangesWithId:
        "Replaced with add_dict()"
        self.ensure_name_unique(notetype)
        out = self.col._backend.add_notetype_legacy(to_json_bytes(notetype))
        notetype["id"] = out.id
        self._mutate_after_write(notetype)
        return out

    def add_dict(self, notetype: NotetypeDict) -> OpChangesWithId:
        "Notetype needs to be fetched from DB after adding."
        self.ensure_name_unique(notetype)
        return self.col._backend.add_notetype_legacy(to_json_bytes(notetype))

    def ensure_name_unique(self, notetype: NotetypeDict) -> None:
        existing_id = self.id_for_name(notetype["name"])
        if existing_id is not None and existing_id != notetype["id"]:
            notetype["name"] += "-" + checksum(str(time.time()))[:5]

    def update_dict(self, notetype: NotetypeDict) -> OpChanges:
        "Update a NotetypeDict. Caller will need to re-load notetype if new fields/cards added."
        self._remove_from_cache(notetype["id"])
        self.ensure_name_unique(notetype)
        return self.col._backend.update_notetype_legacy(to_json_bytes(notetype))

    def _mutate_after_write(self, notetype: NotetypeDict) -> None:
        # existing code expects the note type to be mutated to reflect
        # the changes made when adding, such as ordinal assignment :-(
        updated = self.get(notetype["id"])
        notetype.update(updated)

    # Tools
    ##################################################

    def nids(self, ntid: NotetypeId) -> List[anki.notes.NoteId]:
        "Note ids for M."
        if isinstance(ntid, dict):
            # legacy callers passed in note type
            ntid = ntid["id"]
        return self.col.db.list("select id from notes where mid = ?", ntid)

    def use_count(self, notetype: NotetypeDict) -> int:
        "Number of note using M."
        return self.col.db.scalar(
            "select count() from notes where mid = ?", notetype["id"]
        )

    # Copying
    ##################################################

    def copy(self, notetype: NotetypeDict, add: bool = True) -> NotetypeDict:
        "Copy, save and return."
        cloned = copy.deepcopy(notetype)
        cloned["name"] = without_unicode_isolation(
            self.col.tr.notetypes_copy(val=cloned["name"])
        )
        cloned["id"] = 0
        if add:
            self.add(cloned)
        return cloned

    # Fields
    ##################################################

    def field_map(self, notetype: NotetypeDict) -> Dict[str, Tuple[int, FieldDict]]:
        "Mapping of field name -> (ord, field)."
        return {f["name"]: (f["ord"], f) for f in notetype["flds"]}

    def field_names(self, notetype: NotetypeDict) -> List[str]:
        return [f["name"] for f in notetype["flds"]]

    def sort_idx(self, notetype: NotetypeDict) -> int:
        return notetype["sortf"]

    # Adding & changing fields
    ##################################################

    def new_field(self, name: str) -> FieldDict:
        assert isinstance(name, str)
        notetype = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        field = notetype["flds"][0]
        field["name"] = name
        field["ord"] = None
        return field

    def add_field(self, notetype: NotetypeDict, field: FieldDict) -> None:
        "Modifies schema."
        notetype["flds"].append(field)

    def remove_field(self, notetype: NotetypeDict, field: FieldDict) -> None:
        "Modifies schema."
        notetype["flds"].remove(field)

    def reposition_field(
        self, notetype: NotetypeDict, field: FieldDict, idx: int
    ) -> None:
        "Modifies schema."
        oldidx = notetype["flds"].index(field)
        if oldidx == idx:
            return

        notetype["flds"].remove(field)
        notetype["flds"].insert(idx, field)

    def rename_field(
        self, notetype: NotetypeDict, field: FieldDict, new_name: str
    ) -> None:
        assert field in notetype["flds"]
        field["name"] = new_name

    def set_sort_index(self, notetype: NotetypeDict, idx: int) -> None:
        "Modifies schema."
        assert 0 <= idx < len(notetype["flds"])
        notetype["sortf"] = idx

    # Adding & changing templates
    ##################################################

    def new_template(self, name: str) -> TemplateDict:
        notetype = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        template = notetype["tmpls"][0]
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template["ord"] = None
        return template

    def add_template(self, notetype: NotetypeDict, template: TemplateDict) -> None:
        "Modifies schema."
        notetype["tmpls"].append(template)

    def remove_template(self, notetype: NotetypeDict, template: TemplateDict) -> None:
        "Modifies schema."
        assert len(notetype["tmpls"]) > 1
        notetype["tmpls"].remove(template)

    def reposition_template(
        self, notetype: NotetypeDict, template: TemplateDict, idx: int
    ) -> None:
        "Modifies schema."
        oldidx = notetype["tmpls"].index(template)
        if oldidx == idx:
            return

        notetype["tmpls"].remove(template)
        notetype["tmpls"].insert(idx, template)

    def template_use_count(self, ntid: NotetypeId, ord: int) -> int:
        return self.col.db.scalar(
            """
select count() from cards, notes where cards.nid = notes.id
and notes.mid = ? and cards.ord = ?""",
            ntid,
            ord,
        )

    # Changing notetypes of notes
    ##########################################################################

    def get_single_notetype_of_notes(
        self, note_ids: Sequence[anki.notes.NoteId]
    ) -> NotetypeId:
        return NotetypeId(
            self.col._backend.get_single_notetype_of_notes(note_ids=note_ids)
        )

    def change_notetype_info(
        self, *, old_notetype_id: NotetypeId, new_notetype_id: NotetypeId
    ) -> bytes:
        return self.col._backend.get_change_notetype_info(
            old_notetype_id=old_notetype_id, new_notetype_id=new_notetype_id
        )

    def change_notetype_of_notes(self, input: ChangeNotetypeRequest) -> OpChanges:
        """Assign a new notetype, optionally altering field/template order.

        To get defaults, use

        input = ChangeNotetypeRequest()
        input.ParseFromString(col.models.change_notetype_info(...))
        input.note_ids.extend([...])

        The new_fields and new_templates lists are relative to the new notetype's
        field/template count. Each value represents the index in the previous
        notetype. -1 indicates the original value will be discarded.
        """
        return self.col._backend.change_notetype(input)

    # legacy API - used by unit tests and add-ons

    def change(  # pylint: disable=invalid-name
        self,
        notetype: NotetypeDict,
        nids: List[anki.notes.NoteId],
        newModel: NotetypeDict,
        fmap: Dict[int, Optional[int]],
        cmap: Optional[Dict[int, Optional[int]]],
    ) -> None:
        # - maps are ord->ord, and there should not be duplicate targets
        self.col.mod_schema(check=True)
        assert fmap
        field_map = self._convert_legacy_map(fmap, len(newModel["flds"]))
        if (
            not cmap
            or newModel["type"] == MODEL_CLOZE
            or notetype["type"] == MODEL_CLOZE
        ):
            template_map = []
        else:
            template_map = self._convert_legacy_map(cmap, len(newModel["tmpls"]))

        self.col._backend.change_notetype(
            ChangeNotetypeRequest(
                note_ids=nids,
                new_fields=field_map,
                new_templates=template_map,
                old_notetype_id=notetype["id"],
                new_notetype_id=newModel["id"],
                current_schema=self.col.db.scalar("select scm from col"),
            )
        )

    def _convert_legacy_map(
        self, old_to_new: Dict[int, Optional[int]], new_count: int
    ) -> List[int]:
        "Convert old->new map to list of old indexes"
        new_to_old = {v: k for k, v in old_to_new.items() if v is not None}
        out = []
        for idx in range(new_count):
            try:
                val = new_to_old[idx]
            except KeyError:
                val = -1

            out.append(val)
        return out

    # Schema hash
    ##########################################################################

    def scmhash(self, notetype: NotetypeDict) -> str:
        "Return a hash of the schema, to see if models are compatible."
        buf = ""
        for field in notetype["flds"]:
            buf += field["name"]
        for template in notetype["tmpls"]:
            buf += template["name"]
        return checksum(buf)

    # Legacy
    ##########################################################################

    # pylint: disable=invalid-name

    @deprecated(info="use note.cloze_numbers_in_fields()")
    def _availClozeOrds(
        self, notetype: NotetypeDict, flds: str, allow_empty: bool = True
    ) -> List[int]:
        import anki.notes_pb2

        note = anki.notes_pb2.Note(fields=[flds])
        return list(self.col._backend.cloze_numbers_in_note(note))

    # @deprecated(replaced_by=add_template)
    def addTemplate(self, notetype: NotetypeDict, template: TemplateDict) -> None:
        self.add_template(notetype, template)
        if notetype["id"]:
            self.update(notetype)

    # @deprecated(replaced_by=remove_template)
    def remTemplate(self, notetype: NotetypeDict, template: TemplateDict) -> None:
        self.remove_template(notetype, template)
        self.update(notetype)

    # @deprecated(replaced_by=reposition_template)
    def move_template(
        self, notetype: NotetypeDict, template: TemplateDict, idx: int
    ) -> None:
        self.reposition_template(notetype, template, idx)
        self.update(notetype)

    # @deprecated(replaced_by=add_field)
    def addField(self, notetype: NotetypeDict, field: FieldDict) -> None:
        self.add_field(notetype, field)
        if notetype["id"]:
            self.update(notetype)

    # @deprecated(replaced_by=remove_field)
    def remField(self, notetype: NotetypeDict, field: FieldDict) -> None:
        self.remove_field(notetype, field)
        self.update(notetype)

    # @deprecated(replaced_by=reposition_field)
    def moveField(self, notetype: NotetypeDict, field: FieldDict, idx: int) -> None:
        self.reposition_field(notetype, field, idx)
        self.update(notetype)

    # @deprecated(replaced_by=rename_field)
    def renameField(
        self, notetype: NotetypeDict, field: FieldDict, new_name: str
    ) -> None:
        self.rename_field(notetype, field, new_name)
        self.update(notetype)

    @deprecated(replaced_by=remove)
    def rem(self, m: NotetypeDict) -> None:
        "Delete model, and all its cards/notes."
        self.remove(m["id"])

    # @deprecated(info="not needed; is updated on note add")
    def set_current(self, m: NotetypeDict) -> None:
        self.col.set_config("curModel", m["id"])

    @deprecated(replaced_by=all_names_and_ids)
    def all_names(self) -> List[str]:
        return [n.name for n in self.all_names_and_ids()]

    @deprecated(replaced_by=all_names_and_ids)
    def ids(self) -> List[NotetypeId]:
        return [NotetypeId(n.id) for n in self.all_names_and_ids()]

    @deprecated(info="no longer required")
    def flush(self) -> None:
        pass

    # @deprecated(replaced_by=update_dict)
    def update(self, notetype: NotetypeDict, preserve_usn: bool = True) -> None:
        "Add or update an existing model. Use .update_dict() instead."
        self._remove_from_cache(notetype["id"])
        self.ensure_name_unique(notetype)
        notetype["id"] = self.col._backend.add_or_update_notetype(
            json=to_json_bytes(notetype), preserve_usn_and_mtime=preserve_usn
        )
        self.set_current(notetype)
        self._mutate_after_write(notetype)

    # @deprecated(replaced_by=update_dict)
    def save(self, notetype: NotetypeDict = None, **legacy_kwargs: bool) -> None:
        "Save changes made to provided note type."
        if not notetype:
            print_deprecation_warning(
                "col.models.save() should be passed the changed notetype"
            )
            return

        self.update(notetype, preserve_usn=False)
