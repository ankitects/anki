# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from enum import Enum
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import anki.collection


class LocalizedError(Exception):
    "An error with a localized description."

    def __init__(self, localized: str) -> None:
        self._localized = localized
        super().__init__()

    def __str__(self) -> str:
        return self._localized


class DocumentedError(LocalizedError):
    """A localized error described in the manual."""

    def __init__(self, localized: str, help_page: anki.collection.HelpPage.V) -> None:
        self.help_page = help_page
        super().__init__(localized)


class Interrupted(Exception):
    pass


class NetworkError(LocalizedError):
    pass


class SyncErrorKind(Enum):
    AUTH = 1
    OTHER = 2


class SyncError(LocalizedError):
    def __init__(self, localized: str, kind: SyncErrorKind):
        self.kind = kind
        super().__init__(localized)


class BackendIOError(LocalizedError):
    pass


class CustomStudyError(LocalizedError):
    pass


class DBError(LocalizedError):
    pass


class CardTypeError(DocumentedError):
    pass


class TemplateError(LocalizedError):
    pass


class NotFoundError(Exception):
    pass


class DeletedError(LocalizedError):
    pass


class ExistsError(Exception):
    pass


class UndoEmpty(Exception):
    pass


class FilteredDeckError(LocalizedError):
    pass


class InvalidInput(LocalizedError):
    pass


class SearchError(LocalizedError):
    pass


class AbortSchemaModification(Exception):
    pass


# legacy
DeckRenameError = FilteredDeckError
AnkiError = AbortSchemaModification
