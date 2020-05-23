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

import enum
import json
import os
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Sequence, Union

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb
import anki.buildinfo
from anki import hooks
from anki.dbproxy import Row as DBRow
from anki.dbproxy import ValueForDB
from anki.fluent_pb2 import FluentString as TR
from anki.types import assert_impossible_literal

assert ankirspy.buildhash() == anki.buildinfo.buildhash

SchedTimingToday = pb.SchedTimingTodayOut
BuiltinSortKind = pb.BuiltinSearchOrder.BuiltinSortKind
BackendCard = pb.Card
BackendNote = pb.Note
TagUsnTuple = pb.TagUsnTuple
NoteType = pb.NoteType
DeckTreeNode = pb.DeckTreeNode
StockNoteType = pb.StockNoteType

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
    def kind(self) -> NetworkErrorKind:
        return self.args[1]


class SyncError(StringError):
    def kind(self) -> SyncErrorKind:
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

FormatTimeSpanContext = pb.FormatTimespanIn.Context


class ProgressKind(enum.Enum):
    MediaSync = 0
    MediaCheck = 1


@dataclass
class Progress:
    kind: ProgressKind
    val: Union[MediaSyncProgress, str]


def proto_progress_to_native(progress: pb.Progress) -> Progress:
    kind = progress.WhichOneof("value")
    if kind == "media_sync":
        return Progress(kind=ProgressKind.MediaSync, val=progress.media_sync)
    elif kind == "media_check":
        return Progress(kind=ProgressKind.MediaCheck, val=progress.media_check)
    else:
        assert_impossible_literal(kind)


def _on_progress(progress_bytes: bytes) -> bool:
    progress = pb.Progress()
    progress.ParseFromString(progress_bytes)
    native_progress = proto_progress_to_native(progress)
    return hooks.bg_thread_progress_callback(True, native_progress)


class RustBackend:
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
            locale_folder_path=ftl_folder, preferred_langs=langs, server=server,
        )
        self._backend = ankirspy.open_backend(init_msg.SerializeToString())
        self._backend.set_progress_callback(_on_progress)

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
        return from_json_bytes(self._backend.db_command(to_json_bytes(input)))

    def translate(self, key: TR, **kwargs: Union[str, int, float]) -> str:
        return self.translate_string(translate_string_in(key, **kwargs))

    def format_time_span(
        self,
        seconds: float,
        context: FormatTimeSpanContext = FormatTimeSpanContext.INTERVALS,
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

    # The code in this section is automatically generated - any edits you make
    # will be lost.

    # @@AUTOGEN@@

    def extract_av_tags(self, *, text: str, question_side: bool) -> pb.ExtractAVTagsOut:
        input = pb.ExtractAVTagsIn(text=text, question_side=question_side)
        output = pb.ExtractAVTagsOut()
        output.ParseFromString(self._run_command(1, input))
        return output

    def extract_latex(
        self, *, text: str, svg: bool, expand_clozes: bool
    ) -> pb.ExtractLatexOut:
        input = pb.ExtractLatexIn(text=text, svg=svg, expand_clozes=expand_clozes)
        output = pb.ExtractLatexOut()
        output.ParseFromString(self._run_command(2, input))
        return output

    def get_empty_cards(self) -> pb.EmptyCardsReport:
        input = pb.Empty()
        output = pb.EmptyCardsReport()
        output.ParseFromString(self._run_command(3, input))
        return output

    def render_existing_card(self, *, card_id: int, browser: bool) -> pb.RenderCardOut:
        input = pb.RenderExistingCardIn(card_id=card_id, browser=browser)
        output = pb.RenderCardOut()
        output.ParseFromString(self._run_command(4, input))
        return output

    def render_uncommitted_card(
        self, *, note: pb.Note, card_ord: int, template: bytes, fill_empty: bool
    ) -> pb.RenderCardOut:
        input = pb.RenderUncommittedCardIn(
            note=note, card_ord=card_ord, template=template, fill_empty=fill_empty
        )
        output = pb.RenderCardOut()
        output.ParseFromString(self._run_command(5, input))
        return output

    def strip_av_tags(self, val: str) -> str:
        input = pb.String(val=val)
        output = pb.String()
        output.ParseFromString(self._run_command(6, input))
        return output.val

    def search_cards(self, *, search: str, order: pb.SortOrder) -> Sequence[int]:
        input = pb.SearchCardsIn(search=search, order=order)
        output = pb.SearchCardsOut()
        output.ParseFromString(self._run_command(7, input))
        return output.card_ids

    def search_notes(self, search: str) -> Sequence[int]:
        input = pb.SearchNotesIn(search=search)
        output = pb.SearchNotesOut()
        output.ParseFromString(self._run_command(8, input))
        return output.note_ids

    def find_and_replace(
        self,
        *,
        nids: Sequence[int],
        search: str,
        replacement: str,
        regex: bool,
        match_case: bool,
        field_name: str,
    ) -> int:
        input = pb.FindAndReplaceIn(
            nids=nids,
            search=search,
            replacement=replacement,
            regex=regex,
            match_case=match_case,
            field_name=field_name,
        )
        output = pb.UInt32()
        output.ParseFromString(self._run_command(9, input))
        return output.val

    def local_minutes_west(self, val: int) -> int:
        input = pb.Int64(val=val)
        output = pb.Int32()
        output.ParseFromString(self._run_command(10, input))
        return output.val

    def set_local_minutes_west(self, val: int) -> pb.Empty:
        input = pb.Int32(val=val)
        output = pb.Empty()
        output.ParseFromString(self._run_command(11, input))
        return output

    def sched_timing_today(self) -> pb.SchedTimingTodayOut:
        input = pb.Empty()
        output = pb.SchedTimingTodayOut()
        output.ParseFromString(self._run_command(12, input))
        return output

    def studied_today(self, *, cards: int, seconds: float) -> str:
        input = pb.StudiedTodayIn(cards=cards, seconds=seconds)
        output = pb.String()
        output.ParseFromString(self._run_command(13, input))
        return output.val

    def congrats_learn_message(self, *, next_due: float, remaining: int) -> str:
        input = pb.CongratsLearnMessageIn(next_due=next_due, remaining=remaining)
        output = pb.String()
        output.ParseFromString(self._run_command(14, input))
        return output.val

    def check_media(self) -> pb.CheckMediaOut:
        input = pb.Empty()
        output = pb.CheckMediaOut()
        output.ParseFromString(self._run_command(15, input))
        return output

    def trash_media_files(self, fnames: Sequence[str]) -> pb.Empty:
        input = pb.TrashMediaFilesIn(fnames=fnames)
        output = pb.Empty()
        output.ParseFromString(self._run_command(16, input))
        return output

    def add_media_file(self, *, desired_name: str, data: bytes) -> str:
        input = pb.AddMediaFileIn(desired_name=desired_name, data=data)
        output = pb.String()
        output.ParseFromString(self._run_command(17, input))
        return output.val

    def empty_trash(self) -> pb.Empty:
        input = pb.Empty()
        output = pb.Empty()
        output.ParseFromString(self._run_command(18, input))
        return output

    def restore_trash(self) -> pb.Empty:
        input = pb.Empty()
        output = pb.Empty()
        output.ParseFromString(self._run_command(19, input))
        return output

    def add_or_update_deck_legacy(
        self, *, deck: bytes, preserve_usn_and_mtime: bool
    ) -> int:
        input = pb.AddOrUpdateDeckLegacyIn(
            deck=deck, preserve_usn_and_mtime=preserve_usn_and_mtime
        )
        output = pb.DeckID()
        output.ParseFromString(self._run_command(20, input))
        return output.did

    def deck_tree(self, *, include_counts: bool, top_deck_id: int) -> pb.DeckTreeNode:
        input = pb.DeckTreeIn(include_counts=include_counts, top_deck_id=top_deck_id)
        output = pb.DeckTreeNode()
        output.ParseFromString(self._run_command(21, input))
        return output

    def deck_tree_legacy(self) -> bytes:
        input = pb.Empty()
        output = pb.Json()
        output.ParseFromString(self._run_command(22, input))
        return output.json

    def get_all_decks_legacy(self) -> bytes:
        input = pb.Empty()
        output = pb.Json()
        output.ParseFromString(self._run_command(23, input))
        return output.json

    def get_deck_id_by_name(self, val: str) -> int:
        input = pb.String(val=val)
        output = pb.DeckID()
        output.ParseFromString(self._run_command(24, input))
        return output.did

    def get_deck_legacy(self, did: int) -> bytes:
        input = pb.DeckID(did=did)
        output = pb.Json()
        output.ParseFromString(self._run_command(25, input))
        return output.json

    def get_deck_names(
        self, *, skip_empty_default: bool, include_filtered: bool
    ) -> Sequence[pb.DeckNameID]:
        input = pb.GetDeckNamesIn(
            skip_empty_default=skip_empty_default, include_filtered=include_filtered
        )
        output = pb.DeckNames()
        output.ParseFromString(self._run_command(26, input))
        return output.entries

    def new_deck_legacy(self, val: bool) -> bytes:
        input = pb.Bool(val=val)
        output = pb.Json()
        output.ParseFromString(self._run_command(27, input))
        return output.json

    def remove_deck(self, did: int) -> pb.Empty:
        input = pb.DeckID(did=did)
        output = pb.Empty()
        output.ParseFromString(self._run_command(28, input))
        return output

    def add_or_update_deck_config_legacy(
        self, *, config: bytes, preserve_usn_and_mtime: bool
    ) -> int:
        input = pb.AddOrUpdateDeckConfigLegacyIn(
            config=config, preserve_usn_and_mtime=preserve_usn_and_mtime
        )
        output = pb.DeckConfigID()
        output.ParseFromString(self._run_command(29, input))
        return output.dcid

    def all_deck_config_legacy(self) -> bytes:
        input = pb.Empty()
        output = pb.Json()
        output.ParseFromString(self._run_command(30, input))
        return output.json

    def get_deck_config_legacy(self, dcid: int) -> bytes:
        input = pb.DeckConfigID(dcid=dcid)
        output = pb.Json()
        output.ParseFromString(self._run_command(31, input))
        return output.json

    def new_deck_config_legacy(self) -> bytes:
        input = pb.Empty()
        output = pb.Json()
        output.ParseFromString(self._run_command(32, input))
        return output.json

    def remove_deck_config(self, dcid: int) -> pb.Empty:
        input = pb.DeckConfigID(dcid=dcid)
        output = pb.Empty()
        output.ParseFromString(self._run_command(33, input))
        return output

    def get_card(self, cid: int) -> pb.Card:
        input = pb.CardID(cid=cid)
        output = pb.Card()
        output.ParseFromString(self._run_command(34, input))
        return output

    def update_card(self, input: pb.Card) -> pb.Empty:
        output = pb.Empty()
        output.ParseFromString(self._run_command(35, input))
        return output

    def add_card(self, input: pb.Card) -> int:
        output = pb.CardID()
        output.ParseFromString(self._run_command(36, input))
        return output.cid

    def new_note(self, ntid: int) -> pb.Note:
        input = pb.NoteTypeID(ntid=ntid)
        output = pb.Note()
        output.ParseFromString(self._run_command(37, input))
        return output

    def add_note(self, *, note: pb.Note, deck_id: int) -> int:
        input = pb.AddNoteIn(note=note, deck_id=deck_id)
        output = pb.NoteID()
        output.ParseFromString(self._run_command(38, input))
        return output.nid

    def update_note(self, input: pb.Note) -> pb.Empty:
        output = pb.Empty()
        output.ParseFromString(self._run_command(39, input))
        return output

    def get_note(self, nid: int) -> pb.Note:
        input = pb.NoteID(nid=nid)
        output = pb.Note()
        output.ParseFromString(self._run_command(40, input))
        return output

    def add_note_tags(self, *, nids: Sequence[int], tags: str) -> int:
        input = pb.AddNoteTagsIn(nids=nids, tags=tags)
        output = pb.UInt32()
        output.ParseFromString(self._run_command(41, input))
        return output.val

    def update_note_tags(
        self, *, nids: Sequence[int], tags: str, replacement: str, regex: bool
    ) -> int:
        input = pb.UpdateNoteTagsIn(
            nids=nids, tags=tags, replacement=replacement, regex=regex
        )
        output = pb.UInt32()
        output.ParseFromString(self._run_command(42, input))
        return output.val

    def cloze_numbers_in_note(self, input: pb.Note) -> Sequence[int]:
        output = pb.ClozeNumbersInNoteOut()
        output.ParseFromString(self._run_command(43, input))
        return output.numbers

    def after_note_updates(
        self, *, nids: Sequence[int], mark_notes_modified: bool, generate_cards: bool
    ) -> pb.Empty:
        input = pb.AfterNoteUpdatesIn(
            nids=nids,
            mark_notes_modified=mark_notes_modified,
            generate_cards=generate_cards,
        )
        output = pb.Empty()
        output.ParseFromString(self._run_command(44, input))
        return output

    def field_names_for_notes(self, nids: Sequence[int]) -> Sequence[str]:
        input = pb.FieldNamesForNotesIn(nids=nids)
        output = pb.FieldNamesForNotesOut()
        output.ParseFromString(self._run_command(45, input))
        return output.fields

    def add_or_update_notetype(
        self, *, json: bytes, preserve_usn_and_mtime: bool
    ) -> int:
        input = pb.AddOrUpdateNotetypeIn(
            json=json, preserve_usn_and_mtime=preserve_usn_and_mtime
        )
        output = pb.NoteTypeID()
        output.ParseFromString(self._run_command(46, input))
        return output.ntid

    def get_stock_notetype_legacy(self, kind: pb.StockNoteType) -> bytes:
        input = pb.GetStockNotetypeIn(kind=kind)
        output = pb.Json()
        output.ParseFromString(self._run_command(47, input))
        return output.json

    def get_notetype_legacy(self, ntid: int) -> bytes:
        input = pb.NoteTypeID(ntid=ntid)
        output = pb.Json()
        output.ParseFromString(self._run_command(48, input))
        return output.json

    def get_notetype_names(self) -> Sequence[pb.NoteTypeNameID]:
        input = pb.Empty()
        output = pb.NoteTypeNames()
        output.ParseFromString(self._run_command(49, input))
        return output.entries

    def get_notetype_names_and_counts(self) -> Sequence[pb.NoteTypeNameIDUseCount]:
        input = pb.Empty()
        output = pb.NoteTypeUseCounts()
        output.ParseFromString(self._run_command(50, input))
        return output.entries

    def get_notetype_id_by_name(self, val: str) -> int:
        input = pb.String(val=val)
        output = pb.NoteTypeID()
        output.ParseFromString(self._run_command(51, input))
        return output.ntid

    def remove_notetype(self, ntid: int) -> pb.Empty:
        input = pb.NoteTypeID(ntid=ntid)
        output = pb.Empty()
        output.ParseFromString(self._run_command(52, input))
        return output

    def open_collection(
        self,
        *,
        collection_path: str,
        media_folder_path: str,
        media_db_path: str,
        log_path: str,
    ) -> pb.Empty:
        input = pb.OpenCollectionIn(
            collection_path=collection_path,
            media_folder_path=media_folder_path,
            media_db_path=media_db_path,
            log_path=log_path,
        )
        output = pb.Empty()
        output.ParseFromString(self._run_command(53, input))
        return output

    def close_collection(self, downgrade_to_schema11: bool) -> pb.Empty:
        input = pb.CloseCollectionIn(downgrade_to_schema11=downgrade_to_schema11)
        output = pb.Empty()
        output.ParseFromString(self._run_command(54, input))
        return output

    def check_database(self) -> Sequence[str]:
        input = pb.Empty()
        output = pb.CheckDatabaseOut()
        output.ParseFromString(self._run_command(55, input))
        return output.problems

    def sync_media(self, *, hkey: str, endpoint: str) -> pb.Empty:
        input = pb.SyncMediaIn(hkey=hkey, endpoint=endpoint)
        output = pb.Empty()
        output.ParseFromString(self._run_command(56, input))
        return output

    def abort_media_sync(self) -> pb.Empty:
        input = pb.Empty()
        output = pb.Empty()
        output.ParseFromString(self._run_command(57, input))
        return output

    def before_upload(self) -> pb.Empty:
        input = pb.Empty()
        output = pb.Empty()
        output.ParseFromString(self._run_command(58, input))
        return output

    def translate_string(self, input: pb.TranslateStringIn) -> str:
        output = pb.String()
        output.ParseFromString(self._run_command(59, input))
        return output.val

    def format_timespan(
        self, *, seconds: float, context: pb.FormatTimespanIn.Context
    ) -> str:
        input = pb.FormatTimespanIn(seconds=seconds, context=context)
        output = pb.String()
        output.ParseFromString(self._run_command(60, input))
        return output.val

    def register_tags(
        self, *, tags: str, preserve_usn: bool, usn: int, clear_first: bool
    ) -> bool:
        input = pb.RegisterTagsIn(
            tags=tags, preserve_usn=preserve_usn, usn=usn, clear_first=clear_first
        )
        output = pb.Bool()
        output.ParseFromString(self._run_command(61, input))
        return output.val

    def all_tags(self) -> Sequence[pb.TagUsnTuple]:
        input = pb.Empty()
        output = pb.AllTagsOut()
        output.ParseFromString(self._run_command(62, input))
        return output.tags

    def get_changed_tags(self, val: int) -> Sequence[str]:
        input = pb.Int32(val=val)
        output = pb.GetChangedTagsOut()
        output.ParseFromString(self._run_command(63, input))
        return output.tags

    def get_config_json(self, val: str) -> bytes:
        input = pb.String(val=val)
        output = pb.Json()
        output.ParseFromString(self._run_command(64, input))
        return output.json

    def set_config_json(self, *, key: str, value_json: bytes) -> pb.Empty:
        input = pb.SetConfigJsonIn(key=key, value_json=value_json)
        output = pb.Empty()
        output.ParseFromString(self._run_command(65, input))
        return output

    def remove_config(self, val: str) -> pb.Empty:
        input = pb.String(val=val)
        output = pb.Empty()
        output.ParseFromString(self._run_command(66, input))
        return output

    def set_all_config(self, json: bytes) -> pb.Empty:
        input = pb.Json(json=json)
        output = pb.Empty()
        output.ParseFromString(self._run_command(67, input))
        return output

    def get_all_config(self) -> bytes:
        input = pb.Empty()
        output = pb.Json()
        output.ParseFromString(self._run_command(68, input))
        return output.json

    def get_preferences(self) -> pb.CollectionSchedulingSettings:
        input = pb.Empty()
        output = pb.Preferences()
        output.ParseFromString(self._run_command(69, input))
        return output.sched

    def set_preferences(self, sched: pb.CollectionSchedulingSettings) -> pb.Empty:
        input = pb.Preferences(sched=sched)
        output = pb.Empty()
        output.ParseFromString(self._run_command(70, input))
        return output

    # @@AUTOGEN@@


def translate_string_in(
    key: TR, **kwargs: Union[str, int, float]
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
    os.environ["RUST_LOG"] = "warn,anki::media=debug,anki::dbcheck=debug"
