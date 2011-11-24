# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, simplejson
from anki.lang import _
from anki.utils import intTime
from anki.db import DB
from anki.collection import _Collection
from anki.consts import *
from anki.stdmodels import addBasicModel, addClozeModel

def Collection(path, queue=True, lock=True, server=False):
    "Open a new or existing collection. Path must be unicode."
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
    # add db to col and do any remaining upgrades
    col = _Collection(db, server)
    if ver < SCHEMA_VERSION:
        _upgrade(col, ver)
    elif create:
        # add in reverse order so basic is default
        addClozeModel(col)
        addBasicModel(col)
        col.save()
    if lock:
        col.lock()
    if not queue:
        return col
    # rebuild queue
    col.reset()
    return col

# no upgrades necessary at the moment
def _upgradeSchema(db):
    return SCHEMA_VERSION
def _upgrade(col, ver):
    return

# Creating a new collection
######################################################################

def _createDB(db):
    db.execute("pragma page_size = 4096")
    db.execute("pragma legacy_file_format = 0")
    db.execute("vacuum")
    _addSchema(db)
    _updateIndices(db)
    db.execute("analyze")
    return SCHEMA_VERSION

def _addSchema(db, setColConf=True):
    db.executescript("""
create table if not exists col (
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
    decks           text not null,
    dconf           text not null,
    tags            text not null
);

create table if not exists notes (
    id              integer primary key,
    guid            integer not null,
    mid             integer not null,
    did             integer not null,
    mod             integer not null,
    usn             integer not null,
    tags            text not null,
    flds            text not null,
    sfld            integer not null,
    csum            integer not null,
    flags           integer not null,
    data            text not null
);

create table if not exists cards (
    id              integer primary key,
    nid             integer not null,
    did             integer not null,
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

insert or ignore into col
values(1,0,0,0,%(v)s,0,0,0,'','{}','','','{}');
""" % ({'v':SCHEMA_VERSION}))
    if setColConf:
        _addColVars(db, *_getColVars(db))

def _getColVars(db):
    import anki.collection
    import anki.decks
    g = anki.decks.defaultDeck.copy()
    for k,v in anki.decks.defaultTopConf.items():
        g[k] = v
    g['id'] = 1
    g['name'] = _("Default")
    g['conf'] = 1
    g['mod'] = intTime()
    gc = anki.decks.defaultConf.copy()
    gc['id'] = 1
    return g, gc, anki.collection.defaultConf.copy()

def _addColVars(db, g, gc, c):
    db.execute("""
update col set conf = ?, decks = ?, dconf = ?""",
                   simplejson.dumps(c),
                   simplejson.dumps({'1': g}),
                   simplejson.dumps({'1': gc}))

def _updateIndices(db):
    "Add indices to the DB."
    db.executescript("""
-- syncing
create index if not exists ix_notes_usn on notes (usn);
create index if not exists ix_cards_usn on cards (usn);
create index if not exists ix_revlog_usn on revlog (usn);
-- card spacing, etc
create index if not exists ix_cards_nid on cards (nid);
-- scheduling and deck limiting
create index if not exists ix_cards_sched on cards (did, queue, due);
-- revlog by card
create index if not exists ix_revlog_cid on revlog (cid);
-- field uniqueness
create index if not exists ix_notes_csum on notes (csum);
""")
