# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import json
import os
from typing import Any, Dict, Optional, Tuple

from anki.collection import _Collection
from anki.consts import *
from anki.dbproxy import DBProxy
from anki.lang import _
from anki.media import media_paths_from_col_path
from anki.rsbackend import RustBackend
from anki.stdmodels import (
    addBasicModel,
    addBasicTypingModel,
    addClozeModel,
    addForwardOptionalReverse,
    addForwardReverse,
)
from anki.utils import intTime, isWin


class ServerData:
    minutes_west: Optional[int] = None


def Collection(
    path: str, lock: bool = True, server: Optional[ServerData] = None, log: bool = False
) -> _Collection:
    "Open a new or existing collection. Path must be unicode."
    assert path.endswith(".anki2")
    (media_dir, media_db) = media_paths_from_col_path(path)
    log_path = ""
    if not server:
        log_path = path.replace(".anki2", "2.log")
    path = os.path.abspath(path)
    create = not os.path.exists(path)
    if create:
        base = os.path.basename(path)
        for c in ("/", ":", "\\"):
            assert c not in base
    # connect
    backend = RustBackend(
        path, media_dir, media_db, log_path, server=server is not None
    )
    db = DBProxy(backend, path)
    db.setAutocommit(True)
    if create:
        ver = _createDB(db)
    else:
        ver = _upgradeSchema(db)
    db.execute("pragma temp_store = memory")
    db.execute("pragma cache_size = 10000")
    if not isWin:
        db.execute("pragma journal_mode = wal")
    db.setAutocommit(False)
    # add db to col and do any remaining upgrades
    col = _Collection(db, backend=backend, server=server, log=log)
    if ver < SCHEMA_VERSION:
        raise Exception("This file requires an older version of Anki.")
    elif ver > SCHEMA_VERSION:
        raise Exception("This file requires a newer version of Anki.")
    elif create:
        # add in reverse order so basic is default
        addClozeModel(col)
        addBasicTypingModel(col)
        addForwardOptionalReverse(col)
        addForwardReverse(col)
        addBasicModel(col)
        col.save()
    if lock:
        try:
            col.lock()
        except:
            col.db.close()
            raise
    return col


def _upgradeSchema(db: DBProxy) -> Any:
    return db.scalar("select ver from col")


# Creating a new collection
######################################################################


def _createDB(db: DBProxy) -> int:
    db.execute("pragma page_size = 4096")
    db.execute("pragma legacy_file_format = 0")
    db.execute("vacuum")
    _addSchema(db)
    _updateIndices(db)
    db.execute("analyze")
    return SCHEMA_VERSION


def _addSchema(db: DBProxy, setColConf: bool = True) -> None:
    db.executescript(
        """
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
"""
        % ({"v": SCHEMA_VERSION, "s": intTime(1000)})
    )
    if setColConf:
        _addColVars(db, *_getColVars(db))


def _getColVars(db: DBProxy) -> Tuple[Any, Any, Dict[str, Any]]:
    import anki.collection
    import anki.decks

    g = copy.deepcopy(anki.decks.defaultDeck)
    g["id"] = 1
    g["name"] = _("Default")
    g["conf"] = 1
    g["mod"] = intTime()
    gc = copy.deepcopy(anki.decks.defaultConf)
    gc["id"] = 1
    return g, gc, anki.collection.defaultConf.copy()


def _addColVars(
    db: DBProxy, g: Dict[str, Any], gc: Dict[str, Any], c: Dict[str, Any]
) -> None:
    db.execute(
        """
update col set conf = ?, decks = ?, dconf = ?""",
        json.dumps(c),
        json.dumps({"1": g}),
        json.dumps({"1": gc}),
    )


def _updateIndices(db: DBProxy) -> None:
    "Add indices to the DB."
    db.executescript(
        """
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
"""
    )
