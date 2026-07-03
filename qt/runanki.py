#!/usr/bin/env python3
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

try:
    import bazelfixes

    bazelfixes.fix_pywin32_in_bazel()
    bazelfixes.fix_extraneous_path_in_bazel()
    bazelfixes.fix_run_on_macos()
except ImportError:
    pass

import aqt

if not os.environ.get("ANKI_IMPORT_ONLY"):
    aqt.run()
