# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import copy
import re

from anki.lang import _
from anki.utils import intTime, json
from anki.db import DB
from anki.collection import _Collection
from anki.consts import *
from anki.stdmodels import addBasicModel, addClozeModel, addForwardReverse, \
    addForwardOptionalReverse


def Collection(path, lock=True, server=False, sync=True, log=False):
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
    col = _Collection(db, server, log)
    if ver < SCHEMA_VERSION:
        _upgrade(col, ver)
    elif ver > SCHEMA_VERSION:
        raise Exception("This file requires a newer version of Anki.")
    elif create:
        # add in reverse order so basic is default
        addClozeModel(col)
        addForwardOptionalReverse(col)
        addForwardReverse(col)
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
        col.modSchema(check=False)
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
    if ver < 6:
        col.modSchema(check=False)
        import anki.models
        for m in col.models.all():
            m['css'] = anki.models.defaultModel['css']
            for t in m['tmpls']:
                if 'css' not in t:
                    # ankidroid didn't bump version
                    continue
                m['css'] += "\n" + t['css'].replace(
                    ".card ", ".card%d "%(t['ord']+1))
                del t['css']
            col.models.save(m)
        col.db.execute("update col set ver = 6")
    if ver < 7:
        col.modSchema(check=False)
        col.db.execute(
            "update cards set odue = 0 where (type = 1 or queue = 2) "
            "and not odid")
        col.db.execute("update col set ver = 7")
    if ver < 8:
        col.modSchema(check=False)
        col.db.execute(
            "update cards set due = due / 1000 where due > 4294967296")
        col.db.execute("update col set ver = 8")
    if ver < 9:
        # adding an empty file to a zip makes python's zip code think it's a
        # folder, so remove any empty files
        changed = False
        dir = col.media.dir()
        if dir:
            for f in os.listdir(col.media.dir()):
                if os.path.isfile(f) and not os.path.getsize(f):
                    os.unlink(f)
                    col.media.db.execute(
                        "delete from log where fname = ?", f)
                    col.media.db.execute(
                        "delete from media where fname = ?", f)
                    changed = True
            if changed:
                col.media.db.commit()
        col.db.execute("update col set ver = 9")
    if ver < 10:
        col.db.execute("""
update cards set left = left + left*1000 where queue = 1""")
        col.db.execute("update col set ver = 10")
    if ver < 11:
        col.modSchema(check=False)
        for d in col.decks.all():
            if d['dyn']:
                order = d['order']
                # failed order was removed
                if order >= 5:
                    order -= 1
                d['terms'] = [[d['search'], d['limit'], order]]
                del d['search']
                del d['limit']
                del d['order']
                d['resched'] = True
                d['return'] = True
            else:
                if 'extendNew' not in d:
                    d['extendNew'] = 10
                    d['extendRev'] = 50
            col.decks.save(d)
        for c in col.decks.allConf():
            r = c['rev']
            r['ivlFct'] = r.get("ivlfct", 1)
            if 'ivlfct' in r:
                del r['ivlfct']
            r['maxIvl'] = 36500
            col.decks.save(c)
        for m in col.models.all():
            for t in m['tmpls']:
                t['bqfmt'] = ''
                t['bafmt'] = ''
            col.models.save(m)
        col.db.execute("update col set ver = 11")

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
    id              integer primary key,   /* 0 */
    guid            text not null,         /* 1 */
    mid             integer not null,      /* 2 */
    mod             integer not null,      /* 3 */
    usn             integer not null,      /* 4 */
    tags            text not null,         /* 5 */
    flds            text not null,         /* 6 */
    sfld            integer not null,      /* 7 */
    csum            integer not null,      /* 8 */
    flags           integer not null,      /* 9 */
    data            text not null          /* 10 */
);

create table if not exists cards (
    id              integer primary key,   /* 0 */
    nid             integer not null,      /* 1 */
    did             integer not null,      /* 2 */
    ord             integer not null,      /* 3 */
    mod             integer not null,      /* 4 */
    usn             integer not null,      /* 5 */
    type            integer not null,      /* 6 */
    queue           integer not null,      /* 7 */
    due             integer not null,      /* 8 */
    ivl             integer not null,      /* 9 */
    factor          integer not null,      /* 10 */
    reps            integer not null,      /* 11 */
    lapses          integer not null,      /* 12 */
    left            integer not null,      /* 13 */
    odue            integer not null,      /* 14 */
    odid            integer not null,      /* 15 */
    flags           integer not null,      /* 16 */
    data            text not null          /* 17 */
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
