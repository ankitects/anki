# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os

_buildinfo = {}
for line in open(os.path.join(os.path.dirname(__file__), "buildinfo.txt")).readlines():
    elems = line.split()
    if len(elems) == 2:
        k, v = elems
        _buildinfo[k] = v

buildhash=_buildinfo["STABLE_BUILDHASH"]
version=_buildinfo["STABLE_VERSION"]
