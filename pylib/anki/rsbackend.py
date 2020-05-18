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
from typing import (
    Any,
    Callable,
    Dict,
    Iterable,
    List,
    NewType,
    NoReturn,
    Optional,
    Sequence,
    Tuple,
    Union,
)

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb
import anki.buildinfo
from anki import hooks
from anki.dbproxy import Row as DBRow
from anki.dbproxy import ValueForDB
from anki.fluent_pb2 import FluentString as TR
from anki.sound import AVTag, SoundOrVideoTag, TTSTag
from anki.types import assert_impossible_literal
from anki.utils import intTime

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
except:
    # add compat layer for 32 bit builds that can't use orjson
    print("reverting to stock json")

    class orjson:  # type: ignore
        def dumps(obj: Any) -> bytes:
            return json.dumps(obj).encode("utf8")

        loads = json.loads


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
    else:
        assert_impossible_literal(val)


def av_tag_to_native(tag: pb.AVTag) -> AVTag:
    val = tag.WhichOneof("value")
    if val == "sound_or_video":
        return SoundOrVideoTag(filename=tag.sound_or_video)
    else:
        return TTSTag(
            field_text=tag.tts.field_text,
            lang=tag.tts.lang,
            voices=list(tag.tts.voices),
            other_args=list(tag.tts.other_args),
            speed=tag.tts.speed,
        )


@dataclass
class TemplateReplacement:
    field_name: str
    current_text: str
    filters: List[str]


TemplateReplacementList = List[Union[str, TemplateReplacement]]


@dataclass
class PartiallyRenderedCard:
    qnodes: TemplateReplacementList
    anodes: TemplateReplacementList


MediaSyncProgress = pb.MediaSyncProgress

MediaCheckOutput = pb.MediaCheckOut

FormatTimeSpanContext = pb.FormatTimeSpanIn.Context


@dataclass
class ExtractedLatex:
    filename: str
    latex_body: str


@dataclass
class ExtractedLatexOutput:
    html: str
    latex: List[ExtractedLatex]


class ProgressKind(enum.Enum):
    MediaSync = 0
    MediaCheck = 1


@dataclass
class Progress:
    kind: ProgressKind
    val: Union[MediaSyncProgress, str]


def proto_replacement_list_to_native(
    nodes: List[pb.RenderedTemplateNode],
) -> TemplateReplacementList:
    results: TemplateReplacementList = []
    for node in nodes:
        if node.WhichOneof("value") == "text":
            results.append(node.text)
        else:
            results.append(
                TemplateReplacement(
                    field_name=node.replacement.field_name,
                    current_text=node.replacement.current_text,
                    filters=list(node.replacement.filters),
                )
            )
    return results


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

    def _run_command(
        self, input: pb.BackendInput, release_gil: bool = False
    ) -> pb.BackendOutput:
        input_bytes = input.SerializeToString()
        output_bytes = self._backend.command(input_bytes, release_gil)
        output = pb.BackendOutput()
        output.ParseFromString(output_bytes)
        kind = output.WhichOneof("value")
        if kind == "error":
            raise proto_exception_to_native(output.error)
        else:
            return output

    def open_collection(
        self, col_path: str, media_folder_path: str, media_db_path: str, log_path: str
    ):
        self._run_command(
            pb.BackendInput(
                open_collection=pb.OpenCollectionIn(
                    collection_path=col_path,
                    media_folder_path=media_folder_path,
                    media_db_path=media_db_path,
                    log_path=log_path,
                )
            ),
            release_gil=True,
        )

    def close_collection(self, downgrade=True):
        self._run_command(
            pb.BackendInput(
                close_collection=pb.CloseCollectionIn(downgrade_to_schema11=downgrade)
            ),
            release_gil=True,
        )

    def sched_timing_today(self,) -> SchedTimingToday:
        return self._run_command(
            pb.BackendInput(sched_timing_today=pb.Empty())
        ).sched_timing_today

    def render_existing_card(self, cid: int, browser: bool) -> PartiallyRenderedCard:
        out = self._run_command(
            pb.BackendInput(
                render_existing_card=pb.RenderExistingCardIn(
                    card_id=cid, browser=browser,
                )
            )
        ).render_existing_card

        qnodes = proto_replacement_list_to_native(out.question_nodes)  # type: ignore
        anodes = proto_replacement_list_to_native(out.answer_nodes)  # type: ignore

        return PartiallyRenderedCard(qnodes, anodes)

    def render_uncommitted_card(
        self, note: BackendNote, card_ord: int, template: Dict, fill_empty: bool
    ) -> PartiallyRenderedCard:
        template_json = orjson.dumps(template)
        out = self._run_command(
            pb.BackendInput(
                render_uncommitted_card=pb.RenderUncommittedCardIn(
                    note=note,
                    template=template_json,
                    card_ord=card_ord,
                    fill_empty=fill_empty,
                )
            )
        ).render_uncommitted_card

        qnodes = proto_replacement_list_to_native(out.question_nodes)  # type: ignore
        anodes = proto_replacement_list_to_native(out.answer_nodes)  # type: ignore

        return PartiallyRenderedCard(qnodes, anodes)

    def local_minutes_west(self, stamp: int) -> int:
        return self._run_command(
            pb.BackendInput(local_minutes_west=stamp)
        ).local_minutes_west

    def strip_av_tags(self, text: str) -> str:
        return self._run_command(pb.BackendInput(strip_av_tags=text)).strip_av_tags

    def extract_av_tags(
        self, text: str, question_side: bool
    ) -> Tuple[str, List[AVTag]]:
        out = self._run_command(
            pb.BackendInput(
                extract_av_tags=pb.ExtractAVTagsIn(
                    text=text, question_side=question_side
                )
            )
        ).extract_av_tags
        native_tags = list(map(av_tag_to_native, out.av_tags))

        return out.text, native_tags

    def extract_latex(
        self, text: str, svg: bool, expand_clozes: bool
    ) -> ExtractedLatexOutput:
        out = self._run_command(
            pb.BackendInput(
                extract_latex=pb.ExtractLatexIn(
                    text=text, svg=svg, expand_clozes=expand_clozes
                )
            )
        ).extract_latex

        return ExtractedLatexOutput(
            html=out.text,
            latex=[
                ExtractedLatex(filename=l.filename, latex_body=l.latex_body)
                for l in out.latex
            ],
        )

    def add_file_to_media_folder(self, desired_name: str, data: bytes) -> str:
        return self._run_command(
            pb.BackendInput(
                add_media_file=pb.AddMediaFileIn(desired_name=desired_name, data=data)
            )
        ).add_media_file

    def sync_media(self, hkey: str, endpoint: str) -> None:
        self._run_command(
            pb.BackendInput(sync_media=pb.SyncMediaIn(hkey=hkey, endpoint=endpoint,)),
            release_gil=True,
        )

    def check_media(self) -> MediaCheckOutput:
        return self._run_command(
            pb.BackendInput(check_media=pb.Empty()), release_gil=True,
        ).check_media

    def trash_media_files(self, fnames: List[str]) -> None:
        self._run_command(
            pb.BackendInput(trash_media_files=pb.TrashMediaFilesIn(fnames=fnames))
        )

    def translate(self, key: TR, **kwargs: Union[str, int, float]) -> str:
        return self._run_command(
            pb.BackendInput(translate_string=translate_string_in(key, **kwargs))
        ).translate_string

    def format_time_span(
        self,
        seconds: float,
        context: FormatTimeSpanContext = FormatTimeSpanContext.INTERVALS,
    ) -> str:
        return self._run_command(
            pb.BackendInput(
                format_time_span=pb.FormatTimeSpanIn(seconds=seconds, context=context)
            )
        ).format_time_span

    def studied_today(self, cards: int, seconds: float) -> str:
        return self._run_command(
            pb.BackendInput(
                studied_today=pb.StudiedTodayIn(cards=cards, seconds=seconds)
            )
        ).studied_today

    def learning_congrats_msg(self, next_due: float, remaining: int) -> str:
        return self._run_command(
            pb.BackendInput(
                congrats_learn_msg=pb.CongratsLearnMsgIn(
                    next_due=next_due, remaining=remaining
                )
            )
        ).congrats_learn_msg

    def empty_trash(self):
        self._run_command(pb.BackendInput(empty_trash=pb.Empty()))

    def restore_trash(self):
        self._run_command(pb.BackendInput(restore_trash=pb.Empty()))

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
        return orjson.loads(self._backend.db_command(orjson.dumps(input)))

    def search_cards(
        self, search: str, order: Union[bool, str, int], reverse: bool = False
    ) -> Sequence[int]:
        if isinstance(order, str):
            mode = pb.SortOrder(custom=order)
        elif order is True:
            mode = pb.SortOrder(from_config=pb.Empty())
        elif order is False:
            mode = pb.SortOrder(none=pb.Empty())
        else:
            # sadly we can't use the protobuf type in a Union, so we
            # have to accept an int and convert it
            kind = BuiltinSortKind.Value(BuiltinSortKind.Name(order))
            mode = pb.SortOrder(
                builtin=pb.BuiltinSearchOrder(kind=kind, reverse=reverse)
            )

        return self._run_command(
            pb.BackendInput(search_cards=pb.SearchCardsIn(search=search, order=mode))
        ).search_cards.card_ids

    def search_notes(self, search: str) -> Sequence[int]:
        return self._run_command(
            pb.BackendInput(search_notes=pb.SearchNotesIn(search=search))
        ).search_notes.note_ids

    def get_card(self, cid: int) -> Optional[pb.Card]:
        output = self._run_command(pb.BackendInput(get_card=cid)).get_card
        if output.HasField("card"):
            return output.card
        else:
            return None

    def update_card(self, card: BackendCard) -> None:
        self._run_command(pb.BackendInput(update_card=card))

    # returns the new card id
    def add_card(self, card: BackendCard) -> int:
        return self._run_command(pb.BackendInput(add_card=card)).add_card

    def get_deck_config(self, dcid: int) -> Dict[str, Any]:
        jstr = self._run_command(pb.BackendInput(get_deck_config=dcid)).get_deck_config
        return orjson.loads(jstr)

    def add_or_update_deck_config(self, conf: Dict[str, Any], preserve_usn) -> None:
        conf_json = orjson.dumps(conf)
        id = self._run_command(
            pb.BackendInput(
                add_or_update_deck_config=pb.AddOrUpdateDeckConfigIn(
                    config=conf_json, preserve_usn_and_mtime=preserve_usn
                )
            )
        ).add_or_update_deck_config
        conf["id"] = id

    def all_deck_config(self) -> Sequence[Dict[str, Any]]:
        jstr = self._run_command(
            pb.BackendInput(all_deck_config=pb.Empty())
        ).all_deck_config
        return orjson.loads(jstr)

    def new_deck_config(self) -> Dict[str, Any]:
        jstr = self._run_command(
            pb.BackendInput(new_deck_config=pb.Empty())
        ).new_deck_config
        return orjson.loads(jstr)

    def remove_deck_config(self, dcid: int) -> None:
        self._run_command(pb.BackendInput(remove_deck_config=dcid))

    def abort_media_sync(self):
        self._run_command(pb.BackendInput(abort_media_sync=pb.Empty()))

    def all_tags(self) -> Iterable[TagUsnTuple]:
        return self._run_command(pb.BackendInput(all_tags=pb.Empty())).all_tags.tags

    def register_tags(self, tags: str, usn: Optional[int], clear_first: bool) -> bool:
        if usn is None:
            preserve_usn = False
            usn_ = 0
        else:
            usn_ = usn
            preserve_usn = True

        return self._run_command(
            pb.BackendInput(
                register_tags=pb.RegisterTagsIn(
                    tags=tags,
                    usn=usn_,
                    preserve_usn=preserve_usn,
                    clear_first=clear_first,
                )
            )
        ).register_tags

    def before_upload(self):
        self._run_command(pb.BackendInput(before_upload=pb.Empty()))

    def get_changed_tags(self, usn: int) -> List[str]:
        return list(
            self._run_command(
                pb.BackendInput(get_changed_tags=usn)
            ).get_changed_tags.tags
        )

    def get_config_json(self, key: str) -> Any:
        b = self._run_command(pb.BackendInput(get_config_json=key)).get_config_json
        if b == b"":
            raise KeyError
        return orjson.loads(b)

    def set_config_json(self, key: str, val: Any):
        self._run_command(
            pb.BackendInput(
                set_config_json=pb.SetConfigJson(key=key, val=orjson.dumps(val))
            )
        )

    def remove_config(self, key: str):
        self._run_command(
            pb.BackendInput(
                set_config_json=pb.SetConfigJson(key=key, remove=pb.Empty())
            )
        )

    def get_all_config(self) -> Dict[str, Any]:
        jstr = self._run_command(
            pb.BackendInput(get_all_config=pb.Empty())
        ).get_all_config
        return orjson.loads(jstr)

    def set_all_config(self, conf: Dict[str, Any]):
        self._run_command(pb.BackendInput(set_all_config=orjson.dumps(conf)))

    def get_changed_notetypes(self, usn: int) -> Dict[str, Dict[str, Any]]:
        jstr = self._run_command(
            pb.BackendInput(get_changed_notetypes=usn)
        ).get_changed_notetypes
        return orjson.loads(jstr)

    def get_all_decks(self) -> Dict[str, Dict[str, Any]]:
        jstr = self._run_command(
            pb.BackendInput(get_all_decks=pb.Empty())
        ).get_all_decks
        return orjson.loads(jstr)

    def get_stock_notetype_legacy(self, kind: StockNoteType) -> Dict[str, Any]:
        bytes = self._run_command(
            pb.BackendInput(get_stock_notetype_legacy=kind)
        ).get_stock_notetype_legacy
        return orjson.loads(bytes)

    def get_notetype_names_and_ids(self) -> List[pb.NoteTypeNameID]:
        return list(
            self._run_command(
                pb.BackendInput(get_notetype_names=pb.Empty())
            ).get_notetype_names.entries
        )

    def get_notetype_use_counts(self) -> List[pb.NoteTypeNameIDUseCount]:
        return list(
            self._run_command(
                pb.BackendInput(get_notetype_names_and_counts=pb.Empty())
            ).get_notetype_names_and_counts.entries
        )

    def get_notetype_legacy(self, ntid: int) -> Optional[Dict]:
        try:
            bytes = self._run_command(
                pb.BackendInput(get_notetype_legacy=ntid)
            ).get_notetype_legacy
        except NotFoundError:
            return None
        return orjson.loads(bytes)

    def get_notetype_id_by_name(self, name: str) -> Optional[int]:
        return (
            self._run_command(
                pb.BackendInput(get_notetype_id_by_name=name)
            ).get_notetype_id_by_name
            or None
        )

    def add_or_update_notetype(self, nt: Dict[str, Any], preserve_usn: bool) -> None:
        bjson = orjson.dumps(nt)
        id = self._run_command(
            pb.BackendInput(
                add_or_update_notetype=pb.AddOrUpdateNotetypeIn(
                    json=bjson, preserve_usn_and_mtime=preserve_usn
                )
            ),
            release_gil=True,
        ).add_or_update_notetype
        nt["id"] = id

    def remove_notetype(self, ntid: int) -> None:
        self._run_command(pb.BackendInput(remove_notetype=ntid), release_gil=True)

    def new_note(self, ntid: int) -> BackendNote:
        return self._run_command(pb.BackendInput(new_note=ntid)).new_note

    def add_note(self, note: BackendNote, deck_id: int) -> int:
        return self._run_command(
            pb.BackendInput(add_note=pb.AddNoteIn(note=note, deck_id=deck_id))
        ).add_note

    def update_note(self, note: BackendNote) -> None:
        self._run_command(pb.BackendInput(update_note=note))

    def get_note(self, nid) -> Optional[BackendNote]:
        try:
            return self._run_command(pb.BackendInput(get_note=nid)).get_note
        except NotFoundError:
            return None

    def empty_cards_report(self) -> pb.EmptyCardsReport:
        return self._run_command(
            pb.BackendInput(get_empty_cards=pb.Empty()), release_gil=True
        ).get_empty_cards

    def get_deck_legacy(self, did: int) -> Optional[Dict]:
        try:
            bytes = self._run_command(
                pb.BackendInput(get_deck_legacy=did)
            ).get_deck_legacy
            return orjson.loads(bytes)
        except NotFoundError:
            return None

    def get_deck_names_and_ids(
        self, skip_empty_default: bool, include_filtered: bool = True
    ) -> Sequence[pb.DeckNameID]:
        return self._run_command(
            pb.BackendInput(
                get_deck_names=pb.GetDeckNamesIn(
                    skip_empty_default=skip_empty_default,
                    include_filtered=include_filtered,
                )
            )
        ).get_deck_names.entries

    def add_or_update_deck_legacy(
        self, deck: Dict[str, Any], preserve_usn: bool
    ) -> None:
        deck_json = orjson.dumps(deck)
        id = self._run_command(
            pb.BackendInput(
                add_or_update_deck_legacy=pb.AddOrUpdateDeckLegacyIn(
                    deck=deck_json, preserve_usn_and_mtime=preserve_usn
                )
            )
        ).add_or_update_deck_legacy
        deck["id"] = id

    def new_deck_legacy(self, filtered: bool) -> Dict[str, Any]:
        jstr = self._run_command(
            pb.BackendInput(new_deck_legacy=filtered)
        ).new_deck_legacy
        return orjson.loads(jstr)

    def get_deck_id_by_name(self, name: str) -> Optional[int]:
        return (
            self._run_command(
                pb.BackendInput(get_deck_id_by_name=name)
            ).get_deck_id_by_name
            or None
        )

    def remove_deck(self, did: int) -> None:
        self._run_command(pb.BackendInput(remove_deck=did))

    def deck_tree(self, include_counts: bool, top_deck_id: int = 0) -> DeckTreeNode:
        return self._run_command(
            pb.BackendInput(
                deck_tree=pb.DeckTreeIn(
                    include_counts=include_counts, top_deck_id=top_deck_id
                )
            )
        ).deck_tree

    def check_database(self) -> List[str]:
        return list(
            self._run_command(
                pb.BackendInput(check_database=pb.Empty()), release_gil=True
            ).check_database.problems
        )

    def legacy_deck_tree(self) -> Sequence:
        bytes = self._run_command(
            pb.BackendInput(deck_tree_legacy=pb.Empty())
        ).deck_tree_legacy
        return orjson.loads(bytes)[5]

    def field_names_for_note_ids(self, nids: List[int]) -> Sequence[str]:
        return self._run_command(
            pb.BackendInput(field_names_for_notes=pb.FieldNamesForNotesIn(nids=nids)),
            release_gil=True,
        ).field_names_for_notes.fields

    def find_and_replace(
        self,
        nids: List[int],
        search: str,
        repl: str,
        re: bool,
        nocase: bool,
        field_name: Optional[str],
    ) -> int:
        return self._run_command(
            pb.BackendInput(
                find_and_replace=pb.FindAndReplaceIn(
                    nids=nids,
                    search=search,
                    replacement=repl,
                    regex=re,
                    match_case=not nocase,
                    field_name=field_name,
                )
            ),
            release_gil=True,
        ).find_and_replace

    def after_note_updates(
        self, nids: List[int], generate_cards: bool, mark_notes_modified: bool
    ) -> None:
        self._run_command(
            pb.BackendInput(
                after_note_updates=pb.AfterNoteUpdatesIn(
                    nids=nids,
                    generate_cards=generate_cards,
                    mark_notes_modified=mark_notes_modified,
                )
            ),
            release_gil=True,
        )

    def add_note_tags(self, nids: List[int], tags: str) -> int:
        return self._run_command(
            pb.BackendInput(add_note_tags=pb.AddNoteTagsIn(nids=nids, tags=tags))
        ).add_note_tags

    def update_note_tags(
        self, nids: List[int], tags: str, replacement: str, regex: bool
    ) -> int:
        return self._run_command(
            pb.BackendInput(
                update_note_tags=pb.UpdateNoteTagsIn(
                    nids=nids, tags=tags, replacement=replacement, regex=regex
                )
            )
        ).update_note_tags

    def set_local_minutes_west(self, mins: int) -> None:
        self._run_command(pb.BackendInput(set_local_minutes_west=mins))

    def get_preferences(self) -> pb.Preferences:
        return self._run_command(
            pb.BackendInput(get_preferences=pb.Empty())
        ).get_preferences

    def set_preferences(self, prefs: pb.Preferences) -> None:
        self._run_command(pb.BackendInput(set_preferences=prefs))

    def cloze_numbers_in_note(self, note: pb.Note) -> List[int]:
        return list(
            self._run_command(
                pb.BackendInput(cloze_numbers_in_note=note)
            ).cloze_numbers_in_note.numbers
        )


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
