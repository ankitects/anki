# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

from anki.buildinfo import version
from anki.collection import Collection

if sys.version_info[0] < 3 or sys.version_info[1] < 7:
    raise Exception("Anki requires Python 3.7+")

# ensure unicode filenames are supported
try:
    "テスト".encode(sys.getfilesystemencoding())
except UnicodeEncodeError as exc:
    raise Exception("Anki requires a UTF-8 locale.") from exc

__all__ = ["Collection"]
