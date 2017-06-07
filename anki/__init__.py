# -*- coding: utf-8 -*-
# Copyright: Damien Elmes <anki@ichi2.net>
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import sys

if sys.version_info[0] < 3:
    raise Exception("Anki should be run with Python 3")
elif sys.version_info[1] < 4:
    raise Exception("Anki requires Python 3.4+")

version="2.1.0a14" # build scripts grep this line, so preserve formatting
from anki.storage import Collection
__all__ = ["Collection"]
