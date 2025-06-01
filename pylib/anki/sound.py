# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Sound/TTS references extracted from card text.

These can be accessed via eg card.question_av_tags()
"""

from __future__ import annotations

import os
import re
from dataclasses import dataclass
from typing import Union

from anki import hooks


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

    def path(self, media_folder: str) -> str:
        "Prepend the media folder to the filename."
        # Ensure filename doesn't reference parent folder
        filename = os.path.basename(self.filename)
        filename = hooks.media_file_filter(filename)
        return os.path.join(media_folder, filename)


# note this does not include image tags, which are handled with HTML.
AVTag = Union[SoundOrVideoTag, TTSTag]

AV_REF_RE = re.compile(r"\[anki:(play:(.):(\d+))\]")


def strip_av_refs(text: str) -> str:
    return AV_REF_RE.sub("", text)
