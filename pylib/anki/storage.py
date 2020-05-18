# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import weakref
from typing import Optional

from anki.collection import _Collection
from anki.dbproxy import DBProxy
from anki.media import media_paths_from_col_path
from anki.rsbackend import RustBackend


def Collection(
    path: str,
    backend: Optional[RustBackend] = None,
    server: bool = False,
    log: bool = False,
) -> _Collection:
    "Open a new or existing collection. Path must be unicode."
    assert path.endswith(".anki2")
    if backend is None:
        backend = RustBackend(server=server)

    (media_dir, media_db) = media_paths_from_col_path(path)
    log_path = ""
    should_log = not server and log
    if should_log:
        log_path = path.replace(".anki2", "2.log")
    path = os.path.abspath(path)

    # connect
    backend.open_collection(path, media_dir, media_db, log_path)
    db = DBProxy(weakref.proxy(backend), path)

    # add db to col and do any remaining upgrades
    col = _Collection(db, backend=backend, server=server)
    db.begin()
    return col
