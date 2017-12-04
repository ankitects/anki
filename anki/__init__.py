# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

if sys.version_info[0] < 3 or sys.version_info[1] < 6:
    raise Exception("Anki requires Python 3.6+")

if sys.getfilesystemencoding().lower() in ("ascii", "ansi_x3.4-1968"):
    raise Exception("Anki requires a UTF-8 locale.")

version="2.1.0beta27" # build scripts grep this line, so preserve formatting
from anki.storage import Collection
__all__ = ["Collection"]
