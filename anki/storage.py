# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

CURRENT_VERSION = 100

import os, time, simplejson, re, datetime
from anki.lang import _
from anki.utils import intTime
from anki.db import DB
from anki.deck import _Deck
from anki.stdmodels import addBasicModel, addClozeModel
from anki.errors import AnkiError
from anki.hooks import runHook

def Deck(path, queue=True, lock=True):
    "Open a new or existing deck. Path must be unicode."
    path = os.path.abspath(path)
    create = not os.path.exists(path)
    if create:
        base = os.path.basename(path)
        for c in ("/", ":", "\\"):
            assert c not in base
    # connect
    db = DB(path)
    if create:
        ver = _createDB(db)
    else:
        ver = _upgradeSchema(db)
    db.execute("pragma temp_store = memory")
    db.execute("pragma cache_size = 10000")
    # add db to deck and do any remaining upgrades
    deck = _Deck(db)
    if ver < CURRENT_VERSION:
        _upgradeDeck(deck, ver)
    elif create:
        # add in reverse order so basic is default
        addClozeModel(deck)
        addBasicModel(deck)
        deck.save()
    if lock:
        deck.lock()
    if not queue:
        return deck
    # rebuild queue
    deck.reset()
    return deck

def _createDB(db):
    db.execute("pragma page_size = 4096")
    db.execute("pragma legacy_file_format = 0")
    db.execute("vacuum")
    _addSchema(db)
    _updateIndices(db)
    db.execute("analyze")
    return CURRENT_VERSION

def _addSchema(db, setDeckConf=True):
    db.executescript("""
create table if not exists deck (
    id              integer primary key,
    crt             integer not null,
    mod             integer not null,
    scm             integer not null,
    ver             integer not null,
    dty             integer not null,
    usn             integer not null,
    ls              integer not null,
    conf            text not null,
    models          text not null,
    groups          text not null,
    gconf           text not null,
    tags            text not null
);

create table if not exists facts (
    id              integer primary key,
    guid            integer not null,
    mid             integer not null,
    gid             integer not null,
    mod             integer not null,
    usn             integer not null,
    tags            text not null,
    flds            text not null,
    sfld            integer not null,
    flags           integer not null,
    data            text not null
);

create table if not exists fsums (
    fid             integer not null,
    mid             integer not null,
    csum            integer not null
);
create table if not exists cards (
    id              integer primary key,
    fid             integer not null,
    gid             integer not null,
    ord             integer not null,
    mod             integer not null,
    usn             integer not null,
    type            integer not null,
    queue           integer not null,
    due             integer not null,
    ivl             integer not null,
    factor          integer not null,
    reps            integer not null,
    lapses          integer not null,
    left            integer not null,
    edue            integer not null,
    flags           integer not null,
    data            text not null
);

create table if not exists revlog (
    id              integer primary key,
    cid             integer not null,
    usn             integer not null,
    ease            integer not null,
    ivl             integer not null,
    lastIvl         integer not null,
    factor          integer not null,
    time            integer not null,
    type            integer not null
);

create table if not exists graves (
    usn             integer not null,
    oid             integer not null,
    type            integer not null
);

insert or ignore into deck
values(1,0,0,0,%(v)s,0,0,0,'','{}','','','{}');
""" % ({'v':CURRENT_VERSION}))
    import anki.deck
    if setDeckConf:
        _addDeckVars(db, *_getDeckVars(db))

def _getDeckVars(db):
    import anki.groups
    g = anki.groups.defaultGroup.copy()
    for k,v in anki.groups.defaultTopConf.items():
        g[k] = v
    g['id'] = 1
    g['name'] = _("Default")
    g['conf'] = 1
    g['mod'] = intTime()
    gc = anki.groups.defaultConf.copy()
    gc['id'] = 1
    return g, gc, anki.deck.defaultConf.copy()

def _addDeckVars(db, g, gc, c):
    db.execute("""
update deck set conf = ?, groups = ?, gconf = ?""",
                   simplejson.dumps(c),
                   simplejson.dumps({'1': g}),
                   simplejson.dumps({'1': gc}))

def _updateIndices(db):
    "Add indices to the DB."
    db.executescript("""
-- avoid loading entire facts table in for sync summary
create index if not exists ix_facts_usn on facts (usn);
-- card spacing, etc
create index if not exists ix_cards_fid on cards (fid);
-- scheduling and group limiting
create index if not exists ix_cards_sched on cards (gid, queue, due);
-- revlog by card
create index if not exists ix_revlog_cid on revlog (cid);
-- revlog syncing
create index if not exists ix_revlog_usn on revlog (usn);
-- field uniqueness check
create index if not exists ix_fsums_fid on fsums (fid);
create index if not exists ix_fsums_csum on fsums (csum);
""")

# 2.0 schema migration
######################################################################

def _moveTable(db, table, cards=False):
    if cards:
        insExtra = " order by created"
    else:
        insExtra = ""
    sql = db.scalar(
        "select sql from sqlite_master where name = '%s'" % table)
    sql = sql.replace("TABLE "+table, "temporary table %s2" % table)
    if cards:
        sql = sql.replace("PRIMARY KEY (id),", "")
    db.execute(sql)
    db.execute("insert into %s2 select * from %s%s" % (table, table, insExtra))
    db.execute("drop table "+table)
    _addSchema(db, False)

def _upgradeSchema(db):
    "Alter tables prior to ORM initialization."
    try:
        ver = db.scalar("select ver from deck")
    except:
        ver = db.scalar("select version from decks")
    # latest 1.2 is 65
    if ver < 65:
        raise AnkiError("oldDeckVersion")
    if ver > 99:
        return ver
    runHook("1.x upgrade", db)

    # facts
    ###########
    # tags should have a leading and trailing space if not empty, and not
    # use commas
    db.execute("""
update facts set tags = (case
when trim(tags) == "" then ""
else " " || replace(replace(trim(tags), ",", " "), "  ", " ") || " "
end)
""")
    # pull facts into memory, so we can merge them with fields efficiently
    facts = db.all("""
select id, id, modelId, 1, cast(created*1000 as int), cast(modified as int), 0, tags
from facts order by created""")
    # build field hash
    fields = {}
    for (fid, ord, val) in db.execute(
        "select factId, ordinal, value from fields order by factId, ordinal"):
        if fid not in fields:
            fields[fid] = []
        fields[fid].append((ord, val))
    # build insert data and transform ids, and minimize qt's
    # bold/italics/underline cruft.
    map = {}
    data = []
    factidmap = {}
    times = {}
    from anki.utils import minimizeHTML
    for c, row in enumerate(facts):
        oldid = row[0]
        row = list(row)
        # get rid of old created column and update id
        while row[4] in times:
            row[4] += 1
        times[row[4]] = True
        factidmap[row[0]] = row[4]
        row[0] = row[4]
        del row[4]
        map[oldid] = row[0]
        row.append(minimizeHTML("\x1f".join([x[1] for x in sorted(fields[oldid])])))
        data.append(row)
    # and put the facts into the new table
    db.execute("drop table facts")
    _addSchema(db, False)
    db.executemany("insert into facts values (?,?,?,?,?,?,?,?,'',0,'')", data)
    db.execute("drop table fields")

    # cards
    ###########
    # we need to pull this into memory, to rewrite the creation time if
    # it's not unique and update the fact id
    times = {}
    rows = []
    cardidmap = {}
    for row in db.execute("""
select id, cast(created*1000 as int), factId, ordinal,
cast(modified as int), 0,
(case relativeDelay
when 0 then 1
when 1 then 2
when 2 then 0 end),
(case type
when 0 then 1
when 1 then 2
when 2 then 0
else type end),
cast(due as int), cast(interval as int),
cast(factor*1000 as int), reps, noCount from cards
order by created"""):
        # find an unused time
        row = list(row)
        while row[1] in times:
            row[1] += 1
        times[row[1]] = True
        # rewrite fact id
        row[2] = factidmap[row[2]]
        # note id change and save all but old id
        cardidmap[row[0]] = row[1]
        rows.append(row[1:])
    # drop old table and rewrite
    db.execute("drop table cards")
    _addSchema(db, False)
    db.executemany("""
insert into cards values (?,?,1,?,?,?,?,?,?,?,?,?,?,0,0,0,"")""",
                   rows)

    # reviewHistory -> revlog
    ###########
    # fetch the data so we can rewrite ids quickly
    r = []
    for row in db.execute("""
select
cast(time*1000 as int), cardId, 0, ease,
cast(nextInterval as int), cast(lastInterval as int),
cast(nextFactor*1000 as int), cast(min(thinkingTime, 60)*1000 as int),
yesCount from reviewHistory"""):
        row = list(row)
        # new card ids
        try:
            row[1] = cardidmap[row[1]]
        except:
            # id doesn't exist
            continue
        # no ease 0 anymore
        row[2] = row[2] or 1
        # determine type, overwriting yesCount
        newInt = row[3]
        oldInt = row[4]
        yesCnt = row[7]
        # yesCnt included the current answer
        if row[2] > 1:
            yesCnt -= 1
        if oldInt < 1:
            # new or failed
            if yesCnt:
                # type=relrn
                row[7] = 2
            else:
                # type=lrn
                row[7] = 0
        else:
            # type=rev
            row[7] = 1
        r.append(row)
    db.executemany(
        "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", r)
    db.execute("drop table reviewHistory")

    # deck
    ###########
    _migrateDeckTbl(db)

    # tags
    ###########
    tags = {}
    for t in db.list("select tag from tags"):
        tags[t] = intTime()
    db.execute("update deck set tags = ?", simplejson.dumps(tags))
    db.execute("drop table tags")
    db.execute("drop table cardTags")

    # the rest
    ###########
    db.execute("drop table media")
    db.execute("drop table sources")
    _migrateModels(db)
    _updateIndices(db)
    return ver

def _migrateDeckTbl(db):
    import anki.deck
    db.execute("delete from deck")
    db.execute("""
insert or replace into deck select id, cast(created as int), :t,
:t, 99, 0, 0, cast(lastSync as int),
"", "", "", "", "" from decks""", t=intTime())
    # prepare a group to store the old deck options
    g, gc, conf = _getDeckVars(db)
    # delete old selective study settings, which we can't auto-upgrade easily
    keys = ("newActive", "newInactive", "revActive", "revInactive")
    for k in keys:
        db.execute("delete from deckVars where key=:k", k=k)
    # copy other settings, ignoring deck order as there's a new default
    g['newSpread'] = db.scalar("select newCardSpacing from decks")
    g['newPerDay'] = db.scalar("select newCardsPerDay from decks")
    g['repLim'] = db.scalar("select sessionRepLimit from decks")
    g['timeLim'] = db.scalar("select sessionTimeLimit from decks")
    # this needs to be placed in the model later on
    conf['oldNewOrder'] = db.scalar("select newCardOrder from decks")
    # no reverse option anymore
    conf['oldNewOrder'] = min(1, conf['oldNewOrder'])
    # add any deck vars and save
    dkeys = ("hexCache", "cssCache")
    for (k, v) in db.execute("select * from deckVars").fetchall():
        if k in dkeys:
            pass
        else:
            conf[k] = v
    _addDeckVars(db, g, gc, conf)
    # clean up
    db.execute("drop table decks")
    db.execute("drop table deckVars")

def _migrateModels(db):
    import anki.models
    times = {}
    mods = {}
    for row in db.all(
        "select id, name from models"):
        while 1:
            t = intTime(1000)
            if t not in times:
                times[t] = True
                break
        m = anki.models.defaultModel.copy()
        m['id'] = t
        m['name'] = row[1]
        m['mod'] = intTime()
        m['tags'] = []
        m['flds'] = _fieldsForModel(db, row[0])
        m['tmpls'] = _templatesForModel(db, row[0], m['flds'])
        mods[m['id']] = m
        db.execute("update facts set mid = ? where mid = ?", t, row[0])
    # save and clean up
    db.execute("update deck set models = ?", simplejson.dumps(mods))
    db.execute("drop table fieldModels")
    db.execute("drop table cardModels")
    db.execute("drop table models")

def _fieldsForModel(db, mid):
    import anki.models
    dconf = anki.models.defaultField
    flds = []
    for c, row in enumerate(db.all("""
select name, features, required, "unique",
quizFontFamily, quizFontSize, quizFontColour, editFontSize from fieldModels
where modelId = ?
order by ordinal""", mid)):
        conf = dconf.copy()
        (conf['name'],
         conf['rtl'],
         conf['req'],
         conf['uniq'],
         conf['font'],
         conf['qsize'],
         conf['qcol'],
         conf['esize']) = row
        conf['ord'] = c
        # ensure data is good
        conf['rtl'] = not not conf['rtl']
        conf['pre'] = True
        conf['font'] = conf['font'] or "Arial"
        conf['qcol'] = conf['qcol'] or "#000"
        conf['qsize'] = conf['qsize'] or 20
        conf['esize'] = conf['esize'] or 20
        flds.append(conf)
    return flds

def _templatesForModel(db, mid, flds):
    import anki.models
    dconf = anki.models.defaultTemplate
    tmpls = []
    for c, row in enumerate(db.all("""
select name, active, qformat, aformat, questionInAnswer,
questionAlign, lastFontColour, allowEmptyAnswer, typeAnswer from cardModels
where modelId = ?
order by ordinal""", mid)):
        conf = dconf.copy()
        (conf['name'],
         conf['actv'],
         conf['qfmt'],
         conf['afmt'],
         conf['hideQ'],
         conf['align'],
         conf['bg'],
         conf['emptyAns'],
         conf['typeAns']) = row
        conf['ord'] = c
        # convert the field name to an ordinal
        ordN = None
        for (ord, fm) in enumerate(flds):
            if fm['name'] == conf['typeAns']:
                ordN = ord
                break
        if ordN is not None:
            conf['typeAns'] = ordN
        else:
            conf['typeAns'] = None
        for type in ("qfmt", "afmt"):
            # ensure the new style field format
            conf[type] = re.sub("%\((.+?)\)s", "{{\\1}}", conf[type])
            # some special names have changed
            conf[type] = re.sub(
                "(?i){{tags}}", "{{Tags}}", conf[type])
            conf[type] = re.sub(
                "(?i){{cardModel}}", "{{Template}}", conf[type])
            conf[type] = re.sub(
                "(?i){{modelTags}}", "{{Model}}", conf[type])
        tmpls.append(conf)
    return tmpls

def _postSchemaUpgrade(deck):
    "Handle the rest of the upgrade to 2.0."
    import anki.deck
    # make sure we have a current model id
    deck.models.setCurrent(deck.models.models.values()[0])
    # regenerate css, and set new card order
    for m in deck.models.all():
        m['newOrder'] = deck.conf['oldNewOrder']
        deck.models.save(m)
    del deck.conf['oldNewOrder']
    # fix creation time
    deck.sched._updateCutoff()
    d = datetime.datetime.today()
    d -= datetime.timedelta(hours=4)
    d = datetime.datetime(d.year, d.month, d.day)
    d += datetime.timedelta(hours=4)
    d -= datetime.timedelta(days=1+int((time.time()-deck.crt)/86400))
    deck.crt = int(time.mktime(d.timetuple()))
    deck.sched._updateCutoff()
    # update uniq cache
    deck.updateFieldCache(deck.db.list("select id from facts"))
    # remove old views
    for v in ("failedCards", "revCardsOld", "revCardsNew",
              "revCardsDue", "revCardsRandom", "acqCardsRandom",
              "acqCardsOld", "acqCardsNew"):
        deck.db.execute("drop view if exists %s" % v)
    # remove stats, as it's all in the revlog now
    deck.db.execute("drop table if exists stats")
    # suspended cards don't use ranges anymore
    deck.db.execute("update cards set queue=-1 where queue between -3 and -1")
    deck.db.execute("update cards set queue=-2 where queue between 3 and 5")
    deck.db.execute("update cards set queue=-3 where queue between 6 and 8")
    # remove old deleted tables
    for t in ("cards", "facts", "models", "media"):
        deck.db.execute("drop table if exists %sDeleted" % t)
    # rewrite due times for new cards
    deck.db.execute("""
update cards set due = fid where type=0""")
    # and failed cards
    left = len(deck.groups.conf(1)['new']['delays'])
    deck.db.execute("update cards set edue = ?, left=? where type = 1",
                    deck.sched.today+1, left)
    # and due cards
    deck.db.execute("""
update cards set due = cast(
(case when due < :stamp then 0 else 1 end) +
((due-:stamp)/86400) as int)+:today where type = 2
""", stamp=deck.sched.dayCutoff, today=deck.sched.today)
    # possibly re-randomize
    if deck.models.randomNew():
        deck.sched.randomizeCards()
    # update insertion id
    deck.conf['nextPos'] = deck.db.scalar("select max(id) from facts")+1
    deck.save()

    # optimize and finish
    deck.db.commit()
    deck.db.execute("vacuum")
    deck.db.execute("analyze")
    deck.db.execute("update deck set ver = ?", CURRENT_VERSION)
    deck.save()

# Post-init upgrade
######################################################################

def _upgradeDeck(deck, version):
    "Upgrade deck to the latest version."
    if version >= CURRENT_VERSION:
        return
    if version < 100:
        _postSchemaUpgrade(deck)
