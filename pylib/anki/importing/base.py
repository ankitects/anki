# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

# pylint: disable=invalid-name
from __future__ import annotations

from typing import Any

from anki.collection import Collection
from anki.utils import max_id

# Base importer
##########################################################################


class Importer:
    needMapper = False
    needDelimiter = False
    dst: Collection | None

    def __init__(self, col: Collection, file: str) -> None:
        self.file = file
        self.log: list[str] = []
        self.col = col.weakref()
        self.total = 0
        self.dst = None

    def run(self) -> None:
        pass

    def open(self) -> None:
        "Open file and ensure it's in the right format."
        return

    def close(self) -> None:
        "Closes the open file."
        return

    # Timestamps
    ######################################################################
    # It's too inefficient to check for existing ids on every object,
    # and a previous import may have created timestamps in the future, so we
    # need to make sure our starting point is safe.

    def _prepareTS(self) -> None:
        self._ts = max_id(self.dst.db)

    def ts(self) -> Any:
        self._ts += 1
        return self._ts
