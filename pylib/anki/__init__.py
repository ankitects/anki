# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

from anki.buildinfo import version
from anki.storage import Collection

if sys.version_info[0] < 3 or sys.version_info[1] < 7:
    raise Exception("Anki requires Python 3.7+")

if sys.getfilesystemencoding().lower() in ("ascii", "ansi_x3.4-1968"):
    raise Exception("Anki requires a UTF-8 locale.")


__all__ = ["Collection"]
