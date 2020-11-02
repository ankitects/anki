#!/usr/bin/env python3

import os
import sys
try:
    import bazelfixes

    bazelfixes.fix_pywin32_in_bazel()
    bazelfixes.fix_extraneous_path_in_bazel()
except ImportError:
    pass

import aqt

if not os.environ.get("ANKI_IMPORT_ONLY"):
    aqt.run()
