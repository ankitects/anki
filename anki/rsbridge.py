import _ankirs  # pytype: disable=import-error
import betterproto

from anki.proto import proto as pb


class BridgeException(Exception):
    def __str__(self) -> str:
        err: pb.BridgeError = self.args[0]  # pylint: disable=unsubscriptable-object
        (kind, obj) = betterproto.which_one_of(err, "value")
        if kind == "invalid_input":
            return f"invalid input: {obj.info}"
        else:
            return f"unhandled error: {err} {obj}"


class RSBridge:
    def __init__(self):
        self._bridge = _ankirs.Bridge()

    def _run_command(self, input: pb.BridgeInput) -> pb.BridgeOutput:
        input_bytes = bytes(input)
        output_bytes = self._bridge.command(input_bytes)
        output = pb.BridgeOutput().parse(output_bytes)
        (kind, obj) = betterproto.which_one_of(output, "value")
        if kind == "error":
            raise BridgeException(obj)
        else:
            return output

    def plus_one(self, num: int) -> int:
        input = pb.BridgeInput(plus_one=pb.PlusOneIn(num=num))
        output = self._run_command(input)
        return output.plus_one.num


bridge = RSBridge()
