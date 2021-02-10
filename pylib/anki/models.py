# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import pprint
import sys
import time
import traceback
from typing import Any, Dict, List, Optional, Sequence, Tuple, Union

import anki  # pylint: disable=unused-import
import anki._backend.backend_pb2 as _pb
from anki.consts import *
from anki.errors import NotFoundError
from anki.lang import TR, without_unicode_isolation
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
NoteTypeNameID = _pb.NoteTypeNameID
NoteTypeNameIDUseCount = _pb.NoteTypeNameIDUseCount


# types
NoteType = Dict[str, Any]
Field = Dict[str, Any]
Template = Dict[str, Union[str, int, None]]


class ModelsDictProxy:
    def __init__(self, col: anki.collection.Collection):
        self._col = col.weakref()

    def _warn(self) -> None:
        traceback.print_stack(file=sys.stdout)
        print("add-on should use methods on col.models, not col.models.models dict")

    def __getitem__(self, item: Any) -> Any:
        self._warn()
        return self._col.models.get(int(item))

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
        m: NoteType = None,
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

    _cache: Dict[int, NoteType] = {}

    def _update_cache(self, nt: NoteType) -> None:
        self._cache[nt["id"]] = nt

    def _remove_from_cache(self, ntid: int) -> None:
        if ntid in self._cache:
            del self._cache[ntid]

    def _get_cached(self, ntid: int) -> Optional[NoteType]:
        return self._cache.get(ntid)

    def _clear_cache(self) -> None:
        self._cache = {}

    # Listing note types
    #############################################################

    def all_names_and_ids(self) -> Sequence[NoteTypeNameID]:
        return self.col._backend.get_notetype_names()

    def all_use_counts(self) -> Sequence[NoteTypeNameIDUseCount]:
        return self.col._backend.get_notetype_names_and_counts()

    # legacy

    def allNames(self) -> List[str]:
        return [n.name for n in self.all_names_and_ids()]

    def ids(self) -> List[int]:
        return [n.id for n in self.all_names_and_ids()]

    # only used by importing code
    def have(self, id: int) -> bool:
        if isinstance(id, str):
            id = int(id)
        return any(True for e in self.all_names_and_ids() if e.id == id)

    # Current note type
    #############################################################

    def current(self, forDeck: bool = True) -> NoteType:
        "Get current model."
        m = self.get(self.col.decks.current().get("mid"))
        if not forDeck or not m:
            m = self.get(self.col.conf["curModel"])
        if m:
            return m
        return self.get(self.all_names_and_ids()[0].id)

    def setCurrent(self, m: NoteType) -> None:
        self.col.conf["curModel"] = m["id"]
        self.col.setMod()

    # Retrieving and creating models
    #############################################################

    def id_for_name(self, name: str) -> Optional[int]:
        try:
            return self.col._backend.get_notetype_id_by_name(name)
        except NotFoundError:
            return None

    def get(self, id: int) -> Optional[NoteType]:
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

    def all(self) -> List[NoteType]:
        "Get all models."
        return [self.get(nt.id) for nt in self.all_names_and_ids()]

    def byName(self, name: str) -> Optional[NoteType]:
        "Get model with NAME."
        id = self.id_for_name(name)
        if id:
            return self.get(id)
        else:
            return None

    def new(self, name: str) -> NoteType:
        "Create a new model, and return it."
        # caller should call save() after modifying
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        nt["flds"] = []
        nt["tmpls"] = []
        nt["name"] = name
        return nt

    def rem(self, m: NoteType) -> None:
        "Delete model, and all its cards/notes."
        self.remove(m["id"])

    def remove_all_notetypes(self) -> None:
        for nt in self.all_names_and_ids():
            self._remove_from_cache(nt.id)
            self.col._backend.remove_notetype(nt.id)

    def remove(self, id: int) -> None:
        "Modifies schema."
        self._remove_from_cache(id)
        self.col._backend.remove_notetype(id)

    def add(self, m: NoteType) -> None:
        self.save(m)

    def ensureNameUnique(self, m: NoteType) -> None:
        existing_id = self.id_for_name(m["name"])
        if existing_id is not None and existing_id != m["id"]:
            m["name"] += "-" + checksum(str(time.time()))[:5]

    def update(self, m: NoteType, preserve_usn: bool = True) -> None:
        "Add or update an existing model. Use .save() instead."
        self._remove_from_cache(m["id"])
        self.ensureNameUnique(m)
        m["id"] = self.col._backend.add_or_update_notetype(
            json=to_json_bytes(m), preserve_usn_and_mtime=preserve_usn
        )
        self.setCurrent(m)
        self._mutate_after_write(m)

    def _mutate_after_write(self, nt: NoteType) -> None:
        # existing code expects the note type to be mutated to reflect
        # the changes made when adding, such as ordinal assignment :-(
        updated = self.get(nt["id"])
        nt.update(updated)

    # Tools
    ##################################################

    def nids(self, ntid: int) -> List[int]:
        "Note ids for M."
        if isinstance(ntid, dict):
            # legacy callers passed in note type
            ntid = ntid["id"]
        return self.col.db.list("select id from notes where mid = ?", ntid)

    def useCount(self, m: NoteType) -> int:
        "Number of note using M."
        return self.col.db.scalar("select count() from notes where mid = ?", m["id"])

    # Copying
    ##################################################

    def copy(self, m: NoteType) -> NoteType:
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2["name"] = without_unicode_isolation(
            self.col.tr(TR.NOTETYPES_COPY, val=m2["name"])
        )
        m2["id"] = 0
        self.add(m2)
        return m2

    # Fields
    ##################################################

    def fieldMap(self, m: NoteType) -> Dict[str, Tuple[int, Field]]:
        "Mapping of field name -> (ord, field)."
        return {f["name"]: (f["ord"], f) for f in m["flds"]}

    def fieldNames(self, m: NoteType) -> List[str]:
        return [f["name"] for f in m["flds"]]

    def sortIdx(self, m: NoteType) -> int:
        return m["sortf"]

    # Adding & changing fields
    ##################################################

    def new_field(self, name: str) -> Field:
        assert isinstance(name, str)
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        field = nt["flds"][0]
        field["name"] = name
        field["ord"] = None
        return field

    def add_field(self, m: NoteType, field: Field) -> None:
        "Modifies schema."
        m["flds"].append(field)

    def remove_field(self, m: NoteType, field: Field) -> None:
        "Modifies schema."
        m["flds"].remove(field)

    def reposition_field(self, m: NoteType, field: Field, idx: int) -> None:
        "Modifies schema."
        oldidx = m["flds"].index(field)
        if oldidx == idx:
            return

        m["flds"].remove(field)
        m["flds"].insert(idx, field)

    def rename_field(self, m: NoteType, field: Field, new_name: str) -> None:
        assert field in m["flds"]
        field["name"] = new_name

    def set_sort_index(self, nt: NoteType, idx: int) -> None:
        "Modifies schema."
        assert 0 <= idx < len(nt["flds"])
        nt["sortf"] = idx

    # legacy

    newField = new_field

    def addField(self, m: NoteType, field: Field) -> None:
        self.add_field(m, field)
        if m["id"]:
            self.save(m)

    def remField(self, m: NoteType, field: Field) -> None:
        self.remove_field(m, field)
        self.save(m)

    def moveField(self, m: NoteType, field: Field, idx: int) -> None:
        self.reposition_field(m, field, idx)
        self.save(m)

    def renameField(self, m: NoteType, field: Field, newName: str) -> None:
        self.rename_field(m, field, newName)
        self.save(m)

    # Adding & changing templates
    ##################################################

    def new_template(self, name: str) -> Template:
        nt = from_json_bytes(
            self.col._backend.get_stock_notetype_legacy(StockNotetypeKind.BASIC)
        )
        template = nt["tmpls"][0]
        template["name"] = name
        template["qfmt"] = ""
        template["afmt"] = ""
        template["ord"] = None
        return template

    def add_template(self, m: NoteType, template: Template) -> None:
        "Modifies schema."
        m["tmpls"].append(template)

    def remove_template(self, m: NoteType, template: Template) -> None:
        "Modifies schema."
        assert len(m["tmpls"]) > 1
        m["tmpls"].remove(template)

    def reposition_template(self, m: NoteType, template: Template, idx: int) -> None:
        "Modifies schema."
        oldidx = m["tmpls"].index(template)
        if oldidx == idx:
            return

        m["tmpls"].remove(template)
        m["tmpls"].insert(idx, template)

    # legacy

    newTemplate = new_template

    def addTemplate(self, m: NoteType, template: Template) -> None:
        self.add_template(m, template)
        if m["id"]:
            self.save(m)

    def remTemplate(self, m: NoteType, template: Template) -> None:
        self.remove_template(m, template)
        self.save(m)

    def moveTemplate(self, m: NoteType, template: Template, idx: int) -> None:
        self.reposition_template(m, template, idx)
        self.save(m)

    def template_use_count(self, ntid: int, ord: int) -> int:
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
        m: NoteType,
        nids: List[int],
        newModel: NoteType,
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
        self, nids: List[int], newModel: NoteType, map: Dict[int, Union[None, int]]
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
        nids: List[int],
        oldModel: NoteType,
        newModel: NoteType,
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

    def scmhash(self, m: NoteType) -> str:
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
        self, m: NoteType, flds: str, allowEmpty: bool = True
    ) -> List[int]:
        print("_availClozeOrds() is deprecated; use note.cloze_numbers_in_fields()")
        note = _pb.Note(fields=[flds])
        return list(self.col._backend.cloze_numbers_in_note(note))
