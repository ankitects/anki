# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Optional

from anki.collection import _Collection
from anki.rsbackend import RustBackend


def Collection(
    path: str,
    backend: Optional[RustBackend] = None,
    server: bool = False,
    log: bool = False,
) -> _Collection:
    "Open a new or existing collection. Path must be unicode."
    return _Collection(path, backend, server, log)
