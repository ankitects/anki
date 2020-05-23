# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Legacy support
"""

from typing import List

import anki
import aqt
from anki.sound import SoundOrVideoTag
from anki.template import av_tags_to_native
from aqt.theme import theme_manager

# Routines removed from pylib/
##########################################################################


def bodyClass(col, card) -> str:
    print("bodyClass() deprecated")
    return theme_manager.body_classes_for_card_ord(card.ord)


def allSounds(text) -> List:
    print("allSounds() deprecated")
    out = aqt.mw.col.backend.extract_av_tags(text=text, question_side=True)
    return [
        x.filename
        for x in av_tags_to_native(out.av_tags)
        if isinstance(x, SoundOrVideoTag)
    ]


def stripSounds(text) -> str:
    print("stripSounds() deprecated")
    return aqt.mw.col.backend.strip_av_tags(text)


def fmtTimeSpan(time, pad=0, point=0, short=False, inTime=False, unit=99):
    print("fmtTimeSpan() has become col.backend.format_time_span()")
    return aqt.mw.col.format_timespan(time)


def install_pylib_legacy() -> None:
    anki.utils.bodyClass = bodyClass  # type: ignore
    anki.utils.fmtTimeSpan = fmtTimeSpan  # type: ignore
    anki.sound._soundReg = r"\[sound:(.+?)\]"  # type: ignore
    anki.sound.allSounds = allSounds  # type: ignore
    anki.sound.stripSounds = stripSounds  # type: ignore
