# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import enum

from aqt import AnkiQt


class Change(enum.Enum):
    NO_CHANGE = 0
    BASIC_CHANGE = 1
    SCHEMA_CHANGE = 2


class ChangeTracker:
    _changed = Change.NO_CHANGE

    def __init__(self, mw: AnkiQt) -> None:
        self.mw = mw

    def mark_basic(self) -> None:
        if self._changed == Change.NO_CHANGE:
            self._changed = Change.BASIC_CHANGE

    def mark_schema(self) -> bool:
        "If false, processing should be aborted."
        if self._changed != Change.SCHEMA_CHANGE:
            if not self.mw.confirm_schema_modification():
                return False
            self._changed = Change.SCHEMA_CHANGE
        return True

    def changed(self) -> bool:
        return self._changed != Change.NO_CHANGE
