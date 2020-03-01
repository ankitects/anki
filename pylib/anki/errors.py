# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from typing import Any, Dict


class AnkiError(Exception):
    def __init__(self, type, **data) -> None:
        super().__init__()
        self.type = type
        self.data = data

    def __str__(self) -> Any:
        m = self.type
        if self.data:
            m += ": %s" % repr(self.data)
        return m


class DeckRenameError(Exception):
    def __init__(self, description: str, deck: Dict[str, Any], newName: str) -> None:
        super().__init__()
        self.description = description
        self.deck = deck
        self.newName = newName

    def __str__(self):
        return "Couldn't rename deck: " + self.description
