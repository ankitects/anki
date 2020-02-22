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
from aqt.theme import theme_manager

# Routines removed from pylib/
##########################################################################


def bodyClass(col, card) -> str:
    print("bodyClass() deprecated")
    return theme_manager.body_classes_for_card_ord(card.ord)


def allSounds(text) -> List:
    print("allSounds() deprecated")
    text, tags = aqt.mw.col.backend.extract_av_tags(text, True)
    return [x.filename for x in tags if isinstance(x, SoundOrVideoTag)]


def stripSounds(text) -> str:
    print("stripSounds() deprecated")
    return aqt.mw.col.backend.strip_av_tags(text)


def install_pylib_legacy():
    anki.utils.bodyClass = bodyClass
    anki.sound._soundReg = r"\[sound:(.*?)\]"
    anki.sound.allSounds = allSounds
    anki.sound.stripSounds = stripSounds
