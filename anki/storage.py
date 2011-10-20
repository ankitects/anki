# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, simplejson
from anki.lang import _
from anki.utils import intTime
from anki.db import DB
from anki.deck import _Deck
from anki.consts import *
from anki.stdmodels import addBasicModel, addClozeModel

def Deck(path, queue=True, lock=True, server=False):
    "Open a new or existing deck. Path must be unicode."
    assert path.endswith(".anki2")
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
    deck = _Deck(db, server)
    if ver < SCHEMA_VERSION:
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

# no upgrades necessary at the moment
def _upgradeSchema(db):
    return SCHEMA_VERSION
def _upgradeDeck(deck, ver):
    return

# Creating a new deck
######################################################################

def _createDB(db):
    db.execute("pragma page_size = 4096")
    db.execute("pragma legacy_file_format = 0")
    db.execute("vacuum")
    _addSchema(db)
    _updateIndices(db)
    db.execute("analyze")
    return SCHEMA_VERSION

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
""" % ({'v':SCHEMA_VERSION}))
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
-- syncing
create index if not exists ix_facts_usn on facts (usn);
create index if not exists ix_cards_usn on cards (usn);
create index if not exists ix_revlog_usn on revlog (usn);
-- card spacing, etc
create index if not exists ix_cards_fid on cards (fid);
-- scheduling and group limiting
create index if not exists ix_cards_sched on cards (gid, queue, due);
-- revlog by card
create index if not exists ix_revlog_cid on revlog (cid);
-- field uniqueness check
create index if not exists ix_fsums_fid on fsums (fid);
create index if not exists ix_fsums_csum on fsums (csum);
""")
