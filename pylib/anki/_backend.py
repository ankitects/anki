# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

from __future__ import annotations

import sys
import time
import traceback
from threading import current_thread, main_thread
from typing import TYPE_CHECKING, Any, Iterable, Sequence
from weakref import ref

from markdown import markdown

import anki.buildinfo
from anki import _rsbridge, backend_pb2, i18n_pb2
from anki._backend_generated import RustBackendGenerated
from anki._fluent import GeneratedTranslations
from anki.dbproxy import Row as DBRow
from anki.dbproxy import ValueForDB
from anki.utils import from_json_bytes, to_json_bytes

if TYPE_CHECKING:
    from anki.collection import FsrsItem

from .errors import (
    BackendError,
    BackendIOError,
    CardTypeError,
    CustomStudyError,
    DBError,
    ExistsError,
    FilteredDeckError,
    Interrupted,
    InvalidInput,
    NetworkError,
    NotFoundError,
    SchedulerUpgradeRequired,
    SearchError,
    SyncError,
    SyncErrorKind,
    TemplateError,
    UndoEmpty,
)

# the following comment is required to suppress a warning that only shows up
# when there are other pylint failures
# pylint: disable=c-extension-no-member
if _rsbridge.buildhash() != anki.buildinfo.buildhash:
    raise Exception(
        f"""rsbridge and anki build hashes do not match:
{_rsbridge.buildhash()} vs {anki.buildinfo.buildhash}"""
    )


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

    @staticmethod
    def initialize_logging(path: str | None = None) -> None:
        _rsbridge.initialize_logging(path)

    def __init__(
        self,
        langs: list[str] | None = None,
        server: bool = False,
    ) -> None:
        # pick up global defaults if not provided
        import anki.lang

        if langs is None:
            langs = [anki.lang.current_lang]

        init_msg = backend_pb2.BackendInit(
            preferred_langs=langs,
            server=server,
        )
        self._backend = _rsbridge.open_backend(init_msg.SerializeToString())

    @staticmethod
    def syncserver() -> None:
        _rsbridge.syncserver()

    def db_query(
        self, sql: str, args: Sequence[ValueForDB], first_row_only: bool
    ) -> list[DBRow]:
        return self._db_command(
            dict(kind="query", sql=sql, args=args, first_row_only=first_row_only)
        )

    def db_execute_many(self, sql: str, args: list[list[ValueForDB]]) -> list[DBRow]:
        return self._db_command(dict(kind="executemany", sql=sql, args=args))

    def db_begin(self) -> None:
        return self._db_command(dict(kind="begin"))

    def db_commit(self) -> None:
        return self._db_command(dict(kind="commit"))

    def db_rollback(self) -> None:
        return self._db_command(dict(kind="rollback"))

    def _db_command(self, input: dict[str, Any]) -> Any:
        bytes_input = to_json_bytes(input)
        try:
            return from_json_bytes(self._backend.db_command(bytes_input))
        except Exception as error:
            err_bytes = bytes(error.args[0])
        err = backend_pb2.BackendError()
        err.ParseFromString(err_bytes)
        raise backend_exception_to_pylib(err)

    def translate(
        self, module_index: int, message_index: int, **kwargs: str | int | float
    ) -> str:
        args = {
            k: i18n_pb2.TranslateArgValue(str=v)
            if isinstance(v, str)
            else i18n_pb2.TranslateArgValue(number=v)
            for k, v in kwargs.items()
        }

        return self.translate_string(
            module_index=module_index, message_index=message_index, args=args
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

    def compute_weights_from_items(self, items: Iterable[FsrsItem]) -> Sequence[float]:
        return self.compute_fsrs_weights_from_items(items).weights

    def _run_command(self, service: int, method: int, input: bytes) -> bytes:
        start = time.time()
        try:
            return self._backend.command(service, method, input)
        except Exception as error:
            error_bytes = bytes(error.args[0])
        finally:
            elapsed = time.time() - start
            if current_thread() is main_thread() and elapsed > 0.2:
                print(f"blocked main thread for {int(elapsed*1000)}ms:")
                print("".join(traceback.format_stack()))

        err = backend_pb2.BackendError()
        err.ParseFromString(error_bytes)
        raise backend_exception_to_pylib(err)


class Translations(GeneratedTranslations):
    def __init__(self, backend: ref[RustBackend] | None):
        self.backend = backend

    def __call__(self, key: tuple[int, int], **kwargs: Any) -> str:
        "Mimic the old col.tr / TR interface"
        if "pytest" not in sys.modules:
            traceback.print_stack(file=sys.stdout)
            print("please use tr.message_name() instead of tr(TR.MESSAGE_NAME)")

        (module, message) = key
        return self.backend().translate(
            module_index=module, message_index=message, **kwargs
        )

    def _translate(
        self, module: int, message: int, args: dict[str, str | int | float]
    ) -> str:
        return self.backend().translate(
            module_index=module, message_index=message, **args
        )


def backend_exception_to_pylib(err: backend_pb2.BackendError) -> Exception:
    kind = backend_pb2.BackendError
    val = err.kind
    help_page = err.help_page if err.HasField("help_page") else None
    context = err.context if err.context else None
    backtrace = err.backtrace if err.backtrace else None

    if val == kind.INTERRUPTED:
        return Interrupted(err.message, help_page, context, backtrace)

    elif val == kind.NETWORK_ERROR:
        return NetworkError(err.message, help_page, context, backtrace)

    elif val == kind.SYNC_AUTH_ERROR:
        return SyncError(err.message, help_page, context, backtrace, SyncErrorKind.AUTH)

    elif val == kind.SYNC_OTHER_ERROR:
        return SyncError(
            err.message, help_page, context, backtrace, SyncErrorKind.OTHER
        )

    elif val == kind.IO_ERROR:
        return BackendIOError(err.message, help_page, context, backtrace)

    elif val == kind.DB_ERROR:
        return DBError(err.message, help_page, context, backtrace)

    elif val == kind.CARD_TYPE_ERROR:
        return CardTypeError(err.message, help_page, context, backtrace)

    elif val == kind.TEMPLATE_PARSE:
        return TemplateError(err.message, help_page, context, backtrace)

    elif val == kind.INVALID_INPUT:
        return InvalidInput(err.message, help_page, context, backtrace)

    elif val == kind.JSON_ERROR:
        return BackendError(err.message, help_page, context, backtrace)

    elif val == kind.NOT_FOUND_ERROR:
        return NotFoundError(err.message, help_page, context, backtrace)

    elif val == kind.EXISTS:
        return ExistsError(err.message, help_page, context, backtrace)

    elif val == kind.FILTERED_DECK_ERROR:
        return FilteredDeckError(err.message, help_page, context, backtrace)

    elif val == kind.PROTO_ERROR:
        return BackendError(err.message, help_page, context, backtrace)

    elif val == kind.SEARCH_ERROR:
        return SearchError(markdown(err.message), help_page, context, backtrace)

    elif val == kind.UNDO_EMPTY:
        return UndoEmpty(err.message, help_page, context, backtrace)

    elif val == kind.CUSTOM_STUDY_ERROR:
        return CustomStudyError(err.message, help_page, context, backtrace)

    elif val == kind.SCHEDULER_UPGRADE_REQUIRED:
        return SchedulerUpgradeRequired(err.message, help_page, context, backtrace)

    else:
        # sadly we can't do exhaustiveness checking on protobuf enums
        # assert_exhaustive(val)
        return BackendError(err.message, help_page, context, backtrace)
