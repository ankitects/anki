# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys
import traceback
from typing import Any, Dict, List, Optional, Sequence, Tuple, Union
from weakref import ref

from markdown import markdown

import anki.buildinfo
import anki.lang
from anki import backend_pb2, i18n_pb2
from anki._backend.generated import RustBackendGenerated
from anki.dbproxy import Row as DBRow
from anki.dbproxy import ValueForDB
from anki.utils import from_json_bytes, to_json_bytes

from ..errors import (
    BackendIOError,
    DBError,
    ExistsError,
    FilteredDeckError,
    Interrupted,
    InvalidInput,
    LocalizedError,
    NetworkError,
    NotFoundError,
    SearchError,
    SyncError,
    SyncErrorKind,
    TemplateError,
    UndoEmpty,
)
from . import rsbridge
from .fluent import GeneratedTranslations, LegacyTranslationEnum

# the following comment is required to suppress a warning that only shows up
# when there are other pylint failures
# pylint: disable=c-extension-no-member
assert rsbridge.buildhash() == anki.buildinfo.buildhash


class RustBackend(RustBackendGenerated):
    """
    Python bindings for Anki's Rust libraries.

    Please do not access methods on the backend directly - they may be changed
    or removed at any time. Instead, please use the methods on the collection
    instead. Eg, don't use col._backend.all_deck_config(), instead use
    col.decks.all_config()

    If you need to access a backend method that is not currently accessible
    via the collection, please send through a pull request that adds a
    public method.
    """

    def __init__(
        self,
        langs: Optional[List[str]] = None,
        server: bool = False,
    ) -> None:
        # pick up global defaults if not provided
        if langs is None:
            langs = [anki.lang.currentLang]

        init_msg = backend_pb2.BackendInit(
            preferred_langs=langs,
            server=server,
        )
        self._backend = rsbridge.open_backend(init_msg.SerializeToString())

    def db_query(
        self, sql: str, args: Sequence[ValueForDB], first_row_only: bool
    ) -> List[DBRow]:
        return self._db_command(
            dict(kind="query", sql=sql, args=args, first_row_only=first_row_only)
        )

    def db_execute_many(self, sql: str, args: List[List[ValueForDB]]) -> List[DBRow]:
        return self._db_command(dict(kind="executemany", sql=sql, args=args))

    def db_begin(self) -> None:
        return self._db_command(dict(kind="begin"))

    def db_commit(self) -> None:
        return self._db_command(dict(kind="commit"))

    def db_rollback(self) -> None:
        return self._db_command(dict(kind="rollback"))

    def _db_command(self, input: Dict[str, Any]) -> Any:
        try:
            return from_json_bytes(self._backend.db_command(to_json_bytes(input)))
        except Exception as e:
            err_bytes = bytes(e.args[0])
        err = backend_pb2.BackendError()
        err.ParseFromString(err_bytes)
        raise backend_exception_to_pylib(err)

    def translate(
        self, module_index: int, message_index: int, **kwargs: Union[str, int, float]
    ) -> str:
        return self.translate_string(
            translate_string_in(
                module_index=module_index, message_index=message_index, **kwargs
            )
        )

    def format_time_span(
        self,
        seconds: Any,
        context: Any = 2,
    ) -> str:
        traceback.print_stack(file=sys.stdout)
        print(
            "please use col.format_timespan() instead of col.backend.format_time_span()"
        )
        return self.format_timespan(seconds=seconds, context=context)

    def _run_command(self, service: int, method: int, input: Any) -> bytes:
        input_bytes = input.SerializeToString()
        try:
            return self._backend.command(service, method, input_bytes)
        except Exception as e:
            err_bytes = bytes(e.args[0])
        err = backend_pb2.BackendError()
        err.ParseFromString(err_bytes)
        raise backend_exception_to_pylib(err)


def translate_string_in(
    module_index: int, message_index: int, **kwargs: Union[str, int, float]
) -> i18n_pb2.TranslateStringRequest:
    args = {}
    for (k, v) in kwargs.items():
        if isinstance(v, str):
            args[k] = i18n_pb2.TranslateArgValue(str=v)
        else:
            args[k] = i18n_pb2.TranslateArgValue(number=v)
    return i18n_pb2.TranslateStringRequest(
        module_index=module_index, message_index=message_index, args=args
    )


class Translations(GeneratedTranslations):
    def __init__(self, backend: Optional[ref[RustBackend]]):
        self.backend = backend

    def __call__(self, key: Tuple[int, int], **kwargs: Any) -> str:
        "Mimic the old col.tr / TR interface"
        if "pytest" not in sys.modules:
            traceback.print_stack(file=sys.stdout)
            print("please use tr.message_name() instead of tr(TR.MESSAGE_NAME)")

        (module, message) = key
        return self.backend().translate(
            module_index=module, message_index=message, **kwargs
        )

    def _translate(
        self, module: int, message: int, args: Dict[str, Union[str, int, float]]
    ) -> str:
        return self.backend().translate(
            module_index=module, message_index=message, **args
        )


def backend_exception_to_pylib(err: backend_pb2.BackendError) -> Exception:
    kind = backend_pb2.BackendError
    val = err.kind
    if val == kind.INTERRUPTED:
        return Interrupted()

    elif val == kind.NETWORK_ERROR:
        return NetworkError(err.localized)

    elif val == kind.SYNC_AUTH_ERROR:
        return SyncError(err.localized, SyncErrorKind.AUTH)

    elif val == kind.SYNC_OTHER_ERROR:
        return SyncError(err.localized, SyncErrorKind.OTHER)

    elif val == kind.IO_ERROR:
        return BackendIOError(err.localized)

    elif val == kind.DB_ERROR:
        return DBError(err.localized)

    elif val == kind.TEMPLATE_PARSE:
        return TemplateError(err.localized)

    elif val == kind.INVALID_INPUT:
        return InvalidInput(err.localized)

    elif val == kind.JSON_ERROR:
        return LocalizedError(err.localized)

    elif val == kind.NOT_FOUND_ERROR:
        return NotFoundError()

    elif val == kind.EXISTS:
        return ExistsError()

    elif val == kind.FILTERED_DECK_ERROR:
        return FilteredDeckError(err.localized)

    elif val == kind.PROTO_ERROR:
        return LocalizedError(err.localized)

    elif val == kind.SEARCH_ERROR:
        return SearchError(markdown(err.localized))

    elif val == kind.UNDO_EMPTY:
        return UndoEmpty()

    else:
        # sadly we can't do exhaustiveness checking on protobuf enums
        # assert_exhaustive(val)
        return LocalizedError(err.localized)
