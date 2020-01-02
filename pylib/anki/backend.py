# pylint: skip-file

from typing import Dict, List

import ankirspy  # pytype: disable=import-error

import anki.backend_pb2 as pb

from .types import AllTemplateReqs

SchedTimingToday = pb.SchedTimingTodayOut


class BackendException(Exception):
    def __str__(self) -> str:
        err: pb.BackendError = self.args[0]  # pylint: disable=unsubscriptable-object
        kind = err.WhichOneof("value")
        if kind == "invalid_input":
            return f"invalid input: {err.invalid_input.info}"
        elif kind == "template_parse":
            return f"template parse: {err.template_parse.info}"
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


class Backend:
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

    def plus_one(self, num: int) -> int:
        input = pb.BackendInput(plus_one=pb.PlusOneIn(num=num))
        output = self._run_command(input)
        return output.plus_one.num

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
        self, start: int, end: int, offset: int, rollover: int
    ) -> SchedTimingToday:
        return self._run_command(
            pb.BackendInput(
                sched_timing_today=pb.SchedTimingTodayIn(
                    created=start, now=end, minutes_west=offset, rollover_hour=rollover,
                )
            )
        ).sched_timing_today
