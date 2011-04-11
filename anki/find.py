# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from anki.utils import ids2str, splitFields

SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_FID = 3
SEARCH_TEMPLATE = 4
SEARCH_FIELD = 5
SEARCH_MODEL = 6
SEARCH_GROUP = 7

# Find
##########################################################################

class Finder(object):

    def __init__(self, deck):
        self.deck = deck

    def findCards(self, query):
        "Return a list of card ids for QUERY."
        self.query = query
        self._findLimits()
        if not self.lims['valid']:
            return []
        (q, args) = self._whereClause()
        query = self._orderedSelect(q)
        res = self.deck.db.list(query, **args)
        if self.deck.conf['sortBackwards']:
            res.reverse()
        return res

    def _whereClause(self):
        x = []
        if self.lims['fact']:
            x.append("fid in (select id from facts where %s)" % " and ".join(
                self.lims['fact']))
        if self.lims['card']:
            x.extend(self.lims['card'])
        q = " and ".join(x)
        if not q:
            q = "1"
        return q, self.lims['args']

    def _orderedSelect(self, lim):
        type = self.deck.conf['sortType']
        if not type:
            return "select id from cards c where " + lim
        elif type.startswith("fact"):
            if type == "factCrt":
                sort = "f.crt, c.ord"
            elif type == "factMod":
                sort = "f.mod, c.ord"
            elif type == "factFld":
                sort = "f.sfld collate nocase, c.ord"
            else:
                raise Exception()
            return """
select c.id from cards c, facts f where %s and c.fid=f.id
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
        "Generate a list of fact/card limits for the query."
        self.lims = {
            'fact': [],
            'card': [],
            'args': {},
            'valid': True
        }
        for c, (token, isNeg, type) in enumerate(self._parseQuery()):
            if type == SEARCH_TAG:
                self._findTag(token, isNeg, c)
            elif type == SEARCH_TYPE:
                self._findCardState(token, isNeg)
            elif type == SEARCH_FID:
                self._findFids(token)
            elif type == SEARCH_TEMPLATE:
                self._findTemplate(token, isNeg)
            elif type == SEARCH_FIELD:
                self._findField(token, isNeg)
            elif type == SEARCH_MODEL:
                self._findModel(token, isNeg, c)
            elif type == SEARCH_GROUP:
                self._findGroup(token, isNeg, c)
            else:
                self._findText(token, isNeg, c)

    def _findTag(self, val, neg, c):
        if val == "none":
            self.lims['fact'].append("select id from facts where tags = ''")
            return
        extra = "not" if neg else ""
        val = val.replace("*", "%")
        if not val.startswith("%"):
            val = "% " + val
        if not val.endswith("%"):
            val += " %"
        self.lims['args']["_tag_%d" % c] = val
        self.lims['fact'].append(
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
            cond = "(queue = 2 and due <= %d)" % self.deck.sched.today
        elif val == "recent":
            cond = "c.id in (select id from cards order by mod desc limit 100)"
        if neg:
            cond = "not (%s)" % cond
        self.lims['card'].append(cond)

    def _findText(self, val, neg, c):
        val = val.replace("*", "%")
        extra = "not" if neg else ""
        self.lims['args']["_text_%d"%c] = "%"+val+"%"
        self.lims['fact'].append("flds %s like :_text_%d escape '\\'" % (
            extra, c))

    def _findFids(self, val):
        self.lims['fact'].append("id in (%s)" % val)

    def _findModel(self, val, isNeg, c):
        extra = "not" if isNeg else ""
        self.lims['fact'].append(
            "mid %s in (select id from models where name like :_mod_%d)" % (
                extra, c))
        self.lims['args']['_mod_%d'%c] = val

    def _findGroup(self, val, isNeg, c):
        extra = "not" if isNeg else ""
        self.lims['card'].append(
            "c.gid %s in (select id from groups where name like :_grp_%d)" % (
                extra, c))
        self.lims['args']['_grp_%d'%c] = val

    def _findTemplate(self, val, isNeg):
        lims = []
        comp = "!=" if isNeg else "="
        found = False
        try:
            num = int(val) - 1
        except:
            num = None
        lims = []
        for m in self.deck.models().values():
            for t in m.templates:
                # ordinal number?
                if num is not None and t['ord'] == num:
                    self.lims['card'].append("ord %s %d" % (comp, num))
                    found = True
                # template name?
                elif t['name'].lower() == val.lower():
                    lims.append((
                        "(fid in (select id from facts where mid = %d) "
                        "and ord %s %d)") % (m.id, comp, t['ord']))
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
        for m in self.deck.models().values():
            for f in m.fields:
                if f['name'].lower() == field:
                    mods[m.id] = (m, f['ord'])
        if not mods:
            # nothing has that field
            self.lims['valid'] = False
            return
        # gather fids
        regex = value.replace("%", ".*")
        fids = []
        for (id,mid,flds) in self.deck.db.execute("""
select id, mid, flds from facts
where mid in %s and flds like ? escape '\\'""" % (
                         ids2str(mods.keys())),
                         value):
            flds = splitFields(flds)
            ord = mods[mid][1]
            if re.search(regex, flds[ord]):
                fids.append(id)
        extra = "not" if isNeg else ""
        self.lims['fact'].append("id %s in %s" % (extra, ids2str(fids)))

    def _fieldNames(self):
        fields = set()
        for m in self.deck.models().values():
            fields.update([f['name'].lower() for f in m.fields])
        return list(fields)

    # Most of this function was written by Marcus
    def _parseQuery(self):
        tokens = []
        res = []
        allowedfields = self._fieldNames()
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
                elif token['value'].startswith("group:"):
                    token['value'] = token['value'][6:].lower()
                    type = SEARCH_GROUP
                elif token['value'].startswith("fid:") and len(token['value']) > 4:
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
                    type = SEARCH_FID
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

def findReplace(deck, fids, src, dst, isRe=False, field=None):
    "Find and replace fields in a fact."
    # find
    s = "select id, fid, value from fdata where fid in %s"
    if isRe:
        isRe = re.compile(src)
    else:
        s += " and value like :v"
    if field:
        s += " and fmid = :fmid"
    rows = deck.db.all(s % ids2str(fids),
                      v="%"+src.replace("%", "%%")+"%",
                      fmid=field)
    modded = []
    if isRe:
        modded = [
            {'id': id, 'fid': fid, 'val': re.sub(isRe, dst, val)}
            for (id, fid, val) in rows
            if isRe.search(val)]
    else:
        modded = [
            {'id': id, 'fid': fid, 'val': val.replace(src, dst)}
            for (id, fid, val) in rows
            if val.find(src) != -1]
    # update
    if modded:
        deck.db.executemany(
            'update fdata set value = :val where id = :id', modded)
        deck.updateCardQACacheFromIds([f['fid'] for f in modded],
                                      type="facts")
        if field:
            deck.updateFieldChecksums(field)
        else:
            deck.updateAllFieldChecksums()
    return len(set([f['fid'] for f in modded]))

# Find duplicates
##########################################################################

def findDuplicates(deck, fmids):
    data = deck.db.all(
        "select fid, value from fdata where fmid in %s" %
        ids2str(fmids))
    vals = {}
    for (fid, val) in data:
        if not val.strip():
            continue
        if val not in vals:
            vals[val] = [fid]
        else:
            vals[val].append(fid)
    return [(k,v) for (k,v) in vals.items() if len(v) > 1]
