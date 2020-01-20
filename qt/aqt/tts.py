"""
todo
"""

import subprocess
from concurrent.futures import Future
from typing import Callable, cast

from anki.sound import AVTag, TTSTag
from aqt.sound import OnDoneCallback, Player
from aqt.taskman import TaskManager


class TTSPlayer(Player):  # pylint: disable=abstract-method
    def can_play(self, tag: AVTag) -> bool:
        return isinstance(tag, TTSTag)


class MacTTSPlayer(TTSPlayer):
    _taskman = TaskManager()

    def play(self, tag: AVTag, on_done: Callable[[], None]) -> None:
        ttag = cast(TTSTag, tag)
        self._taskman.run(
            lambda: self._play(ttag), lambda ret: self._on_done(ret, on_done)
        )

    def _play(self, tag: TTSTag) -> None:
        ret = subprocess.run(
            ["say", "-v", "Alex", "-f", "-"],
            input=tag.text,
            encoding="utf8",
            check=True,
        )

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        # will raise on error
        ret.result()
        cb()

    def stop(self):
        pass
