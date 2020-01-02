# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import re
from typing import List

# Shared utils
##########################################################################

_soundReg = r"\[sound:(.*?)\]"


def allSounds(text) -> List:
    return re.findall(_soundReg, text)


def stripSounds(text) -> str:
    return re.sub(_soundReg, "", text)


def hasSound(text) -> bool:
    return re.search(_soundReg, text) is not None
