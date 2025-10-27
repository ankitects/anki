# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

sys.path.extend(["pylib", "qt", "out/pylib", "out/qt"])

import aqt

if not os.environ.get("SKIP_RUN"):
    aqt.run()
