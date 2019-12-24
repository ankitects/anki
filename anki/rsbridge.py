from typing import Dict, List

import _ankirs  # pytype: disable=import-error

import anki.bridge_pb2 as pb

from .types import AllTemplateReqs


class BridgeException(Exception):
    def __str__(self) -> str:
        err: pb.BridgeError = self.args[0]  # pylint: disable=unsubscriptable-object
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


class RSBridge:
    def __init__(self):
        self._bridge = _ankirs.Bridge()

    def _run_command(self, input: pb.BridgeInput) -> pb.BridgeOutput:
        input_bytes = input.SerializeToString()
        output_bytes = self._bridge.command(input_bytes)
        output = pb.BridgeOutput()
        output.ParseFromString(output_bytes)
        kind = output.WhichOneof("value")
        if kind == "error":
            raise BridgeException(output.error)
        else:
            return output

    def plus_one(self, num: int) -> int:
        input = pb.BridgeInput(plus_one=pb.PlusOneIn(num=num))
        output = self._run_command(input)
        return output.plus_one.num

    def template_requirements(
        self, template_fronts: List[str], field_map: Dict[str, int]
    ) -> AllTemplateReqs:
        input = pb.BridgeInput(
            template_requirements=pb.TemplateRequirementsIn(
                template_front=template_fronts, field_names_to_ordinals=field_map
            )
        )
        output = self._run_command(input).template_requirements
        return proto_template_reqs_to_legacy(output.requirements)
