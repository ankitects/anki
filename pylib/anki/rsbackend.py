# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# pylint: skip-file

"""
Python bindings for Anki's Rust libraries.

Please do not access methods on the backend directly - they may be changed
or removed at any time. Instead, please use the methods on the collection
instead. Eg, don't use col.backend.all_deck_config(), instead use
col.decks.all_config()
"""

from __future__ import annotations

import enum
import json
import os
from dataclasses import dataclass
from typing import TYPE_CHECKING, Any, Dict, List, Optional, Sequence, Union

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb
import anki.buildinfo
from anki import hooks
from anki.dbproxy import Row as DBRow
from anki.dbproxy import ValueForDB
from anki.fluent_pb2 import FluentString as TR
from anki.types import assert_impossible_literal

try:
    from anki.rsbackend_gen import RustBackendGenerated
except ImportError:
    # will fail during initial setup
    class RustBackendGenerated:  # type: ignore
        pass


if TYPE_CHECKING:
    from anki.fluent_pb2 import FluentStringValue as TRValue

    FormatTimeSpanContextValue = pb.FormatTimespanIn.ContextValue

assert ankirspy.buildhash() == anki.buildinfo.buildhash

SchedTimingToday = pb.SchedTimingTodayOut
BuiltinSortKind = pb.BuiltinSearchOrder.BuiltinSortKind
BackendCard = pb.Card
BackendNote = pb.Note
TagUsnTuple = pb.TagUsnTuple
NoteType = pb.NoteType
DeckTreeNode = pb.DeckTreeNode
StockNoteType = pb.StockNoteType
SyncAuth = pb.SyncAuth
SyncOutput = pb.SyncCollectionOut
SyncStatus = pb.SyncStatusOut
CountsForDeckToday = pb.CountsForDeckTodayOut

try:
    import orjson

    to_json_bytes = orjson.dumps
    from_json_bytes = orjson.loads
except:
    # add compat layer for 32 bit builds that can't use orjson
    to_json_bytes = lambda obj: json.dumps(obj).encode("utf8")  # type: ignore
    from_json_bytes = json.loads


class Interrupted(Exception):
    pass


class StringError(Exception):
    def __str__(self) -> str:
        return self.args[0]  # pylint: disable=unsubscriptable-object


NetworkErrorKind = pb.NetworkError.NetworkErrorKind
SyncErrorKind = pb.SyncError.SyncErrorKind


class NetworkError(StringError):
    def kind(self) -> pb.NetworkError.NetworkErrorKindValue:
        return self.args[1]


class SyncError(StringError):
    def kind(self) -> pb.SyncError.SyncErrorKindValue:
        return self.args[1]


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


class DeckIsFilteredError(Exception):
    pass


class InvalidInput(StringError):
    pass


def proto_exception_to_native(err: pb.BackendError) -> Exception:
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
    elif val == "deck_is_filtered":
        return DeckIsFilteredError()
    elif val == "proto_error":
        return StringError(err.localized)
    else:
        print("unhandled error type:", val)
        return StringError(err.localized)


MediaSyncProgress = pb.MediaSyncProgress
FullSyncProgress = pb.FullSyncProgress
NormalSyncProgress = pb.NormalSyncProgress
DatabaseCheckProgress = pb.DatabaseCheckProgress

FormatTimeSpanContext = pb.FormatTimespanIn.Context


class ProgressKind(enum.Enum):
    NoProgress = 0
    MediaSync = 1
    MediaCheck = 2
    FullSync = 3
    NormalSync = 4
    DatabaseCheck = 5


@dataclass
class Progress:
    kind: ProgressKind
    val: Union[
        MediaSyncProgress,
        pb.FullSyncProgress,
        NormalSyncProgress,
        DatabaseCheckProgress,
        str,
    ]

    @staticmethod
    def from_proto(proto: pb.Progress) -> Progress:
        kind = proto.WhichOneof("value")
        if kind == "media_sync":
            return Progress(kind=ProgressKind.MediaSync, val=proto.media_sync)
        elif kind == "media_check":
            return Progress(kind=ProgressKind.MediaCheck, val=proto.media_check)
        elif kind == "full_sync":
            return Progress(kind=ProgressKind.FullSync, val=proto.full_sync)
        elif kind == "normal_sync":
            return Progress(kind=ProgressKind.NormalSync, val=proto.normal_sync)
        elif kind == "database_check":
            return Progress(kind=ProgressKind.DatabaseCheck, val=proto.database_check)
        else:
            return Progress(kind=ProgressKind.NoProgress, val="")


class RustBackend(RustBackendGenerated):
    def __init__(
        self,
        ftl_folder: Optional[str] = None,
        langs: Optional[List[str]] = None,
        server: bool = False,
    ) -> None:
        # pick up global defaults if not provided
        if ftl_folder is None:
            ftl_folder = os.path.join(anki.lang.locale_folder, "fluent")
        if langs is None:
            langs = [anki.lang.currentLang]

        init_msg = pb.BackendInit(
            locale_folder_path=ftl_folder,
            preferred_langs=langs,
            server=server,
        )
        self._backend = ankirspy.open_backend(init_msg.SerializeToString())

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
        err = pb.BackendError()
        err.ParseFromString(err_bytes)
        raise proto_exception_to_native(err)

    def translate(self, key: TRValue, **kwargs: Union[str, int, float]) -> str:
        return self.translate_string(translate_string_in(key, **kwargs))

    def format_time_span(
        self,
        seconds: float,
        context: FormatTimeSpanContextValue = FormatTimeSpanContext.INTERVALS,
    ) -> str:
        print(
            "please use col.format_timespan() instead of col.backend.format_time_span()"
        )
        return self.format_timespan(seconds=seconds, context=context)

    def _run_command(self, method: int, input: Any) -> bytes:
        input_bytes = input.SerializeToString()
        try:
            return self._backend.command(method, input_bytes)
        except Exception as e:
            err_bytes = bytes(e.args[0])
        err = pb.BackendError()
        err.ParseFromString(err_bytes)
        raise proto_exception_to_native(err)


def translate_string_in(
    key: TRValue, **kwargs: Union[str, int, float]
) -> pb.TranslateStringIn:
    args = {}
    for (k, v) in kwargs.items():
        if isinstance(v, str):
            args[k] = pb.TranslateArgValue(str=v)
        else:
            args[k] = pb.TranslateArgValue(number=v)
    return pb.TranslateStringIn(key=key, args=args)


# temporarily force logging of media handling
if "RUST_LOG" not in os.environ:
    os.environ[
        "RUST_LOG"
    ] = "warn,anki::media=debug,anki::sync=debug,anki::dbcheck=debug"
