"""
todo
"""

import subprocess
import time
from concurrent.futures import Future
from typing import cast

from anki.sound import AVTag, TTSTag
from aqt.sound import OnDoneCallback, Player, PlayerInterrupted
from aqt.taskman import TaskManager


class TTSPlayer(Player):  # pylint: disable=abstract-method
    def can_play(self, tag: AVTag) -> bool:
        return isinstance(tag, TTSTag)


class MacTTSPlayer(TTSPlayer):
    def __init__(self, taskman: TaskManager):
        self._taskman = taskman
        self._terminate_flag = False

    def play(self, tag: AVTag, on_done: OnDoneCallback) -> None:
        ttag = cast(TTSTag, tag)
        self._taskman.run(
            lambda: self._play(ttag), lambda ret: self._on_done(ret, on_done)
        )

    def _play(self, tag: TTSTag) -> None:
        process = subprocess.Popen(
            ["say", "-v", "Alex", "-f", "-"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # write the input text to stdin
        process.stdin.write(tag.text.encode("utf8"))
        process.stdin.close()
        # and wait for termination
        while True:
            try:
                process.wait(0.1)
                if process.returncode != 0:
                    print(f"player got return code: {process.returncode}")
                return
            except subprocess.TimeoutExpired:
                pass
            if self._terminate_flag:
                process.terminate()
                self._terminate_flag = False
                raise PlayerInterrupted()

    def _on_done(self, ret: Future, cb: OnDoneCallback) -> None:
        try:
            ret.result()
        except PlayerInterrupted:
            # don't fire done callback when interrupted
            return
        cb()

    def stop(self):
        self._terminate_flag = True
        # block until stopped
        while self._terminate_flag:
            time.sleep(0.1)
