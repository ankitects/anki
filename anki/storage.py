# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

CURRENT_VERSION = 100

import os, time, simplejson, re, datetime
from anki.lang import _
from anki.utils import intTime
from anki.db import DB
from anki.deck import _Deck
from anki.stdmodels import BasicModel, ClozeModel
from anki.errors import AnkiError

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
    db.execute("pragma cache_size = 20000")
    # add db to deck and do any remaining upgrades
    deck = _Deck(db)
    if ver < CURRENT_VERSION:
        _upgradeDeck(deck, ver)
    elif create:
        deck.addModel(BasicModel(deck))
        deck.addModel(ClozeModel(deck))
        # default to basic
        deck.conf['currentModelId'] = 1
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
    syncName        text not null,
    lastSync        integer not null,
    qconf           text not null,
    conf            text not null,
    data            text not null
);

create table if not exists cards (
    id              integer primary key,
    fid             integer not null,
    gid             integer not null,
    ord             integer not null,
    crt             integer not null,
    mod             integer not null,
    type            integer not null,
    queue           integer not null,
    due             integer not null,
    ivl             integer not null,
    factor          integer not null,
    reps            integer not null,
    streak          integer not null,
    lapses          integer not null,
    grade           integer not null,
    cycles          integer not null,
    edue            integer not null,
    data            text not null
);

create table if not exists facts (
    id              integer primary key,
    mid             integer not null,
    gid             integer not null,
    crt             integer not null,
    mod             integer not null,
    tags            text not null,
    flds            text not null,
    sfld            text not null,
    data            text not null
);

create table if not exists fsums (
    fid             integer not null,
    mid             integer not null,
    csum            integer not null
);

create table if not exists models (
    id              integer primary key,
    mod             integer not null,
    name            text not null,
    flds            text not null,
    tmpls           text not null,
    conf            text not null,
    css             text not null
);

create table if not exists groups (
    id              integer primary key,
    mod             integer not null,
    name            text not null,
    gcid            integer not null
);

create table if not exists gconf (
    id              integer primary key,
    mod             integer not null,
    name            text not null,
    conf            text not null
);

create table if not exists revlog (
    time            integer primary key,
    cid             integer not null,
    ease            integer not null,
    rep             integer not null,
    ivl             integer not null,
    lastIvl         integer not null,
    factor          integer not null,
    taken           integer not null,
    type            integer not null
);

create table if not exists tags (
    id              integer primary key,
    mod             integer not null,
    name            text not null collate nocase unique
);

insert or ignore into deck
values(1,0,0,0,%(v)s,0,'',0,'', '', '');
""" % ({'v':CURRENT_VERSION}))
    import anki.deck
    import anki.groups
    # create a default group/configuration, which should not be removed
    db.execute(
        "insert or ignore into gconf values (1, ?, ?, ?)""",
        intTime(), _("Default Config"),
        simplejson.dumps(anki.groups.defaultConf))
    db.execute(
        "insert or ignore into groups values (1, ?, ?, 1)",
        intTime(), _("Default Group"))
    if setDeckConf:
        db.execute("update deck set qconf = ?, conf = ?, data = ?",
                   simplejson.dumps(anki.deck.defaultQconf),
                   simplejson.dumps(anki.deck.defaultConf),
                   "{}")

def _updateIndices(db):
    "Add indices to the DB."
    db.executescript("""
-- avoid loading entire facts table in for sync summary
create index if not exists ix_facts_mod on facts (mod);
-- card spacing, etc
create index if not exists ix_cards_fid on cards (fid);
-- revlog by card
create index if not exists ix_revlog_cid on revlog (cid);
-- field uniqueness check
create index if not exists ix_fsums_fid on fsums (fid);
create index if not exists ix_fsums_csum on fsums (csum);
""")

# 2.0 schema migration
######################################################################
# we don't have access to the progress handler at this point, so the GUI code
# will need to set up a progress handling window before opening a deck.

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

def _insertWithIdChange(db, map, idx, table, numVals):
    "Fetching and re-inserting is a lot faster than row by row updates."
    data = []
    for row in db.all("select * from %s" % table):
        row = list(row)
        try:
            row[idx] = map[row[idx]]
            data.append(row)
        except:
            # referenced non-existant object
            pass
    db.execute("delete from %s" % table)
    db.executemany(
        "insert into %s values (?%s)" % (table, ",?"*(numVals-1)),
        data)

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

    # cards
    ###########
    # move into temp table
    _moveTable(db, "cards", True)
    # use the new order to rewrite card ids
    cardmap = dict(db.all("select id, rowid from cards2"))
    # move back, preserving new ids, and rewriting types
    db.execute("""
insert into cards select rowid, factId, 1, ordinal, cast(created as int),
cast(modified as int),
(case relativeDelay
when 0 then 1
when 1 then 2
when 2 then 0 end),
(case type
when 0 then 1
when 1 then 2
when 2 then 0
else type end),
due, cast(interval as int),
cast(factor*1000 as int), reps, successive, noCount, 0, 0, 0, "" from cards2
order by created""")
    db.execute("drop table cards2")

    # tags
    ###########
    _moveTable(db, "tags")
    db.execute("insert or ignore into tags select id, ?, tag from tags2",
               intTime())
    db.execute("drop table tags2")
    db.execute("drop table cardTags")

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
select id, modelId, 1, cast(created as int), cast(modified as int), tags
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
    from anki.utils import minimizeHTML
    for c, row in enumerate(facts):
        oldid = row[0]
        map[oldid] = c+1
        row = list(row)
        row[0] = c+1
        row.append(minimizeHTML("\x1f".join([x[1] for x in sorted(fields[oldid])])))
        data.append(row)
    # use the new order to rewrite fact ids in cards table
    _insertWithIdChange(db, map, 1, "cards", 18)
    # and put the facts into the new table
    db.execute("drop table facts")
    _addSchema(db, False)
    db.executemany("insert into facts values (?,?,?,?,?,?,?,'','')", data)
    db.execute("drop table fields")

    # media
    ###########
    db.execute("drop table media")

    # models
    ###########
    import anki.models
    _moveTable(db, "models")
    db.execute("""
insert into models select id, cast(modified as int),
name, "{}", "{}", ?, "" from models2""", simplejson.dumps(
    anki.models.defaultConf))
    db.execute("drop table models2")

    # reviewHistory -> revlog
    ###########
    # fetch the data so we can rewrite ids quickly
    r = []
    for row in db.execute("""
select
cast(time*1000 as int), cardId, ease, reps,
cast(lastInterval as int), cast(nextInterval as int),
cast(nextFactor*1000 as int), cast(min(thinkingTime, 60)*1000 as int),
1 from reviewHistory"""):
        row = list(row)
        # new card ids
        try:
            row[1] = cardmap[row[1]]
        except:
            # id doesn't exist
            continue
        # no ease 0 anymore
        row[2] = row[2] or 1
        r.append(row)
    db.executemany(
        "insert or ignore into revlog values (?,?,?,?,?,?,?,?,?)", r)
    db.execute("drop table reviewHistory")

    # longer migrations
    ###########
    _migrateDeckTbl(db)
    mods = _migrateFieldsTbl(db)
    _migrateTemplatesTbl(db, mods)

    _updateIndices(db)
    return ver

def _migrateDeckTbl(db):
    import anki.deck
    db.execute("delete from deck")
    db.execute("""
insert or replace into deck select id, cast(created as int), :t,
:t, 99, 0, ifnull(syncName, ""), cast(lastSync as int),
"", "", "" from decks""", t=intTime())
    # update selective study
    qconf = anki.deck.defaultQconf.copy()
    # delete old selective study settings, which we can't auto-upgrade easily
    keys = ("newActive", "newInactive", "revActive", "revInactive")
    for k in keys:
        db.execute("delete from deckVars where key=:k", k=k)
    # copy other settings, ignoring deck order as there's a new default
    keys = ("newCardOrder", "newCardSpacing")
    for k in keys:
        qconf[k] = db.scalar("select %s from decks" % k)
    qconf['newPerDay'] = db.scalar(
        "select newCardsPerDay from decks")
    # fetch remaining settings from decks table
    conf = anki.deck.defaultConf.copy()
    data = {}
    keys = ("sessionRepLimit", "sessionTimeLimit")
    for k in keys:
        conf[k] = db.scalar("select %s from decks" % k)
    # random and due options merged
    qconf['revCardOrder'] = min(2, qconf['revCardOrder'])
    # no reverse option anymore
    qconf['newCardOrder'] = min(1, qconf['newCardOrder'])
    # add any deck vars and save
    dkeys = ("hexCache", "cssCache")
    for (k, v) in db.execute("select * from deckVars").fetchall():
        if k in dkeys:
            pass
        else:
            conf[k] = v
    db.execute("update deck set qconf = :l, conf = :c, data = :d",
               l=simplejson.dumps(qconf),
               c=simplejson.dumps(conf),
               d=simplejson.dumps(data))
    # clean up
    db.execute("drop table decks")
    db.execute("drop table deckVars")

def _migrateFieldsTbl(db):
    import anki.models
    dconf = anki.models.defaultField
    mods = {}
    for row in db.all("""
select modelId, name, features, required, "unique",
quizFontFamily, quizFontSize, quizFontColour, editFontSize from fieldModels
order by modelId, ordinal"""):
        conf = dconf.copy()
        if row[0] not in mods:
            mods[row[0]] = []
        (conf['name'],
         conf['rtl'],
         conf['req'],
         conf['uniq'],
         conf['font'],
         conf['qsize'],
         conf['qcol'],
         conf['esize']) = row[1:]
        # ensure data is good
        conf['rtl'] = not not conf['rtl']
        conf['pre'] = True
        conf['font'] = conf['font'] or "Arial"
        conf['qcol'] = conf['qcol'] or "#000"
        conf['qsize'] = conf['qsize'] or 20
        conf['esize'] = conf['esize'] or 20
        mods[row[0]].append(conf)
    # now we've gathered all the info, save it into the models
    for mid, fms in mods.items():
        db.execute("update models set flds = ? where id = ?",
                   simplejson.dumps(fms), mid)
    # clean up
    db.execute("drop table fieldModels")
    return mods

def _migrateTemplatesTbl(db, fmods):
    import anki.models
    dconf = anki.models.defaultTemplate
    mods = {}
    for row in db.all("""
select modelId, name, active, qformat, aformat, questionInAnswer,
questionAlign, lastFontColour, allowEmptyAnswer, typeAnswer from cardModels
order by modelId, ordinal"""):
        conf = dconf.copy()
        if row[0] not in mods:
            mods[row[0]] = []
        (conf['name'],
         conf['actv'],
         conf['qfmt'],
         conf['afmt'],
         conf['hideQ'],
         conf['align'],
         conf['bg'],
         conf['emptyAns'],
         conf['typeAns']) = row[1:]
        # convert the field name to an ordinal
        ordN = None
        for (ord, fm) in enumerate(fmods[row[0]]):
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
        mods[row[0]].append(conf)
    # now we've gathered all the info, save it into the models
    for mid, tmpls in mods.items():
        db.execute("update models set tmpls = ? where id = ?",
                   simplejson.dumps(tmpls), mid)
    # clean up
    db.execute("drop table cardModels")
    return mods

def _fixupModels(deck):
    # rewrite model/template/field ids
    models = deck.models()
    deck.db.execute("delete from models")
    for c, m in enumerate(models.values()):
        # update ordinals
        m._updateFieldOrds()
        m._updateTemplOrds()
        # rewrite id
        old = m.id
        m.id = c+1
        m.flush()
        deck.db.execute("update facts set mid = ? where mid = ?", m.id, old)

def _postSchemaUpgrade(deck):
    "Handle the rest of the upgrade to 2.0."
    import anki.deck
    # adjust models
    _fixupModels(deck)
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
    deck.db.execute("update cards set edue = ? where type = 1",
                    deck.sched.today+1)
    # and due cards
    deck.db.execute("""
update cards set due = cast(
(case when due < :stamp then 0 else 1 end) +
((due-:stamp)/86400) as int)+:today where type = 2
""", stamp=deck.sched.dayCutoff, today=deck.sched.today)
    # update insertion id
    deck.conf['nextFid'] = deck.db.scalar("select max(id) from facts")+1
    deck.conf['nextCid'] = deck.db.scalar("select max(id) from cards")+1
    deck.save()

    # optimize and finish
    deck.sched.updateDynamicIndices()
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
