# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import pprint
import sys
import time
import traceback
from typing import Any, Dict, List, NewType, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki.collection import OpChanges, OpChangesWithId
from anki.consts import *
from anki.errors import NotFoundError
from anki.lang import without_unicode_isolation
from anki.stdmodels import StockNotetypeKind
from anki.utils import (
    checksum,
    from_json_bytes,
    ids2str,
    intTime,
    joinFields,
    splitFields,
    to_json_bytes,
)

# public exports
Notetype = _pb.Notetype
NotetypeNameId = _pb.NotetypeNameId
NotetypeNameIdUseCount = _pb.NotetypeNameIdUseCount


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
        traceback.print_stack(file=sys.stdout)
        print("add-on should use methods on col.models, not col.models.models dict")

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


class ModelManager:
    # Saving/loading registry
    #############################################################

    def __init__(self, col: anki.collection.Collection) -> None:
        self.col = col.weakref()
        self.models = ModelsDictProxy(col)
        # do not access this directly!
        self._cache = {}

    def __repr__(self) -> str:
        d = dict(self.__dict__)
        del d["col"]
        return f"{super().__repr__()} {pprint.pformat(d, width=300)}"

    def save(
        self,
        m: NotetypeDict = None,
        # no longer used
        templates: bool = False,
        updateReqs: bool = True,
    ) -> None:
        "Save changes made to provided note type."
        if not m:
            print("col.models.save() should be passed the changed notetype")
            return

        self.update(m, preserve_usn=False)

    # legacy
    def flush(self) -> None:
        pass

    # Caching
    #############################################################
    # A lot of existing code expects to be able to quickly and
    # frequently obtain access to an entire notetype, so we currently
    # need to cache responses from the backend. Please do not
    # access the cache directly!

    _cache: Dict[NotetypeId, NotetypeDict] = {}

    def _update_cache(self, nt: NotetypeDict) -> None:
        self._cache[nt["id"]] = nt

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

    # legacy

    def allNames(self) -> List[str]:
        return [n.name for n in self.all_names_and_ids()]

    def ids(self) -> List[NotetypeId]:
        return [NotetypeId(n.id) for n in self.all_names_and_ids()]

    # only used by importing code
    def have(self, id: NotetypeId) -> bool:
        if isinstance(id, str):
            id = int(id)
        return any(True for e in self.all_names_and_ids() if e.id == id)

    # Current note type
    #############################################################

    def current(self, forDeck: bool = True) -> NotetypeDict:
        "Get current model."
        m = self.get(self.col.decks.current().get("mid"))
        if not forDeck or not m:
            m = self.get(self.col.conf["curModel"])
        if m:
            return m
        return self.get(NotetypeId(self.all_names_and_ids()[0].id))

    def setCurrent(self, m: NotetypeDict) -> None:
        """Legacy. The current notetype is now updated on note add."""
        self.col.set_config("curModel", m["id"])

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

        nt = self._get_cached(id)
        if not nt:
            try:
                nt = from_json_bytes(self.col._backend.get_notetype_legacy(id))
                self._update_cache(nt)
            except NotFoundError:
                return None
        return nt

    def all(self) -> List[NotetypeDict]:
        "Get all models."
        return [self.get(NotetypeId(nt.id)) for nt in self.all_names_and_ids()]

    def byName(self, name: str) -> Optional[NotetypeDict]:
        "Get model with NAME."
        id = self.id_for_name(name)
        if id:
            return self.get(id)
        else:
            return None

    def new(self, name: str) -> NotetypeDict:
        "Create a new model, and return it."
        # caller should call save() after modifying
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        nt["flds"] = []
        nt["tmpls"] = []
        nt["name"] = name
        return nt

    def rem(self, m: NotetypeDict) -> None:
        "Delete model, and all its cards/notes."
        self.remove(m["id"])

    def remove_all_notetypes(self) -> None:
        for nt in self.all_names_and_ids():
            self._remove_from_cache(NotetypeId(nt.id))
            self.col._backend.remove_notetype(nt.id)

    def remove(self, id: NotetypeId) -> OpChanges:
        "Modifies schema."
        self._remove_from_cache(id)
        return self.col._backend.remove_notetype(id)

    def add(self, m: NotetypeDict) -> OpChangesWithId:
        "Replaced with add_dict()"
        self.ensureNameUnique(m)
        out = self.col._backend.add_notetype_legacy(to_json_bytes(m))
        m["id"] = out.id
        self._mutate_after_write(m)
        return out

    def add_dict(self, m: NotetypeDict) -> OpChangesWithId:
        "Notetype needs to be fetched from DB after adding."
        self.ensureNameUnique(m)
        return self.col._backend.add_notetype_legacy(to_json_bytes(m))

    def ensureNameUnique(self, m: NotetypeDict) -> None:
        existing_id = self.id_for_name(m["name"])
        if existing_id is not None and existing_id != m["id"]:
            m["name"] += "-" + checksum(str(time.time()))[:5]

    def update(self, m: NotetypeDict, preserve_usn: bool = True) -> None:
        "Add or update an existing model. Use .update_dict() instead."
        self._remove_from_cache(m["id"])
        self.ensureNameUnique(m)
        m["id"] = self.col._backend.add_or_update_notetype(
            json=to_json_bytes(m), preserve_usn_and_mtime=preserve_usn
        )
        self.setCurrent(m)
        self._mutate_after_write(m)

    def update_dict(self, m: NotetypeDict) -> OpChanges:
        "Update a NotetypeDict. Caller will need to re-load notetype if new fields/cards added."
        self._remove_from_cache(m["id"])
        self.ensureNameUnique(m)
        return self.col._backend.update_notetype_legacy(to_json_bytes(m))

    def _mutate_after_write(self, nt: NotetypeDict) -> None:
        # existing code expects the note type to be mutated to reflect
        # the changes made when adding, such as ordinal assignment :-(
        updated = self.get(nt["id"])
        nt.update(updated)

    # Tools
    ##################################################

    def nids(self, ntid: NotetypeId) -> List[anki.notes.NoteId]:
        "Note ids for M."
        if isinstance(ntid, dict):
            # legacy callers passed in note type
            ntid = ntid["id"]
        return self.col.db.list("select id from notes where mid = ?", ntid)

    def useCount(self, m: NotetypeDict) -> int:
        "Number of note using M."
        return self.col.db.scalar("select count() from notes where mid = ?", m["id"])

    # Copying
    ##################################################

    def copy(self, m: NotetypeDict, add: bool = True) -> NotetypeDict:
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2["name"] = without_unicode_isolation(
            self.col.tr.notetypes_copy(val=m2["name"])
        )
        m2["id"] = 0
        if add:
            self.add(m2)
        return m2

    # Fields
    ##################################################

    def fieldMap(self, m: NotetypeDict) -> Dict[str, Tuple[int, FieldDict]]:
        "Mapping of field name -> (ord, field)."
        return {f["name"]: (f["ord"], f) for f in m["flds"]}

    def fieldNames(self, m: NotetypeDict) -> List[str]:
        return [f["name"] for f in m["flds"]]

    def sortIdx(self, m: NotetypeDict) -> int:
        return m["sortf"]

    # Adding & changing fields
    ##################################################

    def new_field(self, name: str) -> FieldDict:
        assert isinstance(name, str)
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        field = nt["flds"][0]
        field["name"] = name
        field["ord"] = None
        return field

    def add_field(self, m: NotetypeDict, field: FieldDict) -> None:
        "Modifies schema."
        m["flds"].append(field)

    def remove_field(self, m: NotetypeDict, field: FieldDict) -> None:
        "Modifies schema."
        m["flds"].remove(field)

    def reposition_field(self, m: NotetypeDict, field: FieldDict, idx: int) -> None:
        "Modifies schema."
        oldidx = m["flds"].index(field)
        if oldidx == idx:
            return

        m["flds"].remove(field)
        m["flds"].insert(idx, field)

    def rename_field(self, m: NotetypeDict, field: FieldDict, new_name: str) -> None:
        assert field in m["flds"]
        field["name"] = new_name

    def set_sort_index(self, nt: NotetypeDict, idx: int) -> None:
        "Modifies schema."
        assert 0 <= idx < len(nt["flds"])
        nt["sortf"] = idx

    # legacy

    newField = new_field

    def addField(self, m: NotetypeDict, field: FieldDict) -> None:
        self.add_field(m, field)
        if m["id"]:
            self.save(m)

    def remField(self, m: NotetypeDict, field: FieldDict) -> None:
        self.remove_field(m, field)
        self.save(m)

    def moveField(self, m: NotetypeDict, field: FieldDict, idx: int) -> None:
        self.reposition_field(m, field, idx)
        self.save(m)

    def renameField(self, m: NotetypeDict, field: FieldDict, newName: str) -> None:
        self.rename_field(m, field, newName)
        self.save(m)

    # Adding & changing templates
    ##################################################

    def new_template(self, name: str) -> TemplateDict:
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        template = nt["tmpls"][0]
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template["ord"] = None
        return template

    def add_template(self, m: NotetypeDict, template: TemplateDict) -> None:
        "Modifies schema."
        m["tmpls"].append(template)

    def remove_template(self, m: NotetypeDict, template: TemplateDict) -> None:
        "Modifies schema."
        assert len(m["tmpls"]) > 1
        m["tmpls"].remove(template)

    def reposition_template(
        self, m: NotetypeDict, template: TemplateDict, idx: int
    ) -> None:
        "Modifies schema."
        oldidx = m["tmpls"].index(template)
        if oldidx == idx:
            return

        m["tmpls"].remove(template)
        m["tmpls"].insert(idx, template)

    # legacy

    newTemplate = new_template

    def addTemplate(self, m: NotetypeDict, template: TemplateDict) -> None:
        self.add_template(m, template)
        if m["id"]:
            self.save(m)

    def remTemplate(self, m: NotetypeDict, template: TemplateDict) -> None:
        self.remove_template(m, template)
        self.save(m)

    def moveTemplate(self, m: NotetypeDict, template: TemplateDict, idx: int) -> None:
        self.reposition_template(m, template, idx)
        self.save(m)

    def template_use_count(self, ntid: NotetypeId, ord: int) -> int:
        return self.col.db.scalar(
            """
select count() from cards, notes where cards.nid = notes.id
and notes.mid = ? and cards.ord = ?""",
            ntid,
            ord,
        )

    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing

    def change(
        self,
        m: NotetypeDict,
        nids: List[anki.notes.NoteId],
        newModel: NotetypeDict,
        fmap: Optional[Dict[int, Union[None, int]]],
        cmap: Optional[Dict[int, Union[None, int]]],
    ) -> None:
        self.col.modSchema(check=True)
        assert newModel["id"] == m["id"] or (fmap and cmap)
        if fmap:
            self._changeNotes(nids, newModel, fmap)
        if cmap:
            self._changeCards(nids, m, newModel, cmap)
        self.col.after_note_updates(nids, mark_modified=True)

    def _changeNotes(
        self,
        nids: List[anki.notes.NoteId],
        newModel: NotetypeDict,
        map: Dict[int, Union[None, int]],
    ) -> None:
        d = []
        nfields = len(newModel["flds"])
        for (nid, flds) in self.col.db.execute(
            f"select id, flds from notes where id in {ids2str(nids)}"
        ):
            newflds = {}
            flds = splitFields(flds)
            for old, new in list(map.items()):
                newflds[new] = flds[old]
            flds = []
            for c in range(nfields):
                flds.append(newflds.get(c, ""))
            flds = joinFields(flds)
            d.append(
                (
                    flds,
                    newModel["id"],
                    intTime(),
                    self.col.usn(),
                    nid,
                )
            )
        self.col.db.executemany(
            "update notes set flds=?,mid=?,mod=?,usn=? where id = ?", d
        )

    def _changeCards(
        self,
        nids: List[anki.notes.NoteId],
        oldModel: NotetypeDict,
        newModel: NotetypeDict,
        map: Dict[int, Union[None, int]],
    ) -> None:
        d = []
        deleted = []
        for (cid, ord) in self.col.db.execute(
            f"select id, ord from cards where nid in {ids2str(nids)}"
        ):
            # if the src model is a cloze, we ignore the map, as the gui
            # doesn't currently support mapping them
            if oldModel["type"] == MODEL_CLOZE:
                new = ord
                if newModel["type"] != MODEL_CLOZE:
                    # if we're mapping to a regular note, we need to check if
                    # the destination ord is valid
                    if len(newModel["tmpls"]) <= ord:
                        new = None
            else:
                # mapping from a regular note, so the map should be valid
                new = map[ord]
            if new is not None:
                d.append((new, self.col.usn(), intTime(), cid))
            else:
                deleted.append(cid)
        self.col.db.executemany("update cards set ord=?,usn=?,mod=? where id=?", d)
        self.col.remove_cards_and_orphaned_notes(deleted)

    # Schema hash
    ##########################################################################

    def scmhash(self, m: NotetypeDict) -> str:
        "Return a hash of the schema, to see if models are compatible."
        s = ""
        for f in m["flds"]:
            s += f["name"]
        for t in m["tmpls"]:
            s += t["name"]
        return checksum(s)

    # Cloze
    ##########################################################################

    def _availClozeOrds(
        self, m: NotetypeDict, flds: str, allowEmpty: bool = True
    ) -> List[int]:
        print("_availClozeOrds() is deprecated; use note.cloze_numbers_in_fields()")
        note = _pb.Note(fields=[flds])
        return list(self.col._backend.cloze_numbers_in_note(note))
