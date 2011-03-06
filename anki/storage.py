# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU GPL, version 3 or later; http://www.gnu.org/copyleft/gpl.html

DECK_VERSION = 100

import os, time, simplejson
from anki.lang import _
#from anki.media import rebuildMediaDir
from anki.utils import intTime
from anki.db import DB
from anki.deck import _Deck
import anki.groups
from anki.stdmodels import BasicModel

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
    if ver < DECK_VERSION:
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
    return DECK_VERSION

def moveTable(s, table):
    sql = s.scalar(
        "select sql from sqlite_master where name = '%s'" % table)
    sql = sql.replace("TABLE "+table, "temporary table %s2" % table)
    s.execute(sql)
    s.execute("insert into %s2 select * from %s" % (table, table))
    s.execute("drop table "+table)

def _upgradeSchema(db):
    "Alter tables prior to ORM initialization."
    try:
        ver = db.scalar("select version from deck")
    except:
        ver = db.scalar("select version from decks")
    if ver < 65:
        raise Exception("oldDeckVersion")
    if ver < 99:
        raise "upgrade"
        # cards
        ###########
        moveTable(s, "cards")
        import cards
        metadata.create_all(engine, tables=[cards.cardsTable])
        s.execute("""
insert into cards select id, factId, 1, cardModelId, cast(modified as int),
question, answer, ordinal, 0, relativeDelay, type, due, cast(interval as int),
cast(factor*1000 as int), reps, successive, noCount, 0, 0 from cards2""")
        s.execute("drop table cards2")
        # tags
        ###########
        moveTable(s, "tags")
        import deck
        deck.DeckStorage._addTables(engine)
        s.execute("insert or ignore into tags select id, :t, tag from tags2",
                  {'t':intTime()})
        # tags should have a leading and trailing space if not empty, and not
        # use commas
        s.execute("""
update facts set tags = (case
when trim(tags) == "" then ""
else " " || replace(replace(trim(tags), ",", " "), "  ", " ") || " "
end)
""")
        s.execute("drop table tags2")
        s.execute("drop table cardTags")
        # facts
        ###########
        s.execute("""
create table facts2
(id, modelId, modified, tags, cache)""")
        # use the rowid to give them an integer order
        s.execute("""
insert into facts2 select id, modelId, modified, tags, spaceUntil from
facts order by created""")
        s.execute("drop table facts")
        import facts
        metadata.create_all(engine, tables=[facts.factsTable])
        s.execute("""
insert or ignore into facts select id, modelId, rowid,
cast(modified as int), tags, cache from facts2""")
        s.execute("drop table facts2")
        # media
        ###########
        moveTable(s, "media")
        import media
        metadata.create_all(engine, tables=[media.mediaTable])
        s.execute("""
insert or ignore into media select id, filename, size, cast(created as int),
originalPath from media2""")
        s.execute("drop table media2")
        # longer migrations
        ###########



        migrateDeck(s, engine)
        migrateFields(s, engine)
        # # fields
        # ###########
        # db.execute(
        #     "alter table fields add column csum text not null default ''")



        # models
        ###########
        moveTable(s, "models")
        import models
        metadata.create_all(engine, tables=[models.modelsTable])
        s.execute("""
insert or ignore into models select id, cast(modified as int), name, "" from models2""")
        s.execute("drop table models2")

    return ver

def migrateDeck(s, engine):
    import deck
    metadata.create_all(engine, tables=[deck.deckTable])
    s.execute("""
insert into deck select id, cast(created as int), cast(modified as int),
0, 99, ifnull(syncName, ""), cast(lastSync as int),
utcOffset, "", "", "" from decks""")
    # update selective study
    qconf = deck.defaultQconf.copy()
    # delete old selective study settings, which we can't auto-upgrade easily
    keys = ("newActive", "newInactive", "revActive", "revInactive")
    for k in keys:
        s.execute("delete from deckVars where key=:k", {'k':k})
    # copy other settings, ignoring deck order as there's a new default
    keys = ("newCardOrder", "newCardSpacing")
    for k in keys:
        qconf[k] = s.execute("select %s from decks" % k).scalar()
    qconf['newPerDay'] = s.execute(
        "select newCardsPerDay from decks").scalar()
    # fetch remaining settings from decks table
    conf = deck.defaultConf.copy()
    data = {}
    keys = ("sessionRepLimit", "sessionTimeLimit")
    for k in keys:
        conf[k] = s.execute("select %s from decks" % k).scalar()
    # random and due options merged
    qconf['revCardOrder'] = min(2, qconf['revCardOrder'])
    # no reverse option anymore
    qconf['newCardOrder'] = min(1, qconf['newCardOrder'])
    # add any deck vars and save
    dkeys = ("hexCache", "cssCache")
    for (k, v) in s.execute("select * from deckVars").fetchall():
        if k in dkeys:
            data[k] = v
        else:
            conf[k] = v
    s.execute("update deck set qconf = :l, config = :c, data = :d",
              {'l':simplejson.dumps(qconf),
               'c':simplejson.dumps(conf),
               'd':simplejson.dumps(data)})
    # clean up
    s.execute("drop table decks")
    s.execute("drop table deckVars")

def _upgradeDeck(deck, version):
    "Upgrade deck to the latest version."
    print version, DECK_VERSION
    if version < DECK_VERSION:
        prog = True
        deck.startProgress()
        deck.updateProgress(_("Upgrading Deck..."))
        oldmod = deck.modified
    else:
        prog = False
    if version < 100:
        # update dynamic indices given we don't use priority anymore
        for d in ("intervalDesc", "intervalAsc", "randomOrder",
                  "dueAsc", "dueDesc"):
            deck.db.execute("drop index if exists ix_cards_%s2" % d)
            execute.db.statement("drop index if exists ix_cards_%s" % d)
        # remove old views
        for v in ("failedCards", "revCardsOld", "revCardsNew",
                  "revCardsDue", "revCardsRandom", "acqCardsRandom",
                  "acqCardsOld", "acqCardsNew"):
            deck.db.execute("drop view if exists %s" % v)
        # add checksums and index
        deck.updateAllFieldChecksums()
        # this was only used for calculating average factor
        deck.db.execute("drop index if exists ix_cards_factor")
        # remove stats, as it's all in the revlog now
        deck.db.execute("drop table if exists stats")
        # migrate revlog data to new table
        deck.db.execute("""
insert or ignore into revlog select
cast(time*1000 as int), cardId, ease, reps,
cast(lastInterval as int), cast(nextInterval as int),
cast(nextFactor*1000 as int), cast(min(thinkingTime, 60)*1000 as int),
0 from reviewHistory""")
        deck.db.execute("drop table reviewHistory")
        # convert old ease0 into ease1
        deck.db.execute("update revlog set ease = 1 where ease = 0")
        # remove priority index
        deck.db.execute("drop index if exists ix_cards_priority")
        # suspended cards don't use ranges anymore
        deck.db.execute("update cards set queue=-1 where queue between -3 and -1")
        deck.db.execute("update cards set queue=-2 where queue between 3 and 5")
        deck.db.execute("update cards set queue=-3 where queue between 6 and 8")
        # update schema time
        deck.db.execute("update deck set schemaMod = :t", t=intTime())
        # remove queueDue as it's become dynamic, and type index
        deck.db.execute("drop index if exists ix_cards_queueDue")
        deck.db.execute("drop index if exists ix_cards_type")
        # remove old deleted tables
        for t in ("cards", "facts", "models", "media"):
            deck.db.execute("drop table if exists %sDeleted" % t)
        # finally, update indices & optimize
        updateIndices(deck.db)
        # rewrite due times for new cards
        deck.db.execute("""
update cards set due = (select pos from facts where factId = facts.id) where type=2""")
        # convert due cards into day-based due
        deck.db.execute("""
update cards set due = cast(
(case when due < :stamp then 0 else 1 end) +
((due-:stamp)/86400) as int)+:today where type
between 0 and 1""", stamp=deck.sched.dayCutoff, today=deck.sched.today)
        print "today", deck.sched.today
        print "cut", deck.sched.dayCutoff
        # setup qconf & config for dynamicIndices()
        deck.qconf = simplejson.loads(deck._qconf)
        deck.config = simplejson.loads(deck._config)
        deck.data = simplejson.loads(deck._data)
        # update factPos
        deck.config['nextFactPos'] = deck.db.scalar("select max(pos) from facts")+1
        deck.flushConfig()
        # add default config

        deck.updateDynamicIndices()
        deck.db.execute("vacuum")
        deck.db.execute("analyze")
        deck.db.execute("update deck set version = ?", DECK_VERSION)
        deck.db.commit()
    if prog:
        assert deck.modified == oldmod
        deck.finishProgress()

def _addSchema(db):
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

create table if not exists gravestones (
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
""" % ({'t': intTime(), 'v':DECK_VERSION}))
    import anki.deck
    db.execute("update deck set qconf = ?, conf = ?, data = ?",
               simplejson.dumps(anki.deck.defaultQconf),
               simplejson.dumps(anki.deck.defaultConf),
               "{}")
    db.execute(
        "insert or ignore into gconf values (1, ?, ?, ?)""",
        intTime(), _("Default Config"),
        simplejson.dumps(anki.groups.defaultConf))
    db.execute(
        "insert or ignore into groups values (1, ?, ?, 1)",
        intTime(), _("Default Group"))

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
create index if not exists ix_gravestones_delTime on gravestones (delTime);
""")
