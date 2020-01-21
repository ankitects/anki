"""
Basic text to speech support.

Users can use the following in their card template:

{{tts en_US:Field}}

or

{{tts ja_JP voices=Kyoko,Otoya,Another_name:Field}}

The first argument must be a language code. If provided,
voices is a comma-separated list of one or more voices that
the user would prefer. Spaces must not be included.
Underscores will be converted to spaces.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from typing import List, Optional, cast

from anki.sound import AVTag, TTSTag
from aqt.sound import SimpleProcessPlayer
from aqt.taskman import TaskManager


@dataclass
class TTSArgs:
    # requested language
    lang: str
    # preferred voices, will use first available if possible
    voices: List[str]

    @classmethod
    def from_string(cls, args: List[str]) -> TTSArgs:
        voices: Optional[List[str]] = None

        lang = args[0]

        for arg in args[1:]:
            try:
                key, val = arg.split("=")
            except ValueError:
                continue
            key = key.strip()
            val = val.strip().replace("_", " ")

            if key == "voices":
                voices = val.split(",")

        return TTSArgs(voices=voices or [], lang=lang)


# Mac support
##########################################################################


@dataclass
class MacVoice:
    name: str
    lang: str


VOICE_HELP_LINE_RE = re.compile(r"^(\S+)\s+(\S+)\s+.*$")


def parse_voice_line(line: str) -> Optional[MacVoice]:
    m = VOICE_HELP_LINE_RE.match(line)
    if not m:
        return None
    return MacVoice(name=m.group(1), lang=m.group(2))


class MacTTSPlayer(SimpleProcessPlayer):
    def __init__(self, taskman: TaskManager):
        super().__init__(taskman)
        self._available_voices: Optional[List[MacVoice]] = None

    def _play(self, tag: AVTag) -> None:
        ttag = cast(TTSTag, tag)
        voice = self.voice_for_tag(ttag)

        self._process = subprocess.Popen(
            ["say", "-v", voice.name, "-f", "-"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # write the input text to stdin
        self._process.stdin.write(ttag.text.encode("utf8"))
        self._process.stdin.close()

        self._wait_for_termination()

    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if not isinstance(tag, TTSTag):
            return None

        # todo
        return 0

    def voices(self) -> List[MacVoice]:
        if not self._available_voices:
            cmd = subprocess.run(
                ["say", "-v", "?"], capture_output=True, check=True, encoding="utf8"
            )
            self._available_voices = []
            for line in cmd.stdout.splitlines():
                voice = parse_voice_line(line)
                if voice:
                    self._available_voices.append(voice)

        return self._available_voices

    def voice_for_tag(self, tag: TTSTag) -> MacVoice:
        args = TTSArgs.from_string(tag.args)
        voices = self.voices()

        # any requested voices match?
        for requested_voice in args.voices:
            avail_voice = next((x for x in voices if x.name == requested_voice), None)
            if avail_voice:
                return avail_voice

        # requested language match?
        avail_voice = next((x for x in voices if x.lang == args.lang), None)
        if avail_voice:
            return avail_voice

        # fall back on first voice
        return voices[0]
