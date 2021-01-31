# Copyright: Ankitects Pty Ltd and contributors
# -*- coding: utf-8 -*-
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Legacy support
"""

from typing import List

import anki
import aqt
from aqt.theme import theme_manager

# Routines removed from pylib/
##########################################################################


def bodyClass(col, card) -> str:
    print("bodyClass() deprecated")
    return theme_manager.body_classes_for_card_ord(card.ord)


def allSounds(text) -> List:
    print("allSounds() deprecated")
    return aqt.mw.col.media._extract_filenames(text)


def stripSounds(text) -> str:
    print("stripSounds() deprecated")
    return aqt.mw.col.media.strip_av_tags(text)


def fmtTimeSpan(time, pad=0, point=0, short=False, inTime=False, unit=99):
    print("fmtTimeSpan() has become col.format_timespan()")
    return aqt.mw.col.format_timespan(time)


def install_pylib_legacy() -> None:
    anki.utils.bodyClass = bodyClass  # type: ignore
    anki.utils.fmtTimeSpan = fmtTimeSpan  # type: ignore
    anki.sound._soundReg = r"\[sound:(.+?)\]"  # type: ignore
    anki.sound.allSounds = allSounds  # type: ignore
    anki.sound.stripSounds = stripSounds  # type: ignore
