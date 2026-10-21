# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""The exceptions Anki raises on purpose.

`AnkiException` is the base class of everything in this module, so
`except anki.errors.AnkiException` catches any error Anki raised itself while
letting unrelated exceptions through.

Most of these are raised by the Rust backend and translated into the matching
class by `_backend.backend_exception_to_pylib()`. A few, like
`AbortSchemaModification`, are raised on the Python side. The translation only
covers the kinds it knows about, so an error that isn't mapped arrives as a
plain `BackendError` — catch that rather than assuming a specific subclass.

Every `BackendError` carries a `message` (what `str()` returns), a
`help_page` linking to the manual if the failure has an entry there, and
`context` and `backtrace` for bug reports. All but `message` may be `None`.
"""

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


class BackendError(AnkiException):
    """An error originating from Anki's backend.

    `help_page` is a `anki.collection.HelpPage` value pointing at the manual
    section describing the failure, or `None` if there isn't one. `context`
    narrows down the failure and `backtrace` is a Rust backtrace, both mostly
    useful in bug reports. Only `str()` is safe to show to users.
    """

    def __init__(
        self,
        message: str,
        help_page: anki.collection.HelpPage.V | None,
        context: str | None,
        backtrace: str | None,
    ) -> None:
        super().__init__()
        self._message = message
        self.help_page = help_page
        self.context = context
        self.backtrace = backtrace

    def __str__(self) -> str:
        return self._message


class Interrupted(BackendError):
    """Raised when the user cancels the operation that was in progress."""


class NetworkError(BackendError):
    """Raised when a request to AnkiWeb fails, eg during a sync."""


class SyncErrorKind(Enum):
    """Why a `SyncError` happened: `AUTH` for a rejected username or
    password, `OTHER` for everything else."""

    AUTH = 1
    OTHER = 2


class SyncError(BackendError):
    """Raised when syncing fails.

    `kind` distinguishes an auth failure from any other cause, which is the
    only case worth prompting the user about.
    """

    def __init__(
        self,
        message: str,
        help_page: anki.collection.HelpPage.V | None,
        context: str | None,
        backtrace: str | None,
        kind: SyncErrorKind,
    ):
        self.kind = kind
        super().__init__(message, help_page, context, backtrace)


class BackendIOError(BackendError):
    """Raised when reading or writing a file fails."""


class CustomStudyError(BackendError):
    """Raised when a custom study deck can't be built with the options given."""


class DBError(BackendError):
    """Raised when the database itself reports an error."""


class CardTypeError(BackendError):
    """Raised when a note type's card templates are unusable.

    Covers a template that won't parse, two templates that generate the same
    card, a missing front field, a reference to a field that doesn't exist, and
    a cloze note with no cloze deletions.
    """


class TemplateError(BackendError):
    """Raised when a card template can't be rendered."""


class NotFoundError(BackendError):
    """Raised when a record the backend expected to exist is missing.

    This normally means the collection is in an inconsistent state rather than
    that the id you passed was bad, so the message reads "Inconsistent database
    state. No such <type>: '<id>'". A genuinely invalid id is more likely to
    come back as `InvalidInput`.
    """


class DeletedError(BackendError):
    pass


class ExistsError(BackendError):
    """Raised when creating something that already exists, eg a note type or
    deck whose name is already taken."""


class UndoEmpty(BackendError):
    """Raised by `col.undo()` and `col.redo()` when there is nothing left to
    undo or redo."""


class FilteredDeckError(BackendError):
    """Raised when an operation isn't allowed on a filtered deck, eg deleting one."""


class InvalidInput(BackendError):
    """Raised when an argument is rejected before any work is done.

    This is the least specific of the backend errors, and the backend reuses
    it for a long list of unrelated conditions: the collection is already open
    or not open at all, an id can't be parsed, a regex is invalid, a number is
    unparsable, a check on the database or media files is required first,
    several note types were selected where one was expected, and a handful of
    FSRS parameter problems. Only `str()` says which one happened, so match on
    the message if you need to tell them apart.
    """


class SearchError(BackendError):
    """Raised when a search query can't be parsed."""


class SchedulerUpgradeRequired(BackendError):
    """Raised when the scheduler needs upgrading before the operation can run.

    This happens on collections whose scheduler predates a format change, and
    resolves itself by completing the upgrade.
    """


class AbortSchemaModification(AnkiException):
    """Raised by `col.mod_schema()` when a `schema_will_change` hook returned
    False, so a hook vetoed the schema change."""


# legacy
DeckRenameError = FilteredDeckError
AnkiError = AbortSchemaModification
