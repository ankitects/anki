# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

from markdown import markdown

import anki._backend.backend_pb2 as _pb

from anki.types import assert_exhaustive


class StringError(Exception):
    def __str__(self) -> str:
        return self.args[0]  # pylint: disable=unsubscriptable-object


class Interrupted(Exception):
    pass


class NetworkError(StringError):
    pass


class SyncError(StringError):
    # pylint: disable=no-member
    def is_auth_error(self) -> bool:
        return self.args[1] == _pb.SyncError.SyncErrorKind.AUTH_FAILED


class IOError(StringError):
    pass


class DBError(StringError):
    pass


class TemplateError(StringError):
    pass


class NotFoundError(Exception):
    pass


class ExistsError(Exception):
    pass


class UndoEmpty(Exception):
    pass


class DeckRenameError(Exception):
    """Legacy error, use FilteredDeckError instead."""

    def __init__(self, description: str, *args: object) -> None:
        super().__init__(description, *args)
        self.description = description


class FilteredDeckError(StringError, DeckRenameError):
    pass


class InvalidInput(StringError):
    pass


class SearchError(StringError):
    pass


def backend_exception_to_pylib(err: _pb.BackendError) -> Exception:
    val = err.WhichOneof("value")
    if val == "interrupted":
        return Interrupted()
    elif val == "network_error":
        return NetworkError(err.localized, err.network_error.kind)
    elif val == "sync_error":
        return SyncError(err.localized, err.sync_error.kind)
    elif val == "io_error":
        return IOError(err.localized)
    elif val == "db_error":
        return DBError(err.localized)
    elif val == "template_parse":
        return TemplateError(err.localized)
    elif val == "invalid_input":
        return InvalidInput(err.localized)
    elif val == "json_error":
        return StringError(err.localized)
    elif val == "not_found_error":
        return NotFoundError()
    elif val == "exists":
        return ExistsError()
    elif val == "filtered_deck_error":
        return FilteredDeckError(err.localized)
    elif val == "proto_error":
        return StringError(err.localized)
    elif val == "search_error":
        return SearchError(markdown(err.localized))
    elif val == "undo_empty":
        return UndoEmpty()
    else:
        assert_exhaustive(val)
        return StringError(err.localized)


# FIXME: this is only used with "abortSchemaMod", but currently some
# add-ons depend on it
class AnkiError(Exception):
    def __init__(self, type: str) -> None:
        super().__init__()
        self.type = type

    def __str__(self) -> str:
        return self.type
