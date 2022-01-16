# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from mypy import pyinfo

if sys.argv[-1] == "getsitepackages":
    pkgs = pyinfo.getsitepackages()
    pkgs.append(os.getenv("EXTRA_SITE_PACKAGES"))
    print(repr(pkgs))
elif sys.argv[-1] == "getprefixes":
    print(repr(pyinfo.getprefixes()))
