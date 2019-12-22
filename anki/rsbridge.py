import _ankirs  # pytype: disable=import-error


class RSBridge:
    def __init__(self):
        self._bridge = _ankirs.Bridge()
        assert self._bridge.cmd("") == "test"

bridge = RSBridge()
