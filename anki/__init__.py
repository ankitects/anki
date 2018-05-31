# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys
import os
import platform

if sys.version_info[0] > 2:
    raise Exception("Anki should be run with Python 2")
elif sys.version_info[1] < 6:
    raise Exception("Anki requires Python 2.6+")
elif sys.getfilesystemencoding().lower() in ("ascii", "ansi_x3.4-1968"):
    raise Exception("Anki requires a UTF-8 locale.")

try:
    import simplejson as json
except:
    import json as json
if json.__version__ < "1.7.3":
    raise Exception("SimpleJSON must be 1.7.3 or later.")

# add path to bundled third party libs
ext = os.path.realpath(os.path.join(
    os.path.dirname(__file__), "../thirdparty"))
sys.path.insert(0, ext)
arch = platform.architecture()
if arch[1] == "ELF":
    # add arch-dependent libs
    sys.path.insert(0, os.path.join(ext, "py2.%d-%s" % (
        sys.version_info[1], arch[0][0:2])))

version="2.0.52" # build scripts grep this line, so preserve formatting
from anki.storage import Collection
__all__ = ["Collection"]
