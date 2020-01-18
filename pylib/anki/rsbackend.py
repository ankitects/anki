# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
# pylint: skip-file
from dataclasses import dataclass
from typing import Dict, List, Tuple, Union

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb
import anki.buildinfo
from anki.models import AllTemplateReqs

assert ankirspy.buildhash() == anki.buildinfo.buildhash

SchedTimingToday = pb.SchedTimingTodayOut


class BackendException(Exception):
    def __str__(self) -> str:
        err: pb.BackendError = self.args[0]  # pylint: disable=unsubscriptable-object
        kind = err.WhichOneof("value")
        if kind == "invalid_input":
            return f"invalid input: {err.invalid_input.info}"
        elif kind == "template_parse":
            return err.template_parse.info
        else:
            return f"unhandled error: {err}"


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


@dataclass
class TemplateReplacement:
    field_name: str
    current_text: str
    filters: List[str]


TemplateReplacementList = List[Union[str, TemplateReplacement]]


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


class RustBackend:
    def __init__(self, path: str):
        self._backend = ankirspy.Backend(path)

    def _run_command(self, input: pb.BackendInput) -> pb.BackendOutput:
        input_bytes = input.SerializeToString()
        output_bytes = self._backend.command(input_bytes)
        output = pb.BackendOutput()
        output.ParseFromString(output_bytes)
        kind = output.WhichOneof("value")
        if kind == "error":
            raise BackendException(output.error)
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
