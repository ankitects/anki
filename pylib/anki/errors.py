# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from enum import Enum
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    import anki.collection


class AnkiException(Exception):
    """
    General Anki exception that all custom exceptions raised by Anki should
    inherit from. Allows add-ons to easily identify Anki-native exceptions.

    When inheriting from a Python built-in exception other than `Exception`,
    please supply `AnkiException` as an additional inheritance:

    ```
    class MyNewAnkiException(ValueError, AnkiException):
        pass
    ```
    """


class LocalizedError(AnkiException):
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


class Interrupted(AnkiException):
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


class NotFoundError(AnkiException):
    pass


class DeletedError(LocalizedError):
    pass


class ExistsError(AnkiException):
    pass


class UndoEmpty(AnkiException):
    pass


class FilteredDeckError(LocalizedError):
    pass


class InvalidInput(LocalizedError):
    pass


class SearchError(LocalizedError):
    pass


class AbortSchemaModification(AnkiException):
    pass


# legacy
DeckRenameError = FilteredDeckError
AnkiError = AbortSchemaModification
