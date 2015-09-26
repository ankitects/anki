# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
import sre_constants

from anki.utils import ids2str, splitFields, joinFields, intTime, fieldChecksum, stripHTMLMedia
from anki.consts import *
from anki.hooks import *


# Find
##########################################################################

class Finder(object):

    def __init__(self, col):
        self.col = col
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
        )
        self.search['is'] = self._findCardState
        runHook("search", self.search)

    def findCards(self, query, order=False):
        "Return a list of card ids for QUERY."
        tokens = self._tokenize(query)
        preds, args = self._where(tokens)
        if preds is None:
            return []
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

    def findNotes(self, query):
        tokens = self._tokenize(query)
        preds, args = self._where(tokens)
        if preds is None:
            return []
        if preds:
            preds = "(" + preds + ")"
        else:
            preds = "1"
        sql = """
select distinct(n.id) from cards c, notes n where c.nid=n.id and """+preds
        try:
            res = self.col.db.list(sql, *args)
        except:
            # invalid grouping
            return []
        return res

    # Tokenizing
    ######################################################################

    def _tokenize(self, query):
        inQuote = False
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
            elif c in (" ", u'\u3000'):
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

    def _where(self, tokens):
        # state and query
        s = dict(isnot=False, isor=False, join=False, q="", bad=False)
        args = []
        def add(txt, wrap=True):
            # failed command?
            if not txt:
                # if it was to be negated then we can just ignore it
                if s['isnot']:
                    s['isnot'] = False
                    return
                else:
                    s['bad'] = True
                    return
            elif txt == "skip":
                return
            # do we need a conjunction?
            if s['join']:
                if s['isor']:
                    s['q'] += " or "
                    s['isor'] = False
                else:
                    s['q'] += " and "
            if s['isnot']:
                s['q'] += " not "
                s['isnot'] = False
            if wrap:
                txt = "(" + txt + ")"
            s['q'] += txt
            s['join'] = True
        for token in tokens:
            if s['bad']:
                return None, None
            # special tokens
            if token == "-":
                s['isnot'] = True
            elif token.lower() == "or":
                s['isor'] = True
            elif token == "(":
                add(token, wrap=False)
                s['join'] = False
            elif token == ")":
                s['q'] += ")"
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
        if s['bad']:
            return None, None
        return s['q'], args

    def _query(self, preds, order):
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

    def _order(self, order):
        if not order:
            return "", False
        elif order is not True:
            # custom order string provided
            return " order by " + order, False
        # use deck default
        type = self.col.conf['sortType']
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
                sort = "c.factor"
            elif type == "cardLapses":
                sort = "c.lapses"
            elif type == "cardIvl":
                sort = "c.ivl"
        if not sort:
            # deck has invalid sort order; revert to noteCrt
            sort = "n.id, c.ord"
        return " order by " + sort, self.col.conf['sortBackwards']

    # Commands
    ######################################################################

    def _findTag(self, (val, args)):
        if val == "none":
            return 'n.tags = ""'
        val = val.replace("*", "%")
        if not val.startswith("%"):
            val = "% " + val
        if not val.endswith("%"):
            val += " %"
        args.append(val)
        return "n.tags like ?"

    def _findCardState(self, (val, args)):
        if val in ("review", "new", "learn"):
            if val == "review":
                n = 2
            elif val == "new":
                n = 0
            else:
                return "queue in (1, 3)"
            return "type = %d" % n
        elif val == "suspended":
            return "c.queue = -1"
        elif val == "buried":
            return "c.queue = -2"
        elif val == "due":
            return """
(c.queue in (2,3) and c.due <= %d) or
(c.queue = 1 and c.due <= %d)""" % (
    self.col.sched.today, self.col.sched.dayCutoff)

    def _findRated(self, (val, args)):
        # days(:optional_ease)
        r = val.split(":")
        try:
            days = int(r[0])
        except ValueError:
            return
        days = min(days, 31)
        # ease
        ease = ""
        if len(r) > 1:
            if r[1] not in ("1", "2", "3", "4"):
                return
            ease = "and ease=%s" % r[1]
        cutoff = (self.col.sched.dayCutoff - 86400*days)*1000
        return ("c.id in (select cid from revlog where id>%d %s)" %
                (cutoff, ease))

    def _findAdded(self, (val, args)):
        try:
            days = int(val)
        except ValueError:
            return
        cutoff = (self.col.sched.dayCutoff - 86400*days)*1000
        return "c.id > %d" % cutoff

    def _findProp(self, (val, args)):
        # extract
        m = re.match("(^.+?)(<=|>=|!=|=|<|>)(.+?$)", val)
        if not m:
            return
        prop, cmp, val = m.groups()
        prop = prop.lower()
        # is val valid?
        try:
            if prop == "ease":
                val = float(val)
            else:
                val = int(val)
        except ValueError:
            return
        # is prop valid?
        if prop not in ("due", "ivl", "reps", "lapses", "ease"):
            return
        # query
        q = []
        if prop == "due":
            val += self.col.sched.today
            # only valid for review/daily learning
            q.append("(c.queue in (2,3))")
        elif prop == "ease":
            prop = "factor"
            val = int(val*1000)
        q.append("(%s %s %s)" % (prop, cmp, val))
        return " and ".join(q)

    def _findText(self, val, args):
        val = val.replace("*", "%")
        args.append("%"+val+"%")
        args.append("%"+val+"%")
        return "(n.sfld like ? escape '\\' or n.flds like ? escape '\\')"

    def _findNids(self, (val, args)):
        if re.search("[^0-9,]", val):
            return
        return "n.id in (%s)" % val

    def _findCids(self, (val, args)):
        if re.search("[^0-9,]", val):
            return
        return "c.id in (%s)" % val

    def _findMid(self, (val, args)):
        if re.search("[^0-9]", val):
            return
        return "n.mid = %s" % val

    def _findModel(self, (val, args)):
        ids = []
        val = val.lower()
        for m in self.col.models.all():
            if m['name'].lower() == val:
                ids.append(m['id'])
        return "n.mid in %s" % ids2str(ids)

    def _findDeck(self, (val, args)):
        # if searching for all decks, skip
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
            ids = dids(self.col.decks.current()['id'])
        elif "*" not in val:
            # single deck
            ids = dids(self.col.decks.id(val, create=False))
        else:
            # wildcard
            ids = set()
            # should use re.escape in the future
            val = val.replace("*", ".*")
            val = val.replace("+", "\\+")
            for d in self.col.decks.all():
                if re.match("(?i)"+val, d['name']):
                    ids.update(dids(d['id']))
        if not ids:
            return
        sids = ids2str(ids)
        return "c.did in %s or c.odid in %s" % (sids, sids)

    def _findTemplate(self, (val, args)):
        # were we given an ordinal number?
        try:
            num = int(val) - 1
        except:
            num = None
        if num is not None:
            return "c.ord = %d" % num
        # search for template names
        lims = []
        for m in self.col.models.all():
            for t in m['tmpls']:
                if t['name'].lower() == val.lower():
                    if m['type'] == MODEL_CLOZE:
                        # if the user has asked for a cloze card, we want
                        # to give all ordinals, so we just limit to the
                        # model instead
                        lims.append("(n.mid = %s)" % m['id'])
                    else:
                        lims.append("(n.mid = %s and c.ord = %s)" % (
                            m['id'], t['ord']))
        return " or ".join(lims)

    def _findField(self, field, val):
        field = field.lower()
        val = val.replace("*", "%")
        # find models that have that field
        mods = {}
        for m in self.col.models.all():
            for f in m['flds']:
                if f['name'].lower() == field:
                    mods[str(m['id'])] = (m, f['ord'])
        if not mods:
            # nothing has that field
            return
        # gather nids
        regex = re.escape(val).replace("\\_", ".").replace("\\%", ".*")
        nids = []
        for (id,mid,flds) in self.col.db.execute("""
select id, mid, flds from notes
where mid in %s and flds like ? escape '\\'""" % (
                         ids2str(mods.keys())),
                         "%"+val+"%"):
            flds = splitFields(flds)
            ord = mods[str(mid)][1]
            strg = flds[ord]
            try:
                if re.search("(?si)^"+regex+"$", strg):
                    nids.append(id)
            except sre_constants.error:
                return
        if not nids:
            return "0"
        return "n.id in %s" % ids2str(nids)

    def _findDupes(self, (val, args)):
        # caller must call stripHTMLMedia on passed val
        try:
            mid, val = val.split(",", 1)
        except OSError:
            return
        csum = fieldChecksum(val)
        nids = []
        for nid, flds in self.col.db.execute(
                "select id, flds from notes where mid=? and csum=?",
                mid, csum):
            if stripHTMLMedia(splitFields(flds)[0]) == val:
                nids.append(nid)
        return "n.id in %s" % ids2str(nids)

# Find and replace
##########################################################################

def findReplace(col, nids, src, dst, regex=False, field=None, fold=True):
    "Find and replace fields in a note."
    mmap = {}
    if field:
        for m in col.models.all():
            for f in m['flds']:
                if f['name'] == field:
                    mmap[str(m['id'])] = f['ord']
        if not mmap:
            return 0
    # find and gather replacements
    if not regex:
        src = re.escape(src)
    if fold:
        src = "(?i)"+src
    regex = re.compile(src)
    def repl(str):
        return re.sub(regex, dst, str)
    d = []
    snids = ids2str(nids)
    nids = []
    for nid, mid, flds in col.db.execute(
        "select id, mid, flds from notes where id in "+snids):
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
            d.append(dict(nid=nid,flds=flds,u=col.usn(),m=intTime()))
    if not d:
        return 0
    # replace
    col.db.executemany(
        "update notes set flds=:flds,mod=:m,usn=:u where id=:nid", d)
    col.updateFieldCache(nids)
    col.genCards(nids)
    return len(d)

def fieldNames(col, downcase=True):
    fields = set()
    names = []
    for m in col.models.all():
        for f in m['flds']:
            if f['name'].lower() not in fields:
                names.append(f['name'])
                fields.add(f['name'].lower())
    if downcase:
        return list(fields)
    return names

# Find duplicates
##########################################################################
# returns array of ("dupestr", [nids])
def findDupes(col, fieldName, search=""):
    # limit search to notes with applicable field name
    if search:
        search = "("+search+") "
    search += "'%s:*'" % fieldName
    # go through notes
    vals = {}
    dupes = []
    fields = {}
    def ordForMid(mid):
        if mid not in fields:
            model = col.models.get(mid)
            for c, f in enumerate(model['flds']):
                if f['name'].lower() == fieldName.lower():
                    fields[mid] = c
                    break
        return fields[mid]
    for nid, mid, flds in col.db.all(
        "select id, mid, flds from notes where id in "+ids2str(
            col.findNotes(search))):
        flds = splitFields(flds)
        ord = ordForMid(mid)
        if ord is None:
            continue
        val = flds[ord]
        val = stripHTMLMedia(val)
        # empty does not count as duplicate
        if not val:
            continue
        if val not in vals:
            vals[val] = []
        vals[val].append(nid)
        if len(vals[val]) == 2:
            dupes.append((val, vals[val]))
    return dupes
