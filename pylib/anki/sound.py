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

    SECURITY: We should only ever construct this with basename(filename),
    as passing arbitrary paths to mpv from a shared deck is a security issue.

    Anki add-ons can supply an absolute file path to play any file on disk
    using the built-in media player.
    """

    filename: str

    def path(self, media_folder: str) -> str:
        "Prepend the media folder to the filename."
        if os.path.basename(self.filename) == self.filename:
            # Path in the current collection's media folder.
            # Ensure filename doesn't reference parent folder.
            head, tail = media_folder, self.filename
        else:
            # Add-ons can use absolute paths to play arbitrary files on disk.
            # Example: sound.av_player.play_tags([SoundOrVideoTag("/path/to/file")])
            head, tail = os.path.split(os.path.abspath(self.filename))
        tail = hooks.media_file_filter(tail)
        return os.path.join(head, tail)


# note this does not include image tags, which are handled with HTML.
AVTag = Union[SoundOrVideoTag, TTSTag]

AV_REF_RE = re.compile(r"\[anki:(play:(.):(\d+))\]")


def strip_av_refs(text: str) -> str:
    return AV_REF_RE.sub("", text)
