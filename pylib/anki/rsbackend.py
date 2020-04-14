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
from anki.models import AllTemplateReqs
from anki.sound import AVTag, SoundOrVideoTag, TTSTag
from anki.types import assert_impossible_literal
from anki.utils import intTime

assert ankirspy.buildhash() == anki.buildinfo.buildhash

SchedTimingToday = pb.SchedTimingTodayOut
BuiltinSortKind = pb.BuiltinSearchOrder.BuiltinSortKind
BackendCard = pb.Card
TagUsnTuple = pb.TagUsnTuple

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
        return StringError(err.localized)
    elif val == "json_error":
        return StringError(err.localized)
    else:
        assert_impossible_literal(val)


def proto_template_reqs_to_legacy(
    reqs: List[pb.TemplateRequirement],
) -> AllTemplateReqs:
    legacy_reqs = []
    for (idx, req) in enumerate(reqs):
        kind = req.WhichOneof("value")
        # fixme: sorting is for the unit tests - should check if any
        # code depends on the order
        if kind == "any":
            legacy_reqs.append((idx, "any", sorted(req.any.ords)))
        elif kind == "all":
            legacy_reqs.append((idx, "all", sorted(req.all.ords)))
        else:
            l: List[int] = []
            legacy_reqs.append((idx, "none", l))
    return legacy_reqs


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

    def template_requirements(
        self, template_fronts: List[str], field_map: Dict[str, int]
    ) -> AllTemplateReqs:
        input = pb.BackendInput(
            template_requirements=pb.TemplateRequirementsIn(
                template_front=template_fronts, field_names_to_ordinals=field_map
            )
        )
        output = self._run_command(input).template_requirements
        reqs: List[pb.TemplateRequirement] = output.requirements  # type: ignore
        return proto_template_reqs_to_legacy(reqs)

    def sched_timing_today(
        self,
        created_secs: int,
        created_mins_west: Optional[int],
        now_mins_west: Optional[int],
        rollover: Optional[int],
    ) -> SchedTimingToday:
        if created_mins_west is not None:
            crt_west = pb.OptionalInt32(val=created_mins_west)
        else:
            crt_west = None

        if now_mins_west is not None:
            now_west = pb.OptionalInt32(val=now_mins_west)
        else:
            now_west = None

        if rollover is not None:
            roll = pb.OptionalInt32(val=rollover)
        else:
            roll = None

        return self._run_command(
            pb.BackendInput(
                sched_timing_today=pb.SchedTimingTodayIn(
                    created_secs=created_secs,
                    now_secs=intTime(),
                    created_mins_west=crt_west,
                    now_mins_west=now_west,
                    rollover_hour=roll,
                )
            )
        ).sched_timing_today

    def render_card(
        self, qfmt: str, afmt: str, fields: Dict[str, str], card_ord: int
    ) -> Tuple[TemplateReplacementList, TemplateReplacementList]:
        out = self._run_command(
            pb.BackendInput(
                render_card=pb.RenderCardIn(
                    question_template=qfmt,
                    answer_template=afmt,
                    fields=fields,
                    card_ordinal=card_ord,
                )
            )
        ).render_card

        qnodes = proto_replacement_list_to_native(out.question_nodes)  # type: ignore
        anodes = proto_replacement_list_to_native(out.answer_nodes)  # type: ignore

        return (qnodes, anodes)

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

    def canonify_tags(self, tags: str) -> Tuple[str, bool]:
        out = self._run_command(pb.BackendInput(canonify_tags=tags)).canonify_tags
        return (out.tags, out.tag_list_changed)

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

    def get_all_notetypes(self) -> Dict[str, Dict[str, Any]]:
        jstr = self._run_command(
            pb.BackendInput(get_all_notetypes=pb.Empty())
        ).get_all_notetypes
        return orjson.loads(jstr)

    def set_all_notetypes(self, nts: Dict[str, Dict[str, Any]]):
        self._run_command(pb.BackendInput(set_all_notetypes=orjson.dumps(nts)))

    def get_all_decks(self) -> Dict[str, Dict[str, Any]]:
        jstr = self._run_command(
            pb.BackendInput(get_all_decks=pb.Empty())
        ).get_all_decks
        return orjson.loads(jstr)

    def set_all_decks(self, nts: Dict[str, Dict[str, Any]]):
        self._run_command(pb.BackendInput(set_all_decks=orjson.dumps(nts)))


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
    os.environ["RUST_LOG"] = "warn,anki::media=debug"
