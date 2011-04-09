# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from anki.utils import ids2str

SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_FID = 3
SEARCH_TEMPLATE = 4
SEARCH_DISTINCT = 5
SEARCH_FIELD = 6
SEARCH_FIELD_EXISTS = 7

# Find
##########################################################################

class Finder(object):

    def __init__(self, deck):
        self.deck = deck

    def findCards(self, query):
        self.query = query
        (q, args) = self.findCardsWhere()
        #fidList = findCardsMatchingFilters(self.deck, filters)
        query = "select id from cards"
        if q:
            query += " where " + q
        # if cmquery['pos'] or cmquery['neg']:
        #     if hasWhere is False:
        #         query += " where "
        #         hasWhere = True
        #     else: query += " and "
        #     if cmquery['pos']:
        #         query += (" fid in(select distinct fid from cards "+
        #                   "where id in (" + cmquery['pos'] + ")) ")
        #         query += " and id in(" + cmquery['pos'] + ") "
        #     if cmquery['neg']:
        #         query += (" fid not in(select distinct fid from "+
        #                   "cards where id in (" + cmquery['neg'] + ")) ")
        # if fidList is not None:
        #     if hasWhere is False:
        #         query += " where "
        #         hasWhere = True
        #     else: query += " and "
        #     query += " fid IN %s" % ids2str(fidList)
        # if showdistinct:
        #     query += " group by fid"
        print query, args
        return self.deck.db.list(query, **args)

    def _findLimits(self):
        "Generate a list of fact/card limits for the query."
        self.lims = {
            'fact': [],
            'card': [],
            'args': {}
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
            elif type == SEARCH_FIELD or type == SEARCH_FIELD_EXISTS:
                field = value = ''
                if type == SEARCH_FIELD:
                    parts = token.split(':', 1);
                    if len(parts) == 2:
                        field = parts[0]
                        value = parts[1]
                elif type == SEARCH_FIELD_EXISTS:
                    field = token
                    value = '*'
                if type == SEARCH_FIELD:
                    if field and value:
                        filters.append(
                            {'scope': 'field',
                             'field': field, 'value': value, 'is_neg': isNeg})
                else:
                    if field and value:
                        if sfquery:
                            if isNeg:
                                sfquery += " except "
                            else:
                                sfquery += " intersect "
                        elif isNeg:
                            sfquery += "select id from facts except "
                        field = field.replace("*", "%")
                        value = value.replace("*", "%")
                        data['args']["_ff_%d" % c] = "%"+value+"%"
                        ids = deck.db.list("""
    select id from fieldmodels where name like :field escape '\\'""", field=field)
                        sfquery += """
    select fid from fdata where fmid in %s and
    value like :_ff_%d escape '\\'""" % (ids2str(ids), c)
            elif type == SEARCH_DISTINCT:
                if isNeg is False:
                    showdistinct = True if token == "one" else False
                else:
                    showdistinct = False if token == "one" else True
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
        if val in ("rev", "new", "lrn"):
            if val == "rev":
                n = 2
            elif val == "new":
                n = 0
            else:
                n = 1
            self.lims['card'].append("type = %d" % n)
        elif val == "suspended":
            self.lims['card'].append("queue = -1")
        elif val == "due":
            self.lims['card'].append("(queue = 2 and due <= %d)" %
                                     self.deck.sched.today)

    def _findText(self, val, neg, c):
        val = val.replace("*", "%")
        extra = "not" if neg else ""
        self.lims['args']["_text_%d"%c] = "%"+val+"%"
        self.lims['fact'].append("flds %s like :_text_%d escape '\\'" % (
            extra, c))

    def _findFids(self, val):
        self.lims['fact'].append("id in (%s)" % val)

    def _findTemplate(self, val, isNeg):
        lims = []
        comp = "!=" if isNeg else "="
        found = False
        for m in self.deck.models().values():
            for t in m.templates:
                if t['name'].lower() == val.lower():
                    self.lims['card'].append((
                        "(fid in (select id from facts where mid = %d) "
                        "and ord %s %d)") % (m.id, comp, t['ord']))
                    found = True
        if not found:
            # no such templates exist; artificially limit query
            self.lims['card'].append("ord = -1")

    def findCardsWhere(self):
        self._findLimits()
        x = []
        if self.lims['fact']:
            x.append("fid in (select id from facts where %s)" % " and ".join(
                self.lims['fact']))
        if self.lims['card']:
            x.extend(self.lims['card'])
        q = " and ".join(x)
        return q, self.lims['args']

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
                if type == SEARCH_FIELD_EXISTS:
                #case: field:"value"
                    res.append((token['value'], isNeg, type, 'none'))
                    intoken = doprocess = False
                elif type == SEARCH_FIELD and field:
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
                elif token['value'].startswith("show:"):
                    token['value'] = token['value'][5:].lower()
                    type = SEARCH_DISTINCT
                elif token['value'].startswith("field:"):
                    type = SEARCH_FIELD_EXISTS
                    parts = token['value'][6:].split(':', 1)
                    field = parts[0]
                    if len(parts) == 1 and parts[0]:
                        token['value'] = parts[0]
                    elif len(parts) == 1 and not parts[0]:
                        intoken = True
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
                            #simple fieldname:value case - no need to look for more data
                            addSearchFieldToken(field, parts[1], isNeg)
                            intoken = doprocess = False

                    if intoken is False: phraselog = []
                if intoken is False and doprocess is True:
                    res.append((token['value'], isNeg, type))
        return res

    def findCardsMatchingFilters(deck, filters):
        factFilters = []
        fieldFilters = {}

        factFilterMatches = []
        fieldFilterMatches = []

        if filters:
            for filter in filters:
                if filter['scope'] == 'field':
                    fieldName = filter['field'].lower()
                    if (fieldName in fieldFilters) is False:
                        fieldFilters[fieldName] = []
                    regexp = re.compile(
                        r'\b' + re.escape(filter['value']) + r'\b', flags=re.I)
                    fieldFilters[fieldName].append(
                        {'value': filter['value'], 'regexp': regexp,
                         'is_neg': filter['is_neg']})

            if len(fieldFilters) > 0:
                raise Exception("nyi")
                sfquery = ''
                args = {}
                for field, filters in fieldFilters.iteritems():
                    for filter in filters:
                        c = len(args)
                        if sfquery:
                            if filter['is_neg']:  sfquery += " except "
                            else: sfquery += " intersect "
                        elif filter['is_neg']: sfquery += "select id from fdata except "
                        field = field.replace("*", "%")
                        value = filter['value'].replace("*", "%")
                        args["_ff_%d" % c] = "%"+value+"%"

                        ids = deck.db.list(
                            "select id from fieldmodels where name like "+
                            ":field escape '\\'", field=field)
                        sfquery += ("select id from fdata where "+
                                    "fmid in %s and value like "+
                                    ":_ff_%d escape '\\'") % (ids2str(ids), c)

                rows = deck.db.execute(
                    'select f.fid, f.value, fm.name from fdata as f '+
                    'left join fieldmodels as fm ON (f.fmid = '+
                    'fm.id) where f.id in (' + sfquery + ')', args)
                while (1):
                    row = rows.fetchone()
                    if row is None: break
                    field = row[2].lower()
                    doesMatch = False
                    if field in fieldFilters:
                        for filter in fieldFilters[field]:
                            res = filter['regexp'].search(row[1])
                            if ((filter['is_neg'] is False and res) or
                                (filter['is_neg'] is True and res is None)):
                                fieldFilterMatches.append(row[0])

        fids = None
        if len(factFilters) > 0 or len(fieldFilters) > 0:
            fids = []
            fids.extend(factFilterMatches)
            fids.extend(fieldFilterMatches)

        return fids

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

# Find & sort
##########################################################################

# copied from ankiqt and trivially changed; will not work at the moment

# if idx == 0:
#     self.sortKey = "question"
# elif idx == 1:
#     self.sortKey = "answer"
# elif idx == 2:
#     self.sortKey = "created"
# elif idx == 3:
#     self.sortKey = "modified"
# elif idx == 4:
#     self.sortKey = "combinedDue"
# elif idx == 5:
#     self.sortKey = "interval"
# elif idx == 6:
#     self.sortKey = "reps"
# elif idx == 7:
#     self.sortKey = "factor"
# elif idx == 8:
#     self.sortKey = "fact"
# elif idx == 9:
#     self.sortKey = "noCount"
# elif idx == 10:
#     self.sortKey = "firstAnswered"
# else:
#     self.sortKey = ("field", self.sortFields[idx-11])

def findSorted(deck, query, sortKey):
    # sorting
    if not query.strip():
        ads = ""
    else:
        ids = self.deck.findCards(query)
        ads = "cards.id in %s" % ids2str(ids)
    sort = ""
    if isinstance(sortKey, types.StringType):
        # card property
        if sortKey == "fact":
            sort = "order by facts.created, cards.created"
        else:
            sort = "order by cards." + sortKey
        if sortKey in ("question", "answer"):
            sort += " collate nocase"
        if sortKey == "fact":
            query = """
select cards.id from cards, facts
where cards.fid = facts.id """
            if ads:
                query += "and " + ads + " "
        else:
            query = "select id from cards "
            if ads:
                query += "where %s " % ads
        query += sort
    else:
        # field value
        ret = self.deck.db.all(
            "select id, numeric from fields where name = :name",
            name=sortKey[1])
        fields = ",".join([str(x[0]) for x in ret])
        # if multiple models have the same field, use the first numeric bool
        numeric = ret[0][1]
        if numeric:
            order = "cast(fdata.value as real)"
        else:
            order = "fdata.value collate nocase"
        if ads:
            ads = " and " + ads
        query = ("select cards.id "
                 "from fdata, cards where fdata.fmid in (%s) "
                 "and fdata.fid = cards.fid" + ads +
                 " order by cards.ordinal, %s") % (fields, order)
    # run the query
    self.cards = self.deck.db.all(query)
