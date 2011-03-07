# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

CURRENT_VERSION = 100

import os, time, simplejson, re
from anki.lang import _
from anki.utils import intTime
from anki.db import DB
from anki.deck import _Deck
from anki.stdmodels import BasicModel
from anki.errors import AnkiError

def Deck(path, queue=True):
    "Open a new or existing deck. Path must be unicode."
    path = os.path.abspath(path)
    create = not os.path.exists(path)
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
        deck.save()
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
    created         integer not null,
    mod             integer not null,
    schema          integer not null,
    version         integer not null,
    syncName        text not null,
    lastSync        integer not null,
    utcOffset       integer not null,
    qconf           text not null,
    conf            text not null,
    data            text not null
);

create table if not exists cards (
    id              integer primary key,
    fid             integer not null,
    tid             integer not null,
    gid             integer not null,
    mod             integer not null,
    q               text not null,
    a               text not null,
    ord             integer not null,
    type            integer not null,
    queue           integer not null,
    due             integer not null,
    interval        integer not null,
    factor          integer not null,
    reps            integer not null,
    streak          integer not null,
    lapses          integer not null,
    grade           integer not null,
    cycles          integer not null
);

create table if not exists facts (
    id              integer primary key,
    mid             integer not null,
    mod             integer not null,
    pos             integer not null,
    tags            text not null,
    cache           text not null
);

create table if not exists models (
    id              integer primary key,
    mod             integer not null,
    name            text not null,
    conf            text not null
);

create table if not exists fields (
    id              integer primary key,
    mid             integer not null,
    ord             integer not null,
    name            text not null,
    numeric         integer not null,
    conf            text not null
);

create table if not exists templates (
    id              integer primary key,
    mid             integer not null,
    ord             integer not null,
    name            text not null,
    active          integer not null,
    qfmt            text not null,
    afmt            text not null,
    conf            text not null
);

create table if not exists fdata (
    fid             integer not null,
    fmid            integer not null,
    ord             integer not null,
    val             text not null,
    csum            text not null
);

create table if not exists graves (
    delTime         integer not null,
    objectId        integer not null,
    type            integer not null
);

create table if not exists gconf (
    id              integer primary key,
    mod             integer not null,
    name            text not null,
    conf            text not null
);

create table if not exists groups (
    id              integer primary key autoincrement,
    mod             integer not null,
    name            text not null,
    gcid            integer not null
);

create table if not exists media (
    file            text primary key,
    mod             integer not null,
    csum            text not null
);

create table if not exists revlog (
    time            integer primary key,
    cid             integer not null,
    ease            integer not null,
    rep             integer not null,
    lastInt         integer not null,
    interval        integer not null,
    factor          integer not null,
    userTime        integer not null,
    flags           integer not null
);

create table if not exists tags (
    id              integer primary key,
    mod             integer not null,
    name            text not null collate nocase unique
);

insert or ignore into deck
values(1,%(t)s,%(t)s,%(t)s,%(v)s,'',0,-2,'', '', '');
""" % ({'t': intTime(), 'v':CURRENT_VERSION}))
    import anki.deck
    import anki.groups
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
-- sync summaries
create index if not exists ix_cards_mod on cards (mod);
create index if not exists ix_facts_mod on facts (mod);
-- card spacing
create index if not exists ix_cards_fid on cards (fid);
-- fact data
create index if not exists ix_fdata_fid on fdata (fid);
create index if not exists ix_fdata_csum on fdata (csum);
-- media
create index if not exists ix_media_csum on media (csum);
-- deletion tracking
create index if not exists ix_graves_delTime on graves (delTime);
""")

# 2.0 schema migration
######################################################################
# we don't have access to the progress handler at this point, so the GUI code
# will need to set up a progress handling window before opening a deck.

def _moveTable(db, table):
    sql = db.scalar(
        "select sql from sqlite_master where name = '%s'" % table)
    sql = sql.replace("TABLE "+table, "temporary table %s2" % table)
    db.execute(sql)
    db.execute("insert into %s2 select * from %s" % (table, table))
    db.execute("drop table "+table)
    _addSchema(db, False)

def _upgradeSchema(db):
    "Alter tables prior to ORM initialization."
    try:
        ver = db.scalar("select version from deck")
    except:
        ver = db.scalar("select version from decks")
    # latest 1.2 is 65
    if ver < 65:
        raise AnkiError("oldDeckVersion")
    if ver > 99:
        return ver

    # cards
    ###########
    _moveTable(db, "cards")
    db.execute("""
insert into cards select id, factId, cardModelId, 1, cast(modified as int),
question, answer, ordinal, relativeDelay, type, due, cast(interval as int),
cast(factor*1000 as int), reps, successive, noCount, 0, 0 from cards2""")
    db.execute("drop table cards2")

    # tags
    ###########
    _moveTable(db, "tags")
    db.execute("insert or ignore into tags select id, ?, tag from tags2",
               intTime())
    # tags should have a leading and trailing space if not empty, and not
    # use commas
    db.execute("""
update facts set tags = (case
when trim(tags) == "" then ""
else " " || replace(replace(trim(tags), ",", " "), "  ", " ") || " "
end)
""")
    db.execute("drop table tags2")
    db.execute("drop table cardTags")

    # facts
    ###########
    db.execute("""
create table facts2
(id, modelId, modified, tags, cache)""")
    # use the rowid to give them an integer order
    db.execute("""
insert into facts2 select id, modelId, modified, tags, spaceUntil from
facts order by created""")
    db.execute("drop table facts")
    _addSchema(db, False)
    db.execute("""
insert or ignore into facts select id, modelId, rowid,
cast(modified as int), tags, cache from facts2""")
    db.execute("drop table facts2")

    # media
    ###########
    _moveTable(db, "media")
    db.execute("""
insert or ignore into media select filename, cast(created as int),
originalPath from media2""")
    db.execute("drop table media2")

    # fields -> fdata
    ###########
    db.execute("""
insert or ignore into fdata select factId, fieldModelId, ordinal, value, ''
from fields""")
    db.execute("drop table fields")

    # models
    ###########
    _moveTable(db, "models")
    db.execute("""
insert or ignore into models select id, cast(modified as int),
name, "{}" from models2""")
    db.execute("drop table models2")

    # reviewHistory -> revlog
    ###########
    db.execute("""
insert or ignore into revlog select
cast(time*1000 as int), cardId, ease, reps,
cast(lastInterval as int), cast(nextInterval as int),
cast(nextFactor*1000 as int), cast(min(thinkingTime, 60)*1000 as int),
0 from reviewHistory""")
    db.execute("drop table reviewHistory")
    # convert old ease0 into ease1
    db.execute("update revlog set ease = 1 where ease = 0")

    # longer migrations
    ###########
    _migrateDeckTbl(db)
    _migrateFieldsTbl(db)
    _migrateTemplatesTbl(db)

    _updateIndices(db)
    return ver

def _migrateDeckTbl(db):
    import anki.deck
    db.execute("delete from deck")
    db.execute("""
insert or replace into deck select id, cast(created as int), :t,
:t, 99, ifnull(syncName, ""), cast(lastSync as int),
utcOffset, "", "", "" from decks""", t=intTime())
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
            data[k] = v
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
    db.execute("""
insert into fields select id, modelId, ordinal, name, numeric, ''
from fieldModels""")
    dconf = anki.models.defaultFieldConf
    for row in db.all("""
select id, features, required, "unique", quizFontFamily, quizFontSize,
quizFontColour, editFontSize from fieldModels"""):
        conf = dconf.copy()
        (conf['rtl'],
         conf['required'],
         conf['unique'],
         conf['font'],
         conf['quizSize'],
         conf['quizColour'],
         conf['editSize']) = row[1:]
        # setup bools
        conf['rtl'] = not not conf['rtl']
        conf['pre'] = True
        # save
        db.execute("update fields set conf = ? where id = ?",
                   simplejson.dumps(conf), row[0])
    # clean up
    db.execute("drop table fieldModels")

def _migrateTemplatesTbl(db):
    # do this after fieldModel migration
    import anki.models
    db.execute("""
insert into templates select id, modelId, ordinal, name, active, qformat,
aformat, '' from cardModels""")
    dconf = anki.models.defaultTemplateConf
    for row in db.all("""
select id, modelId, questionInAnswer, questionAlign, lastFontColour,
allowEmptyAnswer, typeAnswer from cardModels"""):
        conf = dconf.copy()
        (conf['hideQ'],
         conf['align'],
         conf['bg'],
         conf['allowEmptyAns'],
         fname) = row[2:]
        # convert the field name to an id
        conf['typeAnswer'] = db.scalar(
            "select id from fields where name = ? and mid = ?",
            fname, row[1])
        # save
        db.execute("update templates set conf = ? where id = ?",
                   simplejson.dumps(conf), row[0])
    # clean up
    db.execute("drop table cardModels")

def _postSchemaUpgrade(deck):
    "Handle the rest of the upgrade to 2.0."
    import anki.deck
    # remove old views
    for v in ("failedCards", "revCardsOld", "revCardsNew",
              "revCardsDue", "revCardsRandom", "acqCardsRandom",
              "acqCardsOld", "acqCardsNew"):
        deck.db.execute("drop view if exists %s" % v)
    # minimize qt's bold/italics/underline cruft. we made need to use lxml to
    # do this properly
    from anki.utils import minimizeHTML
    r = [(minimizeHTML(x[2]), x[0], x[1]) for x in deck.db.execute(
        "select fid, fmid, val from fdata")]
    deck.db.executemany("update fdata set val = ? where fid = ? and fmid = ?",
                        r)
    # ensure all templates use the new style field format, and update cach
    for m in deck.allModels():
        for t in m.templates:
            t.qfmt = re.sub("%\((.+?)\)s", "{{\\1}}", t.qfmt)
            t.afmt = re.sub("%\((.+?)\)s", "{{\\1}}", t.afmt)
        m.flush()
        m.updateCache()
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
update cards set due = (select pos from facts where fid = facts.id) where type=2""")
    # convert due cards into day-based due
    deck.db.execute("""
update cards set due = cast(
(case when due < :stamp then 0 else 1 end) +
((due-:stamp)/86400) as int)+:today where type
between 0 and 1""", stamp=deck.sched.dayCutoff, today=deck.sched.today)
    # update factPos
    deck.conf['nextFactPos'] = deck.db.scalar("select max(pos) from facts")+1
    deck.save()

    # optimize and finish
    deck.updateDynamicIndices()
    deck.db.execute("vacuum")
    deck.db.execute("analyze")
    deck.db.execute("update deck set version = ?", CURRENT_VERSION)
    deck.save()

# Post-init upgrade
######################################################################

def _upgradeDeck(deck, version):
    "Upgrade deck to the latest version."
    if version >= CURRENT_VERSION:
        return
    if version < 100:
        _postSchemaUpgrade(deck)
