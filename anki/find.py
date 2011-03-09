# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

# A lot of findCards() and related functions was contributed by
# Marcus.

import re
from anki.utils import ids2str

SEARCH_TAG = 0
SEARCH_TYPE = 1
SEARCH_PHRASE = 2
SEARCH_FID = 3
SEARCH_CARD = 4
SEARCH_DISTINCT = 5
SEARCH_FIELD = 6
SEARCH_FIELD_EXISTS = 7
SEARCH_QA = 8
SEARCH_PHRASE_WB = 9

# Find
##########################################################################

def findCards(deck, query):
    (q, cmquery, showdistinct, filters, args) = findCardsWhere(deck, query)
    (fidList, cardIdList) = findCardsMatchingFilters(deck, filters)
    query = "select id from cards"
    hasWhere = False
    if q:
        query += " where " + q
        hasWhere = True
    if cmquery['pos'] or cmquery['neg']:
        if hasWhere is False:
            query += " where "
            hasWhere = True
        else: query += " and "
        if cmquery['pos']:
            query += (" fid in(select distinct fid from cards "+
                      "where id in (" + cmquery['pos'] + ")) ")
            query += " and id in(" + cmquery['pos'] + ") "
        if cmquery['neg']:
            query += (" fid not in(select distinct fid from "+
                      "cards where id in (" + cmquery['neg'] + ")) ")
    if fidList is not None:
        if hasWhere is False:
            query += " where "
            hasWhere = True
        else: query += " and "
        query += " fid IN %s" % ids2str(fidList)
    if cardIdList is not None:
        if hasWhere is False:
            query += " where "
            hasWhere = True
        else: query += " and "
        query += " id IN %s" % ids2str(cardIdList)
    if showdistinct:
        query += " group by fid"
    #print query, args
    return deck.db.list(query, **args)

def findCardsWhere(deck, query):
    (tquery, fquery, qquery, fidquery, cmquery, sfquery, qaquery,
     showdistinct, filters, args) = _findCards(deck, query)
    q = ""
    x = []
    if tquery:
        x.append(" fid in (%s)" % tquery)
    if fquery:
        x.append(" fid in (%s)" % fquery)
    if qquery:
        x.append(" id in (%s)" % qquery)
    if fidquery:
        x.append(" id in (%s)" % fidquery)
    if sfquery:
        x.append(" fid in (%s)" % sfquery)
    if qaquery:
        x.append(" id in (%s)" % qaquery)
    if x:
        q += " and ".join(x)
    return q, cmquery, showdistinct, filters, args

def allFMFields(deck, tolower=False):
    fields = []
    try:
        fields = deck.db.list(
            "select distinct name from fieldmodels order by name")
    except:
        fields = []
    if tolower is True:
        for i, v in enumerate(fields):
            fields[i] = v.lower()
    return fields

def _parseQuery(deck, query):
    tokens = []
    res = []

    allowedfields = allFMFields(deck, True)
    def addSearchFieldToken(field, value, isNeg, filter):
        if field.lower() in allowedfields:
            res.append((field + ':' + value, isNeg, SEARCH_FIELD, filter))
        elif field in ['question', 'answer']:
            res.append((field + ':' + value, isNeg, SEARCH_QA, filter))
        else:
            for p in phraselog:
                res.append((p['value'], p['is_neg'], p['type'], p['filter']))
    # break query into words or phraselog
    # an extra space is added so the loop never ends in the middle
    # completing a token
    for match in re.findall(
        r'(-)?\'(([^\'\\]|\\.)*)\'|(-)?"(([^"\\]|\\.)*)"|(-)?([^ ]+)|([ ]+)',
        query + ' '):
        type = ' '
        if match[1]: type = "'"
        elif match[4]: type = '"'

        value = (match[1] or match[4] or match[7])
        isNeg = (match[0] == '-' or match[3] == '-' or match[6] == '-')

        tokens.append({'type': type, 'value': value, 'is_neg': isNeg,
                       'filter': ('wb' if type == "'" else 'none')})
    intoken = isNeg = False
    field = '' #name of the field for field related commands
    phraselog = [] #log of phrases in case potential command is not a commad
    for c, token in enumerate(tokens):
        doprocess = True # only look for commands when this is true
        #prevent cases such as "field" : value as being processed as a command
        if len(token['value']) == 0:
            if intoken is True and type == SEARCH_FIELD and field:
            #case: fieldname: any thing here check for existance of fieldname
                addSearchFieldToken(field, '*', isNeg, 'none')
                phraselog = [] # reset phrases since command is completed
            intoken = doprocess = False
        if intoken is True:
            if type == SEARCH_FIELD_EXISTS:
            #case: field:"value"
                res.append((token['value'], isNeg, type, 'none'))
                intoken = doprocess = False
            elif type == SEARCH_FIELD and field:
            #case: fieldname:"value"
                addSearchFieldToken(
                    field, token['value'], isNeg, token['filter'])
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
                         'type': SEARCH_PHRASE, 'filter': token['filter']})
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
                (p['value'], p['is_neg'], p['type'], p['filter']))
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
                type = SEARCH_CARD
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
                     'type': SEARCH_PHRASE, 'filter': token['filter']})
                if len(parts) == 2 and parts[0]:
                    field = parts[0]
                    if parts[1]:
                        #simple fieldname:value case - no need to look for more data
                        addSearchFieldToken(field, parts[1], isNeg, 'none')
                        intoken = doprocess = False

                if intoken is False: phraselog = []
            if intoken is False and doprocess is True:
                res.append((token['value'], isNeg, type, token['filter']))
    return res

def findCardsMatchingFilters(deck, filters):
    factFilters = []
    fieldFilters = {}
    cardFilters = {}

    factFilterMatches = []
    fieldFilterMatches = []
    cardFilterMatches = []

    if (len(filters) > 0):
        for filter in filters:
            if filter['scope'] == 'fact':
                regexp = re.compile(
                    r'\b' + re.escape(filter['value']) + r'\b', flags=re.I)
                factFilters.append(
                    {'value': filter['value'], 'regexp': regexp,
                     'is_neg': filter['is_neg']})
            if filter['scope'] == 'field':
                fieldName = filter['field'].lower()
                if (fieldName in fieldFilters) is False:
                    fieldFilters[fieldName] = []
                regexp = re.compile(
                    r'\b' + re.escape(filter['value']) + r'\b', flags=re.I)
                fieldFilters[fieldName].append(
                    {'value': filter['value'], 'regexp': regexp,
                     'is_neg': filter['is_neg']})
            if filter['scope'] == 'card':
                fieldName = filter['field'].lower()
                if (fieldName in cardFilters) is False:
                    cardFilters[fieldName] = []
                regexp = re.compile(r'\b' + re.escape(filter['value']) +
                                    r'\b', flags=re.I)
                cardFilters[fieldName].append(
                    {'value': filter['value'], 'regexp': regexp,
                     'is_neg': filter['is_neg']})

        if len(factFilters) > 0:
            fquery = ''
            args = {}
            for filter in factFilters:
                c = len(args)
                if fquery:
                    if filter['is_neg']: fquery += " except "
                    else: fquery += " intersect "
                elif filter['is_neg']: fquery += "select id from fdata except "

                value = filter['value'].replace("*", "%")
                args["_ff_%d" % c] = "%"+value+"%"

                fquery += (
                    "select id from fdata where value like "+
                    ":_ff_%d escape '\\'" % c)

            rows = deck.db.execute(
                'select fid, value from fdata where id in (' +
                fquery + ')', args)
            while (1):
                row = rows.fetchone()
                if row is None: break
                doesMatch = False
                for filter in factFilters:
                    res = filter['regexp'].search(row[1])
                    if ((filter['is_neg'] is False and res) or
                        (filter['is_neg'] is True and res is None)):
                        factFilterMatches.append(row[0])

        if len(fieldFilters) > 0:
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


        if len(cardFilters) > 0:
            qaquery = ''
            args = {}
            for field, filters in cardFilters.iteritems():
                for filter in filters:
                    c = len(args)
                    if qaquery:
                        if filter['is_neg']: qaquery += " except "
                        else: qaquery += " intersect "
                    elif filter['is_neg']: qaquery += "select id from cards except "
                    value = value.replace("*", "%")
                    args["_ff_%d" % c] = "%"+value+"%"

                    if field == 'question':
                        qaquery += "select id from cards where question "
                        qaquery += "like :_ff_%d escape '\\'" % c
                    else:
                        qaquery += "select id from cards where answer "
                        qaquery += "like :_ff_%d escape '\\'" % c

            rows = deck.db.execute(
                'select id, question, answer from cards where id IN (' +
                qaquery + ')', args)
            while (1):
                row = rows.fetchone()
                if row is None: break
                doesMatch = False
                if field in cardFilters:
                    rowValue = row[1] if field == 'question' else row[2]
                    for filter in cardFilters[field]:
                        res = filter['regexp'].search(rowValue)
                        if ((filter['is_neg'] is False and res) or
                            (filter['is_neg'] is True and res is None)):
                            cardFilterMatches.append(row[0])

    fids = None
    if len(factFilters) > 0 or len(fieldFilters) > 0:
        fids = []
        fids.extend(factFilterMatches)
        fids.extend(fieldFilterMatches)

    cardIds = None
    if len(cardFilters) > 0:
        cardIds = []
        cardIds.extend(cardFilterMatches)

    return (fids, cardIds)

def _findCards(deck, query):
    "Find facts matching QUERY."
    tquery = ""
    fquery = ""
    qquery = ""
    fidquery = ""
    cmquery = { 'pos': '', 'neg': '' }
    sfquery = qaquery = ""
    showdistinct = False
    filters = []
    args = {}
    for c, (token, isNeg, type, filter) in enumerate(_parseQuery(deck, query)):
        if type == SEARCH_TAG:
            # a tag
            if tquery:
                if isNeg:
                    tquery += " except "
                else:
                    tquery += " intersect "
            elif isNeg:
                tquery += "select id from facts except "
            if token == "none":
                tquery += """
select id from cards where fid in (select id from facts where tags = '')"""
            else:
                token = token.replace("*", "%")
                if not token.startswith("%"):
                    token = "% " + token
                if not token.endswith("%"):
                    token += " %"
                args["_tag_%d" % c] = token
                tquery += """
select id from facts where tags like :_tag_%d""" % c
        elif type == SEARCH_TYPE:
            if qquery:
                if isNeg:
                    qquery += " except "
                else:
                    qquery += " intersect "
            elif isNeg:
                qquery += "select id from cards except "
            if token in ("rev", "new", "lrn"):
                if token == "rev":
                    n = 1
                elif token == "new":
                    n = 2
                else:
                    n = 0
                qquery += "select id from cards where type = %d" % n
            elif token == "delayed":
                print "delayed"
                qquery += ("select id from cards where "
                           "due < %d and due > %d and "
                           "type in (0,1,2)") % (
                    deck.dayCutoff, deck.dayCutoff)
            elif token == "suspended":
                qquery += ("select id from cards where "
                           "queue = -1")
            elif token == "leech":
                qquery += (
                    "select id from cards where noCount >= (select value "
                    "from deckvars where key = 'leechFails')")
            else: # due
                qquery += ("select id from cards where "
                           "queue between 0 and 1 and due < %d") % deck.dayCutoff
        elif type == SEARCH_FID:
            if fidquery:
                if isNeg:
                    fidquery += " except "
                else:
                    fidquery += " intersect "
            elif isNeg:
                fidquery += "select id from cards except "
            fidquery += "select id from cards where fid in (%s)" % token
        elif type == SEARCH_CARD:
            print "search_card broken"
            token = token.replace("*", "%")
            ids = deck.db.list("""
select id from tags where name like :tag escape '\\'""", tag=token)
            if isNeg:
                if cmquery['neg']:
                    cmquery['neg'] += " intersect "
                cmquery['neg'] += """
select cardId from cardTags where src = 2 and cardTags.tagId in %s""" % ids2str(ids)
            else:
                if cmquery['pos']:
                    cmquery['pos'] += " intersect "
                cmquery['pos'] += """
select cardId from cardTags where src = 2 and cardTags.tagId in %s""" % ids2str(ids)
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
            if (type == SEARCH_FIELD and filter != 'none'):
                if field and value:
                    filters.append(
                        {'scope': 'field', 'type': filter,
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
                    args["_ff_%d" % c] = "%"+value+"%"
                    ids = deck.db.list("""
select id from fieldmodels where name like :field escape '\\'""", field=field)
                    sfquery += """
select fid from fdata where fmid in %s and
value like :_ff_%d escape '\\'""" % (ids2str(ids), c)
        elif type == SEARCH_QA:
            field = value = ''
            parts = token.split(':', 1);
            if len(parts) == 2:
                field = parts[0]
                value = parts[1]
            if (filter != 'none'):
                if field and value:
                    filters.append(
                        {'scope': 'card', 'type': filter, 'field': field,
                         'value': value, 'is_neg': isNeg})
            else:
                if field and value:
                    if qaquery:
                        if isNeg:
                            qaquery += " except "
                        else:
                            qaquery += " intersect "
                    elif isNeg:
                        qaquery += "select id from cards except "
                    value = value.replace("*", "%")
                    args["_ff_%d" % c] = "%"+value+"%"

                    if field == 'question':
                        qaquery += """
select id from cards where question like :_ff_%d escape '\\'""" % c
                    else:
                        qaquery += """
select id from cards where answer like :_ff_%d escape '\\'""" % c
        elif type == SEARCH_DISTINCT:
            if isNeg is False:
                showdistinct = True if token == "one" else False
            else:
                showdistinct = False if token == "one" else True
        else:
            if (filter != 'none'):
                filters.append(
                    {'scope': 'fact', 'type': filter,
                     'value': token, 'is_neg': isNeg})
            else:
                if fquery:
                    if isNeg:
                        fquery += " except "
                    else:
                        fquery += " intersect "
                elif isNeg:
                    fquery += "select id from facts except "
                token = token.replace("*", "%")
                args["_ff_%d" % c] = "%"+token+"%"
                fquery += """
select id from facts where flds like :_ff_%d escape '\\'""" % c
    return (tquery, fquery, qquery, fidquery, cmquery, sfquery,
            qaquery, showdistinct, filters, args)

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
