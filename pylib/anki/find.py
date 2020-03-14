# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import re
import sre_constants
import unicodedata
from typing import TYPE_CHECKING, Any, List, Optional, Set, Tuple, Union, cast

from anki import hooks
from anki.consts import *
from anki.hooks import *
from anki.utils import (
    fieldChecksum,
    ids2str,
    intTime,
    joinFields,
    splitFields,
    stripHTMLMedia,
)

if TYPE_CHECKING:
    from anki.collection import _Collection

# Find
##########################################################################


class Finder:
    def __init__(self, col: Optional[_Collection]) -> None:
        self.col = col.weakref()
        self.search = dict(
            added=self._findAdded,
            card=self._findTemplate,
            deck=self._findDeck,
            mid=self._findMid,
            nid=self._findNids,
            cid=self._findCids,
            note=self._findModel,
            prop=self._findProp,
            rated=self._findRated,
            tag=self._findTag,
            dupe=self._findDupes,
            flag=self._findFlag,
        )
        self.search["is"] = self._findCardState
        hooks.search_terms_prepared(self.search)

    def findCards(self, query: str, order: Union[bool, str] = False) -> List[Any]:
        "Return a list of card ids for QUERY."
        tokens = self._tokenize(query)
        preds, args = self._where(tokens)
        if preds is None:
            raise Exception("invalidSearch")
        order, rev = self._order(order)
        sql = self._query(preds, order)
        try:
            res = self.col.db.list(sql, *args)
        except:
            # invalid grouping
            return []
        if rev:
            res.reverse()
        return res

    def findNotes(self, query: str) -> List[Any]:
        tokens = self._tokenize(query)
        preds, args = self._where(tokens)
        if preds is None:
            return []
        if preds:
            preds = "(" + preds + ")"
        else:
            preds = "1"
        sql = (
            """
select distinct(n.id) from cards c, notes n where c.nid=n.id and """
            + preds
        )
        try:
            res = self.col.db.list(sql, *args)
        except:
            # invalid grouping
            return []
        return res

    # Tokenizing
    ######################################################################

    def _tokenize(self, query: str) -> List[str]:
        inQuote: Union[bool, str] = False
        tokens = []
        token = ""
        for c in query:
            # quoted text
            if c in ("'", '"'):
                if inQuote:
                    if c == inQuote:
                        inQuote = False
                    else:
                        token += c
                elif token:
                    # quotes are allowed to start directly after a :
                    if token[-1] == ":":
                        inQuote = c
                    else:
                        token += c
                else:
                    inQuote = c
            # separator (space and ideographic space)
            elif c in (" ", "\u3000"):
                if inQuote:
                    token += c
                elif token:
                    # space marks token finished
                    tokens.append(token)
                    token = ""
            # nesting
            elif c in ("(", ")"):
                if inQuote:
                    token += c
                else:
                    if c == ")" and token:
                        tokens.append(token)
                        token = ""
                    tokens.append(c)
            # negation
            elif c == "-":
                if token:
                    token += c
                elif not tokens or tokens[-1] != "-":
                    tokens.append("-")
            # normal character
            else:
                token += c
        # if we finished in a token, add it
        if token:
            tokens.append(token)
        return tokens

    # Query building
    ######################################################################

    def _where(self, tokens: List[str]) -> Tuple[str, Optional[List[str]]]:
        # state and query
        s: Dict[str, Any] = dict(isnot=False, isor=False, join=False, q="", bad=False)
        args: List[Any] = []

        def add(txt, wrap=True):
            # failed command?
            if not txt:
                # if it was to be negated then we can just ignore it
                if s["isnot"]:
                    s["isnot"] = False
                    return None, None
                else:
                    s["bad"] = True
                    return None, None
            elif txt == "skip":
                return None, None
            # do we need a conjunction?
            if s["join"]:
                if s["isor"]:
                    s["q"] += " or "
                    s["isor"] = False
                else:
                    s["q"] += " and "
            if s["isnot"]:
                s["q"] += " not "
                s["isnot"] = False
            if wrap:
                txt = "(" + txt + ")"
            s["q"] += txt
            s["join"] = True

        for token in tokens:
            if s["bad"]:
                return None, None
            # special tokens
            if token == "-":
                s["isnot"] = True
            elif token.lower() == "or":
                s["isor"] = True
            elif token == "(":
                add(token, wrap=False)
                s["join"] = False
            elif token == ")":
                s["q"] += ")"
            # commands
            elif ":" in token:
                cmd, val = token.split(":", 1)
                cmd = cmd.lower()
                if cmd in self.search:
                    add(self.search[cmd]((val, args)))
                else:
                    add(self._findField(cmd, val))
            # normal text search
            else:
                add(self._findText(token, args))
        if s["bad"]:
            return None, None
        return s["q"], args

    def _query(self, preds: str, order: str) -> str:
        # can we skip the note table?
        if "n." not in preds and "n." not in order:
            sql = "select c.id from cards c where "
        else:
            sql = "select c.id from cards c, notes n where c.nid=n.id and "
        # combine with preds
        if preds:
            sql += "(" + preds + ")"
        else:
            sql += "1"
        # order
        if order:
            sql += " " + order
        return sql

    # Ordering
    ######################################################################

    def _order(self, order: Union[bool, str]) -> Tuple[str, bool]:
        if not order:
            return "", False
        elif order is not True:
            # custom order string provided
            return " order by " + cast(str, order), False
        # use deck default
        type = self.col.conf["sortType"]
        sort = None
        if type.startswith("note"):
            if type == "noteCrt":
                sort = "n.id, c.ord"
            elif type == "noteMod":
                sort = "n.mod, c.ord"
            elif type == "noteFld":
                sort = "n.sfld collate nocase, c.ord"
        elif type.startswith("card"):
            if type == "cardMod":
                sort = "c.mod"
            elif type == "cardReps":
                sort = "c.reps"
            elif type == "cardDue":
                sort = "c.type, c.due"
            elif type == "cardEase":
                sort = f"c.type == {CARD_TYPE_NEW}, c.factor"
            elif type == "cardLapses":
                sort = "c.lapses"
            elif type == "cardIvl":
                sort = "c.ivl"
        if not sort:
            # deck has invalid sort order; revert to noteCrt
            sort = "n.id, c.ord"
        return " order by " + sort, self.col.conf["sortBackwards"]

    # Commands
    ######################################################################

    def _findTag(self, args: Tuple[str, List[Any]]) -> str:
        (val, list_args) = args
        if val == "none":
            return 'n.tags = ""'
        val = val.replace("*", "%")
        if not val.startswith("%"):
            val = "% " + val
        if not val.endswith("%") or val.endswith("\\%"):
            val += " %"
        list_args.append(val)
        return "n.tags like ? escape '\\'"

    def _findCardState(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        (val, __) = args
        if val in ("review", "new", "learn"):
            if val == "review":
                n = 2
            elif val == "new":
                n = CARD_TYPE_NEW
            else:
                return f"queue in ({QUEUE_TYPE_LRN}, {QUEUE_TYPE_DAY_LEARN_RELEARN})"
            return "type = %d" % n
        elif val == "suspended":
            return "c.queue = -1"
        elif val == "buried":
            return f"c.queue in ({QUEUE_TYPE_SIBLING_BURIED}, {QUEUE_TYPE_MANUALLY_BURIED})"
        elif val == "due":
            return f"""
(c.queue in ({QUEUE_TYPE_REV},{QUEUE_TYPE_DAY_LEARN_RELEARN}) and c.due <= %d) or
(c.queue = {QUEUE_TYPE_LRN} and c.due <= %d)""" % (
                self.col.sched.today,
                self.col.sched.dayCutoff,
            )
        else:
            # unknown
            return None

    def _findFlag(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        (val, __) = args
        if not val or len(val) != 1 or val not in "01234":
            return None
        mask = 2 ** 3 - 1
        return "(c.flags & %d) == %d" % (mask, int(val))

    def _findRated(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        # days(:optional_ease)
        (val, __) = args
        r = val.split(":")
        try:
            days = int(r[0])
        except ValueError:
            return None
        days = min(days, 31)
        # ease
        ease = ""
        if len(r) > 1:
            if r[1] not in ("1", "2", "3", "4"):
                return None
            ease = "and ease=%s" % r[1]
        cutoff = (self.col.sched.dayCutoff - 86400 * days) * 1000
        return "c.id in (select cid from revlog where id>%d %s)" % (cutoff, ease)

    def _findAdded(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        (val, __) = args
        try:
            days = int(val)
        except ValueError:
            return None
        cutoff = (self.col.sched.dayCutoff - 86400 * days) * 1000
        return "c.id > %d" % cutoff

    def _findProp(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        # extract
        (strval, __) = args
        m = re.match("(^.+?)(<=|>=|!=|=|<|>)(.+?$)", strval)
        if not m:
            return None
        prop, cmp, strval = m.groups()
        prop = prop.lower()  # pytype: disable=attribute-error
        # is val valid?
        try:
            if prop == "ease":
                val = float(strval)
            else:
                val = int(strval)
        except ValueError:
            return None
        # is prop valid?
        if prop not in ("due", "ivl", "reps", "lapses", "ease"):
            return None
        # query
        q = []
        if prop == "due":
            val += self.col.sched.today
            # only valid for review/daily learning
            q.append(f"(c.queue in ({QUEUE_TYPE_REV},{QUEUE_TYPE_DAY_LEARN_RELEARN}))")
        elif prop == "ease":
            prop = "factor"
            val = int(val * 1000)
        q.append("(%s %s %s)" % (prop, cmp, val))
        return " and ".join(q)

    def _findText(self, val: str, args: List[str]) -> str:
        val = val.replace("*", "%")
        args.append("%" + val + "%")
        args.append("%" + val + "%")
        return "(n.sfld like ? escape '\\' or n.flds like ? escape '\\')"

    def _findNids(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        (val, __) = args
        if re.search("[^0-9,]", val):
            return None
        return "n.id in (%s)" % val

    def _findCids(self, args) -> Optional[str]:
        (val, __) = args
        if re.search("[^0-9,]", val):
            return None
        return "c.id in (%s)" % val

    def _findMid(self, args) -> Optional[str]:
        (val, __) = args
        if re.search("[^0-9]", val):
            return None
        return "n.mid = %s" % val

    def _findModel(self, args: Tuple[str, List[Any]]) -> str:
        (val, __) = args
        ids = []
        val = val.lower()
        for m in self.col.models.all():
            if unicodedata.normalize("NFC", m["name"].lower()) == val:
                ids.append(m["id"])
        return "n.mid in %s" % ids2str(ids)

    def _findDeck(self, args: Tuple[str, List[Any]]) -> Optional[str]:
        # if searching for all decks, skip
        (val, __) = args
        if val == "*":
            return "skip"
        # deck types
        elif val == "filtered":
            return "c.odid"

        def dids(did):
            if not did:
                return None
            return [did] + [a[1] for a in self.col.decks.children(did)]

        # current deck?
        ids = None
        if val.lower() == "current":
            ids = dids(self.col.decks.current()["id"])
        elif "*" not in val:
            # single deck
            ids = dids(self.col.decks.id(val, create=False))
        else:
            # wildcard
            ids = set()
            val = re.escape(val).replace(r"\*", ".*")
            for d in self.col.decks.all():
                if re.match("(?i)" + val, unicodedata.normalize("NFC", d["name"])):
                    ids.update(dids(d["id"]))
        if not ids:
            return None
        sids = ids2str(ids)
        return "c.did in %s or c.odid in %s" % (sids, sids)

    def _findTemplate(self, args: Tuple[str, List[Any]]) -> str:
        # were we given an ordinal number?
        (val, __) = args
        try:
            num = int(val) - 1
        except:
            num = None
        if num is not None:
            return "c.ord = %d" % num
        # search for template names
        lims = []
        for m in self.col.models.all():
            for t in m["tmpls"]:
                if unicodedata.normalize("NFC", t["name"].lower()) == val.lower():
                    if m["type"] == MODEL_CLOZE:
                        # if the user has asked for a cloze card, we want
                        # to give all ordinals, so we just limit to the
                        # model instead
                        lims.append("(n.mid = %s)" % m["id"])
                    else:
                        lims.append("(n.mid = %s and c.ord = %s)" % (m["id"], t["ord"]))
        return " or ".join(lims)

    def _findField(self, field: str, val: str) -> Optional[str]:
        field = field.lower()
        val = val.replace("*", "%")
        # find models that have that field
        mods = {}
        for m in self.col.models.all():
            for f in m["flds"]:
                if unicodedata.normalize("NFC", f["name"].lower()) == field:
                    mods[str(m["id"])] = (m, f["ord"])
        if not mods:
            # nothing has that field
            return None
        # gather nids
        regex = re.escape(val).replace("_", ".").replace(re.escape("%"), ".*")
        nids = []
        for (id, mid, flds) in self.col.db.execute(
            """
select id, mid, flds from notes
where mid in %s and flds like ? escape '\\'"""
            % (ids2str(list(mods.keys()))),
            "%" + val + "%",
        ):
            flds = splitFields(flds)
            ord = mods[str(mid)][1]
            strg = flds[ord]
            try:
                if re.search("(?si)^" + regex + "$", strg):
                    nids.append(id)
            except sre_constants.error:
                return None
        if not nids:
            return "0"
        return "n.id in %s" % ids2str(nids)

    def _findDupes(self, args) -> Optional[str]:
        # caller must call stripHTMLMedia on passed val
        (val, __) = args
        try:
            mid, val = val.split(",", 1)
        except OSError:
            return None
        csum = fieldChecksum(val)
        nids = []
        for nid, flds in self.col.db.execute(
            "select id, flds from notes where mid=? and csum=?", mid, csum
        ):
            if stripHTMLMedia(splitFields(flds)[0]) == val:
                nids.append(nid)
        return "n.id in %s" % ids2str(nids)


# Find and replace
##########################################################################


def findReplace(
    col: _Collection,
    nids: List[int],
    src: str,
    dst: str,
    regex: bool = False,
    field: Optional[str] = None,
    fold: bool = True,
) -> int:
    "Find and replace fields in a note."
    mmap: Dict[str, Any] = {}
    if field:
        for m in col.models.all():
            for f in m["flds"]:
                if f["name"].lower() == field.lower():
                    mmap[str(m["id"])] = f["ord"]
        if not mmap:
            return 0
    # find and gather replacements
    if not regex:
        src = re.escape(src)
        dst = dst.replace("\\", "\\\\")
    if fold:
        src = "(?i)" + src
    compiled_re = re.compile(src)

    def repl(s: str):
        return compiled_re.sub(dst, s)

    d = []
    snids = ids2str(nids)
    nids = []
    for nid, mid, flds in col.db.execute(
        "select id, mid, flds from notes where id in " + snids
    ):
        origFlds = flds
        # does it match?
        sflds = splitFields(flds)
        if field:
            try:
                ord = mmap[str(mid)]
                sflds[ord] = repl(sflds[ord])
            except KeyError:
                # note doesn't have that field
                continue
        else:
            for c in range(len(sflds)):
                sflds[c] = repl(sflds[c])
        flds = joinFields(sflds)
        if flds != origFlds:
            nids.append(nid)
            d.append(dict(nid=nid, flds=flds, u=col.usn(), m=intTime()))
    if not d:
        return 0
    # replace
    col.db.executemany("update notes set flds=:flds,mod=:m,usn=:u where id=:nid", d)
    col.updateFieldCache(nids)
    col.genCards(nids)
    return len(d)


def fieldNames(col, downcase=True) -> List:
    fields: Set[str] = set()
    for m in col.models.all():
        for f in m["flds"]:
            name = f["name"].lower() if downcase else f["name"]
            if name not in fields:  # slower w/o
                fields.add(name)
    return list(fields)


def fieldNamesForNotes(col, nids) -> List:
    fields: Set[str] = set()
    mids = col.db.list("select distinct mid from notes where id in %s" % ids2str(nids))
    for mid in mids:
        model = col.models.get(mid)
        for name in col.models.fieldNames(model):
            if name not in fields:  # slower w/o
                fields.add(name)
    return sorted(fields, key=lambda x: x.lower())


# Find duplicates
##########################################################################
# returns array of ("dupestr", [nids])
def findDupes(
    col: _Collection, fieldName: str, search: str = ""
) -> List[Tuple[Any, List]]:
    # limit search to notes with applicable field name
    if search:
        search = "(" + search + ") "
    search += "'%s:*'" % fieldName
    # go through notes
    vals: Dict[str, List[int]] = {}
    dupes = []
    fields: Dict[int, int] = {}

    def ordForMid(mid):
        if mid not in fields:
            model = col.models.get(mid)
            for c, f in enumerate(model["flds"]):
                if f["name"].lower() == fieldName.lower():
                    fields[mid] = c
                    break
        return fields[mid]

    for nid, mid, flds in col.db.all(
        "select id, mid, flds from notes where id in " + ids2str(col.findNotes(search))
    ):
        flds = splitFields(flds)
        ord = ordForMid(mid)
        if ord is None:
            continue
        val = flds[ord]
        val = stripHTMLMedia(val)
        # empty does not count as duplicate
        if not val:
            continue
        vals.setdefault(val, []).append(nid)
        if len(vals[val]) == 2:
            dupes.append((val, vals[val]))
    return dupes
