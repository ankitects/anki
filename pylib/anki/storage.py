# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import copy
import json
import os
import weakref
from dataclasses import dataclass
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
from anki.utils import intTime


@dataclass
class ServerData:
    minutes_west: Optional[int] = None


def Collection(
    path: str,
    backend: Optional[RustBackend] = None,
    server: Optional[ServerData] = None,
) -> _Collection:
    "Open a new or existing collection. Path must be unicode."
    assert path.endswith(".anki2")
    if backend is None:
        backend = RustBackend(server=server is not None)

    (media_dir, media_db) = media_paths_from_col_path(path)
    log_path = ""
    if not server:
        log_path = path.replace(".anki2", "2.log")
    path = os.path.abspath(path)

    # connect
    backend.open_collection(path, media_dir, media_db, log_path)
    db = DBProxy(weakref.proxy(backend), path)

    # initial setup required?
    create = db.scalar("select models = '{}' from col")
    if create:
        initial_db_setup(db)

    # add db to col and do any remaining upgrades
    col = _Collection(db, backend=backend, server=server)
    if create:
        # add in reverse order so basic is default
        addClozeModel(col)
        addBasicTypingModel(col)
        addForwardOptionalReverse(col)
        addForwardReverse(col)
        addBasicModel(col)
        col.save()
    else:
        db.begin()
    return col


# Creating a new collection
######################################################################


def initial_db_setup(db: DBProxy) -> None:
    db.begin()
    _addColVars(db, *_getColVars(db))


def _getColVars(db: DBProxy) -> Tuple[Any, Dict[str, Any]]:
    import anki.collection
    import anki.decks

    g = copy.deepcopy(anki.decks.defaultDeck)
    g["id"] = 1
    g["name"] = _("Default")
    g["conf"] = 1
    g["mod"] = intTime()
    return g, anki.collection.defaultConf.copy()


def _addColVars(db: DBProxy, g: Dict[str, Any], c: Dict[str, Any]) -> None:
    db.execute(
        """
update col set conf = ?, decks = ?""",
        json.dumps(c),
        json.dumps({"1": g}),
    )
