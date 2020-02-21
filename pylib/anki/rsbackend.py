# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# pylint: skip-file

import enum
import os
from dataclasses import dataclass
from typing import Callable, Dict, List, NewType, NoReturn, Optional, Tuple, Union

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb
import anki.buildinfo
from anki import hooks
from anki.models import AllTemplateReqs
from anki.sound import AVTag, SoundOrVideoTag, TTSTag
from anki.types import assert_impossible_literal

assert ankirspy.buildhash() == anki.buildinfo.buildhash

SchedTimingToday = pb.SchedTimingTodayOut


class Interrupted(Exception):
    pass


class StringError(Exception):
    def __str__(self) -> str:
        return self.args[0]  # pylint: disable=unsubscriptable-object


NetworkErrorKind = pb.NetworkError.NetworkErrorKind


class NetworkError(StringError):
    def kind(self) -> NetworkErrorKind:
        return self.args[1]

    def localized(self) -> str:
        return self.args[2]


class IOError(StringError):
    pass


class DBError(StringError):
    pass


class TemplateError(StringError):
    pass


SyncErrorKind = pb.SyncError.SyncErrorKind


class SyncError(StringError):
    def kind(self) -> SyncErrorKind:
        return self.args[1]

    def localized(self) -> str:
        return self.args[2]


def proto_exception_to_native(err: pb.BackendError) -> Exception:
    val = err.WhichOneof("value")
    if val == "interrupted":
        return Interrupted()
    elif val == "network_error":
        e = err.network_error
        return NetworkError(e.info, e.kind, e.localized)
    elif val == "io_error":
        return IOError(err.io_error.info)
    elif val == "db_error":
        return DBError(err.db_error.info)
    elif val == "template_parse":
        return TemplateError(err.template_parse.info)
    elif val == "invalid_input":
        return StringError(err.invalid_input.info)
    elif val == "sync_error":
        e2 = err.sync_error
        return SyncError(e2.info, e2.kind, e2.localized)
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

StringsGroup = pb.StringsGroup

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


class RustBackend:
    def __init__(self, col_path: str, media_folder_path: str, media_db_path: str):
        ftl_folder = os.path.join(anki.lang.locale_folder, "fluent")
        init_msg = pb.BackendInit(
            collection_path=col_path,
            media_folder_path=media_folder_path,
            media_db_path=media_db_path,
            locale_folder_path=ftl_folder,
            preferred_langs=[anki.lang.currentLang],
        )
        self._backend = ankirspy.open_backend(init_msg.SerializeToString())
        self._backend.set_progress_callback(self._on_progress)

    def _on_progress(self, progress_bytes: bytes) -> bool:
        progress = pb.Progress()
        progress.ParseFromString(progress_bytes)
        native_progress = proto_progress_to_native(progress)
        return hooks.bg_thread_progress_callback(True, native_progress)

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
        created_mins_west: int,
        now_secs: int,
        now_mins_west: int,
        rollover: int,
    ) -> SchedTimingToday:
        return self._run_command(
            pb.BackendInput(
                sched_timing_today=pb.SchedTimingTodayIn(
                    created_secs=created_secs,
                    created_mins_west=created_mins_west,
                    now_secs=now_secs,
                    now_mins_west=now_mins_west,
                    rollover_hour=rollover,
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

    def extract_latex(self, text: str, svg: bool) -> ExtractedLatexOutput:
        out = self._run_command(
            pb.BackendInput(extract_latex=pb.ExtractLatexIn(text=text, svg=svg))
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

    def translate(
        self, group: pb.StringsGroup, key: str, **kwargs: Union[str, int, float]
    ):
        args = {}
        for (k, v) in kwargs.items():
            if isinstance(v, str):
                args[k] = pb.TranslateArgValue(str=v)
            else:
                args[k] = pb.TranslateArgValue(number=v)

        return self._run_command(
            pb.BackendInput(
                translate_string=pb.TranslateStringIn(group=group, key=key, args=args)
            )
        ).translate_string

    def format_time_span(
        self,
        seconds: float,
        context: FormatTimeSpanContext = FormatTimeSpanContext.NORMAL,
    ) -> str:
        return self._run_command(
            pb.BackendInput(
                format_time_span=pb.FormatTimeSpanIn(seconds=seconds, context=context)
            )
        ).format_time_span

    def studied_today(self, cards: int, seconds: float,) -> str:
        return self._run_command(
            pb.BackendInput(
                studied_today=pb.StudiedTodayIn(cards=cards, seconds=seconds)
            )
        ).studied_today
