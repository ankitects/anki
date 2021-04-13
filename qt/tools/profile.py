# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

import os
import sys

import snakeviz
from snakeviz.cli import main

profile = os.path.join(os.environ["BUILD_WORKSPACE_DIRECTORY"], "anki.prof")
sys.argv.append(profile)
sys.exit(main())
