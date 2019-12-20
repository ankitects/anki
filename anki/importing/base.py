# -*- coding: utf-8 -*-
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from anki.utils import  maxID
from typing import Any, Optional, List
from anki.collection import _Collection

# Base importer
##########################################################################

class Importer:

    needMapper = False
    needDelimiter = False
    dst: Optional[_Collection]

    def __init__(self, col: _Collection, file: str) -> None:
        self.file = file
        self.log: List[str] = []
        self.col = col
        self.total = 0
        self.dst = None

    def run(self) -> None:
        pass

    # Timestamps
    ######################################################################
    # It's too inefficient to check for existing ids on every object,
    # and a previous import may have created timestamps in the future, so we
    # need to make sure our starting point is safe.

    def _prepareTS(self) -> None:
        self._ts = maxID(self.dst.db)

    def ts(self) -> Any:
        self._ts += 1
        return self._ts
