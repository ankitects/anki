"""
Basic text to speech support.

Users can use the following in their card template:

{{tts en_US:Field}}

or

{{tts ja_JP voices=Kyoko,Otoya,Another_name:Field}}

The first argument must be a language code.

If provided, voices is a comma-separated list of one or more voices that
the user would prefer. Spaces must not be included. Underscores will be
converted to spaces.

AVPlayer decides which TTSPlayer to use based on the returned rank.
In the default implementation, the TTS player is chosen based on the order
of voices the user has specified. When adding new TTS players, your code
can either expose the underlying names the TTS engine provides, or simply
expose the name of the engine, which would mean the user could write
{{tts en_AU voices=MyEngine}} to prioritize your engine.
"""

from __future__ import annotations

import re
import subprocess
from dataclasses import dataclass
from typing import List, Optional

from anki.sound import AVTag, TTSTag
from aqt.sound import SimpleProcessPlayer


@dataclass
class TTSVoice:
    name: str
    lang: str


@dataclass
class TTSVoiceMatch:
    voice: TTSVoice
    rank: int


class TTSPlayer:
    default_rank = 0
    _available_voices: Optional[List[TTSVoice]] = None

    def get_available_voices(self) -> List[TTSVoice]:
        return []

    def voices(self) -> List[TTSVoice]:
        if self._available_voices is None:
            self._available_voices = self.get_available_voices()
        return self._available_voices

    def voice_for_tag(self, tag: TTSTag) -> Optional[TTSVoiceMatch]:
        avail_voices = self.voices()

        rank = self.default_rank

        # any requested voices match?
        for requested_voice in tag.voices:
            for avail in avail_voices:
                if avail.name == requested_voice:
                    return TTSVoiceMatch(voice=avail, rank=rank)

            rank -= 1

        # if no preferred voices match, we fall back on language
        # with a rank of -100
        for avail in avail_voices:
            if avail.lang == tag.lang:
                return TTSVoiceMatch(voice=avail, rank=-100)

        return None


class TTSProcessPlayer(SimpleProcessPlayer, TTSPlayer):
    def rank_for_tag(self, tag: AVTag) -> Optional[int]:
        if not isinstance(tag, TTSTag):
            return None

        match = self.voice_for_tag(tag)
        if match:
            return match.rank
        else:
            return None


# Mac support
##########################################################################


class MacTTSPlayer(TTSProcessPlayer):
    VOICE_HELP_LINE_RE = re.compile(r"^(\S+)\s+(\S+)\s+.*$")

    def _play(self, tag: AVTag) -> None:
        assert isinstance(tag, TTSTag)
        match = self.voice_for_tag(tag)
        assert match
        voice = match.voice

        self._process = subprocess.Popen(
            ["say", "-v", voice.name, "-f", "-"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        # write the input text to stdin
        self._process.stdin.write(tag.field_text.encode("utf8"))
        self._process.stdin.close()

        self._wait_for_termination()

    def get_available_voices(self) -> List[TTSVoice]:
        cmd = subprocess.run(
            ["say", "-v", "?"], capture_output=True, check=True, encoding="utf8"
        )

        voices = []
        for line in cmd.stdout.splitlines():
            voice = self._parse_voice_line(line)
            if voice:
                voices.append(voice)
        return voices

    def _parse_voice_line(self, line: str) -> Optional[TTSVoice]:
        m = self.VOICE_HELP_LINE_RE.match(line)
        if not m:
            return None
        return TTSVoice(name=m.group(1), lang=m.group(2))
