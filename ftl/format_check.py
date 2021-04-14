# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import glob
import os
import sys

import format

template_root = os.path.dirname(sys.argv[1])
template_files = glob.glob(os.path.join(template_root, "*", "*.ftl"), recursive=True)

if not format.check_files(template_files, fix=False):
    sys.exit(1)
