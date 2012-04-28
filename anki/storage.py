# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os, copy, re
from anki.lang import _
from anki.utils import intTime, ids2str, json
from anki.db import DB
from anki.collection import _Collection
from anki.consts import *
from anki.stdmodels import addBasicModel, addClozeModel

def Collection(path, lock=True, server=False, sync=True):
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
    if sync:
        db.execute("pragma cache_size = 10000")
        db.execute("pragma journal_mode = wal")
    else:
        db.execute("pragma synchronous = off")
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
    return col

def _upgradeSchema(db):
    ver = db.scalar("select ver from col")
    if ver == SCHEMA_VERSION:
        return ver
    # add odid to cards, edue->odue
    ######################################################################
    if db.scalar("select ver from col") == 1:
        db.execute("alter table cards rename to cards2")
        _addSchema(db, setColConf=False)
        db.execute("""
insert into cards select
id, nid, did, ord, mod, usn, type, queue, due, ivl, factor, reps, lapses,
left, edue, 0, flags, data from cards2""")
        db.execute("drop table cards2")
        db.execute("update col set ver = 2")
        _updateIndices(db)
    # remove did from notes
    ######################################################################
    if db.scalar("select ver from col") == 2:
        db.execute("alter table notes rename to notes2")
        _addSchema(db, setColConf=False)
        db.execute("""
insert into notes select
id, guid, mid, mod, usn, tags, flds, sfld, csum, flags, data from notes2""")
        db.execute("drop table notes2")
        db.execute("update col set ver = 3")
        _updateIndices(db)
    return ver

def _upgrade(col, ver):
    if ver < 3:
        # new deck properties
        for d in col.decks.all():
            d['dyn'] = 0
            d['collapsed'] = False
            col.decks.save(d)
    if ver < 4:
        col.modSchema()
        clozes = []
        for m in col.models.all():
            if not "{{cloze:" in m['tmpls'][0]['qfmt']:
                m['type'] = MODEL_STD
                col.models.save(m)
            else:
                clozes.append(m)
        for m in clozes:
            _upgradeClozeModel(col, m)
        col.db.execute("update col set ver = 4")
    if ver < 5:
        col.db.execute("update cards set odue = 0 where queue = 2")
        col.db.execute("update col set ver = 5")

def _upgradeClozeModel(col, m):
    m['type'] = MODEL_CLOZE
    # convert first template
    t = m['tmpls'][0]
    for type in 'qfmt', 'afmt':
        t[type] = re.sub("{{cloze:1:(.+?)}}", r"{{cloze:\1}}", t[type])
    t['name'] = _("Cloze")
    # delete non-cloze cards for the model
    rem = []
    for t in m['tmpls'][1:]:
        if "{{cloze:" not in t['qfmt']:
            rem.append(t)
    for r in rem:
        col.models.remTemplate(m, r)
    del m['tmpls'][1:]
    col.models._updateTemplOrds(m)
    col.models.save(m)

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
    guid            text not null,
    mid             integer not null,
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
    odue            integer not null,
    odid            integer not null,
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
values(1,0,0,%(s)s,%(v)s,0,0,0,'','{}','','','{}');
""" % ({'v':SCHEMA_VERSION, 's':intTime(1000)}))
    if setColConf:
        _addColVars(db, *_getColVars(db))

def _getColVars(db):
    import anki.collection
    import anki.decks
    g = copy.deepcopy(anki.decks.defaultDeck)
    g['id'] = 1
    g['name'] = _("Default")
    g['conf'] = 1
    g['mod'] = intTime()
    gc = copy.deepcopy(anki.decks.defaultConf)
    gc['id'] = 1
    return g, gc, anki.collection.defaultConf.copy()

def _addColVars(db, g, gc, c):
    db.execute("""
update col set conf = ?, decks = ?, dconf = ?""",
                   json.dumps(c),
                   json.dumps({'1': g}),
                   json.dumps({'1': gc}))

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
