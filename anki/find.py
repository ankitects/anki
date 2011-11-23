# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from anki.utils import ids2str, splitFields, joinFields, stripHTML, intTime


SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_NID = 3
SEARCH_TEMPLATE = 4
SEARCH_FIELD = 5
SEARCH_MODEL = 6
SEARCH_DECK = 7

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

    def findCards(self, query, full=False):
        "Return a list of card ids for QUERY."
        self.query = query
        self.full = full
        self._findLimits()
        if not self.lims['valid']:
            return []
        (q, args) = self._whereClause()
        query = self._orderedSelect(q)
        res = self.col.db.list(query, **args)
        if self.col.conf['sortBackwards']:
            res.reverse()
        return res

    def _whereClause(self):
        x = []
        if self.lims['note']:
            x.append("nid in (select id from notes where %s)" % " and ".join(
                self.lims['note']))
        if self.lims['card']:
            x.extend(self.lims['card'])
        q = " and ".join(x)
        if not q:
            q = "1"
        return q, self.lims['args']

    def _orderedSelect(self, lim):
        type = self.col.conf['sortType']
        if not type:
            return "select id from cards c where " + lim
        elif type.startswith("note"):
            if type == "noteCrt":
                sort = "f.id, c.ord"
            elif type == "noteMod":
                sort = "f.mod, c.ord"
            elif type == "noteFld":
                sort = "f.sfld collate nocase, c.ord"
            else:
                raise Exception()
            return """
select c.id from cards c, notes f where %s and c.nid=f.id
order by %s""" % (lim, sort)
        elif type.startswith("card"):
            if type == "cardMod":
                sort = "c.mod"
            elif type == "cardReps":
                sort = "c.reps"
            elif type == "cardDue":
                sort = "c.due"
            elif type == "cardEase":
                sort = "c.factor"
            elif type == "cardLapses":
                sort = "c.lapses"
            elif type == "cardIvl":
                sort = "c.ivl"
            else:
                raise Exception()
            return "select c.id from cards c where %s order by %s" % (
                lim, sort)
        else:
            raise Exception()

    def _findLimits(self):
        "Generate a list of note/card limits for the query."
        self.lims = {
            'note': [],
            'card': [],
            'args': {},
            'valid': True
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
            else:
                self._findText(token, isNeg, c)

    def _findTag(self, val, neg, c):
        if val == "none":
            self.lims['note'].append("select id from notes where tags = ''")
            return
        extra = "not" if neg else ""
        val = val.replace("*", "%")
        if not val.startswith("%"):
            val = "% " + val
        if not val.endswith("%"):
            val += " %"
        self.lims['args']["_tag_%d" % c] = val
        self.lims['note'].append(
            "tags %s like :_tag_%d""" % (extra, c))

    def _findCardState(self, val, neg):
        cond = None
        if val in ("rev", "new", "lrn"):
            if val == "rev":
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
        elif val == "recent":
            cond = "c.id in (select id from cards order by mod desc limit 100)"
        if neg:
            cond = "not (%s)" % cond
        if cond:
            self.lims['card'].append(cond)
        else:
            self.lims['valid'] = False

    def _findText(self, val, neg, c):
        val = val.replace("*", "%")
        extra = "not" if neg else ""
        if not self.full:
            self.lims['args']["_text_%d"%c] = "%"+val+"%"
            self.lims['note'].append("flds %s like :_text_%d escape '\\'" % (
                extra, c))
        else:
            # in the future we may want to apply this at the end to speed up
            # the case where there are other limits
            nids = []
            for nid, flds in self.col.db.execute(
                "select id, flds from notes"):
                if val in stripHTML(flds):
                    nids.append(nid)
            self.lims['note'].append("id in " + ids2str(nids))

    def _findNids(self, val):
        self.lims['note'].append("id in (%s)" % val)

    def _findModel(self, val, isNeg):
        extra = "not" if isNeg else ""
        ids = []
        for m in self.col.models.all():
            if m['name'].lower() == val:
                ids.append(m['id'])
        self.lims['note'].append("mid %s in %s" % (extra, ids2str(ids)))

    def _findDeck(self, val, isNeg):
        extra = "!" if isNeg else ""
        id = self.col.decks.id(val, create=False) or 0
        self.lims['card'].append("c.did %s= %s" % (extra, id))

    def _findTemplate(self, val, isNeg):
        lims = []
        comp = "!=" if isNeg else "="
        found = False
        try:
            num = int(val) - 1
        except:
            num = None
        lims = []
        for m in self.col.models.all():
            for t in m['tmpls']:
                # ordinal number?
                if num is not None and t['ord'] == num:
                    self.lims['card'].append("ord %s %d" % (comp, num))
                    found = True
                # template name?
                elif t['name'].lower() == val.lower():
                    lims.append((
                        "(nid in (select id from notes where mid = %s) "
                        "and ord %s %d)") % (m['id'], comp, t['ord']))
                    found = True
        if lims:
            self.lims['card'].append("(" + " or ".join(lims) + ")")
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
                    mods[m['id']] = (m, f['ord'])
        if not mods:
            # nothing has that field
            self.lims['valid'] = False
            return
        # gather nids
        regex = value.replace("%", ".*")
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
            if re.search(regex, strg):
                nids.append(id)
        extra = "not" if isNeg else ""
        self.lims['note'].append("id %s in %s" % (extra, ids2str(nids)))

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
                            addSearchFieldToken(field, parts[1], isNeg, 'none')
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
                elif token['value'].startswith("model:"):
                    token['value'] = token['value'][6:].lower()
                    type = SEARCH_MODEL
                elif token['value'].startswith("deck:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_DECK
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
                    mmap[m['id']] = f['ord']
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
    for nid, mid, flds in col.db.execute(
        "select id, mid, flds from notes where id in "+ids2str(nids)):
        origFlds = flds
        # does it match?
        sflds = splitFields(flds)
        if field:
            ord = mmap[str(mid)]
            sflds[ord] = repl(sflds[ord])
        else:
            for c in range(len(sflds)):
                sflds[c] = repl(sflds[c])
        flds = joinFields(sflds)
        if flds != origFlds:
            d.append(dict(nid=nid,flds=flds,u=col.usn(),m=intTime()))
    if not d:
        return 0
    # replace
    col.db.executemany("update notes set flds=:flds,mod=:m,usn=:u where id=:nid", d)
    col.updateFieldCache(nids)
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
