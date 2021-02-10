# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

"""
Legacy support
"""

from typing import Any, List

import anki
import aqt
from aqt.theme import theme_manager

# Routines removed from pylib/
##########################################################################


def bodyClass(col, card) -> str:  # type: ignore
    print("bodyClass() deprecated")
    return theme_manager.body_classes_for_card_ord(card.ord)


def allSounds(text) -> List:  # type: ignore
    print("allSounds() deprecated")
    return aqt.mw.col.media._extract_filenames(text)


def stripSounds(text) -> str:  # type: ignore
    print("stripSounds() deprecated")
    return aqt.mw.col.media.strip_av_tags(text)


def fmtTimeSpan(
    time: Any,
    pad: Any = 0,
    point: Any = 0,
    short: Any = False,
    inTime: Any = False,
    unit: Any = 99,
) -> Any:
    print("fmtTimeSpan() has become col.format_timespan()")
    return aqt.mw.col.format_timespan(time)


def install_pylib_legacy() -> None:
    anki.utils.bodyClass = bodyClass  # type: ignore
    anki.utils.fmtTimeSpan = fmtTimeSpan  # type: ignore
    anki.sound._soundReg = r"\[sound:(.+?)\]"  # type: ignore
    anki.sound.allSounds = allSounds  # type: ignore
    anki.sound.stripSounds = stripSounds  # type: ignore
