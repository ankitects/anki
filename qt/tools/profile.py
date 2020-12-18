import os
import sys

import snakeviz
from snakeviz.cli import main

profile = os.path.join(os.environ["BUILD_WORKSPACE_DIRECTORY"], "anki.prof")
sys.argv.append(profile)
sys.exit(main())
