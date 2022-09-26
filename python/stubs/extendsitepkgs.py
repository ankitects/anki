# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys
from mypy import pyinfo

# taken from pyinfo
if __name__ == "__main__":
    old_sys_path = sys.path
    sys.path = sys.path[1:]
    import types  # noqa

    sys.path = old_sys_path


def getsearchdirs():
    dirs = pyinfo.getsearchdirs()
    dirs.append(os.getenv("EXTRA_SITE_PACKAGES"))
    return dirs


if sys.argv[-1] == "getsearchdirs":
    print(repr(getsearchdirs()))
else:
    sys.exit(1)
