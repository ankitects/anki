# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import copy
import re
import time
from typing import Any, Callable, Dict, List, Optional, Tuple, Union

import anki  # pylint: disable=unused-import
from anki import hooks
from anki.consts import *
from anki.lang import _
from anki.utils import checksum, ids2str, intTime, joinFields, splitFields

# types
NoteType = Dict[str, Any]
Field = Dict[str, Any]
Template = Dict[str, Union[str, int, None]]
TemplateRequirementType = str  # Union["all", "any", "none"]
# template ordinal, type, list of field ordinals
TemplateRequiredFieldOrds = Tuple[int, TemplateRequirementType, List[int]]
AllTemplateReqs = List[TemplateRequiredFieldOrds]

# Models
##########################################################################

# - careful not to add any lists/dicts/etc here, as they aren't deep copied

defaultModel: NoteType = {
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
    "vers": [],  # FIXME: remove when other clients have caught up
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


class ModelManager:
    models: Dict[str, NoteType]

    # Saving/loading registry
    #############################################################

    def __init__(self, col: anki.storage._Collection) -> None:
        self.col = col.weakref()
        self.models = {}
        self.changed = False

    def save(
        self,
        m: Optional[NoteType] = None,
        templates: bool = False,
        updateReqs: bool = True,
    ) -> None:
        "Mark M modified if provided, and schedule registry flush."
        if m and m["id"]:
            m["mod"] = intTime()
            m["usn"] = self.col.usn()
            if updateReqs:
                self._updateRequired(m)
            if templates:
                self._syncTemplates(m)
        self.changed = True
        hooks.note_type_added(m)

    def flush(self) -> None:
        "Flush the registry if any models were changed."
        if self.changed:
            self.ensureNotEmpty()
            self.col.backend.set_all_notetypes(self.models)
            self.changed = False

    def ensureNotEmpty(self) -> Optional[bool]:
        if not self.models:
            from anki.stdmodels import addBasicModel

            addBasicModel(self.col)
            return True
        return None

    # Retrieving and creating models
    #############################################################

    def current(self, forDeck: bool = True) -> Any:
        "Get current model."
        m = self.get(self.col.decks.current().get("mid"))
        if not forDeck or not m:
            m = self.get(self.col.conf["curModel"])
        return m or list(self.models.values())[0]

    def setCurrent(self, m: NoteType) -> None:
        self.col.conf["curModel"] = m["id"]
        self.col.setMod()

    def get(self, id: Any) -> Any:
        "Get model with ID, or None."
        id = str(id)
        if id in self.models:
            return self.models[id]

    def all(self) -> List:
        "Get all models."
        return list(self.models.values())

    def allNames(self) -> List:
        return [m["name"] for m in self.all()]

    def byName(self, name: str) -> Any:
        "Get model with NAME."
        for m in list(self.models.values()):
            if m["name"] == name:
                return m

    def new(self, name: str) -> NoteType:
        "Create a new model, save it in the registry, and return it."
        # caller should call save() after modifying
        m = defaultModel.copy()
        m["name"] = name
        m["mod"] = intTime()
        m["flds"] = []
        m["tmpls"] = []
        m["tags"] = []
        m["id"] = None
        return m

    def rem(self, m: NoteType) -> None:
        "Delete model, and all its cards/notes."
        self.col.modSchema(check=True)
        current = self.current()["id"] == m["id"]
        # delete notes/cards
        self.col.remCards(
            self.col.db.list(
                """
select id from cards where nid in (select id from notes where mid = ?)""",
                m["id"],
            )
        )
        # then the model
        del self.models[str(m["id"])]
        self.save()
        # GUI should ensure last model is not deleted
        if current:
            self.setCurrent(list(self.models.values())[0])

    def add(self, m: NoteType) -> None:
        self._setID(m)
        self.update(m)
        self.setCurrent(m)
        self.save(m)

    def ensureNameUnique(self, m: NoteType) -> None:
        for mcur in self.all():
            if mcur["name"] == m["name"] and mcur["id"] != m["id"]:
                m["name"] += "-" + checksum(str(time.time()))[:5]
                break

    def update(self, m: NoteType) -> None:
        "Add or update an existing model. Used for syncing and merging."
        self.ensureNameUnique(m)
        self.models[str(m["id"])] = m
        # mark registry changed, but don't bump mod time
        self.save()

    def _setID(self, m: NoteType) -> None:
        while 1:
            id = str(intTime(1000))
            if id not in self.models:
                break
        m["id"] = id

    def have(self, id: int) -> bool:
        return str(id) in self.models

    def ids(self) -> List[str]:
        return list(self.models.keys())

    # Tools
    ##################################################

    def nids(self, m: NoteType) -> Any:
        "Note ids for M."
        return self.col.db.list("select id from notes where mid = ?", m["id"])

    def useCount(self, m: NoteType) -> Any:
        "Number of note using M."
        return self.col.db.scalar("select count() from notes where mid = ?", m["id"])

    def tmplUseCount(self, m: NoteType, ord) -> Any:
        return self.col.db.scalar(
            """
select count() from cards, notes where cards.nid = notes.id
and notes.mid = ? and cards.ord = ?""",
            m["id"],
            ord,
        )

    # Copying
    ##################################################

    def copy(self, m: NoteType) -> Any:
        "Copy, save and return."
        m2 = copy.deepcopy(m)
        m2["name"] = _("%s copy") % m2["name"]
        self.add(m2)
        return m2

    # Fields
    ##################################################

    def newField(self, name: str) -> Field:
        assert isinstance(name, str)
        f = defaultField.copy()
        f["name"] = name
        return f

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
        self.col.updateFieldCache(self.nids(m))
        self.save(m, updateReqs=False)

    def addField(self, m: NoteType, field: Field) -> None:
        # only mod schema if model isn't new
        if m["id"]:
            self.col.modSchema(check=True)
        m["flds"].append(field)
        self._updateFieldOrds(m)
        self.save(m)

        def add(fields):
            fields.append("")
            return fields

        self._transformFields(m, add)

    def remField(self, m: NoteType, field: Field) -> None:
        self.col.modSchema(check=True)
        # save old sort field
        sortFldName = m["flds"][m["sortf"]]["name"]
        idx = m["flds"].index(field)
        m["flds"].remove(field)
        # restore old sort field if possible, or revert to first field
        m["sortf"] = 0
        for c, f in enumerate(m["flds"]):
            if f["name"] == sortFldName:
                m["sortf"] = c
                break
        self._updateFieldOrds(m)

        def delete(fields):
            del fields[idx]
            return fields

        self._transformFields(m, delete)
        if m["flds"][m["sortf"]]["name"] != sortFldName:
            # need to rebuild sort field
            self.col.updateFieldCache(self.nids(m))
        # saves
        self.renameField(m, field, None)

    def moveField(self, m: NoteType, field: Field, idx: int) -> None:
        self.col.modSchema(check=True)
        oldidx = m["flds"].index(field)
        if oldidx == idx:
            return
        # remember old sort field
        sortf = m["flds"][m["sortf"]]
        # move
        m["flds"].remove(field)
        m["flds"].insert(idx, field)
        # restore sort field
        m["sortf"] = m["flds"].index(sortf)
        self._updateFieldOrds(m)
        self.save(m, updateReqs=False)

        def move(fields, oldidx=oldidx):
            val = fields[oldidx]
            del fields[oldidx]
            fields.insert(idx, val)
            return fields

        self._transformFields(m, move)

    def renameField(self, m: NoteType, field: Field, newName: Optional[str]) -> None:
        self.col.modSchema(check=True)
        if newName is not None:
            newName = newName.replace(":", "")
        pat = r"{{([^{}]*)([:#^/]|[^:#/^}][^:}]*?:|)%s}}"

        def wrap(txt):
            def repl(match):
                return "{{" + match.group(1) + match.group(2) + txt + "}}"

            return repl

        for t in m["tmpls"]:
            for fmt in ("qfmt", "afmt"):
                if newName:
                    t[fmt] = re.sub(
                        pat % re.escape(field["name"]), wrap(newName), t[fmt]
                    )
                else:
                    t[fmt] = re.sub(pat % re.escape(field["name"]), "", t[fmt])
        field["name"] = newName
        self.save(m)

    def _updateFieldOrds(self, m: NoteType) -> None:
        for c, f in enumerate(m["flds"]):
            f["ord"] = c

    def _transformFields(self, m: NoteType, fn: Callable) -> None:
        # model hasn't been added yet?
        if not m["id"]:
            return
        r = []
        for (id, flds) in self.col.db.execute(
            "select id, flds from notes where mid = ?", m["id"]
        ):
            r.append((joinFields(fn(splitFields(flds))), intTime(), self.col.usn(), id))
        self.col.db.executemany("update notes set flds=?,mod=?,usn=? where id = ?", r)

    # Templates
    ##################################################

    def newTemplate(self, name: str) -> Template:
        t = defaultTemplate.copy()
        t["name"] = name
        return t

    def addTemplate(self, m: NoteType, template: Template) -> None:
        "Note: should col.genCards() afterwards."
        if m["id"]:
            self.col.modSchema(check=True)
        m["tmpls"].append(template)
        self._updateTemplOrds(m)
        self.save(m)

    def remTemplate(self, m: NoteType, template: Template) -> bool:
        "False if removing template would leave orphan notes."
        assert len(m["tmpls"]) > 1
        # find cards using this template
        ord = m["tmpls"].index(template)
        cids = self.col.db.list(
            """
select c.id from cards c, notes f where c.nid=f.id and mid = ? and ord = ?""",
            m["id"],
            ord,
        )
        # all notes with this template must have at least two cards, or we
        # could end up creating orphaned notes
        if self.col.db.scalar(
            """
select nid, count() from cards where
nid in (select nid from cards where id in %s)
group by nid
having count() < 2
limit 1"""
            % ids2str(cids)
        ):
            return False
        # ok to proceed; remove cards
        self.col.modSchema(check=True)
        self.col.remCards(cids)
        # shift ordinals
        self.col.db.execute(
            """
update cards set ord = ord - 1, usn = ?, mod = ?
 where nid in (select id from notes where mid = ?) and ord > ?""",
            self.col.usn(),
            intTime(),
            m["id"],
            ord,
        )
        m["tmpls"].remove(template)
        self._updateTemplOrds(m)
        self.save(m)
        return True

    def _updateTemplOrds(self, m: NoteType) -> None:
        for c, t in enumerate(m["tmpls"]):
            t["ord"] = c

    def moveTemplate(self, m: NoteType, template: Template, idx: int) -> None:
        oldidx = m["tmpls"].index(template)
        if oldidx == idx:
            return
        oldidxs = dict((id(t), t["ord"]) for t in m["tmpls"])
        m["tmpls"].remove(template)
        m["tmpls"].insert(idx, template)
        self._updateTemplOrds(m)
        # generate change map
        map = []
        for t in m["tmpls"]:
            map.append("when ord = %d then %d" % (oldidxs[id(t)], t["ord"]))
        # apply
        self.save(m, updateReqs=False)
        self.col.db.execute(
            """
update cards set ord = (case %s end),usn=?,mod=? where nid in (
select id from notes where mid = ?)"""
            % " ".join(map),
            self.col.usn(),
            intTime(),
            m["id"],
        )

    def _syncTemplates(self, m: NoteType) -> None:
        rem = self.col.genCards(self.nids(m))

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

    def _updateRequired(self, m: NoteType) -> None:
        fronts = [t["qfmt"] for t in m["tmpls"]]
        field_map = {}
        for (idx, fld) in enumerate(m["flds"]):
            field_map[fld["name"]] = idx
        reqs = self.col.backend.template_requirements(fronts, field_map)
        m["req"] = [list(l) for l in reqs]

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
