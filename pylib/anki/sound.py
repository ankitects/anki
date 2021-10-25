# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Sound/TTS references extracted from card text.

These can be accessed via eg card.question_av_tags()
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Union


@dataclass
class TTSTag:
    """Records information about a text to speech tag.

    See tts.py for more information.
    """

    field_text: str
    lang: str
    voices: list[str]
    speed: float
    # each arg should be in the form 'foo=bar'
    other_args: list[str]


@dataclass
class SoundOrVideoTag:
    """Contains the filename inside a [sound:...] tag.

    Video files also use [sound:...].
    """

    filename: str


# note this does not include image tags, which are handled with HTML.
AVTag = Union[SoundOrVideoTag, TTSTag]

AV_REF_RE = re.compile(r"\[anki:(play:(.):(\d+))\]")


def strip_av_refs(text: str) -> str:
    return AV_REF_RE.sub("", text)
