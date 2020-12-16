# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from typing import Dict


def _build_info_path() -> str:
    path = os.path.join(os.path.dirname(__file__), "buildinfo.txt")
    # running in place?
    if os.path.exists(path):
        return path
    # packaged build?
    path = os.path.join(sys.prefix, "buildinfo.txt")
    if os.path.exists(path):
        return path

    raise Exception("missing buildinfo.txt")

def _get_build_info() -> Dict[str, str]:
    info = {}
    with open(_build_info_path()) as file:
        for line in file.readlines():
            elems = line.split()
            if len(elems) == 2:
                k, v = elems
                info[k] = v

    return info

_buildinfo = _get_build_info()
buildhash=_buildinfo["STABLE_BUILDHASH"]
version=_buildinfo["STABLE_VERSION"]
