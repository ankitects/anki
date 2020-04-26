# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import re
import time
from typing import Any, Dict, List, Optional, Tuple, Union

import anki  # pylint: disable=unused-import
import anki.backend_pb2 as pb
from anki import hooks
from anki.consts import *
from anki.lang import _
from anki.utils import checksum, ids2str, intTime, joinFields, splitFields

# types
NoteType = Dict[str, Any]
Field = Dict[str, Any]
Template = Dict[str, Union[str, int, None]]

# fixme: memory leaks
# fixme: syncing, beforeUpload

# Models
##########################################################################

# - careful not to add any lists/dicts/etc here, as they aren't deep copied

defaultModel: NoteType = {
    "id": 0,
    "sortf": 0,
    "did": 1,
    "latexPre": """\
\\documentclass[12pt]{article}
\\special{papersize=3in,5in}
\\usepackage[utf8]{inputenc}
\\usepackage{amssymb,amsmath}
\\pagestyle{empty}
\\setlength{\\parindent}{0in}
\\begin{document}
""",
    "latexPost": "\\end{document}",
    "mod": 0,
    "usn": 0,
    "req": [],
    "type": MODEL_STD,
    "css": """\
.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}
""",
}

defaultField: Field = {
    "name": "",
    "ord": None,
    "sticky": False,
    # the following alter editing, and are used as defaults for the
    # template wizard
    "rtl": False,
    "font": "Arial",
    "size": 20,
    # reserved for future use
    "media": [],
}

defaultTemplate: Template = {
    "name": "",
    "ord": None,
    "qfmt": "",
    "afmt": "",
    "did": None,
    "bqfmt": "",
    "bafmt": "",
    # we don't define these so that we pick up system font size until set
    #'bfont': "Arial",
    #'bsize': 12,
}


class ModelsDictProxy:
    def __init__(self, col: anki.storage._Collection):
        self._col = col.weakref()

    def _warn(self):
        print("add-on should use methods on col.models, not col.models.models dict")

    def __getitem__(self, item):
        self._warn()
        return self._col.models.get(int(item))

    def __setitem__(self, key, val):
        self._warn()
        self._col.models.save(val)

    def __len__(self):
        self._warn()
        return len(self._col.models.all_names_and_ids())

    def keys(self):
        self._warn()
        return [str(nt.id) for nt in self._col.models.all_names_and_ids()]

    def values(self):
        self._warn()
        return self._col.models.all()

    def items(self):
        self._warn()
        return [(str(nt["id"]), nt) for nt in self._col.models.all()]

    def __contains__(self, item):
        self._warn()
        self._col.models.have(item)


class ModelManager:
    # Saving/loading registry
    #############################################################

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col.weakref()
        self.models = ModelsDictProxy(col)
        # do not access this directly!
        self._cache = {}

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

        # fixme: badly named; also fires on updates
        hooks.note_type_added(m)

    # legacy
    def flush(self) -> None:
        pass

    # fixme: enforce at lower level
    def ensureNotEmpty(self) -> Optional[bool]:
        if not self.all_names_and_ids():
            from anki.stdmodels import addBasicModel

            addBasicModel(self.col)
            return True
        return None

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

    def _clear_cache(self):
        self._cache = {}

    # Listing note types
    #############################################################

    def all_names_and_ids(self) -> List[pb.NoteTypeNameID]:
        return self.col.backend.get_notetype_names_and_ids()

    def all_use_counts(self) -> List[pb.NoteTypeNameIDUseCount]:
        return self.col.backend.get_notetype_use_counts()

    def id_for_name(self, name: str) -> Optional[int]:
        return self.col.backend.get_notetype_id_by_name(name)

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

    def current(self, forDeck: bool = True) -> Any:
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

    def get(self, id: int) -> Optional[NoteType]:
        "Get model with ID, or None."
        # deal with various legacy input types
        if id is None:
            return None
        elif isinstance(id, str):
            id = int(id)

        nt = self._get_cached(id)
        if not nt:
            nt = self.col.backend.get_notetype_legacy(id)
            if nt:
                self._update_cache(nt)
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
        m = defaultModel.copy()
        m["name"] = name
        m["mod"] = intTime()
        m["flds"] = []
        m["tmpls"] = []
        m["tags"] = []
        m["id"] = 0
        return m

    def rem(self, m: NoteType) -> None:
        "Delete model, and all its cards/notes."
        self.remove(m["id"])

    def remove_all_notetypes(self):
        self.col.modSchema(check=True)
        for nt in self.all_names_and_ids():
            self._remove_from_cache(nt.id)
            self.col.backend.remove_notetype(nt.id)

    def remove(self, id: int) -> None:
        self.col.modSchema(check=True)
        self._remove_from_cache(id)
        was_current = self.current()["id"] == id
        self.col.backend.remove_notetype(id)

        # fixme: handle in backend
        if was_current:
            self.col.conf["curModel"] = self.all_names_and_ids()[0].id

    def add(self, m: NoteType) -> None:
        self.save(m)

    def ensureNameUnique(self, m: NoteType) -> None:
        existing_id = self.id_for_name(m["name"])
        if existing_id is not None and existing_id != m["id"]:
            m["name"] += "-" + checksum(str(time.time()))[:5]

    def update(self, m: NoteType, preserve_usn=True) -> None:
        "Add or update an existing model. Use .save() instead."
        self._remove_from_cache(m["id"])
        self.ensureNameUnique(m)
        self.col.backend.add_or_update_notetype(m, preserve_usn=preserve_usn)
        self.setCurrent(m)
        self._mutate_after_write(m)

    def _mutate_after_write(self, nt: NoteType) -> None:
        # existing code expects the note type to be mutated to reflect
        # the changes made when adding, such as ordinal assignment :-(
        updated = self.get(nt["id"])
        nt.update(updated)

    # Tools
    ##################################################

    def nids(self, m: NoteType) -> Any:
        "Note ids for M."
        return self.col.db.list("select id from notes where mid = ?", m["id"])

    def useCount(self, m: NoteType) -> Any:
        "Number of note using M."
        print("useCount() is slow; prefer all_use_counts()")
        return self.col.db.scalar("select count() from notes where mid = ?", m["id"])

    # Copying
    ##################################################

    def copy(self, m: NoteType) -> Any:
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2["name"] = _("%s copy") % m2["name"]
        m2["id"] = 0
        self.add(m2)
        return m2

    # Fields
    ##################################################

    def fieldMap(self, m: NoteType) -> Dict[str, Tuple[int, Field]]:
        "Mapping of field name -> (ord, field)."
        return dict((f["name"], (f["ord"], f)) for f in m["flds"])

    def fieldNames(self, m: NoteType) -> List[str]:
        return [f["name"] for f in m["flds"]]

    def sortIdx(self, m: NoteType) -> Any:
        return m["sortf"]

    def setSortIdx(self, m: NoteType, idx: int) -> None:
        assert 0 <= idx < len(m["flds"])
        self.col.modSchema(check=True)

        m["sortf"] = idx

        self.save(m)

    # Adding & changing fields
    ##################################################

    def newField(self, name: str) -> Field:
        assert isinstance(name, str)
        f = defaultField.copy()
        f["name"] = name
        return f

    def addField(self, m: NoteType, field: Field) -> None:
        if m["id"]:
            self.col.modSchema(check=True)

        m["flds"].append(field)

        if m["id"]:
            self.save(m)

    def remField(self, m: NoteType, field: Field) -> None:
        self.col.modSchema(check=True)

        m["flds"].remove(field)

        self.save(m)

    def moveField(self, m: NoteType, field: Field, idx: int) -> None:
        self.col.modSchema(check=True)
        oldidx = m["flds"].index(field)
        if oldidx == idx:
            return

        m["flds"].remove(field)
        m["flds"].insert(idx, field)

        self.save(m)

    def renameField(self, m: NoteType, field: Field, newName: str) -> None:
        assert field in m["flds"]

        field["name"] = newName

        self.save(m)

    # Adding & changing templates
    ##################################################

    def newTemplate(self, name: str) -> Template:
        t = defaultTemplate.copy()
        t["name"] = name
        return t

    def addTemplate(self, m: NoteType, template: Template) -> None:
        if m["id"]:
            self.col.modSchema(check=True)

        m["tmpls"].append(template)

        if m["id"]:
            self.save(m)

    def remTemplate(self, m: NoteType, template: Template) -> None:
        assert len(m["tmpls"]) > 1
        self.col.modSchema(check=True)

        m["tmpls"].remove(template)

        self.save(m)

    def moveTemplate(self, m: NoteType, template: Template, idx: int) -> None:
        self.col.modSchema(check=True)

        oldidx = m["tmpls"].index(template)
        if oldidx == idx:
            return

        m["tmpls"].remove(template)
        m["tmpls"].insert(idx, template)

        self.save(m)

    # Model changing
    ##########################################################################
    # - maps are ord->ord, and there should not be duplicate targets
    # - newModel should be self if model is not changing

    def change(
        self, m: NoteType, nids: List[int], newModel: NoteType, fmap: Any, cmap: Any
    ) -> None:
        self.col.modSchema(check=True)
        assert newModel["id"] == m["id"] or (fmap and cmap)
        if fmap:
            self._changeNotes(nids, newModel, fmap)
        if cmap:
            self._changeCards(nids, m, newModel, cmap)
        self.col.genCards(nids)

    def _changeNotes(
        self, nids: List[int], newModel: NoteType, map: Dict[int, Union[None, int]]
    ) -> None:
        d = []
        nfields = len(newModel["flds"])
        for (nid, flds) in self.col.db.execute(
            "select id, flds from notes where id in " + ids2str(nids)
        ):
            newflds = {}
            flds = splitFields(flds)
            for old, new in list(map.items()):
                newflds[new] = flds[old]
            flds = []
            for c in range(nfields):
                flds.append(newflds.get(c, ""))
            flds = joinFields(flds)
            d.append((flds, newModel["id"], intTime(), self.col.usn(), nid,))
        self.col.db.executemany(
            "update notes set flds=?,mid=?,mod=?,usn=? where id = ?", d
        )
        self.col.updateFieldCache(nids)

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
            "select id, ord from cards where nid in " + ids2str(nids)
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
        self.col.remCards(deleted)

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

    # Required field/text cache
    ##########################################################################

    # fixme: genCards(), clayout, importing, cards.isEmpty
    def availOrds(self, m: NoteType, flds: str) -> List:
        "Given a joined field string, return available template ordinals."
        if m["type"] == MODEL_CLOZE:
            return self._availClozeOrds(m, flds)
        fields = {}
        for c, f in enumerate(splitFields(flds)):
            fields[c] = f.strip()
        avail = []
        for ord, type, req in m["req"]:
            # unsatisfiable template
            if type == "none":
                continue
            # AND requirement?
            elif type == "all":
                ok = True
                for idx in req:
                    if not fields[idx]:
                        # missing and was required
                        ok = False
                        break
                if not ok:
                    continue
            # OR requirement?
            elif type == "any":
                ok = False
                for idx in req:
                    if fields[idx]:
                        ok = True
                        break
                if not ok:
                    continue
            avail.append(ord)
        return avail

    def _availClozeOrds(self, m: NoteType, flds: str, allowEmpty: bool = True) -> List:
        sflds = splitFields(flds)
        map = self.fieldMap(m)
        ords = set()
        matches = re.findall("{{[^}]*?cloze:(?:[^}]?:)*(.+?)}}", m["tmpls"][0]["qfmt"])
        matches += re.findall("<%cloze:(.+?)%>", m["tmpls"][0]["qfmt"])
        for fname in matches:
            if fname not in map:
                continue
            ord = map[fname][0]
            ords.update(
                [int(m) - 1 for m in re.findall(r"(?s){{c(\d+)::.+?}}", sflds[ord])]
            )
        if -1 in ords:
            ords.remove(-1)
        if not ords and allowEmpty:
            # empty clozes use first ord
            return [0]
        return list(ords)

    # Sync handling
    ##########################################################################

    def beforeUpload(self) -> None:
        for m in self.all():
            m["usn"] = 0
        self.save()
