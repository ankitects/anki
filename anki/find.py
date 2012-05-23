# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from anki.utils import ids2str, splitFields, joinFields, stripHTML, intTime
from anki.consts import *

SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_NID = 3
SEARCH_TEMPLATE = 4
SEARCH_FIELD = 5
SEARCH_MODEL = 6
SEARCH_DECK = 7
SEARCH_PROP = 8
SEARCH_RATED = 9

# Tools
##########################################################################

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

# Find
##########################################################################

class Finder(object):

    def __init__(self, col):
        self.col = col

    def findCards(self, query, full=False, order=None):
        "Return a list of card ids for QUERY."
        self.order = order
        self.query = query
        self.full = full
        self._findLimits()
        if not self.lims['valid']:
            return []
        (q, args) = self._whereClause()
        order = self._order()
        query = """\
select c.id from cards c, notes n where %s
and c.nid=n.id %s""" % (q, order)
        res = self.col.db.list(query, **args)
        if not self.order and self.col.conf['sortBackwards']:
            res.reverse()
        return res

    def _whereClause(self):
        q = " and ".join(self.lims['preds'])
        if not q:
            q = "1"
        return q, self.lims['args']

    def _order(self):
        # user provided override?
        if self.order:
            return self.order
        type = self.col.conf['sortType']
        if not type:
            return
        if type.startswith("note"):
            if type == "noteCrt":
                sort = "n.id, c.ord"
            elif type == "noteMod":
                sort = "n.mod, c.ord"
            elif type == "noteFld":
                sort = "n.sfld collate nocase, c.ord"
            else:
                raise Exception()
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
            else:
                raise Exception()
        else:
            raise Exception()
        return " order by " + sort

    def _findLimits(self):
        "Generate a list of note/card limits for the query."
        self.lims = {
            'preds': [],
            'args': {},
            'valid': True,
        }
        for c, (token, isNeg, type) in enumerate(self._parseQuery()):
            if type == SEARCH_TAG:
                self._findTag(token, isNeg, c)
            elif type == SEARCH_TYPE:
                self._findCardState(token, isNeg)
            elif type == SEARCH_NID:
                self._findNids(token)
            elif type == SEARCH_TEMPLATE:
                self._findTemplate(token, isNeg)
            elif type == SEARCH_FIELD:
                self._findField(token, isNeg)
            elif type == SEARCH_MODEL:
                self._findModel(token, isNeg)
            elif type == SEARCH_DECK:
                self._findDeck(token, isNeg)
            elif type == SEARCH_PROP:
                self._findProp(token, isNeg)
            elif type == SEARCH_RATED:
                self._findRated(token, isNeg)
            else:
                self._findText(token, isNeg, c)

    def _findTag(self, val, neg, c):
        if val == "none":
            if neg:
                t = "tags != ''"
            else:
                t = "tags = ''"
            self.lims['preds'].append(t)
            return
        extra = "not" if neg else ""
        val = val.replace("*", "%")
        if not val.startswith("%"):
            val = "% " + val
        if not val.endswith("%"):
            val += " %"
        self.lims['args']["_tag_%d" % c] = val
        self.lims['preds'].append(
            "tags %s like :_tag_%d""" % (extra, c))

    def _findCardState(self, val, neg):
        cond = None
        if val in ("review", "new", "learn"):
            if val == "review":
                n = 2
            elif val == "new":
                n = 0
            else:
                n = 1
            cond = "type = %d" % n
        elif val == "suspended":
            cond = "queue = -1"
        elif val == "due":
            cond = "(queue = 2 and due <= %d)" % self.col.sched.today
        if cond:
            if neg:
                cond = "not (%s)" % cond
            self.lims['preds'].append(cond)
        else:
            self.lims['valid'] = False

    def _findRated(self, val, neg):
        r = val.split(":")
        if len(r) != 2 or r[0] not in ("1", "2", "3", "4"):
            self.lims['valid'] = False
            return
        try:
            days = int(r[1])
        except ValueError:
            self.lims['valid'] = False
            return
        # bound the search
        days = min(days, 31)
        lim = self.col.sched.dayCutoff - 86400*days
        self.lims['preds'].append(
            "c.id in (select cid from revlog where ease=%s and id>%d)" %
            (r[0], (lim*1000)))

    def _findProp(self, val, neg):
        # extract
        m = re.match("(^.+?)(<=|>=|=|<|>)(.+?$)", val)
        if not m:
            self.lims['valid'] = False
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
            self.lims['valid'] = False
            return
        # is prop valid?
        if prop not in ("due", "ivl", "reps", "lapses", "ease"):
            self.lims['valid'] = False
            return
        # query
        extra = "not" if neg else ""
        if prop == "due":
            val += self.col.sched.today
            # only valid for review/daily learning
            self.lims['preds'].append("queue in (2,3)")
        elif prop == "ease":
            prop = "factor"
            val = int(val*1000)
        sql = "%s (%s %s %s)" % ("not" if neg else "",
                                 prop, cmp, val)
        self.lims['preds'].append(sql)

    def _findText(self, val, neg, c):
        val = val.replace("*", "%")
        if not self.full:
            self.lims['args']["_text_%d"%c] = "%"+val+"%"
            txt = """
(sfld like :_text_%d escape '\\' or flds like :_text_%d escape '\\')""" % (c,c)
            if not neg:
                self.lims['preds'].append(txt)
            else:
                self.lims['preds'].append("not " + txt)
        else:
            nids = []
            extra = "not" if neg else ""
            for nid, flds in self.col.db.execute(
                "select id, flds from notes"):
                if val in stripHTML(flds):
                    nids.append(nid)
            self.lims['preds'].append("n.id %s in %s " % (extra, ids2str(nids)))

    def _findNids(self, val):
        self.lims['preds'].append("n.id in (%s)" % val)

    def _findModel(self, val, isNeg):
        extra = "not" if isNeg else ""
        ids = []
        for m in self.col.models.all():
            if m['name'].lower() == val:
                ids.append(m['id'])
        self.lims['preds'].append("mid %s in %s" % (extra, ids2str(ids)))

    def _findDeck(self, val, isNeg):
        ids = []
        if val.lower() == "current":
            id = self.col.decks.current()['id']
        elif val.lower() == "none":
            if isNeg:
                extra = ""
            else:
                extra = "not"
            self.lims['preds'].append(
                "c.did %s in %s" % (extra, ids2str(self.col.decks.allIds())))
            return
        elif "*" not in val:
            # single deck
            id = self.col.decks.id(val, create=False) or 0
        else:
            # wildcard
            val = val.replace("*", ".*")
            for d in self.col.decks.all():
                if re.match("(?i)"+val, d['name']):
                    id = d['id']
                    ids.extend([id] + [
                        a[1] for a in self.col.decks.children(id)])
            if not ids:
                # invalid search
                self.lims['valid'] = False
                return
        if not ids:
            ids = [id] + [a[1] for a in self.col.decks.children(id)]
        sids = ids2str(ids)
        if not isNeg:
            # normal search
            self.lims['preds'].append(
                "(c.odid in %s or c.did in %s)" % (sids, sids))
        else:
            # inverted search
            self.lims['preds'].append("""
((case c.odid when 0 then 1 else c.odid not in %s end) and c.did not in %s)
""" % (sids, sids))

    def _findTemplate(self, val, isNeg):
        lims = []
        comp = "!=" if isNeg else "="
        found = False
        try:
            num = int(val) - 1
        except:
            num = None
        lims = []
        # were we given an ordinal number?
        if num is not None:
            found = True
            self.lims['preds'].append("ord %s %d" % (comp, num))
        else:
            # search for template names
            for m in self.col.models.all():
                for t in m['tmpls']:
                    # template name?
                    if t['name'].lower() == val.lower():
                        if m['type'] == MODEL_CLOZE:
                            # if the user has asked for a cloze card, we want
                            # to give all ordinals, so we just limit to the
                            # model instead
                            lims.append("(mid = %s)" % m['id'])
                            found = True
                        else:
                            lims.append((
                                "(nid in (select id from notes where mid = %s) "
                                "and ord %s %d)") % (m['id'], comp, t['ord']))
                            found = True
        if lims:
            self.lims['preds'].append("(" + " or ".join(lims) + ")")
        self.lims['valid'] = found

    def _findField(self, token, isNeg):
        field = value = ''
        parts = token.split(':', 1);
        field = parts[0].lower()
        value = "%" + parts[1].replace("*", "%") + "%"
        # find models that have that field
        mods = {}
        for m in self.col.models.all():
            for f in m['flds']:
                if f['name'].lower() == field:
                    mods[str(m['id'])] = (m, f['ord'])
        if not mods:
            # nothing has that field
            self.lims['valid'] = False
            return
        # gather nids
        regex = value.replace("_", ".").replace("%", ".*")
        nids = []
        for (id,mid,flds) in self.col.db.execute("""
select id, mid, flds from notes
where mid in %s and flds like ? escape '\\'""" % (
                         ids2str(mods.keys())),
                         "%" if self.full else value):
            flds = splitFields(flds)
            ord = mods[str(mid)][1]
            strg = flds[ord]
            if self.full:
                strg = stripHTML(strg)
            if re.search("(?i)"+regex, strg):
                nids.append(id)
        extra = "not" if isNeg else ""
        self.lims['preds'].append("""
n.mid in %s and n.id %s in %s""" % (
    ids2str(mods.keys()), extra, ids2str(nids)))

    # Most of this function was written by Marcus
    def _parseQuery(self):
        tokens = []
        res = []
        allowedfields = fieldNames(self.col)
        def addSearchFieldToken(field, value, isNeg):
            if field.lower() in allowedfields:
                res.append((field + ':' + value, isNeg, SEARCH_FIELD))
            else:
                for p in phraselog:
                    res.append((p['value'], p['is_neg'], p['type']))
        # break query into words or phraselog
        # an extra space is added so the loop never ends in the middle
        # completing a token
        for match in re.findall(
            r'(-)?\'(([^\'\\]|\\.)*)\'|(-)?"(([^"\\]|\\.)*)"|(-)?([^ ]+)|([ ]+)',
            self.query + ' '):
            value = (match[1] or match[4] or match[7])
            isNeg = (match[0] == '-' or match[3] == '-' or match[6] == '-')
            tokens.append({'value': value, 'is_neg': isNeg})
        intoken = isNeg = False
        field = '' #name of the field for field related commands
        phraselog = [] #log of phrases in case potential command is not a commad
        for c, token in enumerate(tokens):
            doprocess = True # only look for commands when this is true
            #prevent cases such as "field" : value as being processed as a command
            if len(token['value']) == 0:
                if intoken is True and type == SEARCH_FIELD and field:
                    #case: fieldname: any thing here check for existance of fieldname
                    addSearchFieldToken(field, '*', isNeg)
                    phraselog = [] # reset phrases since command is completed
                intoken = doprocess = False
            if intoken is True:
                if type == SEARCH_FIELD and field:
                    #case: fieldname:"value"
                    addSearchFieldToken(field, token['value'], isNeg)
                    intoken = doprocess = False
                elif type == SEARCH_FIELD and not field:
                    #case: "fieldname":"name" or "field" anything
                    if token['value'].startswith(":") and len(phraselog) == 1:
                        #we now know a colon is next, so mark it as field
                        # and keep looking for the value
                        field = phraselog[0]['value']
                        parts = token['value'].split(':', 1)
                        phraselog.append(
                            {'value': token['value'], 'is_neg': False,
                             'type': SEARCH_PHRASE})
                        if parts[1]:
                            #value is included with the :, so wrap it up
                            addSearchFieldToken(field, parts[1], isNeg)
                            intoken = doprocess = False
                        doprocess = False
                    else:
                        #case: "fieldname"string/"fieldname"tag:name
                        intoken = False
                if intoken is False and doprocess is False:
                    #command has been fully processed
                    phraselog = [] # reset phraselog, since we used it for a command
            if intoken is False:
                #include any non-command related phrases in the query
                for p in phraselog: res.append(
                    (p['value'], p['is_neg'], p['type']))
                phraselog = []
            if intoken is False and doprocess is True:
                field = ''
                isNeg = token['is_neg']
                if token['value'].startswith("tag:"):
                    token['value'] = token['value'][4:]
                    type = SEARCH_TAG
                elif token['value'].startswith("is:"):
                    token['value'] = token['value'][3:].lower()
                    type = SEARCH_TYPE
                elif token['value'].startswith("note:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_MODEL
                elif token['value'].startswith("deck:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_DECK
                elif token['value'].startswith("prop:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_PROP
                elif token['value'].startswith("rated:"):
                    token['value'] = token['value'][6:].lower()
                    type = SEARCH_RATED
                elif token['value'].startswith("nid:") and len(token['value']) > 4:
                    dec = token['value'][4:]
                    try:
                        int(dec)
                        token['value'] = token['value'][4:]
                    except:
                        try:
                            for d in dec.split(","):
                                int(d)
                            token['value'] = token['value'][4:]
                        except:
                            token['value'] = "0"
                    type = SEARCH_NID
                elif token['value'].startswith("card:"):
                    token['value'] = token['value'][5:]
                    type = SEARCH_TEMPLATE
                else:
                    type = SEARCH_FIELD
                    intoken = True
                    parts = token['value'].split(':', 1)
                    phraselog.append(
                        {'value': token['value'], 'is_neg': isNeg,
                         'type': SEARCH_PHRASE})
                    if len(parts) == 2 and parts[0]:
                        field = parts[0]
                        if parts[1]:
                            #simple fieldname:value case -
                            #no need to look for more data
                            addSearchFieldToken(field, parts[1], isNeg)
                            intoken = doprocess = False
                    if intoken is False: phraselog = []
                if intoken is False and doprocess is True:
                    res.append((token['value'], isNeg, type))
        return res

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
    col.db.executemany("update notes set flds=:flds,mod=:m,usn=:u where id=:nid", d)
    col.updateFieldCache(nids)
    col.genCards(nids)
    return len(d)

# Find duplicates
##########################################################################

def findDuplicates(col, fmids):
    data = col.db.all(
        "select nid, value from fdata where fmid in %s" %
        ids2str(fmids))
    vals = {}
    for (nid, val) in data:
        if not val.strip():
            continue
        if val not in vals:
            vals[val] = [nid]
        else:
            vals[val].append(nid)
    return [(k,v) for (k,v) in vals.items() if len(v) > 1]
