# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Sound/TTS references extracted from card text.

Use collection.backend.strip_av_tags(string) to remove all tags,
and collection.backend.get_av_tags(string) to get a list of AVTags.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import List, Union


@dataclass
class TTSTag:
    """Records information about a text to speech tag.

    See tts.py for more information.
    """

    args: List[str]
    text: str


@dataclass
class SoundOrVideoTag:
    """Contains the filename inside a [sound:...] tag.

    Video files also use [sound:...].
    """

    filename: str


# note this does not include image tags, which are handled with HTML.
AVTag = Union[SoundOrVideoTag, TTSTag]

# Legacy utils
##########################################################################
# these will be removed in the future

_soundReg = r"\[sound:(.*?)\]"


def allSounds(text) -> List:
    from aqt import mw

    return [
        x.filename
        for x in mw.col.backend.get_av_tags(text)
        if isinstance(x, SoundOrVideoTag)
    ]


def stripSounds(text) -> str:
    from aqt import mw

    return mw.col.backend.strip_av_tags(text)
